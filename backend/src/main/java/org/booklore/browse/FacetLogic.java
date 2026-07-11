package org.booklore.browse;

public enum FacetLogic {
    AND,
    OR,
    NOT;

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
