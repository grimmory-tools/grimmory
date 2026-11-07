package org.booklore.service.kobo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Slf4j
@Service
@RequiredArgsConstructor
public class KoboUserProfileService {

    static final String PRIVACY_PERMISSIONS = "PrivacyPermissions";
    static final String ANALYTICS_PLATFORM_FEATURES = "analytics.platform.features";

    private final KoboServerProxy koboServerProxy;

    public ResponseEntity<JsonNode> getProxiedUserProfile() {
        ResponseEntity<JsonNode> response = koboServerProxy.proxyCurrentRequest(null, false);
        addAnalyticsPermission(response.getBody());
        return response;
    }

    void addAnalyticsPermission(JsonNode profile) {
        if (!(profile instanceof ObjectNode profileObject)) {
            return;
        }

        JsonNode existing = profileObject.get(PRIVACY_PERMISSIONS);
        ArrayNode permissions;
        if (existing == null || existing.isNull()) {
            permissions = profileObject.putArray(PRIVACY_PERMISSIONS);
        } else if (existing instanceof ArrayNode array) {
            permissions = array;
        } else {
            log.warn("Not adding Kobo privacy permission: {} in user profile is not an array", PRIVACY_PERMISSIONS);
            return;
        }

        for (JsonNode entry : permissions) {
            if (ANALYTICS_PLATFORM_FEATURES.equals(entry.asString())) {
                return;
            }
        }
        permissions.add(ANALYTICS_PLATFORM_FEATURES);
    }
}
