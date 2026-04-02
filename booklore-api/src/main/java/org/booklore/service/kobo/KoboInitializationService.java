package org.booklore.service.kobo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.kobo.KoboResources;
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

    private final KoboServerProxy koboServerProxy;
    private final KoboResourcesComponent koboResourcesComponent;
    private final KoboUrlBuilder koboUrlBuilder;

    private static final Map<String, String[]> initializationResources = Map.<String, String[]>ofEntries(
            Map.entry("add_device", new String[]{"v1", "user", "add-device"}),
            Map.entry("add_entitlement", new String[]{"v1", "library", "{RevisionIds}"}),
            Map.entry("affiliaterequest", new String[]{"v1", "affiliate"}),
            Map.entry("assets", new String[]{"v1", "assets"}),
            Map.entry("audiobook", new String[]{"v1", "products", "audiobooks", "{ProductId}"}),
            Map.entry("audiobook_preview", new String[]{"v1", "products", "audiobooks", "{Id}", "preview"}),
            Map.entry("audiobook_purchase_withcredit", new String[]{"v1", "store", "audiobook", "{Id}"}),
            Map.entry("authorproduct_recommendations", new String[]{"v1", "products", "books", "authors", "recommendations"}),
            Map.entry("autocomplete", new String[]{"v1", "products", "autocomplete"}),
            Map.entry("book", new String[]{"v1", "products", "books", "{ProductId}"}),
            Map.entry("book_subscription", new String[]{"v1", "products", "books", "subscriptions"}),
            Map.entry("browse_history", new String[]{"v1", "user", "browsehistory"}),
            Map.entry("categories", new String[]{"v1", "categories"}),
            Map.entry("category", new String[]{"v1", "categories", "{CategoryId}"}),
            Map.entry("category_featured_lists", new String[]{"v1", "categories", "{CategoryId}", "featured"}),
            Map.entry("category_products", new String[]{"v1", "categories", "{CategoryId}", "products"}),
            Map.entry("checkout_borrowed_book", new String[]{"v1", "library", "borrow"}),
            Map.entry("configuration_data", new String[]{"v1", "configuration"}),
            Map.entry("content_access_book", new String[]{"v1", "products", "books", "{ProductId}", "access"}),
            Map.entry("daily_deal", new String[]{"v1", "products", "dailydeal"}),
            Map.entry("deals", new String[]{"v1", "deals"}),
            Map.entry("delete_entitlement", new String[]{"v1", "library", "{Ids}"}),
            Map.entry("delete_tag", new String[]{"v1", "library", "tags", "{TagId}"}),
            Map.entry("delete_tag_items", new String[]{"v1", "library", "tags", "{TagId}", "items", "delete"}),
            Map.entry("device_auth", new String[]{"v1", "auth", "device"}),
            Map.entry("device_refresh", new String[]{"v1", "auth", "refresh"}),
            Map.entry("ereaderdevices", new String[]{"v2", "products", "EReaderDeviceFeeds"}),
            Map.entry("exchange_auth", new String[]{"v1", "auth", "exchange"}),
            Map.entry("external_book", new String[]{"v1", "products", "books", "external", "{Ids}"}),
            Map.entry("featured_list", new String[]{"v1", "products", "featured", "{FeaturedListId}"}),
            Map.entry("featured_lists", new String[]{"v1", "products", "featured"}),
            Map.entry("fte_feedback", new String[]{"v1", "products", "ftefeedback"}),
            Map.entry("funnel_metrics", new String[]{"v1", "funnelmetrics"}),
            Map.entry("get_download_keys", new String[]{"v1", "library", "downloadkeys"}),
            Map.entry("get_download_link", new String[]{"v1", "library", "downloadlink"}),
            Map.entry("get_tests_request", new String[]{"v1", "analytics", "gettests"}),
            Map.entry("library_book", new String[]{"v1", "user", "library", "books", "{LibraryItemId}"}),
            Map.entry("library_items", new String[]{"v1", "user", "library"}),
            Map.entry("library_metadata", new String[]{"v1", "library", "{Ids}", "metadata"}),
            Map.entry("library_prices", new String[]{"v1", "user", "library", "previews", "prices"}),
            Map.entry("library_search", new String[]{"v1", "library", "search"}),
            Map.entry("library_sync", new String[]{"v1", "library", "sync"}),
            Map.entry("notebooks", new String[]{"api", "internal", "notebooks"}),
            Map.entry("notifications_registration_issue", new String[]{"v1", "notifications", "registration"}),
            Map.entry("personalizedrecommendations", new String[]{"v2", "users", "personalizedrecommendations"}),
            Map.entry("post_analytics_event", new String[]{"v1", "analytics", "event"}),
            Map.entry("product_nextread", new String[]{"v1", "products", "{ProductIds}", "nextread"}),
            Map.entry("product_prices", new String[]{"v1", "products", "{ProductIds}", "prices"}),
            Map.entry("product_recommendations", new String[]{"v1", "products", "{ProductId}", "recommendations"}),
            Map.entry("product_reviews", new String[]{"v1", "products", "{ProductIds}", "reviews"}),
            Map.entry("products", new String[]{"v1", "products"}),
            Map.entry("productsv2", new String[]{"v2", "products"}),
            Map.entry("quickbuy_checkout", new String[]{"v1", "store", "quickbuy", "{PurchaseId}", "checkout"}),
            Map.entry("quickbuy_create", new String[]{"v1", "store", "quickbuy", "purchase"}),
            Map.entry("rakuten_token_exchange", new String[]{"v1", "auth", "rakuten_token_exchange"}),
            Map.entry("rating", new String[]{"v1", "products", "{ProductId}", "rating", "{Rating}"}),
            Map.entry("reading_state", new String[]{"v1", "library", "{Ids}", "state"}),
            Map.entry("related_items", new String[]{"v1", "products", "{Id}", "related"}),
            Map.entry("remaining_book_series", new String[]{"v1", "products", "books", "series", "{SeriesId}"}),
            Map.entry("rename_tag", new String[]{"v1", "library", "tags", "{TagId}"}),
            Map.entry("review", new String[]{"v1", "products", "reviews", "{ReviewId}"}),
            Map.entry("review_sentiment", new String[]{"v1", "products", "reviews", "{ReviewId}", "sentiment", "{Sentiment}"}),
            Map.entry("shelfie_recommendations", new String[]{"v1", "user", "recommendations", "shelfie"}),
            Map.entry("tag_items", new String[]{"v1", "library", "tags", "{TagId}", "Items"}),
            Map.entry("tags", new String[]{"v1", "library", "tags"}),
            Map.entry("taste_profile", new String[]{"v1", "products", "tasteprofile"}),
            Map.entry("update_accessibility_to_preview", new String[]{"v1", "library", "{EntitlementIds}", "preview"}),
            Map.entry("user_loyalty_benefits", new String[]{"v1", "user", "loyalty", "benefits"}),
            Map.entry("user_platform", new String[]{"v1", "user", "platform"}),
            Map.entry("user_profile", new String[]{"v1", "user", "profile"}),
            Map.entry("user_ratings", new String[]{"v1", "user", "ratings"}),
            Map.entry("user_recommendations", new String[]{"v1", "user", "recommendations"}),
            Map.entry("user_reviews", new String[]{"v1", "user", "reviews"}),
            Map.entry("user_wishlist", new String[]{"v1", "user", "wishlist"})
    );

    public ResponseEntity<KoboResources> initialize(String token) throws JacksonException {
        ObjectNode resources = null;

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