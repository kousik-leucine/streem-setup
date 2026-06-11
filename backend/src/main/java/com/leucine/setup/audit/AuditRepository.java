package com.leucine.setup.audit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;

@Repository
public class AuditRepository {

  private final JdbcTemplate jdbc;

  public AuditRepository(@Qualifier("localStoreJdbc") JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final RowMapper<AuditEntry> ROW = (rs, n) -> new AuditEntry(
      rs.getLong("id"),
      rs.getString("connection_id"),
      rs.getString("operation"),
      rs.getString("payload_json"),
      rs.getString("sql_text"),
      AuditStatus.valueOf(rs.getString("status")),
      rs.getString("error"),
      rs.getString("operator"),
      rs.getLong("started_at"),
      (Long) rs.getObject("finished_at")
  );

  public long insert(AuditEntry e) {
    KeyHolder kh = new GeneratedKeyHolder();
    jdbc.update(con -> {
      PreparedStatement ps = con.prepareStatement("""
          INSERT INTO audit_log (connection_id, operation, payload_json, sql_text,
                                 status, error, operator, started_at, finished_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
          """, Statement.RETURN_GENERATED_KEYS);
      ps.setString(1, e.connectionId());
      ps.setString(2, e.operation());
      ps.setString(3, e.payloadJson());
      ps.setString(4, e.sqlText());
      ps.setString(5, e.status().name());
      ps.setString(6, e.error());
      ps.setString(7, e.operator());
      ps.setLong(8, e.startedAt());
      if (e.finishedAt() == null) ps.setNull(9, java.sql.Types.INTEGER);
      else ps.setLong(9, e.finishedAt());
      return ps;
    }, kh);
    return Objects.requireNonNull(kh.getKey()).longValue();
  }

  public void update(long id, AuditStatus status, String sqlText, String error, long finishedAt) {
    jdbc.update("""
        UPDATE audit_log SET status = ?, sql_text = ?, error = ?, finished_at = ?
        WHERE id = ?
        """, status.name(), sqlText, error, finishedAt, id);
  }

  public List<AuditEntry> findRecent(int limit) {
    return jdbc.query(
        "SELECT * FROM audit_log ORDER BY started_at DESC LIMIT ?",
        ROW, limit);
  }

  public List<AuditEntry> findForConnection(String connectionId, int limit) {
    return jdbc.query(
        "SELECT * FROM audit_log WHERE connection_id = ? ORDER BY started_at DESC LIMIT ?",
        ROW, connectionId, limit);
  }
}
