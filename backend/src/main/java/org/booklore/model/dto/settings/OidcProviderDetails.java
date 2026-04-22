package org.booklore.model.dto.settings;

import lombok.Data;

@Data
public class OidcProviderDetails {
    private String providerName;
    private String clientId;
    private String clientSecret;
    private String issuerUri;
    private String scopes;
    private ClaimMapping claimMapping;

    @Data
    public static class ClaimMapping {
        private String username;
        private String name;
        private String email;
        private String groups;
    }
}