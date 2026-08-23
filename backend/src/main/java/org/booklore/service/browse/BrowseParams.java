package org.booklore.service.browse;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class BrowseParams {

    private BrowseParams() {
    }

    static String preserved(List<String> facet, String query) {
        List<String> parts = new ArrayList<>();
        if (facet != null) {
            for (String entry : facet) {
                if (entry != null && !entry.isBlank()) {
                    parts.add("facet=" + encode(entry));
                }
            }
        }
        if (query != null && !query.isBlank()) {
            parts.add("query=" + encode(query));
        }
        return String.join("&", parts);
    }

    static boolean hasFacet(List<String> facet, String key, String value) {
        if (facet == null) {
            return false;
        }
        return facet.stream().anyMatch(entry -> matchesFacet(entry, key, value));
    }

    private static boolean matchesFacet(String entry, String key, String value) {
        if (entry == null) {
            return false;
        }
        int colon = entry.indexOf(':');
        if (colon <= 0 || colon == entry.length() - 1) {
            return false;
        }
        String entryKey = entry.startsWith("+") ? entry.substring(1, colon) : entry.substring(0, colon);
        return entryKey.equals(key) && entry.substring(colon + 1).equalsIgnoreCase(value);
    }

    static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
