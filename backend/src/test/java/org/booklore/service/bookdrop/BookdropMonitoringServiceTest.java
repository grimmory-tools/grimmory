package org.booklore.service.bookdrop;

import org.booklore.config.AppProperties;
import org.booklore.repository.BookdropFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.util.List;

import static org.mockito.Mockito.*;

class BookdropMonitoringServiceTest {

    private AppProperties appProperties;
    private BookdropEventHandlerService eventHandler;
    private BookdropFileRepository bookdropFileRepository;
    private BookdropMonitoringService monitoringService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        appProperties = mock(AppProperties.class);
        eventHandler = mock(BookdropEventHandlerService.class);
        bookdropFileRepository = mock(BookdropFileRepository.class);
        
        when(appProperties.getBookdropFolder()).thenReturn(tempDir.toString());
        monitoringService = new BookdropMonitoringService(appProperties, eventHandler, bookdropFileRepository);
    }

    @Test
    void scanExistingBookdropFiles_ShouldIgnoreDotUnderscoreFiles() throws IOException {
        Path validFile = tempDir.resolve("book.epub");
        Files.createFile(validFile);

        Path invalidFile = tempDir.resolve("._book.epub");
        Files.createFile(invalidFile);
        
        Path hiddenFile = tempDir.resolve(".hidden.epub");
        Files.createFile(hiddenFile);

        Path subDir = tempDir.resolve("subdir");
        Files.createDirectories(subDir);
        Path validFileInSubdir = subDir.resolve("another.epub");
        Files.createFile(validFileInSubdir);

        Path invalidFileInSubdir = subDir.resolve("._another.epub");
        Files.createFile(invalidFileInSubdir);

        when(bookdropFileRepository.findAllFilePathsIn(anyList())).thenReturn(List.of());

        monitoringService.start();
        
        monitoringService.stop();

        verify(eventHandler).enqueueFile(eq(validFile), eq(StandardWatchEventKinds.ENTRY_CREATE));
        verify(eventHandler).enqueueFile(eq(validFileInSubdir), eq(StandardWatchEventKinds.ENTRY_CREATE));

        verify(eventHandler, never()).enqueueFile(eq(invalidFile), any());
        verify(eventHandler, never()).enqueueFile(eq(hiddenFile), any());
        verify(eventHandler, never()).enqueueFile(eq(invalidFileInSubdir), any());
    }

    @Test
    void scanExistingBookdropFiles_ShouldSkipFilesAlreadyTrackedInDatabase() throws IOException {
        Path alreadyTracked = tempDir.resolve("already-tracked.epub");
        Files.createFile(alreadyTracked);

        Path newFile = tempDir.resolve("new-file.epub");
        Files.createFile(newFile);

        when(bookdropFileRepository.findAllFilePathsIn(anyList()))
                .thenReturn(List.of(alreadyTracked.toAbsolutePath().toString()));

        monitoringService.start();

        monitoringService.stop();

        verify(eventHandler, never()).enqueueFile(eq(alreadyTracked), eq(StandardWatchEventKinds.ENTRY_CREATE));
        verify(eventHandler).enqueueFile(eq(newFile), eq(StandardWatchEventKinds.ENTRY_CREATE));
    }
}
