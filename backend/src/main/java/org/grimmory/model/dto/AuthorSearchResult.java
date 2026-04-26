package org.grimmory.model.dto;

import lombok.Builder;
import lombok.Data;
import org.grimmory.model.enums.AuthorMetadataSource;

@Data
@Builder
public class AuthorSearchResult {
    private AuthorMetadataSource source;
    private String asin;
    private String name;
    private String description;
    private String imageUrl;
}
