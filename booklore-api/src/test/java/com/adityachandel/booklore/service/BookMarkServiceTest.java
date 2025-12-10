package com.adityachandel.booklore.service;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.mapper.BookMarkMapper;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.BookMark;
import com.adityachandel.booklore.model.dto.CreateBookMarkRequest;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.model.entity.BookMarkEntity;
import com.adityachandel.booklore.repository.BookMarkRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.UserRepository;
import com.adityachandel.booklore.service.book.BookMarkService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookMarkServiceTest {

    @Mock
    private BookMarkRepository bookMarkRepository;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BookMarkMapper mapper;
    @Mock
    private AuthenticationService authenticationService;

    @InjectMocks
    private BookMarkService bookMarkService;

    private final Long userId = 1L;
    private final Long bookId = 100L;
    private final Long bookmarkId = 50L;
    private BookLoreUser userDto;
    private BookLoreUserEntity userEntity;
    private BookEntity bookEntity;
    private BookMarkEntity bookmarkEntity;
    private BookMark bookmarkDto;

    @BeforeEach
    void setUp() {
        userDto = BookLoreUser.builder().id(userId).build();
        userEntity = BookLoreUserEntity.builder().id(userId).build();
        bookEntity = BookEntity.builder().id(bookId).build();
        bookmarkEntity = BookMarkEntity.builder().id(bookmarkId).user(userEntity).book(bookEntity).cfi("cfi").title("title").build();
        bookmarkDto = BookMark.builder().id(bookmarkId).bookId(bookId).cfi("cfi").title("title").build();
    }

    @Test
    void getBookmarksForBook_Success() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(userDto);
        when(bookMarkRepository.findByBookIdAndUserIdOrderByCreatedAtDesc(bookId, userId)).thenReturn(List.of(bookmarkEntity));
        when(mapper.toDto(bookmarkEntity)).thenReturn(bookmarkDto);

        List<BookMark> result = bookMarkService.getBookmarksForBook(bookId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(bookmarkId, result.get(0).getId());
        verify(bookMarkRepository).findByBookIdAndUserIdOrderByCreatedAtDesc(bookId, userId);
    }

    @Test
    void createBookmark_Success() {
        CreateBookMarkRequest request = new CreateBookMarkRequest(bookId, "new-cfi", "New Bookmark");
        
        when(authenticationService.getAuthenticatedUser()).thenReturn(userDto);
        when(userRepository.getReferenceById(userId)).thenReturn(userEntity);
        when(bookRepository.findById(bookId)).thenReturn(Optional.of(bookEntity));
        when(bookMarkRepository.save(any(BookMarkEntity.class))).thenReturn(bookmarkEntity);
        when(mapper.toDto(bookmarkEntity)).thenReturn(bookmarkDto);

        BookMark result = bookMarkService.createBookmark(request);

        assertNotNull(result);
        assertEquals(bookmarkId, result.getId());
        verify(bookMarkRepository).save(any(BookMarkEntity.class));
    }

    @Test
    void createBookmark_BookNotFound() {
        CreateBookMarkRequest request = new CreateBookMarkRequest(bookId, "new-cfi", "New Bookmark");
        
        when(authenticationService.getAuthenticatedUser()).thenReturn(userDto);
        when(userRepository.getReferenceById(userId)).thenReturn(userEntity);
        when(bookRepository.findById(bookId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> bookMarkService.createBookmark(request));
        verify(bookMarkRepository, never()).save(any());
    }

    @Test
    void deleteBookmark_Success() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(userDto);
        when(bookMarkRepository.findByIdAndUserId(bookmarkId, userId)).thenReturn(Optional.of(bookmarkEntity));

        bookMarkService.deleteBookmark(bookmarkId);

        verify(bookMarkRepository).delete(bookmarkEntity);
    }

    @Test
    void deleteBookmark_NotFound() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(userDto);
        when(bookMarkRepository.findByIdAndUserId(bookmarkId, userId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> bookMarkService.deleteBookmark(bookmarkId));
        verify(bookMarkRepository, never()).delete(any());
    }
}
