package com.leucine.setup.operations.newOrg;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Input for the new-org operation. Mirrors the original setup SQL provided by the
 * user, grouped into logical blocks.
 */
public record NewOrgPayload(
    @Valid @NotNull Organisation organisation,
    @Valid @NotNull Facility facility,
    @Valid @NotEmpty List<UseCase> useCases,
    @Valid @NotNull AccountOwner accountOwner,
    @Valid PasswordPolicy passwordPolicy
) {

  public record Organisation(
      @NotBlank String name,
      @NotBlank String fqdn,
      @NotBlank String serviceId,
      String serviceFqdn        // optional; defaults to `fqdn`
  ) {}

  public record Facility(
      @NotBlank String name,
      @NotBlank String timezone,
      String dateFormat,
      String dateTimeFormat,
      String timeFormat
  ) {}

  public record UseCase(
      @NotBlank String name,
      @NotBlank String label,
      String cardColor,             // hex, e.g. "#EDF5FF"
      @Valid @NotEmpty List<Property> properties
  ) {}

  public record Property(
      @NotBlank String name,
      @NotBlank String label,
      String placeholder,           // defaults to label
      boolean mandatory
  ) {}

  public record AccountOwner(
      @NotBlank String username,
      @NotBlank String email,
      @NotBlank String firstName,
      @NotBlank String lastName,
      @NotBlank String employeeId,
      String department,
      @NotBlank String password,            // plaintext — bcrypted before insert
      Long challengeQuestionId,             // default 1
      String challengeAnswer                // default "leucine"
  ) {}

  public record PasswordPolicy(
      Integer maxAgeSeconds,
      Integer minLength,
      Integer minLowercase,
      Integer minUppercase,
      Integer minNumeric,
      Integer minSpecial,
      Integer minHistory,
      Integer expirationDays
  ) {}
}
