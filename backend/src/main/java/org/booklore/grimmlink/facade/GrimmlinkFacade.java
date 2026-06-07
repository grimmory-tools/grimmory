package org.booklore.grimmlink.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.config.security.userdetails.KoreaderUserDetails;
import org.booklore.exception.ApiError;
import org.booklore.grimmlink.dto.*;
import org.booklore.grimmlink.model.GrimmlinkMetadataItemEntity;
import org.booklore.grimmlink.model.GrimmlinkMetadataItemType;
import org.booklore.grimmlink.repository.GrimmlinkMetadataItemRepository;
import org.booklore.mapper.BookMapper;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.progress.KoreaderProgress;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.ReadStatus;
import org.booklore.repository.*;
import org.booklore.service.book.BookDownloadService;
import org.booklore.service.koreader.KoreaderService;
import org.booklore.service.opds.MagicShelfBookService;
import org.springframework.data.domain.PageRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GrimmlinkFacade {

    private static final Set<ReadStatus> SUPPORTED_MANUAL_READ_STATUSES = EnumSet.of(
            ReadStatus.UNREAD,
            ReadStatus.READING,
            ReadStatus.READ,
            ReadStatus.PAUSED,
            ReadStatus.ABANDONED,
            ReadStatus.RE_READING
    );

    private final KoreaderService koreaderService;
    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final BookFileRepository bookFileRepository;
    private final UserBookProgressRepository userBookProgressRepository;
    private final UserBookFileProgressRepository userBookFileProgressRepository;
    private final ReadingSessionRepository readingSessionRepository;
    private final ShelfRepository shelfRepository;
    private final MagicShelfRepository magicShelfRepository;
    private final BookDownloadService bookDownloadService;
    private final MagicShelfBookService magicShelfBookService;
    private final GrimmlinkMetadataItemRepository metadataItemRepository;
    private final BookMapper bookMapper;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Map<String, Object> authorize() {
        Map<String, String> upstreamResponse = koreaderService.authorizeUser().getBody();
        BookLoreUserEntity reader = requireCurrentReaderEntity(false);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("username", upstreamResponse != null ? upstreamResponse.getOrDefault("username", reader.getUsername()) : reader.getUsername());
        response.put("userId", reader.getId());
        if (reader.getKoreaderUser() != null) {
            response.put("syncEnabled", reader.getKoreaderUser().isSyncEnabled());
            response.put("syncWithWebReader", reader.getKoreaderUser().isSyncWithWebReader());
        }
        return response;
    }

    @Transactional(readOnly = true)
    public Book getBookByHash(String bookHash) {
        BookLoreUserEntity reader = requireCurrentReaderEntity(true);
        BookEntity book = loadAccessibleBookByHash(reader, bookHash);
        return bookMapper.toBook(book);
    }

    @Transactional(readOnly = true)
    public KoreaderProgress getProgress(String bookHash) {
        return koreaderService.getProgress(bookHash);
    }

    @Transactional
    public void updateProgress(KoreaderProgress request) {
        String bookHash = firstNonBlank(request.resolveBookHash(), request.getDocument(), request.getCurrentHash(), request.getInitialHash());
        if (bookHash == null) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("bookHash or document is required");
        }
        koreaderService.saveProgress(bookHash, request);
        if (request.getCurrentPage() != null || request.getTotalPages() != null || request.getLocation() != null) {
            persistFileProgressIfPossible(bookHash, request);
        }
    }

    @Transactional(readOnly = true)
    public List<String> getSupportedReadStatuses() {
        return SUPPORTED_MANUAL_READ_STATUSES.stream().map(Enum::name).toList();
    }

    @Transactional
    public Map<String, Object> updateReadStatus(Long bookId, String requestedStatus) {
        BookLoreUserEntity reader = requireCurrentReaderEntity(true);
        BookEntity book = loadAccessibleBookById(reader, bookId);
        ReadStatus readStatus = normalizeReadStatus(requestedStatus);
        UserBookProgressEntity progress = userBookProgressRepository.findByUserIdAndBookId(reader.getId(), book.getId())
                .orElseGet(UserBookProgressEntity::new);
        progress.setUser(reader);
        progress.setBook(book);
        progress.setReadStatus(readStatus);
        progress.setReadStatusModifiedTime(Instant.now());
        if (readStatus == ReadStatus.READ) {
            progress.setDateFinished(Instant.now());
        }
        userBookProgressRepository.save(progress);
        return Map.of("bookId", book.getId(), "status", readStatus.name(), "updated", true);
    }

    @Transactional(readOnly = true)
    public KoreaderProgress getPdfProgress(Long bookId) {
        BookLoreUserEntity reader = requireCurrentReaderEntity(true);
        BookEntity book = loadAccessibleBookById(reader, bookId);
        UserBookProgressEntity progress = userBookProgressRepository.findByUserIdAndBookId(reader.getId(), book.getId()).orElse(null);
        BookFileEntity file = resolvePrimaryFile(book);
        return KoreaderProgress.builder()
                .bookId(book.getId())
                .bookFileId(file != null ? file.getId() : null)
                .bookHash(resolveHash(file))
                .document(resolveHash(file))
                .fileFormat(file != null && file.getBookType() != null ? file.getBookType().name() : null)
                .currentPage(progress != null ? progress.getPdfProgress() : null)
                .percentage(progress != null ? progress.getPdfProgressPercent() : null)
                .progress(progress != null && progress.getPdfProgress() != null ? String.valueOf(progress.getPdfProgress()) : null)
                .updatedAt(progress != null ? progress.getLastReadTime() : null)
                .device("BookLore")
                .device_id("BookLore")
                .build();
    }

    @Transactional
    public KoreaderProgress updatePdfProgress(Long bookId, KoreaderProgress request) {
        BookLoreUserEntity reader = requireCurrentReaderEntity(true);
        BookEntity book = loadAccessibleBookById(reader, bookId);
        BookFileEntity file = resolveRequestedFile(book, request);
        UserBookProgressEntity progress = userBookProgressRepository.findByUserIdAndBookId(reader.getId(), book.getId())
                .orElseGet(UserBookProgressEntity::new);
        progress.setUser(reader);
        progress.setBook(book);
        progress.setPdfProgress(request.getCurrentPage());
        progress.setPdfProgressPercent(normalizePercent(request.getPercentage()));
        progress.setKoreaderProgress(firstNonBlank(request.getRawKoreaderProgress(), request.getProgress()));
        progress.setKoreaderProgressPercent(request.getPercentage());
        progress.setKoreaderDevice(request.getDevice());
        progress.setKoreaderDeviceId(request.getDeviceId());
        progress.setKoreaderLastSyncTime(Instant.now());
        progress.setLastReadTime(resolveClientTime(request));
        userBookProgressRepository.save(progress);

        if (file != null) {
            UserBookFileProgressEntity fileProgress = userBookFileProgressRepository.findByUserIdAndBookFileId(reader.getId(), file.getId())
                    .orElseGet(UserBookFileProgressEntity::new);
            fileProgress.setUser(reader);
            fileProgress.setBookFile(file);
            fileProgress.setPositionData(request.getCurrentPage() != null ? String.valueOf(request.getCurrentPage()) : request.getProgress());
            fileProgress.setPositionHref(firstNonBlank(request.getRawKoreaderLocation(), request.getLocation()));
            fileProgress.setProgressPercent(normalizePercent(request.getPercentage()));
            fileProgress.setLastReadTime(progress.getLastReadTime());
            userBookFileProgressRepository.save(fileProgress);
        }
        return getPdfProgress(bookId);
    }

    @Transactional(readOnly = true)
    public List<GrimmlinkShelfSummary> listShelves(String typeFilter) {
        BookLoreUserEntity reader = requireCurrentReaderEntity(true);
        String normalizedType = normalizeShelfTypeOrNull(typeFilter);
        List<GrimmlinkShelfSummary> summaries = new ArrayList<>();
        if (normalizedType == null || "regular".equals(normalizedType)) {
            summaries.addAll(shelfRepository.findByUserIdOrPublicShelfTrue(reader.getId()).stream()
                    .filter(shelf -> canReadShelf(reader, shelf))
                    .map(shelf -> GrimmlinkShelfSummary.builder()
                            .id(shelf.getId())
                            .name(shelf.getName())
                            .type("regular")
                            .visibility(shelf.isPublic() ? "public" : "personal")
                            .bookCount(shelf.getBookCount())
                            .build())
                    .toList());
        }
        if (normalizedType == null || "magic".equals(normalizedType)) {
            Map<Long, MagicShelfEntity> shelvesById = new LinkedHashMap<>();
            magicShelfRepository.findAllByUserId(reader.getId()).forEach(shelf -> shelvesById.put(shelf.getId(), shelf));
            magicShelfRepository.findAllByIsPublicIsTrue().forEach(shelf -> shelvesById.putIfAbsent(shelf.getId(), shelf));
            summaries.addAll(shelvesById.values().stream()
                    .filter(shelf -> canReadMagicShelf(reader, shelf))
                    .map(shelf -> GrimmlinkShelfSummary.builder()
                            .id(shelf.getId())
                            .name(shelf.getName())
                            .type("magic")
                            .visibility(shelf.isPublic() ? "public" : "personal")
                            .bookCount(countMagicShelfBooks(reader, shelf.getId()))
                            .description("Rule-based Magic Shelf")
                            .build())
                    .toList());
        }
        return summaries;
    }

    @Transactional(readOnly = true)
    public List<GrimmlinkBookSummary> listShelfBooks(String shelfType, Long shelfId) {
        BookLoreUserEntity reader = requireCurrentReaderEntity(true);
        if ("magic".equals(normalizeShelfType(shelfType))) {
            List<Long> bookIds = magicShelfBookService.getBookIdsByMagicShelfId(reader.getId(), shelfId);
            if (bookIds == null || bookIds.isEmpty()) {
                return List.of();
            }
            List<BookEntity> books = bookRepository.findAllForSummaryByIds(bookIds.stream().distinct().toList());
            Map<Long, BookEntity> byId = books.stream().collect(Collectors.toMap(BookEntity::getId, book -> book, (left, right) -> left));
            return bookIds.stream().distinct()
                    .map(byId::get)
                    .filter(Objects::nonNull)
                    .filter(book -> canAccessBook(reader, book))
                    .map(this::toBookSummary)
                    .toList();
        }

        ShelfEntity shelf = shelfRepository.findByIdWithUser(shelfId)
                .orElseThrow(() -> ApiError.SHELF_NOT_FOUND.createException(shelfId));
        if (!canReadShelf(reader, shelf)) {
            throw ApiError.FORBIDDEN.createException("Shelf is not accessible to the authenticated user");
        }
        return bookRepository.findAllWithMetadataByShelfId(shelfId).stream()
                .filter(book -> canAccessBook(reader, book))
                .map(this::toBookSummary)
                .toList();
    }

    public ResponseEntity<Resource> downloadBook(Long bookId) {
        BookLoreUserEntity reader = requireCurrentReaderEntity(true);
        loadAccessibleBookById(reader, bookId);
        return bookDownloadService.downloadBook(bookId);
    }

    @Transactional
    public GrimmlinkShelfRemovalResponse removeBookFromShelf(String shelfType, Long shelfId, Long bookId) {
        BookLoreUserEntity reader = requireCurrentReaderEntity(true);
        String normalizedType = normalizeShelfType(shelfType);
        if ("magic".equals(normalizedType)) {
            return GrimmlinkShelfRemovalResponse.builder()
                    .shelfId(shelfId)
                    .bookId(bookId)
                    .shelfType("magic")
                    .removed(false)
                    .status("unsupported")
                    .message("Magic Shelf is rule-based and cannot be manually removed from")
                    .build();
        }
        ShelfEntity shelf = shelfRepository.findByIdWithUser(shelfId)
                .orElseThrow(() -> ApiError.SHELF_NOT_FOUND.createException(shelfId));
        if (!canModifyShelf(reader, shelf)) {
            throw ApiError.FORBIDDEN.createException("Shelf membership can only be modified by the shelf owner or an admin");
        }
        BookEntity book = loadAccessibleBookById(reader, bookId);
        boolean removed = book.getShelves().removeIf(existingShelf -> existingShelf.getId().equals(shelfId));
        if (removed) {
            bookRepository.save(book);
        }
        return GrimmlinkShelfRemovalResponse.builder()
                .shelfId(shelfId)
                .bookId(bookId)
                .shelfType("regular")
                .removed(removed)
                .status(removed ? "removed" : "noop")
                .message(removed ? "Shelf membership removed" : "Book is not currently in this shelf")
                .build();
    }

    @Transactional
    public GrimmlinkMetadataSyncResponse syncMetadata(GrimmlinkMetadataSyncRequest request) {
        BookLoreUserEntity reader = requireCurrentReaderEntity(true);
        GrimmlinkMetadataSyncRequest normalized = request == null ? new GrimmlinkMetadataSyncRequest() : request;
        if (normalized.getAnnotations() == null) {
            normalized.setAnnotations(new ArrayList<>());
        }
        if (normalized.getBookmarks() == null) {
            normalized.setBookmarks(new ArrayList<>());
        }
        Optional<BookEntity> bookOpt = resolveMetadataBook(reader, normalized);
        if (bookOpt.isEmpty()) {
            return metadataNotFoundResponse(normalized);
        }
        BookEntity book = bookOpt.get();
        BookFileEntity bookFile = resolveBookFile(book, normalized.getBookFileId());
        GrimmlinkItemResult rating = normalized.getRating() != null
                ? upsertMetadataItem(reader, book, bookFile, GrimmlinkMetadataItemType.RATING, normalized.getRating().getDedupeKey(), normalized.getRating().getUpdatedAt(), normalized)
                : null;
        List<GrimmlinkItemResult> annotations = normalized.getAnnotations().stream()
                .map(item -> upsertMetadataItem(reader, book, bookFile, GrimmlinkMetadataItemType.ANNOTATION, item.getDedupeKey(), item.getUpdatedAt(), item))
                .toList();
        List<GrimmlinkItemResult> bookmarks = normalized.getBookmarks().stream()
                .map(item -> upsertMetadataItem(reader, book, bookFile, GrimmlinkMetadataItemType.BOOKMARK, item.getDedupeKey(), item.getUpdatedAt(), item))
                .toList();
        return GrimmlinkMetadataSyncResponse.builder()
                .bookId(book.getId())
                .ok(StreamSafe.allSuccess(rating, annotations, bookmarks))
                .results(GrimmlinkMetadataSyncResults.builder()
                        .rating(rating)
                        .annotations(annotations)
                        .bookmarks(bookmarks)
                        .build())
                .build();
    }

    @Transactional(readOnly = true)
    public GrimmlinkMetadataPullResponse pullMetadata(Long bookId, String bookHash, Long bookFileId, Instant since, Integer limit, String type) {
        BookLoreUserEntity reader = requireCurrentReaderEntity(true);
        BookEntity book = resolvePullBook(reader, bookId, bookHash, bookFileId);
        BookFileEntity bookFile = bookFileId != null ? resolveBookFile(book, bookFileId) : null;
        GrimmlinkMetadataItemType itemType = normalizeMetadataType(type);
        int normalizedLimit = normalizeLimit(limit);
        List<GrimmlinkMetadataPullItem> items = metadataItemRepository
                .findPullItems(reader.getId(), book.getId(), bookFile != null ? bookFile.getId() : null, itemType, since, PageRequest.of(0, normalizedLimit))
                .stream()
                .map(this::toPullItem)
                .toList();
        Instant nextCursor = items.isEmpty() ? since : items.get(items.size() - 1).getUpdatedAt();
        return GrimmlinkMetadataPullResponse.builder()
                .bookId(book.getId())
                .bookFileId(bookFile != null ? bookFile.getId() : null)
                .ok(true)
                .since(since)
                .nextCursor(nextCursor)
                .limit(normalizedLimit)
                .items(items)
                .build();
    }

    @Transactional
    public GrimmlinkMetadataBatchResponse syncMetadataBatch(GrimmlinkMetadataSyncRequest request) {
        GrimmlinkMetadataSyncRequest normalized = request == null ? new GrimmlinkMetadataSyncRequest() : request;
        GrimmlinkMetadataSyncResponse push = syncMetadata(normalized);
        GrimmlinkMetadataPullResponse pull = push.isOk()
                ? pullMetadata(normalized.getBookId(), normalized.getBookHash(), normalized.getBookFileId(), normalized.getSince(), normalized.getLimit(), normalized.getType())
                : GrimmlinkMetadataPullResponse.builder()
                .bookId(push.getBookId())
                .ok(false)
                .since(normalized.getSince())
                .limit(normalizeLimit(normalized.getLimit()))
                .items(List.of())
                .build();
        return GrimmlinkMetadataBatchResponse.builder()
                .ok(push.isOk() && pull.isOk())
                .push(push)
                .pull(pull)
                .build();
    }

    @Transactional
    public void recordReadingSession(org.booklore.model.dto.request.ReadingSessionRequest request) {
        BookLoreUserEntity reader = requireCurrentReaderEntity(true);
        BookEntity book = loadAccessibleBookById(reader, request.getBookId());
        ReadingSessionEntity session = readingSessionRepository.save(buildSession(reader, book, request, null));
        log.info(
                "Grimmlink reading session persisted successfully: sessionId={}, userId={}, bookId={}, bookHash={}, bookType={}, duration={}s, device={}, deviceId={}",
                session.getId(),
                reader.getId(),
                book.getId(),
                session.getBookHash(),
                session.getBookType(),
                session.getDurationSeconds(),
                session.getDevice(),
                session.getDeviceId()
        );
    }

    @Transactional
    public GrimmlinkReadingSessionBatchResponse recordReadingSessionsBatch(GrimmlinkReadingSessionBatchRequest request) {
        BookLoreUserEntity reader = requireCurrentReaderEntity(true);
        BookEntity book = loadAccessibleBookById(reader, request.getBookId());
        List<GrimmlinkReadingSessionBatchResponse.SessionResult> results = request.getSessions().stream()
                .map(item -> {
                    ReadingSessionEntity session = readingSessionRepository.save(buildSession(reader, book, request, item));
                    return GrimmlinkReadingSessionBatchResponse.SessionResult.builder()
                            .sessionId(session.getId())
                            .startTime(session.getStartTime())
                            .endTime(session.getEndTime())
                            .build();
                })
                .toList();
        log.info(
                "Grimmlink reading session batch persisted successfully: userId={}, bookId={}, bookHash={}, bookType={}, requested={}, saved={}, device={}, deviceId={}",
                reader.getId(),
                book.getId(),
                resolveRequestBookHash(request.getBookHash(), book),
                resolveBookType(request.getBookType(), book),
                request.getSessions().size(),
                results.size(),
                trimToNull(request.getDevice()),
                trimToNull(request.getDeviceId())
        );
        return GrimmlinkReadingSessionBatchResponse.builder()
                .totalRequested(request.getSessions().size())
                .successCount(results.size())
                .results(results)
                .build();
    }

    private ReadingSessionEntity buildSession(BookLoreUserEntity reader, BookEntity book, org.booklore.model.dto.request.ReadingSessionRequest request, GrimmlinkReadingSessionItemRequest ignored) {
        return ReadingSessionEntity.builder()
                .user(reader)
                .book(book)
                .bookType(request.getBookType() != null ? request.getBookType() : resolveBookType(book))
                .bookHash(resolveRequestBookHash(request.getBookHash(), book))
                .device(trimToNull(request.getDevice()))
                .deviceId(trimToNull(request.getDeviceId()))
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .durationSeconds(request.getDurationSeconds())
                .durationFormatted(request.getDurationFormatted())
                .startProgress(request.getStartProgress())
                .endProgress(request.getEndProgress())
                .progressDelta(request.getProgressDelta())
                .startLocation(request.getStartLocation())
                .endLocation(request.getEndLocation())
                .currentPage(request.getCurrentPage())
                .totalPages(request.getTotalPages())
                .build();
    }

    private ReadingSessionEntity buildSession(BookLoreUserEntity reader, BookEntity book, GrimmlinkReadingSessionBatchRequest request, GrimmlinkReadingSessionItemRequest item) {
        return ReadingSessionEntity.builder()
                .user(reader)
                .book(book)
                .bookType(resolveBookType(request.getBookType(), book))
                .bookHash(resolveRequestBookHash(request.getBookHash(), book))
                .device(trimToNull(request.getDevice()))
                .deviceId(trimToNull(request.getDeviceId()))
                .startTime(item.getStartTime())
                .endTime(item.getEndTime())
                .durationSeconds(item.getDurationSeconds())
                .durationFormatted(item.getDurationFormatted())
                .startProgress(item.getStartProgress())
                .endProgress(item.getEndProgress())
                .progressDelta(item.getProgressDelta())
                .startLocation(item.getStartLocation())
                .endLocation(item.getEndLocation())
                .currentPage(item.getCurrentPage())
                .totalPages(item.getTotalPages())
                .build();
    }

    private BookLoreUserEntity requireCurrentReaderEntity(boolean requireSyncEnabled) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof KoreaderUserDetails details)) {
            throw ApiError.GENERIC_UNAUTHORIZED.createException("Authentication required");
        }
        if (requireSyncEnabled && !details.isSyncEnabled()) {
            throw ApiError.GENERIC_UNAUTHORIZED.createException("Sync is disabled for this user");
        }
        if (details.getBookLoreUserId() == null) {
            throw ApiError.GENERIC_UNAUTHORIZED.createException("KOReader user is not linked to a Grimmory user");
        }
        return userRepository.findByIdWithDetails(details.getBookLoreUserId())
                .orElseThrow(() -> ApiError.GENERIC_UNAUTHORIZED.createException("Authenticated user no longer exists"));
    }

    private BookEntity loadAccessibleBookByHash(BookLoreUserEntity reader, String bookHash) {
        BookEntity book = bookRepository.findByCurrentHash(bookHash)
                .orElseThrow(() -> ApiError.GENERIC_NOT_FOUND.createException("Book not found for hash " + bookHash));
        if (!canAccessBook(reader, book)) {
            throw ApiError.FORBIDDEN.createException("Book is not accessible to the authenticated user");
        }
        return book;
    }

    private BookEntity loadAccessibleBookById(BookLoreUserEntity reader, Long bookId) {
        BookEntity book = bookRepository.findByIdWithBookFiles(bookId)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        if (!canAccessBook(reader, book)) {
            throw ApiError.FORBIDDEN.createException("Book is not accessible to the authenticated user");
        }
        return book;
    }

    private void persistFileProgressIfPossible(String bookHash, KoreaderProgress request) {
        BookLoreUserEntity reader = requireCurrentReaderEntity(true);
        BookEntity book = loadAccessibleBookByHash(reader, bookHash);
        BookFileEntity file = resolveRequestedFile(book, request);
        if (file == null) {
            return;
        }
        UserBookFileProgressEntity fileProgress = userBookFileProgressRepository.findByUserIdAndBookFileId(reader.getId(), file.getId())
                .orElseGet(UserBookFileProgressEntity::new);
        fileProgress.setUser(reader);
        fileProgress.setBookFile(file);
        fileProgress.setPositionData(firstNonBlank(request.getProgress(), request.getLocation()));
        fileProgress.setPositionHref(request.getLocation());
        fileProgress.setProgressPercent(normalizePercent(request.getPercentage()));
        fileProgress.setLastReadTime(resolveClientTime(request));
        userBookFileProgressRepository.save(fileProgress);
    }

    private Optional<BookEntity> resolveMetadataBook(BookLoreUserEntity reader, GrimmlinkMetadataSyncRequest request) {
        if (request.getBookId() != null) {
            return bookRepository.findByIdWithBookFiles(request.getBookId()).filter(book -> canAccessBook(reader, book));
        }
        if (trimToNull(request.getBookHash()) != null) {
            return bookRepository.findByCurrentHash(request.getBookHash()).filter(book -> canAccessBook(reader, book));
        }
        return Optional.empty();
    }

    private BookEntity resolvePullBook(BookLoreUserEntity reader, Long bookId, String bookHash, Long bookFileId) {
        if (bookId != null) {
            return loadAccessibleBookById(reader, bookId);
        }
        if (trimToNull(bookHash) != null) {
            return loadAccessibleBookByHash(reader, bookHash);
        }
        if (bookFileId != null) {
            BookFileEntity file = bookFileRepository.findById(bookFileId)
                    .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookFileId));
            BookEntity book = file.getBook();
            if (book == null || !canAccessBook(reader, book)) {
                throw ApiError.FORBIDDEN.createException("Book is not accessible to the authenticated user");
            }
            return book;
        }
        throw ApiError.GENERIC_BAD_REQUEST.createException("bookId, bookHash, or bookFileId is required");
    }

    private GrimmlinkMetadataSyncResponse metadataNotFoundResponse(GrimmlinkMetadataSyncRequest request) {
        GrimmlinkItemResult rating = request.getRating() != null
                ? failedResult("rating", request.getRating().getDedupeKey(), "book_not_found")
                : null;
        List<GrimmlinkItemResult> annotations = request.getAnnotations().stream()
                .map(item -> failedResult("annotation", item.getDedupeKey(), "book_not_found"))
                .toList();
        List<GrimmlinkItemResult> bookmarks = request.getBookmarks().stream()
                .map(item -> failedResult("bookmark", item.getDedupeKey(), "book_not_found"))
                .toList();
        return GrimmlinkMetadataSyncResponse.builder()
                .ok(false)
                .results(GrimmlinkMetadataSyncResults.builder()
                        .rating(rating)
                        .annotations(annotations)
                        .bookmarks(bookmarks)
                        .build())
                .build();
    }

    private GrimmlinkItemResult upsertMetadataItem(BookLoreUserEntity reader, BookEntity book, BookFileEntity bookFile,
                                                  GrimmlinkMetadataItemType type, String dedupeKey, Instant clientUpdatedAt,
                                                  Object payload) {
        String normalizedKey = trimToNull(dedupeKey);
        if (normalizedKey == null) {
            return failedResult(type.name().toLowerCase(Locale.ROOT), null, "missing_dedupe_key");
        }
        String payloadJson = toJson(payload);
        String contentHash = sha256Hex(type.name() + ":" + payloadJson);
        GrimmlinkMetadataItemEntity entity = metadataItemRepository
                .findByUserIdAndBookIdAndItemTypeAndDedupeKey(reader.getId(), book.getId(), type, normalizedKey)
                .orElseGet(GrimmlinkMetadataItemEntity::new);
        boolean duplicate = entity.getId() != null && Objects.equals(entity.getContentHash(), contentHash);
        entity.setUser(reader);
        entity.setBook(book);
        entity.setBookFile(bookFile);
        entity.setItemType(type);
        entity.setDedupeKey(normalizedKey);
        entity.setDevice(payload instanceof GrimmlinkMetadataSyncRequest request ? trimToNull(request.getDevice()) : null);
        entity.setDeviceId(payload instanceof GrimmlinkMetadataSyncRequest request ? trimToNull(request.getDeviceId()) : null);
        entity.setContentHash(contentHash);
        entity.setPayloadJson(payloadJson);
        entity.setClientUpdatedAt(clientUpdatedAt);
        entity.setSyncedAt(Instant.now());
        GrimmlinkMetadataItemEntity saved = metadataItemRepository.save(entity);
        return GrimmlinkItemResult.builder()
                .type(type.name().toLowerCase(Locale.ROOT))
                .dedupeKey(normalizedKey)
                .status(duplicate ? "duplicate" : "synced")
                .id(String.valueOf(saved.getId()))
                .build();
    }

    private GrimmlinkMetadataPullItem toPullItem(GrimmlinkMetadataItemEntity entity) {
        return GrimmlinkMetadataPullItem.builder()
                .id(String.valueOf(entity.getId()))
                .type(entity.getItemType().name().toLowerCase(Locale.ROOT))
                .bookId(entity.getBook() != null ? entity.getBook().getId() : null)
                .bookFileId(entity.getBookFile() != null ? entity.getBookFile().getId() : null)
                .dedupeKey(entity.getDedupeKey())
                .contentHash(entity.getContentHash())
                .payload(readPayload(entity.getPayloadJson()))
                .payloadJson(entity.getPayloadJson())
                .clientUpdatedAt(entity.getClientUpdatedAt())
                .syncedAt(entity.getSyncedAt())
                .updatedAt(entity.getUpdatedAt())
                .device(entity.getDevice())
                .deviceId(entity.getDeviceId())
                .build();
    }

    private GrimmlinkItemResult failedResult(String type, String dedupeKey, String reason) {
        return GrimmlinkItemResult.builder()
                .type(type)
                .dedupeKey(dedupeKey)
                .status("failed")
                .reason(reason)
                .build();
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return String.valueOf(payload);
        }
    }

    private Object readPayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(payloadJson, Object.class);
        } catch (Exception e) {
            return payloadJson;
        }
    }

    private String sha256Hex(String value) {
        try {
            byte[] hashed = MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(Objects.hashCode(value));
        }
    }

    private GrimmlinkBookSummary toBookSummary(BookEntity book) {
        BookFileEntity primaryFile = resolvePrimaryFile(book);
        return GrimmlinkBookSummary.builder()
                .bookId(book.getId())
                .bookFileId(primaryFile != null ? primaryFile.getId() : null)
                .title(book.getMetadata() != null ? book.getMetadata().getTitle() : null)
                .author(resolveAuthor(book))
                .fileName(primaryFile != null ? primaryFile.getFileName() : null)
                .originalFileName(primaryFile != null ? primaryFile.getFileName() : null)
                .extension(resolveExtension(primaryFile != null ? primaryFile.getFileName() : null))
                .fileFormat(primaryFile != null && primaryFile.getBookType() != null ? primaryFile.getBookType().name() : null)
                .fileSizeKb(primaryFile != null ? primaryFile.getFileSizeKb() : null)
                .fileSize(primaryFile != null && primaryFile.getFileSizeKb() != null ? primaryFile.getFileSizeKb() * 1024 : null)
                .bookHash(resolveHash(primaryFile))
                .seriesName(book.getMetadata() != null ? book.getMetadata().getSeriesName() : null)
                .seriesNumber(book.getMetadata() != null ? book.getMetadata().getSeriesNumber() : null)
                .build();
    }

    private BookFileEntity resolvePrimaryFile(BookEntity book) {
        return book != null ? book.getPrimaryBookFile() : null;
    }

    private BookFileEntity resolveRequestedFile(BookEntity book, KoreaderProgress request) {
        if (request.getBookFileId() != null) {
            return bookFileRepository.findById(request.getBookFileId())
                    .filter(file -> file.getBook() != null && file.getBook().getId().equals(book.getId()))
                    .orElse(null);
        }
        return resolvePrimaryFile(book);
    }

    private BookFileEntity resolveBookFile(BookEntity book, Long bookFileId) {
        if (bookFileId == null) {
            return resolvePrimaryFile(book);
        }
        return bookFileRepository.findById(bookFileId)
                .filter(file -> file.getBook() != null && file.getBook().getId().equals(book.getId()))
                .orElse(resolvePrimaryFile(book));
    }

    private BookFileType resolveBookType(BookEntity book) {
        BookFileEntity primary = resolvePrimaryFile(book);
        if (primary != null && primary.getBookType() != null) {
            return primary.getBookType();
        }
        throw ApiError.GENERIC_BAD_REQUEST.createException("bookType is required when the book has no primary file");
    }

    private BookFileType resolveBookType(String requestedType, BookEntity book) {
        if (trimToNull(requestedType) != null) {
            try {
                return BookFileType.valueOf(requestedType.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw ApiError.GENERIC_BAD_REQUEST.createException("Invalid bookType: " + requestedType);
            }
        }
        return resolveBookType(book);
    }

    private String resolveRequestBookHash(String requestedHash, BookEntity book) {
        if (trimToNull(requestedHash) != null) {
            return requestedHash.trim();
        }
        return resolveHash(resolvePrimaryFile(book));
    }

    private String resolveHash(BookFileEntity file) {
        if (file == null) {
            return null;
        }
        return firstNonBlank(file.getCurrentHash(), file.getInitialHash());
    }

    private String resolveExtension(String fileName) {
        if (fileName == null) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 && dot < fileName.length() - 1 ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : null;
    }

    private String resolveAuthor(BookEntity book) {
        if (book.getMetadata() == null || book.getMetadata().getAuthors() == null) {
            return null;
        }
        return book.getMetadata().getAuthors().stream()
                .map(AuthorEntity::getName)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.joining(", "));
    }

    private ReadStatus normalizeReadStatus(String requestedStatus) {
        if (trimToNull(requestedStatus) == null) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("status is required");
        }
        if ("ON_HOLD".equalsIgnoreCase(requestedStatus.trim())) {
            return ReadStatus.PAUSED;
        }
        try {
            ReadStatus status = ReadStatus.valueOf(requestedStatus.trim().toUpperCase(Locale.ROOT));
            if (!SUPPORTED_MANUAL_READ_STATUSES.contains(status)) {
                throw ApiError.GENERIC_BAD_REQUEST.createException("Unsupported status: " + requestedStatus);
            }
            return status;
        } catch (IllegalArgumentException e) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("Unsupported status: " + requestedStatus);
        }
    }

    private Float normalizePercent(Float percentage) {
        if (percentage == null) {
            return null;
        }
        return percentage <= 1.0f ? percentage * 100.0f : percentage;
    }

    private Instant resolveClientTime(KoreaderProgress request) {
        if (request.getUpdatedAt() != null) {
            return request.getUpdatedAt();
        }
        if (request.getTimestamp() != null) {
            return Instant.ofEpochSecond(request.getTimestamp());
        }
        return Instant.now();
    }

    private Integer countMagicShelfBooks(BookLoreUserEntity reader, Long shelfId) {
        try {
            List<Long> ids = magicShelfBookService.getBookIdsByMagicShelfId(reader.getId(), shelfId);
            return ids != null ? ids.size() : 0;
        } catch (Exception ex) {
            log.debug("Unable to resolve magic shelf count for shelf {}: {}", shelfId, ex.getMessage());
            return null;
        }
    }

    private String normalizeShelfTypeOrNull(String shelfType) {
        return trimToNull(shelfType) == null ? null : normalizeShelfType(shelfType);
    }

    private String normalizeShelfType(String shelfType) {
        String normalized = shelfType == null ? "regular" : shelfType.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "regular";
        }
        if (!"regular".equals(normalized) && !"magic".equals(normalized)) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("Unsupported shelf type: " + shelfType);
        }
        return normalized;
    }

    private GrimmlinkMetadataItemType normalizeMetadataType(String type) {
        String normalized = trimToNull(type);
        if (normalized == null) {
            return null;
        }
        try {
            return GrimmlinkMetadataItemType.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("Unsupported metadata type: " + type);
        }
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return 100;
        }
        return Math.max(1, Math.min(limit, 500));
    }

    private boolean canReadShelf(BookLoreUserEntity reader, ShelfEntity shelf) {
        return isAdmin(reader) || shelf.isPublic() || shelf.getUser().getId().equals(reader.getId());
    }

    private boolean canModifyShelf(BookLoreUserEntity reader, ShelfEntity shelf) {
        return isAdmin(reader) || shelf.getUser().getId().equals(reader.getId());
    }

    private boolean canReadMagicShelf(BookLoreUserEntity reader, MagicShelfEntity shelf) {
        return isAdmin(reader) || shelf.isPublic() || shelf.getUserId().equals(reader.getId());
    }

    private boolean canAccessBook(BookLoreUserEntity reader, BookEntity book) {
        return isAdmin(reader) || reader.getLibraries().stream().anyMatch(library -> library.getId().equals(book.getLibrary().getId()));
    }

    private boolean isAdmin(BookLoreUserEntity reader) {
        return reader.getPermissions() != null && reader.getPermissions().isPermissionAdmin();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private static final class StreamSafe {
        private static boolean allSuccess(GrimmlinkItemResult rating,
                                          List<GrimmlinkItemResult> annotations,
                                          List<GrimmlinkItemResult> bookmarks) {
            return (rating == null || !"failed".equals(rating.getStatus()))
                    && annotations.stream().noneMatch(item -> "failed".equals(item.getStatus()))
                    && bookmarks.stream().noneMatch(item -> "failed".equals(item.getStatus()));
        }
    }
}
