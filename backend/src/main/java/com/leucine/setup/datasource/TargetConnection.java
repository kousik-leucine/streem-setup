package com.leucine.setup.datasource;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * One target Postgres handle, optionally tunneled through SSH. Always close in a
 * try-with-resources so the SSH session and any backing socket get released.
 */
public final class TargetConnection implements AutoCloseable {

  private final JdbcTemplate jdbc;
  private final SshTunnel tunnel;       // nullable

  TargetConnection(JdbcTemplate jdbc, SshTunnel tunnel) {
    this.jdbc = jdbc;
    this.tunnel = tunnel;
  }

  public JdbcTemplate jdbc() { return jdbc; }

  @Override
  public void close() {
    if (tunnel != null) tunnel.close();
  }
}
