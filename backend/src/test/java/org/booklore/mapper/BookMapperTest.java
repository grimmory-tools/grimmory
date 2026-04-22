package org.booklore.mapper;

import org.booklore.model.dto.Book;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.enums.BookFileType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class BookMapperTest {

    @Autowired
    private BookMapper mapper;

    @Test
    void shouldMapExistingFieldsCorrectly() {
        LibraryEntity library = new LibraryEntity();
        library.setId(123L);
        library.setName("Test Library");

        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setId(1L);
        libraryPath.setPath("/tmp");

        BookEntity entity = new BookEntity();
        entity.setId(1L);

        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(entity);
        primaryFile.setFileName("Test Book");
        primaryFile.setFileSubPath(".");
        primaryFile.setBookFormat(true);
        primaryFile.setBookType(BookFileType.EPUB);
        entity.setBookFiles(List.of(primaryFile));
        entity.setLibrary(library);
        entity.setLibraryPath(libraryPath);

        Book dto = mapper.toBook(entity);

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getLibraryId()).isEqualTo(123L);
        assertThat(dto.getLibraryName()).isEqualTo("Test Library");
        assertThat(dto.getLibraryPath()).isNotNull();
        assertThat(dto.getLibraryPath().getId()).isEqualTo(1L);
        assertThat(dto.getPrimaryFile().getBookType()).isEqualTo(BookFileType.EPUB);
        assertThat(dto.getAlternativeFormats()).isEmpty();
        assertThat(dto.getSupplementaryFiles()).isEmpty();

    }
}