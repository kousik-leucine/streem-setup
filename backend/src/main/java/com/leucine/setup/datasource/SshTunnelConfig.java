package com.leucine.setup.datasource;

import com.leucine.setup.connection.SshAuthMethod;

/** Inputs needed to open an SshTunnel. Secrets are plaintext (already decrypted). */
public record SshTunnelConfig(
    String host,
    int port,
    String username,
    SshAuthMethod authMethod,
    String password,                 // PASSWORD only
    String privateKeyPath,           // KEY only
    String keyPassphrase,            // KEY only, optional
    String remoteHost,
    int remotePort,
    boolean strictHostKeyCheck
) {
}
