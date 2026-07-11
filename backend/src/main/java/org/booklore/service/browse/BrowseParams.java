package org.booklore.service.browse;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class BrowseParams {

    private BrowseParams() {
    }

    static String preserved(List<String> facet, String facetLogic, String query) {
        List<String> parts = new ArrayList<>();
        if (facet != null) {
            for (String entry : facet) {
                if (entry != null && !entry.isBlank()) {
                    parts.add("facet=" + encode(entry));
                }
            }
        }
        if (facetLogic != null && !facetLogic.isBlank()) {
            parts.add("facet_logic=" + encode(facetLogic));
        }
        if (query != null && !query.isBlank()) {
            parts.add("query=" + encode(query));
        }
        return String.join("&", parts);
    }

    static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
