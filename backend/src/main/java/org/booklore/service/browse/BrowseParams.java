package org.booklore.service.browse;

import org.booklore.exception.ApiError;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class BrowseParams {

    private BrowseParams() {
    }

    static Map<String, List<String>> parseFacets(List<String> facet) {
        Map<String, List<String>> facets = new LinkedHashMap<>();
        if (facet == null) {
            return facets;
        }
        for (String entry : facet) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            int colon = entry.indexOf(':');
            if (colon <= 0 || colon == entry.length() - 1) {
                throw ApiError.INVALID_FACET.createException("Facet must be in key:value form: " + entry);
            }
            facets.computeIfAbsent(entry.substring(0, colon), k -> new ArrayList<>()).add(entry.substring(colon + 1));
        }
        return facets;
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
        return entry.substring(0, colon).equals(key) && entry.substring(colon + 1).equalsIgnoreCase(value);
    }

    static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
