package org.booklore.browse;

public enum FacetLogic {
    AND,
    OR,
    NOT;

    public static FacetLogic from(String value) {
        return switch (value == null ? "" : value.trim().toLowerCase()) {
            case "and" -> AND;
            case "not" -> NOT;
            default -> OR;
        };
    }
}
