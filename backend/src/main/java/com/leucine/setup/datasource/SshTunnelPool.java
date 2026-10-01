package com.leucine.setup.datasource;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Caches open SSH tunnels by connection id so consecutive operations against
 * the same target reuse one session instead of paying 2–5 s of SSH handshake
 * per request. An idle tunnel is closed after
 * {@link TargetProperties#tunnelIdleTimeout()}; a live one is held open by
 * SSH keepalives rather than by traffic.
 *
 * Handshakes are serialized per connection id — concurrent first-time requests for the
 * same target wait on one handshake instead of racing to open several — while different
 * connection ids never block each other.
 *
 * Invalidate explicitly on connection update / delete so a re-keyed bastion
 * doesn't get reused with stale credentials.
 */
@Component
public class SshTunnelPool {

  private static final Logger log = LoggerFactory.getLogger(SshTunnelPool.class);

  private final TargetProperties props;
  private final Map<String, Pooled> tunnels = new ConcurrentHashMap<>();
  private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();
  /** Last local port used per connection, so a reconnect can keep the JDBC URL stable. */
  private final Map<String, Integer> lastLocalPort = new ConcurrentHashMap<>();
  private final List<Consumer<String>> invalidationListeners = new CopyOnWriteArrayList<>();

  private ScheduledExecutorService evictor;

  public SshTunnelPool(TargetProperties props) {
    this.props = props;
  }

  @PostConstruct
  void start() {
    evictor = Executors.newSingleThreadScheduledExecutor(daemon("ssh-tunnel-evictor"));
    evictor.scheduleAtFixedRate(this::evictIdle, 60, 60, TimeUnit.SECONDS);
  }

  @PreDestroy
  void stop() {
    if (evictor != null) evictor.shutdownNow();
    tunnels.values().forEach(p -> p.tunnel.close());
    tunnels.clear();
  }

  /**
   * Register a callback fired with the connection id whenever its tunnel is dropped
   * (invalidated, evicted, or found dead), so anything layered on the tunnel — a JDBC
   * pool holding sockets through it — can be torn down with it.
   */
  public void onInvalidate(Consumer<String> listener) {
    invalidationListeners.add(listener);
  }

  /**
   * Get a live tunnel for {@code connectionId}, opening one if the cached
   * tunnel is missing or has died. Returns the (shared) tunnel; do not close it.
   */
  public SshTunnel borrow(String connectionId, SshTunnelConfig cfg) {
    Pooled cached = tunnels.get(connectionId);
    if (cached != null && cached.tunnel.isAlive()) {          // fast path: no locking at all
      cached.lastUsedAt = System.currentTimeMillis();
      return cached.tunnel;
    }

    ReentrantLock lock = locks.computeIfAbsent(connectionId, k -> new ReentrantLock());
    lock.lock();
    try {
      Pooled existing = tunnels.get(connectionId);
      if (existing != null && existing.tunnel.isAlive()) {    // opened while we waited
        existing.lastUsedAt = System.currentTimeMillis();
        return existing.tunnel;
      }
      if (existing != null) {
        tunnels.remove(connectionId);
        existing.tunnel.close();
        notifyInvalidated(connectionId);                      // its JDBC sockets are dead too
      }
      try {
        SshTunnel t = SshTunnel.open(cfg, props, lastLocalPort.getOrDefault(connectionId, 0));
        lastLocalPort.put(connectionId, t.localPort());
        tunnels.put(connectionId, new Pooled(t, System.currentTimeMillis()));
        log.info("Opened tunnel for connection {} (pool size now {})", connectionId, tunnels.size());
        return t;
      } catch (Exception e) {
        throw new IllegalStateException("Failed to open SSH tunnel to "
            + cfg.host() + ": " + e.getMessage(), e);
      }
    } finally {
      lock.unlock();
    }
  }

  /** True when a live tunnel is already cached — no handshake needed for the next request. */
  public boolean isWarm(String connectionId) {
    Pooled p = tunnels.get(connectionId);
    return p != null && p.tunnel.isAlive();
  }

  /** Drop a connection's tunnel (e.g. on update/delete). Safe if absent. */
  public void invalidate(String connectionId) {
    Pooled p = tunnels.remove(connectionId);
    lastLocalPort.remove(connectionId);
    if (p != null) {
      p.tunnel.close();
      log.info("Invalidated tunnel for connection {}", connectionId);
    }
    notifyInvalidated(connectionId);
  }

  private void evictIdle() {
    long now = System.currentTimeMillis();
    long idleMillis = props.tunnelIdleTimeout().toMillis();
    Iterator<Map.Entry<String, Pooled>> it = tunnels.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<String, Pooled> e = it.next();
      Pooled p = e.getValue();
      if (!p.tunnel.isAlive() || now - p.lastUsedAt > idleMillis) {
        log.info("Evicting tunnel for connection {} (alive={}, idleMs={})",
            e.getKey(), p.tunnel.isAlive(), now - p.lastUsedAt);
        p.tunnel.close();
        it.remove();
        notifyInvalidated(e.getKey());
      }
    }
  }

  private void notifyInvalidated(String connectionId) {
    for (Consumer<String> l : invalidationListeners) {
      try {
        l.accept(connectionId);
      } catch (Exception e) {
        log.warn("Tunnel invalidation listener failed for {}: {}", connectionId, e.getMessage());
      }
    }
  }

  private static java.util.concurrent.ThreadFactory daemon(String name) {
    return r -> {
      Thread t = new Thread(r, name);
      t.setDaemon(true);
      return t;
    };
  }

  private static final class Pooled {
    final SshTunnel tunnel;
    volatile long lastUsedAt;
    Pooled(SshTunnel tunnel, long lastUsedAt) {
      this.tunnel = tunnel;
      this.lastUsedAt = lastUsedAt;
    }
  }
}
