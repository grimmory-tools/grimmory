package org.booklore.service.hardcover;

import org.booklore.model.dto.BookIdentifier;
import org.booklore.model.dto.HardcoverBookProgress;
import org.booklore.model.dto.HardcoverSyncSettings;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.enums.ReadStatus;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.booklore.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.client.RestClient;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests focused specifically on the Hardcover *import* logic:
 * importHardcoverData() and the private helpers it relies on
 * (pagination, response parsing, matching, and persistence).
 * <p>
 * Persistence for the import path now goes entirely through
 * {@link UserBookProgressRepository} / {@link BookRepository} / {@link UserRepository}
 * (no more EntityManager or JdbcTemplate).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HardcoverImportServiceTest {

    @Mock
    private HardcoverSyncSettingsService hardcoverSyncSettingsService;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private UserBookProgressRepository userBookProgressRepository;
    @Mock
    private UserRepository userRepository;

    @Mock
    private RestClient restClient;
    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock
    private RestClient.RequestBodySpec requestBodySpec;
    @Mock
    private RestClient.ResponseSpec responseSpec;

    private HardcoverSyncService service;
    private HardcoverSyncSettings hardcoverSyncSettings;

    private static final Long TEST_USER_ID = 1L;

    @BeforeEach
    void setUp() {
        service = new HardcoverSyncService(hardcoverSyncSettingsService, bookRepository,
                userBookProgressRepository, restClient, userRepository);

        hardcoverSyncSettings = new HardcoverSyncSettings();
        hardcoverSyncSettings.setHardcoverSyncEnabled(true);
        hardcoverSyncSettings.setHardcoverApiKey("test-api-key");
        when(hardcoverSyncSettingsService.getSettingsForUserId(TEST_USER_ID)).thenReturn(hardcoverSyncSettings);

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        doReturn(requestBodySpec).when(requestBodySpec).body(ArgumentMatchers.any(Object.class));
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
    }

    // =========================================================================
    // importHardcoverData() orchestration
    // =========================================================================

    @Nested
    @DisplayName("importHardcoverData orchestration")
    class ImportOrchestration {

        @Test
        @DisplayName("Should not call Hardcover or persistence when sync is disabled for user")
        void whenSyncDisabled_shouldSkipEntirely() {
            hardcoverSyncSettings.setHardcoverSyncEnabled(false);

            service.importHardcoverData(TEST_USER_ID, false);

            verify(restClient, never()).post();
            verify(userBookProgressRepository, never()).saveAll(any());
            verify(userBookProgressRepository, never()).findById(any());
        }

        @Test
        @DisplayName("Should not call Hardcover when user settings are missing")
        void whenSettingsNull_shouldSkip() {
            when(hardcoverSyncSettingsService.getSettingsForUserId(TEST_USER_ID)).thenReturn(null);

            service.importHardcoverData(TEST_USER_ID, false);

            verify(restClient, never()).post();
        }

        @Test
        @DisplayName("Should throw when an import is already in progress")
        void whenAlreadyImporting_shouldThrow() throws Exception {
            setImportLock(true);
            try {
                assertThrows(RuntimeException.class, () -> service.importHardcoverData(TEST_USER_ID, false));
                verify(restClient, never()).post();
            } finally {
                setImportLock(false);
            }
        }

        @Test
        @DisplayName("Should release the lock after a successful import so a later import can proceed")
        void afterSuccess_shouldReleaseLock() throws Exception {
            when(responseSpec.body(Map.class))
                    .thenReturn(userBooksPageResponse(0, List.of()));

            service.importHardcoverData(TEST_USER_ID, false);

            assertFalse(readImportLock());
        }

        @Test
        @DisplayName("Should release the lock even when parsing throws")
        void whenParsingThrows_shouldStillReleaseLockAndNotPropagate() {
            // status_id missing causes an NPE inside parseHardcoverResponse, which
            // importHardcoverData must swallow.
            Map<String, Object> badBook = new HashMap<>();
            badBook.put("book_id", 1);
            when(responseSpec.body(Map.class))
                    .thenReturn(userBooksPageResponse(1, List.of(badBook)));

            assertDoesNotThrow(() -> service.importHardcoverData(TEST_USER_ID, false));

            verify(userBookProgressRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("Should stop early without touching persistence when Hardcover returns no data")
        void whenHardcoverReturnsNull_shouldSkipPersistence() {
            when(responseSpec.body(Map.class)).thenReturn(null);

            service.importHardcoverData(TEST_USER_ID, false);

            verify(userBookProgressRepository, never()).saveAll(any());
            verify(userBookProgressRepository, never()).findById(any());
        }

        @Test
        @DisplayName("Should always insert missing progress records, regardless of overwriteData")
        void shouldAlwaysCreateNewProgressRecords() {
            Map<String, Object> book = userBook(111, 2, 10, null, null, null);
            when(responseSpec.body(Map.class))
                    .thenReturn(userBooksPageResponse(1, List.of(book)));
            BookIdentifier identifier = mockIdentifier("111", null, null, 500, 900);
            when(userBookProgressRepository.findMissingProgressBookIdsByHardcoverId(
                    eq(TEST_USER_ID), anySet(), anySet(), anySet()))
                    .thenReturn(List.of(identifier));
            when(bookRepository.findById(500L)).thenReturn(Optional.of(mock(BookEntity.class)));

            service.importHardcoverData(TEST_USER_ID, false);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<UserBookProgressEntity>> captor = ArgumentCaptor.forClass(List.class);
            verify(userBookProgressRepository, times(1)).saveAll(captor.capture());
            assertEquals(1, captor.getValue().size());
        }

        @Test
        @DisplayName("Should skip updating existing progress when overwriteData is false")
        void whenOverwriteFalse_shouldNotUpdateExisting() {
            Map<String, Object> book = userBook(111, 2, 10, null, null, null);
            when(responseSpec.body(Map.class))
                    .thenReturn(userBooksPageResponse(1, List.of(book)));

            service.importHardcoverData(TEST_USER_ID, false);

            verify(userBookProgressRepository, never())
                    .findExistingProgressBookIdsByIdentifiers(any(), any(), any(), any());
            verify(userBookProgressRepository, never()).findById(any());
        }

        @Test
        @DisplayName("Should update existing progress when overwriteData is true")
        void whenOverwriteTrue_shouldUpdateExisting() {
            Map<String, Object> book = userBook(111, 2, 10, null, null, null);
            when(responseSpec.body(Map.class))
                    .thenReturn(userBooksPageResponse(1, List.of(book)));
            BookIdentifier existing = mockIdentifier("111", null, null, 500, 900);
            when(userBookProgressRepository.findExistingProgressBookIdsByIdentifiers(
                    eq(TEST_USER_ID), anySet(), anySet(), anySet()))
                    .thenReturn(List.of(existing));
            UserBookProgressEntity entity = new UserBookProgressEntity();
            when(userBookProgressRepository.findById(900L)).thenReturn(Optional.of(entity));

            service.importHardcoverData(TEST_USER_ID, true);

            verify(userBookProgressRepository, times(1)).save(entity);
            assertEquals(ReadStatus.READING, entity.getReadStatus());
        }
    }

    // =========================================================================
    // getUserBooksFromHardcover() pagination
    // =========================================================================

    @Nested
    @DisplayName("getUserBooksFromHardcover pagination")
    class Pagination {

        @Test
        @DisplayName("Should return all books across multiple pages")
        void multiPage_shouldMergeAllResults() throws Exception {
            Map<String, Object> b1 = userBook(1, 2, 10, null, null, null);
            Map<String, Object> b2 = userBook(2, 2, 11, null, null, null);
            Map<String, Object> b3 = userBook(3, 2, 12, null, null, null);

            when(responseSpec.body(Map.class))
                    .thenReturn(userBooksPageResponse(3, List.of(b1, b2)))
                    .thenReturn(userBooksPageResponse(3, List.of(b3)));

            @SuppressWarnings("unchecked")
            List<Map> result = (List<Map>) invokePrivate("getUserBooksFromHardcover");

            assertNotNull(result);
            assertEquals(3, result.size());
            verify(restClient, times(2)).post();
        }

        @Test
        @DisplayName("Should stop after a single page when all books are returned at once")
        void singlePage_shouldNotRequestAgain() throws Exception {
            Map<String, Object> b1 = userBook(1, 2, 10, null, null, null);
            when(responseSpec.body(Map.class))
                    .thenReturn(userBooksPageResponse(1, List.of(b1)));

            @SuppressWarnings("unchecked")
            List<Map> result = (List<Map>) invokePrivate("getUserBooksFromHardcover");

            assertEquals(1, result.size());
            verify(restClient, times(1)).post();
        }

        @Test
        @DisplayName("Should return null when the API response is null")
        void nullResponse_returnsNull() throws Exception {
            when(responseSpec.body(Map.class)).thenReturn(null);

            assertNull(invokePrivate("getUserBooksFromHardcover"));
        }

        @Test
        @DisplayName("Should return null when 'me' is missing from the response")
        void missingMe_returnsNull() throws Exception {
            when(responseSpec.body(Map.class)).thenReturn(Map.of("data", Map.of()));

            assertNull(invokePrivate("getUserBooksFromHardcover"));
        }

        @Test
        @DisplayName("Should return null when user_books is empty")
        void emptyUserBooks_returnsNull() throws Exception {
            when(responseSpec.body(Map.class)).thenReturn(userBooksPageResponse(0, List.of()));

            assertNull(invokePrivate("getUserBooksFromHardcover"));
        }

        @Test
        @DisplayName("Should return null when the aggregate count is missing")
        void missingAggregateCount_returnsNull() throws Exception {
            Map<String, Object> me = new HashMap<>();
            me.put("user_books", new ArrayList<>(List.of(userBook(1, 2, 10, null, null, null))));
            me.put("user_books_aggregate", Map.of("aggregate", new HashMap<String, Object>()));
            Map<String, Object> data = new HashMap<>();
            data.put("me", new ArrayList<>(List.of(me)));
            when(responseSpec.body(Map.class)).thenReturn(Map.of("data", data));

            assertNull(invokePrivate("getUserBooksFromHardcover"));
        }
    }

    // =========================================================================
    // parseHardcoverResponse()
    // =========================================================================

    @Nested
    @DisplayName("parseHardcoverResponse")
    class ResponseParsing {

        @Test
        @DisplayName("Should map every known status_id to the matching ReadStatus")
        void shouldMapAllStatuses() throws Exception {
            List<Map> books = List.of(
                    userBook(1, 1, null, null, null, null),
                    userBook(2, 2, null, null, null, null),
                    userBook(3, 3, null, null, null, null),
                    userBook(4, 4, null, null, null, null),
                    userBook(5, 5, null, null, null, null),
                    userBook(6, 6, null, null, null, null)
            );

            List<HardcoverBookProgress> parsed = parse(books);

            assertEquals(ReadStatus.UNREAD, parsed.get(0).getStatus());
            assertEquals(ReadStatus.READING, parsed.get(1).getStatus());
            assertEquals(ReadStatus.READ, parsed.get(2).getStatus());
            assertEquals(ReadStatus.PAUSED, parsed.get(3).getStatus());
            assertEquals(ReadStatus.ABANDONED, parsed.get(4).getStatus());
            assertEquals(ReadStatus.WONT_READ, parsed.get(5).getStatus());
        }

        @Test
        @DisplayName("Should default to UNSET for an unrecognized status_id")
        void unknownStatus_shouldDefaultToUnset() throws Exception {
            List<HardcoverBookProgress> parsed = parse(List.of(userBook(1, 99, null, null, null, null)));

            assertEquals(ReadStatus.UNSET, parsed.get(0).getStatus());
        }

        @Test
        @DisplayName("Should scale a 5-point Hardcover rating to Booklore's 10-point scale")
        void shouldScaleRating() throws Exception {
            List<HardcoverBookProgress> parsed = parse(List.of(userBook(1, 2, null, 4.5, null, null)));

            assertEquals(9, parsed.get(0).getRating());
        }

        @Test
        @DisplayName("Should leave rating null when Hardcover does not provide one")
        void missingRating_shouldStayNull() throws Exception {
            List<HardcoverBookProgress> parsed = parse(List.of(userBook(1, 2, null, null, null, null)));

            assertNull(parsed.get(0).getRating());
        }

        @Test
        @DisplayName("Should parse last_read_date into a Date")
        void shouldParseLastReadDate() throws Exception {
            List<HardcoverBookProgress> parsed = parse(List.of(userBook(1, 2, null, null, "2024-3-15", null)));

            assertNotNull(parsed.get(0).getLastReadDate());
        }

        @Test
        @DisplayName("Should leave last_read_date null when absent")
        void missingLastReadDate_shouldStayNull() throws Exception {
            List<HardcoverBookProgress> parsed = parse(List.of(userBook(1, 2, null, null, null, null)));

            assertNull(parsed.get(0).getLastReadDate());
        }

        @Test
        @DisplayName("Should throw when an unparseable last_read_date is provided")
        void malformedLastReadDate_shouldThrow() {
            List<Map> books = List.of(userBook(1, 2, null, null, "not-a-date", null));

            assertThrows(Exception.class, () -> parse(books));
        }

        @Test
        @DisplayName("Should collect ISBN-10 and ISBN-13 from every edition into the lookup maps")
        void shouldCollectIsbnsFromEditions() throws Exception {
            List<Map<String, Object>> editions = List.of(
                    editionIsbns("1111111111", "1111111111111"),
                    editionIsbns("2222222222", null),
                    editionIsbns(null, "3333333333333")
            );
            List<Map> books = List.of(userBook(1, 2, null, null, null, editions));

            Map<String, HardcoverBookProgress> isbn10 = new HashMap<>();
            Map<String, HardcoverBookProgress> isbn13 = new HashMap<>();
            Map<String, HardcoverBookProgress> hcIds = new HashMap<>();
            List<HardcoverBookProgress> parsed = parse(books, isbn10, isbn13, hcIds);

            assertTrue(isbn10.containsKey("1111111111"));
            assertTrue(isbn10.containsKey("2222222222"));
            assertFalse(isbn10.containsKey("3333333333333"));
            assertTrue(isbn13.containsKey("1111111111111"));
            assertTrue(isbn13.containsKey("3333333333333"));
            assertSame(parsed.get(0), isbn10.get("1111111111"));
            assertSame(parsed.get(0), isbn13.get("1111111111111"));
        }

        @Test
        @DisplayName("Should register every parsed book under its Hardcover id")
        void shouldRegisterHardcoverIds() throws Exception {
            Map<String, HardcoverBookProgress> isbn10 = new HashMap<>();
            Map<String, HardcoverBookProgress> isbn13 = new HashMap<>();
            Map<String, HardcoverBookProgress> hcIds = new HashMap<>();
            List<HardcoverBookProgress> parsed = parse(
                    List.of(userBook(42, 2, null, null, null, null)), isbn10, isbn13, hcIds);

            assertSame(parsed.get(0), hcIds.get("42"));
        }

        @Test
        @DisplayName("Should not fail and leave ISBN maps empty when the 'book' field is absent")
        void missingBookField_shouldNotPopulateIsbnMaps() throws Exception {
            Map<String, HardcoverBookProgress> isbn10 = new HashMap<>();
            Map<String, HardcoverBookProgress> isbn13 = new HashMap<>();
            Map<String, HardcoverBookProgress> hcIds = new HashMap<>();

            assertDoesNotThrow(() -> parse(
                    List.of(userBook(1, 2, null, null, null, null)), isbn10, isbn13, hcIds));
            assertTrue(isbn10.isEmpty());
            assertTrue(isbn13.isEmpty());
        }

        @Test
        @DisplayName("Should capture edition_id when present")
        void shouldCaptureEditionId() throws Exception {
            List<HardcoverBookProgress> parsed = parse(List.of(userBook(1, 2, 77, null, null, null)));

            assertEquals(77, parsed.get(0).getEditionId());
        }

        private List<HardcoverBookProgress> parse(List<Map> books) throws Exception {
            return parse(books, new HashMap<>(), new HashMap<>(), new HashMap<>());
        }

        @SuppressWarnings("unchecked")
        private List<HardcoverBookProgress> parse(List<Map> books,
                                                    Map<String, HardcoverBookProgress> isbn10,
                                                    Map<String, HardcoverBookProgress> isbn13,
                                                    Map<String, HardcoverBookProgress> hcIds) throws Exception {
            Method m = HardcoverSyncService.class.getDeclaredMethod("parseHardcoverResponse",
                    List.class, Map.class, Map.class, Map.class);
            m.setAccessible(true);
            try {
                return (List<HardcoverBookProgress>) m.invoke(service, books, isbn10, isbn13, hcIds);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof Exception ex) throw ex;
                throw e;
            }
        }
    }

    // =========================================================================
    // getHardcoverBook() matching priority
    // =========================================================================

    @Nested
    @DisplayName("getHardcoverBook matching priority")
    class HardcoverBookMatching {

        @Test
        @DisplayName("Should prefer a Hardcover-id match over an ISBN match")
        void shouldPreferHardcoverIdMatch() throws Exception {
            HardcoverBookProgress byId = new HardcoverBookProgress();
            HardcoverBookProgress byIsbn = new HardcoverBookProgress();
            Map<String, HardcoverBookProgress> isbn10 = Map.of("111", byIsbn);
            Map<String, HardcoverBookProgress> isbn13 = Map.of();
            Map<String, HardcoverBookProgress> hcIds = Map.of("999", byId);

            BookIdentifier identifier = mockIdentifier("999", "111", null, 1, 1);

            Object result = invokePrivate("getHardcoverBook",
                    new Class<?>[]{Map.class, Map.class, Map.class, BookIdentifier.class},
                    isbn10, isbn13, hcIds, identifier);

            assertSame(byId, result);
        }

        @Test
        @DisplayName("Should fall back to ISBN-10 when there is no Hardcover-id match")
        void shouldFallBackToIsbn10() throws Exception {
            HardcoverBookProgress byIsbn10 = new HardcoverBookProgress();
            Map<String, HardcoverBookProgress> isbn10 = Map.of("111", byIsbn10);
            Map<String, HardcoverBookProgress> isbn13 = Map.of();
            Map<String, HardcoverBookProgress> hcIds = Map.of();

            BookIdentifier identifier = mockIdentifier("999", "111", null, 1, 1);

            Object result = invokePrivate("getHardcoverBook",
                    new Class<?>[]{Map.class, Map.class, Map.class, BookIdentifier.class},
                    isbn10, isbn13, hcIds, identifier);

            assertSame(byIsbn10, result);
        }

        @Test
        @DisplayName("Should fall back to ISBN-13 when there is no Hardcover-id or ISBN-10 match")
        void shouldFallBackToIsbn13() throws Exception {
            HardcoverBookProgress byIsbn13 = new HardcoverBookProgress();
            Map<String, HardcoverBookProgress> isbn10 = Map.of();
            Map<String, HardcoverBookProgress> isbn13 = Map.of("2222222222222", byIsbn13);
            Map<String, HardcoverBookProgress> hcIds = Map.of();

            BookIdentifier identifier = mockIdentifier("999", "111", "2222222222222", 1, 1);

            Object result = invokePrivate("getHardcoverBook",
                    new Class<?>[]{Map.class, Map.class, Map.class, BookIdentifier.class},
                    isbn10, isbn13, hcIds, identifier);

            assertSame(byIsbn13, result);
        }

        @Test
        @DisplayName("Should return null when nothing matches")
        void shouldReturnNullWhenNoMatch() throws Exception {
            BookIdentifier identifier = mockIdentifier("999", "111", "222", 1, 1);

            Object result = invokePrivate("getHardcoverBook",
                    new Class<?>[]{Map.class, Map.class, Map.class, BookIdentifier.class},
                    Map.of(), Map.of(), Map.of(), identifier);

            assertNull(result);
        }
    }

    // =========================================================================
    // createNewProgressRecords()
    // =========================================================================

    @Nested
    @DisplayName("createNewProgressRecords")
    class NewProgressRecords {

        @Test
        @DisplayName("Should not touch bookRepository or save anything when there are no missing-progress books")
        void noMissingBooks_shouldSkipInsert() throws Exception {
            when(userBookProgressRepository.findMissingProgressBookIdsByHardcoverId(
                    eq(TEST_USER_ID), anySet(), anySet(), anySet())).thenReturn(List.of());

            invokePrivate("createNewProgressRecords",
                    new Class<?>[]{Long.class, Map.class, Map.class, Map.class, ArrayList.class},
                    TEST_USER_ID, new HashMap<>(), new HashMap<>(), new HashMap<>(), new ArrayList<HardcoverBookProgress>());

            verify(bookRepository, never()).findById(any());
            verify(userBookProgressRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("Should build a new progress entity with the book, status, rating and dates from Hardcover")
        void shouldBuildEntityWithMappedFields() throws Exception {
            HardcoverBookProgress book = new HardcoverBookProgress();
            book.setHardcoverId("111");
            book.setStatus(ReadStatus.READING);
            book.setRating(8);
            book.setLastReadDate(new java.util.Date(1_700_000_000_000L));

            Map<String, HardcoverBookProgress> hcIds = Map.of("111", book);
            BookIdentifier identifier = mockIdentifier("111", null, null, 42, 1);
            when(userBookProgressRepository.findMissingProgressBookIdsByHardcoverId(
                    eq(TEST_USER_ID), anySet(), anySet(), anySet())).thenReturn(List.of(identifier));
            BookEntity bookEntity = mock(BookEntity.class);
            when(bookRepository.findById(42L)).thenReturn(Optional.of(bookEntity));

            invokePrivate("createNewProgressRecords",
                    new Class<?>[]{Long.class, Map.class, Map.class, Map.class, ArrayList.class},
                    TEST_USER_ID, new HashMap<>(), new HashMap<>(), hcIds, new ArrayList<>(List.of(book)));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<UserBookProgressEntity>> captor = ArgumentCaptor.forClass(List.class);
            verify(userBookProgressRepository).saveAll(captor.capture());
            assertEquals(1, captor.getValue().size());
            UserBookProgressEntity saved = captor.getValue().get(0);
            assertSame(bookEntity, saved.getBook());
            assertEquals(ReadStatus.READING, saved.getReadStatus());
            assertEquals(8, saved.getPersonalRating());
            assertNotNull(saved.getLastReadTime());
            assertNotNull(saved.getDateFinished());
        }

        @Test
        @DisplayName("Should leave rating and dates null when both are absent from Hardcover")
        void shouldLeaveRatingAndDateNullWhenAbsent() throws Exception {
            HardcoverBookProgress book = new HardcoverBookProgress();
            book.setHardcoverId("111");
            book.setStatus(ReadStatus.UNREAD);

            Map<String, HardcoverBookProgress> hcIds = Map.of("111", book);
            BookIdentifier identifier = mockIdentifier("111", null, null, 42, 1);
            when(userBookProgressRepository.findMissingProgressBookIdsByHardcoverId(
                    eq(TEST_USER_ID), anySet(), anySet(), anySet())).thenReturn(List.of(identifier));
            when(bookRepository.findById(42L)).thenReturn(Optional.of(mock(BookEntity.class)));

            invokePrivate("createNewProgressRecords",
                    new Class<?>[]{Long.class, Map.class, Map.class, Map.class, ArrayList.class},
                    TEST_USER_ID, new HashMap<>(), new HashMap<>(), hcIds, new ArrayList<>(List.of(book)));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<UserBookProgressEntity>> captor = ArgumentCaptor.forClass(List.class);
            verify(userBookProgressRepository).saveAll(captor.capture());
            UserBookProgressEntity saved = captor.getValue().get(0);
            assertNull(saved.getPersonalRating());
            assertNull(saved.getLastReadTime());
            assertNull(saved.getDateFinished());
        }

        @Test
        @DisplayName("Should exclude a candidate row that cannot be matched to any Hardcover book")
        void unmatchedCandidate_shouldBeExcludedFromBatch() throws Exception {
            BookIdentifier identifier = mockIdentifier("does-not-exist", null, null, 42, 1);
            when(userBookProgressRepository.findMissingProgressBookIdsByHardcoverId(
                    eq(TEST_USER_ID), anySet(), anySet(), anySet())).thenReturn(List.of(identifier));

            invokePrivate("createNewProgressRecords",
                    new Class<?>[]{Long.class, Map.class, Map.class, Map.class, ArrayList.class},
                    TEST_USER_ID, new HashMap<>(), new HashMap<>(), new HashMap<>(), new ArrayList<HardcoverBookProgress>());

            verify(bookRepository, never()).findById(any());
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<UserBookProgressEntity>> captor = ArgumentCaptor.forClass(List.class);
            verify(userBookProgressRepository).saveAll(captor.capture());
            assertTrue(captor.getValue().isEmpty());
        }

        @Test
        @DisplayName("Should propagate when the matched book cannot be found in the local repository")
        void bookNotFoundLocally_shouldThrow() throws Exception {
            HardcoverBookProgress book = new HardcoverBookProgress();
            book.setHardcoverId("111");
            book.setStatus(ReadStatus.UNREAD);

            Map<String, HardcoverBookProgress> hcIds = Map.of("111", book);
            BookIdentifier identifier = mockIdentifier("111", null, null, 42, 1);
            when(userBookProgressRepository.findMissingProgressBookIdsByHardcoverId(
                    eq(TEST_USER_ID), anySet(), anySet(), anySet())).thenReturn(List.of(identifier));
            when(bookRepository.findById(42L)).thenReturn(Optional.empty());

            assertThrows(NoSuchElementException.class, () -> invokePrivate("createNewProgressRecords",
                    new Class<?>[]{Long.class, Map.class, Map.class, Map.class, ArrayList.class},
                    TEST_USER_ID, new HashMap<>(), new HashMap<>(), hcIds, new ArrayList<>(List.of(book))));
        }
    }

    // =========================================================================
    // updateExistingProgress()
    // =========================================================================

    @Nested
    @DisplayName("updateExistingProgress")
    class ExistingProgressUpdate {

        @Test
        @DisplayName("Should not touch the repository when there are no existing-progress books")
        void noExistingBooks_shouldSkipUpdate() throws Exception {
            when(userBookProgressRepository.findExistingProgressBookIdsByIdentifiers(
                    eq(TEST_USER_ID), anySet(), anySet(), anySet())).thenReturn(List.of());

            invokePrivate("updateExistingProgress",
                    new Class<?>[]{Long.class, Map.class, Map.class, Map.class, ArrayList.class},
                    TEST_USER_ID, new HashMap<>(), new HashMap<>(), new HashMap<>(), new ArrayList<HardcoverBookProgress>());

            verify(userBookProgressRepository, never()).findById(any());
            verify(userBookProgressRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should update status, rating and dates from the matched Hardcover book")
        void shouldUpdateMatchedEntity() throws Exception {
            HardcoverBookProgress book = new HardcoverBookProgress();
            book.setHardcoverId("111");
            book.setStatus(ReadStatus.READ);
            book.setRating(10);
            book.setLastReadDate(new java.util.Date(1_700_000_000_000L));

            Map<String, HardcoverBookProgress> hcIds = Map.of("111", book);
            BookIdentifier identifier = mockIdentifier("111", null, null, 42, 900);
            when(userBookProgressRepository.findExistingProgressBookIdsByIdentifiers(
                    eq(TEST_USER_ID), anySet(), anySet(), anySet())).thenReturn(List.of(identifier));

            UserBookProgressEntity entity = new UserBookProgressEntity();
            when(userBookProgressRepository.findById(900L)).thenReturn(Optional.of(entity));

            invokePrivate("updateExistingProgress",
                    new Class<?>[]{Long.class, Map.class, Map.class, Map.class, ArrayList.class},
                    TEST_USER_ID, new HashMap<>(), new HashMap<>(), hcIds, new ArrayList<>(List.of(book)));

            assertEquals(ReadStatus.READ, entity.getReadStatus());
            assertEquals(10, entity.getPersonalRating());
            assertNotNull(entity.getLastReadTime());
            assertNotNull(entity.getDateFinished());
            verify(userBookProgressRepository).save(entity);
        }

        @Test
        @DisplayName("Should skip a candidate that cannot be matched to any Hardcover book")
        void unmatchedCandidate_shouldBeSkipped() throws Exception {
            BookIdentifier identifier = mockIdentifier("does-not-exist", null, null, 42, 900);
            when(userBookProgressRepository.findExistingProgressBookIdsByIdentifiers(
                    eq(TEST_USER_ID), anySet(), anySet(), anySet())).thenReturn(List.of(identifier));

            invokePrivate("updateExistingProgress",
                    new Class<?>[]{Long.class, Map.class, Map.class, Map.class, ArrayList.class},
                    TEST_USER_ID, new HashMap<>(), new HashMap<>(), new HashMap<>(), new ArrayList<HardcoverBookProgress>());

            verify(userBookProgressRepository, never()).findById(any());
            verify(userBookProgressRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should clear last-read and finished dates when Hardcover has no last_read_date")
        void noLastReadDate_shouldClearDates() throws Exception {
            HardcoverBookProgress book = new HardcoverBookProgress();
            book.setHardcoverId("111");
            book.setStatus(ReadStatus.READING);

            Map<String, HardcoverBookProgress> hcIds = Map.of("111", book);
            BookIdentifier identifier = mockIdentifier("111", null, null, 42, 900);
            when(userBookProgressRepository.findExistingProgressBookIdsByIdentifiers(
                    eq(TEST_USER_ID), anySet(), anySet(), anySet())).thenReturn(List.of(identifier));

            UserBookProgressEntity entity = new UserBookProgressEntity();
            entity.setLastReadTime(java.time.Instant.now());
            when(userBookProgressRepository.findById(900L)).thenReturn(Optional.of(entity));

            invokePrivate("updateExistingProgress",
                    new Class<?>[]{Long.class, Map.class, Map.class, Map.class, ArrayList.class},
                    TEST_USER_ID, new HashMap<>(), new HashMap<>(), hcIds, new ArrayList<>(List.of(book)));

            assertNull(entity.getLastReadTime());
            assertNull(entity.getDateFinished());
        }

        @Test
        @DisplayName("Should propagate when the matched progress record cannot be found in the repository")
        void progressNotFoundLocally_shouldThrow() throws Exception {
            HardcoverBookProgress book = new HardcoverBookProgress();
            book.setHardcoverId("111");
            book.setStatus(ReadStatus.READING);

            Map<String, HardcoverBookProgress> hcIds = Map.of("111", book);
            BookIdentifier identifier = mockIdentifier("111", null, null, 42, 900);
            when(userBookProgressRepository.findExistingProgressBookIdsByIdentifiers(
                    eq(TEST_USER_ID), anySet(), anySet(), anySet())).thenReturn(List.of(identifier));
            when(userBookProgressRepository.findById(900L)).thenReturn(Optional.empty());

            assertThrows(NoSuchElementException.class, () -> invokePrivate("updateExistingProgress",
                    new Class<?>[]{Long.class, Map.class, Map.class, Map.class, ArrayList.class},
                    TEST_USER_ID, new HashMap<>(), new HashMap<>(), hcIds, new ArrayList<>(List.of(book))));
        }
    }

    // =========================================================================
    // Reflection & fixture helpers
    // =========================================================================

    private Object invokePrivate(String methodName, Class<?>[] paramTypes, Object... args) throws Exception {
        Method m = HardcoverSyncService.class.getDeclaredMethod(methodName, paramTypes);
        m.setAccessible(true);
        try {
            return m.invoke(service, args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception ex) throw ex;
            throw e;
        }
    }

    private Object invokePrivate(String methodName) throws Exception {
        return invokePrivate(methodName, new Class<?>[0]);
    }

    private void setImportLock(boolean locked) throws Exception {
        var lockField = HardcoverSyncService.class.getDeclaredField("hardcoverImportLock");
        lockField.setAccessible(true);
        ((java.util.concurrent.atomic.AtomicBoolean) lockField.get(service)).set(locked);
    }

    private boolean readImportLock() throws Exception {
        var lockField = HardcoverSyncService.class.getDeclaredField("hardcoverImportLock");
        lockField.setAccessible(true);
        return ((java.util.concurrent.atomic.AtomicBoolean) lockField.get(service)).get();
    }

    /** Mocks a BookIdentifier since it's an interface with no public implementation in test scope. */
    private BookIdentifier mockIdentifier(String hardcoverBookId, String isbn10, String isbn13,
                                           Integer bookId, Integer progressId) {
        BookIdentifier identifier = mock(BookIdentifier.class);
        doReturn(hardcoverBookId).when(identifier).getHardcoverBookId();
        doReturn(isbn10).when(identifier).getIsbn10();
        doReturn(isbn13).when(identifier).getIsbn13();
        doReturn(bookId).when(identifier).getBookId();
        doReturn(progressId).when(identifier).getProgressId();
        return identifier;
    }

    private Map<String, Object> userBook(Integer bookId, Integer statusId, Integer editionId,
                                          Double rating, String lastReadDate,
                                          List<Map<String, Object>> editions) {
        Map<String, Object> book = new HashMap<>();
        book.put("book_id", bookId);
        book.put("status_id", statusId);
        if (editionId != null) book.put("edition_id", editionId);
        if (rating != null) book.put("rating", rating);
        if (lastReadDate != null) book.put("last_read_date", lastReadDate);
        if (editions != null) {
            book.put("book", Map.of("editions", editions));
        }
        return book;
    }

    private Map<String, Object> editionIsbns(String isbn10, String isbn13) {
        Map<String, Object> edition = new HashMap<>();
        if (isbn10 != null) edition.put("isbn_10", isbn10);
        if (isbn13 != null) edition.put("isbn_13", isbn13);
        return edition;
    }

    private Map<String, Object> userBooksPageResponse(int aggregateCount, List<Map<String, Object>> page) {
        Map<String, Object> me = new HashMap<>();
        // HardcoverSyncService casts these to ArrayList explicitly, so an immutable
        // List.of(...)/List.copyOf(...) here would blow up with a ClassCastException.
        me.put("user_books", new ArrayList<>(page));
        me.put("user_books_aggregate", Map.of("aggregate", Map.of("count", aggregateCount)));
        Map<String, Object> data = new HashMap<>();
        data.put("me", new ArrayList<>(List.of(me)));
        return Map.of("data", data);
    }
}