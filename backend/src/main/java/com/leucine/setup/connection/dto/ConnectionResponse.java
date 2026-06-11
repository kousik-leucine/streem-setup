package com.leucine.setup.connection.dto;

import com.leucine.setup.connection.Connection;
import com.leucine.setup.connection.Environment;
import com.leucine.setup.connection.SshAuthMethod;

public record ConnectionResponse(
    String id,
    String name,
    String host,
    int port,
    String database,
    String username,
    String sslMode,
    Environment environment,
    String notes,
    long createdAt,
    Long lastUsedAt,
    SshConfigView ssh
) {
  public record SshConfigView(
      String host,
      int port,
      String username,
      SshAuthMethod authMethod,
      String privateKeyPath,
      boolean strictHostKeyCheck
  ) {}

  public static ConnectionResponse from(Connection c) {
    SshConfigView ssh = null;
    if (c.ssh() != null) {
      ssh = new SshConfigView(
          c.ssh().host(), c.ssh().port(), c.ssh().username(),
          c.ssh().authMethod(), c.ssh().privateKeyPath(),
          c.ssh().strictHostKeyCheck()
      );
    }
    return new ConnectionResponse(c.id(), c.name(), c.host(), c.port(), c.database(),
        c.username(), c.sslMode(), c.environment(), c.notes(), c.createdAt(), c.lastUsedAt(), ssh);
  }
}
