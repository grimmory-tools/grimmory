package org.booklore.opf;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.settings.LibraryFile;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.service.book.BookCreatorService;
import org.booklore.util.FileService;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdjacentOpfMetadataAugmenter implements BookScanMetadataAugmenter {

    private final AdjacentOpfLocator locator;
    private final OpfMetadataExtractor extractor;
    private final BookCreatorService bookCreatorService;

    @Override
    public void augment(LibraryFile libraryFile, BookEntity bookEntity) {
        locator.find(libraryFile)
                .flatMap(extractor::extract)
                .ifPresent(metadata -> apply(metadata, bookEntity));
    }

    void apply(BookMetadata source, BookEntity bookEntity) {
        if (source == null || bookEntity == null || bookEntity.getMetadata() == null) {
            return;
        }

        BookMetadataEntity target = bookEntity.getMetadata();
        if (!locked(target.getTitleLocked()) && StringUtils.isNotBlank(source.getTitle())) {
            target.setTitle(FileService.truncate(source.getTitle(), 1000));
        }
        if (!locked(target.getPublisherLocked()) && StringUtils.isNotBlank(source.getPublisher())) {
            target.setPublisher(FileService.truncate(source.getPublisher(), 1000));
        }
        if (!locked(target.getPublishedDateLocked()) && source.getPublishedDate() != null) {
            target.setPublishedDate(source.getPublishedDate());
        }
        if (!locked(target.getDescriptionLocked()) && StringUtils.isNotBlank(source.getDescription())) {
            target.setDescription(FileService.truncate(source.getDescription(), 5000));
        }
        if (!locked(target.getLanguageLocked()) && StringUtils.isNotBlank(source.getLanguage())) {
            target.setLanguage(FileService.truncate(source.getLanguage(), 10));
        }
        if (!locked(target.getIsbn10Locked()) && StringUtils.isNotBlank(source.getIsbn10())) {
            target.setIsbn10(FileService.truncate(source.getIsbn10(), 10));
        }
        if (!locked(target.getIsbn13Locked()) && StringUtils.isNotBlank(source.getIsbn13())) {
            target.setIsbn13(FileService.truncate(source.getIsbn13(), 13));
        }
        if (!locked(target.getSeriesNameLocked()) && StringUtils.isNotBlank(source.getSeriesName())) {
            target.setSeriesName(FileService.truncate(source.getSeriesName(), 1000));
        }
        if (!locked(target.getSeriesNumberLocked()) && source.getSeriesNumber() != null) {
            target.setSeriesNumber(source.getSeriesNumber());
        }
        if (!locked(target.getAuthorsLocked()) && source.getAuthors() != null && !source.getAuthors().isEmpty()) {
            bookCreatorService.addAuthorsToBook(source.getAuthors(), bookEntity);
        }
        if (!locked(target.getCategoriesLocked()) && source.getCategories() != null && !source.getCategories().isEmpty()) {
            bookCreatorService.addCategoriesToBook(source.getCategories(), bookEntity);
        }
    }

    private boolean locked(Boolean value) {
        return Boolean.TRUE.equals(value);
    }
}
