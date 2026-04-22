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
public class CreateBookNoteV2Request {

    @NotNull(message = "Book ID is required")
    private Long bookId;

    @NotEmpty(message = "CFI is required")
    @Size(max = 1000, message = "CFI must not exceed 1000 characters")
    private String cfi;

    @Size(max = 5000, message = "Selected text must not exceed 5000 characters")
    private String selectedText;

    @NotEmpty(message = "Note content is required")
    private String noteContent;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Color must be a valid hex color (e.g., #FFFF00)")
    private String color;

    @Size(max = 500, message = "Chapter title must not exceed 500 characters")
    private String chapterTitle;
}
