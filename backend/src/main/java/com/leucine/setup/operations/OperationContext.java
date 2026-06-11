package com.leucine.setup.operations;

import com.leucine.setup.connection.Connection;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Per-request context an operation needs: the target Postgres handle, the
 * connection it points at (for audit), and a fresh ID generator.
 */
public record OperationContext(
    Connection connection,
    JdbcTemplate target,
    IdGenerator ids
) {
  public static OperationContext of(Connection connection, JdbcTemplate target) {
    return new OperationContext(connection, target, new IdGenerator());
  }
}
