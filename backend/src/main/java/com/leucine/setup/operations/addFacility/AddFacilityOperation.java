package com.leucine.setup.operations.addFacility;

import com.leucine.setup.operations.AbstractSqlOperation;
import com.leucine.setup.operations.IdGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AddFacilityOperation extends AbstractSqlOperation<AddFacilityPayload> {

  private static final long SYSTEM_USER_ID = 1L;

  @Override
  public String name() { return "ADD_FACILITY"; }

  @Override
  protected List<String> preflight(AddFacilityPayload p, JdbcTemplate t) {
    List<String> warnings = new ArrayList<>();
    Integer orgHit = t.queryForObject(
        "SELECT count(*) FROM organisations WHERE id = ? AND archived = false",
        Integer.class, p.organisationId());
    if (orgHit == null || orgHit == 0) {
      throw new IllegalArgumentException("Organisation id " + p.organisationId() + " not found (or archived)");
    }

    Integer dupHit = t.queryForObject(
        "SELECT count(*) FROM facilities WHERE name = ? AND organisations_id = ? AND archived = false",
        Integer.class, p.name(), p.organisationId());
    if (dupHit != null && dupHit > 0) {
      warnings.add("Facility named \"" + p.name() + "\" already exists in this organisation; will be added anyway");
    }
    return warnings;
  }

  @Override
  protected Plan plan(AddFacilityPayload p, IdGenerator ids) {
    long facilityId = ids.next();
    long now = facilityId;

    return new PlanBuilder()
        .id("facilityId", facilityId)
        .add("facility",
            """
            INSERT INTO facilities
            (id, name, organisations_id, created_at, modified_at, archived,
             created_by, date_format, date_time_format, modified_by, time_format, time_zone)
            VALUES (?, ?, ?, ?, ?, false, ?, ?, ?, ?, ?, ?)
            """,
            List.of(facilityId, p.name(), p.organisationId(), now, now,
                SYSTEM_USER_ID,
                or(p.dateFormat(), "MMM dd, yyyy"),
                or(p.dateTimeFormat(), "MMM dd, yyyy HH:mm"),
                SYSTEM_USER_ID,
                or(p.timeFormat(), "HH:mm"),
                p.timezone()))
        .add("organisation_facilities_mapping",
            """
            INSERT INTO organisation_facilities_mapping
            (facilities_id, organisations_id, created_at, created_by)
            VALUES (?, ?, ?, ?)
            """,
            List.of(facilityId, p.organisationId(), now, SYSTEM_USER_ID))
        .build();
  }

  @Override
  protected String summary(AddFacilityPayload p, Plan plan) {
    return "Add facility \"%s\" to organisation %d".formatted(p.name(), p.organisationId());
  }

  private static String or(String v, String def) {
    return (v != null && !v.isBlank()) ? v : def;
  }
}
