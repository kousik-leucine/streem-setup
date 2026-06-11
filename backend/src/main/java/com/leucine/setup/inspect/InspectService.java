package com.leucine.setup.inspect;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leucine.setup.connection.Connection;
import com.leucine.setup.connection.ConnectionService;
import com.leucine.setup.datasource.TargetConnection;
import com.leucine.setup.datasource.TargetDataSourceFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Read-only queries against a target Postgres to populate UI dropdowns. */
@Service
public class InspectService {

  private final ConnectionService connections;
  private final TargetDataSourceFactory dsFactory;
  private final ObjectMapper json;

  public InspectService(ConnectionService connections, TargetDataSourceFactory dsFactory,
                        ObjectMapper json) {
    this.connections = connections;
    this.dsFactory = dsFactory;
    this.json = json;
  }

  public List<NamedRow> organisations(String connectionId) {
    return withTarget(connectionId, t -> t.query("""
        SELECT id, name, fqdn AS extra FROM organisations
        WHERE archived = false
        ORDER BY name
        """, (rs, n) -> new NamedRow(rs.getLong("id"), rs.getString("name"), rs.getString("extra"))));
  }

  public List<NamedRow> facilities(String connectionId, long organisationId) {
    return withTarget(connectionId, t -> t.query("""
        SELECT id, name, time_zone AS extra FROM facilities
        WHERE organisations_id = ? AND archived = false
        ORDER BY name
        """, (rs, n) -> new NamedRow(rs.getLong("id"), rs.getString("name"), rs.getString("extra")),
        organisationId));
  }

  public List<NamedRow> useCasesForFacility(String connectionId, long facilityId) {
    return withTarget(connectionId, t -> t.query("""
        SELECT uc.id, uc.name, uc.label AS extra
        FROM use_cases uc
        JOIN facility_use_case_mapping m ON m.use_cases_id = uc.id
        WHERE m.facilities_id = ? AND uc.archived = false
        ORDER BY uc.name
        """, (rs, n) -> new NamedRow(rs.getLong("id"), rs.getString("name"), rs.getString("extra")),
        facilityId));
  }

  public List<NamedRow> useCasesUnmappedToFacility(String connectionId, long facilityId) {
    return withTarget(connectionId, t -> t.query("""
        SELECT uc.id, uc.name, uc.label AS extra
        FROM use_cases uc
        WHERE uc.archived = false
          AND NOT EXISTS (
            SELECT 1 FROM facility_use_case_mapping m
            WHERE m.use_cases_id = uc.id AND m.facilities_id = ?
          )
        ORDER BY uc.name
        """, (rs, n) -> new NamedRow(rs.getLong("id"), rs.getString("name"), rs.getString("extra")),
        facilityId));
  }

  /**
   * Current feature_flags JSONB on organisation_settings for the org, parsed
   * to a Map so the controller can return JSON the frontend can consume directly.
   */
  public Map<String, Object> featureFlags(String connectionId, long organisationId) {
    return withTarget(connectionId, t -> {
      String raw;
      try {
        raw = t.queryForObject(
            "SELECT COALESCE(feature_flags, '{}'::jsonb)::text FROM organisation_settings WHERE organisations_id = ?",
            String.class, organisationId);
      } catch (org.springframework.dao.EmptyResultDataAccessException e) {
        return null;
      }
      if (raw == null) return Map.of();
      try {
        return json.readValue(raw, new TypeReference<Map<String, Object>>() {});
      } catch (Exception e) {
        throw new IllegalStateException("Failed to parse feature_flags JSON: " + e.getMessage(), e);
      }
    });
  }

  public List<NamedRow> propertiesForUseCase(String connectionId, long useCaseId) {
    return withTarget(connectionId, t -> t.query("""
        SELECT id, name, label AS extra
        FROM properties
        WHERE use_cases_id = ? AND archived = false
        ORDER BY order_tree
        """, (rs, n) -> new NamedRow(rs.getLong("id"), rs.getString("name"), rs.getString("extra")),
        useCaseId));
  }

  /**
   * Opens a target (with SSH tunnel if configured), passes its JdbcTemplate to {@code fn},
   * closes the tunnel after. All inspect queries route through here.
   */
  private <T> T withTarget(String connectionId, Function<JdbcTemplate, T> fn) {
    Connection c = connections.get(connectionId)
        .orElseThrow(() -> new IllegalArgumentException("Connection not found: " + connectionId));
    try (TargetConnection tc = dsFactory.open(c, connections.decryptedSecrets(connectionId))) {
      return fn.apply(tc.jdbc());
    }
  }

  public record NamedRow(long id, String name, String extra) {}
}
