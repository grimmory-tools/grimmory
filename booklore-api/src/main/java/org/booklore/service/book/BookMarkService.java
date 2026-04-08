package org.booklore.service.book;

import org.booklore.config.BookmarkProperties;
import org.booklore.mapper.BookMarkMapper;
import org.booklore.model.dto.BookMark;
import org.booklore.model.dto.CreateBookMarkRequest;
import org.booklore.model.dto.UpdateBookMarkRequest;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.BookMarkEntity;
import org.booklore.repository.BookMarkRepository;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserRepository;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookMarkService {

    private final BookMarkRepository bookMarkRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final AuthenticationService authenticationService;
    private final BookMarkMapper mapper;
    private final BookmarkProperties bookmarkProperties;

    @Transactional(readOnly = true)
    public List<BookMark> getBookmarksForBook(Long bookId) {
        Long userId = getCurrentUserId();
        return bookMarkRepository.findByBookIdAndUserIdOrderByPriorityAscCreatedAtDesc(bookId, userId)
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public BookMark getBookmarkById(Long bookmarkId) {
        return mapper.toDto(findBookmarkByIdAndUser(bookmarkId));
    }

    @Transactional
    public BookMark createBookmark(CreateBookMarkRequest request) {
        Long userId = getCurrentUserId();

        // Validate no duplicate based on bookmark type
        if (request.isAudiobookBookmark()) {
            validateNoDuplicateAudiobookBookmark(request.getPositionMs(), request.getTrackIndex(), request.getBookId(), userId);
        } else if (request.isPdfBookmark()) {
            validateNoDuplicatePdfBookmark(request.getPageNumber(), request.getBookId(), userId);
        } else if (request.getCfi() != null) {
            validateNoDuplicateBookmark(request.getCfi(), request.getBookId(), userId);
        }

        BookMarkEntity bookmark = BookMarkEntity.builder()
                .cfi(request.getCfi())
                .positionMs(request.getPositionMs())
                .trackIndex(request.getTrackIndex())
                .pageNumber(request.getPageNumber())
                .title(request.getTitle())
                .book(findBook(request.getBookId()))
                .user(findUser(userId))
                .priority(bookmarkProperties.getDefaultPriority())
                .build();

        log.info("Creating bookmark for book {} by user {}", request.getBookId(), userId);
        return mapper.toDto(bookMarkRepository.save(bookmark));
    }

    @Transactional
    public BookMark updateBookmark(Long bookmarkId, UpdateBookMarkRequest request) {
        BookMarkEntity bookmark = findBookmarkByIdAndUser(bookmarkId);

        // Validate CFI uniqueness if CFI is being updated
        if (request.getCfi() != null) {
            validateNoDuplicateBookmark(request.getCfi(), bookmark.getBookId(), bookmark.getUserId(), bookmarkId);
        }

        // Validate page-number uniqueness if page number is being updated
        if (request.getPageNumber() != null) {
            validateNoDuplicatePdfBookmark(request.getPageNumber(), bookmark.getBookId(), bookmark.getUserId(), bookmarkId);
        }

        applyUpdates(bookmark, request);

        log.info("Updating bookmark {}", bookmarkId);
        return mapper.toDto(bookMarkRepository.save(bookmark));
    }

    @Transactional
    public void deleteBookmark(Long bookmarkId) {
        BookMarkEntity bookmark = findBookmarkByIdAndUser(bookmarkId);
        log.info("Deleting bookmark {}", bookmarkId);
        bookMarkRepository.delete(bookmark);
    }

    private Long getCurrentUserId() {
        return authenticationService.getAuthenticatedUser().getId();
    }

    private BookMarkEntity findBookmarkByIdAndUser(Long bookmarkId) {
        Long userId = getCurrentUserId();
        return bookMarkRepository.findByIdAndUserId(bookmarkId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Bookmark not found: " + bookmarkId));
    }

    private BookEntity findBook(Long bookId) {
        return bookRepository.findById(bookId)
                .orElseThrow(() -> new EntityNotFoundException("Book not found: " + bookId));
    }

    private BookLoreUserEntity findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
    }

    private void validateNoDuplicateBookmark(String cfi, Long bookId, Long userId) {
        validateNoDuplicateBookmark(cfi, bookId, userId, null);
    }

    /**
     * Priority: 1 (highest/most important) to 5 (lowest/least important).
     * Bookmarks are sorted by priority ascending (1 first), then by creation date descending.
     */
    private void validateNoDuplicateBookmark(String cfi, Long bookId, Long userId, Long excludeBookmarkId) {
        boolean exists = (excludeBookmarkId == null)
                ? bookMarkRepository.existsByCfiAndBookIdAndUserId(cfi, bookId, userId)
                : bookMarkRepository.existsByCfiAndBookIdAndUserIdExcludeId(cfi, bookId, userId, excludeBookmarkId);

        if (exists) {
            throw new APIException("Bookmark already exists at this location", HttpStatus.CONFLICT);
        }
    }

    /**
     * Validate no duplicate audiobook bookmark exists within 5 seconds of the position.
     */
    private void validateNoDuplicateAudiobookBookmark(Long positionMs, Integer trackIndex, Long bookId, Long userId) {
        boolean exists = bookMarkRepository.existsByPositionMsNearAndBookIdAndUserId(positionMs, trackIndex, bookId, userId);
        if (exists) {
            throw new APIException("A bookmark already exists near this position", HttpStatus.CONFLICT);
        }
    }

    /**
     * Validate no duplicate PDF bookmark exists for the exact page number.
     */
    private void validateNoDuplicatePdfBookmark(Integer pageNumber, Long bookId, Long userId) {
        validateNoDuplicatePdfBookmark(pageNumber, bookId, userId, null);
    }

    private void validateNoDuplicatePdfBookmark(Integer pageNumber, Long bookId, Long userId, Long excludeBookmarkId) {
        boolean exists = (excludeBookmarkId == null)
                ? bookMarkRepository.existsByPageNumberAndBookIdAndUserId(pageNumber, bookId, userId)
                : bookMarkRepository.existsByPageNumberAndBookIdAndUserIdExcludeId(pageNumber, bookId, userId, excludeBookmarkId);

        if (exists) {
            throw new APIException("A bookmark already exists on this page", HttpStatus.CONFLICT);
        }
    }

    private void applyUpdates(BookMarkEntity bookmark, UpdateBookMarkRequest request) {
        Optional.ofNullable(request.getTitle()).ifPresent(bookmark::setTitle);
        Optional.ofNullable(request.getCfi()).ifPresent(bookmark::setCfi);
        Optional.ofNullable(request.getColor()).ifPresent(bookmark::setColor);
        Optional.ofNullable(request.getNotes()).ifPresent(bookmark::setNotes);
        Optional.ofNullable(request.getPriority()).ifPresent(bookmark::setPriority);
        Optional.ofNullable(request.getPageNumber()).ifPresent(bookmark::setPageNumber);
    }
}
