package com.adityachandel.booklore.service;

import com.adityachandel.booklore.config.AppProperties;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.BookdropFileRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.service.bookdrop.BookDropService;
import com.adityachandel.booklore.service.bookdrop.BookdropNotificationService;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessorRegistry;
import com.adityachandel.booklore.service.metadata.MetadataRefreshService;
import com.adityachandel.booklore.service.bookdrop.BookdropMonitoringService;
import com.adityachandel.booklore.service.monitoring.MonitoringService;
import com.adityachandel.booklore.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class BookDropServiceTest {

    private BookdropFileRepository bookdropFileRepository;
    private LibraryRepository libraryRepository;
    private BookRepository bookRepository;
    private MonitoringService monitoringService;
    private BookdropMonitoringService bookdropMonitoringService;
    private NotificationService notificationService;
    private MetadataRefreshService metadataRefreshService;
    private BookdropNotificationService bookdropNotificationService;
    private AppProperties appProperties;

    private BookDropService bookDropService;

    @BeforeEach
    void setUp() {
        bookdropFileRepository = mock(BookdropFileRepository.class);
        libraryRepository = mock(LibraryRepository.class);
        bookRepository = mock(BookRepository.class);
        monitoringService = mock(MonitoringService.class);
        bookdropMonitoringService = mock(BookdropMonitoringService.class);
        notificationService = mock(NotificationService.class);
        metadataRefreshService = mock(MetadataRefreshService.class);
        bookdropNotificationService = mock(BookdropNotificationService.class);
        BookFileProcessorRegistry processorRegistry = mock(BookFileProcessorRegistry.class);
        appProperties = mock(AppProperties.class);

        bookDropService = new BookDropService(
                bookdropFileRepository, libraryRepository, bookRepository,
                monitoringService, bookdropMonitoringService,
                notificationService, metadataRefreshService,
                bookdropNotificationService,
                processorRegistry,
                appProperties
        );
    }

    @Test
    void discardAllFiles_shouldDeleteFilesDirsAndNotify() throws Exception {
        Path bookdropPath = Paths.get("/tmp/bookdrop");
        when(appProperties.getBookdropFolder()).thenReturn(bookdropPath.toString());

        Files.createDirectories(bookdropPath);
        Path testFile = bookdropPath.resolve("testfile.txt");
        Files.createFile(testFile);

        when(bookdropFileRepository.count()).thenReturn(1L);

        Path tempCoverDir = Paths.get("/tmp/config/bookdrop_temp");
        when(appProperties.getPathConfig()).thenReturn("/tmp/config");
        Files.createDirectories(tempCoverDir);
        Path tempCover = tempCoverDir.resolve("1.jpg");
        Files.createFile(tempCover);

        bookDropService.discardAllFiles();

        verify(bookdropFileRepository).deleteAll();
        verify(bookdropNotificationService).sendBookdropFileSummaryNotification();
        verify(bookdropMonitoringService).pauseMonitoring();
        verify(bookdropMonitoringService).resumeMonitoring();

        Files.deleteIfExists(testFile);
        Files.deleteIfExists(bookdropPath);
        Files.deleteIfExists(tempCover);
        Files.deleteIfExists(tempCoverDir);
    }

    @Test
    void getBookdropCover_shouldReturnResourceIfExists() throws Exception {
        when(appProperties.getPathConfig()).thenReturn("/tmp/config");
        long bookdropId = 123L;
        Path coverPath = Paths.get("/tmp/config/bookdrop_temp", bookdropId + ".jpg");
        File coverFile = coverPath.toFile();

        coverFile.getParentFile().mkdirs();
        coverFile.createNewFile();

        Resource resource = bookDropService.getBookdropCover(bookdropId);

        assertThat(resource).isInstanceOf(PathResource.class);
        assertThat(resource.getFilename()).isEqualTo(bookdropId + ".jpg");

        coverFile.delete();
    }

    @Test
    void getBookdropCover_shouldReturnNullIfNotExists() {
        when(appProperties.getPathConfig()).thenReturn("/tmp/config");
        long bookdropId = 99999L;

        Resource resource = bookDropService.getBookdropCover(bookdropId);

        assertThat(resource).isNull();
    }
}