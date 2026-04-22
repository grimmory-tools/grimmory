package org.booklore.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthorDetails {
    private Long id;
    private String name;
    private String description;
    private String asin;
    private boolean nameLocked;
    private boolean descriptionLocked;
    private boolean asinLocked;
    private boolean photoLocked;
}
