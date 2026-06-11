package com.leucine.setup.operations.setFeatureFlags;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.Map;

/**
 * Boolean flags to merge into organisation_settings.feature_flags via JSONB ||.
 * Existing flags not in the map are preserved.
 */
public record SetFeatureFlagsPayload(
    @NotNull @Positive Long organisationId,
    @NotEmpty Map<String, Boolean> flags
) {
}
