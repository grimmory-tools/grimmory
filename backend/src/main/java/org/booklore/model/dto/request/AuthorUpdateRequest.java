package org.booklore.model.dto.request;

import lombok.Data;

@Data
public class AuthorUpdateRequest {
    private String name;
    private String description;
    private String asin;
    private Boolean nameLocked;
    private Boolean descriptionLocked;
    private Boolean asinLocked;
    private Boolean photoLocked;
}
