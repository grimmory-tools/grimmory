package org.booklore.model.dto;

import org.booklore.model.enums.IconType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MagicShelf {

    private Long id;

    @NotBlank(message = "Shelf name must not be blank")
    @Size(max = 255, message = "Shelf name must not exceed 255 characters")
    private String name;

    @Size(max = 64, message = "Icon must not exceed 64 characters")
    private String icon;

    private IconType iconType;

    @NotNull(message = "Filter JSON must not be null")
    @Size(min = 2, message = "Filter JSON must not be empty")
    private String filterJson;

    private Boolean isPublic = false;
}
