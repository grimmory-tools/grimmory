package org.booklore.model.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
public class CreateAnnotationRequest {

    @NotNull(message = "Book ID is required")
    private Long bookId;

    @NotEmpty(message = "CFI is required")
    @Size(max = 1000, message = "CFI must not exceed 1000 characters")
    private String cfi;

    @NotEmpty(message = "Text is required")
    @Size(max = 5000, message = "Text must not exceed 5000 characters")
    private String text;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Color must be a valid hex color (e.g., #FFFF00)")
    private String color;

    @Pattern(regexp = "^(highlight|underline|strikethrough|squiggly)$", message = "Style must be one of: highlight, underline, strikethrough, squiggly")
    private String style;

    @Size(max = 5000, message = "Note must not exceed 5000 characters")
    private String note;

    @Size(max = 500, message = "Chapter title must not exceed 500 characters")
    private String chapterTitle;
}
