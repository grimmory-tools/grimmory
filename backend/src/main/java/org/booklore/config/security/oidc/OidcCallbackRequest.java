package org.booklore.config.security.oidc;

import jakarta.validation.constraints.NotBlank;

public record OidcCallbackRequest(
        @NotBlank String code,
        @NotBlank String codeVerifier,
        @NotBlank String redirectUri,
        @NotBlank String nonce,
        @NotBlank String state
) {}
