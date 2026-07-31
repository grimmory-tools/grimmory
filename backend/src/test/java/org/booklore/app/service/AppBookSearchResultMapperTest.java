package org.booklore.app.service;

import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.DocumentParseStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AppBookSearchResultMapperTest {

    @Test
    void exposesPrimaryDocumentParseStatus() {
        BookFileEntity file = BookFileEntity.builder()
                .fileName("report.docx")
                .bookType(BookFileType.DOC)
                .isBookFormat(true)
                .documentParseStatus(DocumentParseStatus.UNREADABLE)
                .build();
        BookEntity book = BookEntity.builder()
                .id(9L)
                .metadata(BookMetadataEntity.builder().title("Report").build())
                .bookFiles(List.of(file))
                .build();

        var result = AppBookSearchResultMapper.toResult(book);

        assertThat(result.primaryFileDocumentParseStatus()).isEqualTo("UNREADABLE");
    }
}
