package com.adityachandel.booklore.service.book;

import com.adityachandel.booklore.mapper.BookMarkMapper;
import com.adityachandel.booklore.model.dto.BookMark;
import com.adityachandel.booklore.model.dto.CreateBookMarkRequest;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.model.entity.BookMarkEntity;
import com.adityachandel.booklore.repository.BookMarkRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.UserRepository;
import com.adityachandel.booklore.config.security.service.AuthenticationService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BookMarkService {

    private final BookMarkRepository bookMarkRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final BookMarkMapper mapper;
    private final AuthenticationService authenticationService;

    @Transactional(readOnly = true)
    public List<BookMark> getBookmarksForBook(Long bookId) {
        Long userId = authenticationService.getAuthenticatedUser().getId();
        return bookMarkRepository.findByBookIdAndUserIdOrderByCreatedAtDesc(bookId, userId)
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @Transactional
    public BookMark createBookmark(CreateBookMarkRequest request) {
        Long userId = authenticationService.getAuthenticatedUser().getId();
        
        // Check for existing bookmark
        if (bookMarkRepository.existsByCfiAndBookIdAndUserId(request.getCfi(), request.getBookId(), userId)) {
            throw new IllegalArgumentException("Bookmark already exists at this location");
        }
        
        BookLoreUserEntity currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        
        BookEntity book = bookRepository.findById(request.getBookId())
                .orElseThrow(() -> new EntityNotFoundException("Book not found: " + request.getBookId()));

        BookMarkEntity entity = BookMarkEntity.builder()
                .user(currentUser)
                .book(book)
                .cfi(request.getCfi())
                .title(request.getTitle())
                .build();

        BookMarkEntity saved = bookMarkRepository.save(entity);
        return mapper.toDto(saved);
    }

    @Transactional
    public void deleteBookmark(Long bookmarkId) {
        Long userId = authenticationService.getAuthenticatedUser().getId();
        BookMarkEntity bookmark = bookMarkRepository.findByIdAndUserId(bookmarkId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Bookmark not found: " + bookmarkId));
        bookMarkRepository.delete(bookmark);
    }
}
