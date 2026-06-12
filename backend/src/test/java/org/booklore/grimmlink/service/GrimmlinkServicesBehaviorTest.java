package org.booklore.grimmlink.service;

import org.booklore.config.security.userdetails.KoreaderUserDetails;
import org.booklore.grimmlink.dto.*;
import org.booklore.grimmlink.model.GrimmlinkMetadataItemEntity;
import org.booklore.grimmlink.model.GrimmlinkMetadataItemType;
import org.booklore.grimmlink.repository.GrimmlinkMetadataItemRepository;
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
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive behavior tests for the focused Grimmlink services.
 * Covers: reading session idempotency, PDF bridge, metadata syncMode,
 * book hash matcher, progress sync, shelf cursor pagination.
 */
class GrimmlinkServicesBehaviorTest {

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

    private GrimmlinkBookService bookService;
    private GrimmlinkProgressService progressService;
    private GrimmlinkPdfBridgeService pdfBridgeService;
    private GrimmlinkReadingSessionService readingSessionService;
    private GrimmlinkMetadataService metadataService;
    private GrimmlinkShelfService shelfService;

    private AutoCloseable mocks;
    private BookLoreUserEntity reader;
    private BookEntity book;
    private BookFileEntity bookFile;
    private LibraryEntity library;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        GrimmlinkAuthService authService = new GrimmlinkAuthService(koreaderService, userRepository);
        bookService = new GrimmlinkBookService(
                authService,
                hashMatcher,
                bookRepository,
                userBookProgressRepository,
                bookMapper,
                bookDownloadService);
        progressService = new GrimmlinkProgressService(
                authService,
                bookService,
                hashMatcher,
                bookFileRepository,
                userBookProgressRepository,
                userBookFileProgressRepository);
        pdfBridgeService = new GrimmlinkPdfBridgeService(
                authService,
                bookService,
                bookFileRepository,
                userBookProgressRepository,
                userBookFileProgressRepository);
        readingSessionService = new GrimmlinkReadingSessionService(
                authService,
                bookService,
                readingSessionRepository);
        metadataService = new GrimmlinkMetadataService(
                authService,
                bookService,
                hashMatcher,
                bookRepository,
                bookFileRepository,
                metadataItemRepository,
                objectMapper);
        shelfService = new GrimmlinkShelfService(
                authService,
                bookService,
                bookRepository,
                shelfRepository,
                magicShelfRepository,
                magicShelfBookService);

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

        readingSessionService.recordReadingSession(request);

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

        readingSessionService.recordReadingSession(request);

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

        GrimmlinkReadingSessionBatchResponse response =
                readingSessionService.recordReadingSessionsBatch(batch);

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

        GrimmlinkReadingSessionBatchResponse response =
                readingSessionService.recordReadingSessionsBatch(batch);

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

        KoreaderProgress result = pdfBridgeService.getPdfProgress(99L);

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

        KoreaderProgress result = pdfBridgeService.getPdfProgress(99L);

        assertNotNull(result);
        assertEquals(Float.valueOf(42.5f), result.getPercentage());
    }

    @Test
    void updatePdfProgress_detectsConflictWithoutForce() {
        UserBookProgressEntity existing = new UserBookProgressEntity();
        existing.setId(10L);
        existing.setLastReadTime(Instant.parse("2026-06-10T12:00:00Z"));
        when(userBookProgressRepository.findByUserIdAndBookId(7L, 99L))
                .thenReturn(Optional.of(existing));

        KoreaderProgress request = KoreaderProgress.builder()
                .currentPage(20)
                .expectedUpdatedAt(Instant.parse("2026-06-10T11:00:00Z").toEpochMilli())
                .build();

        KoreaderProgress result = pdfBridgeService.updatePdfProgress(99L, request);

        assertTrue(result.getConflictDetected());
        assertFalse(result.getUpdated());
        assertEquals("remote_newer", result.getConversionStatus());
        verify(userBookProgressRepository, never()).save(any());
    }

    @Test
    void updatePdfProgress_forceOverwritesConflict() {
        UserBookProgressEntity existing = new UserBookProgressEntity();
        existing.setId(10L);
        existing.setLastReadTime(Instant.parse("2026-06-10T12:00:00Z"));
        when(userBookProgressRepository.findByUserIdAndBookId(7L, 99L))
                .thenReturn(Optional.of(existing));
        when(userBookProgressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userBookFileProgressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        KoreaderProgress request = KoreaderProgress.builder()
                .currentPage(20)
                .percentage(0.25f)
                .expectedUpdatedAt(Instant.parse("2026-06-10T11:00:00Z").toEpochMilli())
                .force(true)
                .build();

        KoreaderProgress result = pdfBridgeService.updatePdfProgress(99L, request);

        assertFalse(result.getConflictDetected());
        assertTrue(result.getUpdated());
        assertEquals("ok", result.getConversionStatus());
        assertEquals(Integer.valueOf(20), existing.getPdfProgress());
        assertEquals(Float.valueOf(25.0f), existing.getPdfProgressPercent());
        verify(userBookProgressRepository).save(existing);
        verify(userBookFileProgressRepository).save(any());
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

        GrimmlinkMetadataBatchResponse response = metadataService.syncMetadataBatch(request);

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

        GrimmlinkMetadataBatchResponse response = metadataService.syncMetadataBatch(request);

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

        GrimmlinkMetadataBatchResponse response = metadataService.syncMetadataBatch(request);

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

        GrimmlinkMetadataBatchResponse response = metadataService.syncMetadataBatch(request);

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

        org.booklore.model.dto.Book result = bookService.getBookByHash("accessible-hash");

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

        progressService.updateProgress(progress);

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

        progressService.updateProgress(request);

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
                .updatedAt(Instant.parse("2026-06-10T11:00:00Z"))
                .build();

        progressService.updateProgress(request);

        ArgumentCaptor<UserBookProgressEntity> captor = ArgumentCaptor.forClass(UserBookProgressEntity.class);
        verify(userBookProgressRepository).save(captor.capture());
        UserBookProgressEntity saved = captor.getValue();

        assertEquals(ReadStatus.READ, saved.getReadStatus());
    }

    @Test
    void downloadBook_checksAccessAndReturnsBinaryResponse() {
        Resource resource = mock(Resource.class);
        ResponseEntity<Resource> expected = ResponseEntity.ok(resource);
        when(bookDownloadService.downloadBook(99L)).thenReturn(expected);

        ResponseEntity<Resource> result = bookService.downloadBook(99L);

        assertSame(expected, result);
        verify(bookDownloadService).downloadBook(99L);
    }

    @Test
    void removeBookFromRegularShelf_onlyRemovesMembership() {
        ShelfEntity shelf = new ShelfEntity();
        shelf.setId(3L);
        shelf.setUser(reader);
        book.setShelves(new java.util.HashSet<>(Set.of(shelf)));
        when(shelfRepository.findByIdWithUser(3L)).thenReturn(Optional.of(shelf));

        GrimmlinkShelfRemovalResponse response =
                shelfService.removeBookFromShelf("regular", 3L, 99L);

        assertTrue(response.isRemoved());
        assertTrue(book.getShelves().isEmpty());
        verify(bookRepository).save(book);
        verifyNoInteractions(bookDownloadService);
    }

    @Test
    void removeBookFromMagicShelf_isUnsupported() {
        GrimmlinkShelfRemovalResponse response =
                shelfService.removeBookFromShelf("magic", 3L, 99L);

        assertFalse(response.isRemoved());
        assertEquals("unsupported", response.getStatus());
        verifyNoInteractions(shelfRepository);
        verify(bookRepository, never()).save(any());
    }

    @Test
    void updateProgress_replacesOlderManualReadStatus() {
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
                .updatedAt(Instant.parse("2026-06-10T13:00:00Z"))
                .build();

        progressService.updateProgress(request);

        ArgumentCaptor<UserBookProgressEntity> captor = ArgumentCaptor.forClass(UserBookProgressEntity.class);
        verify(userBookProgressRepository).save(captor.capture());
        assertEquals(ReadStatus.READING, captor.getValue().getReadStatus());
    }

    @Test
    void updateProgress_usesPageRatioForDisplayWithoutDerivingReadStatus() {
        when(hashMatcher.resolveAccessibleBookByHash(any(), eq("hash-123")))
                .thenReturn(book);
        when(userBookProgressRepository.findByUserIdAndBookId(7L, 99L))
                .thenReturn(Optional.empty());
        when(userBookProgressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        KoreaderProgress request = KoreaderProgress.builder()
                .document("hash-123")
                .currentPage(25)
                .totalPages(100)
                .updatedAt(Instant.parse("2026-06-10T13:00:00Z"))
                .build();

        progressService.updateProgress(request);

        ArgumentCaptor<UserBookProgressEntity> captor = ArgumentCaptor.forClass(UserBookProgressEntity.class);
        verify(userBookProgressRepository).save(captor.capture());
        UserBookProgressEntity saved = captor.getValue();
        assertEquals(Float.valueOf(25.0f), saved.getKoreaderProgressPercent());
        assertNull(saved.getReadStatus());

        ArgumentCaptor<UserBookFileProgressEntity> fileCaptor =
                ArgumentCaptor.forClass(UserBookFileProgressEntity.class);
        verify(userBookFileProgressRepository).save(fileCaptor.capture());
        assertEquals(Float.valueOf(25.0f), fileCaptor.getValue().getProgressPercent());
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

        progressService.updateProgress(request);

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

        progressService.updateProgress(request);

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

        bookService.updateReadStatus(99L, "READING");

        ArgumentCaptor<UserBookProgressEntity> captor = ArgumentCaptor.forClass(UserBookProgressEntity.class);
        verify(userBookProgressRepository).save(captor.capture());
        UserBookProgressEntity saved = captor.getValue();
        assertEquals(ReadStatus.READING, saved.getReadStatus());
    }

    // ──────────────────────────────────────────
    // 7) GET PROGRESS with HASH MATCHER
    // ──────────────────────────────────────────

    @Test
    void getProgress_withKoreaderLastSyncTime_returnsTimestampAndProgressData() {
        when(hashMatcher.resolveAccessibleBookByHash(any(), eq("hash-abc")))
                .thenReturn(book);
        UserBookProgressEntity existing = new UserBookProgressEntity();
        existing.setId(20L);
        existing.setKoreaderProgressPercent(75.0f);
        existing.setKoreaderProgress("{\"page\":50}");
        existing.setKoreaderDevice("android");
        existing.setKoreaderDeviceId("dev-99");
        existing.setKoreaderLastSyncTime(Instant.parse("2026-06-11T10:05:00Z"));
        existing.setLastReadTime(Instant.parse("2026-06-11T10:00:00Z"));
        when(userBookProgressRepository.findByUserIdAndBookId(7L, 99L))
                .thenReturn(Optional.of(existing));

        KoreaderProgress result = progressService.getProgress("hash-abc");

        assertNotNull(result);
        assertEquals(Float.valueOf(75.0f), result.getPercentage());
        assertEquals("{\"page\":50}", result.getProgress());
        assertEquals("android", result.getDevice());
        assertEquals("dev-99", result.getDevice_id());
        assertEquals(Long.valueOf(1781172300L), result.getTimestamp());
        assertEquals(Instant.parse("2026-06-11T10:05:00Z"), result.getUpdatedAt());
        assertEquals(Long.valueOf(99L), result.getBookId());
    }

    @Test
    void getProgress_withoutKoreaderLastSyncTime_fallsBackToLastReadTime() {
        when(hashMatcher.resolveAccessibleBookByHash(any(), eq("hash-abc")))
                .thenReturn(book);
        UserBookProgressEntity existing = new UserBookProgressEntity();
        existing.setLastReadTime(Instant.parse("2026-06-11T10:00:00Z"));
        when(userBookProgressRepository.findByUserIdAndBookId(7L, 99L))
                .thenReturn(Optional.of(existing));

        KoreaderProgress result = progressService.getProgress("hash-abc");

        assertEquals(Long.valueOf(1781172000L), result.getTimestamp());
        assertEquals(Instant.parse("2026-06-11T10:00:00Z"), result.getUpdatedAt());
    }

    @Test
    void getProgress_withInitialHash_usesHashMatcher() {
        when(hashMatcher.resolveAccessibleBookByHash(any(), eq("initial-hash-xyz")))
                .thenReturn(book);
        when(userBookProgressRepository.findByUserIdAndBookId(7L, 99L))
                .thenReturn(Optional.empty());

        KoreaderProgress result = progressService.getProgress("initial-hash-xyz");

        assertNotNull(result);
        assertEquals(Long.valueOf(99L), result.getBookId());
        // No progress → percentage is null
        assertNull(result.getPercentage());
        verify(hashMatcher).resolveAccessibleBookByHash(reader, "initial-hash-xyz");
    }

    @Test
    void getProgress_inaccessibleHash_throwsException() {
        when(hashMatcher.resolveAccessibleBookByHash(any(), eq("restricted-hash")))
                .thenThrow(ApiError.FORBIDDEN.createException("access denied"));

        assertThrows(APIException.class,
                () -> progressService.getProgress("restricted-hash"));
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

        GrimmlinkMetadataBatchResponse response = metadataService.syncMetadataBatch(request);

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

        GrimmlinkMetadataBatchResponse response = metadataService.syncMetadataBatch(request);

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

        GrimmlinkMetadataBatchResponse response = metadataService.syncMetadataBatch(request);

        // Should return metadataNotFoundResponse (not throw)
        assertNotNull(response);
    }

    @Test
    void syncMetadata_preservesPayloadShapeAndDeviceProvenance() throws Exception {
        GrimmlinkRatingPayload rating = new GrimmlinkRatingPayload();
        rating.setDedupeKey("rating-1");
        rating.setValue(4);

        GrimmlinkAnnotationPayload annotation = new GrimmlinkAnnotationPayload();
        annotation.setDedupeKey("annotation-1");
        annotation.setText("highlight");

        GrimmlinkBookmarkPayload bookmark = new GrimmlinkBookmarkPayload();
        bookmark.setDedupeKey("bookmark-1");
        bookmark.setTitle("chapter");

        GrimmlinkMetadataSyncRequest request = new GrimmlinkMetadataSyncRequest();
        request.setBookId(99L);
        request.setDevice("KOReader");
        request.setDeviceId("device-42");
        request.setRating(rating);
        request.setAnnotations(List.of(annotation));
        request.setBookmarks(List.of(bookmark));

        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(metadataItemRepository.findByUserIdAndBookIdAndItemTypeAndDedupeKey(
                anyLong(), anyLong(), any(), anyString()))
                .thenReturn(Optional.empty());
        when(metadataItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GrimmlinkMetadataSyncResponse response = metadataService.syncMetadata(request);

        assertTrue(response.isOk());
        verify(objectMapper).writeValueAsString(rating);
        verify(objectMapper).writeValueAsString(annotation);
        verify(objectMapper).writeValueAsString(bookmark);
        verify(objectMapper, never()).writeValueAsString(request);

        ArgumentCaptor<GrimmlinkMetadataItemEntity> captor =
                ArgumentCaptor.forClass(GrimmlinkMetadataItemEntity.class);
        verify(metadataItemRepository, times(3)).save(captor.capture());
        assertEquals(
                Set.of(
                        GrimmlinkMetadataItemType.RATING,
                        GrimmlinkMetadataItemType.ANNOTATION,
                        GrimmlinkMetadataItemType.BOOKMARK),
                captor.getAllValues().stream()
                        .map(GrimmlinkMetadataItemEntity::getItemType)
                        .collect(java.util.stream.Collectors.toSet()));
        assertTrue(captor.getAllValues().stream()
                .allMatch(item -> "KOReader".equals(item.getDevice())
                        && "device-42".equals(item.getDeviceId())));
    }
}
