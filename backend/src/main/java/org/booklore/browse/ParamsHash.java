package org.booklore.browse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Computes a short, stable fingerprint of a browse request's facet/query parameters. The
 * fingerprint is stored in the cursor and compared against the request so that following a
 * cursor with conflicting facets is rejected. Only parameters that change the result set are
 * hashed (query, facet logic, facet selections); sort and page position are excluded.
 */
public final class ParamsHash {

    private static final int LENGTH = 12;

    private ParamsHash() {
    }

    public static String compute(String query, Map<String, List<String>> facets, FacetLogic logic) {
        StringBuilder canonical = new StringBuilder();
        canonical.append("q=").append(query == null ? "" : query.trim());
        canonical.append("|logic=").append(logic == null ? FacetLogic.AND : logic);
        canonical.append("|facets=");
        if (facets != null) {
            List<String> keys = new ArrayList<>(facets.keySet());
            keys.sort(String::compareTo);
            for (String key : keys) {
                List<String> values = new ArrayList<>(facets.get(key));
                values.sort(String::compareTo);
                canonical.append(key).append('=').append(String.join(",", values)).append(';');
            }
        }
        return hash(canonical.toString());
    }

    private static String hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < bytes.length && hex.length() < LENGTH; i++) {
                hex.append(Character.forDigit((bytes[i] >> 4) & 0xF, 16));
                hex.append(Character.forDigit(bytes[i] & 0xF, 16));
            }
            return hex.substring(0, LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
