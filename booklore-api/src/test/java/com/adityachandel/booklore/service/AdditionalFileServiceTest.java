package com.adityachandel.booklore.service;

import com.adityachandel.booklore.mapper.AdditionalFileMapper;
import com.adityachandel.booklore.model.dto.AdditionalFile;
import com.adityachandel.booklore.model.entity.BookAdditionalFileEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.enums.AdditionalFileType;
import com.adityachandel.booklore.repository.BookAdditionalFileRepository;
import com.adityachandel.booklore.service.file.AdditionalFileService;
import com.adityachandel.booklore.service.monitoring.MonitoringRegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdditionalFileServiceTest {

    @Mock
    private BookAdditionalFileRepository additionalFileRepository;

    @Mock
    private AdditionalFileMapper additionalFileMapper;

    @Mock
    private MonitoringRegistrationService monitoringRegistrationService;

    @InjectMocks
    private AdditionalFileService additionalFileService;

    @TempDir
    Path tempDir;

    private BookAdditionalFileEntity fileEntity;
    private AdditionalFile additionalFile;
    private BookEntity bookEntity;

    @BeforeEach
    void setUp() throws IOException {
        Path testFile = tempDir.resolve("test-file.pdf");
        Files.createFile(testFile);

        LibraryPathEntity libraryPathEntity = new LibraryPathEntity();
        libraryPathEntity.setId(1L);
        libraryPathEntity.setPath(tempDir.toString());

        bookEntity = new BookEntity();
        bookEntity.setId(100L);
        bookEntity.setLibraryPath(libraryPathEntity);

        fileEntity = new BookAdditionalFileEntity();
        fileEntity.setId(1L);
        fileEntity.setBook(bookEntity);
        fileEntity.setFileName("test-file.pdf");
        fileEntity.setFileSubPath(".");
        fileEntity.setAdditionalFileType(AdditionalFileType.ALTERNATIVE_FORMAT);

        additionalFile = mock(AdditionalFile.class);
    }

    @Test
    void getAdditionalFilesByBookId_WhenFilesExist_ShouldReturnMappedFiles() {
        Long bookId = 100L;
        List<BookAdditionalFileEntity> entities = List.of(fileEntity);
        List<AdditionalFile> expectedFiles = List.of(additionalFile);

        when(additionalFileRepository.findByBookId(bookId)).thenReturn(entities);
        when(additionalFileMapper.toAdditionalFiles(entities)).thenReturn(expectedFiles);

        List<AdditionalFile> result = additionalFileService.getAdditionalFilesByBookId(bookId);

        assertEquals(expectedFiles, result);
        verify(additionalFileRepository).findByBookId(bookId);
        verify(additionalFileMapper).toAdditionalFiles(entities);
    }

    @Test
    void getAdditionalFilesByBookId_WhenNoFilesExist_ShouldReturnEmptyList() {
        Long bookId = 100L;
        List<BookAdditionalFileEntity> entities = Collections.emptyList();
        List<AdditionalFile> expectedFiles = Collections.emptyList();

        when(additionalFileRepository.findByBookId(bookId)).thenReturn(entities);
        when(additionalFileMapper.toAdditionalFiles(entities)).thenReturn(expectedFiles);

        List<AdditionalFile> result = additionalFileService.getAdditionalFilesByBookId(bookId);

        assertTrue(result.isEmpty());
        verify(additionalFileRepository).findByBookId(bookId);
        verify(additionalFileMapper).toAdditionalFiles(entities);
    }

    @Test
    void getAdditionalFilesByBookIdAndType_WhenFilesExist_ShouldReturnMappedFiles() {
        Long bookId = 100L;
        AdditionalFileType type = AdditionalFileType.ALTERNATIVE_FORMAT;
        List<BookAdditionalFileEntity> entities = List.of(fileEntity);
        List<AdditionalFile> expectedFiles = List.of(additionalFile);

        when(additionalFileRepository.findByBookIdAndAdditionalFileType(bookId, type)).thenReturn(entities);
        when(additionalFileMapper.toAdditionalFiles(entities)).thenReturn(expectedFiles);

        List<AdditionalFile> result = additionalFileService.getAdditionalFilesByBookIdAndType(bookId, type);

        assertEquals(expectedFiles, result);
        verify(additionalFileRepository).findByBookIdAndAdditionalFileType(bookId, type);
        verify(additionalFileMapper).toAdditionalFiles(entities);
    }

    @Test
    void getAdditionalFilesByBookIdAndType_WhenNoFilesExist_ShouldReturnEmptyList() {
        Long bookId = 100L;
        AdditionalFileType type = AdditionalFileType.SUPPLEMENTARY;
        List<BookAdditionalFileEntity> entities = Collections.emptyList();
        List<AdditionalFile> expectedFiles = Collections.emptyList();

        when(additionalFileRepository.findByBookIdAndAdditionalFileType(bookId, type)).thenReturn(entities);
        when(additionalFileMapper.toAdditionalFiles(entities)).thenReturn(expectedFiles);

        List<AdditionalFile> result = additionalFileService.getAdditionalFilesByBookIdAndType(bookId, type);

        assertTrue(result.isEmpty());
        verify(additionalFileRepository).findByBookIdAndAdditionalFileType(bookId, type);
        verify(additionalFileMapper).toAdditionalFiles(entities);
    }

    @Test
    void deleteAdditionalFile_WhenFileNotFound_ShouldThrowException() {
        Long fileId = 1L;
        when(additionalFileRepository.findById(fileId)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> additionalFileService.deleteAdditionalFile(fileId)
        );

        assertEquals("Additional file not found with id: 1", exception.getMessage());
        verify(additionalFileRepository).findById(fileId);
        verify(additionalFileRepository, never()).delete(any());
        verify(monitoringRegistrationService, never()).unregisterSpecificPath(any());
    }

    @Test
    void deleteAdditionalFile_WhenFileExists_ShouldDeleteSuccessfully() {
        Long fileId = 1L;
        Path parentPath = fileEntity.getFullFilePath().getParent();

        when(additionalFileRepository.findById(fileId)).thenReturn(Optional.of(fileEntity));

        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.deleteIfExists(fileEntity.getFullFilePath())).thenReturn(true);

            additionalFileService.deleteAdditionalFile(fileId);

            verify(additionalFileRepository).findById(fileId);
            verify(monitoringRegistrationService).unregisterSpecificPath(parentPath);
            filesMock.verify(() -> Files.deleteIfExists(fileEntity.getFullFilePath()));
            verify(additionalFileRepository).delete(fileEntity);
        }
    }

    @Test
    void deleteAdditionalFile_WhenIOExceptionOccurs_ShouldStillDeleteFromRepository() {
        Long fileId = 1L;
        Path parentPath = fileEntity.getFullFilePath().getParent();

        when(additionalFileRepository.findById(fileId)).thenReturn(Optional.of(fileEntity));

        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.deleteIfExists(fileEntity.getFullFilePath())).thenThrow(new IOException("File access error"));

            additionalFileService.deleteAdditionalFile(fileId);

            verify(additionalFileRepository).findById(fileId);
            verify(monitoringRegistrationService).unregisterSpecificPath(parentPath);
            filesMock.verify(() -> Files.deleteIfExists(fileEntity.getFullFilePath()));
            verify(additionalFileRepository).delete(fileEntity);
        }
    }

    @Test
    void deleteAdditionalFile_WhenEntityRelationshipsMissing_ShouldThrowIllegalStateException() {
        Long fileId = 1L;
        BookAdditionalFileEntity invalidEntity = new BookAdditionalFileEntity();
        invalidEntity.setId(fileId);

        when(additionalFileRepository.findById(fileId)).thenReturn(Optional.of(invalidEntity));

        assertThrows(
                IllegalStateException.class,
                () -> additionalFileService.deleteAdditionalFile(fileId)
        );

        verify(additionalFileRepository).findById(fileId);
        verify(additionalFileRepository, never()).delete(any());
        verify(monitoringRegistrationService, never()).unregisterSpecificPath(any());
    }

    @Test
    void downloadAdditionalFile_WhenFileNotFound_ShouldReturnNotFound() throws IOException {
        Long fileId = 1L;
        when(additionalFileRepository.findById(fileId)).thenReturn(Optional.empty());

        ResponseEntity<Resource> result = additionalFileService.downloadAdditionalFile(fileId);

        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
        assertNull(result.getBody());
        verify(additionalFileRepository).findById(fileId);
    }

    @Test
    void downloadAdditionalFile_WhenPhysicalFileNotExists_ShouldReturnNotFound() throws IOException {
        Long fileId = 1L;

        BookAdditionalFileEntity entityWithNonExistentFile = new BookAdditionalFileEntity();
        entityWithNonExistentFile.setId(fileId);
        entityWithNonExistentFile.setBook(bookEntity);
        entityWithNonExistentFile.setFileName("non-existent.pdf");
        entityWithNonExistentFile.setFileSubPath(".");

        when(additionalFileRepository.findById(fileId)).thenReturn(Optional.of(entityWithNonExistentFile));

        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            Path actualPath = entityWithNonExistentFile.getFullFilePath();
            filesMock.when(() -> Files.exists(actualPath)).thenReturn(false);

            ResponseEntity<Resource> result = additionalFileService.downloadAdditionalFile(fileId);

            assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
            assertNull(result.getBody());
            verify(additionalFileRepository).findById(fileId);
            filesMock.verify(() -> Files.exists(actualPath));
        }
    }

    @Test
    void downloadAdditionalFile_WhenFileExists_ShouldReturnFileResource() throws Exception {
        Long fileId = 1L;
        when(additionalFileRepository.findById(fileId)).thenReturn(Optional.of(fileEntity));

        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.exists(fileEntity.getFullFilePath())).thenReturn(true);

            ResponseEntity<Resource> result = additionalFileService.downloadAdditionalFile(fileId);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertNotNull(result.getBody());
            assertTrue(result.getHeaders().containsKey(HttpHeaders.CONTENT_DISPOSITION));
            assertTrue(result.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("test-file.pdf"));
            assertEquals(MediaType.APPLICATION_OCTET_STREAM, result.getHeaders().getContentType());

            verify(additionalFileRepository).findById(fileId);
            filesMock.verify(() -> Files.exists(fileEntity.getFullFilePath()));
        }
    }

    @Test
    void downloadAdditionalFile_WhenEntityRelationshipsMissing_ShouldThrowIllegalStateException() {
        Long fileId = 1L;
        BookAdditionalFileEntity invalidEntity = new BookAdditionalFileEntity();
        invalidEntity.setId(fileId);

        when(additionalFileRepository.findById(fileId)).thenReturn(Optional.of(invalidEntity));

        assertThrows(
                IllegalStateException.class,
                () -> additionalFileService.downloadAdditionalFile(fileId)
        );

        verify(additionalFileRepository).findById(fileId);
    }
}
