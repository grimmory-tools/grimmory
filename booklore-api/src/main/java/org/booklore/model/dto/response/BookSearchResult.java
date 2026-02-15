package org.booklore.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookSearchResult {
    private Long id;
    private String title;
    private String hash;
    private String coverHash;
    private Double matchScore;
}
