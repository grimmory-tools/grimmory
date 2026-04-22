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
public class BookMark {
    private Long id;
    private Long userId;
    private Long bookId;
    private String cfi;           // For EPUB bookmarks
    private Long positionMs;      // For audiobook bookmarks
    private Integer trackIndex;   // For folder-based audiobooks
    private Integer pageNumber;    // For PDF bookmarks
    private String title;
    private String color;
    private String notes;
    private Integer priority;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
