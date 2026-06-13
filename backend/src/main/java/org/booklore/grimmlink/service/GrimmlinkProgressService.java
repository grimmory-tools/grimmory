package org.booklore.grimmlink.service;

import lombok.RequiredArgsConstructor;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.progress.KoreaderProgress;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.UserBookFileProgressEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.repository.BookFileRepository;
import org.booklore.repository.UserBookFileProgressRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class GrimmlinkProgressService {

    private final GrimmlinkAuthService authService;
    private final GrimmlinkBookService bookService;
    private final GrimmlinkHashMatcher hashMatcher;
    private final BookFileRepository bookFileRepository;
    private final UserBookProgressRepository userBookProgressRepository;
    private final UserBookFileProgressRepository userBookFileProgressRepository;

    @Transactional(readOnly = true)
    public KoreaderProgress getProgress(String bookHash) {
        BookLoreUserEntity reader = authService.requireCurrentReader(true);
        BookEntity book = hashMatcher.resolveAccessibleBookByHash(reader, bookHash);
        UserBookProgressEntity progress = userBookProgressRepository
                .findByUserIdAndBookId(reader.getId(), book.getId())
                .orElse(null);
        BookFileEntity primaryFile = bookService.resolvePrimaryFile(book);
        Instant effectiveTime = progress != null
                ? progress.getKoreaderLastSyncTime() != null
                        ? progress.getKoreaderLastSyncTime()
                        : progress.getLastReadTime()
                : null;
        return KoreaderProgress.builder()
                .timestamp(effectiveTime != null ? effectiveTime.getEpochSecond() : null)
                .bookId(book.getId())
                .bookFileId(primaryFile != null ? primaryFile.getId() : null)
                .bookHash(bookHash)
                .document(bookHash)
                .fileFormat(primaryFile != null && primaryFile.getBookType() != null
                        ? primaryFile.getBookType().name()
                        : null)
                .percentage(progress != null
                        ? fromStoredKoreaderFraction(progress.getKoreaderProgressPercent())
                        : null)
                .progress(progress != null ? progress.getKoreaderProgress() : null)
                .updatedAt(effectiveTime)
                .device(progress != null ? progress.getKoreaderDevice() : null)
                .device_id(progress != null ? progress.getKoreaderDeviceId() : null)
                .build();
    }

    @Transactional
    public void updateProgress(KoreaderProgress request) {
        String bookHash = bookService.firstNonBlank(
                request.resolveBookHash(),
                request.getDocument(),
                request.getCurrentHash(),
                request.getInitialHash());
        if (bookHash == null) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("bookHash or document is required");
        }
        BookLoreUserEntity reader = authService.requireCurrentReader(true);
        BookEntity book = hashMatcher.resolveAccessibleBookByHash(reader, bookHash);
        UserBookProgressEntity progress = userBookProgressRepository
                .findByUserIdAndBookId(reader.getId(), book.getId())
                .orElseGet(UserBookProgressEntity::new);

        boolean isNew = progress.getId() == null;
        org.booklore.model.enums.ReadStatus previousManualStatus =
                !isNew && progress.getReadStatusModifiedTime() != null
                ? progress.getReadStatus()
                : null;
        Instant clientTime = resolveClientTime(request);
        boolean preserveManualStatus = previousManualStatus != null
                && progress.getReadStatusModifiedTime().isAfter(clientTime);
        Float normalizedPercent = resolvePercent(request);

        progress.setUser(reader);
        progress.setBook(book);
        progress.setKoreaderProgress(bookService.firstNonBlank(
                request.getRawKoreaderProgress(), request.getProgress()));
        progress.setKoreaderProgressPercent(toStoredKoreaderFraction(normalizedPercent));
        progress.setKoreaderDevice(request.getDevice());
        progress.setKoreaderDeviceId(request.getDevice_id());
        progress.setKoreaderLastSyncTime(Instant.now());
        progress.setLastReadTime(clientTime);

        if (normalizedPercent != null) {
            if (preserveManualStatus) {
                progress.setReadStatus(previousManualStatus);
            } else {
                org.booklore.model.enums.ReadStatus derived = deriveReadStatus(normalizedPercent);
                progress.setReadStatus(derived);
                if (derived == org.booklore.model.enums.ReadStatus.READ
                        && (isNew || progress.getDateFinished() == null)) {
                    progress.setDateFinished(Instant.now());
                }
            }
        }
        userBookProgressRepository.save(progress);

        if (request.getCurrentPage() != null
                || request.getTotalPages() != null
                || request.getLocation() != null) {
            persistFileProgressIfPossible(bookHash, request);
        }
    }

    private void persistFileProgressIfPossible(String bookHash, KoreaderProgress request) {
        BookLoreUserEntity reader = authService.requireCurrentReader(true);
        BookEntity book = hashMatcher.resolveAccessibleBookByHash(reader, bookHash);
        BookFileEntity file = resolveRequestedFile(book, request);
        if (file == null) {
            return;
        }
        UserBookFileProgressEntity fileProgress = userBookFileProgressRepository
                .findByUserIdAndBookFileId(reader.getId(), file.getId())
                .orElseGet(UserBookFileProgressEntity::new);
        fileProgress.setUser(reader);
        fileProgress.setBookFile(file);
        fileProgress.setPositionData(bookService.firstNonBlank(request.getProgress(), request.getLocation()));
        fileProgress.setPositionHref(request.getLocation());
        fileProgress.setProgressPercent(resolvePercent(request));
        fileProgress.setLastReadTime(resolveClientTime(request));
        userBookFileProgressRepository.save(fileProgress);
    }

    private BookFileEntity resolveRequestedFile(BookEntity book, KoreaderProgress request) {
        if (request.getBookFileId() != null) {
            return bookFileRepository.findById(request.getBookFileId())
                    .filter(file -> file.getBook() != null && file.getBook().getId().equals(book.getId()))
                    .orElse(null);
        }
        return bookService.resolvePrimaryFile(book);
    }

    static Float resolvePercent(KoreaderProgress request) {
        Float percentage = clampPercent(request.getPercentage());
        if (percentage != null) {
            return percentage;
        }

        Float progressRatio = parseProgressRatio(request.getProgress());
        if (progressRatio != null) {
            return clampPercent(progressRatio * 100.0f);
        }

        return calculatePageRatio(request.getCurrentPage(), request.getTotalPages());
    }

    private static Float parseProgressRatio(String progress) {
        if (progress == null || progress.isBlank()) {
            return null;
        }
        try {
            return Float.parseFloat(progress.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Float clampPercent(Float percentage) {
        if (percentage == null) {
            return null;
        }
        if (!Float.isFinite(percentage)) {
            return null;
        }
        return Math.max(0.0f, Math.min(100.0f, percentage));
    }

    static Float toStoredKoreaderFraction(Float percentage) {
        Float clamped = clampPercent(percentage);
        return clamped != null ? clamped / 100.0f : null;
    }

    static Float fromStoredKoreaderFraction(Float storedValue) {
        if (storedValue == null || !Float.isFinite(storedValue)) {
            return null;
        }
        // The legacy field stores a 0-1 fraction. Values above 1 may have been
        // written by early GrimmLink v1 previews, so preserve them as percent.
        return clampPercent(storedValue > 1.0f ? storedValue : storedValue * 100.0f);
    }

    static Float calculatePageRatio(Integer currentPage, Integer totalPages) {
        if (currentPage == null || totalPages == null || currentPage < 0 || totalPages <= 0) {
            return null;
        }
        return clampPercent(currentPage * 100.0f / totalPages);
    }

    private org.booklore.model.enums.ReadStatus deriveReadStatus(Float normalizedPercent) {
        if (normalizedPercent >= 99.0f) {
            return org.booklore.model.enums.ReadStatus.READ;
        }
        if (normalizedPercent >= 1.0f) {
            return org.booklore.model.enums.ReadStatus.READING;
        }
        return org.booklore.model.enums.ReadStatus.UNREAD;
    }

    static Instant resolveClientTime(KoreaderProgress request) {
        if (request.getUpdatedAt() != null) {
            return request.getUpdatedAt();
        }
        if (request.getTimestamp() != null) {
            return Instant.ofEpochSecond(request.getTimestamp());
        }
        return Instant.now();
    }
}
