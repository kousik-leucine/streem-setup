package com.leucine.setup.connection;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ConnectionRepository {

  private final JdbcTemplate jdbc;

  public ConnectionRepository(@Qualifier("localStoreJdbc") JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final RowMapper<Connection> ROW_MAPPER = (rs, n) -> {
    Connection.SshConfig ssh = null;
    boolean useSsh = rs.getInt("use_ssh_tunnel") != 0;
    if (useSsh) {
      ssh = new Connection.SshConfig(
          rs.getString("ssh_host"),
          rs.getInt("ssh_port"),
          rs.getString("ssh_username"),
          SshAuthMethod.valueOf(rs.getString("ssh_auth_method")),
          rs.getString("ssh_private_key_path"),
          rs.getInt("ssh_strict_host_key_check") != 0
      );
    }
    return new Connection(
        rs.getString("id"),
        rs.getString("name"),
        rs.getString("host"),
        rs.getInt("port"),
        rs.getString("database"),
        rs.getString("username"),
        rs.getString("ssl_mode"),
        Environment.valueOf(rs.getString("environment")),
        rs.getString("notes"),
        rs.getLong("created_at"),
        (Long) rs.getObject("last_used_at"),
        ssh
    );
  };

  public List<Connection> findAll() {
    return jdbc.query(
        "SELECT * FROM connections ORDER BY environment, name",
        ROW_MAPPER
    );
  }

  public Optional<Connection> findById(String id) {
    try {
      return Optional.ofNullable(jdbc.queryForObject(
          "SELECT * FROM connections WHERE id = ?", ROW_MAPPER, id));
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }
  }

  public byte[] findPasswordById(String id) {
    return jdbc.queryForObject(
        "SELECT password_enc FROM connections WHERE id = ?", byte[].class, id);
  }

  public byte[] findSshPasswordById(String id) {
    return jdbc.queryForObject(
        "SELECT ssh_password_enc FROM connections WHERE id = ?", byte[].class, id);
  }

  public byte[] findSshKeyPassphraseById(String id) {
    return jdbc.queryForObject(
        "SELECT ssh_key_passphrase_enc FROM connections WHERE id = ?", byte[].class, id);
  }

  public void insert(Connection c, EncryptedSecrets secrets) {
    jdbc.update("""
        INSERT INTO connections
        (id, name, host, port, database, username, password_enc,
         ssl_mode, environment, notes, created_at,
         use_ssh_tunnel, ssh_host, ssh_port, ssh_username, ssh_auth_method,
         ssh_password_enc, ssh_private_key_path, ssh_key_passphrase_enc,
         ssh_strict_host_key_check)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        c.id(), c.name(), c.host(), c.port(), c.database(), c.username(), secrets.dbPassword(),
        c.sslMode(), c.environment().name(), c.notes(), c.createdAt(),
        c.ssh() != null ? 1 : 0,
        c.ssh() != null ? c.ssh().host() : null,
        c.ssh() != null ? c.ssh().port() : null,
        c.ssh() != null ? c.ssh().username() : null,
        c.ssh() != null ? c.ssh().authMethod().name() : null,
        secrets.sshPassword(),
        c.ssh() != null ? c.ssh().privateKeyPath() : null,
        secrets.sshKeyPassphrase(),
        c.ssh() != null && c.ssh().strictHostKeyCheck() ? 1 : 0);
  }

  public void update(Connection c, EncryptedSecrets secrets) {
    boolean updateDbPw = secrets.dbPassword() != null;
    boolean updateSshPw = secrets.sshPassword() != null;
    boolean updateSshPass = secrets.sshKeyPassphrase() != null;
    StringBuilder sql = new StringBuilder("""
        UPDATE connections
        SET name = ?, host = ?, port = ?, database = ?, username = ?,
            ssl_mode = ?, environment = ?, notes = ?,
            use_ssh_tunnel = ?, ssh_host = ?, ssh_port = ?, ssh_username = ?,
            ssh_auth_method = ?, ssh_private_key_path = ?, ssh_strict_host_key_check = ?
        """);
    java.util.List<Object> params = new java.util.ArrayList<>();
    params.add(c.name()); params.add(c.host()); params.add(c.port()); params.add(c.database());
    params.add(c.username()); params.add(c.sslMode()); params.add(c.environment().name()); params.add(c.notes());
    params.add(c.ssh() != null ? 1 : 0);
    params.add(c.ssh() != null ? c.ssh().host() : null);
    params.add(c.ssh() != null ? c.ssh().port() : null);
    params.add(c.ssh() != null ? c.ssh().username() : null);
    params.add(c.ssh() != null ? c.ssh().authMethod().name() : null);
    params.add(c.ssh() != null ? c.ssh().privateKeyPath() : null);
    params.add(c.ssh() != null && c.ssh().strictHostKeyCheck() ? 1 : 0);

    if (updateDbPw)   { sql.append(", password_enc = ?");           params.add(secrets.dbPassword()); }
    if (updateSshPw)  { sql.append(", ssh_password_enc = ?");       params.add(secrets.sshPassword()); }
    if (updateSshPass){ sql.append(", ssh_key_passphrase_enc = ?"); params.add(secrets.sshKeyPassphrase()); }

    // When SSH is disabled, clear any leftover encrypted SSH secrets so they
    // can't be reused if the user re-enables SSH with different credentials.
    if (c.ssh() == null) {
      sql.append(", ssh_password_enc = NULL, ssh_key_passphrase_enc = NULL");
    }

    sql.append(" WHERE id = ?");
    params.add(c.id());

    jdbc.update(sql.toString(), params.toArray());
  }

  public void touchLastUsed(String id, long when) {
    jdbc.update("UPDATE connections SET last_used_at = ? WHERE id = ?", when, id);
  }

  public int delete(String id) {
    return jdbc.update("DELETE FROM connections WHERE id = ?", id);
  }

  /** Bundle of pre-encrypted secret bytes for insert/update. Any can be null to skip. */
  public record EncryptedSecrets(byte[] dbPassword, byte[] sshPassword, byte[] sshKeyPassphrase) {}
}
