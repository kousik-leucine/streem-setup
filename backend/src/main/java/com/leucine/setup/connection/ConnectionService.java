package com.leucine.setup.connection;

import com.leucine.setup.connection.dto.CreateConnectionRequest;
import com.leucine.setup.connection.dto.DiscoverDatabasesRequest;
import com.leucine.setup.connection.dto.SshConfigDto;
import com.leucine.setup.connection.dto.TestConnectionResult;
import com.leucine.setup.connection.dto.UpdateConnectionRequest;
import com.leucine.setup.crypto.AesGcm;
import com.leucine.setup.datasource.SshTunnelPool;
import com.leucine.setup.datasource.TargetConnection;
import com.leucine.setup.datasource.TargetDataSourceFactory;
import com.leucine.setup.datasource.TargetDataSourceFactory.TargetSecrets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ConnectionService {

  private static final Logger log = LoggerFactory.getLogger(ConnectionService.class);

  private final ConnectionRepository repo;
  private final AesGcm crypto;
  private final TargetDataSourceFactory dsFactory;
  private final SshTunnelPool tunnelPool;

  public ConnectionService(ConnectionRepository repo, AesGcm crypto,
                           TargetDataSourceFactory dsFactory,
                           SshTunnelPool tunnelPool) {
    this.repo = repo;
    this.crypto = crypto;
    this.dsFactory = dsFactory;
    this.tunnelPool = tunnelPool;
  }

  public List<Connection> list() {
    return repo.findAll();
  }

  public Optional<Connection> get(String id) {
    return repo.findById(id);
  }

  public Connection create(CreateConnectionRequest req) {
    Connection.SshConfig ssh = sshConfigFromDto(req.ssh());
    Connection c = new Connection(
        UUID.randomUUID().toString(),
        req.name(), req.host(), req.port(), req.database(), req.username(),
        req.sslMode(), req.environment(), req.notes(),
        System.currentTimeMillis(), null,
        ssh
    );

    byte[] dbPw = crypto.encrypt(req.password());
    byte[] sshPw = encryptIfPresent(req.ssh() != null ? req.ssh().password() : null);
    byte[] sshPass = encryptIfPresent(req.ssh() != null ? req.ssh().keyPassphrase() : null);

    repo.insert(c, new ConnectionRepository.EncryptedSecrets(dbPw, sshPw, sshPass));
    log.info("Created connection {} ({})", c.name(), c.environment());
    return c;
  }

  public Connection update(String id, UpdateConnectionRequest req) {
    Connection existing = repo.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Connection not found: " + id));

    Connection.SshConfig ssh = sshConfigFromDto(req.ssh());
    Connection updated = new Connection(
        existing.id(),
        req.name(), req.host(), req.port(), req.database(), req.username(),
        req.sslMode(), req.environment(), req.notes(),
        existing.createdAt(), existing.lastUsedAt(),
        ssh
    );

    byte[] dbPw = encryptIfPresent(req.password());
    byte[] sshPw = encryptIfPresent(req.ssh() != null ? req.ssh().password() : null);
    byte[] sshPass = encryptIfPresent(req.ssh() != null ? req.ssh().keyPassphrase() : null);

    repo.update(updated, new ConnectionRepository.EncryptedSecrets(dbPw, sshPw, sshPass));
    tunnelPool.invalidate(id);                     // re-keyed bastion shouldn't get a stale tunnel
    log.info("Updated connection {}", updated.name());
    return updated;
  }

  public boolean delete(String id) {
    tunnelPool.invalidate(id);
    return repo.delete(id) > 0;
  }

  /**
   * Bundle of decrypted secrets for one connection. Caller is expected to use
   * within a try-with-resources scope around {@link TargetDataSourceFactory#open}.
   */
  public TargetSecrets decryptedSecrets(String id) {
    String dbPw = crypto.decrypt(repo.findPasswordById(id));
    String sshPw = decryptIfPresent(repo.findSshPasswordById(id));
    String sshPass = decryptIfPresent(repo.findSshKeyPassphraseById(id));
    return new TargetSecrets(dbPw, sshPw, sshPass);
  }

  public TestConnectionResult test(String id) {
    Connection conn = repo.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Connection not found: " + id));
    TargetSecrets secrets = decryptedSecrets(id);

    try (TargetConnection tc = dsFactory.open(conn, secrets)) {
      String version = tc.jdbc().queryForObject("SELECT version()", String.class);
      repo.touchLastUsed(id, System.currentTimeMillis());
      return TestConnectionResult.success(version);
    } catch (Exception e) {
      Throwable root = rootCause(e);
      log.warn("Test connection failed for {}: {}", conn.name(), root.getMessage());
      return TestConnectionResult.failure(root.getClass().getSimpleName() + ": " + root.getMessage());
    }
  }

  /**
   * Lists the databases visible on the server described by {@code req}, for populating the
   * connection form's database picker before the connection is saved. Opens a one-off,
   * non-pooled handle to the maintenance database and closes it (and any SSH tunnel)
   * immediately, so this leaves nothing running against the server.
   */
  public List<String> listDatabases(DiscoverDatabasesRequest req) {
    Connection.SshConfig ssh = sshConfigFromDto(req.ssh());
    // Transient, unsaved connection so we reuse the SSH/SSL plumbing; database is overridden
    // at open() with the maintenance DB since the real target is what we're discovering.
    Connection probe = new Connection(
        "discover", "discover", req.host(), req.port(), req.connectDatabase(),
        req.username(), req.sslMode(), null, null,
        System.currentTimeMillis(), null, ssh
    );
    TargetSecrets secrets = new TargetSecrets(
        req.password(),
        req.ssh() != null ? req.ssh().password() : null,
        req.ssh() != null ? req.ssh().keyPassphrase() : null
    );

    try (TargetConnection tc = dsFactory.openEphemeral(probe, secrets, req.connectDatabase())) {
      return tc.jdbc().queryForList("""
          SELECT datname FROM pg_database
          WHERE datistemplate = false AND datallowconn = true
          ORDER BY datname
          """, String.class);
    }
  }

  // --- helpers ------------------------------------------------------------

  private Connection.SshConfig sshConfigFromDto(SshConfigDto dto) {
    if (dto == null) return null;
    return new Connection.SshConfig(
        dto.host(), dto.port(), dto.username(),
        dto.authMethod(), dto.privateKeyPath(),
        dto.strictHostKeyCheck()
    );
  }

  private byte[] encryptIfPresent(String plaintext) {
    return (plaintext != null && !plaintext.isBlank()) ? crypto.encrypt(plaintext) : null;
  }

  private String decryptIfPresent(byte[] enc) {
    return (enc != null && enc.length > 0) ? crypto.decrypt(enc) : null;
  }

  private static Throwable rootCause(Throwable t) {
    Throwable cur = t;
    while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
    return cur;
  }
}
