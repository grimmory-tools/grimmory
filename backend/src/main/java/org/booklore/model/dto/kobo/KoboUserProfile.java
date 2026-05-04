package org.booklore.model.dto.kobo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KoboUserProfile {
    @Builder.Default
    private String affiliateName = "Grimmory";

    @Builder.Default
    private boolean isOneStore = false;

    @Builder.Default
    private boolean isChildAccount = false;

    @Builder.Default
    private boolean isLibraryMigrated = true;

    @Builder.Default
    private boolean vipMembershipPurchased = true;

    @Builder.Default
    private boolean hasPurchased = true;

    @Builder.Default
    private boolean hasPurchasedBook = true;

    @Builder.Default
    private boolean hasPurchasedAudiobook = false;

    @Builder.Default
    private boolean safeSearch = false;

    @Builder.Default
    private boolean audiobooksEnabled = false;

    @Builder.Default
    private boolean isOrangeAffiliated = false;

    @Builder.Default
    private boolean isEligibleForOrangeDeal = false;

    @Builder.Default
    private String platformId = "00000000-0000-0000-0000-000000000001";

    @Builder.Default
    private String partnerId = "00000000-0000-0000-0000-000000000001";

    @Builder.Default
    private String koboCrmOptInSettings = "ExplicitlyConsentedNo";

    @Builder.Default
    private List<String> privatePermissions = List.of();

    @Builder.Default
    private List<String> linkedAccounts = List.of();
}
