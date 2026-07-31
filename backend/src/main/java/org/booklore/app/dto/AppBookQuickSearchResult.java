package org.booklore.app.dto;

import lombok.Builder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Builder
public record AppBookQuickSearchResult(
        Long id,
        String title,
        List<String> authors,
        String seriesName,
        Float seriesNumber,
        LocalDate publishedDate,
        String primaryFileType,
        String primaryFileDocumentParseStatus,
        String primaryFileName,
        Instant coverUpdatedOn,
        Instant audiobookCoverUpdatedOn) {
}
