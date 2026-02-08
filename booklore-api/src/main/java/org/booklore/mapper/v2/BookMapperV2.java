package org.booklore.mapper.v2;

import org.booklore.mapper.ComicMetadataMapper;
import org.booklore.mapper.ShelfMapper;
import org.booklore.model.dto.*;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", uses = {ShelfMapper.class, ComicMetadataMapper.class}, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface BookMapperV2 {

    @Mapping(source = "library.id", target = "libraryId")
    @Mapping(source = "library.name", target = "libraryName")
    @Mapping(source = "libraryPath", target = "libraryPath", qualifiedByName = "mapLibraryPathIdOnly")
    @Mapping(source = "bookFiles", target = "primaryFile", qualifiedByName = "mapPrimaryFile")
    @Mapping(source = "bookFiles", target = "alternativeFormats", qualifiedByName = "mapAlternativeFormats")
    @Mapping(source = "bookFiles", target = "supplementaryFiles", qualifiedByName = "mapSupplementaryFiles")
    @Mapping(target = "metadata", qualifiedByName = "mapMetadata")
    Book toDTO(BookEntity bookEntity);

    @Named("mapMetadata")
    @Mapping(target = "authors", source = "authors", qualifiedByName = "mapAuthors")
    @Mapping(target = "categories", source = "categories", qualifiedByName = "mapCategories")
    @Mapping(target = "moods", source = "moods", qualifiedByName = "mapMoods")
    @Mapping(target = "tags", source = "tags", qualifiedByName = "mapTags")
    BookMetadata mapMetadata(BookMetadataEntity metadataEntity);

    @AfterMapping
    default void mapAudiobookMetadata(BookEntity bookEntity, @MappingTarget Book book) {
        if (book.getMetadata() == null) {
            return;
        }

        BookMetadataEntity metadataEntity = bookEntity.getMetadata();
        BookMetadata metadata = book.getMetadata();

        if (metadataEntity != null) {
            metadata.setNarrator(metadataEntity.getNarrator());
            metadata.setAbridged(metadataEntity.getAbridged());
            metadata.setNarratorLocked(metadataEntity.getNarratorLocked());
            metadata.setAbridgedLocked(metadataEntity.getAbridgedLocked());
        }

        BookFileEntity audiobookFile = bookEntity.getBookFiles() != null
                ? bookEntity.getBookFiles().stream()
                    .filter(bf -> bf.getBookType() == BookFileType.AUDIOBOOK && bf.isBook())
                    .findFirst()
                    .orElse(null)
                : null;

        if (audiobookFile != null && audiobookFile.getDurationSeconds() != null) {
            List<AudiobookMetadata.ChapterInfo> chapters = null;
            if (audiobookFile.getChapters() != null) {
                chapters = audiobookFile.getChapters().stream()
                        .map(ch -> AudiobookMetadata.ChapterInfo.builder()
                                .index(ch.getIndex())
                                .title(ch.getTitle())
                                .startTimeMs(ch.getStartTimeMs())
                                .endTimeMs(ch.getEndTimeMs())
                                .durationMs(ch.getDurationMs())
                                .build())
                        .toList();
            }

            metadata.setAudiobookMetadata(
                    AudiobookMetadata.builder()
                            .durationSeconds(audiobookFile.getDurationSeconds())
                            .bitrate(audiobookFile.getBitrate())
                            .sampleRate(audiobookFile.getSampleRate())
                            .channels(audiobookFile.getChannels())
                            .codec(audiobookFile.getCodec())
                            .chapterCount(audiobookFile.getChapterCount())
                            .chapters(chapters)
                            .build()
            );
        }
    }

    @Named("mapAuthors")
    default Set<String> mapAuthors(Set<AuthorEntity> authors) {
        return authors == null ? Set.of() :
                authors.stream().map(AuthorEntity::getName).collect(Collectors.toSet());
    }

    @Named("mapCategories")
    default Set<String> mapCategories(Set<CategoryEntity> categories) {
        return categories == null ? Set.of() :
                categories.stream().map(CategoryEntity::getName).collect(Collectors.toSet());
    }

    @Named("mapMoods")
    default Set<String> mapMoods(Set<MoodEntity> moods) {
        return moods == null ? Set.of() :
                moods.stream().map(MoodEntity::getName).collect(Collectors.toSet());
    }

    @Named("mapTags")
    default Set<String> mapTags(Set<TagEntity> tags) {
        return tags == null ? Set.of() :
                tags.stream().map(TagEntity::getName).collect(Collectors.toSet());
    }

    @Named("mapPrimaryFile")
    default BookFile mapPrimaryFile(List<BookFileEntity> bookFiles) {
        BookFileEntity primary = getPrimaryBookFile(bookFiles);
        return toBookFile(primary);
    }

    @Named("mapAlternativeFormats")
    default List<BookFile> mapAlternativeFormats(List<BookFileEntity> bookFiles) {
        if (bookFiles == null) return null;
        BookFileEntity primary = getPrimaryBookFile(bookFiles);
        return bookFiles.stream()
                .filter(BookFileEntity::isBook)
                .filter(bf -> !bf.equals(primary))
                .map(this::toBookFile)
                .toList();
    }

    @Named("mapSupplementaryFiles")
    default List<BookFile> mapSupplementaryFiles(List<BookFileEntity> bookFiles) {
        if (bookFiles == null) return null;
        return bookFiles.stream()
                .filter(bf -> !bf.isBook())
                .map(this::toBookFile)
                .toList();
    }

    default BookFileEntity getPrimaryBookFile(List<BookFileEntity> bookFiles) {
        if (bookFiles == null || bookFiles.isEmpty()) return null;

        List<BookFileEntity> bookFormats = bookFiles.stream()
                .filter(BookFileEntity::isBook)
                .toList();

        if (bookFormats.isEmpty()) return null;

        BookFileEntity firstBook = bookFormats.getFirst();
        LibraryEntity library = firstBook.getBook() != null ? firstBook.getBook().getLibrary() : null;

        if (library != null && library.getFormatPriority() != null && !library.getFormatPriority().isEmpty()) {
            for (BookFileType format : library.getFormatPriority()) {
                var match = bookFormats.stream()
                        .filter(bf -> bf.getBookType() == format)
                        .findFirst();
                if (match.isPresent()) {
                    return match.get();
                }
            }
        }
        return firstBook;
    }

    default BookFile toBookFile(BookFileEntity entity) {
        if (entity == null) return null;
        return BookFile.builder()
                .id(entity.getId())
                .bookId(entity.getBook().getId())
                .fileName(entity.getFileName())
                .filePath(entity.getFullFilePath().toString())
                .fileSubPath(entity.getFileSubPath())
                .isBook(entity.isBook())
                .bookType(entity.getBookType())
                .archiveType(entity.getArchiveType())
                .fileSizeKb(entity.getFileSizeKb())
                .extension(extractExtension(entity))
                .description(entity.getDescription())
                .addedOn(entity.getAddedOn())
                .build();
    }

    default String extractExtension(BookFileEntity entity) {
        if (entity == null) return null;
        String fileName;
        if (entity.isFolderBased()) {
            var firstFile = entity.getFirstAudioFile();
            fileName = firstFile != null ? firstFile.getFileName().toString() : null;
        } else {
            fileName = entity.getFileName();
        }
        if (fileName == null) return null;
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot + 1).toLowerCase() : null;
    }

    @Named("mapLibraryPathIdOnly")
    default LibraryPath mapLibraryPathIdOnly(LibraryPathEntity entity) {
        if (entity == null) return null;
        return LibraryPath.builder()
                .id(entity.getId())
                .build();
    }
}
