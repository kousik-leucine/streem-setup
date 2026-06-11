package com.leucine.setup.connection.dto;

import com.leucine.setup.connection.Environment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateConnectionRequest(
    @NotBlank String name,
    @NotBlank String host,
    @Min(1) int port,
    @NotBlank String database,
    @NotBlank String username,
    @NotBlank String password,
    String sslMode,
    @NotNull Environment environment,
    String notes,
    @Valid SshConfigDto ssh                  // null when SSH tunnel is not used
) {
}
