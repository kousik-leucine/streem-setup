package com.leucine.setup.datasource;

import com.leucine.setup.connection.Connection;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Caches one JDBC connection pool per target connection.
 *
 * Without this every query opened a fresh physical Postgres connection — TCP setup, a new
 * SSH channel on the tunnel, TLS, then SCRAM authentication — which on a remote bastion
 * costs far more than the query itself. Pooling pays that once and keeps
 * {@link TargetProperties#poolMinIdle()} connections warm, so a dropdown or a preview is
 * one round trip.
 *
 * A cached pool is discarded when the connection's shape changes (host, port, database,
 * credentials, tunnel port) or when {@link SshTunnelPool} drops the tunnel underneath it,
 * so a re-keyed bastion or a moved local port can never be reused with stale sockets.
 */
@Component
public class TargetDataSourcePool {

  private static final Logger log = LoggerFactory.getLogger(TargetDataSourcePool.class);

  private final TargetProperties props;
  private final Map<String, Entry> pools = new ConcurrentHashMap<>();
  private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

  public TargetDataSourcePool(TargetProperties props, SshTunnelPool tunnelPool) {
    this.props = props;
    // A dead tunnel means every socket in the matching pool is dead: drop it together.
    tunnelPool.onInvalidate(this::invalidate);
  }

  @PreDestroy
  void stop() {
    pools.values().forEach(e -> closeQuietly(e.dataSource));
    pools.clear();
  }

  /**
   * The pooled DataSource for {@code conn} reaching Postgres at {@code host:port}
   * (loopback when tunneled). Pool creation is serialized per connection id so
   * concurrent first requests build one pool, not several.
   */
  public HikariDataSource get(Connection conn, String host, int port, String database,
                              String password) {
    String fingerprint = fingerprint(conn, host, port, database, password);

    Entry cached = pools.get(conn.id());
    if (cached != null && cached.fingerprint.equals(fingerprint) && !cached.dataSource.isClosed()) {
      return cached.dataSource;                       // fast path: no locking
    }

    ReentrantLock lock = locks.computeIfAbsent(conn.id(), k -> new ReentrantLock());
    lock.lock();
    try {
      Entry existing = pools.get(conn.id());
      if (existing != null && existing.fingerprint.equals(fingerprint) && !existing.dataSource.isClosed()) {
        return existing.dataSource;
      }
      if (existing != null) {
        log.info("Target pool for {} is stale, rebuilding", conn.name());
        pools.remove(conn.id());
        closeQuietly(existing.dataSource);
      }
      HikariDataSource ds = build(conn, host, port, database, password);
      pools.put(conn.id(), new Entry(fingerprint, ds));
      return ds;
    } finally {
      lock.unlock();
    }
  }

  /** Close and forget a connection's pool. Safe if absent. */
  public void invalidate(String connectionId) {
    Entry e = pools.remove(connectionId);
    if (e != null) {
      closeQuietly(e.dataSource);
      log.info("Closed target pool for connection {}", connectionId);
    }
  }

  /** True when a pool with at least one live connection is already standing. */
  public boolean isWarm(String connectionId) {
    Entry e = pools.get(connectionId);
    return e != null && !e.dataSource.isClosed()
        && e.dataSource.getHikariPoolMXBean() != null
        && e.dataSource.getHikariPoolMXBean().getTotalConnections() > 0;
  }

  private HikariDataSource build(Connection conn, String host, int port, String database,
                                 String password) {
    HikariConfig hc = new HikariConfig();
    hc.setPoolName("target-" + conn.id());
    hc.setDriverClassName("org.postgresql.Driver");
    hc.setJdbcUrl(jdbcUrl(conn, host, port, database));
    hc.setUsername(conn.username());
    hc.setPassword(password);

    hc.setMaximumPoolSize(props.poolMaxSize());
    hc.setMinimumIdle(props.poolMinIdle());
    hc.setConnectionTimeout(props.poolAcquireTimeout().toMillis());
    hc.setIdleTimeout(props.poolIdleTimeout().toMillis());
    hc.setMaxLifetime(props.poolMaxLifetime().toMillis());
    hc.setKeepaliveTime(props.poolKeepAlive().toMillis());
    hc.setConnectionTestQuery(null);                  // driver's isValid() — no extra round trip

    hc.addDataSourceProperty("ApplicationName", "streem-setup");
    hc.addDataSourceProperty("connectTimeout", String.valueOf(props.dbConnectTimeout().toSeconds()));
    hc.addDataSourceProperty("socketTimeout", String.valueOf(props.dbSocketTimeout().toSeconds()));
    hc.addDataSourceProperty("tcpKeepAlive", "true");
    // Skips a version-negotiation round trip on every physical connect.
    hc.addDataSourceProperty("assumeMinServerVersion", "9.0");

    HikariDataSource ds = new HikariDataSource(hc);
    log.info("Opened target pool for {} -> {}:{}/{} (max {})",
        conn.name(), host, port, database, props.poolMaxSize());
    return ds;
  }

  private String jdbcUrl(Connection conn, String host, int port, String database) {
    StringBuilder url = new StringBuilder("jdbc:postgresql://")
        .append(host).append(':').append(port)
        .append('/').append(database);
    if (conn.sslMode() != null && !conn.sslMode().isBlank()) {
      url.append("?sslmode=").append(conn.sslMode());
    }
    return url.toString();
  }

  /**
   * Everything that would make a standing pool wrong if it changed. The password is hashed
   * rather than stored so a rotated credential still rebuilds the pool without keeping the
   * secret in a long-lived map.
   */
  private String fingerprint(Connection conn, String host, int port, String database,
                             String password) {
    return String.join("|",
        host, String.valueOf(port), database,
        String.valueOf(conn.username()), String.valueOf(conn.sslMode()),
        String.valueOf(Objects.hashCode(password)));
  }

  private static void closeQuietly(HikariDataSource ds) {
    try {
      ds.close();
    } catch (Exception e) {
      log.warn("Error closing target pool: {}", e.getMessage());
    }
  }

  private record Entry(String fingerprint, HikariDataSource dataSource) {}
}
