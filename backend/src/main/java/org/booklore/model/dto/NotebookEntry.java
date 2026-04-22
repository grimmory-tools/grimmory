package org.booklore.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotebookEntry {
    private Long id;
    private String type;
    private Long bookId;
    private String bookTitle;
    private String text;
    private String note;
    private String color;
    private String style;
    private String chapterTitle;
    private String primaryBookType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
