package com.leucine.setup.operations.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Standard envelope for /api/ops/.../preview and /api/ops/.../execute requests. */
public record OpRequest<P>(
    @NotBlank String connectionId,
    @Valid @NotNull P payload
) {
}
