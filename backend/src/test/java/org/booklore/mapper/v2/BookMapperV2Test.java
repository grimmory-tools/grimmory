package org.booklore.mapper.v2;

import org.booklore.model.dto.Book;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.enums.BookFileType;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BookMapperV2Test {

    private final BookMapperV2 mapper = Mappers.getMapper(BookMapperV2.class);

    @Test
    void shouldMapFilepathFieldsCorrectly() {
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

        entity.setBookFiles(List.of(primaryFile, supplementaryFile));
        entity.setLibrary(library);
        entity.setLibraryPath(libraryPath);

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
}
