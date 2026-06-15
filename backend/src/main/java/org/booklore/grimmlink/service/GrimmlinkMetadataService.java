package org.booklore.grimmlink.service;

import lombok.RequiredArgsConstructor;
import org.booklore.config.BookmarkProperties;
import org.booklore.exception.ApiError;
import org.booklore.grimmlink.dto.GrimmlinkBookmarkPayload;
import org.booklore.grimmlink.dto.GrimmlinkItemResult;
import org.booklore.grimmlink.dto.GrimmlinkLocationPayload;
import org.booklore.grimmlink.dto.GrimmlinkMetadataBatchResponse;
import org.booklore.grimmlink.dto.GrimmlinkMetadataPullItem;
import org.booklore.grimmlink.dto.GrimmlinkMetadataPullResponse;
import org.booklore.grimmlink.dto.GrimmlinkMetadataSyncRequest;
import org.booklore.grimmlink.dto.GrimmlinkMetadataSyncResponse;
import org.booklore.grimmlink.dto.GrimmlinkMetadataSyncResults;
import org.booklore.grimmlink.dto.GrimmlinkRatingPayload;
import org.booklore.grimmlink.model.GrimmlinkMetadataItemEntity;
import org.booklore.grimmlink.model.GrimmlinkMetadataItemType;
import org.booklore.grimmlink.repository.GrimmlinkMetadataItemRepository;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.BookMarkEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.repository.BookFileRepository;
import org.booklore.repository.BookMarkRepository;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GrimmlinkMetadataService {

    private final GrimmlinkAuthService authService;
    private final GrimmlinkBookService bookService;
    private final GrimmlinkHashMatcher hashMatcher;
    private final BookRepository bookRepository;
    private final BookFileRepository bookFileRepository;
    private final UserBookProgressRepository userBookProgressRepository;
    private final BookMarkRepository bookMarkRepository;
    private final BookmarkProperties bookmarkProperties;
    private final GrimmlinkMetadataItemRepository metadataItemRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public GrimmlinkMetadataSyncResponse syncMetadata(GrimmlinkMetadataSyncRequest request) {
        BookLoreUserEntity reader = authService.requireCurrentReader(true);
        GrimmlinkMetadataSyncRequest normalized = normalizeRequest(request);
        Optional<BookEntity> bookOpt = resolveMetadataBook(reader, normalized);
        if (bookOpt.isEmpty()) {
            return metadataNotFoundResponse(normalized);
        }
        BookEntity book = bookOpt.get();
        BookFileEntity bookFile = resolveBookFile(book, normalized.getBookFileId());
        GrimmlinkItemResult rating = normalized.getRating() != null
                ? syncRating(reader, book, bookFile, normalized)
                : null;
        List<GrimmlinkItemResult> annotations = normalized.getAnnotations().stream()
                .map(item -> upsertMetadataItem(
                        reader,
                        book,
                        bookFile,
                        GrimmlinkMetadataItemType.ANNOTATION,
                        item.getDedupeKey(),
                        item.getUpdatedAt(),
                        normalized.getDevice(),
                        normalized.getDeviceId(),
                        item))
                .toList();
        List<GrimmlinkItemResult> bookmarks = normalized.getBookmarks().stream()
                .map(item -> syncBookmark(reader, book, bookFile, normalized, item))
                .toList();
        return GrimmlinkMetadataSyncResponse.builder()
                .bookId(book.getId())
                .ok(allSuccess(rating, annotations, bookmarks))
                .results(GrimmlinkMetadataSyncResults.builder()
                        .rating(rating)
                        .annotations(annotations)
                        .bookmarks(bookmarks)
                        .build())
                .build();
    }

    @Transactional(readOnly = true)
    public GrimmlinkMetadataPullResponse pullMetadata(
            Long bookId,
            String bookHash,
            Long bookFileId,
            Instant since,
            Instant cursor,
            Integer limit,
            String type) {
        BookLoreUserEntity reader = authService.requireCurrentReader(true);
        BookEntity book = resolvePullBook(reader, bookId, bookHash, bookFileId);
        BookFileEntity bookFile = bookFileId != null ? resolveBookFile(book, bookFileId) : null;
        GrimmlinkMetadataItemType itemType = normalizeMetadataType(type);
        int normalizedLimit = normalizeLimit(limit);
        Instant effectiveSince = cursor != null ? cursor : since;
        List<GrimmlinkMetadataPullItem> storedItems = metadataItemRepository
                .findPullItems(
                        reader.getId(),
                        book.getId(),
                        bookFile != null ? bookFile.getId() : null,
                        itemType,
                        effectiveSince,
                        PageRequest.of(0, normalizedLimit))
                .stream()
                .map(this::toPullItem)
                .toList();
        List<GrimmlinkMetadataPullItem> items = new ArrayList<>(normalizedLimit);
        if (itemType == null || itemType == GrimmlinkMetadataItemType.RATING) {
            userBookProgressRepository.findByUserIdAndBookId(reader.getId(), book.getId())
                    .map(UserBookProgressEntity::getPersonalRating)
                    .filter(rating -> rating >= 1 && rating <= 10)
                    .map(rating -> GrimmlinkMetadataPullItem.builder()
                            .id("grimmory-personal-rating")
                            .type("rating")
                            .bookId(book.getId())
                            .bookFileId(bookFile != null ? bookFile.getId() : null)
                            .dedupeKey("grimmory-personal-rating:" + rating)
                            .payload(Map.of(
                                    "value", rating,
                                    "scale", 10,
                                    "source", "grimmory-web"))
                            .device("Grimmory Web")
                            .build())
                    .ifPresent(items::add);
        }
        List<GrimmlinkMetadataPullItem> candidates = new ArrayList<>(storedItems);
        if (itemType == null || itemType == GrimmlinkMetadataItemType.BOOKMARK) {
            bookMarkRepository.findByBookIdAndUserIdOrderByPriorityAscCreatedAtDesc(book.getId(), reader.getId())
                    .stream()
                    .map(bookmark -> toGrimmoryBookmarkPullItem(bookmark, bookFile))
                    .filter(bookmark -> effectiveSince == null
                            || bookmark.getUpdatedAt() == null
                            || bookmark.getUpdatedAt().isAfter(effectiveSince))
                    .forEach(candidates::add);
        }
        candidates.sort(Comparator.comparing(
                GrimmlinkMetadataPullItem::getUpdatedAt,
                Comparator.nullsLast(Comparator.naturalOrder())));
        int candidateLimit = Math.max(0, normalizedLimit - items.size());
        candidates.stream()
                .limit(candidateLimit)
                .forEach(items::add);
        Instant nextCursor = candidates.isEmpty() || candidateLimit == 0
                ? effectiveSince
                : candidates.get(Math.min(candidates.size(), candidateLimit) - 1).getUpdatedAt();
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
        GrimmlinkMetadataSyncRequest normalized = request == null
                ? new GrimmlinkMetadataSyncRequest()
                : request;
        String syncMode = normalized.getSyncMode();
        syncMode = syncMode != null
                ? syncMode.trim().toLowerCase(Locale.ROOT)
                : "incremental";
        if (!"push".equals(syncMode) && !"pull".equals(syncMode)) {
            syncMode = "incremental";
        }

        GrimmlinkMetadataSyncResponse push = null;
        GrimmlinkMetadataPullResponse pull = null;
        if ("push".equals(syncMode) || "incremental".equals(syncMode)) {
            push = syncMetadata(normalized);
        }
        if ("pull".equals(syncMode) || "incremental".equals(syncMode)) {
            pull = pullMetadata(
                    normalized.getBookId(),
                    normalized.getBookHash(),
                    normalized.getBookFileId(),
                    normalized.getSince(),
                    normalized.getCursor(),
                    normalized.getLimit(),
                    normalized.getType());
        }

        if (push == null) {
            push = GrimmlinkMetadataSyncResponse.builder()
                    .bookId(normalized.getBookId())
                    .ok(true)
                    .results(GrimmlinkMetadataSyncResults.builder().build())
                    .build();
        }
        if (pull == null) {
            pull = GrimmlinkMetadataPullResponse.builder()
                    .bookId(normalized.getBookId())
                    .ok(true)
                    .since(normalized.getSince())
                    .limit(normalizeLimit(normalized.getLimit()))
                    .items(List.of())
                    .build();
        }
        return GrimmlinkMetadataBatchResponse.builder()
                .ok(push.isOk() && pull.isOk())
                .push(push)
                .pull(pull)
                .build();
    }

    private GrimmlinkMetadataSyncRequest normalizeRequest(GrimmlinkMetadataSyncRequest request) {
        GrimmlinkMetadataSyncRequest normalized = request == null
                ? new GrimmlinkMetadataSyncRequest()
                : request;
        if (normalized.getAnnotations() == null) {
            normalized.setAnnotations(new ArrayList<>());
        }
        if (normalized.getBookmarks() == null) {
            normalized.setBookmarks(new ArrayList<>());
        }
        return normalized;
    }

    private Optional<BookEntity> resolveMetadataBook(
            BookLoreUserEntity reader,
            GrimmlinkMetadataSyncRequest request) {
        if (request.getBookId() != null) {
            return bookRepository.findByIdWithBookFiles(request.getBookId())
                    .filter(book -> bookService.canAccessBook(reader, book));
        }
        if (bookService.trimToNull(request.getBookHash()) != null) {
            try {
                return Optional.of(
                        hashMatcher.resolveAccessibleBookByHash(reader, request.getBookHash()));
            } catch (Exception e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private BookEntity resolvePullBook(
            BookLoreUserEntity reader,
            Long bookId,
            String bookHash,
            Long bookFileId) {
        if (bookId != null) {
            return bookService.loadAccessibleBookById(reader, bookId);
        }
        if (bookService.trimToNull(bookHash) != null) {
            return hashMatcher.resolveAccessibleBookByHash(reader, bookHash);
        }
        if (bookFileId != null) {
            BookFileEntity file = bookFileRepository.findById(bookFileId)
                    .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookFileId));
            BookEntity book = file.getBook();
            if (book == null || !bookService.canAccessBook(reader, book)) {
                throw ApiError.FORBIDDEN.createException(
                        "Book is not accessible to the authenticated user");
            }
            return book;
        }
        throw ApiError.GENERIC_BAD_REQUEST.createException(
                "bookId, bookHash, or bookFileId is required");
    }

    private BookFileEntity resolveBookFile(BookEntity book, Long bookFileId) {
        if (bookFileId == null) {
            return bookService.resolvePrimaryFile(book);
        }
        return bookFileRepository.findById(bookFileId)
                .filter(file -> file.getBook() != null && file.getBook().getId().equals(book.getId()))
                .orElse(bookService.resolvePrimaryFile(book));
    }

    private GrimmlinkMetadataSyncResponse metadataNotFoundResponse(
            GrimmlinkMetadataSyncRequest request) {
        GrimmlinkItemResult rating = request.getRating() != null
                ? failedResult(
                        "rating",
                        request.getRating().getDedupeKey(),
                        "book_not_found")
                : null;
        List<GrimmlinkItemResult> annotations = request.getAnnotations().stream()
                .map(item -> failedResult(
                        "annotation",
                        item.getDedupeKey(),
                        "book_not_found"))
                .toList();
        List<GrimmlinkItemResult> bookmarks = request.getBookmarks().stream()
                .map(item -> failedResult(
                        "bookmark",
                        item.getDedupeKey(),
                        "book_not_found"))
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

    private GrimmlinkItemResult syncRating(
            BookLoreUserEntity reader,
            BookEntity book,
            BookFileEntity bookFile,
            GrimmlinkMetadataSyncRequest request) {
        GrimmlinkRatingPayload payload = request.getRating();
        String dedupeKey = bookService.trimToNull(payload.getDedupeKey());
        // Handle rating removal (value=0 or value=null signals deletion)
        if (payload.getValue() == null || payload.getValue() == 0) {
            if (dedupeKey != null) {
                metadataItemRepository.findByUserIdAndBookIdAndItemTypeAndDedupeKey(
                        reader.getId(), book.getId(),
                        GrimmlinkMetadataItemType.RATING, dedupeKey)
                        .ifPresent(metadataItemRepository::delete);
            }
            userBookProgressRepository.findByUserIdAndBookId(reader.getId(), book.getId())
                    .ifPresent(progress -> {
                        progress.setPersonalRating(null);
                        userBookProgressRepository.save(progress);
                    });
            return GrimmlinkItemResult.builder()
                    .type("rating")
                    .dedupeKey(dedupeKey)
                    .status("removed")
                    .build();
        }
        Integer normalizedRating = normalizePersonalRating(payload);
        if (normalizedRating == null) {
            return failedResult("rating", dedupeKey, "invalid_rating");
        }
        GrimmlinkItemResult result = upsertMetadataItem(
                reader,
                book,
                bookFile,
                GrimmlinkMetadataItemType.RATING,
                dedupeKey,
                payload.getUpdatedAt(),
                request.getDevice(),
                request.getDeviceId(),
                payload);
        if (!"failed".equals(result.getStatus())) {
            UserBookProgressEntity progress = userBookProgressRepository
                    .findByUserIdAndBookId(reader.getId(), book.getId())
                    .orElseGet(() -> UserBookProgressEntity.builder()
                            .user(reader)
                            .book(book)
                            .build());
            progress.setPersonalRating(normalizedRating);
            userBookProgressRepository.save(progress);
        }
        return result;
    }

    private Integer normalizePersonalRating(GrimmlinkRatingPayload payload) {
        if (payload == null || payload.getValue() == null) {
            return null;
        }
        int scale = payload.getScale() == null ? 10 : payload.getScale();
        int value = payload.getValue();
        if (scale == 10 && value >= 1 && value <= 10) {
            return value;
        }
        if (scale == 5 && value >= 1 && value <= 5) {
            return value * 2;
        }
        return null;
    }

    private GrimmlinkItemResult syncBookmark(
            BookLoreUserEntity reader,
            BookEntity book,
            BookFileEntity bookFile,
            GrimmlinkMetadataSyncRequest request,
            GrimmlinkBookmarkPayload payload) {
        String dedupeKey = bookService.trimToNull(payload.getDedupeKey());
        if (dedupeKey == null) {
            return failedResult("bookmark", null, "missing_dedupe_key");
        }
        // Handle bookmark deletion (empty content = deletion signal)
        if (isBookmarkDeletion(payload)) {
            removeGrimmoryBookmark(reader, book, dedupeKey);
            return GrimmlinkItemResult.builder()
                    .type("bookmark")
                    .dedupeKey(dedupeKey)
                    .status("deleted")
                    .build();
        }
        GrimmlinkItemResult result = upsertMetadataItem(
                reader,
                book,
                bookFile,
                GrimmlinkMetadataItemType.BOOKMARK,
                dedupeKey,
                payload.getUpdatedAt(),
                request.getDevice(),
                request.getDeviceId(),
                payload);
        if (!"failed".equals(result.getStatus())) {
            saveGrimmoryBookmark(reader, book, payload);
        }
        return result;
    }

    private void saveGrimmoryBookmark(
            BookLoreUserEntity reader,
            BookEntity book,
            GrimmlinkBookmarkPayload payload) {
        Integer pageNumber = bookmarkPage(payload);
        String cfi = pageNumber == null ? bookmarkAnchor(payload) : null;
        if (pageNumber == null && cfi == null) {
            return;
        }
        Optional<BookMarkEntity> existing = pageNumber != null
                ? bookMarkRepository.findFirstByPageNumberAndBookIdAndUserId(
                        pageNumber,
                        book.getId(),
                        reader.getId())
                : bookMarkRepository.findFirstByCfiAndBookIdAndUserId(
                        cfi,
                        book.getId(),
                        reader.getId());
        BookMarkEntity bookmark = existing.orElseGet(() -> BookMarkEntity.builder()
                .user(reader)
                .book(book)
                .pageNumber(pageNumber)
                .cfi(cfi)
                .priority(bookmarkProperties.getDefaultPriority())
                .build());
        bookmark.setTitle(truncate(bookService.trimToNull(payload.getTitle()), bookmarkProperties.getMaxTitleLength()));
        bookmark.setNotes(truncate(bookService.trimToNull(payload.getNotes()), bookmarkProperties.getMaxNotesLength()));
        if (payload.getCreatedAt() != null) {
            bookmark.setCreatedAt(toLocalDateTime(payload.getCreatedAt()));
        }
        bookMarkRepository.save(bookmark);
    }

    private boolean isBookmarkDeletion(GrimmlinkBookmarkPayload payload) {
        return payload.getTitle() == null
                && payload.getNotes() == null
                && bookmarkPage(payload) == null
                && bookmarkAnchor(payload) == null;
    }

    private void removeGrimmoryBookmark(
            BookLoreUserEntity reader,
            BookEntity book,
            String dedupeKey) {
        // 1. Look up existing metadata_item to extract page/CFI for BookMark deletion
        Optional<GrimmlinkMetadataItemEntity> existing = metadataItemRepository
                .findByUserIdAndBookIdAndItemTypeAndDedupeKey(
                        reader.getId(), book.getId(),
                        GrimmlinkMetadataItemType.BOOKMARK, dedupeKey);
        existing.ifPresent(entity -> {
            String payloadJson = entity.getPayloadJson();
            if (payloadJson != null && !payloadJson.isBlank()) {
                try {
                    GrimmlinkBookmarkPayload archivedPayload = objectMapper.readValue(
                            payloadJson, GrimmlinkBookmarkPayload.class);
                    Integer page = bookmarkPage(archivedPayload);
                    if (page != null) {
                        bookMarkRepository.findFirstByPageNumberAndBookIdAndUserId(page, book.getId(), reader.getId())
                                .ifPresent(bookMarkRepository::delete);
                        return;
                    }
                    String cfi = bookmarkAnchor(archivedPayload);
                    if (cfi != null) {
                        bookMarkRepository.findFirstByCfiAndBookIdAndUserId(cfi, book.getId(), reader.getId())
                                .ifPresent(bookMarkRepository::delete);
                        return;
                    }
                } catch (Exception ignored) {
                    // Could not parse archived payload; BookMarkEntity won't be deleted
                }
            }
        });
        // 2. Delete the metadata item
        existing.ifPresent(metadataItemRepository::delete);
    }

    private Integer bookmarkPage(GrimmlinkBookmarkPayload payload) {
        Integer page = payload.getPage();
        if (page == null && payload.getLocation() != null) {
            page = payload.getLocation().getPageno();
        }
        return page != null && page > 0 ? page : null;
    }

    private String bookmarkAnchor(GrimmlinkBookmarkPayload payload) {
        GrimmlinkLocationPayload location = payload.getLocation();
        if (location == null) {
            return null;
        }
        String anchor = firstNonBlank(
                location.getCfi(),
                location.getPos0(),
                location.getRaw());
        return truncate(anchor, bookmarkProperties.getMaxCfiLength());
    }

    private GrimmlinkMetadataPullItem toGrimmoryBookmarkPullItem(
            BookMarkEntity bookmark,
            BookFileEntity bookFile) {
        Map<String, Object> payload = new LinkedHashMap<>();
        putIfNotNull(payload, "title", bookmark.getTitle());
        putIfNotNull(payload, "notes", bookmark.getNotes());
        putIfNotNull(payload, "page", bookmark.getPageNumber());
        Map<String, Object> location = new LinkedHashMap<>();
        putIfNotNull(location, "pageno", bookmark.getPageNumber());
        putIfNotNull(location, "cfi", bookmark.getCfi());
        if (!location.isEmpty()) {
            payload.put("location", location);
        }
        putIfNotNull(payload, "createdAt", toInstant(bookmark.getCreatedAt()));
        putIfNotNull(payload, "updatedAt", toInstant(bookmark.getUpdatedAt()));
        payload.put("source", "grimmory-web");

        String version = bookmark.getUpdatedAt() != null
                ? bookmark.getUpdatedAt().toString()
                : String.valueOf(bookmark.getVersion());
        return GrimmlinkMetadataPullItem.builder()
                .id("grimmory-bookmark:" + bookmark.getId())
                .type("bookmark")
                .bookId(bookmark.getBookId() != null
                        ? bookmark.getBookId()
                        : bookmark.getBook().getId())
                .bookFileId(bookFile != null ? bookFile.getId() : null)
                .dedupeKey("grimmory-bookmark:" + bookmark.getId() + ":" + version)
                .payload(payload)
                .updatedAt(toInstant(bookmark.getUpdatedAt()))
                .device("Grimmory Web")
                .build();
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = bookService.trimToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private LocalDateTime toLocalDateTime(Instant value) {
        return value != null ? LocalDateTime.ofInstant(value, ZoneOffset.UTC) : null;
    }

    private Instant toInstant(LocalDateTime value) {
        return value != null ? value.toInstant(ZoneOffset.UTC) : null;
    }

    private GrimmlinkItemResult upsertMetadataItem(
            BookLoreUserEntity reader,
            BookEntity book,
            BookFileEntity bookFile,
            GrimmlinkMetadataItemType type,
            String dedupeKey,
            Instant clientUpdatedAt,
            String device,
            String deviceId,
            Object payload) {
        String normalizedKey = bookService.trimToNull(dedupeKey);
        if (normalizedKey == null) {
            return failedResult(type.name().toLowerCase(Locale.ROOT), null, "missing_dedupe_key");
        }
        String payloadJson = toJson(payload);
        String contentHash = sha256Hex(type.name() + ":" + payloadJson);
        GrimmlinkMetadataItemEntity entity = metadataItemRepository
                .findByUserIdAndBookIdAndItemTypeAndDedupeKey(
                        reader.getId(),
                        book.getId(),
                        type,
                        normalizedKey)
                .orElseGet(GrimmlinkMetadataItemEntity::new);
        boolean duplicate = entity.getId() != null
                && Objects.equals(entity.getContentHash(), contentHash);
        entity.setUser(reader);
        entity.setBook(book);
        entity.setBookFile(bookFile);
        entity.setItemType(type);
        entity.setDedupeKey(normalizedKey);
        entity.setDevice(bookService.trimToNull(device));
        entity.setDeviceId(bookService.trimToNull(deviceId));
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

    private GrimmlinkMetadataItemType normalizeMetadataType(String type) {
        String normalized = bookService.trimToNull(type);
        if (normalized == null) {
            return null;
        }
        try {
            return GrimmlinkMetadataItemType.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiError.GENERIC_BAD_REQUEST.createException(
                    "Unsupported metadata type: " + type);
        }
    }

    private int normalizeLimit(Integer limit) {
        return limit == null ? 100 : Math.max(1, Math.min(limit, 500));
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

    private boolean allSuccess(
            GrimmlinkItemResult rating,
            List<GrimmlinkItemResult> annotations,
            List<GrimmlinkItemResult> bookmarks) {
        return (rating == null || !"failed".equals(rating.getStatus()))
                && annotations.stream().noneMatch(item -> "failed".equals(item.getStatus()))
                && bookmarks.stream().noneMatch(item -> "failed".equals(item.getStatus()));
    }
}
