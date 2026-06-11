package org.booklore.grimmlink.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.grimmlink.dto.GrimmlinkReadingSessionBatchRequest;
import org.booklore.grimmlink.dto.GrimmlinkReadingSessionBatchResponse;
import org.booklore.grimmlink.dto.GrimmlinkReadingSessionItemRequest;
import org.booklore.model.dto.request.ReadingSessionRequest;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.ReadingSessionEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.ReadingSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GrimmlinkReadingSessionService {

    private final GrimmlinkAuthService authService;
    private final GrimmlinkBookService bookService;
    private final ReadingSessionRepository readingSessionRepository;

    @Transactional
    public void recordReadingSession(ReadingSessionRequest request) {
        BookLoreUserEntity reader = authService.requireCurrentReader(true);
        BookEntity book = bookService.loadAccessibleBookById(reader, request.getBookId());
        String effectiveHash = resolveRequestBookHash(request.getBookHash(), book);
        String effectiveDeviceId = bookService.trimToNull(request.getDeviceId());
        Optional<ReadingSessionEntity> existing = readingSessionRepository.findDuplicate(
                reader.getId(),
                book.getId(),
                effectiveHash,
                request.getStartTime(),
                request.getEndTime(),
                effectiveDeviceId);
        if (existing.isPresent()) {
            log.info(
                    "Grimmlink reading session duplicate (skipped): sessionId={}, userId={}, bookId={}, bookHash={}",
                    existing.get().getId(),
                    reader.getId(),
                    book.getId(),
                    effectiveHash);
            return;
        }

        ReadingSessionEntity session = readingSessionRepository.save(
                buildSession(reader, book, request));
        log.info(
                "Grimmlink reading session persisted successfully: sessionId={}, userId={}, bookId={}, bookHash={}, bookType={}, duration={}s, device={}, deviceId={}",
                session.getId(),
                reader.getId(),
                book.getId(),
                session.getBookHash(),
                session.getBookType(),
                session.getDurationSeconds(),
                session.getDevice(),
                session.getDeviceId());
    }

    @Transactional
    public GrimmlinkReadingSessionBatchResponse recordReadingSessionsBatch(
            GrimmlinkReadingSessionBatchRequest request) {
        BookLoreUserEntity reader = authService.requireCurrentReader(true);
        BookEntity book = bookService.loadAccessibleBookById(reader, request.getBookId());
        String effectiveHash = resolveRequestBookHash(request.getBookHash(), book);
        String effectiveDeviceId = bookService.trimToNull(request.getDeviceId());
        int successCount = 0;
        List<GrimmlinkReadingSessionBatchResponse.SessionResult> results = new ArrayList<>();

        for (int i = 0; i < request.getSessions().size(); i++) {
            GrimmlinkReadingSessionItemRequest item = request.getSessions().get(i);
            try {
                Optional<ReadingSessionEntity> existing = readingSessionRepository.findDuplicate(
                        reader.getId(),
                        book.getId(),
                        effectiveHash,
                        item.getStartTime(),
                        item.getEndTime(),
                        effectiveDeviceId);
                if (existing.isPresent()) {
                    results.add(GrimmlinkReadingSessionBatchResponse.SessionResult.builder()
                            .index(i)
                            .sessionId(existing.get().getId())
                            .status("duplicate")
                            .message("Duplicate reading session")
                            .startTime(item.getStartTime())
                            .endTime(item.getEndTime())
                            .build());
                    successCount++;
                    continue;
                }

                ReadingSessionEntity session = readingSessionRepository.save(
                        buildSession(reader, book, request, item));
                results.add(GrimmlinkReadingSessionBatchResponse.SessionResult.builder()
                        .index(i)
                        .sessionId(session.getId())
                        .status("created")
                        .startTime(session.getStartTime())
                        .endTime(session.getEndTime())
                        .build());
                successCount++;
            } catch (Exception e) {
                log.warn("Grimmlink reading session batch item {} failed: {}", i, e.getMessage());
                results.add(GrimmlinkReadingSessionBatchResponse.SessionResult.builder()
                        .index(i)
                        .status("error")
                        .message(e.getMessage())
                        .startTime(item.getStartTime())
                        .endTime(item.getEndTime())
                        .build());
            }
        }

        log.info(
                "Grimmlink reading session batch persisted successfully: userId={}, bookId={}, bookHash={}, bookType={}, requested={}, saved={}, device={}, deviceId={}",
                reader.getId(),
                book.getId(),
                effectiveHash,
                resolveBookType(request.getBookType(), book),
                request.getSessions().size(),
                successCount,
                bookService.trimToNull(request.getDevice()),
                bookService.trimToNull(request.getDeviceId()));
        return GrimmlinkReadingSessionBatchResponse.builder()
                .totalRequested(request.getSessions().size())
                .successCount(successCount)
                .results(results)
                .build();
    }

    private ReadingSessionEntity buildSession(
            BookLoreUserEntity reader,
            BookEntity book,
            ReadingSessionRequest request) {
        return ReadingSessionEntity.builder()
                .user(reader)
                .book(book)
                .bookType(request.getBookType() != null ? request.getBookType() : resolveBookType(book))
                .bookHash(resolveRequestBookHash(request.getBookHash(), book))
                .device(bookService.trimToNull(request.getDevice()))
                .deviceId(bookService.trimToNull(request.getDeviceId()))
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

    private ReadingSessionEntity buildSession(
            BookLoreUserEntity reader,
            BookEntity book,
            GrimmlinkReadingSessionBatchRequest request,
            GrimmlinkReadingSessionItemRequest item) {
        return ReadingSessionEntity.builder()
                .user(reader)
                .book(book)
                .bookType(resolveBookType(request.getBookType(), book))
                .bookHash(resolveRequestBookHash(request.getBookHash(), book))
                .device(bookService.trimToNull(request.getDevice()))
                .deviceId(bookService.trimToNull(request.getDeviceId()))
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

    private BookFileType resolveBookType(BookEntity book) {
        if (bookService.resolvePrimaryFile(book) != null
                && bookService.resolvePrimaryFile(book).getBookType() != null) {
            return bookService.resolvePrimaryFile(book).getBookType();
        }
        throw org.booklore.exception.ApiError.GENERIC_BAD_REQUEST.createException(
                "bookType is required when the book has no primary file");
    }

    private BookFileType resolveBookType(String requestedType, BookEntity book) {
        if (bookService.trimToNull(requestedType) != null) {
            try {
                return BookFileType.valueOf(requestedType.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw org.booklore.exception.ApiError.GENERIC_BAD_REQUEST.createException(
                        "Invalid bookType: " + requestedType);
            }
        }
        return resolveBookType(book);
    }

    private String resolveRequestBookHash(String requestedHash, BookEntity book) {
        if (bookService.trimToNull(requestedHash) != null) {
            return requestedHash.trim();
        }
        return bookService.resolveHash(bookService.resolvePrimaryFile(book));
    }
}
