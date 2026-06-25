package org.booklore.service.book;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
import org.booklore.mapper.BookMapper;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.request.BookConversionRequest;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.websocket.Topic;
import org.booklore.repository.BookAdditionalFileRepository;
import org.booklore.repository.BookRepository;
import org.booklore.repository.LibraryRepository;
import org.booklore.service.NotificationService;
import org.booklore.service.monitoring.MonitoringRegistrationService;
import org.booklore.service.restriction.ContentRestrictionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookConversionServiceTest {

    @Mock private BookRepository bookRepository;
    @Mock private BookAdditionalFileRepository bookAdditionalFileRepository;
    @Mock private LibraryRepository libraryRepository;
    @Mock private AuthenticationService authenticationService;
    @Mock private ContentRestrictionService contentRestrictionService;
    @Mock private CalibreEbookConversionService calibreEbookConversionService;
    @Mock private MonitoringRegistrationService monitoringRegistrationService;
    @Mock private NotificationService notificationService;
    @Mock private BookMapper bookMapper;
    @Mock private TransactionTemplate transactionTemplate;

    @TempDir
    Path tempDir;

    private LibraryEntity library;
    private LibraryPathEntity libraryPath;
    private BookConversionService service;

    @BeforeEach
    void setUp() {
        library = LibraryEntity.builder()
                .id(1L)
                .name("Test Library")
                .allowedFormats(new ArrayList<>())
                .libraryPaths(new ArrayList<>())
                .build();
        libraryPath = LibraryPathEntity.builder()
                .id(10L)
                .library(library)
                .path(tempDir.toString())
                .build();
        library.getLibraryPaths().add(libraryPath);

        stubTransactionTemplate();
        service = createService(Runnable::run);
    }

    @Test
    void scheduleConversions_rejectsUnavailableConverter() {
        when(calibreEbookConversionService.isAvailable()).thenReturn(false);

        assertThatExceptionOfType(APIException.class)
                .isThrownBy(() -> service.scheduleConversions(request(BookFileType.EPUB, 1L)))
                .withMessageContaining("Calibre ebook-convert is not available");
    }

    @Test
    void scheduleConversions_rejectsUnsupportedTargetFormat() {
        assertThatExceptionOfType(APIException.class)
                .isThrownBy(() -> service.scheduleConversions(request(BookFileType.CBX, 1L)))
                .withMessageContaining("Unsupported target conversion format: CBX");

        verifyNoInteractions(calibreEbookConversionService);
    }

    @Test
    void scheduleConversions_convertsAccessibleBookAndPersistsTargetFile() throws Exception {
        BookEntity book = createBook(100L, "Dune.epub", "series", BookFileType.EPUB);
        Book dto = Book.builder().id(100L).build();
        when(calibreEbookConversionService.isAvailable()).thenReturn(true);
        when(authenticationService.getAuthenticatedUser()).thenReturn(adminUser());
        when(bookRepository.findByIdWithBookFiles(100L)).thenReturn(Optional.of(book));
        when(libraryRepository.findByIdWithPaths(1L)).thenReturn(Optional.of(library));
        when(monitoringRegistrationService.getPathsForLibraries(Set.of(1L))).thenReturn(Set.of(tempDir));
        when(bookMapper.toBookWithDescription(book, false)).thenReturn(dto);
        doAnswer(invocation -> {
            Path output = invocation.getArgument(1);
            Files.writeString(output, "converted mobi");
            return output;
        }).when(calibreEbookConversionService).convert(any(Path.class), any(Path.class));
        doAnswer(invocation -> {
            BookFileEntity saved = invocation.getArgument(0);
            saved.setId(500L);
            book.getBookFiles().add(saved);
            return saved;
        }).when(bookAdditionalFileRepository).save(any(BookFileEntity.class));

        var response = service.scheduleConversions(request(BookFileType.MOBI, 100L));

        assertThat(response.acceptedCount()).isEqualTo(1);
        assertThat(response.targetFormat()).isEqualTo(BookFileType.MOBI);
        Path target = tempDir.resolve("series/Dune.mobi");
        assertThat(target).exists().hasContent("converted mobi");

        ArgumentCaptor<BookFileEntity> captor = ArgumentCaptor.forClass(BookFileEntity.class);
        verify(bookAdditionalFileRepository).save(captor.capture());
        BookFileEntity saved = captor.getValue();
        assertThat(saved.getBook()).isSameAs(book);
        assertThat(saved.getFileName()).isEqualTo("Dune.mobi");
        assertThat(saved.getFileSubPath()).isEqualTo("series");
        assertThat(saved.isBookFormat()).isTrue();
        assertThat(saved.isFolderBased()).isFalse();
        assertThat(saved.getBookType()).isEqualTo(BookFileType.MOBI);
        assertThat(saved.getFileSizeKb()).isEqualTo(0L);
        assertThat(saved.getInitialHash()).isNotBlank();
        assertThat(saved.getCurrentHash()).isEqualTo(saved.getInitialHash());
        assertThat(saved.getAddedOn()).isNotNull();

        verify(monitoringRegistrationService).unregisterLibrary(1L);
        verify(monitoringRegistrationService).waitForEventsDrainedByPaths(Set.of(tempDir), 300L);
        verify(monitoringRegistrationService).registerLibraryPaths(1L, tempDir);
        verify(notificationService).sendMessageToUser("admin", Topic.BOOK_UPDATE, dto);
    }

    @Test
    void scheduleConversions_skipsExistingTargetFormatWithoutInvokingCalibre() throws Exception {
        BookEntity book = createBook(100L, "Dune.epub", "", BookFileType.EPUB);
        book.getBookFiles().add(BookFileEntity.builder()
                .id(101L)
                .book(book)
                .fileName("Dune.mobi")
                .fileSubPath("")
                .isBookFormat(true)
                .bookType(BookFileType.MOBI)
                .build());
        when(calibreEbookConversionService.isAvailable()).thenReturn(true);
        when(authenticationService.getAuthenticatedUser()).thenReturn(adminUser());
        when(bookRepository.findByIdWithBookFiles(100L)).thenReturn(Optional.of(book));

        service.scheduleConversions(request(BookFileType.MOBI, 100L));

        verify(calibreEbookConversionService, never()).convert(any(Path.class), any(Path.class));
        verify(bookAdditionalFileRepository, never()).save(any(BookFileEntity.class));
    }

    @Test
    void scheduleConversions_skipsTargetFormatDisallowedByLibrary() throws Exception {
        library.setAllowedFormats(List.of(BookFileType.EPUB));
        BookEntity book = createBook(100L, "Dune.epub", "", BookFileType.EPUB);
        when(calibreEbookConversionService.isAvailable()).thenReturn(true);
        when(authenticationService.getAuthenticatedUser()).thenReturn(adminUser());
        when(bookRepository.findByIdWithBookFiles(100L)).thenReturn(Optional.of(book));

        service.scheduleConversions(request(BookFileType.MOBI, 100L));

        verify(calibreEbookConversionService, never()).convert(any(Path.class), any(Path.class));
        verify(bookAdditionalFileRepository, never()).save(any(BookFileEntity.class));
    }

    @Test
    void scheduleConversions_rejectsRestrictedNonAdminBeforeQueuingWork() throws Exception {
        Executor executor = mock(Executor.class);
        service = createService(executor);
        BookEntity book = createBook(100L, "Dune.epub", "", BookFileType.EPUB);
        BookLoreUser user = nonAdminUserWithAssignedLibrary(1L);
        when(calibreEbookConversionService.isAvailable()).thenReturn(true);
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(bookRepository.findByIdWithBookFiles(100L)).thenReturn(Optional.of(book));
        when(contentRestrictionService.applyRestrictions(anyList(), eq(77L))).thenReturn(List.of());

        assertThatExceptionOfType(APIException.class)
                .isThrownBy(() -> service.scheduleConversions(request(BookFileType.MOBI, 100L)))
                .withMessageContaining("You are not authorized to access this book.");

        verify(executor, never()).execute(any(Runnable.class));
    }

    private BookConversionService createService(Executor executor) {
        return new BookConversionService(
                bookRepository,
                bookAdditionalFileRepository,
                libraryRepository,
                authenticationService,
                contentRestrictionService,
                calibreEbookConversionService,
                monitoringRegistrationService,
                notificationService,
                bookMapper,
                executor,
                transactionTemplate
        );
    }

    private void stubTransactionTemplate() {
        lenient().doAnswer(invocation -> {
            Consumer<TransactionStatus> consumer = invocation.getArgument(0);
            consumer.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
    }

    private BookConversionRequest request(BookFileType targetFormat, Long... bookIds) {
        BookConversionRequest request = new BookConversionRequest();
        request.setBookIds(new LinkedHashSet<>(List.of(bookIds)));
        request.setTargetFormat(targetFormat);
        return request;
    }

    private BookEntity createBook(Long id, String fileName, String fileSubPath, BookFileType sourceType) throws IOException {
        Path directory = fileSubPath == null || fileSubPath.isBlank()
                ? tempDir
                : tempDir.resolve(fileSubPath);
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(fileName), "source");

        BookEntity book = BookEntity.builder()
                .id(id)
                .library(library)
                .libraryPath(libraryPath)
                .bookFiles(new ArrayList<>())
                .build();
        BookFileEntity file = BookFileEntity.builder()
                .id(1L)
                .book(book)
                .fileName(fileName)
                .fileSubPath(fileSubPath == null ? "" : fileSubPath)
                .isBookFormat(true)
                .folderBased(false)
                .bookType(sourceType)
                .build();
        book.getBookFiles().add(file);
        return book;
    }

    private BookLoreUser adminUser() {
        BookLoreUser.UserPermissions permissions = new BookLoreUser.UserPermissions();
        permissions.setAdmin(true);
        permissions.setCanMoveOrganizeFiles(true);
        return BookLoreUser.builder()
                .id(1L)
                .username("admin")
                .permissions(permissions)
                .assignedLibraries(List.of())
                .build();
    }

    private BookLoreUser nonAdminUserWithAssignedLibrary(Long libraryId) {
        BookLoreUser.UserPermissions permissions = new BookLoreUser.UserPermissions();
        permissions.setCanMoveOrganizeFiles(true);
        return BookLoreUser.builder()
                .id(77L)
                .username("reader")
                .permissions(permissions)
                .assignedLibraries(List.of(org.booklore.model.dto.Library.builder().id(libraryId).build()))
                .build();
    }
}
