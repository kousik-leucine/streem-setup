package com.leucine.setup.operations.mapProperty;

import com.leucine.setup.operations.AbstractSqlOperation;
import com.leucine.setup.operations.IdGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MapPropertyOperation extends AbstractSqlOperation<MapPropertyPayload> {

  private static final long SYSTEM_USER_ID = 1L;

  @Override
  public String name() { return "MAP_PROPERTY"; }

  @Override
  protected List<String> preflight(MapPropertyPayload p, JdbcTemplate t) {
    List<String> warnings = new ArrayList<>();

    Integer propRow = t.queryForObject(
        """
        SELECT count(*) FROM properties pr
        WHERE pr.id = ? AND pr.use_cases_id = ? AND pr.archived = false
        """,
        Integer.class, p.propertyId(), p.useCaseId());
    if (propRow == null || propRow == 0) {
      throw new IllegalArgumentException(
          "Property " + p.propertyId() + " does not belong to use case " + p.useCaseId());
    }

    Integer fucMap = t.queryForObject(
        "SELECT count(*) FROM facility_use_case_mapping WHERE facilities_id = ? AND use_cases_id = ?",
        Integer.class, p.facilityId(), p.useCaseId());
    if (fucMap == null || fucMap == 0) {
      throw new IllegalArgumentException(
          "Facility " + p.facilityId() + " is not mapped to use case " + p.useCaseId()
              + ". Run map-usecase first.");
    }

    Integer existing = t.queryForObject(
        """
        SELECT count(*) FROM facility_use_case_property_mapping
        WHERE facilities_id = ? AND use_cases_id = ? AND properties_id = ?
        """,
        Integer.class, p.facilityId(), p.useCaseId(), p.propertyId());
    if (existing != null && existing > 0) {
      throw new IllegalArgumentException(
          "Property " + p.propertyId() + " is already mapped to facility " + p.facilityId());
    }

    return warnings;
  }

  @Override
  protected Plan plan(MapPropertyPayload p, IdGenerator ids) {
    long mappingId = ids.next();
    long now = mappingId;

    return new PlanBuilder()
        .id("mappingId", mappingId)
        .add("facility_use_case_property_mapping",
            """
            INSERT INTO facility_use_case_property_mapping
            (id, facilities_id, use_cases_id, properties_id, label_alias, place_holder_alias,
             order_tree, is_mandatory, created_by, created_at, modified_by, modified_at)
            SELECT
              ?, ?, ?, ?,
              COALESCE(?, pr.label),
              COALESCE(?, pr.place_holder),
              COALESCE((SELECT MAX(order_tree) + 1 FROM facility_use_case_property_mapping
                        WHERE facilities_id = ? AND use_cases_id = ?), 1),
              ?, ?, ?, ?, ?
            FROM properties pr WHERE pr.id = ?
            """,
            List.of(mappingId, p.facilityId(), p.useCaseId(), p.propertyId(),
                emptyToNull(p.labelAlias()),
                emptyToNull(p.placeholderAlias()),
                p.facilityId(), p.useCaseId(),
                p.mandatory(), SYSTEM_USER_ID, now, SYSTEM_USER_ID, now,
                p.propertyId()))
        .build();
  }

  @Override
  protected String summary(MapPropertyPayload p, Plan plan) {
    return "Map property %d on use case %d to facility %d"
        .formatted(p.propertyId(), p.useCaseId(), p.facilityId());
  }

  private static String emptyToNull(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }
}
