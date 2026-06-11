package com.leucine.setup.operations.addFacility;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddFacilityPayload(
    @NotNull @Positive Long organisationId,
    @NotBlank String name,
    @NotBlank String timezone,
    String dateFormat,
    String dateTimeFormat,
    String timeFormat
) {
}
