package com.leucine.setup.datasource;

import com.jcraft.jsch.JSchException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Caches open SSH tunnels by connection id so consecutive operations against
 * the same target reuse one session instead of paying 2–5 s of SSH handshake
 * per request. An idle tunnel is closed after {@link #IDLE_MILLIS}.
 *
 * Invalidate explicitly on connection update / delete so a re-keyed bastion
 * doesn't get reused with stale credentials.
 */
@Component
public class SshTunnelPool {

  private static final Logger log = LoggerFactory.getLogger(SshTunnelPool.class);
  private static final long IDLE_MILLIS = TimeUnit.MINUTES.toMillis(5);

  private final Map<String, Pooled> tunnels = new ConcurrentHashMap<>();
  private ScheduledExecutorService evictor;

  @PostConstruct
  void start() {
    evictor = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "ssh-tunnel-evictor");
      t.setDaemon(true);
      return t;
    });
    evictor.scheduleAtFixedRate(this::evictIdle, 60, 60, TimeUnit.SECONDS);
  }

  @PreDestroy
  void stop() {
    if (evictor != null) evictor.shutdownNow();
    tunnels.values().forEach(p -> p.tunnel.close());
    tunnels.clear();
  }

  /**
   * Get a live tunnel for {@code connectionId}, opening one if the cached
   * tunnel is missing or has died. Returns the (shared) tunnel; do not close it.
   */
  public SshTunnel borrow(String connectionId, SshTunnelConfig cfg) {
    Pooled pooled = tunnels.compute(connectionId, (k, existing) -> {
      if (existing != null && existing.tunnel.isAlive()) {
        existing.lastUsedAt = System.currentTimeMillis();
        return existing;
      }
      if (existing != null) existing.tunnel.close();
      try {
        SshTunnel t = SshTunnel.open(cfg);
        log.info("Opened tunnel for connection {} (pool size now {})",
            connectionId, tunnels.size() + 1);
        return new Pooled(t, System.currentTimeMillis());
      } catch (JSchException | IOException e) {
        throw new IllegalStateException("Failed to open SSH tunnel to "
            + cfg.host() + ": " + e.getMessage(), e);
      }
    });
    return pooled.tunnel;
  }

  /** Drop a connection's tunnel (e.g. on update/delete). Safe if absent. */
  public void invalidate(String connectionId) {
    Pooled p = tunnels.remove(connectionId);
    if (p != null) {
      p.tunnel.close();
      log.info("Invalidated tunnel for connection {}", connectionId);
    }
  }

  private void evictIdle() {
    long now = System.currentTimeMillis();
    Iterator<Map.Entry<String, Pooled>> it = tunnels.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<String, Pooled> e = it.next();
      Pooled p = e.getValue();
      if (!p.tunnel.isAlive() || now - p.lastUsedAt > IDLE_MILLIS) {
        log.info("Evicting tunnel for connection {} (alive={}, idleMs={})",
            e.getKey(), p.tunnel.isAlive(), now - p.lastUsedAt);
        p.tunnel.close();
        it.remove();
      }
    }
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
