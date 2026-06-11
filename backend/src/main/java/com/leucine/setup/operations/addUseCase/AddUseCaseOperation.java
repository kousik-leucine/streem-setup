package com.leucine.setup.operations.addUseCase;

import com.leucine.setup.operations.AbstractSqlOperation;
import com.leucine.setup.operations.IdGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AddUseCaseOperation extends AbstractSqlOperation<AddUseCasePayload> {

  private static final long SYSTEM_USER_ID = 1L;

  @Override
  public String name() { return "ADD_USECASE"; }

  @Override
  protected List<String> preflight(AddUseCasePayload p, JdbcTemplate t) {
    List<String> warnings = new ArrayList<>();

    Integer facilityHit = t.queryForObject(
        "SELECT count(*) FROM facilities WHERE id = ? AND archived = false",
        Integer.class, p.facilityId());
    if (facilityHit == null || facilityHit == 0) {
      throw new IllegalArgumentException("Facility id " + p.facilityId() + " not found (or archived)");
    }

    Integer nameHit = t.queryForObject(
        "SELECT count(*) FROM use_cases WHERE name = ? AND archived = false",
        Integer.class, p.name());
    if (nameHit != null && nameHit > 0) {
      throw new IllegalArgumentException("Use case named \"" + p.name()
          + "\" already exists. Use map-usecase to map it to this facility instead.");
    }

    return warnings;
  }

  @Override
  protected Plan plan(AddUseCasePayload p, IdGenerator ids) {
    long useCaseId = ids.next();
    long now = useCaseId;
    String cardColor = (p.cardColor() != null && !p.cardColor().isBlank()) ? p.cardColor() : "#EDF5FF";
    String cardColorJson = "{\"card-color\": \"" + cardColor + "\"}";

    PlanBuilder b = new PlanBuilder().id("useCaseId", useCaseId);

    b.add("use_case",
        """
        INSERT INTO use_cases
        (id, name, label, description, order_tree, metadata, archived,
         created_by, created_at, modified_by, modified_at)
        VALUES (?, ?, ?, NULL, 1, ?::jsonb, false, ?, ?, ?, ?)
        """,
        List.of(useCaseId, p.name(), p.label(), cardColorJson,
            SYSTEM_USER_ID, now, SYSTEM_USER_ID, now));

    b.add("facility_use_case_mapping",
        """
        INSERT INTO facility_use_case_mapping
        (facilities_id, use_cases_id, quota, created_by, created_at, modified_by, modified_at)
        VALUES (?, ?, 0, ?, ?, ?, ?)
        """,
        List.of(p.facilityId(), useCaseId, SYSTEM_USER_ID, now, SYSTEM_USER_ID, now));

    int order = 1;
    List<Long> propertyIds = new ArrayList<>();
    for (AddUseCasePayload.Property prop : p.properties()) {
      long propertyId = ids.next();
      long mappingId = ids.next();
      propertyIds.add(propertyId);
      String placeholder = (prop.placeholder() != null && !prop.placeholder().isBlank())
          ? prop.placeholder() : prop.label();

      b.add("property: " + prop.name(),
          """
          INSERT INTO properties
          (id, use_cases_id, name, label, place_holder, order_tree, is_global, type,
           archived, created_by, created_at, modified_by, modified_at)
          VALUES (?, ?, ?, ?, ?, ?, false, 'CHECKLIST', false, ?, ?, ?, ?)
          """,
          List.of(propertyId, useCaseId, prop.name(), prop.label(), placeholder,
              order, SYSTEM_USER_ID, now, SYSTEM_USER_ID, now));

      b.add("facility_use_case_property_mapping: " + prop.name(),
          """
          INSERT INTO facility_use_case_property_mapping
          (id, facilities_id, use_cases_id, properties_id, label_alias, place_holder_alias,
           order_tree, is_mandatory, created_by, created_at, modified_by, modified_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          """,
          List.of(mappingId, p.facilityId(), useCaseId, propertyId,
              prop.label(), placeholder, order, prop.mandatory(),
              SYSTEM_USER_ID, now, SYSTEM_USER_ID, now));
      order++;
    }

    b.id("propertyIds", propertyIds);
    return b.build();
  }

  @Override
  protected String summary(AddUseCasePayload p, Plan plan) {
    return "Add use case \"%s\" with %d properties to facility %d"
        .formatted(p.name(), p.properties().size(), p.facilityId());
  }
}
