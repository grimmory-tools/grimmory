package org.booklore.service.kobo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.Library;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KoboRatingService {
    private final BookRepository bookRepository;
    private final UserBookProgressRepository userBookProgressRepository;

    private boolean userHasAccess(BookLoreUser user, BookEntity book) {
        if (user.getPermissions().isAdmin()) {
            return true;
        }

        if (user.getAssignedLibraries() == null) {
            return false;
        }

        Long libraryId = book.getLibrary().getId();

        return user.getAssignedLibraries().stream()
                .map(Library::getId)
                .anyMatch(libraryId::equals);
    }

    private UserBookProgressEntity getUserBookProgress(BookLoreUser user, BookEntity book) {
        return userBookProgressRepository
                .findByUserIdAndBookId(user.getId(), book.getId())
                .orElseGet(
                        () -> (
                                UserBookProgressEntity.builder()
                                    .user(BookLoreUserEntity.builder().id(user.getId()).build())
                                    .book(book)
                                    .build()
                        )
                );
    }

    @Transactional
    public ResponseEntity<?> updatePersonalRating(BookLoreUser user, long bookId, int rating) {
        if (user == null) {
            // If the user is not allowed to access the book, it doesn't exist to them.
            log.debug("User is not authenticated to rate book {}", bookId);
            throw ApiError.BOOK_NOT_FOUND.createException(bookId);
        }

        BookEntity book = bookRepository.findById(bookId)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        if (!userHasAccess(user, book)) {
            // If the user is not allowed to access the book, it doesn't exist to them.
            log.info("User {} does not have access to rate book {}", user.getId(), bookId);
            throw ApiError.BOOK_NOT_FOUND.createException(bookId);
        }

        UserBookProgressEntity progress = getUserBookProgress(user, book);

        // Kobo rating is out of 5, but personal ratings are out of 10.
        int personalRating = Math.clamp((long) rating * 2, 0, 10);
        progress.setPersonalRating(personalRating);

        userBookProgressRepository.save(progress);

        return ResponseEntity.ok().build();
    }

}
