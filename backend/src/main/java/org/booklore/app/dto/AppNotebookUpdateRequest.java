package org.booklore.app.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppNotebookUpdateRequest {
    @Size(max = 5000)
    private String note;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$")
    private String color;
}
