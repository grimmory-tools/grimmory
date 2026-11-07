package org.booklore.service.kobo;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KoboUserProfileServiceTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final KoboServerProxy koboServerProxy = mock(KoboServerProxy.class);
    private final KoboUserProfileService service = new KoboUserProfileService(koboServerProxy);

    private List<String> proxiedPermissions(String profileJson) {
        when(koboServerProxy.proxyCurrentRequest(null, false))
                .thenReturn(ResponseEntity.ok(objectMapper.readTree(profileJson)));

        JsonNode permissions = service.getProxiedUserProfile().getBody().get("PrivacyPermissions");
        List<String> result = new ArrayList<>();
        permissions.forEach(entry -> result.add(entry.asString()));
        return result;
    }

    @Test
    void addsAnalyticsPermission() {
        List<String> permissions = proxiedPermissions("{\"CountryCode\":\"DE\",\"PrivacyPermissions\":[\"feature.recommendation\"]}");

        assertThat(permissions).containsExactly("feature.recommendation", "analytics.platform.features");
    }

    @Test
    void doesNotDuplicateAnalyticsPermission() {
        List<String> permissions = proxiedPermissions("{\"PrivacyPermissions\":[\"analytics.platform.features\"]}");

        assertThat(permissions).containsExactly("analytics.platform.features");
    }

    @Test
    void createsArrayWhenMissing() {
        List<String> permissions = proxiedPermissions("{\"CountryCode\":\"DE\"}");

        assertThat(permissions).containsExactly("analytics.platform.features");
    }
}
