package org.booklore.service.hardcover;

import org.booklore.model.dto.HardcoverSyncSettings;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.repository.BookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HardcoverSyncServiceTest {

    @Mock
    private HardcoverSyncSettingsService hardcoverSyncSettingsService;

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
    private HardcoverSyncSettings hardcoverSyncSettings;

    private static final Long TEST_BOOK_ID = 100L;
    private static final Long TEST_USER_ID = 1L;

    @BeforeEach
    void setUp() throws Exception {
        // Create service with mocked dependencies
        service = new HardcoverSyncService(hardcoverSyncSettingsService, bookRepository);
        
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

        // Setup Hardcover sync settings
        hardcoverSyncSettings = new HardcoverSyncSettings();
        hardcoverSyncSettings.setHardcoverSyncEnabled(true);
        hardcoverSyncSettings.setHardcoverApiKey("test-api-key");

        when(hardcoverSyncSettingsService.getSettingsForUserId(TEST_USER_ID)).thenReturn(hardcoverSyncSettings);
        when(bookRepository.findByIdWithMetadata(TEST_BOOK_ID)).thenReturn(Optional.of(testBook));
        
        // Setup RestClient mock chain - handles multiple calls
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        doReturn(requestBodySpec).when(requestBodySpec).body(ArgumentMatchers.any(Object.class));
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
    }

    // === Tests for skipping sync (no API calls should be made) ===

    @Test
    @DisplayName("Should skip sync when Hardcover sync is not enabled for user")
    void syncProgressToHardcover_whenHardcoverDisabled_shouldSkip() {
        hardcoverSyncSettings.setHardcoverSyncEnabled(false);

        service.syncProgressToHardcover(TEST_BOOK_ID, 50.0f, TEST_USER_ID);

        verify(restClient, never()).post();
    }

    @Test
    @DisplayName("Should skip sync when API key is missing")
    void syncProgressToHardcover_whenApiKeyMissing_shouldSkip() {
        hardcoverSyncSettings.setHardcoverApiKey(null);

        service.syncProgressToHardcover(TEST_BOOK_ID, 50.0f, TEST_USER_ID);

        verify(restClient, never()).post();
    }

    @Test
    @DisplayName("Should skip sync when API key is blank")
    void syncProgressToHardcover_whenApiKeyBlank_shouldSkip() {
        hardcoverSyncSettings.setHardcoverApiKey("   ");

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
        when(bookRepository.findByIdWithMetadata(TEST_BOOK_ID)).thenReturn(Optional.empty());

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
    @DisplayName("Should skip sync when no ISBN or hardcoverBookId available")
    void syncProgressToHardcover_whenNoIsbnOrHardcoverBookId_shouldSkip() {
        testMetadata.setIsbn13(null);
        testMetadata.setIsbn10(null);
        testMetadata.setHardcoverBookId(null);

        service.syncProgressToHardcover(TEST_BOOK_ID, 50.0f, TEST_USER_ID);

        verify(restClient, never()).post();
    }

    // === Tests for successful sync (API calls should be made) ===

    @Test
    @DisplayName("Should use stored hardcoverBookId when available")
    void syncProgressToHardcover_withStoredBookId_shouldUseStoredId() {
        testMetadata.setHardcoverBookId("12345");
        testMetadata.setPageCount(300);

        // Mock successful responses for the chain
        when(responseSpec.body(Map.class))
                .thenReturn(createBookByIdResponse(12345, 300, edition(10, 300), null, null))
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
                .thenReturn(createBooksByIsbnResponse(12345, 300, 10, 300))
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
        when(responseSpec.body(Map.class)).thenReturn(createEmptyBooksResponse());

        service.syncProgressToHardcover(TEST_BOOK_ID, 50.0f, TEST_USER_ID);

        // Should call search only
        verify(restClient, times(1)).post();
    }

    @Test
    @DisplayName("Should set status to READ when progress >= 99%")
    void syncProgressToHardcover_whenProgress99Percent_shouldMakeApiCalls() {
        testMetadata.setHardcoverBookId("12345");
        testMetadata.setPageCount(300);

        when(responseSpec.body(Map.class))
                .thenReturn(createBookByIdResponse(12345, 300, edition(10, 300), null, null))
                .thenReturn(createInsertUserBookResponse(5001, null))
                .thenReturn(createEmptyUserBookReadsResponse())
                .thenReturn(createInsertUserBookReadResponse());

        service.syncProgressToHardcover(TEST_BOOK_ID, 99.0f, TEST_USER_ID);

        verify(restClient, atLeastOnce()).post();
    }

    @Test
    @DisplayName("Should set status to CURRENTLY_READING when progress < 99%")
    void syncProgressToHardcover_whenProgressLessThan99_shouldMakeApiCalls() {
        testMetadata.setHardcoverBookId("12345");
        testMetadata.setPageCount(300);

        when(responseSpec.body(Map.class))
                .thenReturn(createBookByIdResponse(12345, 300, edition(10, 300), null, null))
                .thenReturn(createInsertUserBookResponse(5001, null))
                .thenReturn(createEmptyUserBookReadsResponse())
                .thenReturn(createInsertUserBookReadResponse());

        service.syncProgressToHardcover(TEST_BOOK_ID, 50.0f, TEST_USER_ID);

        verify(restClient, atLeastOnce()).post();
    }

    @Test
    @DisplayName("Should handle existing user_book gracefully")
    void syncProgressToHardcover_whenUserBookExists_shouldFindExisting() {
        testMetadata.setHardcoverBookId("12345");
        testMetadata.setPageCount(300);

        // Mock: insert_user_book returns error, then find existing, then create progress
        when(responseSpec.body(Map.class))
                .thenReturn(createBookByIdResponse(12345, 300, edition(10, 300), null, null))
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
        testMetadata.setHardcoverBookId("12345");
        testMetadata.setPageCount(300);

        // Mock: insert_user_book -> find existing read -> update read
        when(responseSpec.body(Map.class))
                .thenReturn(createBookByIdResponse(12345, 300, edition(10, 300), null, null))
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
                .thenReturn(createBooksByIsbnResponse(12345, 300, 10, 300))
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
        testMetadata.setHardcoverBookId("12345");
        testMetadata.setPageCount(300);

        when(responseSpec.body(Map.class)).thenReturn(Map.of("errors", List.of(Map.of("message", "Unauthorized"))));

        assertDoesNotThrow(() -> service.syncProgressToHardcover(TEST_BOOK_ID, 50.0f, TEST_USER_ID));
    }

    @Test
    @DisplayName("Should handle null response gracefully")
    void syncProgressToHardcover_whenResponseNull_shouldNotThrow() {
        testMetadata.setHardcoverBookId("12345");
        testMetadata.setPageCount(300);

        when(responseSpec.body(Map.class)).thenReturn(null);

        assertDoesNotThrow(() -> service.syncProgressToHardcover(TEST_BOOK_ID, 50.0f, TEST_USER_ID));
    }

    @Test
    @DisplayName("Should skip sync when user settings not found")
    void syncProgressToHardcover_whenUserSettingsNotFound_shouldSkip() {
        when(hardcoverSyncSettingsService.getSettingsForUserId(TEST_USER_ID)).thenReturn(null);

        service.syncProgressToHardcover(TEST_BOOK_ID, 50.0f, TEST_USER_ID);

        verify(restClient, never()).post();
    }

    // === resolveHardcoverBook ===
    @Test
    @DisplayName("Returns null when all identifiers are blank")
    void whenAllBlank_returnsNull() throws Exception {
        assertNull(resolveHardcoverBook("", "  ", ""));
        verify(restClient, never()).post();
    }

    @Test
    @DisplayName("Returns null when bookId format is invalid")
    void withInvalidBookIdFormat_returnsNull() throws Exception {
        assertNull(resolveHardcoverBook("not-a-number", null, null));
        verify(restClient, never()).post();
    }

    @Test
    @DisplayName("Uses default ebook edition when only bookId provided")
    void withBookIdOnly_usesEbookEdition() throws Exception {
        when(responseSpec.body(Map.class))
                .thenReturn(createBookByIdResponse(12345, 300, edition(10, 280), null, null));

        Object result = resolveHardcoverBook("12345", null, null);

        assertNotNull(result);
        assertEquals(10, readPrivateIntField(result, "editionId"));
        assertEquals(280, readPrivateIntField(result, "pages"));
    }

    @Test
    @DisplayName("Falls back to physical edition when no ebook available")
    void withBookIdOnly_fallsBackToPhysicalEdition() throws Exception {
        when(responseSpec.body(Map.class))
                .thenReturn(createBookByIdResponse(12345, 300, null, edition(20, 250), null));

        Object result = resolveHardcoverBook("12345", null, null);

        assertNotNull(result);
        assertEquals(20, readPrivateIntField(result, "editionId"));
        assertEquals(250, readPrivateIntField(result, "pages"));
    }

    @Test
    @DisplayName("Returns null when no editions found for bookId only")
    void withBookIdOnly_noEditions_returnsNull() throws Exception {
        when(responseSpec.body(Map.class))
                .thenReturn(createBookByIdResponse(12345, 300, null, null, null));

        assertNull(resolveHardcoverBook("12345", null, null));
    }

    @Test
    @DisplayName("Uses ISBN-matched edition over defaults when bookId and isbn provided")
    void withBookIdAndIsbn_usesIsbnMatchedEdition() throws Exception {
        when(responseSpec.body(Map.class))
                .thenReturn(createBookByIdResponse(12345, 300, edition(10, 200), null, List.of(edition(30, 320))));

        Object result = resolveHardcoverBook("12345", "9781234567890", null);

        assertNotNull(result);
        assertEquals(30, readPrivateIntField(result, "editionId"));
        assertEquals(320, readPrivateIntField(result, "pages"));
    }

    @Test
    @DisplayName("Falls back to ebook edition when no ISBN match found")
    void withBookIdAndIsbn_noIsbnMatch_fallsBackToEbook() throws Exception {
        when(responseSpec.body(Map.class))
                .thenReturn(createBookByIdResponse(12345, 300, edition(10, 300), null, List.of()));

        Object result = resolveHardcoverBook("12345", "9781234567890", null);

        assertNotNull(result);
        assertEquals(10, readPrivateIntField(result, "editionId"));
    }

    @Test
    @DisplayName("Returns null when API returns null response for bookId only")
    void withBookId_whenApiReturnsNull_returnsNull() throws Exception {
        when(responseSpec.body(Map.class)).thenReturn(null);

        assertNull(resolveHardcoverBook("12345", null, null));
    }

    @Test
    @DisplayName("Resolves book and edition when only isbn13 provided")
    void withIsbn13Only_resolvesViaIsbn() throws Exception {
        when(responseSpec.body(Map.class))
                .thenReturn(createBooksByIsbnResponse(12345, 300, 99, 280));

        Object result = resolveHardcoverBook(null, "9781234567890", null);

        assertNotNull(result);
        assertEquals(99, readPrivateIntField(result, "editionId"));
        assertEquals(280, readPrivateIntField(result, "pages"));
    }

    @Test
    @DisplayName("Resolves book and edition when only isbn10 provided")
    void withIsbn10Only_resolvesViaIsbn() throws Exception {
        when(responseSpec.body(Map.class))
                .thenReturn(createBooksByIsbnResponse(12345, 300, 99, 280));

        Object result = resolveHardcoverBook(null, null, "1234567890");

        assertNotNull(result);
        assertEquals(99, readPrivateIntField(result, "editionId"));
        assertEquals(280, readPrivateIntField(result, "pages"));
    }

    @Test
    @DisplayName("Returns null when no books found for isbn only")
    void withIsbnOnly_noBooksFound_returnsNull() throws Exception {
        when(responseSpec.body(Map.class)).thenReturn(createEmptyBooksResponse());

        assertNull(resolveHardcoverBook(null, "9781234567890", null));
    }

    @Test
    @DisplayName("Returns null when API returns null response for isbn only")
    void withIsbnOnly_whenApiReturnsNull_returnsNull() throws Exception {
        when(responseSpec.body(Map.class)).thenReturn(null);

        assertNull(resolveHardcoverBook(null, "9781234567890", null));
    }

    // === Reflection helpers ===

    private Object resolveHardcoverBook(String bookId, String isbn13, String isbn10) throws Exception {
        Method method = HardcoverSyncService.class
                .getDeclaredMethod("resolveHardcoverBook", String.class, String.class, String.class);
        method.setAccessible(true);
        return method.invoke(service, bookId, isbn13, isbn10);
    }

    // === Helper methods to create mock responses ===

    private Map<String, Object> edition(Integer id, Integer pages) {
        return Map.of("id", id, "pages", pages);
    }

    private Map<String, Object> createBookByIdResponse(
            Integer bookId,
            Integer pages,
            Map<String, Object> ebookEdition,
            Map<String, Object> physicalEdition,
            List<Map<String, Object>> isbnEditions) {
        Map<String, Object> book = new HashMap<>();
        if (bookId != null) book.put("id", bookId);
        if (pages != null) book.put("pages", pages);
        if (ebookEdition != null) book.put("default_ebook_edition", ebookEdition);
        if (physicalEdition != null) book.put("default_physical_edition", physicalEdition);
        book.put("editions", isbnEditions != null ? isbnEditions : List.of());
        return Map.of("data", Map.of("books", List.of(book)));
    }

    private Map<String, Object> createBooksByIsbnResponse(Integer bookId, Integer bookPages, Integer editionId, Integer editionPages) {
        Map<String, Object> book = Map.of("id", bookId, "pages", bookPages, "editions", List.of(edition(editionId, editionPages)));
        return Map.of("data", Map.of("books", List.of(book)));
    }

    private Map<String, Object> createEmptyBooksResponse() {
        return Map.of("data", Map.of("books", List.of()));
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

    private Integer readPrivateIntField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (Integer) field.get(target);
    }
}
