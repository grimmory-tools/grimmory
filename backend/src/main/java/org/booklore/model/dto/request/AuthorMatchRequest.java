package org.booklore.model.dto.request;

import lombok.Data;
import org.booklore.model.enums.AuthorMetadataSource;

@Data
public class AuthorMatchRequest {
    private AuthorMetadataSource source;
    private String asin;
    private String region = "us";
}
