package com.leucine.setup.operations.addLicense;

import com.leucine.setup.operations.AbstractSqlOperation;
import com.leucine.setup.operations.IdGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates one row in {@code licenses} per (facility, use case) pair.
 *
 * Everything about a license except product/type is a knob the platform team
 * flips while reproducing subscription states, so the payload carries a state
 * preset (GRACE, INTIMATE, ...) that derives the dates instead of making the
 * operator work them out by hand.
 */
@Component
public class AddLicenseOperation extends AbstractSqlOperation<AddLicensePayload> {

  private static final long SYSTEM_USER_ID = 1L;

  @Override
  public String name() { return "ADD_LICENSE"; }

  @Override
  protected List<String> preflight(AddLicensePayload p, JdbcTemplate t) {
    List<String> warnings = new ArrayList<>();

    Integer tableHit = t.queryForObject("""
        SELECT count(*) FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'licenses'
        """, Integer.class);
    if (tableHit == null || tableHit == 0) {
      throw new IllegalArgumentException(
          "This target has no public.licenses table - point at the jaas database");
    }

    Integer orgHit = t.queryForObject(
        "SELECT count(*) FROM organisations WHERE id = ? AND archived = false",
        Integer.class, p.organisationId());
    if (orgHit == null || orgHit == 0) {
      throw new IllegalArgumentException(
          "Organisation id " + p.organisationId() + " not found (or archived)");
    }

    for (Long facilityId : p.facilityIds()) {
      Integer hit = t.queryForObject(
          "SELECT count(*) FROM facilities WHERE id = ? AND organisations_id = ? AND archived = false",
          Integer.class, facilityId, p.organisationId());
      if (hit == null || hit == 0) {
        throw new IllegalArgumentException("Facility id " + facilityId
            + " not found, archived, or not owned by organisation " + p.organisationId());
      }
    }

    for (Long useCaseId : p.useCaseIds()) {
      Integer hit = t.queryForObject(
          "SELECT count(*) FROM use_cases WHERE id = ? AND archived = false",
          Integer.class, useCaseId);
      if (hit == null || hit == 0) {
        throw new IllegalArgumentException("Use case id " + useCaseId + " not found (or archived)");
      }
    }

    List<String> existing = new ArrayList<>();
    List<String> unmapped = new ArrayList<>();
    for (Long facilityId : p.facilityIds()) {
      for (Long useCaseId : p.useCaseIds()) {
        Integer licHit = t.queryForObject("""
            SELECT count(*) FROM licenses
            WHERE facilities_id = ? AND use_cases_id = ? AND archived = false
            """, Integer.class, facilityId, useCaseId);
        if (licHit != null && licHit > 0) {
          existing.add(facilityId + "/" + useCaseId);
        }
        Integer mapHit = t.queryForObject("""
            SELECT count(*) FROM facility_use_case_mapping
            WHERE facilities_id = ? AND use_cases_id = ?
            """, Integer.class, facilityId, useCaseId);
        if (mapHit == null || mapHit == 0) {
          unmapped.add(facilityId + "/" + useCaseId);
        }
      }
    }

    if (!existing.isEmpty()) {
      if (p.skipExistingOrTrue()) {
        warnings.add(existing.size() + " pair(s) already hold an unarchived license and will be skipped: "
            + join(existing));
      } else {
        throw new IllegalArgumentException(
            "Unarchived licenses already exist for " + existing.size() + " pair(s): " + join(existing)
                + " - enable \"skip existing\" or archive them first");
      }
    }

    if (!unmapped.isEmpty()) {
      warnings.add(unmapped.size() + " pair(s) have no facility_use_case_mapping row: " + join(unmapped)
          + " - the license will exist but the use case will not show up for the facility");
    }

    Integer sysUserHit = t.queryForObject(
        "SELECT count(*) FROM users WHERE id = ?", Integer.class, SYSTEM_USER_ID);
    if (sysUserHit == null || sysUserHit == 0) {
      warnings.add("System user id=" + SYSTEM_USER_ID
          + " not found in target. created_by/modified_by references will fail.");
    }

    return warnings;
  }

  @Override
  protected Plan plan(AddLicensePayload p, IdGenerator ids) {
    AddLicensePayload.Resolved r = p.resolve(LocalDate.now());
    PlanBuilder b = new PlanBuilder();
    List<Long> licenseIds = new ArrayList<>();

    for (Long facilityId : p.facilityIds()) {
      for (Long useCaseId : p.useCaseIds()) {
        long licenseId = ids.next();
        long now = licenseId;
        licenseIds.add(licenseId);

        List<Object> params = List.of(
            licenseId, p.organisationId(), facilityId, useCaseId,
            p.productOrDefault(), p.typeOrDefault(),
            r.startDate().toString(), p.periodOrDefault(), r.renewalDate().toString(),
            r.paymentDone(), p.intimateOrDefault(), p.graceOrDefault(),
            p.workflowOrDefault().name(),
            SYSTEM_USER_ID, now, SYSTEM_USER_ID, now,
            p.restrictionsOrDefault());

        if (p.skipExistingOrTrue()) {
          // INSERT ... SELECT ... WHERE NOT EXISTS keeps the skip decision inside the
          // transaction, so a license created between preview and execute is still skipped.
          List<Object> withGuard = new ArrayList<>(params);
          withGuard.add(facilityId);
          withGuard.add(useCaseId);
          b.add("license: facility %d / use case %d (skipped if one already exists)".formatted(facilityId, useCaseId),
              """
              INSERT INTO licenses
              (id, organisations_id, facilities_id, use_cases_id, product, type,
               subscription_start_date, subscription_period, subscription_renewal_date,
               payment_done, intimate_before, grace_period, workflow, archived,
               created_by, created_at, modified_by, modified_at, feature_restrictions)
              SELECT ?::bigint, ?::bigint, ?::bigint, ?::bigint, ?::varchar, ?::varchar,
                     ?::date, ?::int, ?::date, ?::boolean, ?::int, ?::int, ?::varchar, false,
                     ?::bigint, ?::bigint, ?::bigint, ?::bigint, ?::jsonb
              WHERE NOT EXISTS (
                SELECT 1 FROM licenses l
                WHERE l.facilities_id = ? AND l.use_cases_id = ? AND l.archived = false
              )
              """,
              withGuard);
        } else {
          b.add("license: facility %d / use case %d".formatted(facilityId, useCaseId),
              """
              INSERT INTO licenses
              (id, organisations_id, facilities_id, use_cases_id, product, type,
               subscription_start_date, subscription_period, subscription_renewal_date,
               payment_done, intimate_before, grace_period, workflow, archived,
               created_by, created_at, modified_by, modified_at, feature_restrictions)
              VALUES (?, ?, ?, ?, ?, ?, ?::date, ?, ?::date, ?, ?, ?, ?, false, ?, ?, ?, ?, ?::jsonb)
              """,
              params);
        }
      }
    }

    return b.id("licenseCount", licenseIds.size())
        .id("licenseIds", licenseIds)
        .id("facilityIds", p.facilityIds())
        .id("useCaseIds", p.useCaseIds())
        .id("subscriptionStartDate", r.startDate().toString())
        .id("subscriptionRenewalDate", r.renewalDate().toString())
        .id("paymentDone", r.paymentDone())
        .build();
  }

  @Override
  protected String summary(AddLicensePayload p, Plan plan) {
    AddLicensePayload.Resolved r = p.resolve(LocalDate.now());
    return "%d license(s) - %d facilit%s x %d use case(s) - state %s (start %s, renewal %s, payment_done=%s, grace %dd)"
        .formatted(p.pairCount(),
            p.facilityIds().size(), p.facilityIds().size() == 1 ? "y" : "ies",
            p.useCaseIds().size(),
            p.stateOrDefault(), r.startDate(), r.renewalDate(), r.paymentDone(), p.graceOrDefault());
  }

  private static String join(List<String> pairs) {
    if (pairs.size() <= 8) return String.join(", ", pairs);
    return String.join(", ", pairs.subList(0, 8)) + " ... (+" + (pairs.size() - 8) + " more)";
  }
}
