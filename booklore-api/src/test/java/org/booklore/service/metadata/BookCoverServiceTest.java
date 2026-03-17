package org.booklore.service.metadata;

import org.booklore.config.AppProperties;
import org.booklore.exception.APIException;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.settings.MetadataPersistenceSettings;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookRepository;
import org.booklore.repository.projection.BookCoverUpdateProjection;
import org.booklore.service.NotificationService;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.book.BookQueryService;
import org.booklore.service.fileprocessor.BookFileProcessor;
import org.booklore.service.fileprocessor.BookFileProcessorRegistry;
import org.booklore.service.metadata.writer.MetadataWriter;
import org.booklore.service.metadata.writer.MetadataWriterFactory;
import org.booklore.service.file.FileFingerprint;
import org.booklore.util.FileService;
import org.booklore.util.SecurityContextVirtualThread;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookCoverServiceTest {

    @Mock private AppProperties appProperties;
    @Mock private BookRepository bookRepository;
    @Mock private NotificationService notificationService;
    @Mock private AppSettingService appSettingService;
    @Mock private FileService fileService;
    @Mock private BookFileProcessorRegistry processorRegistry;
    @Mock private BookQueryService bookQueryService;
    @Mock private CoverImageGenerator coverImageGenerator;
    @Mock private MetadataWriterFactory metadataWriterFactory;
    @Mock private TransactionTemplate transactionTemplate;

    @InjectMocks
    private BookCoverService service;

    @BeforeEach
    void setUp() {
        lenient().when(appProperties.isLocalStorage()).thenReturn(true);
    }

    private BookEntity buildBook(long id, boolean coverLocked) {
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("Test Book")
                .coverLocked(coverLocked)
                .build();
        return BookEntity.builder()
                .id(id)
                .metadata(metadata)
                .bookFiles(new ArrayList<>())
                .build();
    }

    private BookEntity buildBookWithAudiobookLock(long id, boolean audiobookCoverLocked) {
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("Test Audiobook")
                .audiobookCoverLocked(audiobookCoverLocked)
                .coverLocked(false)
                .build();
        return BookEntity.builder()
                .id(id)
                .metadata(metadata)
                .bookFiles(new ArrayList<>())
                .build();
    }

    @Nested
    class BookNotFound {

        @Test
        void generateCustomCoverThrowsWhenBookNotFound() {
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.generateCustomCover(1L))
                    .isInstanceOf(APIException.class)
                    .hasMessageContaining("Book not found");
        }

        @Test
        void updateCoverFromFileThrowsWhenBookNotFound() {
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.empty());
            MultipartFile file = mock(MultipartFile.class);

            assertThatThrownBy(() -> service.updateCoverFromFile(1L, file))
                    .isInstanceOf(APIException.class);
        }

        @Test
        void updateCoverFromUrlThrowsWhenBookNotFound() {
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateCoverFromUrl(1L, "http://example.com/cover.jpg"))
                    .isInstanceOf(APIException.class);
        }

        @Test
        void regenerateCoverThrowsWhenBookNotFound() {
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.regenerateCover(1L))
                    .isInstanceOf(APIException.class);
        }
    }

    @Nested
    class CoverLockChecks {

        @Test
        void generateCustomCoverThrowsWhenCoverLocked() {
            BookEntity book = buildBook(1L, true);
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));

            assertThatThrownBy(() -> service.generateCustomCover(1L))
                    .isInstanceOf(APIException.class)
                    .hasMessageContaining("locked");
        }

        @Test
        void updateCoverFromFileThrowsWhenCoverLocked() {
            BookEntity book = buildBook(1L, true);
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));
            MultipartFile file = mock(MultipartFile.class);

            assertThatThrownBy(() -> service.updateCoverFromFile(1L, file))
                    .isInstanceOf(APIException.class)
                    .hasMessageContaining("locked");
        }

        @Test
        void updateCoverFromUrlThrowsWhenCoverLocked() {
            BookEntity book = buildBook(1L, true);
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));

            assertThatThrownBy(() -> service.updateCoverFromUrl(1L, "http://example.com"))
                    .isInstanceOf(APIException.class)
                    .hasMessageContaining("locked");
        }

        @Test
        void regenerateCoverThrowsWhenCoverLocked() {
            BookEntity book = buildBook(1L, true);
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));

            assertThatThrownBy(() -> service.regenerateCover(1L))
                    .isInstanceOf(APIException.class)
                    .hasMessageContaining("locked");
        }
    }

    @Nested
    class AudiobookCoverLockChecks {

        @Test
        void updateAudiobookCoverFromFileThrowsWhenLocked() {
            BookEntity book = buildBookWithAudiobookLock(1L, true);
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));
            MultipartFile file = mock(MultipartFile.class);

            assertThatThrownBy(() -> service.updateAudiobookCoverFromFile(1L, file))
                    .isInstanceOf(APIException.class)
                    .hasMessageContaining("locked");
        }

        @Test
        void updateAudiobookCoverFromUrlThrowsWhenLocked() {
            BookEntity book = buildBookWithAudiobookLock(1L, true);
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));

            assertThatThrownBy(() -> service.updateAudiobookCoverFromUrl(1L, "http://example.com"))
                    .isInstanceOf(APIException.class)
                    .hasMessageContaining("locked");
        }

        @Test
        void regenerateAudiobookCoverThrowsWhenLocked() {
            BookEntity book = buildBookWithAudiobookLock(1L, true);
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));

            assertThatThrownBy(() -> service.regenerateAudiobookCover(1L))
                    .isInstanceOf(APIException.class)
                    .hasMessageContaining("locked");
        }

        @Test
        void generateCustomAudiobookCoverThrowsWhenLocked() {
            BookEntity book = buildBookWithAudiobookLock(1L, true);
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));

            assertThatThrownBy(() -> service.generateCustomAudiobookCover(1L))
                    .isInstanceOf(APIException.class)
                    .hasMessageContaining("locked");
        }
    }

    @Nested
    class RegenerateCover {

        @Test
        void throwsWhenNoEbookFileFound() {
            BookEntity book = buildBook(1L, false);
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));

            assertThatThrownBy(() -> service.regenerateCover(1L))
                    .isInstanceOf(APIException.class)
                    .hasMessageContaining("no ebook file found");
        }

        @Test
        void throwsWhenProcessorFailsToRegenerate() {
            BookEntity book = buildBook(1L, false);
            BookFileEntity ebookFile = BookFileEntity.builder()
                    .bookType(BookFileType.EPUB)
                    .isBookFormat(true)
                    .build();
            book.setBookFiles(List.of(ebookFile));
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));

            BookFileProcessor processor = mock(BookFileProcessor.class);
            when(processorRegistry.getProcessorOrThrow(BookFileType.EPUB)).thenReturn(processor);
            when(processor.generateCover(book, ebookFile)).thenReturn(false);

            assertThatThrownBy(() -> service.regenerateCover(1L))
                    .isInstanceOf(APIException.class)
                    .hasMessageContaining("no embedded cover image found");
        }

        @Test
        void successfulRegenerationUpdatesCoverMetadata() {
            BookEntity book = buildBook(1L, false);
            BookFileEntity ebookFile = BookFileEntity.builder()
                    .bookType(BookFileType.EPUB)
                    .isBookFormat(true)
                    .build();
            book.setBookFiles(List.of(ebookFile));
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));

            BookFileProcessor processor = mock(BookFileProcessor.class);
            when(processorRegistry.getProcessorOrThrow(BookFileType.EPUB)).thenReturn(processor);
            when(processor.generateCover(book, ebookFile)).thenReturn(true);

            service.regenerateCover(1L);

            assertThat(book.getMetadata().getCoverUpdatedOn()).isNotNull();
            assertThat(book.getMetadataUpdatedAt()).isNotNull();
            assertThat(book.getBookCoverHash()).isNotNull();
            verify(bookRepository).save(book);
        }
    }

    @Nested
    class RegenerateAudiobookCover {

        @Test
        void throwsWhenNoAudiobookFileFound() {
            BookEntity book = buildBookWithAudiobookLock(1L, false);
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));

            assertThatThrownBy(() -> service.regenerateAudiobookCover(1L))
                    .isInstanceOf(APIException.class)
                    .hasMessageContaining("no audiobook file found");
        }

        @Test
        void throwsWhenProcessorFailsToExtractCover() {
            BookEntity book = buildBookWithAudiobookLock(1L, false);
            BookFileEntity audiobookFile = BookFileEntity.builder()
                    .bookType(BookFileType.AUDIOBOOK)
                    .build();
            book.setBookFiles(List.of(audiobookFile));
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));

            BookFileProcessor processor = mock(BookFileProcessor.class);
            when(processorRegistry.getProcessorOrThrow(BookFileType.AUDIOBOOK)).thenReturn(processor);
            when(processor.generateAudiobookCover(book)).thenReturn(false);

            assertThatThrownBy(() -> service.regenerateAudiobookCover(1L))
                    .isInstanceOf(APIException.class)
                    .hasMessageContaining("no embedded cover image found");
        }
    }

    @Nested
    class FormatPrioritySelection {

        @Test
        void selectsEbookByFormatPrioritySkippingAudiobook() {
            BookEntity book = buildBook(1L, false);

            BookFileEntity audiobookFile = BookFileEntity.builder()
                    .bookType(BookFileType.AUDIOBOOK)
                    .isBookFormat(false)
                    .build();
            BookFileEntity pdfFile = BookFileEntity.builder()
                    .bookType(BookFileType.PDF)
                    .isBookFormat(true)
                    .build();
            BookFileEntity epubFile = BookFileEntity.builder()
                    .bookType(BookFileType.EPUB)
                    .isBookFormat(true)
                    .build();
            book.setBookFiles(List.of(audiobookFile, pdfFile, epubFile));

            LibraryEntity library = LibraryEntity.builder()
                    .formatPriority(List.of(BookFileType.AUDIOBOOK, BookFileType.EPUB, BookFileType.PDF))
                    .build();
            book.setLibrary(library);

            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));

            BookFileProcessor processor = mock(BookFileProcessor.class);
            when(processorRegistry.getProcessorOrThrow(BookFileType.EPUB)).thenReturn(processor);
            when(processor.generateCover(eq(book), any())).thenReturn(true);

            service.regenerateCover(1L);

            verify(processorRegistry).getProcessorOrThrow(BookFileType.EPUB);
        }

        @Test
        void fallsBackToFirstNonAudiobookFileWhenNoPriorityMatch() {
            BookEntity book = buildBook(1L, false);

            BookFileEntity audiobookFile = BookFileEntity.builder()
                    .bookType(BookFileType.AUDIOBOOK)
                    .build();
            BookFileEntity pdfFile = BookFileEntity.builder()
                    .bookType(BookFileType.PDF)
                    .isBookFormat(true)
                    .build();
            book.setBookFiles(List.of(audiobookFile, pdfFile));
            book.setLibrary(LibraryEntity.builder().formatPriority(null).build());

            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));

            BookFileProcessor processor = mock(BookFileProcessor.class);
            when(processorRegistry.getProcessorOrThrow(BookFileType.PDF)).thenReturn(processor);
            when(processor.generateCover(eq(book), any())).thenReturn(true);

            service.regenerateCover(1L);

            verify(processorRegistry).getProcessorOrThrow(BookFileType.PDF);
        }
    }

    @Nested
    class GenerateCustomCover {

        @Test
        void generatesCoverWithTitleAndAuthor() {
            BookEntity book = buildBook(1L, false);
            AuthorEntity author = AuthorEntity.builder().name("Jane Doe").build();
            book.getMetadata().setAuthors(List.of(author));
            book.setBookFiles(new ArrayList<>());
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));
            when(coverImageGenerator.generateCover("Test Book", "Jane Doe")).thenReturn(new byte[]{1, 2, 3});
            when(bookRepository.findCoverUpdateInfoByIds(any())).thenReturn(List.of());

            service.generateCustomCover(1L);

            verify(coverImageGenerator).generateCover("Test Book", "Jane Doe");
            verify(fileService).createThumbnailFromBytes(eq(1L), any());
            verify(bookRepository).save(book);
        }
    }

    @Nested
    class BulkCoverFromFile {

        @Test
        void filtersOutLockedBooksForBulkOperations() {
            BookEntity unlocked = buildBook(1L, false);
            BookEntity locked = buildBook(2L, true);

            when(bookQueryService.findAllWithMetadataByIds(Set.of(1L, 2L)))
                    .thenReturn(List.of(unlocked, locked));

            MultipartFile file = mock(MultipartFile.class);
            when(file.isEmpty()).thenReturn(false);
            when(file.getContentType()).thenReturn("image/jpeg");
            when(file.getSize()).thenReturn(1024L);
            try {
                when(file.getBytes()).thenReturn(new byte[]{1, 2, 3});
            } catch (Exception ignored) {}

            service.updateCoverFromFileForBooks(Set.of(1L, 2L), file);

            verify(bookQueryService).findAllWithMetadataByIds(Set.of(1L, 2L));
        }
    }

    @Nested
    class FileValidation {

        @Test
        void rejectsEmptyFile() {
            MultipartFile file = mock(MultipartFile.class);
            when(file.isEmpty()).thenReturn(true);

            assertThatThrownBy(() -> service.updateCoverFromFileForBooks(Set.of(1L), file))
                    .isInstanceOf(APIException.class)
                    .hasMessageContaining("empty");
        }

        @Test
        void rejectsNonImageContentType() {
            MultipartFile file = mock(MultipartFile.class);
            when(file.isEmpty()).thenReturn(false);
            when(file.getContentType()).thenReturn("application/pdf");

            assertThatThrownBy(() -> service.updateCoverFromFileForBooks(Set.of(1L), file))
                    .isInstanceOf(APIException.class)
                    .hasMessageContaining("JPEG and PNG");
        }

        @Test
        void rejectsFileLargerThan5MB() {
            MultipartFile file = mock(MultipartFile.class);
            when(file.isEmpty()).thenReturn(false);
            when(file.getContentType()).thenReturn("image/jpeg");
            when(file.getSize()).thenReturn(6L * 1024 * 1024);

            assertThatThrownBy(() -> service.updateCoverFromFileForBooks(Set.of(1L), file))
                    .isInstanceOf(APIException.class)
                    .hasMessageContaining("exceeds");
        }

        @Test
        void acceptsJpegFile() {
            MultipartFile file = mock(MultipartFile.class);
            when(file.isEmpty()).thenReturn(false);
            when(file.getContentType()).thenReturn("image/jpeg");
            when(file.getSize()).thenReturn(1024L);
            try {
                when(file.getBytes()).thenReturn(new byte[]{1, 2, 3});
            } catch (Exception ignored) {}

            when(bookQueryService.findAllWithMetadataByIds(any())).thenReturn(List.of());

            service.updateCoverFromFileForBooks(Set.of(1L), file);
        }

        @Test
        void acceptsPngFile() {
            MultipartFile file = mock(MultipartFile.class);
            when(file.isEmpty()).thenReturn(false);
            when(file.getContentType()).thenReturn("image/png");
            when(file.getSize()).thenReturn(1024L);
            try {
                when(file.getBytes()).thenReturn(new byte[]{1, 2, 3});
            } catch (Exception ignored) {}

            when(bookQueryService.findAllWithMetadataByIds(any())).thenReturn(List.of());

            service.updateCoverFromFileForBooks(Set.of(1L), file);
        }

        @Test
        void rejectsNullContentType() {
            MultipartFile file = mock(MultipartFile.class);
            when(file.isEmpty()).thenReturn(false);
            when(file.getContentType()).thenReturn(null);

            assertThatThrownBy(() -> service.updateCoverFromFileForBooks(Set.of(1L), file))
                    .isInstanceOf(APIException.class);
        }
    }

    @Nested
    class UpdateCoverFromUrl {

        @Test
        void successfullyUpdatesCoverFromUrl() {
            BookEntity book = buildBook(1L, false);
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));
            when(bookRepository.findCoverUpdateInfoByIds(any())).thenReturn(List.of());

            service.updateCoverFromUrl(1L, "https://example.com/cover.jpg");

            verify(fileService).createThumbnailFromUrl(1L, "https://example.com/cover.jpg");
            verify(bookRepository).save(book);
            assertThat(book.getMetadata().getCoverUpdatedOn()).isNotNull();
            assertThat(book.getBookCoverHash()).isNotNull();
        }
    }

    @Nested
    class UpdateCoverFromFileSuccess {

        @Test
        void successfullyUpdatesCoverFromFile() {
            BookEntity book = buildBook(1L, false);
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));
            when(bookRepository.findCoverUpdateInfoByIds(any())).thenReturn(List.of());
            MultipartFile file = mock(MultipartFile.class);

            service.updateCoverFromFile(1L, file);

            verify(fileService).createThumbnailFromFile(1L, file);
            verify(bookRepository).save(book);
            assertThat(book.getMetadata().getCoverUpdatedOn()).isNotNull();
        }
    }

    @Nested
    class UpdateAudiobookCoverFromFile {

        @Test
        void successfullyUpdatesCoverFromFile() {
            BookEntity book = buildBookWithAudiobookLock(1L, false);
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));
            when(bookRepository.findCoverUpdateInfoByIds(any())).thenReturn(List.of());
            MultipartFile file = mock(MultipartFile.class);

            service.updateAudiobookCoverFromFile(1L, file);

            verify(fileService).createAudiobookThumbnailFromFile(1L, file);
            verify(bookRepository).save(book);
            assertThat(book.getMetadata().getAudiobookCoverUpdatedOn()).isNotNull();
            assertThat(book.getAudiobookCoverHash()).isNotNull();
        }

        @Test
        void throwsWhenBookNotFound() {
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.empty());
            MultipartFile file = mock(MultipartFile.class);

            assertThatThrownBy(() -> service.updateAudiobookCoverFromFile(1L, file))
                    .isInstanceOf(APIException.class);
        }
    }

    @Nested
    class UpdateAudiobookCoverFromUrl {

        @Test
        void successfullyUpdatesCoverFromUrl() {
            BookEntity book = buildBookWithAudiobookLock(1L, false);
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));
            when(bookRepository.findCoverUpdateInfoByIds(any())).thenReturn(List.of());

            service.updateAudiobookCoverFromUrl(1L, "https://example.com/audiobook-cover.jpg");

            verify(fileService).createAudiobookThumbnailFromUrl(1L, "https://example.com/audiobook-cover.jpg");
            verify(bookRepository).save(book);
            assertThat(book.getMetadata().getAudiobookCoverUpdatedOn()).isNotNull();
            assertThat(book.getAudiobookCoverHash()).isNotNull();
        }

        @Test
        void throwsWhenBookNotFound() {
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateAudiobookCoverFromUrl(1L, "https://example.com"))
                    .isInstanceOf(APIException.class);
        }
    }

    @Nested
    class RegenerateAudiobookCoverSuccess {

        @Test
        void successfullyRegeneratesAudiobookCover() {
            BookEntity book = buildBookWithAudiobookLock(1L, false);
            BookFileEntity audiobookFile = BookFileEntity.builder()
                    .bookType(BookFileType.AUDIOBOOK)
                    .build();
            book.setBookFiles(List.of(audiobookFile));
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));

            BookFileProcessor processor = mock(BookFileProcessor.class);
            when(processorRegistry.getProcessorOrThrow(BookFileType.AUDIOBOOK)).thenReturn(processor);
            when(processor.generateAudiobookCover(book)).thenReturn(true);
            when(bookRepository.findCoverUpdateInfoByIds(any())).thenReturn(List.of());

            service.regenerateAudiobookCover(1L);

            assertThat(book.getMetadata().getAudiobookCoverUpdatedOn()).isNotNull();
            assertThat(book.getAudiobookCoverHash()).isNotNull();
            verify(bookRepository).save(book);
        }
    }

    @Nested
    class GenerateCustomAudiobookCover {

        @Test
        void successfullyGeneratesCustomAudiobookCover() {
            BookEntity book = buildBookWithAudiobookLock(1L, false);
            AuthorEntity author = AuthorEntity.builder().name("Author Name").build();
            book.getMetadata().setAuthors(List.of(author));
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));
            when(coverImageGenerator.generateSquareCover("Test Audiobook", "Author Name")).thenReturn(new byte[]{1, 2});
            when(bookRepository.findCoverUpdateInfoByIds(any())).thenReturn(List.of());

            service.generateCustomAudiobookCover(1L);

            verify(coverImageGenerator).generateSquareCover("Test Audiobook", "Author Name");
            verify(fileService).createAudiobookThumbnailFromBytes(eq(1L), any());
            verify(bookRepository).save(book);
            assertThat(book.getMetadata().getAudiobookCoverUpdatedOn()).isNotNull();
        }

        @Test
        void throwsWhenBookNotFound() {
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.generateCustomAudiobookCover(1L))
                    .isInstanceOf(APIException.class);
        }

        @Test
        void throwsWhenAudiobookCoverLocked() {
            BookEntity book = buildBookWithAudiobookLock(1L, true);
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));

            assertThatThrownBy(() -> service.generateCustomAudiobookCover(1L))
                    .isInstanceOf(APIException.class);
        }

        @Test
        void handlesNullAuthors() {
            BookEntity book = buildBookWithAudiobookLock(1L, false);
            book.getMetadata().setAuthors(null);
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));
            when(coverImageGenerator.generateSquareCover("Test Audiobook", null)).thenReturn(new byte[]{1});
            when(bookRepository.findCoverUpdateInfoByIds(any())).thenReturn(List.of());

            service.generateCustomAudiobookCover(1L);

            verify(coverImageGenerator).generateSquareCover("Test Audiobook", null);
        }
    }

    @Nested
    class BulkRegenerateCoversForBooks {

        @Test
        void delegatesToVirtualThreadWithUnlockedBooks() {
            BookEntity unlocked = buildBook(1L, false);
            unlocked.setBookFiles(List.of(BookFileEntity.builder().bookType(BookFileType.EPUB).isBookFormat(true).build()));
            unlocked.setLibrary(LibraryEntity.builder().build());
            BookEntity locked = buildBook(2L, true);

            when(bookQueryService.findAllWithMetadataByIds(Set.of(1L, 2L)))
                    .thenReturn(List.of(unlocked, locked));

            try (MockedStatic<SecurityContextVirtualThread> secMock = mockStatic(SecurityContextVirtualThread.class)) {
                secMock.when(() -> SecurityContextVirtualThread.runWithSecurityContext(any(Runnable.class)))
                        .thenAnswer(inv -> {
                            inv.<Runnable>getArgument(0).run();
                            return null;
                        });

                service.regenerateCoversForBooks(Set.of(1L, 2L));

                secMock.verify(() -> SecurityContextVirtualThread.runWithSecurityContext(any(Runnable.class)));
            }
        }
    }

    @Nested
    class BulkGenerateCustomCoversForBooks {

        @Test
        void delegatesToVirtualThreadWithUnlockedBooks() {
            BookEntity unlocked = buildBook(1L, false);
            BookEntity locked = buildBook(2L, true);

            when(bookQueryService.findAllWithMetadataByIds(Set.of(1L, 2L)))
                    .thenReturn(List.of(unlocked, locked));

            try (MockedStatic<SecurityContextVirtualThread> secMock = mockStatic(SecurityContextVirtualThread.class)) {
                secMock.when(() -> SecurityContextVirtualThread.runWithSecurityContext(any(Runnable.class)))
                        .thenAnswer(inv -> {
                            inv.<Runnable>getArgument(0).run();
                            return null;
                        });

                service.generateCustomCoversForBooks(Set.of(1L, 2L));

                secMock.verify(() -> SecurityContextVirtualThread.runWithSecurityContext(any(Runnable.class)));
            }
        }
    }

    @Nested
    class BulkUpdateCoverFromFileForBooks {

        @Test
        void processesOnlyUnlockedBooks() throws Exception {
            BookEntity unlocked = buildBook(1L, false);
            BookEntity locked = buildBook(2L, true);

            when(bookQueryService.findAllWithMetadataByIds(Set.of(1L, 2L)))
                    .thenReturn(List.of(unlocked, locked));

            MultipartFile file = mock(MultipartFile.class);
            when(file.isEmpty()).thenReturn(false);
            when(file.getContentType()).thenReturn("image/png");
            when(file.getSize()).thenReturn(1024L);
            when(file.getBytes()).thenReturn(new byte[]{1, 2, 3});

            try (MockedStatic<SecurityContextVirtualThread> secMock = mockStatic(SecurityContextVirtualThread.class)) {
                secMock.when(() -> SecurityContextVirtualThread.runWithSecurityContext(any(Runnable.class)))
                        .thenAnswer(inv -> {
                            inv.<Runnable>getArgument(0).run();
                            return null;
                        });

                service.updateCoverFromFileForBooks(Set.of(1L, 2L), file);

                secMock.verify(() -> SecurityContextVirtualThread.runWithSecurityContext(any(Runnable.class)));
            }
        }
    }

    @Nested
    class RegenerateCoversAll {

        @Test
        void regeneratesCoversForAllUnlockedBooks() {
            BookEntity book = buildBook(1L, false);
            BookFileEntity ebookFile = BookFileEntity.builder()
                    .bookType(BookFileType.EPUB).isBookFormat(true).build();
            book.setBookFiles(List.of(ebookFile));
            book.setLibrary(LibraryEntity.builder().build());

            when(bookQueryService.getAllFullBookEntities()).thenReturn(List.of(book));

            BookFileProcessor processor = mock(BookFileProcessor.class);
            when(transactionTemplate.execute(any())).thenAnswer(inv -> {
                var callback = inv.getArgument(0, org.springframework.transaction.support.TransactionCallback.class);
                return callback.doInTransaction(null);
            });
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));
            when(processorRegistry.getProcessorOrThrow(BookFileType.EPUB)).thenReturn(processor);
            when(processor.generateCover(book)).thenReturn(true);
            when(bookRepository.findCoverUpdateInfoByIds(any())).thenReturn(List.of());

            try (MockedStatic<SecurityContextVirtualThread> secMock = mockStatic(SecurityContextVirtualThread.class)) {
                secMock.when(() -> SecurityContextVirtualThread.runWithSecurityContext(any(Runnable.class)))
                        .thenAnswer(inv -> {
                            inv.<Runnable>getArgument(0).run();
                            return null;
                        });

                service.regenerateCovers(false);

                verify(bookRepository).save(book);
                assertThat(book.getMetadata().getCoverUpdatedOn()).isNotNull();
            }
        }

        @Test
        void skipsLockedBooks() {
            BookEntity locked = buildBook(1L, true);
            BookFileEntity ebookFile = BookFileEntity.builder()
                    .bookType(BookFileType.EPUB).isBookFormat(true).build();
            locked.setBookFiles(List.of(ebookFile));
            locked.setLibrary(LibraryEntity.builder().build());

            when(bookQueryService.getAllFullBookEntities()).thenReturn(List.of(locked));

            try (MockedStatic<SecurityContextVirtualThread> secMock = mockStatic(SecurityContextVirtualThread.class)) {
                secMock.when(() -> SecurityContextVirtualThread.runWithSecurityContext(any(Runnable.class)))
                        .thenAnswer(inv -> {
                            inv.<Runnable>getArgument(0).run();
                            return null;
                        });

                service.regenerateCovers(false);

                verify(bookRepository, never()).save(any());
            }
        }

        @Test
        void missingOnlySkipsBooksWithExistingCover() {
            BookEntity withCover = buildBook(1L, false);
            withCover.setBookCoverHash("existingHash");
            BookFileEntity ebookFile1 = BookFileEntity.builder()
                    .bookType(BookFileType.EPUB).isBookFormat(true).build();
            withCover.setBookFiles(List.of(ebookFile1));
            withCover.setLibrary(LibraryEntity.builder().build());

            BookEntity withoutCover = buildBook(2L, false);
            withoutCover.setBookCoverHash(null);
            BookFileEntity ebookFile2 = BookFileEntity.builder()
                    .bookType(BookFileType.EPUB).isBookFormat(true).build();
            withoutCover.setBookFiles(List.of(ebookFile2));
            withoutCover.setLibrary(LibraryEntity.builder().build());

            when(bookQueryService.getAllFullBookEntities()).thenReturn(List.of(withCover, withoutCover));

            BookFileProcessor processor = mock(BookFileProcessor.class);
            when(transactionTemplate.execute(any())).thenAnswer(inv -> {
                var callback = inv.getArgument(0, org.springframework.transaction.support.TransactionCallback.class);
                return callback.doInTransaction(null);
            });
            when(bookRepository.findByIdWithBookFiles(2L)).thenReturn(Optional.of(withoutCover));
            when(processorRegistry.getProcessorOrThrow(BookFileType.EPUB)).thenReturn(processor);
            when(processor.generateCover(withoutCover)).thenReturn(true);
            when(bookRepository.findCoverUpdateInfoByIds(any())).thenReturn(List.of());

            try (MockedStatic<SecurityContextVirtualThread> secMock = mockStatic(SecurityContextVirtualThread.class)) {
                secMock.when(() -> SecurityContextVirtualThread.runWithSecurityContext(any(Runnable.class)))
                        .thenAnswer(inv -> {
                            inv.<Runnable>getArgument(0).run();
                            return null;
                        });

                service.regenerateCovers(true);

                verify(transactionTemplate, times(1)).execute(any());
                verify(bookRepository).save(withoutCover);
                verify(bookRepository, never()).findById(1L);
            }
        }

        @Test
        void skipsBooksWithNoPrimaryFile() {
            BookEntity book = buildBook(1L, false);
            book.setBookFiles(new ArrayList<>());

            when(bookQueryService.getAllFullBookEntities()).thenReturn(List.of(book));

            try (MockedStatic<SecurityContextVirtualThread> secMock = mockStatic(SecurityContextVirtualThread.class)) {
                secMock.when(() -> SecurityContextVirtualThread.runWithSecurityContext(any(Runnable.class)))
                        .thenAnswer(inv -> {
                            inv.<Runnable>getArgument(0).run();
                            return null;
                        });

                service.regenerateCovers(false);

                verify(transactionTemplate, never()).execute(any());
            }
        }
    }

    @Nested
    class FindEbookFileEdgeCases {

        @Test
        void returnsNullWhenBookFilesIsNull() {
            BookEntity book = buildBook(1L, false);
            book.setBookFiles(null);
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));

            assertThatThrownBy(() -> service.regenerateCover(1L))
                    .isInstanceOf(APIException.class)
                    .hasMessageContaining("no ebook file found");
        }

        @Test
        void returnsNullWhenBookFilesIsEmpty() {
            BookEntity book = buildBook(1L, false);
            book.setBookFiles(new ArrayList<>());
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));

            assertThatThrownBy(() -> service.regenerateCover(1L))
                    .isInstanceOf(APIException.class)
                    .hasMessageContaining("no ebook file found");
        }

        @Test
        void returnsNullWhenOnlyAudiobookFiles() {
            BookEntity book = buildBook(1L, false);
            BookFileEntity audiobookFile = BookFileEntity.builder()
                    .bookType(BookFileType.AUDIOBOOK).isBookFormat(false).build();
            book.setBookFiles(List.of(audiobookFile));
            book.setLibrary(LibraryEntity.builder().formatPriority(null).build());
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));

            assertThatThrownBy(() -> service.regenerateCover(1L))
                    .isInstanceOf(APIException.class)
                    .hasMessageContaining("no ebook file found");
        }

        @Test
        void selectsFirstMatchingFormatFromPriority() {
            BookEntity book = buildBook(1L, false);
            BookFileEntity pdfFile = BookFileEntity.builder()
                    .bookType(BookFileType.PDF).isBookFormat(true).build();
            BookFileEntity epubFile = BookFileEntity.builder()
                    .bookType(BookFileType.EPUB).isBookFormat(true).build();
            book.setBookFiles(List.of(pdfFile, epubFile));
            book.setLibrary(LibraryEntity.builder()
                    .formatPriority(List.of(BookFileType.EPUB, BookFileType.PDF)).build());
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));

            BookFileProcessor processor = mock(BookFileProcessor.class);
            when(processorRegistry.getProcessorOrThrow(BookFileType.EPUB)).thenReturn(processor);
            when(processor.generateCover(eq(book), eq(epubFile))).thenReturn(true);

            service.regenerateCover(1L);

            verify(processorRegistry).getProcessorOrThrow(BookFileType.EPUB);
        }

        @Test
        void fallsBackWhenPriorityFormatNotAvailable() {
            BookEntity book = buildBook(1L, false);
            BookFileEntity pdfFile = BookFileEntity.builder()
                    .bookType(BookFileType.PDF).isBookFormat(true).build();
            book.setBookFiles(List.of(pdfFile));
            book.setLibrary(LibraryEntity.builder()
                    .formatPriority(List.of(BookFileType.EPUB)).build());
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));

            BookFileProcessor processor = mock(BookFileProcessor.class);
            when(processorRegistry.getProcessorOrThrow(BookFileType.PDF)).thenReturn(processor);
            when(processor.generateCover(eq(book), eq(pdfFile))).thenReturn(true);

            service.regenerateCover(1L);

            verify(processorRegistry).getProcessorOrThrow(BookFileType.PDF);
        }

        @Test
        void handlesEmptyFormatPriorityList() {
            BookEntity book = buildBook(1L, false);
            BookFileEntity epubFile = BookFileEntity.builder()
                    .bookType(BookFileType.EPUB).isBookFormat(true).build();
            book.setBookFiles(List.of(epubFile));
            book.setLibrary(LibraryEntity.builder().formatPriority(List.of()).build());
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));

            BookFileProcessor processor = mock(BookFileProcessor.class);
            when(processorRegistry.getProcessorOrThrow(BookFileType.EPUB)).thenReturn(processor);
            when(processor.generateCover(eq(book), eq(epubFile))).thenReturn(true);

            service.regenerateCover(1L);

            verify(processorRegistry).getProcessorOrThrow(BookFileType.EPUB);
        }
    }

    @Nested
    class WriteCoverToBookFile {

        @Test
        void writesAndUpdatesHashWhenWriterExists() {
            BookEntity book = buildBook(1L, false);
            BookFileEntity primaryFile = BookFileEntity.builder()
                    .bookType(BookFileType.EPUB).isBookFormat(true)
                    .fileName("test.epub").fileSubPath("sub")
                    .build();
            book.setBookFiles(List.of(primaryFile));
            book.setLibrary(LibraryEntity.builder().build());
            book.setLibraryPath(LibraryPathEntity.builder().path("/lib").build());

            AppSettings appSettings = mock(AppSettings.class);
            MetadataPersistenceSettings persistSettings = mock(MetadataPersistenceSettings.class);
            when(appSettingService.getAppSettings()).thenReturn(appSettings);
            when(appSettings.getMetadataPersistenceSettings()).thenReturn(persistSettings);
            when(persistSettings.isConvertCbrCb7ToCbz()).thenReturn(false);

            MetadataWriter writer = mock(MetadataWriter.class);
            when(metadataWriterFactory.getWriter(BookFileType.EPUB)).thenReturn(Optional.of(writer));
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));
            when(bookRepository.findCoverUpdateInfoByIds(any())).thenReturn(List.of());

            try (MockedStatic<FileFingerprint> fpMock = mockStatic(FileFingerprint.class)) {
                fpMock.when(() -> FileFingerprint.generateHash(any())).thenReturn("abc123");

                service.updateCoverFromUrl(1L, "https://example.com/cover.jpg");

                verify(metadataWriterFactory).getWriter(BookFileType.EPUB);
                assertThat(primaryFile.getCurrentHash()).isEqualTo("abc123");
            }
        }

        @Test
        void skipsWriteWhenNoPrimaryFile() {
            BookEntity book = buildBook(1L, false);
            book.setBookFiles(new ArrayList<>());
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));
            when(bookRepository.findCoverUpdateInfoByIds(any())).thenReturn(List.of());

            service.updateCoverFromUrl(1L, "https://example.com/cover.jpg");

            verify(metadataWriterFactory, never()).getWriter(any());
        }
    }

    @Nested
    class NotifyBookCoverUpdate {

        @Test
        void sendsNotificationWhenUpdatesExist() {
            BookEntity book = buildBook(1L, false);
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));

            BookCoverUpdateProjection projection = mock(BookCoverUpdateProjection.class);
            when(bookRepository.findCoverUpdateInfoByIds(List.of(1L))).thenReturn(List.of(projection));

            service.updateCoverFromUrl(1L, "https://example.com/cover.jpg");

            verify(notificationService).sendMessage(any(), eq(List.of(projection)));
        }

        @Test
        void doesNotSendNotificationWhenNoUpdates() {
            BookEntity book = buildBook(1L, false);
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));
            when(bookRepository.findCoverUpdateInfoByIds(any())).thenReturn(List.of());

            service.updateCoverFromUrl(1L, "https://example.com/cover.jpg");

            verify(notificationService, never()).sendMessage(any(), anyList());
        }
    }

    @Nested
    class GetAuthorNames {

        @Test
        void returnsNullForEmptyAuthors() {
            BookEntity book = buildBook(1L, false);
            book.getMetadata().setAuthors(new ArrayList<>());
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));
            when(coverImageGenerator.generateCover("Test Book", null)).thenReturn(new byte[]{1});
            when(bookRepository.findCoverUpdateInfoByIds(any())).thenReturn(List.of());

            service.generateCustomCover(1L);

            verify(coverImageGenerator).generateCover("Test Book", null);
        }

        @Test
        void joinsMultipleAuthorNames() {
            BookEntity book = buildBook(1L, false);
            List<AuthorEntity> authors = new ArrayList<>();
            authors.add(AuthorEntity.builder().name("Alice").build());
            authors.add(AuthorEntity.builder().name("Bob").build());
            book.getMetadata().setAuthors(authors);
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));
            when(coverImageGenerator.generateCover(eq("Test Book"), argThat(s -> s.contains("Alice") && s.contains("Bob"))))
                    .thenReturn(new byte[]{1});
            when(bookRepository.findCoverUpdateInfoByIds(any())).thenReturn(List.of());

            service.generateCustomCover(1L);

            verify(coverImageGenerator).generateCover(eq("Test Book"), argThat(s -> s.contains("Alice") && s.contains("Bob")));
        }
    }

    @Nested
    class NetworkStorageGating {

        @Test
        void writeCoverToBookFile_networkStorage_skipsFileWrite() {
            when(appProperties.isLocalStorage()).thenReturn(false);

            BookEntity book = buildBook(1L, false);
            BookFileEntity bookFile = BookFileEntity.builder()
                    .bookType(BookFileType.EPUB)
                    .isBookFormat(true)
                    .build();
            book.setBookFiles(List.of(bookFile));
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));
            when(bookRepository.findCoverUpdateInfoByIds(any())).thenReturn(List.of());

            service.updateCoverFromUrl(1L, "https://example.com/cover.jpg");

            verify(metadataWriterFactory, never()).getWriter(any());
            verify(bookRepository).save(book);
        }

        @Test
        void writeAudiobookCoverToFile_networkStorage_skipsFileWrite() {
            when(appProperties.isLocalStorage()).thenReturn(false);

            BookEntity book = buildBookWithAudiobookLock(1L, false);
            BookFileEntity audiobookFile = BookFileEntity.builder()
                    .bookType(BookFileType.AUDIOBOOK)
                    .build();
            book.setBookFiles(List.of(audiobookFile));
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));
            when(bookRepository.findCoverUpdateInfoByIds(any())).thenReturn(List.of());

            service.updateAudiobookCoverFromUrl(1L, "https://example.com/audiobook-cover.jpg");

            verify(metadataWriterFactory, never()).getWriter(any());
            verify(bookRepository).save(book);
        }
    }
}
