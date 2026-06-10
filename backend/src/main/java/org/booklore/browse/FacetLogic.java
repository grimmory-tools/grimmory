package org.booklore.browse;

/**
 * How multiple facets combine, controlled by the facet_logic parameter. Defaults to AND,
 * so each additional facet narrows the result.
 */
public enum FacetLogic {
    AND,
    OR,
    NOT;

    /**
     * Parses the facet_logic parameter; null, blank, or unrecognized values yield AND.
     */
    public static FacetLogic from(String value) {
        if (value == null || value.isBlank()) {
            return AND;
        }
        return switch (value.trim().toLowerCase()) {
            case "or" -> OR;
            case "not" -> NOT;
            default -> AND;
        };
    }
}
