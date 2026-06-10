package org.booklore.browse;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * The resource-agnostic browse response envelope reused by every paginated endpoint. An
 * endpoint may serialize its own back-compat shape instead of returning this directly.
 *
 * @param <T> the element DTO type
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BrowsePage<T>(List<T> content, PageMetadata page, List<Link> links) {

    /**
     * Pagination metadata. number and totalPages are derived from offset, limit, and total so
     * offset-style clients keep working; cursor is the opaque token for the current page.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PageMetadata(int number, int size, long totalElements, int totalPages, String cursor) {
    }

    public static <T> BrowsePage<T> of(List<T> content, long offset, int limit, long totalElements, String cursor, List<Link> links) {
        int number = limit > 0 ? (int) (offset / limit) : 0;
        int totalPages = limit > 0 ? (int) Math.ceil((double) totalElements / limit) : 0;
        return new BrowsePage<>(content, new PageMetadata(number, limit, totalElements, totalPages, cursor), links);
    }
}
