package org.booklore.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBookNoteRequest {
    private Long id;

    @NotNull(message = "Book ID is required")
    private Long bookId;

    private String title;

    @NotBlank(message = "Content is required")
    private String content;
}