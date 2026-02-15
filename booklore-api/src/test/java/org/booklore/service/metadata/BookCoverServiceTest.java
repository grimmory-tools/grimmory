package org.booklore.service.metadata;

import org.booklore.exception.ApiError;
import org.booklore.mapper.BookMetadataMapper;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.settings.MetadataPersistenceSettings;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookRepository;
import org.booklore.repository.projection.BookCoverUpdateProjection;
import org.booklore.service.NotificationService;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.book.BookQueryService;
import org.booklore.service.file.FileFingerprint;
import org.booklore.service.fileprocessor.BookFileProcessor;
import org.booklore.service.fileprocessor.BookFileProcessorRegistry;
import org.booklore.service.metadata.writer.MetadataWriter;
import org.booklore.service.metadata.writer.MetadataWriterFactory;
import org.booklore.util.FileService;
import org.booklore.util.SecurityContextVirtualThread;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class BookCoverServiceTest {

    @Mock private BookRepository bookRepository;
    @Mock private BookMetadataMapper bookMetadataMapper;
    @Mock private NotificationService notificationService;
    @Mock private AppSettingService appSettingService;
    @Mock private FileService fileService;
    @Mock private BookFileProcessorRegistry processorRegistry;
    @Mock private BookQueryService bookQueryService;
    @Mock private CoverImageGenerator coverImageGenerator;
    @Mock private MetadataWriterFactory metadataWriterFactory;
    @Mock private TransactionTemplate transactionTemplate;

    @InjectMocks private BookCoverService bookCoverService;

    @Captor private ArgumentCaptor<Object> notificationCaptor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private BookEntity mockBookEntity(Long id, boolean coverLocked) {
        BookMetadataEntity metadata = new BookMetadataEntity();
        metadata.setTitle("Test Title");
        metadata.setAuthors(Set.of(new AuthorEntity(1L, "Author Name", null)));
        metadata.setCoverLocked(coverLocked);
        BookEntity book = new BookEntity();
        book.setId(id);
        book.setMetadata(metadata);
        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(book);
        book.setBookFiles(List.of(primaryFile));
        book.getPrimaryBookFile().setBookType(BookFileType.EPUB);
        return book;
    }

    @Test
    void generateCustomCover_coverLocked_throws() {
        BookEntity book = mockBookEntity(2L, true);
        when(bookRepository.findById(2L)).thenReturn(Optional.of(book));
        when(bookRepository.findAllWithMetadataByIds(any())).thenReturn(List.of(book));
        when(appSettingService.getAppSettings()).thenReturn(mockAppSettings(true, false));
        assertThatThrownBy(() -> bookCoverService.generateCustomCover(2L))
                .isInstanceOf(ApiError.METADATA_LOCKED.createException().getClass());
    }

    @Test
    void updateCoverFromFile_success() {
        MultipartFile file = mock(MultipartFile.class);
        BookEntity book = spy(mockBookEntity(3L, false));
        doReturn(Path.of("/dummy/path")).when(book).getFullFilePath();
        when(bookRepository.findById(3L)).thenReturn(Optional.of(book));
        when(bookRepository.save(any())).thenReturn(book);
        when(appSettingService.getAppSettings()).thenReturn(mockAppSettings(true, false));

        MetadataWriter writer = mock(MetadataWriter.class);
        when(metadataWriterFactory.getWriter(any())).thenReturn(Optional.of(writer));

        BookCoverUpdateProjection projection = mock(BookCoverUpdateProjection.class);
        when(bookRepository.findCoverUpdateInfoByIds(any())).thenReturn(List.of(projection));

        try (MockedStatic<FileFingerprint> fingerprintMock = mockStatic(FileFingerprint.class)) {
            fingerprintMock.when(() -> FileFingerprint.generateHash(any())).thenReturn("dummyhash");
            bookCoverService.updateCoverFromFile(3L, file);

            verify(fileService).createThumbnailFromFile(3L, file);
        }
    }

    @Test
    void updateCoverFromUrl_success() {
        String url = "http://test.com/cover.jpg";
        BookEntity book = spy(mockBookEntity(4L, false));
        doReturn(Path.of("/dummy/path")).when(book).getFullFilePath();
        when(bookRepository.findById(4L)).thenReturn(Optional.of(book));
        when(bookRepository.save(any())).thenReturn(book);
        when(appSettingService.getAppSettings()).thenReturn(mockAppSettings(true, false));

        MetadataWriter writer = mock(MetadataWriter.class);
        when(metadataWriterFactory.getWriter(any())).thenReturn(Optional.of(writer));

        BookCoverUpdateProjection projection = mock(BookCoverUpdateProjection.class);
        when(bookRepository.findCoverUpdateInfoByIds(any())).thenReturn(List.of(projection));

        try (MockedStatic<FileFingerprint> fingerprintMock = mockStatic(FileFingerprint.class)) {
            fingerprintMock.when(() -> FileFingerprint.generateHash(any())).thenReturn("dummyhash");
            bookCoverService.updateCoverFromUrl(4L, url);

            verify(fileService).createThumbnailFromUrl(4L, url);
        }
    }

    @Test
    void updateCoverFromFileForBooks_success() throws Exception {
        try (MockedStatic<SecurityContextVirtualThread> ignored = mockStatic(SecurityContextVirtualThread.class)) {
            MultipartFile file = mock(MultipartFile.class);
            when(file.isEmpty()).thenReturn(false);
            when(file.getContentType()).thenReturn("image/jpeg");
            doReturn(new byte[]{1,2,3}).when(file).getBytes();
            BookEntity book = spy(mockBookEntity(5L, false));
            doReturn(Path.of("/dummy/path")).when(book).getFullFilePath();
            when(bookQueryService.findAllWithMetadataByIds(any())).thenReturn(List.of(book));
            when(bookRepository.findById(5L)).thenReturn(Optional.of(book));
            when(bookRepository.save(any())).thenReturn(book);
            when(appSettingService.getAppSettings()).thenReturn(mockAppSettings(true, false));
            doNothing().when(fileService).createThumbnailFromBytes(eq(5L), any());
            doNothing().when(notificationService).sendMessage(any(), any());

            MetadataWriter writer = mock(MetadataWriter.class);
            when(metadataWriterFactory.getWriter(any())).thenReturn(Optional.of(writer));

            BookCoverUpdateProjection projection = mock(BookCoverUpdateProjection.class);
            when(bookRepository.findCoverUpdateInfoByIds(any())).thenReturn(List.of(projection));

            when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
                org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
                return callback.doInTransaction(null);
            });

            try (MockedStatic<FileFingerprint> fingerprintMock = mockStatic(FileFingerprint.class)) {
                fingerprintMock.when(() -> FileFingerprint.generateHash(any())).thenReturn("dummyhash");

                ignored.when(() -> SecurityContextVirtualThread.runWithSecurityContext(any(Runnable.class)))
                        .thenAnswer(invocation -> {
                            Runnable runnable = invocation.getArgument(0);
                            runnable.run();
                            return null;
                        });

                bookCoverService.updateCoverFromFileForBooks(Set.of(5L), file);

                ArgumentCaptor<Long> idCaptor = ArgumentCaptor.forClass(Long.class);
                verify(fileService).createThumbnailFromBytes(idCaptor.capture(), any());
                assertThat(idCaptor.getValue()).isEqualTo(5L);
                verify(bookRepository).save(any(BookEntity.class));
                verify(notificationService, atLeastOnce()).sendMessage(any(), any());
            }
        }
    }

    @Test
    void updateCoverFromFileForBooks_invalidFile_throws() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(true);
        assertThatThrownBy(() -> bookCoverService.updateCoverFromFileForBooks(Set.of(6L), file))
                .isInstanceOf(ApiError.INVALID_INPUT.createException("dummy").getClass());
    }

    @Test
    void regenerateCover_success() {
        BookEntity book = mockBookEntity(7L, false);
        when(bookRepository.findByIdWithBookFiles(7L)).thenReturn(Optional.of(book));
        BookFileProcessor processor = mock(BookFileProcessor.class);
        when(processorRegistry.getProcessorOrThrow(any())).thenReturn(processor);
        when(processor.generateCover(any(BookEntity.class), any(BookFileEntity.class))).thenReturn(true);
        when(bookRepository.save(any())).thenReturn(book);

        bookCoverService.regenerateCover(7L);

        verify(processor).generateCover(eq(book), any(BookFileEntity.class));
        verify(bookRepository).save(book);
    }

    @Test
    void regenerateCover_coverLocked_throws() {
        BookEntity book = mockBookEntity(8L, true);
        when(bookRepository.findByIdWithBookFiles(8L)).thenReturn(Optional.of(book));
        assertThatThrownBy(() -> bookCoverService.regenerateCover(8L))
                .isInstanceOf(ApiError.METADATA_LOCKED.createException().getClass());
    }

    @Test
    void regenerateCoversForBooks_success() {
        try (MockedStatic<SecurityContextVirtualThread> ignored = mockStatic(SecurityContextVirtualThread.class)) {
            BookEntity book = mockBookEntity(9L, false);
            when(bookQueryService.findAllWithMetadataByIds(any())).thenReturn(List.of(book));
            when(bookRepository.findById(9L)).thenReturn(Optional.of(book));
            when(bookRepository.save(any())).thenReturn(book);

            BookFileProcessor processor = mock(BookFileProcessor.class);
            when(processorRegistry.getProcessorOrThrow(any())).thenReturn(processor);
            when(processor.generateCover(any())).thenReturn(true);

            BookCoverUpdateProjection projection = mock(BookCoverUpdateProjection.class);
            when(bookRepository.findCoverUpdateInfoByIds(any())).thenReturn(List.of(projection));
            doNothing().when(notificationService).sendMessage(any(), any());

            when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
                org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
                return callback.doInTransaction(null);
            });

            ignored.when(() -> SecurityContextVirtualThread.runWithSecurityContext(any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        Runnable runnable = invocation.getArgument(0);
                        runnable.run();
                        return null;
                    });

            bookCoverService.regenerateCoversForBooks(Set.of(9L));

            ArgumentCaptor<BookEntity> bookCaptor = ArgumentCaptor.forClass(BookEntity.class);
            verify(processor).generateCover(bookCaptor.capture());
            assertThat(bookCaptor.getValue().getId()).isEqualTo(9L);
            verify(notificationService, atLeastOnce()).sendMessage(any(), any());
        }
    }

    @Test
    void updateCover_metadataPersistenceSettings_saveToOriginalFile() {
        MultipartFile file = mock(MultipartFile.class);
        BookEntity book = spy(mockBookEntity(10L, false));
        doReturn(Path.of("/dummy/path")).when(book).getFullFilePath();
        when(bookRepository.findById(10L)).thenReturn(Optional.of(book));
        when(bookRepository.save(any())).thenReturn(book);
        when(appSettingService.getAppSettings()).thenReturn(mockAppSettings(true, false));

        MetadataWriter writer = mock(MetadataWriter.class);
        when(metadataWriterFactory.getWriter(any())).thenReturn(Optional.of(writer));

        BookCoverUpdateProjection projection = mock(BookCoverUpdateProjection.class);
        when(bookRepository.findCoverUpdateInfoByIds(any())).thenReturn(List.of(projection));

        try (MockedStatic<FileFingerprint> fingerprintMock = mockStatic(FileFingerprint.class)) {
            fingerprintMock.when(() -> FileFingerprint.generateHash(any())).thenReturn("dummyhash");
            bookCoverService.updateCoverFromFile(10L, file);

            verify(writer).replaceCoverImageFromUpload(book, file);
        }
    }

    private AppSettings mockAppSettings(boolean saveToOriginalFile, boolean convertCbrCb7ToCbz) {
        MetadataPersistenceSettings.SaveToOriginalFile saveToOriginalFileObj =
            MetadataPersistenceSettings.SaveToOriginalFile.builder()
                .epub(MetadataPersistenceSettings.FormatSettings.builder().enabled(saveToOriginalFile).build())
                .pdf(MetadataPersistenceSettings.FormatSettings.builder().enabled(saveToOriginalFile).build())
                .cbx(MetadataPersistenceSettings.FormatSettings.builder().enabled(saveToOriginalFile).build())
                .build();

        MetadataPersistenceSettings settings = new MetadataPersistenceSettings();
        settings.setSaveToOriginalFile(saveToOriginalFileObj);
        settings.setConvertCbrCb7ToCbz(convertCbrCb7ToCbz);
        return new AppSettings() {
            @Override
            public MetadataPersistenceSettings getMetadataPersistenceSettings() {
                return settings;
            }
        };
    }
}

