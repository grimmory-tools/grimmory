package com.adityachandel.booklore.model.dto.settings;

import lombok.Data;

@Data
public class OidcProviderDetails {
    private String providerName;
    private String clientId;
    private String issuerUri;
    private String jwksUrl;
    private ClaimMapping claimMapping;

    @Data
    public static class ClaimMapping {
        private String username;
        private String name;
        private String email;
    }
}