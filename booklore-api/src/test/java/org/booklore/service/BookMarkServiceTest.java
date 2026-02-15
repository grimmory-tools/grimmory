package org.booklore.service;

import org.booklore.config.BookmarkProperties;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
import org.booklore.mapper.BookMarkMapper;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.BookMark;
import org.booklore.model.dto.CreateBookMarkRequest;
import org.booklore.model.dto.UpdateBookMarkRequest;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.BookMarkEntity;
import org.booklore.repository.BookMarkRepository;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserRepository;
import org.booklore.service.book.BookMarkService;
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
    @Mock
    private BookmarkProperties bookmarkProperties;

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
        userDto = BookLoreUser.builder().id(userId).isDefaultPassword(false).build();
        userEntity = BookLoreUserEntity.builder().id(userId).isDefaultPassword(false).build();
        bookEntity = BookEntity.builder().id(bookId).build();
        bookmarkEntity = BookMarkEntity.builder().id(bookmarkId).user(userEntity).book(bookEntity).cfi("cfi").title("title").version(1L).build();
        bookmarkDto = BookMark.builder().id(bookmarkId).bookId(bookId).cfi("cfi").title("title").build();
    }

    @Test
    void getBookmarksForBook_Success() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(userDto);
        when(bookMarkRepository.findByBookIdAndUserIdOrderByPriorityAscCreatedAtDesc(bookId, userId)).thenReturn(List.of(bookmarkEntity));
        when(mapper.toDto(bookmarkEntity)).thenReturn(bookmarkDto);

        List<BookMark> result = bookMarkService.getBookmarksForBook(bookId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(bookmarkId, result.getFirst().getId());
        verify(bookMarkRepository).findByBookIdAndUserIdOrderByPriorityAscCreatedAtDesc(bookId, userId);
    }

    @Test
    void createBookmark_Success() {
        CreateBookMarkRequest request = CreateBookMarkRequest.builder()
                .bookId(bookId)
                .cfi("new-cfi")
                .title("New Bookmark")
                .build();

        when(authenticationService.getAuthenticatedUser()).thenReturn(userDto);
        when(bookmarkProperties.getDefaultPriority()).thenReturn(3);
        when(bookMarkRepository.existsByCfiAndBookIdAndUserId("new-cfi", bookId, userId)).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(userEntity));
        when(bookRepository.findById(bookId)).thenReturn(Optional.of(bookEntity));
        when(bookMarkRepository.save(any(BookMarkEntity.class))).thenReturn(bookmarkEntity);
        when(mapper.toDto(bookmarkEntity)).thenReturn(bookmarkDto);

        BookMark result = bookMarkService.createBookmark(request);

        assertNotNull(result);
        assertEquals(bookmarkId, result.getId());
        verify(bookMarkRepository).save(any(BookMarkEntity.class));
    }

    @Test
    void createBookmark_Duplicate() {
        CreateBookMarkRequest request = CreateBookMarkRequest.builder()
                .bookId(bookId)
                .cfi("new-cfi")
                .title("New Bookmark")
                .build();

        when(authenticationService.getAuthenticatedUser()).thenReturn(userDto);
        when(bookMarkRepository.existsByCfiAndBookIdAndUserId("new-cfi", bookId, userId)).thenReturn(true); // Duplicate exists

        assertThrows(APIException.class, () -> bookMarkService.createBookmark(request));
        verify(bookMarkRepository, never()).save(any());
    }

    @Test
    void createBookmark_BookNotFound() {
        CreateBookMarkRequest request = CreateBookMarkRequest.builder()
                .bookId(bookId)
                .cfi("new-cfi")
                .title("New Bookmark")
                .build();

        when(authenticationService.getAuthenticatedUser()).thenReturn(userDto);
        when(bookMarkRepository.existsByCfiAndBookIdAndUserId("new-cfi", bookId, userId)).thenReturn(false); // No duplicate
        when(bookRepository.findById(bookId)).thenReturn(Optional.empty()); // Book doesn't exist

        assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> bookMarkService.createBookmark(request));
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

    @Test
    void updateBookmark_Success() {
        var updateRequest = UpdateBookMarkRequest.builder()
                .title("Updated Title")
                .color("#FF0000")
                .notes("Updated notes")
                .priority(3)
                .build();

        when(authenticationService.getAuthenticatedUser()).thenReturn(userDto);
        when(bookMarkRepository.findByIdAndUserId(bookmarkId, userId)).thenReturn(Optional.of(bookmarkEntity));
        when(bookMarkRepository.save(any(BookMarkEntity.class))).thenReturn(bookmarkEntity);
        when(mapper.toDto(bookmarkEntity)).thenReturn(bookmarkDto);

        BookMark result = bookMarkService.updateBookmark(bookmarkId, updateRequest);

        assertNotNull(result);
        assertEquals(bookmarkId, result.getId());
        verify(bookMarkRepository).save(any(BookMarkEntity.class));
    }

    @Test
    void updateBookmark_NotFound() {
        var updateRequest = UpdateBookMarkRequest.builder()
                .title("Updated Title")
                .build();

        when(authenticationService.getAuthenticatedUser()).thenReturn(userDto);
        when(bookMarkRepository.findByIdAndUserId(bookmarkId, userId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> bookMarkService.updateBookmark(bookmarkId, updateRequest));
        verify(bookMarkRepository, never()).save(any());
    }

    @Test
    void getBookmarkById_Success() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(userDto);
        when(bookMarkRepository.findByIdAndUserId(bookmarkId, userId)).thenReturn(Optional.of(bookmarkEntity));
        when(mapper.toDto(bookmarkEntity)).thenReturn(bookmarkDto);

        BookMark result = bookMarkService.getBookmarkById(bookmarkId);

        assertNotNull(result);
        assertEquals(bookmarkId, result.getId());
        verify(bookMarkRepository).findByIdAndUserId(bookmarkId, userId);
    }

    @Test
    void getBookmarkById_NotFound() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(userDto);
        when(bookMarkRepository.findByIdAndUserId(bookmarkId, userId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> bookMarkService.getBookmarkById(bookmarkId));
        verify(bookMarkRepository).findByIdAndUserId(bookmarkId, userId);
    }
}
