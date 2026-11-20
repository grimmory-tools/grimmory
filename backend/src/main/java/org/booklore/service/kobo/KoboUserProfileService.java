package org.booklore.service.kobo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.kobo.KoboUserProfile;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Serves the Kobo user profile and grants the {@value #ANALYTICS_PLATFORM_FEATURES} privacy permission, which makes
 * the device post the reading analytics events Grimmory turns into reading sessions. The events themselves are consumed
 * by Grimmory and never forwarded to Kobo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KoboUserProfileService {

    static final String PRIVACY_PERMISSIONS = "PrivacyPermissions";
    static final String ANALYTICS_PLATFORM_FEATURES = KoboUserProfile.ANALYTICS_PLATFORM_FEATURES;

    private final KoboServerProxy koboServerProxy;

    /**
     * Returns the user profile for the device, with the analytics permission granted either way.
     *
     * @param forwardToKoboStore whether to proxy the profile from the Kobo store instead of returning a default profile
     * @return the proxied Kobo store profile, or Grimmory's default profile
     */
    public ResponseEntity<?> getUserProfile(boolean forwardToKoboStore) {
        if (forwardToKoboStore) {
            return getProxiedUserProfile();
        }
        return ResponseEntity.ok(KoboUserProfile.builder().build());
    }

    /**
     * Fetches the user profile from the Kobo store and adds the analytics permission to it.
     *
     * @return the proxied response; its body is modified in place when it is a profile object
     */
    ResponseEntity<JsonNode> getProxiedUserProfile() {
        ResponseEntity<JsonNode> response = koboServerProxy.proxyCurrentRequest(null, false);
        addAnalyticsPermission(response.getBody());
        return response;
    }

    /**
     * Adds {@value #ANALYTICS_PLATFORM_FEATURES} to the profile's {@value #PRIVACY_PERMISSIONS} array, creating the
     * array if it is missing or {@code null}. Other permissions are left untouched, and a profile that is not an
     * object or whose permissions are not an array is left unchanged.
     *
     * @param profile the profile JSON returned by the Kobo store, may be {@code null}
     */
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
