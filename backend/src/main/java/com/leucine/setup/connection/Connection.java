package com.leucine.setup.connection;

/**
 * A configured Postgres target. Never carries plaintext secrets — fetch the db
 * password (and SSH secrets, if applicable) via the service to decrypt only at
 * the point of use.
 */
public record Connection(
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
    SshConfig ssh                              // null when SSH tunneling is off
) {

  /** SSH bastion settings; only present when the connection uses an SSH tunnel. */
  public record SshConfig(
      String host,
      int port,
      String username,
      SshAuthMethod authMethod,
      String privateKeyPath,                   // KEY only
      boolean strictHostKeyCheck
  ) {}
}
