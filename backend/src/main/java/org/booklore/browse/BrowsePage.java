package org.booklore.browse;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BrowsePage<T>(List<T> content, PageMetadata page, List<Link> links) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PageMetadata(int number, int size, long totalElements, int totalPages, String cursor) {
    }

    public static <T> BrowsePage<T> of(List<T> content, long offset, int limit, long totalElements, String cursor, List<Link> links) {
        int number = limit > 0 ? (int) (offset / limit) : 0;
        int totalPages = limit > 0 ? (int) Math.ceil((double) totalElements / limit) : 0;
        return new BrowsePage<>(content, new PageMetadata(number, limit, totalElements, totalPages, cursor), links);
    }
}
