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
 * Opens a JDBC handle to a target Postgres on demand.
 *
 * Both expensive parts of reaching a target are cached: the SSH session comes from
 * {@link SshTunnelPool} and the physical Postgres connections come from
 * {@link TargetDataSourcePool}. A warm connection therefore costs one round trip, versus
 * a full SSH handshake plus Postgres authentication per query before. Neither the tunnel
 * nor the pool is owned by the returned {@link TargetConnection} — closing it releases
 * nothing shared, so it stays safe in try-with-resources.
 */
@Component
public class TargetDataSourceFactory {

  private static final Logger log = LoggerFactory.getLogger(TargetDataSourceFactory.class);

  private final SshTunnelPool tunnelPool;
  private final TargetDataSourcePool dataSourcePool;
  private final TargetProperties props;

  public TargetDataSourceFactory(SshTunnelPool tunnelPool, TargetDataSourcePool dataSourcePool,
                                 TargetProperties props) {
    this.tunnelPool = tunnelPool;
    this.dataSourcePool = dataSourcePool;
    this.props = props;
  }

  public TargetConnection open(Connection conn, TargetSecrets secrets) {
    String jdbcHost = conn.host();
    int jdbcPort = conn.port();

    if (conn.ssh() != null) {
      SshTunnel tunnel = tunnelPool.borrow(conn.id(), sshConfig(conn, secrets));
      jdbcHost = "127.0.0.1";
      jdbcPort = tunnel.localPort();
    }

    DataSource ds = dataSourcePool.get(conn, jdbcHost, jdbcPort, conn.database(), secrets.dbPassword());
    log.debug("Opened target {} (ssh={})", conn.name(), conn.ssh() != null);
    // Tunnel and pool are shared; TargetConnection.close() releases neither.
    return new TargetConnection(new JdbcTemplate(ds), null);
  }

  /**
   * Establishes the SSH tunnel and the JDBC pool for {@code conn} without running any
   * caller work, so the first real request doesn't pay for either. Called when the operator
   * selects a connection in the UI; safe to call repeatedly (a warm connection is a no-op).
   */
  public void warm(Connection conn, TargetSecrets secrets) {
    try (TargetConnection tc = open(conn, secrets)) {
      tc.jdbc().queryForObject("SELECT 1", Integer.class);   // forces one physical connection
    }
  }

  /** True when both the tunnel (if any) and the JDBC pool are already standing. */
  public boolean isWarm(Connection conn) {
    boolean tunnelReady = conn.ssh() == null || tunnelPool.isWarm(conn.id());
    return tunnelReady && dataSourcePool.isWarm(conn.id());
  }

  /**
   * Opens a <strong>non-pooled</strong> handle for one-off work against an unsaved
   * connection (e.g. enumerating databases before the connection is created), connecting
   * to {@code connectDatabase} rather than the connection's configured database. The
   * returned {@link TargetConnection} <em>owns</em> its SSH tunnel — closing it (always do
   * so in try-with-resources) tears the tunnel down immediately, so nothing lingers on the
   * bastion or the database server. Do not route normal operations through this; use
   * {@link #open} so repeated work reuses the pooled tunnel and pooled connections.
   */
  public TargetConnection openEphemeral(Connection conn, TargetSecrets secrets, String connectDatabase) {
    String jdbcHost = conn.host();
    int jdbcPort = conn.port();
    SshTunnel tunnel = null;
    try {
      if (conn.ssh() != null) {
        tunnel = SshTunnel.open(sshConfig(conn, secrets), props);
        jdbcHost = "127.0.0.1";
        jdbcPort = tunnel.localPort();
      }
      DataSource ds = buildUnpooledDataSource(conn, jdbcHost, jdbcPort, secrets.dbPassword(), connectDatabase);
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

  /** Single-use handle for {@link #openEphemeral}; pooling a throwaway target buys nothing. */
  private DataSource buildUnpooledDataSource(Connection conn, String host, int port, String password,
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

    Properties driverProps = new Properties();
    driverProps.setProperty("ApplicationName", "streem-setup");
    driverProps.setProperty("connectTimeout", String.valueOf(props.dbConnectTimeout().toSeconds()));
    driverProps.setProperty("socketTimeout", String.valueOf(props.dbSocketTimeout().toSeconds()));
    driverProps.setProperty("assumeMinServerVersion", "9.0");
    ds.setConnectionProperties(driverProps);
    return ds;
  }

  public record TargetSecrets(String dbPassword, String sshPassword, String sshKeyPassphrase) {}
}
