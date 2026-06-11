package org.booklore.grimmlink.service;

import lombok.RequiredArgsConstructor;
import org.booklore.exception.ApiError;
import org.booklore.mapper.BookMapper;
import org.booklore.model.dto.Book;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.enums.ReadStatus;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.booklore.service.book.BookDownloadService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GrimmlinkBookService {

    private static final Set<ReadStatus> SUPPORTED_MANUAL_READ_STATUSES = EnumSet.of(
            ReadStatus.UNREAD,
            ReadStatus.READING,
            ReadStatus.READ,
            ReadStatus.PAUSED,
            ReadStatus.ABANDONED,
            ReadStatus.RE_READING
    );

    private final GrimmlinkAuthService authService;
    private final GrimmlinkHashMatcher hashMatcher;
    private final BookRepository bookRepository;
    private final UserBookProgressRepository userBookProgressRepository;
    private final BookMapper bookMapper;
    private final BookDownloadService bookDownloadService;

    @Transactional(readOnly = true)
    public Book getBookByHash(String bookHash) {
        BookLoreUserEntity reader = authService.requireCurrentReader(true);
        return bookMapper.toBook(hashMatcher.resolveAccessibleBookByHash(reader, bookHash));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> downloadBook(Long bookId) {
        BookLoreUserEntity reader = authService.requireCurrentReader(true);
        loadAccessibleBookById(reader, bookId);
        return bookDownloadService.downloadBook(bookId);
    }

    @Transactional(readOnly = true)
    public List<String> getSupportedReadStatuses() {
        return SUPPORTED_MANUAL_READ_STATUSES.stream().map(Enum::name).toList();
    }

    @Transactional
    public Map<String, Object> updateReadStatus(Long bookId, String requestedStatus) {
        BookLoreUserEntity reader = authService.requireCurrentReader(true);
        BookEntity book = loadAccessibleBookById(reader, bookId);
        ReadStatus readStatus = normalizeReadStatus(requestedStatus);
        UserBookProgressEntity progress = userBookProgressRepository
                .findByUserIdAndBookId(reader.getId(), book.getId())
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

    BookEntity loadAccessibleBookById(BookLoreUserEntity reader, Long bookId) {
        BookEntity book = bookRepository.findByIdWithBookFiles(bookId)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        if (!canAccessBook(reader, book)) {
            throw ApiError.FORBIDDEN.createException("Book is not accessible to the authenticated user");
        }
        return book;
    }

    boolean canAccessBook(BookLoreUserEntity reader, BookEntity book) {
        return isAdmin(reader)
                || reader.getLibraries().stream()
                .anyMatch(library -> library.getId().equals(book.getLibrary().getId()));
    }

    boolean isAdmin(BookLoreUserEntity reader) {
        return reader.getPermissions() != null && reader.getPermissions().isPermissionAdmin();
    }

    BookFileEntity resolvePrimaryFile(BookEntity book) {
        return book != null ? book.getPrimaryBookFile() : null;
    }

    String resolveHash(BookFileEntity file) {
        if (file == null) {
            return null;
        }
        return firstNonBlank(file.getCurrentHash(), file.getInitialHash());
    }

    String firstNonBlank(String... values) {
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ReadStatus normalizeReadStatus(String requestedStatus) {
        if (trimToNull(requestedStatus) == null) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("status is required");
        }
        if ("ON_HOLD".equalsIgnoreCase(requestedStatus.trim())) {
            return ReadStatus.PAUSED;
        }
        try {
            ReadStatus status = ReadStatus.valueOf(
                    requestedStatus.trim().toUpperCase(Locale.ROOT));
            if (!SUPPORTED_MANUAL_READ_STATUSES.contains(status)) {
                throw ApiError.GENERIC_BAD_REQUEST.createException(
                        "Unsupported status: " + requestedStatus);
            }
            return status;
        } catch (IllegalArgumentException e) {
            throw ApiError.GENERIC_BAD_REQUEST.createException(
                    "Unsupported status: " + requestedStatus);
        }
    }
}
