package com.leucine.setup.operations.newOrg;

import com.leucine.setup.operations.ExecuteResult;
import com.leucine.setup.operations.IdGenerator;
import com.leucine.setup.operations.Operation;
import com.leucine.setup.operations.OperationContext;
import com.leucine.setup.operations.PreviewResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Bootstraps a brand-new streem-backend organisation from a single payload.
 * Mirrors the manual setup SQL the platform team runs today.
 */
@Component
public class NewOrgOperation implements Operation<NewOrgPayload> {

  private static final Logger log = LoggerFactory.getLogger(NewOrgOperation.class);
  private static final long SYSTEM_USER_ID = 1L;
  private static final long ACCOUNT_OWNER_ROLE_ID = 1L;
  private static final long DEFAULT_CHALLENGE_QUESTION_ID = 1L;

  private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

  @Override
  public String name() { return "NEW_ORG"; }

  @Override
  public PreviewResult preview(NewOrgPayload payload, OperationContext ctx) {
    List<String> warnings = preflight(payload, ctx.target());
    NewOrgPlan plan = buildPlan(payload, ctx.ids());
    String sql = plan.statements().stream()
        .map(s -> "-- " + s.comment() + "\n" + s.render() + ";")
        .collect(Collectors.joining("\n\n"));
    String summary = "New org \"%s\" with facility \"%s\", %d use case(s), account owner \"%s\""
        .formatted(payload.organisation().name(),
            payload.facility().name(),
            payload.useCases().size(),
            payload.accountOwner().username());
    return new PreviewResult(sql, summary, warnings);
  }

  @Override
  public ExecuteResult execute(NewOrgPayload payload, OperationContext ctx) {
    preflight(payload, ctx.target());                 // fail loudly before any write
    NewOrgPlan plan = buildPlan(payload, ctx.ids());

    JdbcTemplate target = ctx.target();
    DataSourceTransactionManager txMgr =
        new DataSourceTransactionManager(target.getDataSource());
    TransactionTemplate tx = new TransactionTemplate(txMgr);

    String renderedSql = renderAll(plan.statements());
    log.info("Executing NEW_ORG against {} ({} statements)",
        ctx.connection().name(), plan.statements().size());

    try {
      tx.execute(new TransactionCallbackWithoutResult() {
        @Override
        protected void doInTransactionWithoutResult(TransactionStatus status) {
          for (SqlStatement s : plan.statements()) {
            target.update(s.template(), s.params().toArray());
          }
        }
      });
    } catch (DataAccessException e) {
      log.error("NEW_ORG failed against {}: {}", ctx.connection().name(), e.getMessage());
      throw e;
    }

    return new ExecuteResult(0L, plan.generatedIds(), "Org created");
  }

  // --- private helpers ---------------------------------------------------------

  /**
   * Read-only checks against the target. Returns warnings (non-blocking) and
   * throws IllegalArgumentException for hard failures.
   */
  private List<String> preflight(NewOrgPayload payload, JdbcTemplate target) {
    List<String> warnings = new ArrayList<>();

    Integer fqdnHit = target.queryForObject(
        "SELECT count(*) FROM organisations WHERE fqdn = ?",
        Integer.class, payload.organisation().fqdn());
    if (fqdnHit != null && fqdnHit > 0) {
      throw new IllegalArgumentException(
          "Organisation with fqdn '" + payload.organisation().fqdn() + "' already exists");
    }

    Integer userHit = target.queryForObject(
        "SELECT count(*) FROM users WHERE username = ?",
        Integer.class, payload.accountOwner().username());
    if (userHit != null && userHit > 0) {
      throw new IllegalArgumentException(
          "User with username '" + payload.accountOwner().username() + "' already exists");
    }

    Integer sysUserHit = target.queryForObject(
        "SELECT count(*) FROM users WHERE id = ?", Integer.class, SYSTEM_USER_ID);
    if (sysUserHit == null || sysUserHit == 0) {
      warnings.add("System user id=" + SYSTEM_USER_ID
          + " not found in target. created_by/modified_by references will fail.");
    }

    return warnings;
  }

  private NewOrgPlan buildPlan(NewOrgPayload p, IdGenerator ids) {
    Map<String, Object> generated = new LinkedHashMap<>();
    List<SqlStatement> stmts = new ArrayList<>();

    long orgId = ids.next();
    long settingsId = ids.next();
    long facilityId = ids.next();
    long ownerId = ids.next();
    long passwordPolicyId = ids.next();
    long now = orgId;                            // epoch-seconds convention: id == created_at

    generated.put("organisationId", orgId);
    generated.put("organisationSettingsId", settingsId);
    generated.put("facilityId", facilityId);
    generated.put("accountOwnerId", ownerId);
    generated.put("passwordPolicyId", passwordPolicyId);

    // 1. organisations
    stmts.add(new SqlStatement(
        "organisation",
        """
        INSERT INTO organisations (id, name, archived, created_at, modified_at, fqdn,
                                   created_by, modified_by, is_master)
        VALUES (?, ?, false, ?, ?, ?, ?, ?, false)
        """,
        List.of(orgId, p.organisation().name(), now, now, p.organisation().fqdn(),
            SYSTEM_USER_ID, SYSTEM_USER_ID)
    ));

    // 2. organisation_settings
    stmts.add(new SqlStatement(
        "organisation_settings",
        """
        INSERT INTO organisation_settings
        (id, created_at, created_by, modified_at, modified_by, auto_unlock_after,
         max_failed_login_attempts, organisations_id, password_reset_token_expiration,
         registration_token_expiration, session_idle_timeout, logo_url,
         max_failed_additional_verification_attempts, max_failed_challenge_question_attempts,
         extras, feature_flags)
        VALUES (?, 0, ?, 0, ?, 15, 3, ?, 60, 60, 10, NULL, 3, 3,
                '{}'::jsonb, '{"metabaseReports": false}'::jsonb)
        """,
        List.of(settingsId, SYSTEM_USER_ID, SYSTEM_USER_ID, orgId)
    ));

    // 3. organisation_services_mapping
    String serviceFqdn = (p.organisation().serviceFqdn() != null
        && !p.organisation().serviceFqdn().isBlank())
        ? p.organisation().serviceFqdn() : p.organisation().fqdn();
    stmts.add(new SqlStatement(
        "organisation_services_mapping",
        """
        INSERT INTO organisation_services_mapping
        (organisations_id, services_id, created_at, created_by, fqdn)
        VALUES (?, ?, ?, ?, ?)
        """,
        List.of(orgId, p.organisation().serviceId(), now, SYSTEM_USER_ID, serviceFqdn)
    ));

    // 4. facilities
    NewOrgPayload.Facility f = p.facility();
    stmts.add(new SqlStatement(
        "facility",
        """
        INSERT INTO facilities
        (id, name, organisations_id, created_at, modified_at, archived,
         created_by, date_format, date_time_format, modified_by, time_format, time_zone)
        VALUES (?, ?, ?, ?, ?, false, NULL, ?, ?, NULL, ?, ?)
        """,
        List.of(facilityId, f.name(), orgId, now, now,
            orDefault(f.dateFormat(), "MMM dd, yyyy"),
            orDefault(f.dateTimeFormat(), "MMM dd, yyyy HH:mm"),
            orDefault(f.timeFormat(), "HH:mm"),
            f.timezone())
    ));

    // 5..8. use cases + facility-usecase mapping + properties + property mapping
    for (NewOrgPayload.UseCase uc : p.useCases()) {
      long useCaseId = ids.next();
      String cardColorJson = "{\"card-color\": \""
          + (uc.cardColor() != null && !uc.cardColor().isBlank() ? uc.cardColor() : "#EDF5FF")
          + "\"}";

      stmts.add(new SqlStatement(
          "use_case: " + uc.name(),
          """
          INSERT INTO use_cases
          (id, name, label, description, order_tree, metadata, archived,
           created_by, created_at, modified_by, modified_at)
          VALUES (?, ?, ?, NULL, 1, ?::jsonb, false, ?, ?, ?, ?)
          """,
          List.of(useCaseId, uc.name(), uc.label(), cardColorJson,
              SYSTEM_USER_ID, now, SYSTEM_USER_ID, now)
      ));

      stmts.add(new SqlStatement(
          "facility_use_case_mapping: " + uc.name(),
          """
          INSERT INTO facility_use_case_mapping
          (facilities_id, use_cases_id, quota, created_by, created_at, modified_by, modified_at)
          VALUES (?, ?, 0, ?, ?, ?, ?)
          """,
          List.of(facilityId, useCaseId, SYSTEM_USER_ID, now, SYSTEM_USER_ID, now)
      ));

      int propertyOrder = 1;
      for (NewOrgPayload.Property prop : uc.properties()) {
        long propertyId = ids.next();
        long mappingId = ids.next();
        String placeholder = (prop.placeholder() != null && !prop.placeholder().isBlank())
            ? prop.placeholder() : prop.label();

        stmts.add(new SqlStatement(
            "property: " + prop.name(),
            """
            INSERT INTO properties
            (id, use_cases_id, name, label, place_holder, order_tree, is_global, type,
             archived, created_by, created_at, modified_by, modified_at)
            VALUES (?, ?, ?, ?, ?, ?, false, 'CHECKLIST', false, ?, ?, ?, ?)
            """,
            List.of(propertyId, useCaseId, prop.name(), prop.label(), placeholder,
                propertyOrder, SYSTEM_USER_ID, now, SYSTEM_USER_ID, now)
        ));

        stmts.add(new SqlStatement(
            "facility_use_case_property_mapping: " + prop.name(),
            """
            INSERT INTO facility_use_case_property_mapping
            (id, facilities_id, use_cases_id, properties_id, label_alias, place_holder_alias,
             order_tree, is_mandatory, created_by, created_at, modified_by, modified_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            List.of(mappingId, facilityId, useCaseId, propertyId,
                prop.label(), placeholder, propertyOrder, prop.mandatory(),
                SYSTEM_USER_ID, now, SYSTEM_USER_ID, now)
        ));
        propertyOrder++;
      }
    }

    // 9. users (account owner)
    NewOrgPayload.AccountOwner owner = p.accountOwner();
    String hashedPassword = bcrypt.encode(owner.password());
    long challengeQuestionId = owner.challengeQuestionId() != null
        ? owner.challengeQuestionId() : DEFAULT_CHALLENGE_QUESTION_ID;
    String challengeAnswer = owner.challengeAnswer() != null
        ? owner.challengeAnswer() : "leucine";

    stmts.add(new SqlStatement(
        "account owner user",
        """
        INSERT INTO users
        (id, created_at, modified_at, organisations_id, employee_id, email, first_name,
         archived, last_name, created_by, modified_by, locked_at, department,
         failed_login_attempts, is_system_user, password, password_updated_at, username,
         state, failed_additional_verification_attempts, challenge_questions_id,
         challenge_questions_answer, failed_challenge_question_attempts, type)
        VALUES (?, ?, ?, ?, ?, ?, ?, false, ?, ?, ?, NULL, ?, 0, false, ?, ?, ?,
                'REGISTERED', 0, ?, ?, 0, 'LOCAL')
        """,
        List.of(ownerId, now, now, orgId, owner.employeeId(), owner.email(),
            owner.firstName(), owner.lastName(),
            SYSTEM_USER_ID, SYSTEM_USER_ID,
            owner.department() != null ? owner.department() : "",
            hashedPassword, now, owner.username(),
            challengeQuestionId, challengeAnswer)
    ));

    // 10. organisation_facilities_mapping (created_by = the new account owner, matching original SQL)
    stmts.add(new SqlStatement(
        "organisation_facilities_mapping",
        """
        INSERT INTO organisation_facilities_mapping
        (facilities_id, organisations_id, created_at, created_by)
        VALUES (?, ?, ?, ?)
        """,
        List.of(facilityId, orgId, now, ownerId)
    ));

    // 11. user_roles_mapping
    stmts.add(new SqlStatement(
        "user_roles_mapping (account owner role)",
        """
        INSERT INTO user_roles_mapping (roles_id, users_id, created_at, created_by)
        VALUES (?, ?, ?, ?)
        """,
        List.of(ACCOUNT_OWNER_ROLE_ID, ownerId, now, SYSTEM_USER_ID)
    ));

    // 12. user_facilities_mapping
    stmts.add(new SqlStatement(
        "user_facilities_mapping",
        """
        INSERT INTO user_facilities_mapping (facilities_id, users_id, created_at, created_by)
        VALUES (?, ?, ?, ?)
        """,
        List.of(facilityId, ownerId, now, SYSTEM_USER_ID)
    ));

    // 13. password_policies
    NewOrgPayload.PasswordPolicy pp = p.passwordPolicy() != null
        ? p.passwordPolicy()
        : new NewOrgPayload.PasswordPolicy(null, null, null, null, null, null, null, null);
    stmts.add(new SqlStatement(
        "password_policies",
        """
        INSERT INTO password_policies
        (id, created_at, created_by, modified_at, modified_by,
         allow_password_similar_to_username_or_email, maximum_password_age,
         minimum_lowercase_characters, minimum_numeric_characters,
         minimum_password_history, minimum_password_length, minimum_special_characters,
         minimum_uppercase_characters, organisations_id, password_expiration)
        VALUES (?, 0, ?, 0, NULL, false, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        List.of(passwordPolicyId, SYSTEM_USER_ID,
            orDefaultInt(pp.maxAgeSeconds(), 129600),
            orDefaultInt(pp.minLowercase(), 1),
            orDefaultInt(pp.minNumeric(), 1),
            orDefaultInt(pp.minHistory(), 3),
            orDefaultInt(pp.minLength(), 9),
            orDefaultInt(pp.minSpecial(), 1),
            orDefaultInt(pp.minUppercase(), 1),
            orgId,
            orDefaultInt(pp.expirationDays(), 90))
    ));

    return new NewOrgPlan(stmts, generated);
  }

  private static String renderAll(List<SqlStatement> stmts) {
    return stmts.stream()
        .map(s -> "-- " + s.comment() + "\n" + s.render() + ";")
        .collect(Collectors.joining("\n\n"));
  }

  private static String orDefault(String v, String def) {
    return (v != null && !v.isBlank()) ? v : def;
  }

  private static int orDefaultInt(Integer v, int def) {
    return v != null ? v : def;
  }
}
