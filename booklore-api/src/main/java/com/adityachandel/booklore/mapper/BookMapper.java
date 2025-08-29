package com.adityachandel.booklore.mapper;

import com.adityachandel.booklore.model.dto.AdditionalFile;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.LibraryPath;
import com.adityachandel.booklore.model.entity.AuthorEntity;
import com.adityachandel.booklore.model.entity.BookAdditionalFileEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.CategoryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.enums.AdditionalFileType;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", uses = {BookMetadataMapper.class, ShelfMapper.class, AdditionalFileMapper.class})
public interface BookMapper {

    @Mapping(source = "library.id", target = "libraryId")
    @Mapping(source = "libraryPath", target = "libraryPath", qualifiedByName = "mapLibraryPathIdOnly")
    @Mapping(source = "metadata", target = "metadata")
    @Mapping(source = "shelves", target = "shelves")
    @Mapping(source = "additionalFiles", target = "alternativeFormats", qualifiedByName = "mapAlternativeFormats")
    @Mapping(source = "additionalFiles", target = "supplementaryFiles", qualifiedByName = "mapSupplementaryFiles")
    Book toBook(BookEntity bookEntity);

    @Mapping(source = "library.id", target = "libraryId")
    @Mapping(source = "libraryPath", target = "libraryPath", qualifiedByName = "mapLibraryPathIdOnly")
    @Mapping(source = "metadata", target = "metadata")
    @Mapping(source = "shelves", target = "shelves")
    @Mapping(source = "additionalFiles", target = "alternativeFormats", qualifiedByName = "mapAlternativeFormats")
    @Mapping(source = "additionalFiles", target = "supplementaryFiles", qualifiedByName = "mapSupplementaryFiles")
    Book toBookWithDescription(BookEntity bookEntity, @Context boolean includeDescription);

    @Mapping(source = "library.id", target = "libraryId")
    @Mapping(source = "libraryPath", target = "libraryPath", qualifiedByName = "mapLibraryPathIdOnly")
    @Mapping(target = "metadata", ignore = true)
    @Mapping(target = "shelves", ignore = true)
    @Mapping(target = "alternativeFormats", ignore = true)
    @Mapping(target = "supplementaryFiles", ignore = true)
    Book toBookWithoutMetadataAndShelves(BookEntity bookEntity);

    default Set<String> mapAuthors(Set<AuthorEntity> authors) {
        if (authors == null) return null;
        return authors.stream()
                .map(AuthorEntity::getName)
                .collect(Collectors.toSet());
    }

    default Set<String> mapCategories(Set<CategoryEntity> categories) {
        if (categories == null) return null;
        return categories.stream()
                .map(CategoryEntity::getName)
                .collect(Collectors.toSet());
    }

    @Named("mapLibraryPathIdOnly")
    default LibraryPath mapLibraryPathIdOnly(LibraryPathEntity entity) {
        if (entity == null) return null;
        return LibraryPath.builder()
                .id(entity.getId())
                .build();
    }

    @Named("mapAlternativeFormats")
    default List<AdditionalFile> mapAlternativeFormats(List<BookAdditionalFileEntity> additionalFiles) {
        if (additionalFiles == null) return null;
        return additionalFiles.stream()
                .filter(af -> AdditionalFileType.ALTERNATIVE_FORMAT.equals(af.getAdditionalFileType()))
                .map(this::toAdditionalFile)
                .toList();
    }

    @Named("mapSupplementaryFiles")
    default List<AdditionalFile> mapSupplementaryFiles(List<BookAdditionalFileEntity> additionalFiles) {
        if (additionalFiles == null) return null;
        return additionalFiles.stream()
                .filter(af -> AdditionalFileType.SUPPLEMENTARY.equals(af.getAdditionalFileType()))
                .map(this::toAdditionalFile)
                .toList();
    }

    default AdditionalFile toAdditionalFile(BookAdditionalFileEntity entity) {
        if (entity == null) return null;
        return AdditionalFile.builder()
                .id(entity.getId())
                .bookId(entity.getBook().getId())
                .fileName(entity.getFileName())
                .filePath(entity.getFullFilePath().toString())
                .fileSubPath(entity.getFileSubPath())
                .additionalFileType(entity.getAdditionalFileType())
                .fileSizeKb(entity.getFileSizeKb())
                .description(entity.getDescription())
                .addedOn(entity.getAddedOn())
                .build();
    }
}
