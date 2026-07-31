package org.booklore.app.service;

import org.booklore.app.dto.AppBookQuickSearchResult;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookMetadataEntity;

import java.util.List;

/**
 * Shared hydration of a {@link BookEntity} into the interactive search payload.
 * Used by both the lexical and the semantic catalog search.
 */
final class AppBookSearchResultMapper {

    private AppBookSearchResultMapper() {
    }

    static AppBookQuickSearchResult toResult(BookEntity book) {
        BookMetadataEntity metadata = book.getMetadata();
        BookFileEntity primaryFile = book.getPrimaryBookFile();
        return AppBookQuickSearchResult.builder()
                .id(book.getId())
                .title(metadata == null ? null : metadata.getTitle())
                .authors(metadata == null || metadata.getAuthors() == null
                        ? List.of()
                        : metadata.getAuthors().stream().map(AuthorEntity::getName).toList())
                .seriesName(metadata == null ? null : metadata.getSeriesName())
                .seriesNumber(metadata == null ? null : metadata.getSeriesNumber())
                .publishedDate(metadata == null ? null : metadata.getPublishedDate())
                .primaryFileType(primaryFile == null ? null : primaryFile.getBookType().name())
                .primaryFileDocumentParseStatus(primaryFile == null || primaryFile.getDocumentParseStatus() == null
                        ? null
                        : primaryFile.getDocumentParseStatus().name())
                .primaryFileName(primaryFile == null ? null : primaryFile.getFileName())
                .coverUpdatedOn(metadata == null ? null : metadata.getCoverUpdatedOn())
                .audiobookCoverUpdatedOn(metadata == null ? null : metadata.getAudiobookCoverUpdatedOn())
                .build();
    }
}
