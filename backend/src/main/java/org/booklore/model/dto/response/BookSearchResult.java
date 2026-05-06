package org.booklore.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookSearchResult {
    private Long id;
    private String title;
    private String author;
    private String isbn10;
    private String isbn13;
    private Double matchScore;
}
