package org.booklore.service.kobo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KoboUserProfileServiceTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Mock
    private KoboServerProxy koboServerProxy;

    private KoboUserProfileService service;

    @BeforeEach
    void setUp() {
        service = new KoboUserProfileService(koboServerProxy);
    }

    private List<String> proxiedPermissions(String profileJson) {
        when(koboServerProxy.proxyCurrentRequest(null, false))
                .thenReturn(ResponseEntity.ok(objectMapper.readTree(profileJson)));

        JsonNode body = objectMapper.valueToTree(service.getUserProfile(true).getBody());
        return permissions(body);
    }

    private static List<String> permissions(JsonNode profile) {
        List<String> result = new ArrayList<>();
        profile.get("PrivacyPermissions").forEach(entry -> result.add(entry.asString()));
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
    void addsToEmptyArray() {
        List<String> permissions = proxiedPermissions("{\"PrivacyPermissions\":[]}");

        assertThat(permissions).containsExactly("analytics.platform.features");
    }

    @Test
    void createsArrayWhenMissing() {
        List<String> permissions = proxiedPermissions("{\"CountryCode\":\"DE\"}");

        assertThat(permissions).containsExactly("analytics.platform.features");
    }

    @Test
    void createsArrayWhenPermissionsAreNull() {
        List<String> permissions = proxiedPermissions("{\"PrivacyPermissions\":null}");

        assertThat(permissions).containsExactly("analytics.platform.features");
    }

    @Test
    void leavesNonArrayPermissionsUnchanged() {
        String profileJson = "{\"CountryCode\":\"DE\",\"PrivacyPermissions\":\"unexpected\"}";
        ResponseEntity<JsonNode> response = ResponseEntity.ok(objectMapper.readTree(profileJson));
        when(koboServerProxy.proxyCurrentRequest(null, false)).thenReturn(response);

        ResponseEntity<?> actual = service.getUserProfile(true);

        assertThat(actual).isSameAs(response);
        assertThat(actual.getBody()).isEqualTo(objectMapper.readTree(profileJson));
    }

    @Test
    void leavesNullBodyUnchanged() {
        ResponseEntity<JsonNode> response = ResponseEntity.noContent().build();
        when(koboServerProxy.proxyCurrentRequest(null, false)).thenReturn(response);

        ResponseEntity<?> actual = service.getUserProfile(true);

        assertThat(actual).isSameAs(response);
        assertThat(actual.getBody()).isNull();
    }

    @Test
    void defaultProfileGrantsAnalyticsPermission() {
        JsonNode profile = objectMapper.valueToTree(service.getUserProfile(false).getBody());

        assertThat(permissions(profile)).containsExactly("analytics.platform.features");
        assertThat(profile.has("PrivatePermissions")).isFalse();
        verifyNoInteractions(koboServerProxy);
    }
}
