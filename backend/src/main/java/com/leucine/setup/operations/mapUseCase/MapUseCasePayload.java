package com.leucine.setup.operations.mapUseCase;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record MapUseCasePayload(
    @NotNull @Positive Long facilityId,
    @NotNull @Positive Long useCaseId,
    /**
     * When true, also map every property of this use case to this facility (via
     * facility_use_case_property_mapping). Defaults to true — a facility-usecase
     * pair with no property mappings is usually unusable.
     */
    Boolean includeProperties
) {
  public boolean includePropertiesOrTrue() {
    return includeProperties == null || includeProperties;
  }
}
