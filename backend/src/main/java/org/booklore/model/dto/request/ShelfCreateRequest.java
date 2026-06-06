package org.booklore.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.booklore.model.enums.IconType;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShelfCreateRequest {
    @Null(message = "Id should be null for creation.")
    private Long id;

    @NotBlank(message = "Shelf name must not be empty.")
    private String name;

    private String icon;
    private IconType iconType;

    private boolean publicShelf;
}
