package com.leucine.setup.crypto;

import com.leucine.setup.store.LocalStoreProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Set;
import javax.crypto.spec.SecretKeySpec;

/**
 * Holds the symmetric key used to encrypt connection passwords in the local store.
 *
 * v1: key is generated once on first run and saved to ~/.streem-setup/key (0600 on POSIX).
 *     This protects against casual snooping of store.db, same threat model as ~/.pgpass.
 *
 * Later: replace with a master-password-derived key (PBKDF2) entered at app launch.
 */
@Component
public class LocalKeyStore {

  private static final Logger log = LoggerFactory.getLogger(LocalKeyStore.class);
  private static final int KEY_BYTES = 32;            // AES-256

  private final Path keyPath;
  private SecretKeySpec key;

  public LocalKeyStore(LocalStoreProperties props) {
    Path storeDb = Paths.get(props.path());
    this.keyPath = storeDb.getParent().resolve("key");
  }

  @PostConstruct
  void load() throws IOException {
    byte[] raw;
    if (Files.exists(keyPath)) {
      raw = Files.readAllBytes(keyPath);
      if (raw.length != KEY_BYTES) {
        throw new IllegalStateException("Corrupt key file at " + keyPath + " (expected " + KEY_BYTES + " bytes)");
      }
      log.info("Loaded local encryption key from {}", keyPath);
    } else {
      raw = new byte[KEY_BYTES];
      new SecureRandom().nextBytes(raw);
      Files.write(keyPath, raw);
      tryRestrictPermissions(keyPath);
      log.info("Generated new local encryption key at {}", keyPath);
    }
    this.key = new SecretKeySpec(raw, "AES");
  }

  public SecretKeySpec key() {
    return key;
  }

  private static void tryRestrictPermissions(Path p) {
    try {
      Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
      Files.setPosixFilePermissions(p, perms);
    } catch (UnsupportedOperationException | IOException e) {
      // Windows or other non-POSIX FS: rely on user profile permissions
      log.debug("Could not set POSIX permissions on {} ({})", p, e.getMessage());
    }
  }
}
