package org.booklore.service.library;

import org.booklore.model.document.DocumentParseResult;
import org.booklore.model.dto.settings.LibraryFile;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.DocumentParseStatus;
import org.booklore.repository.BookAdditionalFileRepository;
import org.booklore.repository.BookRepository;
import org.booklore.service.document.DocumentContentExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookFileAutoAttacherTest {

    @Mock
    private BookRepository bookRepository;
    @Mock
    private BookAdditionalFileRepository additionalFileRepository;
    @Mock
    private DocumentContentExtractor documentContentExtractor;
    @Mock
    private LibraryFile libraryFile;

    @Test
    void storesUnreadableVerdictOnAttachedDocumentWithoutChangingPrimaryFile() {
        LibraryPathEntity libraryPath = LibraryPathEntity.builder().id(4L).path("/library").build();
        BookFileEntity primary = BookFileEntity.builder()
                .fileName("book.epub")
                .bookType(BookFileType.EPUB)
                .isBookFormat(true)
                .build();
        BookEntity book = BookEntity.builder()
                .id(7L)
                .libraryPath(libraryPath)
                .bookFiles(List.of(primary))
                .build();
        Path documentPath = Path.of("/library/book.docx");
        when(bookRepository.findByIdWithBookFiles(7L)).thenReturn(Optional.of(book));
        when(libraryFile.getLibraryPathEntity()).thenReturn(libraryPath);
        when(libraryFile.getFileSubPath()).thenReturn("");
        when(libraryFile.getFileName()).thenReturn("book.docx");
        when(libraryFile.getBookFileType()).thenReturn(BookFileType.DOC);
        when(libraryFile.getFullPath()).thenReturn(documentPath);
        when(additionalFileRepository.findByLibraryPath_IdAndFileSubPathAndFileName(4L, "", "book.docx"))
                .thenReturn(Optional.empty());
        when(documentContentExtractor.parse(documentPath.toFile())).thenReturn(DocumentParseResult.unreadable());

        new BookFileAutoAttacher(bookRepository, additionalFileRepository, documentContentExtractor)
                .attach(7L, libraryFile, "hash", 12L);

        ArgumentCaptor<BookFileEntity> saved = ArgumentCaptor.forClass(BookFileEntity.class);
        verify(additionalFileRepository).save(saved.capture());
        assertThat(saved.getValue().getDocumentParseStatus()).isEqualTo(DocumentParseStatus.UNREADABLE);
        assertThat(primary.getDocumentParseStatus()).isNull();
    }
}
