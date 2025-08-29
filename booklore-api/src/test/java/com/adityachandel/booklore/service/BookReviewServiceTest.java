package com.adityachandel.booklore.service;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.mapper.BookReviewMapper;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.BookReview;
import com.adityachandel.booklore.model.dto.settings.AppSettings;
import com.adityachandel.booklore.model.dto.settings.MetadataPublicReviewsSettings;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.entity.BookReviewEntity;
import com.adityachandel.booklore.model.enums.MetadataProvider;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.BookReviewRepository;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.metadata.BookReviewUpdateService;
import com.adityachandel.booklore.service.metadata.MetadataRefreshService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookReviewServiceTest {

    @Mock
    private BookReviewRepository bookReviewRepository;
    @Mock
    private BookReviewMapper mapper;
    @Mock
    private BookReviewUpdateService bookReviewUpdateService;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private AppSettingService appSettingService;
    @Mock
    private MetadataRefreshService metadataRefreshService;
    @Mock
    private AuthenticationService authenticationService;

    private BookReviewService service;

    @BeforeEach
    void setUp() {
        service = new BookReviewService(
            bookReviewRepository,
            mapper,
            bookReviewUpdateService,
            bookRepository,
            appSettingService,
            metadataRefreshService,
            authenticationService
        );
    }

    private BookReview createBookReview(MetadataProvider provider) {
        return BookReview.builder()
            .metadataProvider(provider)
            .reviewerName("Test Reviewer")
            .title("Great Book")
            .rating(4.5f)
            .date(Instant.now())
            .body("Excellent read")
            .build();
    }

    private BookReviewEntity createBookReviewEntity(MetadataProvider provider) {
        return BookReviewEntity.builder()
            .metadataProvider(provider)
            .reviewerName("Test Reviewer")
            .title("Great Book")
            .rating(4.5f)
            .date(Instant.now())
            .body("Excellent read")
            .build();
    }

    private BookLoreUser createUser(boolean isAdmin, boolean canManipulateLibrary) {
        BookLoreUser.UserPermissions permissions = new BookLoreUser.UserPermissions();
        permissions.setAdmin(isAdmin);
        permissions.setCanManipulateLibrary(canManipulateLibrary);

        BookLoreUser user = new BookLoreUser();
        user.setPermissions(permissions);
        return user;
    }

    private MetadataPublicReviewsSettings createReviewSettings(boolean enabled, MetadataProvider... providers) {
        Set<MetadataPublicReviewsSettings.ReviewProviderConfig> configs = new HashSet<>();
        for (MetadataProvider provider : providers) {
            MetadataPublicReviewsSettings.ReviewProviderConfig config =
                MetadataPublicReviewsSettings.ReviewProviderConfig.builder()
                    .provider(provider)
                    .enabled(true)
                    .build();
            configs.add(config);
        }

        return MetadataPublicReviewsSettings.builder()
            .downloadEnabled(enabled)
            .providers(configs)
            .build();
    }

    @Test
    void getByBookId_returnsExistingReviews_whenReviewsExist() {
        Long bookId = 1L;
        BookReviewEntity entity = createBookReviewEntity(MetadataProvider.Amazon);
        BookReview dto = createBookReview(MetadataProvider.Amazon);
        AppSettings appSettings = new AppSettings();
        appSettings.setMetadataPublicReviewsSettings(createReviewSettings(true));

        when(bookReviewRepository.findByBookMetadataBookId(bookId))
            .thenReturn(Collections.singletonList(entity));
        when(mapper.toDto(entity)).thenReturn(dto);
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        List<BookReview> result = service.getByBookId(bookId);

        assertEquals(1, result.size());
        assertEquals(dto, result.getFirst());
        verify(metadataRefreshService, never()).fetchMetadataForBook(anyList(), any(BookEntity.class));
    }

    @Test
    void getByBookId_returnsEmptyList_whenDownloadDisabled() {
        Long bookId = 1L;
        AppSettings appSettings = new AppSettings();
        appSettings.setMetadataPublicReviewsSettings(createReviewSettings(false));

        when(bookReviewRepository.findByBookMetadataBookId(bookId))
            .thenReturn(Collections.emptyList());
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        List<BookReview> result = service.getByBookId(bookId);

        assertTrue(result.isEmpty());
        verify(metadataRefreshService, never()).fetchMetadataForBook(anyList(), any(BookEntity.class));
    }

    @Test
    void getByBookId_returnsEmptyList_whenUserLacksPermissions() {
        Long bookId = 1L;
        AppSettings appSettings = new AppSettings();
        appSettings.setMetadataPublicReviewsSettings(createReviewSettings(true, MetadataProvider.Amazon));
        BookLoreUser user = createUser(false, false);

        when(bookReviewRepository.findByBookMetadataBookId(bookId))
            .thenReturn(Collections.emptyList());
        when(appSettingService.getAppSettings()).thenReturn(appSettings);
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);

        List<BookReview> result = service.getByBookId(bookId);

        assertTrue(result.isEmpty());
        verify(metadataRefreshService, never()).fetchMetadataForBook(anyList(), any(BookEntity.class));
    }

    @Test
    void getByBookId_fetchesAndSavesReviews_whenAdminUserAndNoExistingReviews() {
        Long bookId = 1L;
        AppSettings appSettings = new AppSettings();
        appSettings.setMetadataPublicReviewsSettings(createReviewSettings(true, MetadataProvider.Amazon));
        BookLoreUser user = createUser(true, false);
        BookEntity bookEntity = new BookEntity();
        bookEntity.setMetadata(new BookMetadataEntity());
        BookReview freshReview = createBookReview(MetadataProvider.Amazon);
        BookReviewEntity savedEntity = createBookReviewEntity(MetadataProvider.Amazon);

        when(bookReviewRepository.findByBookMetadataBookId(bookId))
            .thenReturn(Collections.emptyList())
            .thenReturn(Collections.singletonList(savedEntity));
        when(appSettingService.getAppSettings()).thenReturn(appSettings);
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(bookRepository.findById(bookId)).thenReturn(Optional.of(bookEntity));
        when(mapper.toDto(savedEntity)).thenReturn(freshReview);

        Map<MetadataProvider, BookMetadata> metadataMap = new HashMap<>();
        metadataMap.put(MetadataProvider.Amazon, BookMetadata.builder()
            .bookReviews(Collections.singletonList(freshReview))
            .build());
        when(metadataRefreshService.fetchMetadataForBook(
            eq(Collections.singletonList(MetadataProvider.Amazon)), eq(bookEntity)))
            .thenReturn(metadataMap);

        List<BookReview> result = service.getByBookId(bookId);

        assertEquals(1, result.size());
        assertEquals(freshReview, result.get(0));
        verify(bookReviewUpdateService).addReviewsToBook(
            Collections.singletonList(freshReview), bookEntity.getMetadata());
        verify(bookRepository).save(bookEntity);
    }

    @Test
    void getByBookId_fetchesAndSavesReviews_whenLibraryManipulatorAndNoExistingReviews() {
        Long bookId = 1L;
        AppSettings appSettings = new AppSettings();
        appSettings.setMetadataPublicReviewsSettings(createReviewSettings(true, MetadataProvider.GoodReads));
        BookLoreUser user = createUser(false, true);
        BookEntity bookEntity = new BookEntity();
        bookEntity.setMetadata(new BookMetadataEntity());
        BookReview freshReview = createBookReview(MetadataProvider.GoodReads);
        BookReviewEntity savedEntity = createBookReviewEntity(MetadataProvider.GoodReads);

        when(bookReviewRepository.findByBookMetadataBookId(bookId))
            .thenReturn(Collections.emptyList())
            .thenReturn(Collections.singletonList(savedEntity));
        when(appSettingService.getAppSettings()).thenReturn(appSettings);
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(bookRepository.findById(bookId)).thenReturn(Optional.of(bookEntity));
        when(mapper.toDto(savedEntity)).thenReturn(freshReview);

        Map<MetadataProvider, BookMetadata> metadataMap = new HashMap<>();
        metadataMap.put(MetadataProvider.GoodReads, BookMetadata.builder()
            .bookReviews(Collections.singletonList(freshReview))
            .build());
        when(metadataRefreshService.fetchMetadataForBook(
            eq(Collections.singletonList(MetadataProvider.GoodReads)), eq(bookEntity)))
            .thenReturn(metadataMap);

        List<BookReview> result = service.getByBookId(bookId);

        assertEquals(1, result.size());
        verify(bookReviewUpdateService).addReviewsToBook(anyList(), any());
        verify(bookRepository).save(bookEntity);
    }

    @Test
    void getByBookId_throwsException_whenBookNotFound() {
        Long bookId = 1L;
        AppSettings appSettings = new AppSettings();
        appSettings.setMetadataPublicReviewsSettings(createReviewSettings(true, MetadataProvider.Amazon));
        BookLoreUser user = createUser(true, false);

        when(bookReviewRepository.findByBookMetadataBookId(bookId))
            .thenReturn(Collections.emptyList());
        when(appSettingService.getAppSettings()).thenReturn(appSettings);
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(bookRepository.findById(bookId)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> service.getByBookId(bookId));

        assertNotNull(exception);
    }

    @Test
    void fetchBookReviews_returnsEmptyList_whenDownloadDisabled() {
        BookEntity bookEntity = new BookEntity();
        AppSettings appSettings = new AppSettings();
        appSettings.setMetadataPublicReviewsSettings(createReviewSettings(false));

        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        List<BookReview> result = service.fetchBookReviews(bookEntity);

        assertTrue(result.isEmpty());
        verify(metadataRefreshService, never()).fetchMetadataForBook(anyList(), any(BookEntity.class));
    }

    @Test
    void fetchBookReviews_filtersEnabledProviders() {
        BookEntity bookEntity = new BookEntity();
        MetadataPublicReviewsSettings settings = createReviewSettings(true,
            MetadataProvider.Amazon, MetadataProvider.GoodReads);

        // Disable GoodReads
        settings.getProviders().stream()
            .filter(config -> config.getProvider() == MetadataProvider.GoodReads)
            .findFirst()
            .ifPresent(config -> config.setEnabled(false));

        AppSettings appSettings = new AppSettings();
        appSettings.setMetadataPublicReviewsSettings(settings);

        when(appSettingService.getAppSettings()).thenReturn(appSettings);
        when(metadataRefreshService.fetchMetadataForBook(anyList(), any(BookEntity.class)))
            .thenReturn(Collections.emptyMap());

        service.fetchBookReviews(bookEntity);

        verify(metadataRefreshService).fetchMetadataForBook(
            eq(Collections.singletonList(MetadataProvider.Amazon)), eq(bookEntity));
    }

    @Test
    void fetchBookReviews_aggregatesReviewsFromMultipleProviders() {
        BookEntity bookEntity = new BookEntity();
        AppSettings appSettings = new AppSettings();
        appSettings.setMetadataPublicReviewsSettings(createReviewSettings(true,
            MetadataProvider.Amazon, MetadataProvider.GoodReads));

        BookReview amazonReview = createBookReview(MetadataProvider.Amazon);
        BookReview goodreadsReview = createBookReview(MetadataProvider.GoodReads);

        Map<MetadataProvider, BookMetadata> metadataMap = new HashMap<>();
        metadataMap.put(MetadataProvider.Amazon, BookMetadata.builder()
            .bookReviews(Collections.singletonList(amazonReview))
            .build());
        metadataMap.put(MetadataProvider.GoodReads, BookMetadata.builder()
            .bookReviews(Collections.singletonList(goodreadsReview))
            .build());

        when(appSettingService.getAppSettings()).thenReturn(appSettings);
        when(metadataRefreshService.fetchMetadataForBook(anyList(), any(BookEntity.class)))
            .thenReturn(metadataMap);

        List<BookReview> result = service.fetchBookReviews(bookEntity);

        assertEquals(2, result.size());
        assertTrue(result.contains(amazonReview));
        assertTrue(result.contains(goodreadsReview));
    }

    @Test
    void fetchBookReviews_skipsMetadataWithNullReviews() {
        BookEntity bookEntity = new BookEntity();
        AppSettings appSettings = new AppSettings();
        appSettings.setMetadataPublicReviewsSettings(createReviewSettings(true, MetadataProvider.Amazon));

        Map<MetadataProvider, BookMetadata> metadataMap = new HashMap<>();
        metadataMap.put(MetadataProvider.Amazon, BookMetadata.builder()
            .bookReviews(null)
            .build());

        when(appSettingService.getAppSettings()).thenReturn(appSettings);
        when(metadataRefreshService.fetchMetadataForBook(anyList(), any(BookEntity.class)))
            .thenReturn(metadataMap);

        List<BookReview> result = service.fetchBookReviews(bookEntity);

        assertTrue(result.isEmpty());
    }

    @Test
    void delete_removesReview_whenReviewExists() {
        Long reviewId = 1L;

        when(bookReviewRepository.existsById(reviewId)).thenReturn(true);

        service.delete(reviewId);

        verify(bookReviewRepository).deleteById(reviewId);
    }

    @Test
    void delete_throwsException_whenReviewNotFound() {
        Long reviewId = 1L;

        when(bookReviewRepository.existsById(reviewId)).thenReturn(false);

        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class,
            () -> service.delete(reviewId));

        assertEquals("Review not found: " + reviewId, exception.getMessage());
        verify(bookReviewRepository, never()).deleteById(reviewId);
    }
}
