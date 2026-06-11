package com.leucine.setup.operations.mapUseCase;

import com.leucine.setup.operations.AbstractSqlOperation;
import com.leucine.setup.operations.IdGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MapUseCaseOperation extends AbstractSqlOperation<MapUseCasePayload> {

  private static final long SYSTEM_USER_ID = 1L;

  @Override
  public String name() { return "MAP_USECASE"; }

  @Override
  protected List<String> preflight(MapUseCasePayload p, JdbcTemplate t) {
    List<String> warnings = new ArrayList<>();

    Integer facHit = t.queryForObject(
        "SELECT count(*) FROM facilities WHERE id = ? AND archived = false",
        Integer.class, p.facilityId());
    if (facHit == null || facHit == 0) {
      throw new IllegalArgumentException("Facility id " + p.facilityId() + " not found");
    }

    Integer ucHit = t.queryForObject(
        "SELECT count(*) FROM use_cases WHERE id = ? AND archived = false",
        Integer.class, p.useCaseId());
    if (ucHit == null || ucHit == 0) {
      throw new IllegalArgumentException("Use case id " + p.useCaseId() + " not found");
    }

    Integer mapHit = t.queryForObject(
        "SELECT count(*) FROM facility_use_case_mapping WHERE facilities_id = ? AND use_cases_id = ?",
        Integer.class, p.facilityId(), p.useCaseId());
    if (mapHit != null && mapHit > 0) {
      throw new IllegalArgumentException(
          "Facility " + p.facilityId() + " is already mapped to use case " + p.useCaseId());
    }

    return warnings;
  }

  @Override
  protected Plan plan(MapUseCasePayload p, IdGenerator ids) {
    long now = ids.next();
    PlanBuilder b = new PlanBuilder()
        .id("facilityId", p.facilityId())
        .id("useCaseId", p.useCaseId());

    b.add("facility_use_case_mapping",
        """
        INSERT INTO facility_use_case_mapping
        (facilities_id, use_cases_id, quota, created_by, created_at, modified_by, modified_at)
        VALUES (?, ?, 0, ?, ?, ?, ?)
        """,
        List.of(p.facilityId(), p.useCaseId(), SYSTEM_USER_ID, now, SYSTEM_USER_ID, now));

    if (p.includePropertiesOrTrue()) {
      // We can't fetch properties at plan-build time (no DB access here without a
      // JdbcTemplate). Emit an INSERT-from-SELECT that copies every property of the
      // use case into facility_use_case_property_mapping. order_tree mirrors the
      // property's own order_tree; ids generated via the same counter offsets used
      // upstream — but here we let Postgres pick by combining a base id + row_number.
      b.add("facility_use_case_property_mapping (bulk)",
          """
          INSERT INTO facility_use_case_property_mapping
          (id, facilities_id, use_cases_id, properties_id, label_alias, place_holder_alias,
           order_tree, is_mandatory, created_by, created_at, modified_by, modified_at)
          SELECT
            ? + ROW_NUMBER() OVER (ORDER BY pr.order_tree),
            ?, ?, pr.id, pr.label, pr.place_holder, pr.order_tree, false,
            ?, ?, ?, ?
          FROM properties pr
          WHERE pr.use_cases_id = ? AND pr.archived = false
          """,
          List.of(ids.next(), p.facilityId(), p.useCaseId(),
              SYSTEM_USER_ID, now, SYSTEM_USER_ID, now,
              p.useCaseId()));
    }

    return b.build();
  }

  @Override
  protected String summary(MapUseCasePayload p, Plan plan) {
    return "Map use case %d to facility %d%s".formatted(p.useCaseId(), p.facilityId(),
        p.includePropertiesOrTrue() ? " (with all properties)" : "");
  }
}
