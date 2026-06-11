package com.leucine.setup.operations.setFeatureFlags;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leucine.setup.operations.AbstractSqlOperation;
import com.leucine.setup.operations.IdGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class SetFeatureFlagsOperation extends AbstractSqlOperation<SetFeatureFlagsPayload> {

  private final ObjectMapper json;

  public SetFeatureFlagsOperation(ObjectMapper json) {
    this.json = json;
  }

  @Override
  public String name() { return "SET_FEATURE_FLAGS"; }

  @Override
  protected List<String> preflight(SetFeatureFlagsPayload p, JdbcTemplate t) {
    List<String> warnings = new ArrayList<>();

    Integer orgHit = t.queryForObject(
        "SELECT count(*) FROM organisations WHERE id = ? AND archived = false",
        Integer.class, p.organisationId());
    if (orgHit == null || orgHit == 0) {
      throw new IllegalArgumentException("Organisation id " + p.organisationId() + " not found");
    }

    Integer settingsHit = t.queryForObject(
        "SELECT count(*) FROM organisation_settings WHERE organisations_id = ?",
        Integer.class, p.organisationId());
    if (settingsHit == null || settingsHit == 0) {
      throw new IllegalArgumentException(
          "organisation_settings row missing for organisation " + p.organisationId());
    }

    return warnings;
  }

  @Override
  protected Plan plan(SetFeatureFlagsPayload p, IdGenerator ids) {
    String mergedFragment = toJsonString(p.flags());
    long now = ids.next();

    return new PlanBuilder()
        .id("flagsMerged", p.flags())
        .add("organisation_settings.feature_flags merge",
            """
            UPDATE organisation_settings
            SET feature_flags = COALESCE(feature_flags, '{}'::jsonb) || ?::jsonb,
                modified_at = ?
            WHERE organisations_id = ?
            """,
            List.of(mergedFragment, now, p.organisationId()))
        .build();
  }

  @Override
  protected String summary(SetFeatureFlagsPayload p, Plan plan) {
    return "Merge %d feature flag(s) into organisation %d settings"
        .formatted(p.flags().size(), p.organisationId());
  }

  private String toJsonString(Map<String, Boolean> flags) {
    try {
      return json.writeValueAsString(flags);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize flags", e);
    }
  }
}
