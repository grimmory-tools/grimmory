package org.booklore.service.kobo;

import org.booklore.exception.APIException;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.Library;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KoboRatingServiceTest {
    @Mock
    private BookRepository bookRepository;

    @Mock
    private UserBookProgressRepository userBookProgressRepository;

    @Captor
    ArgumentCaptor<UserBookProgressEntity> progressCaptor;

    @InjectMocks
    private KoboRatingService service;

    private BookLoreUser getFakeUser(long userId, boolean isAdmin, List<Library> libraries) {
        BookLoreUser.UserPermissions permissions = new BookLoreUser.UserPermissions();

        permissions.setAdmin(isAdmin);

        return BookLoreUser.builder()
                .id(userId)
                .permissions(
                        permissions
                )
                .assignedLibraries(libraries)
                .build();
    }

    private BookEntity getFakeBook(long bookId, long libraryId) {
        LibraryEntity library = LibraryEntity.builder()
                .id(libraryId)
                .build();

        return BookEntity.builder()
                .id(bookId)
                .library(library)
                .build();
    }

    @Test
    void updatePersonalRating_throwsForNullUser() {
        assertThrows(APIException.class, () -> service.updatePersonalRating(null, 123L, 0));
    }

    @Test
    void updatePersonalRating_throwsForNoAccessUser() {
        BookLoreUser user = getFakeUser(1L, false, List.of());

        when(bookRepository.findById(123L)).thenReturn(Optional.of(getFakeBook(123L, 2L)));

        assertThrows(APIException.class, () -> service.updatePersonalRating(user, 123L, 0));
    }

    @Test
    void updatePersonalRating_throwsForWrongLibraryUser() {
        BookLoreUser user = getFakeUser(1L, false, List.of(Library.builder().id(1L).build()));

        when(bookRepository.findById(123L)).thenReturn(Optional.of(getFakeBook(123L, 2L)));

        assertThrows(APIException.class, () -> service.updatePersonalRating(user, 123L, 0));
    }

    @Test
    void updatePersonalRating_allowsAdmin() {
        BookLoreUser user = getFakeUser(1L, true, List.of());

        when(bookRepository.findById(123L)).thenReturn(Optional.of(getFakeBook(123L, 2L)));

        ResponseEntity<?> response = service.updatePersonalRating(user, 123L, 0);

        assertTrue(response.getStatusCode().is2xxSuccessful());
    }

    @Test
    void updatePersonalRating_allowsLibrary() {
        BookLoreUser user = getFakeUser(1L, false, List.of(Library.builder().id(2L).build()));

        when(bookRepository.findById(123L)).thenReturn(Optional.of(getFakeBook(123L, 2L)));

        ResponseEntity<?> response = service.updatePersonalRating(user, 123L, 0);

        assertTrue(response.getStatusCode().is2xxSuccessful());
    }

    @Test
    void updatePersonalRating_sideEffectSavesUserProgress() {
        BookLoreUser user = getFakeUser(1L, true, List.of());

        when(bookRepository.findById(123L)).thenReturn(Optional.of(getFakeBook(123L, 2L)));

        service.updatePersonalRating(user, 123L, 0);

        verify(userBookProgressRepository).save(progressCaptor.capture());

        UserBookProgressEntity progress = progressCaptor.getValue();

        assertEquals(1L, progress.getUser().getId());
        assertEquals(123L, progress.getBook().getId());
    }

    @Test
    void updatePersonalRating_sideEffectDoublesRating() {
        BookLoreUser user = getFakeUser(1L, true, List.of());

        when(bookRepository.findById(123L)).thenReturn(Optional.of(getFakeBook(123L, 2L)));

        service.updatePersonalRating(user, 123L, 4);

        verify(userBookProgressRepository).save(progressCaptor.capture());

        UserBookProgressEntity progress = progressCaptor.getValue();

        assertEquals(8, progress.getPersonalRating());
    }

    @Test
    void updatePersonalRating_sideEffectMinimumRating() {
        BookLoreUser user = getFakeUser(1L, true, List.of());

        when(bookRepository.findById(123L)).thenReturn(Optional.of(getFakeBook(123L, 2L)));

        service.updatePersonalRating(user, 123L, -1);

        verify(userBookProgressRepository).save(progressCaptor.capture());

        UserBookProgressEntity progress = progressCaptor.getValue();

        assertEquals(0, progress.getPersonalRating());
    }

    @Test
    void updatePersonalRating_sideEffectMaximumRating() {
        BookLoreUser user = getFakeUser(1L, true, List.of());

        when(bookRepository.findById(123L)).thenReturn(Optional.of(getFakeBook(123L, 2L)));

        service.updatePersonalRating(user, 123L, 6);

        verify(userBookProgressRepository).save(progressCaptor.capture());

        UserBookProgressEntity progress = progressCaptor.getValue();

        assertEquals(10, progress.getPersonalRating());
    }
}
