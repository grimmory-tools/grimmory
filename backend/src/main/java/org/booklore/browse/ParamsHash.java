package org.booklore.browse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

// Fingerprint of the request params (query, facet logic, facet selections)
// Used within the cursor + compared on follow-up so cursors w/ conflicting facets are rejected
public final class ParamsHash {

    private static final int LENGTH = 12;

    private ParamsHash() {
    }

    public static String compute(String query, Map<String, List<String>> facets, FacetLogic logic) {
        StringBuilder canonical = new StringBuilder();
        appendField(canonical, query == null ? "" : query.trim());
        appendField(canonical, (logic == null ? FacetLogic.AND : logic).name());
        if (facets != null) {
            List<String> keys = new ArrayList<>(facets.keySet());
            keys.sort(String::compareTo);
            for (String key : keys) {
                appendField(canonical, key);
                List<String> values = new ArrayList<>(facets.get(key));
                values.sort(String::compareTo);
                appendField(canonical, Integer.toString(values.size()));
                for (String value : values) {
                    appendField(canonical, value);
                }
            }
        }
        return hash(canonical.toString());
    }

    private static void appendField(StringBuilder sb, String value) {
        String v = value == null ? "" : value;
        sb.append(v.length()).append(':').append(v).append('|');
    }

    private static String hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes).substring(0, LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
