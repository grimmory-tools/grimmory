package com.adityachandel.booklore.service.bookdrop;

import com.adityachandel.booklore.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;

import static org.mockito.Mockito.*;

class BookdropMonitoringServiceTest {

    private AppProperties appProperties;
    private BookdropEventHandlerService eventHandler;
    private BookdropMonitoringService monitoringService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        appProperties = mock(AppProperties.class);
        eventHandler = mock(BookdropEventHandlerService.class);
        
        when(appProperties.getBookdropFolder()).thenReturn(tempDir.toString());
        monitoringService = new BookdropMonitoringService(appProperties, eventHandler);
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

        monitoringService.start();
        
        monitoringService.stop();

        verify(eventHandler).enqueueFile(eq(validFile), eq(StandardWatchEventKinds.ENTRY_CREATE));
        verify(eventHandler).enqueueFile(eq(validFileInSubdir), eq(StandardWatchEventKinds.ENTRY_CREATE));

        verify(eventHandler, never()).enqueueFile(eq(invalidFile), any());
        verify(eventHandler, never()).enqueueFile(eq(hiddenFile), any());
        verify(eventHandler, never()).enqueueFile(eq(invalidFileInSubdir), any());
    }
}
