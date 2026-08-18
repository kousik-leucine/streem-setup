package com.leucine.setup.operations.addLicense;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Bulk license creation: every facility in {@code facilityIds} crossed with every
 * use case in {@code useCaseIds} gets one row in {@code licenses}.
 *
 * Dates are ISO strings ("2026-08-10"); they are only read when state is CUSTOM.
 * For every other state the dates are derived so the license lands in that state
 * when jaas evaluates it (see LicenseService#getDetails in java-common-services).
 */
public record AddLicensePayload(
    @NotNull @Positive Long organisationId,
    @NotEmpty List<Long> facilityIds,
    @NotEmpty List<Long> useCaseIds,
    String product,
    String type,
    LicenseState state,
    String subscriptionStartDate,
    String subscriptionRenewalDate,
    Boolean paymentDone,
    Integer subscriptionPeriod,
    Integer intimateBefore,
    Integer gracePeriod,
    Workflow workflow,
    String featureRestrictions,
    Boolean skipExisting
) {

  /** What jaas should report for these licenses. CUSTOM = use the dates as given. */
  public enum LicenseState { GENUINE, INTIMATE, GRACE, GRACE_EXCEEDED, CUSTOM }

  /** Mirrors Misc.LicenseWorkflow in java-common-services/jaas. */
  public enum Workflow { NONE, NOTIFICATION_UNBLOCKED, NOTIFICATION_BLOCKED }

  /** The three date/payment fields actually written, after applying the state preset. */
  public record Resolved(LocalDate startDate, LocalDate renewalDate, boolean paymentDone) {}

  public String productOrDefault()   { return blank(product) ? "dwi" : product.trim(); }
  public String typeOrDefault()      { return blank(type) ? "full" : type.trim(); }
  public int periodOrDefault()       { return subscriptionPeriod != null ? subscriptionPeriod : 365; }
  public int intimateOrDefault()     { return intimateBefore != null ? intimateBefore : 30; }
  public int graceOrDefault()        { return gracePeriod != null ? gracePeriod : 30; }
  public LicenseState stateOrDefault(){ return state != null ? state : LicenseState.GENUINE; }
  public Workflow workflowOrDefault(){ return workflow != null ? workflow : Workflow.NOTIFICATION_UNBLOCKED; }
  public String restrictionsOrDefault() { return blank(featureRestrictions) ? "{}" : featureRestrictions.trim(); }
  public boolean skipExistingOrTrue() { return skipExisting == null || skipExisting; }

  public int pairCount() { return facilityIds.size() * useCaseIds.size(); }

  /**
   * Turns the chosen state into concrete dates + payment_done, relative to {@code today}.
   *
   * jaas derives the state as:
   *   expired       = !payment_done
   *   graceEndsOn   = renewal + grace_period
   *   intimateOn    = renewal - intimate_before
   *   GRACE          <- expired && graceEndsOn > today
   *   GRACE_EXCEEDED <- expired && graceEndsOn < today
   *   INTIMATE       <- !expired && intimateOn < today
   *   GENUINE        <- otherwise
   */
  public Resolved resolve(LocalDate today) {
    int period = periodOrDefault();
    LocalDate renewal;
    boolean paid;

    switch (stateOrDefault()) {
      case CUSTOM -> {
        LocalDate start = parseDate(subscriptionStartDate, "subscriptionStartDate");
        renewal = parseDate(subscriptionRenewalDate, "subscriptionRenewalDate");
        if (start.isAfter(renewal)) {
          throw new IllegalArgumentException(
              "subscriptionStartDate " + start + " is after subscriptionRenewalDate " + renewal);
        }
        return new Resolved(start, renewal, paymentDone == null || paymentDone);
      }
      case GENUINE -> {
        renewal = today.plusDays(period);
        paid = true;
      }
      case INTIMATE -> {
        int intimate = intimateOrDefault();
        if (intimate < 2) {
          throw new IllegalArgumentException(
              "INTIMATE needs intimateBefore >= 2 (got " + intimate + ") - nothing to warn about otherwise");
        }
        renewal = today.plusDays(intimate / 2);
        paid = true;
      }
      case GRACE -> {
        int grace = graceOrDefault();
        if (grace < 2) {
          throw new IllegalArgumentException(
              "GRACE needs gracePeriod >= 2 (got " + grace + ") - the grace window would already be over");
        }
        renewal = today.minusDays(grace / 2);
        paid = false;
      }
      case GRACE_EXCEEDED -> {
        renewal = today.minusDays(graceOrDefault() + 10L);
        paid = false;
      }
      default -> throw new IllegalStateException("Unhandled state " + stateOrDefault());
    }
    return new Resolved(renewal.minusDays(period), renewal, paid);
  }

  private static LocalDate parseDate(String raw, String field) {
    if (blank(raw)) {
      throw new IllegalArgumentException(field + " is required when state is CUSTOM");
    }
    try {
      return LocalDate.parse(raw.trim());
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException(field + " must be an ISO date (yyyy-MM-dd), got \"" + raw + "\"");
    }
  }

  private static boolean blank(String s) { return s == null || s.isBlank(); }
}
