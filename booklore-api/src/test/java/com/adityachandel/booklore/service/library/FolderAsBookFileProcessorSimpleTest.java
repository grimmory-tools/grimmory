package com.adityachandel.booklore.service.library;

import com.adityachandel.booklore.model.enums.LibraryScanMode;
import com.adityachandel.booklore.repository.BookAdditionalFileRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessorRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FolderAsBookFileProcessorSimpleTest {

    @Test
    void getScanMode_shouldReturnFolderAsBook() {
        BookRepository bookRepository = mock(BookRepository.class);
        BookAdditionalFileRepository bookAdditionalFileRepository = mock(BookAdditionalFileRepository.class);
        NotificationService notificationService = mock(NotificationService.class);
        BookFileProcessorRegistry bookFileProcessorRegistry = mock(BookFileProcessorRegistry.class);

        FolderAsBookFileProcessor processor = new FolderAsBookFileProcessor(
                bookRepository, bookAdditionalFileRepository, notificationService, bookFileProcessorRegistry);

        assertThat(processor.getScanMode()).isEqualTo(LibraryScanMode.FOLDER_AS_BOOK);
    }

    @Test
    void supportsSupplementaryFiles_shouldReturnTrue() {
        BookRepository bookRepository = mock(BookRepository.class);
        BookAdditionalFileRepository bookAdditionalFileRepository = mock(BookAdditionalFileRepository.class);
        NotificationService notificationService = mock(NotificationService.class);
        BookFileProcessorRegistry bookFileProcessorRegistry = mock(BookFileProcessorRegistry.class);

        FolderAsBookFileProcessor processor = new FolderAsBookFileProcessor(
                bookRepository, bookAdditionalFileRepository, notificationService, bookFileProcessorRegistry);

        assertThat(processor.supportsSupplementaryFiles()).isTrue();
    }
}
