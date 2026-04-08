package org.booklore.app.dto;

import jakarta.validation.constraints.Size;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.ReadStatus;

import java.util.List;

public record BookListRequest(
        Integer page,
        Integer size,
        String sort,
        String dir,
        Long libraryId,
        Long shelfId,
        ReadStatus status,
        String search,
        BookFileType fileType,
        Integer minRating,
        Integer maxRating,
        @Size(max = 20) List<String> authors,
        @Size(max = 20) List<String> language,
        @Size(max = 20) List<String> series,
        @Size(max = 20) List<String> category,
        @Size(max = 20) List<String> publisher,
        @Size(max = 20) List<String> tag,
        @Size(max = 20) List<String> mood,
        @Size(max = 20) List<String> narrator,
        Long magicShelfId,
        Boolean unshelved,
        String filterMode) {

    /**
     * Returns the effective filter mode, defaulting to "or" when not specified.
     */
    public String effectiveFilterMode() {
        if (filterMode == null || filterMode.isBlank()) return "or";
        return switch (filterMode.toLowerCase()) {
            case "and", "not" -> filterMode.toLowerCase();
            default -> "or";
        };
    }

    private static boolean hasValues(List<String> list) {
        return list != null && !list.isEmpty() && list.stream().anyMatch(s -> s != null && !s.isBlank());
    }

    /**
     * Returns non-blank trimmed values from the given list, or an empty list.
     */
    public static List<String> cleanValues(List<String> list) {
        if (list == null) return List.of();
        return list.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .toList();
    }
}
