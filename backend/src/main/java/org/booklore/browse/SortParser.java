package org.booklore.browse;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Parses the sort query parameter into ordered SortTerms.
 *
 * Format: comma-separated keys, each optionally prefixed with '-' for descending; ascending by
 * default (e.g. "seriesName,-seriesNumber"). An implicit ascending tiebreaker on the primary
 * key is always appended unless the caller already sorts on it, so pagination is stable.
 */
public final class SortParser {

    /** The implicit primary-key tiebreaker key appended to every sort. */
    public static final String TIEBREAKER_KEY = "id";

    private SortParser() {
    }

    /**
     * Parses sortString and validates each key against knownKeys.
     *
     * @param sortString the raw sort parameter; null/blank yields just the tiebreaker
     * @param knownKeys  the registered sort keys; must include TIEBREAKER_KEY
     * @return the ordered sort terms, always ending in the ascending primary-key tiebreaker
     * @throws InvalidSortException if a token is empty or references an unknown key
     */
    public static List<SortTerm> parse(String sortString, Set<String> knownKeys) {
        List<SortTerm> terms = new ArrayList<>();
        boolean tiebreakerCovered = false;

        if (sortString != null && !sortString.isBlank()) {
            for (String raw : sortString.split(",", -1)) {
                String token = raw.trim();
                boolean descending = false;
                if (token.startsWith("-")) {
                    descending = true;
                    token = token.substring(1).trim();
                }
                if (token.isEmpty()) {
                    throw new InvalidSortException("Empty sort key in: " + sortString);
                }
                if (!knownKeys.contains(token)) {
                    throw new InvalidSortException("Unknown sort key: " + token);
                }
                terms.add(new SortTerm(token, descending));
                if (token.equals(TIEBREAKER_KEY)) {
                    tiebreakerCovered = true;
                }
            }
        }

        if (!tiebreakerCovered) {
            terms.add(new SortTerm(TIEBREAKER_KEY, false));
        }
        return terms;
    }
}
