package com.leucine.setup.store;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Lightweight idempotent migrator for the local SQLite store. The
 * {@code schema.sql} runner creates tables when missing; this class adds any
 * columns introduced after the table already exists on an operator's machine.
 *
 * SQLite's ADD COLUMN doesn't support IF NOT EXISTS, so we inspect
 * {@code PRAGMA table_info(...)} first.
 */
@Component
public class LocalStoreMigrator {

  private static final Logger log = LoggerFactory.getLogger(LocalStoreMigrator.class);
  private final JdbcTemplate jdbc;

  public LocalStoreMigrator(@Qualifier("localStoreJdbc") JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @PostConstruct
  void migrate() {
    addColumnsIfMissing("connections", expectedConnectionColumns());
  }

  private void addColumnsIfMissing(String table, Map<String, String> expected) {
    Set<String> existing = jdbc.query(
        "PRAGMA table_info(" + table + ")",
        (rs, n) -> rs.getString("name")
    ).stream().collect(Collectors.toSet());

    for (Map.Entry<String, String> col : expected.entrySet()) {
      if (!existing.contains(col.getKey())) {
        String ddl = "ALTER TABLE " + table + " ADD COLUMN " + col.getKey() + " " + col.getValue();
        log.info("Local store migration: {}", ddl);
        jdbc.execute(ddl);
      }
    }
  }

  /** Columns + their SQLite types/defaults, in declaration order. */
  private static Map<String, String> expectedConnectionColumns() {
    Map<String, String> m = new LinkedHashMap<>();
    m.put("use_ssh_tunnel",            "INTEGER NOT NULL DEFAULT 0");
    m.put("ssh_host",                  "TEXT");
    m.put("ssh_port",                  "INTEGER");
    m.put("ssh_username",              "TEXT");
    m.put("ssh_auth_method",           "TEXT");
    m.put("ssh_password_enc",          "BLOB");
    m.put("ssh_private_key_path",      "TEXT");
    m.put("ssh_key_passphrase_enc",    "BLOB");
    m.put("ssh_strict_host_key_check", "INTEGER NOT NULL DEFAULT 0");
    return m;
  }

  // suppress unused warning
  @SuppressWarnings("unused")
  private static final List<String> NO_OP = List.of();
}
