package org.booklore.mapper.v2;

import org.booklore.mapper.BookMapper;
import org.booklore.model.dto.Book;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.enums.BookFileType;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class BookMapperV2Test {
    @Autowired
    private BookMapperV2 mapper;

    private BookEntity createBook() {
        LibraryEntity library = new LibraryEntity();
        library.setId(123L);
        library.setName("Test Library");

        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setId(1L);
        libraryPath.setPath("/tmp/test-library");

        BookEntity entity = new BookEntity();
        entity.setId(1L);

        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(entity);
        primaryFile.setFileName("test-book.epub");
        primaryFile.setFileSubPath("fiction/science-fiction");
        primaryFile.setBookFormat(true);
        primaryFile.setBookType(BookFileType.EPUB);
        primaryFile.setFileSizeKb(1024L);

        BookFileEntity supplementaryFile = new BookFileEntity();
        supplementaryFile.setBook(entity);
        supplementaryFile.setFileName("test-book-cover.jpg");
        supplementaryFile.setFileSubPath("fiction/science-fiction");
        supplementaryFile.setBookFormat(false);
        supplementaryFile.setBookType(BookFileType.EPUB);
        supplementaryFile.setFileSizeKb(256L);

        entity.setBookFiles(Set.of(primaryFile, supplementaryFile));
        entity.setLibrary(library);
        entity.setLibraryPath(libraryPath);

        return entity;
    }

    @Test
    void shouldMapFilepathFieldsCorrectly() {
        BookEntity entity = createBook();

        Book dto = mapper.toDTO(entity);

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getLibraryId()).isEqualTo(123L);
        assertThat(dto.getLibraryName()).isEqualTo("Test Library");
        assertThat(dto.getLibraryPath()).isNotNull();
        assertThat(dto.getLibraryPath().getId()).isEqualTo(1L);

        assertThat(dto.getPrimaryFile()).isNotNull();
        assertThat(dto.getPrimaryFile().getBookType()).isEqualTo(BookFileType.EPUB);
        assertThat(dto.getPrimaryFile().getFileName()).isEqualTo("test-book.epub");
        assertThat(dto.getPrimaryFile().getFileSubPath()).isEqualTo("fiction/science-fiction");
        assertThat(dto.getPrimaryFile().getFileSizeKb()).isEqualTo(1024L);
        assertThat(dto.getPrimaryFile().getFilePath()).isNotNull();
        assertThat(dto.getAlternativeFormats()).isEmpty();

        assertThat(dto.getSupplementaryFiles()).hasSize(1);
        assertThat(dto.getSupplementaryFiles().get(0).getFileName()).isEqualTo("test-book-cover.jpg");
        assertThat(dto.getSupplementaryFiles().get(0).isBook()).isFalse();
    }

    @Test
    void metadataIsNull() {
        BookEntity entity = createBook();
        entity.setMetadata(null);

        Book dto = mapper.toDTO(entity);

        assertThat(dto.getMetadata()).isNull();
    }

    @Test
    void allMetadataLocked_unlockedMetadata() {
        BookEntity entity = createBook();

        BookMetadataEntity bookMetadataEntity = new BookMetadataEntity();

        entity.setMetadata(bookMetadataEntity);

        Book dto = mapper.toDTO(entity);

        assertThat(dto.getMetadata().getAllMetadataLocked()).isFalse();
    }

    @Test
    void allMetadataLocked_partiallyLockedMetadata() {
        BookEntity entity = createBook();

        BookMetadataEntity bookMetadataEntity = new BookMetadataEntity();
        bookMetadataEntity.setTitleLocked(true);
        entity.setMetadata(bookMetadataEntity);

        Book dto = mapper.toDTO(entity);

        assertThat(dto.getMetadata().getAllMetadataLocked()).isFalse();
    }

    @Test
    void allMetadataLocked_lockedMetadata() {
        BookEntity entity = createBook();

        BookMetadataEntity bookMetadataEntity = new BookMetadataEntity();
        bookMetadataEntity.setDescription("Foo");
        bookMetadataEntity.applyLockToAllFields(true);
        entity.setMetadata(bookMetadataEntity);

        Book dto = mapper.toDTO(entity);

        assertThat(dto.getMetadata().getAllMetadataLocked()).isTrue();
    }
}
