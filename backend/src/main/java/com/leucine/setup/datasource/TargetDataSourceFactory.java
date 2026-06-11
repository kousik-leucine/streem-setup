package com.leucine.setup.datasource;

import com.leucine.setup.connection.Connection;
import com.leucine.setup.connection.SshAuthMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * Opens a JDBC handle to a target Postgres on demand. If the connection is
 * configured for SSH tunneling, borrows a (shared) tunnel from {@link SshTunnelPool}
 * — so repeated operations against the same target don't pay the SSH handshake
 * every time. The tunnel is owned by the pool, not by the returned
 * {@link TargetConnection}.
 */
@Component
public class TargetDataSourceFactory {

  private static final Logger log = LoggerFactory.getLogger(TargetDataSourceFactory.class);

  private final SshTunnelPool tunnelPool;

  public TargetDataSourceFactory(SshTunnelPool tunnelPool) {
    this.tunnelPool = tunnelPool;
  }

  public TargetConnection open(Connection conn, TargetSecrets secrets) {
    String jdbcHost = conn.host();
    int jdbcPort = conn.port();

    if (conn.ssh() != null) {
      SshTunnel tunnel = tunnelPool.borrow(conn.id(), sshConfig(conn, secrets));
      jdbcHost = "127.0.0.1";
      jdbcPort = tunnel.localPort();
    }

    DataSource ds = buildDataSource(conn, jdbcHost, jdbcPort, secrets.dbPassword(), conn.database());
    JdbcTemplate jdbc = new JdbcTemplate(ds);
    log.debug("Opened target {} (ssh={})", conn.name(), conn.ssh() != null);
    // Tunnel is pool-owned; TargetConnection.close() is a no-op for the tunnel.
    return new TargetConnection(jdbc, null);
  }

  /**
   * Opens a <strong>non-pooled</strong> handle for one-off work against an unsaved
   * connection (e.g. enumerating databases before the connection is created), connecting
   * to {@code connectDatabase} rather than the connection's configured database. The
   * returned {@link TargetConnection} <em>owns</em> its SSH tunnel — closing it (always do
   * so in try-with-resources) tears the tunnel down immediately, so nothing lingers on the
   * bastion or the database server. Do not route normal operations through this; use
   * {@link #open} so repeated work reuses the pooled tunnel.
   */
  public TargetConnection openEphemeral(Connection conn, TargetSecrets secrets, String connectDatabase) {
    String jdbcHost = conn.host();
    int jdbcPort = conn.port();
    SshTunnel tunnel = null;
    try {
      if (conn.ssh() != null) {
        tunnel = SshTunnel.open(sshConfig(conn, secrets));
        jdbcHost = "127.0.0.1";
        jdbcPort = tunnel.localPort();
      }
      DataSource ds = buildDataSource(conn, jdbcHost, jdbcPort, secrets.dbPassword(), connectDatabase);
      JdbcTemplate jdbc = new JdbcTemplate(ds);
      log.debug("Opened ephemeral target {}/{} (ssh={})", conn.host(), connectDatabase, conn.ssh() != null);
      return new TargetConnection(jdbc, tunnel);
    } catch (Exception e) {
      if (tunnel != null) tunnel.close();      // never leak the tunnel on a failed open
      throw new IllegalStateException("Failed to open connection to "
          + conn.host() + ": " + e.getMessage(), e);
    }
  }

  private SshTunnelConfig sshConfig(Connection conn, TargetSecrets secrets) {
    return new SshTunnelConfig(
        conn.ssh().host(),
        conn.ssh().port(),
        conn.ssh().username(),
        conn.ssh().authMethod(),
        conn.ssh().authMethod() == SshAuthMethod.PASSWORD ? secrets.sshPassword() : null,
        conn.ssh().privateKeyPath(),
        secrets.sshKeyPassphrase(),
        conn.host(),
        conn.port(),
        conn.ssh().strictHostKeyCheck()
    );
  }

  private DataSource buildDataSource(Connection conn, String host, int port, String password,
                                     String database) {
    DriverManagerDataSource ds = new DriverManagerDataSource();
    ds.setDriverClassName("org.postgresql.Driver");
    StringBuilder url = new StringBuilder("jdbc:postgresql://")
        .append(host).append(':').append(port)
        .append('/').append(database);
    if (conn.sslMode() != null && !conn.sslMode().isBlank()) {
      url.append("?sslmode=").append(conn.sslMode());
    }
    ds.setUrl(url.toString());
    ds.setUsername(conn.username());
    ds.setPassword(password);

    Properties props = new Properties();
    props.setProperty("ApplicationName", "streem-setup");
    props.setProperty("connectTimeout", "10");
    props.setProperty("socketTimeout", "60");
    ds.setConnectionProperties(props);
    return ds;
  }

  public record TargetSecrets(String dbPassword, String sshPassword, String sshKeyPassphrase) {}
}
