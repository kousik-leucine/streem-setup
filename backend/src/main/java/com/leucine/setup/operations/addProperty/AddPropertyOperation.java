package com.leucine.setup.operations.addProperty;

import com.leucine.setup.operations.AbstractSqlOperation;
import com.leucine.setup.operations.IdGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AddPropertyOperation extends AbstractSqlOperation<AddPropertyPayload> {

  private static final long SYSTEM_USER_ID = 1L;

  @Override
  public String name() { return "ADD_PROPERTY"; }

  @Override
  protected List<String> preflight(AddPropertyPayload p, JdbcTemplate t) {
    List<String> warnings = new ArrayList<>();

    Integer ucHit = t.queryForObject(
        "SELECT count(*) FROM use_cases WHERE id = ? AND archived = false",
        Integer.class, p.useCaseId());
    if (ucHit == null || ucHit == 0) {
      throw new IllegalArgumentException("Use case id " + p.useCaseId() + " not found (or archived)");
    }

    Integer facHit = t.queryForObject(
        "SELECT count(*) FROM facilities WHERE id = ? AND archived = false",
        Integer.class, p.facilityId());
    if (facHit == null || facHit == 0) {
      throw new IllegalArgumentException("Facility id " + p.facilityId() + " not found (or archived)");
    }

    Integer mapHit = t.queryForObject(
        "SELECT count(*) FROM facility_use_case_mapping WHERE facilities_id = ? AND use_cases_id = ?",
        Integer.class, p.facilityId(), p.useCaseId());
    if (mapHit == null || mapHit == 0) {
      throw new IllegalArgumentException(
          "Facility " + p.facilityId() + " is not mapped to use case " + p.useCaseId()
              + ". Run map-usecase first.");
    }

    Integer nameHit = t.queryForObject(
        "SELECT count(*) FROM properties WHERE use_cases_id = ? AND name = ? AND archived = false",
        Integer.class, p.useCaseId(), p.name());
    if (nameHit != null && nameHit > 0) {
      throw new IllegalArgumentException("Property \"" + p.name()
          + "\" already exists on use case " + p.useCaseId());
    }

    return warnings;
  }

  @Override
  protected Plan plan(AddPropertyPayload p, IdGenerator ids) {
    long propertyId = ids.next();
    long mappingId = ids.next();
    long now = propertyId;
    String placeholder = (p.placeholder() != null && !p.placeholder().isBlank())
        ? p.placeholder() : p.label();

    // place property at end of the order tree
    PlanBuilder b = new PlanBuilder()
        .id("propertyId", propertyId)
        .id("mappingId", mappingId);

    b.add("property",
        """
        INSERT INTO properties
        (id, use_cases_id, name, label, place_holder, order_tree, is_global, type,
         archived, created_by, created_at, modified_by, modified_at)
        VALUES (?, ?, ?, ?, ?,
                COALESCE((SELECT MAX(order_tree) + 1 FROM properties WHERE use_cases_id = ?), 1),
                false, 'CHECKLIST', false, ?, ?, ?, ?)
        """,
        List.of(propertyId, p.useCaseId(), p.name(), p.label(), placeholder,
            p.useCaseId(), SYSTEM_USER_ID, now, SYSTEM_USER_ID, now));

    b.add("facility_use_case_property_mapping",
        """
        INSERT INTO facility_use_case_property_mapping
        (id, facilities_id, use_cases_id, properties_id, label_alias, place_holder_alias,
         order_tree, is_mandatory, created_by, created_at, modified_by, modified_at)
        VALUES (?, ?, ?, ?, ?, ?,
                COALESCE((SELECT MAX(order_tree) + 1 FROM facility_use_case_property_mapping
                          WHERE facilities_id = ? AND use_cases_id = ?), 1),
                ?, ?, ?, ?, ?)
        """,
        List.of(mappingId, p.facilityId(), p.useCaseId(), propertyId,
            p.label(), placeholder,
            p.facilityId(), p.useCaseId(),
            p.mandatory(), SYSTEM_USER_ID, now, SYSTEM_USER_ID, now));

    return b.build();
  }

  @Override
  protected String summary(AddPropertyPayload p, Plan plan) {
    return "Add property \"%s\" to use case %d on facility %d"
        .formatted(p.name(), p.useCaseId(), p.facilityId());
  }
}
