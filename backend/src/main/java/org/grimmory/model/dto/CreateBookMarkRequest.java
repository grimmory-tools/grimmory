package org.grimmory.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBookMarkRequest {
    @NotNull
    private Long bookId;

    // For EPUB bookmarks
    private String cfi;

    // For audiobook bookmarks
    private Long positionMs;
    private Integer trackIndex;

    // For PDF bookmarks
    @Min(value = 1, message = "Page number must be at least 1")
    private Integer pageNumber;

    private String title;

    /**
     * Check if this is an audiobook bookmark (has positionMs) vs EPUB bookmark (has cfi)
     */
    public boolean isAudiobookBookmark() {
        return positionMs != null;
    }

    /**
     * Check if this is a PDF bookmark (has pageNumber, no cfi or positionMs)
     */
    public boolean isPdfBookmark() {
        return pageNumber != null && cfi == null && positionMs == null;
    }
}
