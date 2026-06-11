package com.leucine.setup.operations.addProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddPropertyPayload(
    @NotNull @Positive Long facilityId,
    @NotNull @Positive Long useCaseId,
    @NotBlank String name,
    @NotBlank String label,
    String placeholder,
    boolean mandatory
) {
}
