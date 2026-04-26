package org.grimmory.service.email;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.request.SendBookByEmailRequest;
import org.booklore.model.entity.*;
import org.booklore.repository.BookRepository;
import org.booklore.repository.EmailProviderV2Repository;
import org.booklore.repository.EmailRecipientV2Repository;
import org.booklore.repository.UserEmailProviderPreferenceRepository;
import org.booklore.service.NotificationService;
import org.booklore.service.audit.AuditService;
import org.booklore.util.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SendEmailV2ServiceTest {

    @Mock
    private EmailProviderV2Repository emailProviderRepository;

    @Mock
    private UserEmailProviderPreferenceRepository preferenceRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private EmailRecipientV2Repository emailRecipientRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AuthenticationService authenticationService;

    @Mock
    private AuditService auditService;

    @Mock
    private Executor taskExecutor;

    @InjectMocks
    private SendEmailV2Service sendEmailV2Service;

    private BookLoreUser user;
    private BookEntity book;
    private BookFileEntity bookFile;
    private EmailProviderV2Entity emailProvider;
    private EmailRecipientV2Entity emailRecipient;
    private UserEmailProviderPreferenceEntity preference;

    @BeforeEach
    void setUp() {
        user = BookLoreUser.builder().id(1L).username("testuser").build();

        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("Test Book")
                .build();

        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setPath("/library");

        bookFile = new BookFileEntity();
        bookFile.setFileName("test-book.epub");
        bookFile.setFileSubPath("books");
        bookFile.setBookFormat(true);

        book = new BookEntity();
        book.setId(10L);
        book.setMetadata(metadata);
        book.setLibraryPath(libraryPath);
        book.setBookFiles(List.of(bookFile));

        emailProvider = EmailProviderV2Entity.builder()
                .id(100L)
                .userId(1L)
                .name("Test Provider")
                .host("smtp.test.com")
                .port(587)
                .username("user@test.com")
                .password("password")
                .auth(true)
                .startTls(true)
                .build();

        emailRecipient = EmailRecipientV2Entity.builder()
                .id(200L)
                .userId(1L)
                .email("recipient@test.com")
                .name("Test Recipient")
                .defaultRecipient(true)
                .build();

        preference = UserEmailProviderPreferenceEntity.builder()
                .id(1L)
                .userId(1L)
                .defaultProviderId(100L)
                .build();
    }

    @Test
    void emailBookQuick_success() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(bookRepository.findByIdWithBookFiles(10L)).thenReturn(Optional.of(book));
        when(preferenceRepository.findByUserId(1L)).thenReturn(Optional.of(preference));
        when(emailProviderRepository.findAccessibleProvider(100L, 1L)).thenReturn(Optional.of(emailProvider));
        when(emailRecipientRepository.findDefaultEmailRecipientByUserId(1L)).thenReturn(Optional.of(emailRecipient));

        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(taskExecutor).execute(any(Runnable.class));

        try (MockedStatic<FileUtils> fileUtilsMock = mockStatic(FileUtils.class)) {
            fileUtilsMock.when(() -> FileUtils.getBookFullPath(book, bookFile)).thenReturn(Path.of("/library/books/test-book.epub"));

            sendEmailV2Service.emailBookQuick(10L);

            verify(taskExecutor).execute(any(Runnable.class));
            verify(notificationService, atLeastOnce()).sendMessage(any(), any());
        }
    }

    @Test
    void emailBookQuick_bookNotFound() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(bookRepository.findByIdWithBookFiles(10L)).thenReturn(Optional.empty());

        assertThrows(APIException.class, () -> sendEmailV2Service.emailBookQuick(10L));
    }

    @Test
    void emailBookQuick_defaultProviderNotFound_noPreference() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(bookRepository.findByIdWithBookFiles(10L)).thenReturn(Optional.of(book));
        when(preferenceRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThrows(APIException.class, () -> sendEmailV2Service.emailBookQuick(10L));
    }

    @Test
    void emailBookQuick_defaultProviderNotFound_providerMissing() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(bookRepository.findByIdWithBookFiles(10L)).thenReturn(Optional.of(book));
        when(preferenceRepository.findByUserId(1L)).thenReturn(Optional.of(preference));
        when(emailProviderRepository.findAccessibleProvider(100L, 1L)).thenReturn(Optional.empty());

        assertThrows(APIException.class, () -> sendEmailV2Service.emailBookQuick(10L));
    }

    @Test
    void emailBookQuick_defaultRecipientNotFound() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(bookRepository.findByIdWithBookFiles(10L)).thenReturn(Optional.of(book));
        when(preferenceRepository.findByUserId(1L)).thenReturn(Optional.of(preference));
        when(emailProviderRepository.findAccessibleProvider(100L, 1L)).thenReturn(Optional.of(emailProvider));
        when(emailRecipientRepository.findDefaultEmailRecipientByUserId(1L)).thenReturn(Optional.empty());

        assertThrows(APIException.class, () -> sendEmailV2Service.emailBookQuick(10L));
    }

    @Test
    void emailBook_success_userOwnedProvider() {
        SendBookByEmailRequest request = SendBookByEmailRequest.builder()
                .bookId(10L)
                .providerId(100L)
                .recipientId(200L)
                .build();

        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(emailProviderRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(emailProvider));
        when(bookRepository.findByIdWithBookFiles(10L)).thenReturn(Optional.of(book));
        when(emailRecipientRepository.findByIdAndUserId(200L, 1L)).thenReturn(Optional.of(emailRecipient));

        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(taskExecutor).execute(any(Runnable.class));

        try (MockedStatic<FileUtils> fileUtilsMock = mockStatic(FileUtils.class)) {
            fileUtilsMock.when(() -> FileUtils.getBookFullPath(book, bookFile)).thenReturn(Path.of("/library/books/test-book.epub"));

            sendEmailV2Service.emailBook(request);

            verify(taskExecutor).execute(any(Runnable.class));
            verify(notificationService, atLeastOnce()).sendMessage(any(), any());
        }
    }

    @Test
    void emailBook_success_sharedProvider() {
        SendBookByEmailRequest request = SendBookByEmailRequest.builder()
                .bookId(10L)
                .providerId(100L)
                .recipientId(200L)
                .build();

        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(emailProviderRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.empty());
        when(emailProviderRepository.findSharedProviderById(100L)).thenReturn(Optional.of(emailProvider));
        when(bookRepository.findByIdWithBookFiles(10L)).thenReturn(Optional.of(book));
        when(emailRecipientRepository.findByIdAndUserId(200L, 1L)).thenReturn(Optional.of(emailRecipient));

        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(taskExecutor).execute(any(Runnable.class));

        try (MockedStatic<FileUtils> fileUtilsMock = mockStatic(FileUtils.class)) {
            fileUtilsMock.when(() -> FileUtils.getBookFullPath(book, bookFile)).thenReturn(Path.of("/library/books/test-book.epub"));

            sendEmailV2Service.emailBook(request);

            verify(taskExecutor).execute(any(Runnable.class));
            verify(notificationService, atLeastOnce()).sendMessage(any(), any());
        }
    }

    @Test
    void emailBook_providerNotFound() {
        SendBookByEmailRequest request = SendBookByEmailRequest.builder()
                .bookId(10L)
                .providerId(100L)
                .recipientId(200L)
                .build();

        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(emailProviderRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.empty());
        when(emailProviderRepository.findSharedProviderById(100L)).thenReturn(Optional.empty());

        assertThrows(APIException.class, () -> sendEmailV2Service.emailBook(request));
    }

    @Test
    void emailBook_bookNotFound() {
        SendBookByEmailRequest request = SendBookByEmailRequest.builder()
                .bookId(10L)
                .providerId(100L)
                .recipientId(200L)
                .build();

        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(emailProviderRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(emailProvider));
        when(bookRepository.findByIdWithBookFiles(10L)).thenReturn(Optional.empty());

        assertThrows(APIException.class, () -> sendEmailV2Service.emailBook(request));
    }

    @Test
    void emailBook_recipientNotFound() {
        SendBookByEmailRequest request = SendBookByEmailRequest.builder()
                .bookId(10L)
                .providerId(100L)
                .recipientId(200L)
                .build();

        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(emailProviderRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(emailProvider));
        when(bookRepository.findByIdWithBookFiles(10L)).thenReturn(Optional.of(book));
        when(emailRecipientRepository.findByIdAndUserId(200L, 1L)).thenReturn(Optional.empty());

        assertThrows(APIException.class, () -> sendEmailV2Service.emailBook(request));
    }

    @Test
    void emailBookQuick_sendEmailFailure_logsError() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(bookRepository.findByIdWithBookFiles(10L)).thenReturn(Optional.of(book));
        when(preferenceRepository.findByUserId(1L)).thenReturn(Optional.of(preference));
        when(emailProviderRepository.findAccessibleProvider(100L, 1L)).thenReturn(Optional.of(emailProvider));
        when(emailRecipientRepository.findDefaultEmailRecipientByUserId(1L)).thenReturn(Optional.of(emailRecipient));

        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(taskExecutor).execute(any(Runnable.class));

        try (MockedStatic<FileUtils> fileUtilsMock = mockStatic(FileUtils.class)) {
            fileUtilsMock.when(() -> FileUtils.getBookFullPath(book, bookFile))
                    .thenThrow(new IllegalStateException("Book file not found"));

            sendEmailV2Service.emailBookQuick(10L);

            // Error is caught and logged, not rethrown
            verify(notificationService, atLeastOnce()).sendMessage(any(), any());
        }
    }

    @Test
    void emailBook_notificationSentBeforeAsyncExecution() {
        SendBookByEmailRequest request = SendBookByEmailRequest.builder()
                .bookId(10L)
                .providerId(100L)
                .recipientId(200L)
                .build();

        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(emailProviderRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(emailProvider));
        when(bookRepository.findByIdWithBookFiles(10L)).thenReturn(Optional.of(book));
        when(emailRecipientRepository.findByIdAndUserId(200L, 1L)).thenReturn(Optional.of(emailRecipient));

        // Don't execute the runnable - just capture it
        doNothing().when(taskExecutor).execute(any(Runnable.class));

        try (MockedStatic<FileUtils> fileUtilsMock = mockStatic(FileUtils.class)) {
            fileUtilsMock.when(() -> FileUtils.getBookFullPath(book, bookFile)).thenReturn(Path.of("/library/books/test-book.epub"));

            sendEmailV2Service.emailBook(request);

            InOrder inOrder = inOrder(notificationService, taskExecutor);
            inOrder.verify(notificationService).sendMessage(any(), any());
            inOrder.verify(taskExecutor).execute(any(Runnable.class));
        }
    }
}
