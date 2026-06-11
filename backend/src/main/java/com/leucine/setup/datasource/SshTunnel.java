package com.leucine.setup.datasource;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Properties;

/**
 * One-shot SSH local port forward: {@code 127.0.0.1:<freeLocalPort> ↔ remoteHost:remotePort}
 * tunneled through the bastion.
 *
 * Lifecycle: build via {@link #open}, query {@link #localPort()} for the loopback port
 * the JDBC URL should use, then {@link #close()} when the operation is done.
 */
public final class SshTunnel implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(SshTunnel.class);
  private static final int CONNECT_TIMEOUT_MS = 10_000;

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

  public static SshTunnel open(SshTunnelConfig cfg) throws JSchException, IOException {
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

    Properties props = new Properties();
    props.put("StrictHostKeyChecking", cfg.strictHostKeyCheck() ? "yes" : "no");
    props.put("PreferredAuthentications",
        cfg.authMethod() == com.leucine.setup.connection.SshAuthMethod.PASSWORD
            ? "password,keyboard-interactive"
            : "publickey");
    session.setConfig(props);

    session.connect(CONNECT_TIMEOUT_MS);

    int localPort = findFreePort();
    session.setPortForwardingL("127.0.0.1", localPort, cfg.remoteHost(), cfg.remotePort());
    log.info("SSH tunnel open: 127.0.0.1:{} -> {}@{}:{} -> {}:{}",
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

  private static int findFreePort() throws IOException {
    try (ServerSocket s = new ServerSocket(0)) {
      return s.getLocalPort();
    }
  }
}
