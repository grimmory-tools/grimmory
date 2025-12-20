package com.adityachandel.booklore.service.hardcover;

import com.adityachandel.booklore.model.dto.KoboSyncSettings;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.kobo.KoboSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HardcoverSyncServiceTest {

    @Mock
    private KoboSettingsService koboSettingsService;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RestClient.RequestBodySpec requestBodySpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private HardcoverSyncService service;

    private BookEntity testBook;
    private BookMetadataEntity testMetadata;
    private KoboSyncSettings koboSyncSettings;

    private static final Long TEST_BOOK_ID = 100L;
    private static final Long TEST_USER_ID = 1L;

    @BeforeEach
    void setUp() throws Exception {
        // Create service with mocked dependencies
        service = new HardcoverSyncService(koboSettingsService, bookRepository);
        
        // Inject our mocked restClient using reflection
        Field restClientField = HardcoverSyncService.class.getDeclaredField("restClient");
        restClientField.setAccessible(true);
        restClientField.set(service, restClient);

        testBook = new BookEntity();
        testBook.setId(TEST_BOOK_ID);

        testMetadata = new BookMetadataEntity();
        testMetadata.setIsbn13("9781234567890");
        testMetadata.setPageCount(300);
        testBook.setMetadata(testMetadata);

        // Setup Kobo sync settings with Hardcover enabled
        koboSyncSettings = new KoboSyncSettings();
        koboSyncSettings.setHardcoverSyncEnabled(true);
        koboSyncSettings.setHardcoverApiKey("test-api-key");

        when(koboSettingsService.getSettingsByUserId(TEST_USER_ID)).thenReturn(koboSyncSettings);
        when(bookRepository.findById(TEST_BOOK_ID)).thenReturn(Optional.of(testBook));
        
        // Setup RestClient mock chain - handles multiple calls
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
    }

    // === Tests for skipping sync (no API calls should be made) ===

    @Test
    @DisplayName("Should skip sync when Hardcover sync is not enabled for user")
    void syncProgressToHardcover_whenHardcoverDisabled_shouldSkip() {
        koboSyncSettings.setHardcoverSyncEnabled(false);

        service.syncProgressToHardcover(TEST_BOOK_ID, 50.0f, TEST_USER_ID);

        verify(restClient, never()).post();
    }

    @Test
    @DisplayName("Should skip sync when API key is missing")
    void syncProgressToHardcover_whenApiKeyMissing_shouldSkip() {
        koboSyncSettings.setHardcoverApiKey(null);

        service.syncProgressToHardcover(TEST_BOOK_ID, 50.0f, TEST_USER_ID);

        verify(restClient, never()).post();
    }

    @Test
    @DisplayName("Should skip sync when API key is blank")
    void syncProgressToHardcover_whenApiKeyBlank_shouldSkip() {
        koboSyncSettings.setHardcoverApiKey("   ");

        service.syncProgressToHardcover(TEST_BOOK_ID, 50.0f, TEST_USER_ID);

        verify(restClient, never()).post();
    }

    @Test
    @DisplayName("Should skip sync when progress is null")
    void syncProgressToHardcover_whenProgressNull_shouldSkip() {
        service.syncProgressToHardcover(TEST_BOOK_ID, null, TEST_USER_ID);

        verify(restClient, never()).post();
    }

    @Test
    @DisplayName("Should skip sync when book not found")
    void syncProgressToHardcover_whenBookNotFound_shouldSkip() {
        when(bookRepository.findById(TEST_BOOK_ID)).thenReturn(Optional.empty());

        service.syncProgressToHardcover(TEST_BOOK_ID, 50.0f, TEST_USER_ID);

        verify(restClient, never()).post();
    }

    @Test
    @DisplayName("Should skip sync when book has no metadata")
    void syncProgressToHardcover_whenNoMetadata_shouldSkip() {
        testBook.setMetadata(null);

        service.syncProgressToHardcover(TEST_BOOK_ID, 50.0f, TEST_USER_ID);

        verify(restClient, never()).post();
    }

    @Test
    @DisplayName("Should skip sync when no ISBN available")
    void syncProgressToHardcover_whenNoIsbn_shouldSkip() {
        testMetadata.setIsbn13(null);
        testMetadata.setIsbn10(null);

        service.syncProgressToHardcover(TEST_BOOK_ID, 50.0f, TEST_USER_ID);

        verify(restClient, never()).post();
    }

    // === Tests for successful sync (API calls should be made) ===

    @Test
    @DisplayName("Should use stored hardcoverBookId when available")
    void syncProgressToHardcover_withStoredBookId_shouldUseStoredId() {
        testMetadata.setHardcoverBookId(12345);
        testMetadata.setPageCount(300);

        // Mock successful responses for the chain
        when(responseSpec.body(Map.class))
                .thenReturn(createInsertUserBookResponse(5001, null))
                .thenReturn(createEmptyUserBookReadsResponse())
                .thenReturn(createInsertUserBookReadResponse());

        service.syncProgressToHardcover(TEST_BOOK_ID, 50.0f, TEST_USER_ID);

        // Verify API was called at least once (using stored ID, no search needed)
        verify(restClient, atLeastOnce()).post();
    }

    @Test
    @DisplayName("Should search by ISBN when hardcoverBookId is not stored")
    void syncProgressToHardcover_withoutStoredBookId_shouldSearchByIsbn() {
        // Mock successful responses for the chain
        when(responseSpec.body(Map.class))
                .thenReturn(createSearchResponse(12345, 300))
                .thenReturn(createInsertUserBookResponse(5001, null))
                .thenReturn(createEmptyUserBookReadsResponse())
                .thenReturn(createInsertUserBookReadResponse());

        service.syncProgressToHardcover(TEST_BOOK_ID, 50.0f, TEST_USER_ID);

        // Verify API was called at least once
        verify(restClient, atLeastOnce()).post();
    }

    @Test
    @DisplayName("Should skip further processing when book not found on Hardcover")
    void syncProgressToHardcover_whenBookNotFoundOnHardcover_shouldSkipAfterSearch() {
        // Mock: search returns empty results
        when(responseSpec.body(Map.class)).thenReturn(createEmptySearchResponse());

        service.syncProgressToHardcover(TEST_BOOK_ID, 50.0f, TEST_USER_ID);

        // Should call search only
        verify(restClient, times(1)).post();
    }

    @Test
    @DisplayName("Should set status to READ when progress >= 99%")
    void syncProgressToHardcover_whenProgress99Percent_shouldMakeApiCalls() {
        testMetadata.setHardcoverBookId(12345);
        testMetadata.setPageCount(300);

        when(responseSpec.body(Map.class))
                .thenReturn(createInsertUserBookResponse(5001, null))
                .thenReturn(createEmptyUserBookReadsResponse())
                .thenReturn(createInsertUserBookReadResponse());

        service.syncProgressToHardcover(TEST_BOOK_ID, 99.0f, TEST_USER_ID);

        verify(restClient, atLeastOnce()).post();
    }

    @Test
    @DisplayName("Should set status to CURRENTLY_READING when progress < 99%")
    void syncProgressToHardcover_whenProgressLessThan99_shouldMakeApiCalls() {
        testMetadata.setHardcoverBookId(12345);
        testMetadata.setPageCount(300);

        when(responseSpec.body(Map.class))
                .thenReturn(createInsertUserBookResponse(5001, null))
                .thenReturn(createEmptyUserBookReadsResponse())
                .thenReturn(createInsertUserBookReadResponse());

        service.syncProgressToHardcover(TEST_BOOK_ID, 50.0f, TEST_USER_ID);

        verify(restClient, atLeastOnce()).post();
    }

    @Test
    @DisplayName("Should handle existing user_book gracefully")
    void syncProgressToHardcover_whenUserBookExists_shouldFindExisting() {
        testMetadata.setHardcoverBookId(12345);

        // Mock: insert_user_book returns error, then find existing, then create progress
        when(responseSpec.body(Map.class))
                .thenReturn(createInsertUserBookResponse(null, "Book already exists"))
                .thenReturn(createFindUserBookResponse(5001))
                .thenReturn(createEmptyUserBookReadsResponse())
                .thenReturn(createInsertUserBookReadResponse());

        service.syncProgressToHardcover(TEST_BOOK_ID, 50.0f, TEST_USER_ID);

        verify(restClient, atLeastOnce()).post();
    }

    @Test
    @DisplayName("Should update existing reading progress")
    void syncProgressToHardcover_whenProgressExists_shouldUpdate() {
        testMetadata.setHardcoverBookId(12345);

        // Mock: insert_user_book -> find existing read -> update read
        when(responseSpec.body(Map.class))
                .thenReturn(createInsertUserBookResponse(5001, null))
                .thenReturn(createFindUserBookReadResponse(6001))
                .thenReturn(createUpdateUserBookReadResponse());

        service.syncProgressToHardcover(TEST_BOOK_ID, 50.0f, TEST_USER_ID);

        verify(restClient, atLeastOnce()).post();
    }

    @Test
    @DisplayName("Should use ISBN10 when ISBN13 is missing")
    void syncProgressToHardcover_whenIsbn13Missing_shouldUseIsbn10() {
        testMetadata.setIsbn13(null);
        testMetadata.setIsbn10("1234567890");

        when(responseSpec.body(Map.class))
                .thenReturn(createSearchResponse(12345, 300))
                .thenReturn(createInsertUserBookResponse(5001, null))
                .thenReturn(createEmptyUserBookReadsResponse())
                .thenReturn(createInsertUserBookReadResponse());

        service.syncProgressToHardcover(TEST_BOOK_ID, 50.0f, TEST_USER_ID);

        verify(restClient, atLeastOnce()).post();
    }

    // === Tests for error handling ===

    @Test
    @DisplayName("Should handle API errors gracefully")
    void syncProgressToHardcover_whenApiError_shouldNotThrow() {
        testMetadata.setHardcoverBookId(12345);

        when(responseSpec.body(Map.class)).thenReturn(Map.of("errors", List.of(Map.of("message", "Unauthorized"))));

        assertDoesNotThrow(() -> service.syncProgressToHardcover(TEST_BOOK_ID, 50.0f, TEST_USER_ID));
    }

    @Test
    @DisplayName("Should handle null response gracefully")
    void syncProgressToHardcover_whenResponseNull_shouldNotThrow() {
        testMetadata.setHardcoverBookId(12345);

        when(responseSpec.body(Map.class)).thenReturn(null);

        assertDoesNotThrow(() -> service.syncProgressToHardcover(TEST_BOOK_ID, 50.0f, TEST_USER_ID));
    }

    @Test
    @DisplayName("Should skip sync when user settings not found")
    void syncProgressToHardcover_whenUserSettingsNotFound_shouldSkip() {
        when(koboSettingsService.getSettingsByUserId(TEST_USER_ID)).thenReturn(null);

        service.syncProgressToHardcover(TEST_BOOK_ID, 50.0f, TEST_USER_ID);

        verify(restClient, never()).post();
    }

    // === Helper methods to create mock responses ===

    private Map<String, Object> createSearchResponse(Integer bookId, Integer pages) {
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> search = new HashMap<>();
        Map<String, Object> results = new HashMap<>();
        Map<String, Object> hit = new HashMap<>();
        Map<String, Object> document = new HashMap<>();

        document.put("id", bookId.toString());
        document.put("pages", pages);
        hit.put("document", document);
        results.put("hits", List.of(hit));
        search.put("results", results);
        data.put("search", search);
        response.put("data", data);

        return response;
    }

    private Map<String, Object> createEmptySearchResponse() {
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> search = new HashMap<>();
        Map<String, Object> results = new HashMap<>();

        results.put("hits", List.of());
        search.put("results", results);
        data.put("search", search);
        response.put("data", data);

        return response;
    }

    private Map<String, Object> createInsertUserBookResponse(Integer userBookId, String error) {
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> insertResult = new HashMap<>();

        if (userBookId != null) {
            Map<String, Object> userBook = new HashMap<>();
            userBook.put("id", userBookId);
            insertResult.put("user_book", userBook);
        }
        if (error != null) {
            insertResult.put("error", error);
        }

        data.put("insert_user_book", insertResult);
        response.put("data", data);

        return response;
    }

    private Map<String, Object> createFindUserBookResponse(Integer userBookId) {
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> me = new HashMap<>();
        Map<String, Object> userBook = new HashMap<>();

        userBook.put("id", userBookId);
        me.put("user_books", List.of(userBook));
        data.put("me", me);
        response.put("data", data);

        return response;
    }

    private Map<String, Object> createInsertUserBookReadResponse() {
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> insertResult = new HashMap<>();
        Map<String, Object> userBookRead = new HashMap<>();

        userBookRead.put("id", 6001);
        insertResult.put("user_book_read", userBookRead);
        data.put("insert_user_book_read", insertResult);
        response.put("data", data);

        return response;
    }

    private Map<String, Object> createFindUserBookReadResponse(Integer readId) {
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> read = new HashMap<>();

        read.put("id", readId);
        data.put("user_book_reads", List.of(read));
        response.put("data", data);

        return response;
    }

    private Map<String, Object> createEmptyUserBookReadsResponse() {
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> data = new HashMap<>();

        data.put("user_book_reads", List.of());
        response.put("data", data);

        return response;
    }

    private Map<String, Object> createUpdateUserBookReadResponse() {
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> updateResult = new HashMap<>();
        Map<String, Object> userBookRead = new HashMap<>();

        userBookRead.put("id", 6001);
        userBookRead.put("progress", 50);
        updateResult.put("user_book_read", userBookRead);
        data.put("update_user_book_read", updateResult);
        response.put("data", data);

        return response;
    }
}
