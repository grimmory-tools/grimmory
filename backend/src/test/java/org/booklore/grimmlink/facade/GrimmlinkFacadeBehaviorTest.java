package org.booklore.grimmlink.facade;

import org.booklore.config.security.userdetails.KoreaderUserDetails;
import org.booklore.grimmlink.dto.*;
import org.booklore.grimmlink.repository.GrimmlinkMetadataItemRepository;
import org.booklore.grimmlink.service.GrimmlinkHashMatcher;
import org.booklore.exception.APIException;
import org.booklore.exception.ApiError;
import org.booklore.mapper.BookMapper;
import org.booklore.model.dto.progress.KoreaderProgress;
import org.booklore.model.dto.request.ReadingSessionRequest;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.ReadStatus;
import org.booklore.repository.*;
import org.booklore.service.book.BookDownloadService;
import org.booklore.service.koreader.KoreaderService;
import org.booklore.service.opds.MagicShelfBookService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive behavior tests for GrimmlinkFacade.
 * Covers: reading session idempotency, PDF bridge, metadata syncMode,
 * book hash matcher, progress sync, shelf cursor pagination.
 */
class GrimmlinkFacadeBehaviorTest {

    @Mock private KoreaderService koreaderService;
    @Mock private UserRepository userRepository;
    @Mock private BookRepository bookRepository;
    @Mock private BookFileRepository bookFileRepository;
    @Mock private UserBookProgressRepository userBookProgressRepository;
    @Mock private UserBookFileProgressRepository userBookFileProgressRepository;
    @Mock private ReadingSessionRepository readingSessionRepository;
    @Mock private ShelfRepository shelfRepository;
    @Mock private MagicShelfRepository magicShelfRepository;
    @Mock private BookDownloadService bookDownloadService;
    @Mock private MagicShelfBookService magicShelfBookService;
    @Mock private GrimmlinkMetadataItemRepository metadataItemRepository;
    @Mock private GrimmlinkHashMatcher hashMatcher;
    @Mock private BookMapper bookMapper;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private GrimmlinkFacade grimmlinkFacade;

    private AutoCloseable mocks;
    private BookLoreUserEntity reader;
    private BookEntity book;
    private BookFileEntity bookFile;
    private LibraryEntity library;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);

        library = new LibraryEntity();
        library.setId(11L);
        library.setFormatPriority(List.of(BookFileType.PDF));

        reader = new BookLoreUserEntity();
        reader.setId(7L);
        reader.setLibraries(Set.of(library));

        book = new BookEntity();
        book.setId(99L);
        book.setLibrary(library);

        bookFile = new BookFileEntity();
        bookFile.setId(5L);
        bookFile.setBook(book);
        bookFile.setBookType(BookFileType.PDF);
        bookFile.setCurrentHash("hash-123");
        book.setBookFiles(List.of(bookFile));

        KoreaderUserDetails principal = new KoreaderUserDetails(
                "grimmlink-user", "secret", true, true, 7L, List.of()
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );

        lenient().when(userRepository.findByIdWithDetails(7L)).thenReturn(Optional.of(reader));
        lenient().when(bookRepository.findByIdWithBookFiles(99L)).thenReturn(Optional.of(book));
    }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.clearContext();
        if (mocks != null) mocks.close();
    }

    // ──────────────────────────────────────────
    // 1) READING SESSION IDEMPOTENCY
    // ──────────────────────────────────────────

    @Test
    void recordReadingSession_single_skipsDuplicate() {
        ReadingSessionRequest request = new ReadingSessionRequest();
        request.setBookId(99L);
        request.setBookHash("hash-123");
        request.setBookType(BookFileType.PDF);
        request.setDevice("android");
        request.setDeviceId("dev-1");
        request.setStartTime(Instant.parse("2026-06-10T10:00:00Z"));
        request.setEndTime(Instant.parse("2026-06-10T10:05:00Z"));
        request.setDurationSeconds(300);

        when(readingSessionRepository.findDuplicate(
                7L, 99L, "hash-123",
                request.getStartTime(), request.getEndTime(),
                "dev-1"
        )).thenReturn(Optional.of(new ReadingSessionEntity()));

        grimmlinkFacade.recordReadingSession(request);

        verify(readingSessionRepository, never()).save(any());
    }

    @Test
    void recordReadingSession_single_savesWhenNoDuplicate() {
        ReadingSessionRequest request = new ReadingSessionRequest();
        request.setBookId(99L);
        request.setBookHash("hash-123");
        request.setBookType(BookFileType.PDF);
        request.setDevice("android");
        request.setDeviceId("dev-1");
        request.setStartTime(Instant.parse("2026-06-10T10:00:00Z"));
        request.setEndTime(Instant.parse("2026-06-10T10:05:00Z"));
        request.setDurationSeconds(300);

        when(readingSessionRepository.findDuplicate(
                7L, 99L, "hash-123",
                request.getStartTime(), request.getEndTime(),
                "dev-1"
        )).thenReturn(Optional.empty());
        when(readingSessionRepository.save(any())).thenAnswer(inv -> {
            ReadingSessionEntity s = inv.getArgument(0);
            s.setId(501L);
            return s;
        });

        grimmlinkFacade.recordReadingSession(request);

        verify(readingSessionRepository).save(any());
    }

    @Test
    void recordReadingSessionsBatch_returnsPerItemStatusWithDuplicates() {
        GrimmlinkReadingSessionItemRequest item1 = new GrimmlinkReadingSessionItemRequest();
        item1.setStartTime(Instant.parse("2026-06-10T10:00:00Z"));
        item1.setEndTime(Instant.parse("2026-06-10T10:05:00Z"));
        item1.setDurationSeconds(300);
        item1.setStartProgress(0.0f);
        item1.setEndProgress(10.0f);

        GrimmlinkReadingSessionItemRequest item2 = new GrimmlinkReadingSessionItemRequest();
        item2.setStartTime(Instant.parse("2026-06-10T11:00:00Z"));
        item2.setEndTime(Instant.parse("2026-06-10T11:10:00Z"));
        item2.setDurationSeconds(600);

        GrimmlinkReadingSessionBatchRequest batch = new GrimmlinkReadingSessionBatchRequest();
        batch.setBookId(99L);
        batch.setBookHash("hash-123");
        batch.setBookType("PDF");
        batch.setDevice("android");
        batch.setDeviceId("dev-1");
        batch.setSessions(List.of(item1, item2));

        when(readingSessionRepository.findDuplicate(
                7L, 99L, "hash-123",
                item1.getStartTime(), item1.getEndTime(), "dev-1"
        )).thenReturn(Optional.of(new ReadingSessionEntity()));

        when(readingSessionRepository.findDuplicate(
                7L, 99L, "hash-123",
                item2.getStartTime(), item2.getEndTime(), "dev-1"
        )).thenReturn(Optional.empty());

        when(readingSessionRepository.save(any())).thenAnswer(inv -> {
            ReadingSessionEntity s = inv.getArgument(0);
            s.setId(502L);
            return s;
        });

        GrimmlinkReadingSessionBatchResponse response = grimmlinkFacade.recordReadingSessionsBatch(batch);

        assertNotNull(response);
        assertEquals(2, response.getTotalRequested());
        assertEquals(2, response.getSuccessCount());
        assertEquals(2, response.getResults().size());

        assertEquals(0, response.getResults().get(0).getIndex());
        assertEquals("duplicate", response.getResults().get(0).getStatus());

        assertEquals(1, response.getResults().get(1).getIndex());
        assertEquals("created", response.getResults().get(1).getStatus());
        assertNotNull(response.getResults().get(1).getSessionId());
    }

    @Test
    void recordReadingSessionsBatch_handlesEmptyBatch() {
        GrimmlinkReadingSessionBatchRequest batch = new GrimmlinkReadingSessionBatchRequest();
        batch.setBookId(99L);
        batch.setSessions(List.of());

        GrimmlinkReadingSessionBatchResponse response = grimmlinkFacade.recordReadingSessionsBatch(batch);

        assertNotNull(response);
        assertEquals(0, response.getTotalRequested());
        assertEquals(0, response.getSuccessCount());
        assertTrue(response.getResults().isEmpty());
    }

    // ──────────────────────────────────────────
    // 2) PDF BRIDGE — FORMAT CHECK + GET PROGRESS
    // ──────────────────────────────────────────

    @Test
    void getPdfProgress_returnsProgressForPdf() {
        // getPdfProgress uses resolvePrimaryFile(book) -> book.getPrimaryBookFile() -> bookFile
        // It does NOT use bookFileRepository
        when(userBookProgressRepository.findByUserIdAndBookId(7L, 99L))
                .thenReturn(Optional.empty());

        KoreaderProgress result = grimmlinkFacade.getPdfProgress(99L);

        assertNotNull(result);
        assertEquals(99L, result.getBookId());
        assertEquals(5, result.getBookFileId().intValue());
    }

    @Test
    void getPdfProgress_returnsExistingProgress() {
        UserBookProgressEntity existing = new UserBookProgressEntity();
        existing.setId(10L);
        existing.setPdfProgressPercent(42.5f);
        existing.setPdfProgress(85); // current page
        existing.setLastReadTime(Instant.parse("2026-06-10T12:00:00Z"));

        when(userBookProgressRepository.findByUserIdAndBookId(7L, 99L))
                .thenReturn(Optional.of(existing));

        KoreaderProgress result = grimmlinkFacade.getPdfProgress(99L);

        assertNotNull(result);
        assertEquals(Float.valueOf(42.5f), result.getPercentage());
    }

    // ──────────────────────────────────────────
    // 3) METADATA SYNC MODE — push/pull/incremental dispatch
    // ──────────────────────────────────────────

    @Test
    void syncMetadataBatch_dispatchPushOnly() {
        GrimmlinkMetadataSyncRequest request = new GrimmlinkMetadataSyncRequest();
        request.setBookId(99L);
        request.setSyncMode("push");

        // syncMetadata() needs findByIdWithDetails and book lookup
        when(bookRepository.findByIdWithBookFiles(99L)).thenReturn(Optional.of(book));
        when(metadataItemRepository.findByUserIdAndBookIdAndItemTypeAndDedupeKey(
                anyLong(), anyLong(), any(), any()))
                .thenReturn(Optional.empty());

        GrimmlinkMetadataBatchResponse response = grimmlinkFacade.syncMetadataBatch(request);

        assertNotNull(response);
        assertTrue(response.isOk());
        assertNotNull(response.getPush());
        assertNotNull(response.getPull());
        assertTrue(response.getPull().getItems().isEmpty());
    }

    @Test
    void syncMetadataBatch_dispatchPullOnly() {
        GrimmlinkMetadataSyncRequest request = new GrimmlinkMetadataSyncRequest();
        request.setBookId(99L);
        request.setSyncMode("pull");

        when(metadataItemRepository.findPullItems(
                anyLong(), anyLong(), any(), any(), any(), any()))
                .thenReturn(List.of());

        GrimmlinkMetadataBatchResponse response = grimmlinkFacade.syncMetadataBatch(request);

        assertNotNull(response);
        assertTrue(response.isOk());
        assertNotNull(response.getPush());
        assertTrue(response.getPush().isOk());
        assertNotNull(response.getPull());
    }

    @Test
    void syncMetadataBatch_dispatchIncremental() {
        GrimmlinkMetadataSyncRequest request = new GrimmlinkMetadataSyncRequest();
        request.setBookId(99L);
        request.setSyncMode("incremental");

        when(bookRepository.findByIdWithBookFiles(99L)).thenReturn(Optional.of(book));
        when(metadataItemRepository.findByUserIdAndBookIdAndItemTypeAndDedupeKey(
                anyLong(), anyLong(), any(), any()))
                .thenReturn(Optional.empty());
        when(metadataItemRepository.findPullItems(
                anyLong(), anyLong(), any(), any(), any(), any()))
                .thenReturn(List.of());

        GrimmlinkMetadataBatchResponse response = grimmlinkFacade.syncMetadataBatch(request);

        assertNotNull(response);
        assertTrue(response.isOk());
        assertNotNull(response.getPush());
        assertNotNull(response.getPull());
    }

    @Test
    void syncMetadataBatch_defaultsToIncrementalWhenSyncModeNull() {
        GrimmlinkMetadataSyncRequest request = new GrimmlinkMetadataSyncRequest();
        request.setBookId(99L);
        request.setBookHash("hash-123");

        when(bookRepository.findByIdWithBookFiles(99L)).thenReturn(Optional.of(book));
        when(metadataItemRepository.findByUserIdAndBookIdAndItemTypeAndDedupeKey(
                anyLong(), anyLong(), any(), any()))
                .thenReturn(Optional.empty());
        when(metadataItemRepository.findPullItems(
                anyLong(), anyLong(), any(), any(), any(), any()))
                .thenReturn(List.of());

        GrimmlinkMetadataBatchResponse response = grimmlinkFacade.syncMetadataBatch(request);

        assertNotNull(response);
        assertTrue(response.isOk());
    }

    // ──────────────────────────────────────────
    // 4) BOOK HASH MATCHER (delegation)
    // ──────────────────────────────────────────

    @Test
    void getBookByHash_delegatesToHashMatcher() {
        when(hashMatcher.resolveAccessibleBookByHash(any(), eq("accessible-hash")))
                .thenReturn(book);
        org.booklore.model.dto.Book mapped = org.booklore.model.dto.Book.builder()
                .id(99L).title("Book Title").build();
        when(bookMapper.toBook(book)).thenReturn(mapped);

        org.booklore.model.dto.Book result = grimmlinkFacade.getBookByHash("accessible-hash");

        assertNotNull(result);
        assertEquals(99L, result.getId());
        verify(hashMatcher).resolveAccessibleBookByHash(any(), eq("accessible-hash"));
    }

    @Test
    void loadAccessibleBookByHash_usesHashMatcher() {
        when(hashMatcher.resolveAccessibleBookByHash(any(), eq("hash-xyz")))
                .thenReturn(book);
        when(userBookProgressRepository.findByUserIdAndBookId(7L, 99L))
                .thenReturn(Optional.empty());
        when(userBookProgressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        KoreaderProgress progress = KoreaderProgress.builder()
                .document("hash-xyz")
                .percentage(0.5f)
                .currentPage(10)
                .build();

        grimmlinkFacade.updateProgress(progress);

        verify(hashMatcher, times(2)).resolveAccessibleBookByHash(any(), eq("hash-xyz"));
    }

    // ──────────────────────────────────────────
    // 5) PROGRESS SYNC — NO CFI, PRESERVE MANUAL STATUS
    // ──────────────────────────────────────────

    @Test
    void updateProgress_savesDirectlyWithoutCfiGeneration() {
        when(hashMatcher.resolveAccessibleBookByHash(any(), eq("hash-123")))
                .thenReturn(book);
        when(userBookProgressRepository.findByUserIdAndBookId(7L, 99L))
                .thenReturn(Optional.empty());
        when(userBookProgressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        KoreaderProgress request = KoreaderProgress.builder()
                .document("hash-123")
                .percentage(0.5f)
                .currentPage(10)
                .progress("some-progress")
                .device("android")
                .device_id("dev-42")
                .build();

        grimmlinkFacade.updateProgress(request);

        verify(koreaderService, never()).saveProgress(anyString(), any());

        ArgumentCaptor<UserBookProgressEntity> captor = ArgumentCaptor.forClass(UserBookProgressEntity.class);
        verify(userBookProgressRepository).save(captor.capture());
        UserBookProgressEntity saved = captor.getValue();
        assertNotNull(saved);
        assertEquals("some-progress", saved.getKoreaderProgress());
        assertEquals(Float.valueOf(50.0f), saved.getKoreaderProgressPercent());
        assertEquals("android", saved.getKoreaderDevice());
        assertEquals("dev-42", saved.getKoreaderDeviceId());
    }

    @Test
    void updateProgress_preservesManualReadStatus() {
        UserBookProgressEntity existing = new UserBookProgressEntity();
        existing.setId(10L);
        existing.setReadStatus(ReadStatus.READ);
        existing.setReadStatusModifiedTime(Instant.parse("2026-06-10T12:00:00Z"));

        when(hashMatcher.resolveAccessibleBookByHash(any(), eq("hash-123")))
                .thenReturn(book);
        when(userBookProgressRepository.findByUserIdAndBookId(7L, 99L))
                .thenReturn(Optional.of(existing));
        when(userBookProgressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        KoreaderProgress request = KoreaderProgress.builder()
                .document("hash-123")
                .percentage(0.3f)
                .build();

        grimmlinkFacade.updateProgress(request);

        ArgumentCaptor<UserBookProgressEntity> captor = ArgumentCaptor.forClass(UserBookProgressEntity.class);
        verify(userBookProgressRepository).save(captor.capture());
        UserBookProgressEntity saved = captor.getValue();

        assertEquals(ReadStatus.READ, saved.getReadStatus());
    }

    @Test
    void updateProgress_derivesReadingFromPercentage() {
        when(hashMatcher.resolveAccessibleBookByHash(any(), eq("hash-123")))
                .thenReturn(book);
        when(userBookProgressRepository.findByUserIdAndBookId(7L, 99L))
                .thenReturn(Optional.empty());
        when(userBookProgressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        KoreaderProgress request = KoreaderProgress.builder()
                .document("hash-123")
                .percentage(0.5f)
                .build();

        grimmlinkFacade.updateProgress(request);

        ArgumentCaptor<UserBookProgressEntity> captor = ArgumentCaptor.forClass(UserBookProgressEntity.class);
        verify(userBookProgressRepository).save(captor.capture());
        UserBookProgressEntity saved = captor.getValue();

        assertEquals(ReadStatus.READING, saved.getReadStatus());
    }

    @Test
    void updateProgress_derivesReadFromCompletedPercentage() {
        when(hashMatcher.resolveAccessibleBookByHash(any(), eq("hash-123")))
                .thenReturn(book);
        when(userBookProgressRepository.findByUserIdAndBookId(7L, 99L))
                .thenReturn(Optional.empty());
        when(userBookProgressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        KoreaderProgress request = KoreaderProgress.builder()
                .document("hash-123")
                .percentage(0.995f) // 99.5% -> READ
                .build();

        grimmlinkFacade.updateProgress(request);

        ArgumentCaptor<UserBookProgressEntity> captor = ArgumentCaptor.forClass(UserBookProgressEntity.class);
        verify(userBookProgressRepository).save(captor.capture());
        UserBookProgressEntity saved = captor.getValue();

        assertEquals(ReadStatus.READ, saved.getReadStatus());
        assertNotNull(saved.getDateFinished());
    }

    // ──────────────────────────────────────────
    // 6) READ STATUS — UPDATE
    // ──────────────────────────────────────────

    @Test
    void updateReadStatus_updatesStatus() {
        when(userBookProgressRepository.findByUserIdAndBookId(7L, 99L))
                .thenReturn(Optional.empty());
        when(userBookProgressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        grimmlinkFacade.updateReadStatus(99L, "READING");

        ArgumentCaptor<UserBookProgressEntity> captor = ArgumentCaptor.forClass(UserBookProgressEntity.class);
        verify(userBookProgressRepository).save(captor.capture());
        UserBookProgressEntity saved = captor.getValue();
        assertEquals(ReadStatus.READING, saved.getReadStatus());
    }

    // ──────────────────────────────────────────
    // 7) GET PROGRESS with HASH MATCHER
    // ──────────────────────────────────────────

    @Test
    void getProgress_withCurrentHash_returnsProgressData() {
        when(hashMatcher.resolveAccessibleBookByHash(any(), eq("hash-abc")))
                .thenReturn(book);
        UserBookProgressEntity existing = new UserBookProgressEntity();
        existing.setId(20L);
        existing.setKoreaderProgressPercent(75.0f);
        existing.setKoreaderProgress("{\"page\":50}");
        existing.setKoreaderDevice("android");
        existing.setKoreaderDeviceId("dev-99");
        existing.setLastReadTime(Instant.parse("2026-06-11T10:00:00Z"));
        when(userBookProgressRepository.findByUserIdAndBookId(7L, 99L))
                .thenReturn(Optional.of(existing));

        KoreaderProgress result = grimmlinkFacade.getProgress("hash-abc");

        assertNotNull(result);
        assertEquals(Float.valueOf(75.0f), result.getPercentage());
        assertEquals("{\"page\":50}", result.getProgress());
        assertEquals("android", result.getDevice());
        assertEquals("dev-99", result.getDevice_id());
        assertEquals(Instant.parse("2026-06-11T10:00:00Z"), result.getUpdatedAt());
        assertEquals(Long.valueOf(99L), result.getBookId());
    }

    @Test
    void getProgress_withInitialHash_usesHashMatcher() {
        when(hashMatcher.resolveAccessibleBookByHash(any(), eq("initial-hash-xyz")))
                .thenReturn(book);
        when(userBookProgressRepository.findByUserIdAndBookId(7L, 99L))
                .thenReturn(Optional.empty());

        KoreaderProgress result = grimmlinkFacade.getProgress("initial-hash-xyz");

        assertNotNull(result);
        assertEquals(Long.valueOf(99L), result.getBookId());
        // No progress → percentage is null
        assertNull(result.getPercentage());
    }

    @Test
    void getProgress_inaccessibleHash_throwsException() {
        when(hashMatcher.resolveAccessibleBookByHash(any(), eq("restricted-hash")))
                .thenThrow(ApiError.FORBIDDEN.createException("access denied"));

        assertThrows(APIException.class,
                () -> grimmlinkFacade.getProgress("restricted-hash"));
    }

    // ──────────────────────────────────────────
    // 8) METADATA PUSH by HASH (resolveMetadataBook)
    // ──────────────────────────────────────────

    @Test
    void syncMetadata_pushByHash_currentHashResolvesViaHashMatcher() {
        GrimmlinkMetadataSyncRequest request = new GrimmlinkMetadataSyncRequest();
        request.setSyncMode("push");
        request.setBookHash("current-hash-123");

        when(hashMatcher.resolveAccessibleBookByHash(any(), eq("current-hash-123")))
                .thenReturn(book);
        when(metadataItemRepository.findByUserIdAndBookIdAndItemTypeAndDedupeKey(
                eq(7L), eq(99L), any(), any())).thenReturn(Optional.empty());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        GrimmlinkMetadataBatchResponse response = grimmlinkFacade.syncMetadataBatch(request);

        assertNotNull(response);
        assertTrue(response.isOk());
        verify(hashMatcher).resolveAccessibleBookByHash(any(), eq("current-hash-123"));
    }

    @Test
    void syncMetadata_pushByInitialHash_resolvesViaHashMatcher() {
        GrimmlinkMetadataSyncRequest request = new GrimmlinkMetadataSyncRequest();
        request.setSyncMode("push");
        request.setBookHash("initial-hash-456");

        when(hashMatcher.resolveAccessibleBookByHash(any(), eq("initial-hash-456")))
                .thenReturn(book);
        when(metadataItemRepository.findByUserIdAndBookIdAndItemTypeAndDedupeKey(
                eq(7L), eq(99L), any(), any())).thenReturn(Optional.empty());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        GrimmlinkMetadataBatchResponse response = grimmlinkFacade.syncMetadataBatch(request);

        assertNotNull(response);
        assertTrue(response.isOk());
        verify(hashMatcher).resolveAccessibleBookByHash(any(), eq("initial-hash-456"));
    }

    @Test
    void syncMetadata_pushByInaccessibleHash_returnsMetadataNotFound() {
        GrimmlinkMetadataSyncRequest request = new GrimmlinkMetadataSyncRequest();
        request.setSyncMode("push");
        request.setBookHash("forbidden-hash");

        when(hashMatcher.resolveAccessibleBookByHash(any(), eq("forbidden-hash")))
                .thenThrow(ApiError.FORBIDDEN.createException("access denied"));

        GrimmlinkMetadataBatchResponse response = grimmlinkFacade.syncMetadataBatch(request);

        // Should return metadataNotFoundResponse (not throw)
        assertNotNull(response);
    }
}
