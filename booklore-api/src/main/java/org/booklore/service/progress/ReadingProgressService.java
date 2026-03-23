package org.booklore.service.progress;

import
        org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.*;
import org.booklore.model.dto.progress.AudiobookProgress;
import org.booklore.model.dto.progress.CbxProgress;
import org.booklore.model.dto.progress.EpubProgress;
import org.booklore.model.dto.progress.KoboProgress;
import org.booklore.model.dto.progress.KoProgress;
import org.booklore.model.dto.progress.PdfProgress;
import org.booklore.model.dto.request.BookFileProgress;
import org.booklore.model.dto.request.ReadProgressRequest;
import org.booklore.model.dto.response.BookStatusUpdateResponse;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.ReadStatus;
import org.booklore.model.enums.ResetProgressType;
import org.booklore.model.enums.UserPermission;
import org.booklore.repository.*;
import org.booklore.service.hardcover.HardcoverSyncService;
import org.booklore.service.kobo.KoboReadingStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class ReadingProgressService {

    private static final float READING_THRESHOLD = 0.1f;
    private static final float COMPLETED_THRESHOLD = 99.5f;

    private final UserBookProgressRepository userBookProgressRepository;
    private final UserBookFileProgressRepository userBookFileProgressRepository;
    private final BookRepository bookRepository;
    private final BookFileRepository bookFileRepository;
    private final UserRepository userRepository;
    private final AuthenticationService authenticationService;
    private final KoboReadingStateService koboReadingStateService;
    private final HardcoverSyncService hardcoverSyncService;

    // ==================== Methods from UserProgressService ====================

    public Map<Long, UserBookProgressEntity> fetchUserProgress(Long userId, Set<Long> bookIds) {
        return userBookProgressRepository.findByUserIdAndBookIdIn(userId, bookIds).stream()
                .collect(Collectors.toMap(p -> p.getBook().getId(), p -> p));
    }

    public Map<Long, UserBookFileProgressEntity> fetchUserFileProgress(Long userId, Set<Long> bookIds) {
        if (bookIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<UserBookFileProgressEntity> fileProgressList =
                userBookFileProgressRepository.findByUserIdAndBookFileBookIdIn(userId, bookIds);

        return fileProgressList.stream()
                .collect(Collectors.toMap(
                        p -> p.getBookFile().getBook().getId(),
                        p -> p,
                        (existing, replacement) -> {
                            if (existing.getLastReadTime() == null) return replacement;
                            if (replacement.getLastReadTime() == null) return existing;
                            return replacement.getLastReadTime().isAfter(existing.getLastReadTime())
                                    ? replacement : existing;
                        }
                ));
    }

    public Optional<UserBookFileProgressEntity> fetchUserFileProgressForFile(Long userId, Long bookFileId) {
        return userBookFileProgressRepository.findByUserIdAndBookFileId(userId, bookFileId);
    }

    // ==================== Methods from BookProgressUtil ====================

    public void enrichBookWithProgress(Book book, UserBookProgressEntity progress) {
        enrichBookWithProgress(book, progress, null);
    }

    public void enrichBookWithProgress(Book book, UserBookProgressEntity progress,
                                        UserBookFileProgressEntity fileProgress) {
        if (progress != null) {
            book.setReadStatus(progress.getReadStatus() == null ?
                    String.valueOf(ReadStatus.UNSET) : String.valueOf(progress.getReadStatus()));
            book.setDateFinished(progress.getDateFinished());
            book.setPersonalRating(progress.getPersonalRating());

            setBookProgress(book, progress);
            book.setLastReadTime(progress.getLastReadTime());
        }

        if (fileProgress != null) {
            setBookProgressFromFileProgress(book, fileProgress);
            if (progress == null || fileProgress.getLastReadTime() != null &&
                    (progress.getLastReadTime() == null ||
                     fileProgress.getLastReadTime().isAfter(progress.getLastReadTime()))) {
                book.setLastReadTime(fileProgress.getLastReadTime());
            }
        }
    }

    private void setBookProgress(Book book, UserBookProgressEntity progress) {
        if (progress.getKoboProgressPercent() != null) {
            book.setKoboProgress(KoboProgress.builder()
                    .percentage(roundToOneDecimal(progress.getKoboProgressPercent()))
                    .build());
        }

        if (progress.getKoreaderProgressPercent() != null) {
            book.setKoreaderProgress(KoProgress.builder()
                    .percentage(roundToOneDecimal(progress.getKoreaderProgressPercent() * 100))
                    .build());
        }

        if (progress.getEpubProgress() != null || progress.getEpubProgressPercent() != null) {
            book.setEpubProgress(EpubProgress.builder()
                    .cfi(progress.getEpubProgress())
                    .href(progress.getEpubProgressHref())
                    .percentage(roundToOneDecimal(progress.getEpubProgressPercent()))
                    .build());
        }

        if (progress.getPdfProgress() != null || progress.getPdfProgressPercent() != null) {
            book.setPdfProgress(PdfProgress.builder()
                    .page(progress.getPdfProgress())
                    .percentage(roundToOneDecimal(progress.getPdfProgressPercent()))
                    .build());
        }

        if (progress.getCbxProgress() != null || progress.getCbxProgressPercent() != null) {
            book.setCbxProgress(CbxProgress.builder()
                    .page(progress.getCbxProgress())
                    .percentage(roundToOneDecimal(progress.getCbxProgressPercent()))
                    .build());
        }
    }

    private void setBookProgressFromFileProgress(Book book, UserBookFileProgressEntity fileProgress) {
        BookFileType type = fileProgress.getBookFile() != null ? fileProgress.getBookFile().getBookType() : null;
        if (type == null) return;

        switch (type) {
            case EPUB, FB2, MOBI, AZW3 -> book.setEpubProgress(EpubProgress.builder()
                    .cfi(fileProgress.getPositionData())
                    .href(fileProgress.getPositionHref())
                    .percentage(roundToOneDecimal(fileProgress.getProgressPercent()))
                    .ttsPositionCfi(fileProgress.getTtsPositionCfi())
                    .build());
            case PDF -> book.setPdfProgress(PdfProgress.builder()
                    .page(parseIntOrNull(fileProgress.getPositionData()))
                    .percentage(roundToOneDecimal(fileProgress.getProgressPercent()))
                    .build());
            case CBX -> book.setCbxProgress(CbxProgress.builder()
                    .page(parseIntOrNull(fileProgress.getPositionData()))
                    .percentage(roundToOneDecimal(fileProgress.getProgressPercent()))
                    .build());
            case AUDIOBOOK -> book.setAudiobookProgress(AudiobookProgress.builder()
                    .positionMs(parseLongOrNull(fileProgress.getPositionData()))
                    .trackIndex(parseIntOrNull(fileProgress.getPositionHref()))
                    .percentage(roundToOneDecimal(fileProgress.getProgressPercent()))
                    .build());
        }
    }

    private Integer parseIntOrNull(String value) {
        if (value == null) return null;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseLongOrNull(String value) {
        if (value == null) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Float roundToOneDecimal(Float value) {
        return value != null ? Math.round(value * 10f) / 10f : null;
    }

    // ==================== Methods from BookUpdateService ====================

    @Transactional
    public void updateReadProgress(ReadProgressRequest request) {
        BookEntity book = bookRepository.findByIdWithBookFiles(request.getBookId())
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(request.getBookId()));
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        BookLoreUserEntity userEntity = findUserOrThrow(user.getId());
        Instant now = Instant.now();

        UserBookProgressEntity progress = userBookProgressRepository
                .findByUserIdAndBookId(user.getId(), book.getId())
                .orElseGet(UserBookProgressEntity::new);

        progress.setUser(userEntity);
        progress.setBook(book);
        progress.setLastReadTime(now);

        Float percentage = null;

        boolean hasProgressData = request.getFileProgress() != null
                || request.getEpubProgress() != null
                || request.getPdfProgress() != null
                || request.getCbxProgress() != null
                || request.getAudiobookProgress() != null;

        if (hasProgressData) {
            if (request.getFileProgress() != null) {
                BookFileProgress fileProgress = request.getFileProgress();
                percentage = fileProgress.progressPercent();

                saveToUserBookFileProgress(userEntity, fileProgress, now);

                BookFileEntity bookFile = bookFileRepository.findById(fileProgress.bookFileId())
                        .orElseThrow(() -> ApiError.GENERIC_NOT_FOUND.createException("Book file not found"));
                updateProgressFromFileProgress(progress, bookFile.getBookType(), fileProgress);
            } else {
                BookFileEntity primaryFile = book.getPrimaryBookFile();
                if (primaryFile == null) {
                    throw ApiError.UNSUPPORTED_BOOK_TYPE.createException();
                }
                percentage = updateProgressByBookType(progress, primaryFile.getBookType(), request);

                if (percentage != null) {
                    saveToUserBookFileProgressFromLegacy(userEntity, primaryFile, progress, now);
                }
            }

            if (percentage != null) {
                progress.setReadStatus(calculateReadStatus(percentage, progress.getReadStatus()));
                BookFileEntity primaryFile = book.getPrimaryBookFile();
                if (primaryFile != null) {
                    setProgressPercent(progress, primaryFile.getBookType(), percentage);
                }
            }
        }

        if (percentage != null) {
            ReadStatus newStatus = calculateReadStatus(percentage, progress.getReadStatus());
            progress.setReadStatus(newStatus);
            BookFileEntity primaryFile = book.getPrimaryBookFile();
            if (primaryFile != null) {
                setProgressPercent(progress, primaryFile.getBookType(), percentage);
            }
            // Auto-set dateFinished when the book transitions to READ and no date is set yet
            if (newStatus == ReadStatus.READ && progress.getDateFinished() == null) {
                progress.setDateFinished(now);
            }
        }
        if (request.getDateFinished() != null) {
            progress.setDateFinished(request.getDateFinished());
        }

        userBookProgressRepository.save(progress);

        if (percentage != null) {
            hardcoverSyncService.syncProgressToHardcover(book.getId(), percentage, user.getId());
        }
    }

    @Transactional
    public List<BookStatusUpdateResponse> resetProgress(List<Long> bookIds, ResetProgressType type) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        validateResetPermission(bookIds, user, type);

        Set<Long> existingProgressBookIds = validateBooksAndGetExistingProgress(user.getId(), bookIds);
        Instant now = Instant.now();

        if (!existingProgressBookIds.isEmpty()) {
            performReset(user.getId(), existingProgressBookIds, type, now);
        }

        return buildResetResponses(bookIds, existingProgressBookIds, now);
    }

    private void saveToUserBookFileProgress(BookLoreUserEntity user, BookFileProgress fileProgress, Instant now) {
        UserBookFileProgressEntity entity = userBookFileProgressRepository
                .findByUserIdAndBookFileId(user.getId(), fileProgress.bookFileId())
                .orElseGet(UserBookFileProgressEntity::new);

        BookFileEntity bookFile = bookFileRepository.findById(fileProgress.bookFileId())
                .orElseThrow(() -> ApiError.GENERIC_NOT_FOUND.createException("Book file not found"));

        entity.setUser(user);
        entity.setBookFile(bookFile);
        entity.setPositionData(fileProgress.positionData());
        entity.setPositionHref(fileProgress.positionHref());
        entity.setProgressPercent(fileProgress.progressPercent());
        entity.setTtsPositionCfi(fileProgress.ttsPositionCfi());
        entity.setLastReadTime(now);

        userBookFileProgressRepository.save(entity);
    }

    private void saveToUserBookFileProgressFromLegacy(BookLoreUserEntity user, BookFileEntity bookFile,
                                                       UserBookProgressEntity progress, Instant now) {
        UserBookFileProgressEntity entity = userBookFileProgressRepository
                .findByUserIdAndBookFileId(user.getId(), bookFile.getId())
                .orElseGet(UserBookFileProgressEntity::new);

        entity.setUser(user);
        entity.setBookFile(bookFile);
        entity.setLastReadTime(now);

        switch (bookFile.getBookType()) {
            case PDF -> {
                entity.setPositionData(progress.getPdfProgress() != null ?
                        String.valueOf(progress.getPdfProgress()) : null);
                entity.setProgressPercent(progress.getPdfProgressPercent());
            }
            case EPUB, FB2, MOBI, AZW3 -> {
                entity.setPositionData(progress.getEpubProgress());
                entity.setPositionHref(progress.getEpubProgressHref());
                entity.setProgressPercent(progress.getEpubProgressPercent());
            }
            case CBX -> {
                entity.setPositionData(progress.getCbxProgress() != null ?
                        String.valueOf(progress.getCbxProgress()) : null);
                entity.setProgressPercent(progress.getCbxProgressPercent());
            }
            case AUDIOBOOK -> {
                // Audiobook progress is handled via new file-level progress system
                // No legacy book-level progress columns exist for audiobooks
            }
        }

        userBookFileProgressRepository.save(entity);
    }

    private void updateProgressFromFileProgress(UserBookProgressEntity progress, BookFileType bookType,
                                                 BookFileProgress fileProgress) {
        switch (bookType) {
            case PDF -> {
                progress.setPdfProgress(fileProgress.positionData() != null ?
                        Integer.parseInt(fileProgress.positionData()) : null);
                progress.setPdfProgressPercent(fileProgress.progressPercent());
            }
            case EPUB, FB2, MOBI, AZW3 -> {
                progress.setEpubProgress(fileProgress.positionData());
                progress.setEpubProgressHref(fileProgress.positionHref());
                progress.setEpubProgressPercent(fileProgress.progressPercent());
            }
            case CBX -> {
                progress.setCbxProgress(fileProgress.positionData() != null ?
                        Integer.parseInt(fileProgress.positionData()) : null);
                progress.setCbxProgressPercent(fileProgress.progressPercent());
            }
            case AUDIOBOOK -> {
                // Audiobook progress is stored only in UserBookFileProgressEntity
                // No legacy columns in UserBookProgressEntity for audiobooks
            }
        }
    }

    private Float updateProgressByBookType(UserBookProgressEntity progress, BookFileType bookType, ReadProgressRequest request) {
        return switch (bookType) {
            case EPUB, FB2, MOBI, AZW3 -> updateEbookProgress(progress, request.getEpubProgress());
            case PDF -> updatePdfProgress(progress, request.getPdfProgress());
            case CBX -> updateCbxProgress(progress, request.getCbxProgress());
            case AUDIOBOOK -> updateAudiobookProgress(request.getAudiobookProgress());
        };
    }

    private Float updateEbookProgress(UserBookProgressEntity progress, EpubProgress epubProgress) {
        if (epubProgress == null) return null;

        progress.setEpubProgress(epubProgress.getCfi());
        progress.setEpubProgressHref(epubProgress.getHref());

        float percentage = epubProgress.getPercentage();
        return Math.round(percentage * 10f) / 10f;
    }

    private Float updatePdfProgress(UserBookProgressEntity progress, PdfProgress pdfProgress) {
        if (pdfProgress == null) return null;

        progress.setPdfProgress(pdfProgress.getPage());
        float percentage = pdfProgress.getPercentage();
        return Math.round(percentage * 10f) / 10f;
    }

    private Float updateCbxProgress(UserBookProgressEntity progress, CbxProgress cbxProgress) {
        if (cbxProgress == null) return null;

        progress.setCbxProgress(cbxProgress.getPage());
        float percentage = cbxProgress.getPercentage();
        return Math.round(percentage * 10f) / 10f;
    }

    private Float updateAudiobookProgress(AudiobookProgress audiobookProgress) {
        if (audiobookProgress == null) return null;

        // Audiobook progress is stored in file-level progress (UserBookFileProgressEntity)
        // Legacy book-level progress entity doesn't have audiobook-specific columns
        float percentage = audiobookProgress.getPercentage();
        return Math.round(percentage * 10f) / 10f;
    }

    private void setProgressPercent(UserBookProgressEntity progress, BookFileType type, Float percentage) {
        switch (type) {
            case EPUB, FB2, MOBI, AZW3 -> progress.setEpubProgressPercent(percentage);
            case PDF -> progress.setPdfProgressPercent(percentage);
            case CBX -> progress.setCbxProgressPercent(percentage);
            case AUDIOBOOK -> {
                // Audiobook progress percentage is stored in UserBookFileProgressEntity
                // No legacy column exists in UserBookProgressEntity for audiobooks
            }
        }
    }

    private ReadStatus calculateReadStatus(Float percentage, ReadStatus currentStatus) {
        ReadStatus newStatus;
        if (percentage >= COMPLETED_THRESHOLD) {
            newStatus = ReadStatus.READ;
        } else if (percentage > READING_THRESHOLD) {
            newStatus = ReadStatus.READING;
        } else {
            newStatus = ReadStatus.UNREAD;
        }

        // Only allow automatic status changes that represent progress upgrades
        // Don't downgrade from manually set or higher progress statuses
        if (newStatus == ReadStatus.UNREAD) {
            // Preserve any status that indicates the user has engaged with the book
            if (currentStatus == ReadStatus.READING ||
                currentStatus == ReadStatus.RE_READING ||
                currentStatus == ReadStatus.READ ||
                currentStatus == ReadStatus.PARTIALLY_READ ||
                currentStatus == ReadStatus.PAUSED ||
                currentStatus == ReadStatus.ABANDONED ||
                currentStatus == ReadStatus.WONT_READ) {
                return currentStatus;
            }
        }

        return newStatus;
    }

    private void performReset(Long userId, Set<Long> bookIds, ResetProgressType type, Instant now) {
        List<Long> bookIdList = new ArrayList<>(bookIds);

        switch (type) {
            case BOOKLORE -> {
                userBookProgressRepository.bulkResetBookloreProgress(userId, bookIdList, now);
                userBookFileProgressRepository.deleteByUserIdAndBookIds(userId, bookIdList);
            }
            case KOREADER -> userBookProgressRepository.bulkResetKoreaderProgress(userId, bookIdList);
            case KOBO -> {
                userBookProgressRepository.bulkResetKoboProgress(userId, bookIdList);
                bookIds.forEach(koboReadingStateService::deleteReadingState);
            }
        }
    }

    private void validateResetPermission(List<Long> bookIds, BookLoreUser user, ResetProgressType type) {
        if (bookIds.size() <= 1) return;
        UserPermission permission = switch (type) {
            case BOOKLORE -> UserPermission.CAN_BULK_RESET_BOOKLORE_READ_PROGRESS;
            case KOREADER -> UserPermission.CAN_BULK_RESET_KOREADER_READ_PROGRESS;
            default -> null;
        };
        if (permission != null && !permission.isGranted(user.getPermissions())) {
            throw ApiError.PERMISSION_DENIED.createException(permission);
        }
    }

    private Set<Long> validateBooksAndGetExistingProgress(Long userId, List<Long> bookIds) {
        long existingBooksCount = bookRepository.countByIdIn(bookIds);
        if (existingBooksCount != bookIds.size()) {
            throw ApiError.BOOK_NOT_FOUND.createException("One or more books not found");
        }

        return userBookProgressRepository.findExistingProgressBookIds(userId, new HashSet<>(bookIds));
    }

    private List<BookStatusUpdateResponse> buildResetResponses(List<Long> bookIds, Set<Long> existingBookIds, Instant now) {
        return bookIds.stream()
                .map(bookId -> BookStatusUpdateResponse.builder()
                        .bookId(bookId)
                        .readStatus(null)
                        .readStatusModifiedTime(existingBookIds.contains(bookId) ? now : null)
                        .dateFinished(null)
                        .build())
                .collect(Collectors.toList());
    }

    private BookLoreUserEntity findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with ID: " + userId));
    }
}
