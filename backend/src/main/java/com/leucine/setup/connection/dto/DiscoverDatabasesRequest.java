package com.leucine.setup.connection.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Connects to a Postgres server with these (as-yet unsaved) credentials and lists the
 * databases it can see, so the connection form can offer a pick-list instead of a free-text
 * database field. Connects to {@code maintenanceDatabase} (default {@code postgres}) since the
 * target database is the thing being chosen. Secrets are used in-memory only and never stored.
 */
public record DiscoverDatabasesRequest(
    @NotBlank String host,
    @Min(1) int port,
    @NotBlank String username,
    @NotBlank String password,
    String sslMode,
    String maintenanceDatabase,              // null/blank -> "postgres"
    @Valid SshConfigDto ssh                   // null when no SSH tunnel
) {
  public String connectDatabase() {
    return (maintenanceDatabase == null || maintenanceDatabase.isBlank())
        ? "postgres" : maintenanceDatabase;
  }
}
