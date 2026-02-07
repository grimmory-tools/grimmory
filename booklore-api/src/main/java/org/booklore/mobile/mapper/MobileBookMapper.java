package org.booklore.mobile.mapper;

import org.booklore.mobile.dto.MobileBookDetail;
import org.booklore.mobile.dto.MobileBookFile;
import org.booklore.mobile.dto.MobileBookSummary;
import org.booklore.mobile.dto.MobileLibrarySummary;
import org.booklore.mobile.dto.MobileMagicShelfSummary;
import org.booklore.mobile.dto.MobileShelfSummary;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.mapstruct.*;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MobileBookMapper {

    @Mapping(target = "id", source = "book.id")
    @Mapping(target = "title", source = "book.metadata.title")
    @Mapping(target = "authors", source = "book.metadata.authors", qualifiedByName = "mapAuthors")
    @Mapping(target = "thumbnailUrl", source = "book", qualifiedByName = "mapThumbnailUrl")
    @Mapping(target = "readStatus", source = "progress.readStatus")
    @Mapping(target = "personalRating", source = "progress.personalRating")
    @Mapping(target = "seriesName", source = "book.metadata.seriesName")
    @Mapping(target = "seriesNumber", source = "book.metadata.seriesNumber")
    @Mapping(target = "libraryId", source = "book.library.id")
    @Mapping(target = "addedOn", source = "book.addedOn")
    @Mapping(target = "lastReadTime", source = "progress.lastReadTime")
    @Mapping(target = "readProgress", source = "progress", qualifiedByName = "mapReadProgress")
    @Mapping(target = "primaryFileType", source = "book", qualifiedByName = "mapPrimaryFileType")
    MobileBookSummary toSummary(BookEntity book, UserBookProgressEntity progress);

    @Mapping(target = "id", source = "book.id")
    @Mapping(target = "title", source = "book.metadata.title")
    @Mapping(target = "authors", source = "book.metadata.authors", qualifiedByName = "mapAuthors")
    @Mapping(target = "thumbnailUrl", source = "book", qualifiedByName = "mapThumbnailUrl")
    @Mapping(target = "readStatus", source = "progress.readStatus")
    @Mapping(target = "personalRating", source = "progress.personalRating")
    @Mapping(target = "seriesName", source = "book.metadata.seriesName")
    @Mapping(target = "seriesNumber", source = "book.metadata.seriesNumber")
    @Mapping(target = "libraryId", source = "book.library.id")
    @Mapping(target = "addedOn", source = "book.addedOn")
    @Mapping(target = "lastReadTime", source = "progress.lastReadTime")
    @Mapping(target = "subtitle", source = "book.metadata.subtitle")
    @Mapping(target = "description", source = "book.metadata.description")
    @Mapping(target = "categories", source = "book.metadata.categories", qualifiedByName = "mapCategories")
    @Mapping(target = "publisher", source = "book.metadata.publisher")
    @Mapping(target = "publishedDate", source = "book.metadata.publishedDate")
    @Mapping(target = "pageCount", source = "book.metadata.pageCount")
    @Mapping(target = "isbn13", source = "book.metadata.isbn13")
    @Mapping(target = "language", source = "book.metadata.language")
    @Mapping(target = "goodreadsRating", source = "book.metadata.goodreadsRating")
    @Mapping(target = "goodreadsReviewCount", source = "book.metadata.goodreadsReviewCount")
    @Mapping(target = "libraryName", source = "book.library.name")
    @Mapping(target = "shelves", source = "book.shelves", qualifiedByName = "mapShelves")
    @Mapping(target = "readProgress", source = "progress", qualifiedByName = "mapReadProgress")
    @Mapping(target = "primaryFileType", source = "book", qualifiedByName = "mapPrimaryFileType")
    @Mapping(target = "fileTypes", source = "book", qualifiedByName = "mapFileTypes")
    @Mapping(target = "files", source = "book", qualifiedByName = "mapFiles")
    @Mapping(target = "epubProgress", source = "progress", qualifiedByName = "mapEpubProgress")
    @Mapping(target = "pdfProgress", source = "progress", qualifiedByName = "mapPdfProgress")
    @Mapping(target = "cbxProgress", source = "progress", qualifiedByName = "mapCbxProgress")
    @Mapping(target = "audiobookProgress", source = "fileProgress", qualifiedByName = "mapAudiobookProgress")
    MobileBookDetail toDetail(BookEntity book, UserBookProgressEntity progress, UserBookFileProgressEntity fileProgress);

    @Named("mapAuthors")
    default List<String> mapAuthors(Set<AuthorEntity> authors) {
        if (authors == null || authors.isEmpty()) {
            return Collections.emptyList();
        }
        return authors.stream()
                .map(AuthorEntity::getName)
                .collect(Collectors.toList());
    }

    @Named("mapCategories")
    default Set<String> mapCategories(Set<CategoryEntity> categories) {
        if (categories == null || categories.isEmpty()) {
            return Collections.emptySet();
        }
        return categories.stream()
                .map(CategoryEntity::getName)
                .collect(Collectors.toSet());
    }

    @Named("mapThumbnailUrl")
    default String mapThumbnailUrl(BookEntity book) {
        if (book == null || book.getId() == null) {
            return null;
        }
        return "/api/books/" + book.getId() + "/cover";
    }

    @Named("mapShelves")
    default List<MobileShelfSummary> mapShelves(Set<ShelfEntity> shelves) {
        if (shelves == null || shelves.isEmpty()) {
            return Collections.emptyList();
        }
        return shelves.stream()
                .map(this::toShelfSummary)
                .collect(Collectors.toList());
    }

    default MobileShelfSummary toShelfSummary(ShelfEntity shelf) {
        if (shelf == null) {
            return null;
        }
        return MobileShelfSummary.builder()
                .id(shelf.getId())
                .name(shelf.getName())
                .icon(shelf.getIcon())
                .bookCount(shelf.getBookEntities() != null ? shelf.getBookEntities().size() : 0)
                .publicShelf(shelf.isPublic())
                .build();
    }

    @Named("mapReadProgress")
    default Float mapReadProgress(UserBookProgressEntity progress) {
        if (progress == null) {
            return null;
        }
        if (progress.getKoreaderProgressPercent() != null) {
            return progress.getKoreaderProgressPercent();
        }
        if (progress.getKoboProgressPercent() != null) {
            return progress.getKoboProgressPercent();
        }
        if (progress.getEpubProgressPercent() != null) {
            return progress.getEpubProgressPercent();
        }
        if (progress.getPdfProgressPercent() != null) {
            return progress.getPdfProgressPercent();
        }
        if (progress.getCbxProgressPercent() != null) {
            return progress.getCbxProgressPercent();
        }
        return null;
    }

    @Named("mapEpubProgress")
    default MobileBookDetail.EpubProgress mapEpubProgress(UserBookProgressEntity progress) {
        if (progress == null || progress.getEpubProgress() == null) {
            return null;
        }
        return MobileBookDetail.EpubProgress.builder()
                .cfi(progress.getEpubProgress())
                .href(progress.getEpubProgressHref())
                .percentage(progress.getEpubProgressPercent())
                .updatedAt(progress.getLastReadTime())
                .build();
    }

    @Named("mapPdfProgress")
    default MobileBookDetail.PdfProgress mapPdfProgress(UserBookProgressEntity progress) {
        if (progress == null || progress.getPdfProgress() == null) {
            return null;
        }
        return MobileBookDetail.PdfProgress.builder()
                .page(progress.getPdfProgress())
                .percentage(progress.getPdfProgressPercent())
                .updatedAt(progress.getLastReadTime())
                .build();
    }

    @Named("mapCbxProgress")
    default MobileBookDetail.CbxProgress mapCbxProgress(UserBookProgressEntity progress) {
        if (progress == null || progress.getCbxProgress() == null) {
            return null;
        }
        return MobileBookDetail.CbxProgress.builder()
                .page(progress.getCbxProgress())
                .percentage(progress.getCbxProgressPercent())
                .updatedAt(progress.getLastReadTime())
                .build();
    }

    @Named("mapAudiobookProgress")
    default MobileBookDetail.AudiobookProgress mapAudiobookProgress(UserBookFileProgressEntity fileProgress) {
        if (fileProgress == null) return null;
        if (fileProgress.getBookFile() == null ||
            fileProgress.getBookFile().getBookType() != BookFileType.AUDIOBOOK) {
            return null;
        }

        return MobileBookDetail.AudiobookProgress.builder()
                .positionMs(parseLongOrNull(fileProgress.getPositionData()))
                .trackIndex(parseIntOrNull(fileProgress.getPositionHref()))
                .percentage(fileProgress.getProgressPercent())
                .updatedAt(fileProgress.getLastReadTime())
                .build();
    }

    default Long parseLongOrNull(String value) {
        if (value == null) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    default Integer parseIntOrNull(String value) {
        if (value == null) return null;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Named("mapPrimaryFileType")
    default String mapPrimaryFileType(BookEntity book) {
        if (book == null) {
            return null;
        }
        BookFileEntity primaryFile = book.getPrimaryBookFile();
        if (primaryFile != null && primaryFile.getBookType() != null) {
            return primaryFile.getBookType().name();
        }
        return null;
    }

    @Named("mapFileTypes")
    default List<String> mapFileTypes(BookEntity book) {
        if (book == null || book.getBookFiles() == null || book.getBookFiles().isEmpty()) {
            return Collections.emptyList();
        }
        return book.getBookFiles().stream()
                .filter(bf -> bf.getBookType() != null)
                .map(bf -> bf.getBookType().name())
                .distinct()
                .collect(Collectors.toList());
    }

    @Named("mapFiles")
    default List<MobileBookFile> mapFiles(BookEntity book) {
        if (book == null || book.getBookFiles() == null || book.getBookFiles().isEmpty()) {
            return Collections.emptyList();
        }
        BookFileEntity primaryFile = book.getPrimaryBookFile();
        Long primaryId = primaryFile != null ? primaryFile.getId() : null;

        return book.getBookFiles().stream()
                .filter(bf -> bf.getBookType() != null && bf.isBook())
                .map(bf -> {
                    String extension = null;
                    try {
                        String fileName = bf.getFileName();
                        int lastDot = fileName.lastIndexOf('.');
                        if (lastDot > 0) {
                            extension = fileName.substring(lastDot + 1);
                        }
                    } catch (Exception e) {
                        // Handle case where extension cannot be extracted
                    }

                    return MobileBookFile.builder()
                            .id(bf.getId())
                            .bookId(bf.getBook() != null ? bf.getBook().getId() : null)
                            .fileName(bf.getFileName())
                            .isBook(bf.isBook())
                            .folderBased(bf.isFolderBased())
                            .bookType(bf.getBookType().name())
                            .archiveType(bf.getArchiveType() != null ? bf.getArchiveType().name() : null)
                            .fileSizeKb(bf.getFileSizeKb())
                            .extension(extension)
                            .addedOn(bf.getAddedOn())
                            .isPrimary(bf.getId().equals(primaryId))
                            .build();
                })
                .collect(Collectors.toList());
    }

    default MobileLibrarySummary toLibrarySummary(LibraryEntity library, long bookCount) {
        if (library == null) {
            return null;
        }
        return MobileLibrarySummary.builder()
                .id(library.getId())
                .name(library.getName())
                .icon(library.getIcon())
                .bookCount(bookCount)
                .build();
    }

    default MobileShelfSummary toShelfSummaryFromEntity(ShelfEntity shelf) {
        if (shelf == null) {
            return null;
        }
        return MobileShelfSummary.builder()
                .id(shelf.getId())
                .name(shelf.getName())
                .icon(shelf.getIcon())
                .bookCount(shelf.getBookEntities() != null ? shelf.getBookEntities().size() : 0)
                .publicShelf(shelf.isPublic())
                .build();
    }

    default MobileMagicShelfSummary toMagicShelfSummary(MagicShelfEntity magicShelf) {
        if (magicShelf == null) {
            return null;
        }
        return MobileMagicShelfSummary.builder()
                .id(magicShelf.getId())
                .name(magicShelf.getName())
                .icon(magicShelf.getIcon())
                .iconType(magicShelf.getIconType() != null ? magicShelf.getIconType().name() : null)
                .publicShelf(magicShelf.isPublic())
                .build();
    }
}
