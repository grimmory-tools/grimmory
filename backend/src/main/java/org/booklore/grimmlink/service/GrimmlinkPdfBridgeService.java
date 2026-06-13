package org.booklore.grimmlink.service;

import lombok.RequiredArgsConstructor;
import org.booklore.model.dto.progress.KoreaderProgress;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.UserBookFileProgressEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookFileRepository;
import org.booklore.repository.UserBookFileProgressRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class GrimmlinkPdfBridgeService {

    private static final String WEB_READER_DEVICE = "WEB_READER";
    private static final String WEB_READER_DEVICE_ID = "web-reader";

    private final GrimmlinkAuthService authService;
    private final GrimmlinkBookService bookService;
    private final BookFileRepository bookFileRepository;
    private final UserBookProgressRepository userBookProgressRepository;
    private final UserBookFileProgressRepository userBookFileProgressRepository;

    @Transactional(readOnly = true)
    public KoreaderProgress getPdfProgress(Long bookId) {
        BookLoreUserEntity reader = authService.requireCurrentReader(true);
        BookEntity book = bookService.loadAccessibleBookById(reader, bookId);
        UserBookProgressEntity progress = userBookProgressRepository
                .findByUserIdAndBookId(reader.getId(), book.getId())
                .orElse(null);
        BookFileEntity file = bookService.resolvePrimaryFile(book);
        return KoreaderProgress.builder()
                .bookId(book.getId())
                .bookFileId(file != null ? file.getId() : null)
                .bookHash(bookService.resolveHash(file))
                .document(bookService.resolveHash(file))
                .fileFormat(file != null && file.getBookType() != null ? file.getBookType().name() : null)
                .currentPage(progress != null ? progress.getPdfProgress() : null)
                .percentage(progress != null ? progress.getPdfProgressPercent() : null)
                .progress(progress != null && progress.getPdfProgress() != null
                        ? String.valueOf(progress.getPdfProgress())
                        : null)
                .updatedAt(progress != null ? progress.getLastReadTime() : null)
                .device("BookLore")
                .device_id("BookLore")
                .build();
    }

    @Transactional
    public KoreaderProgress updatePdfProgress(Long bookId, KoreaderProgress request) {
        BookLoreUserEntity reader = authService.requireCurrentReader(true);
        BookEntity book = bookService.loadAccessibleBookById(reader, bookId);
        BookFileEntity file = resolveRequestedFile(book, request);

        if (file != null && file.getBookType() != null && file.getBookType() != BookFileType.PDF) {
            KoreaderProgress rejected = getPdfProgress(bookId);
            rejected.setConflictDetected(false);
            rejected.setUpdated(false);
            rejected.setConversionStatus("unsupported_format");
            rejected.setMessage("PDF bridge only supports PDF format, got " + file.getBookType().name());
            return rejected;
        }

        UserBookProgressEntity existing = userBookProgressRepository
                .findByUserIdAndBookId(reader.getId(), book.getId())
                .orElse(null);
        if (existing != null && existing.getLastReadTime() != null && request.getExpectedUpdatedAt() != null) {
            Instant expected = Instant.ofEpochMilli(request.getExpectedUpdatedAt());
            boolean isConflict = Math.abs(
                    expected.toEpochMilli() - existing.getLastReadTime().toEpochMilli()) > 1000;
            if (isConflict && !Boolean.TRUE.equals(request.getForce())) {
                KoreaderProgress current = getPdfProgress(bookId);
                current.setConflictDetected(true);
                current.setUpdated(false);
                current.setConversionStatus("remote_newer");
                current.setMessage(
                        "Timestamp conflict: server lastReadTime differs from client expectedUpdatedAt");
                return current;
            }
        }

        UserBookProgressEntity progress = existing != null ? existing : new UserBookProgressEntity();
        Float resolvedPercent = GrimmlinkProgressService.resolvePercent(request);
        Instant lastReadTime = GrimmlinkProgressService.resolveClientTime(request);
        progress.setUser(reader);
        progress.setBook(book);
        progress.setPdfProgress(request.getCurrentPage());
        progress.setPdfProgressPercent(resolvedPercent);
        progress.setKoreaderProgress(bookService.firstNonBlank(
                request.getRawKoreaderProgress(),
                request.getProgress(),
                request.getCurrentPage() != null ? String.valueOf(request.getCurrentPage()) : null));
        progress.setKoreaderProgressPercent(
                GrimmlinkProgressService.toStoredKoreaderFraction(resolvedPercent));
        progress.setKoreaderDevice(WEB_READER_DEVICE);
        progress.setKoreaderDeviceId(WEB_READER_DEVICE_ID);
        progress.setKoreaderLastSyncTime(Instant.now());
        progress.setLastReadTime(lastReadTime);
        userBookProgressRepository.save(progress);

        if (file != null) {
            UserBookFileProgressEntity fileProgress = userBookFileProgressRepository
                    .findByUserIdAndBookFileId(reader.getId(), file.getId())
                    .orElseGet(UserBookFileProgressEntity::new);
            fileProgress.setUser(reader);
            fileProgress.setBookFile(file);
            fileProgress.setPositionData(request.getCurrentPage() != null
                    ? String.valueOf(request.getCurrentPage())
                    : request.getProgress());
            fileProgress.setPositionHref(bookService.firstNonBlank(
                    request.getRawKoreaderLocation(), request.getLocation()));
            fileProgress.setProgressPercent(resolvedPercent);
            fileProgress.setLastReadTime(lastReadTime);
            userBookFileProgressRepository.save(fileProgress);
        }

        KoreaderProgress result = getPdfProgress(bookId);
        result.setConflictDetected(false);
        result.setUpdated(true);
        result.setConversionStatus("ok");
        return result;
    }

    private BookFileEntity resolveRequestedFile(BookEntity book, KoreaderProgress request) {
        if (request.getBookFileId() != null) {
            return bookFileRepository.findById(request.getBookFileId())
                    .filter(file -> file.getBook() != null && file.getBook().getId().equals(book.getId()))
                    .orElse(null);
        }
        return bookService.resolvePrimaryFile(book);
    }
}
