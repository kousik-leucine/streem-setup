package com.leucine.setup.datasource;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

/**
 * SSH local port forward: {@code 127.0.0.1:<localPort> ↔ remoteHost:remotePort}
 * tunneled through the bastion.
 *
 * Lifecycle: build via {@link #open}, query {@link #localPort()} for the loopback port
 * the JDBC URL should use, then {@link #close()} when the operation is done. Sessions are
 * normally shared and reopened by {@link SshTunnelPool}, which asks for the same local
 * port each time so a JDBC pool built on top survives a reconnect.
 */
public final class SshTunnel implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(SshTunnel.class);

  /**
   * Algorithm preferences prepended to JSch's defaults (defaults stay as fallback, so an
   * older bastion still negotiates). AES-GCM/CTR beat ChaCha20 on any AES-NI CPU, and
   * curve25519 keeps the key exchange to one cheap round trip.
   */
  private static final String[] FAST_KEX = {"curve25519-sha256", "curve25519-sha256@libssh.org"};
  private static final String[] FAST_CIPHERS = {"aes128-gcm@openssh.com", "aes128-ctr", "aes256-gcm@openssh.com"};
  private static final String[] FAST_MACS = {"hmac-sha2-256-etm@openssh.com", "hmac-sha2-256"};
  private static final String[] FAST_HOST_KEYS = {"ssh-ed25519", "rsa-sha2-256", "ecdsa-sha2-nistp256"};

  private final Session session;
  private final int localPort;

  private SshTunnel(Session session, int localPort) {
    this.session = session;
    this.localPort = localPort;
  }

  public int localPort() { return localPort; }

  public boolean isAlive() {
    return session != null && session.isConnected();
  }

  public static SshTunnel open(SshTunnelConfig cfg, TargetProperties props)
      throws JSchException, IOException {
    return open(cfg, props, 0);
  }

  /**
   * Opens the tunnel, binding {@code preferredLocalPort} when it is still free so a
   * reconnect can reuse the JDBC URL (and therefore the JDBC pool) that pointed at it.
   * Pass 0 — or a port that is taken — to get any free port.
   */
  public static SshTunnel open(SshTunnelConfig cfg, TargetProperties props, int preferredLocalPort)
      throws JSchException, IOException {
    long startedAt = System.nanoTime();
    JSch jsch = new JSch();

    if (cfg.authMethod() == com.leucine.setup.connection.SshAuthMethod.KEY) {
      if (cfg.privateKeyPath() == null || cfg.privateKeyPath().isBlank()) {
        throw new IllegalArgumentException("SSH key auth requires sshPrivateKeyPath");
      }
      if (cfg.keyPassphrase() != null && !cfg.keyPassphrase().isBlank()) {
        jsch.addIdentity(cfg.privateKeyPath(), cfg.keyPassphrase());
      } else {
        jsch.addIdentity(cfg.privateKeyPath());
      }
    }

    Session session = jsch.getSession(cfg.username(), cfg.host(), cfg.port());

    if (cfg.authMethod() == com.leucine.setup.connection.SshAuthMethod.PASSWORD) {
      if (cfg.password() == null) {
        throw new IllegalArgumentException("SSH password auth requires sshPassword");
      }
      session.setPassword(cfg.password());
    }

    Properties conf = new Properties();
    conf.put("StrictHostKeyChecking", cfg.strictHostKeyCheck() ? "yes" : "no");
    conf.put("PreferredAuthentications",
        cfg.authMethod() == com.leucine.setup.connection.SshAuthMethod.PASSWORD
            ? "password,keyboard-interactive"
            : "publickey");
    // Compression costs CPU on both ends and buys nothing on a LAN-speed bastion link.
    conf.put("compression.s2c", "none");
    conf.put("compression.c2s", "none");
    conf.put("compression_level", "0");
    session.setConfig(conf);

    if (props.sshFastAlgorithms()) {
      prefer(session, "kex", FAST_KEX);
      prefer(session, "cipher.c2s", FAST_CIPHERS);
      prefer(session, "cipher.s2c", FAST_CIPHERS);
      prefer(session, "mac.c2s", FAST_MACS);
      prefer(session, "mac.s2c", FAST_MACS);
      prefer(session, "server_host_key", FAST_HOST_KEYS);
    }

    // Keepalive is what stops the bastion (or an idle NAT) from silently dropping a
    // tunnel that the JDBC pool is still holding connections through.
    session.setServerAliveInterval((int) props.sshKeepAlive().toMillis());
    session.setServerAliveCountMax(props.sshKeepAliveCountMax());
    session.setDaemonThread(true);

    session.connect((int) props.sshConnectTimeout().toMillis());

    int localPort = bindLocalPort(session, preferredLocalPort, cfg);
    log.info("SSH tunnel open in {} ms: 127.0.0.1:{} -> {}@{}:{} -> {}:{}",
        (System.nanoTime() - startedAt) / 1_000_000,
        localPort, cfg.username(), cfg.host(), cfg.port(),
        cfg.remoteHost(), cfg.remotePort());

    return new SshTunnel(session, localPort);
  }

  @Override
  public void close() {
    if (session != null && session.isConnected()) {
      try {
        session.disconnect();
      } catch (Exception e) {
        log.warn("Error closing SSH session: {}", e.getMessage());
      }
    }
  }

  /** Prepend {@code first} to an algorithm list, keeping JSch's defaults as fallback. */
  private static void prefer(Session session, String key, String[] first) {
    String current = session.getConfig(key);
    Set<String> merged = new LinkedHashSet<>();
    for (String f : first) merged.add(f);
    if (current != null && !current.isBlank()) {
      for (String c : current.split(",")) {
        if (!c.isBlank()) merged.add(c.trim());
      }
    }
    session.setConfig(key, String.join(",", merged));
  }

  private static int bindLocalPort(Session session, int preferred, SshTunnelConfig cfg)
      throws JSchException, IOException {
    if (preferred > 0 && isFree(preferred)) {
      try {
        session.setPortForwardingL("127.0.0.1", preferred, cfg.remoteHost(), cfg.remotePort());
        return preferred;
      } catch (JSchException e) {
        log.debug("Could not reuse local port {} ({}), falling back to a free port",
            preferred, e.getMessage());
      }
    }
    int port = findFreePort();
    session.setPortForwardingL("127.0.0.1", port, cfg.remoteHost(), cfg.remotePort());
    return port;
  }

  private static boolean isFree(int port) {
    try (ServerSocket s = new ServerSocket(port, 1, InetAddress.getLoopbackAddress())) {
      return s.getLocalPort() == port;
    } catch (IOException e) {
      return false;
    }
  }

  private static int findFreePort() throws IOException {
    try (ServerSocket s = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      return s.getLocalPort();
    }
  }
}
