package com.leucine.setup.operations.mapProperty;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record MapPropertyPayload(
    @NotNull @Positive Long facilityId,
    @NotNull @Positive Long useCaseId,
    @NotNull @Positive Long propertyId,
    /** When false (default), uses the property's own label/placeholder. */
    String labelAlias,
    String placeholderAlias,
    boolean mandatory
) {
}
