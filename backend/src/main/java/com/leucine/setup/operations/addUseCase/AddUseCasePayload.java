package com.leucine.setup.operations.addUseCase;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record AddUseCasePayload(
    @NotNull @Positive Long facilityId,
    @NotBlank String name,
    @NotBlank String label,
    String cardColor,
    @Valid @NotEmpty List<Property> properties
) {
  public record Property(
      @NotBlank String name,
      @NotBlank String label,
      String placeholder,
      boolean mandatory
  ) {}
}
