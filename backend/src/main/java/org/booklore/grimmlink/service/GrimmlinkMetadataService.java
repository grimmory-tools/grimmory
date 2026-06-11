package org.booklore.grimmlink.service;

import lombok.RequiredArgsConstructor;
import org.booklore.exception.ApiError;
import org.booklore.grimmlink.dto.GrimmlinkItemResult;
import org.booklore.grimmlink.dto.GrimmlinkMetadataBatchResponse;
import org.booklore.grimmlink.dto.GrimmlinkMetadataPullItem;
import org.booklore.grimmlink.dto.GrimmlinkMetadataPullResponse;
import org.booklore.grimmlink.dto.GrimmlinkMetadataSyncRequest;
import org.booklore.grimmlink.dto.GrimmlinkMetadataSyncResponse;
import org.booklore.grimmlink.dto.GrimmlinkMetadataSyncResults;
import org.booklore.grimmlink.model.GrimmlinkMetadataItemEntity;
import org.booklore.grimmlink.model.GrimmlinkMetadataItemType;
import org.booklore.grimmlink.repository.GrimmlinkMetadataItemRepository;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.repository.BookFileRepository;
import org.booklore.repository.BookRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
                ? upsertMetadataItem(
                        reader,
                        book,
                        bookFile,
                        GrimmlinkMetadataItemType.RATING,
                        normalized.getRating().getDedupeKey(),
                        normalized.getRating().getUpdatedAt(),
                        normalized)
                : null;
        List<GrimmlinkItemResult> annotations = normalized.getAnnotations().stream()
                .map(item -> upsertMetadataItem(
                        reader,
                        book,
                        bookFile,
                        GrimmlinkMetadataItemType.ANNOTATION,
                        item.getDedupeKey(),
                        item.getUpdatedAt(),
                        item))
                .toList();
        List<GrimmlinkItemResult> bookmarks = normalized.getBookmarks().stream()
                .map(item -> upsertMetadataItem(
                        reader,
                        book,
                        bookFile,
                        GrimmlinkMetadataItemType.BOOKMARK,
                        item.getDedupeKey(),
                        item.getUpdatedAt(),
                        item))
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
        List<GrimmlinkMetadataPullItem> items = metadataItemRepository
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
        Instant nextCursor = items.isEmpty()
                ? effectiveSince
                : items.get(items.size() - 1).getUpdatedAt();
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

    private GrimmlinkItemResult upsertMetadataItem(
            BookLoreUserEntity reader,
            BookEntity book,
            BookFileEntity bookFile,
            GrimmlinkMetadataItemType type,
            String dedupeKey,
            Instant clientUpdatedAt,
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
        entity.setDevice(payload instanceof GrimmlinkMetadataSyncRequest metadataRequest
                ? bookService.trimToNull(metadataRequest.getDevice())
                : null);
        entity.setDeviceId(payload instanceof GrimmlinkMetadataSyncRequest metadataRequest
                ? bookService.trimToNull(metadataRequest.getDeviceId())
                : null);
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
