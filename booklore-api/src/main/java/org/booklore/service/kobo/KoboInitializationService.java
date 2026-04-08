package org.booklore.service.kobo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.kobo.KoboResources;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.util.kobo.KoboUrlBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KoboInitializationService {

    private final AppSettingService appSettingService;
    private final KoboServerProxy koboServerProxy;
    private final KoboResourcesComponent koboResourcesComponent;
    private final KoboUrlBuilder koboUrlBuilder;

    private static final Map<String, String[]> initializationResources = Map.<String, String[]>ofEntries(
            Map.entry("delete_entitlement", new String[]{"v1", "library", "{Ids}"}),
            Map.entry("get_tests_request", new String[]{"v1", "analytics", "gettests"}),
            Map.entry("library_book", new String[]{"v1", "user", "library", "books", "{LibraryItemId}"}),
            Map.entry("library_metadata", new String[]{"v1", "library", "{Ids}", "metadata"}),
            Map.entry("library_search", new String[]{"v1", "library", "search"}),
            Map.entry("library_sync", new String[]{"v1", "library", "sync"}),
            Map.entry("post_analytics_event", new String[]{"v1", "analytics", "event"}),
            Map.entry("reading_state", new String[]{"v1", "library", "{Ids}", "state"})
    );

    private boolean isForwardingToKoboStore() {
        return appSettingService.getAppSettings().getKoboSettings().isForwardToKoboStore();
    }

    public ResponseEntity<KoboResources> initialize(String token) throws JacksonException {
        ObjectNode resources = null;

        if (isForwardingToKoboStore()) {
            try {
                ResponseEntity<JsonNode> response = koboServerProxy.proxyCurrentRequest(null, false);
                JsonNode body = response.getBody();
                JsonNode bodyResources = body == null ? null : body.get("Resources");

                if (bodyResources instanceof ObjectNode objectNode) {
                    resources = objectNode;
                } else {
                    log.warn("Unexpected response from Kobo /v1/initialization, fallback to noproxy");
                }
            } catch (Exception e) {
                log.warn("Failed to get response from Kobo /v1/initialization, fallback to noproxy", e);
            }
        }

        if (resources == null) {
            resources = koboResourcesComponent.getResources();
        }

        UriComponentsBuilder baseBuilder = koboUrlBuilder.baseBuilder();

        for (String name : initializationResources.keySet()) {
            resources.put(name, koboUrlBuilder.withBaseUrl(token, initializationResources.get(name)));
        }

        // Build extra routes for CDN
        resources.put("image_host", baseBuilder.build().toUriString());
        resources.put("image_url_template", koboUrlBuilder.imageUrlTemplate(token));
        resources.put("image_url_quality_template", koboUrlBuilder.imageUrlQualityTemplate(token));

        return ResponseEntity.ok()
                .header("x-kobo-apitoken", "e30=")
                .body(new KoboResources(resources));
    }
}