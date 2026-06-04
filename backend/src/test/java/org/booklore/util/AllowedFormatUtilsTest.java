package org.booklore.util;

import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.enums.BookFileType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AllowedFormatUtilsTest {

    @Test
    void isAllowedBookFile_allowsAnySupportedBookFileWhenAllowedFormatsIsNull() {
        LibraryEntity library = LibraryEntity.builder()
                .allowedFormats(null)
                .build();

        assertThat(AllowedFormatUtils.isAllowedBookFile(library, "book.pdf")).isTrue();
    }

    @Test
    void isAllowedBookFile_allowsAnySupportedBookFileWhenAllowedFormatsIsEmpty() {
        LibraryEntity library = LibraryEntity.builder()
                .allowedFormats(List.of())
                .build();

        assertThat(AllowedFormatUtils.isAllowedBookFile(library, "book.pdf")).isTrue();
    }

    @Test
    void isAllowedBookFile_rejectsBookFileOutsideConfiguredFormats() {
        LibraryEntity library = LibraryEntity.builder()
                .allowedFormats(List.of(BookFileType.EPUB))
                .build();

        assertThat(AllowedFormatUtils.isAllowedBookFile(library, "book.pdf")).isFalse();
    }
}
