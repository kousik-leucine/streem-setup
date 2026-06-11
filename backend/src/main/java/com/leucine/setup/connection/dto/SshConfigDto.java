package com.leucine.setup.connection.dto;

import com.leucine.setup.connection.SshAuthMethod;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SshConfigDto(
    @NotBlank String host,
    @Min(1) int port,
    @NotBlank String username,
    @NotNull SshAuthMethod authMethod,
    String password,                 // PASSWORD only (optional on update)
    String privateKeyPath,           // KEY only
    String keyPassphrase,            // KEY only, optional
    boolean strictHostKeyCheck
) {
}
