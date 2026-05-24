package org.booklore.service.kobo;

import org.booklore.model.entity.*;
import org.booklore.model.enums.ShelfType;
import org.booklore.repository.BookRepository;
import org.booklore.repository.KoboUserSettingsRepository;
import org.booklore.repository.ShelfRepository;
import org.booklore.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KoboAutoShelfServiceTest {

    @Mock
    private KoboUserSettingsRepository koboUserSettingsRepository;

    @Mock
    private ShelfRepository shelfRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private KoboCompatibilityService koboCompatibilityService;

    @InjectMocks
    private KoboAutoShelfService koboAutoShelfService;

    private BookEntity testBook;
    private BookLoreUserEntity testUser1;
    private BookLoreUserEntity testUser2;
    private LibraryEntity library1;
    private LibraryEntity library2;
    private ShelfEntity koboShelf1;
    private ShelfEntity koboShelf2;
    private KoboUserSettingsEntity settings1;
    private KoboUserSettingsEntity settings2;

    @BeforeEach
    void setUp() {
        library1 = LibraryEntity.builder()
                .id(1L)
                .build();

        library2 = LibraryEntity.builder()
                .id(2L)
                .build();

        testBook = BookEntity.builder()
                .id(1L)
                .library(library1)
                .shelves(new HashSet<>())
                .build();

        testUser1 = BookLoreUserEntity.builder()
                .id(100L)
                .libraries(Set.of(library1))
                .permissions(UserPermissionsEntity.builder().build())
                .isDefaultPassword(false).build();

        testUser2 = BookLoreUserEntity.builder()
                .id(200L)
                .libraries(Set.of(library1, library2))
                .permissions(UserPermissionsEntity.builder().build())
                .isDefaultPassword(false).build();

        koboShelf1 = ShelfEntity.builder()
                .id(10L)
                .name(ShelfType.KOBO.getName())
                .user(testUser1)
                .build();

        koboShelf2 = ShelfEntity.builder()
                .id(20L)
                .name(ShelfType.KOBO.getName())
                .user(testUser2)
                .build();

        settings1 = KoboUserSettingsEntity.builder()
                .userId(100L)
                .autoAddToShelf(true)
                .syncEnabled(true)
                .build();

        settings2 = KoboUserSettingsEntity.builder()
                .userId(200L)
                .autoAddToShelf(true)
                .syncEnabled(true)
                .build();
    }

    @Test
    void autoAddBookToKoboShelves_withNullBookId_shouldReturnEarly() {
        koboAutoShelfService.autoAddBookToKoboShelves(null);

        verify(bookRepository, never()).findByIdWithBookFiles(anyLong());
        verify(koboUserSettingsRepository, never()).findByAutoAddToShelfTrueAndSyncEnabledTrue();
        verify(shelfRepository, never()).findByUserIdInAndName(anyList(), anyString());
        verify(bookRepository, never()).save(any());
    }

    @Test
    void autoAddBookToKoboShelves_withNonExistentBook_shouldReturnEarly() {
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.empty());

        koboAutoShelfService.autoAddBookToKoboShelves(1L);

        verify(bookRepository).findByIdWithBookFiles(1L);
        verify(koboUserSettingsRepository, never()).findByAutoAddToShelfTrueAndSyncEnabledTrue();
        verify(shelfRepository, never()).findByUserIdInAndName(anyList(), anyString());
        verify(bookRepository, never()).save(any());
    }

    @Test
    void autoAddBookToKoboShelves_withIncompatibleBook_shouldReturnEarly() {
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(testBook));
        when(koboCompatibilityService.isBookSupportedForKobo(testBook)).thenReturn(false);

        koboAutoShelfService.autoAddBookToKoboShelves(1L);

        verify(bookRepository).findByIdWithBookFiles(1L);
        verify(koboCompatibilityService).isBookSupportedForKobo(testBook);
        verify(koboUserSettingsRepository, never()).findByAutoAddToShelfTrueAndSyncEnabledTrue();
        verify(shelfRepository, never()).findByUserIdInAndName(anyList(), anyString());
        verify(bookRepository, never()).save(any());
    }

    @Test
    void autoAddBookToKoboShelves_withNoEligibleUsers_shouldReturnEarly() {
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(testBook));
        when(koboCompatibilityService.isBookSupportedForKobo(testBook)).thenReturn(true);
        when(koboUserSettingsRepository.findByAutoAddToShelfTrueAndSyncEnabledTrue())
                .thenReturn(Collections.emptyList());

        koboAutoShelfService.autoAddBookToKoboShelves(1L);

        verify(bookRepository).findByIdWithBookFiles(1L);
        verify(koboCompatibilityService).isBookSupportedForKobo(testBook);
        verify(koboUserSettingsRepository).findByAutoAddToShelfTrueAndSyncEnabledTrue();
        verify(shelfRepository, never()).findByUserIdInAndName(anyList(), anyString());
        verify(bookRepository, never()).save(any());
    }

    @Test
    void autoAddBookToKoboShelves_successfully_shouldAddBookToShelves() {
        when(userRepository.findAllById(List.of(100L, 200L))).thenReturn(List.of(testUser1, testUser2));
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(testBook));
        when(koboCompatibilityService.isBookSupportedForKobo(testBook)).thenReturn(true);
        when(koboUserSettingsRepository.findByAutoAddToShelfTrueAndSyncEnabledTrue())
                .thenReturn(List.of(settings1, settings2));
        when(shelfRepository.findByUserIdInAndName(List.of(100L, 200L), ShelfType.KOBO.getName()))
                .thenReturn(List.of(koboShelf1, koboShelf2));

        koboAutoShelfService.autoAddBookToKoboShelves(1L);

        verify(bookRepository).findByIdWithBookFiles(1L);
        verify(koboCompatibilityService).isBookSupportedForKobo(testBook);
        verify(koboUserSettingsRepository).findByAutoAddToShelfTrueAndSyncEnabledTrue();
        verify(shelfRepository).findByUserIdInAndName(List.of(100L, 200L), ShelfType.KOBO.getName());
        verify(bookRepository).save(testBook);

        assert testBook.getShelves().contains(koboShelf1);
        assert testBook.getShelves().contains(koboShelf2);
        assert testBook.getShelves().size() == 2;
    }

    @Test
    void autoAddBookToKoboShelves_withOneUserMissingShelf_shouldAddOnlyToExistingShelves() {
        when(userRepository.findAllById(List.of(100L, 200L))).thenReturn(List.of(testUser1, testUser2));
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(testBook));
        when(koboCompatibilityService.isBookSupportedForKobo(testBook)).thenReturn(true);
        when(koboUserSettingsRepository.findByAutoAddToShelfTrueAndSyncEnabledTrue())
                .thenReturn(List.of(settings1, settings2));
        when(shelfRepository.findByUserIdInAndName(List.of(100L, 200L), ShelfType.KOBO.getName()))
                .thenReturn(List.of(koboShelf1));

        koboAutoShelfService.autoAddBookToKoboShelves(1L);

        verify(bookRepository).save(testBook);
        assert testBook.getShelves().contains(koboShelf1);
        assert !testBook.getShelves().contains(koboShelf2);
        assert testBook.getShelves().size() == 1;
    }

    @Test
    void autoAddBookToKoboShelves_withBookAlreadyOnShelf_shouldNotDuplicate() {
        testBook.getShelves().add(koboShelf1);

        when(userRepository.findAllById(List.of(100L, 200L))).thenReturn(List.of(testUser1, testUser2));
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(testBook));
        when(koboCompatibilityService.isBookSupportedForKobo(testBook)).thenReturn(true);
        when(koboUserSettingsRepository.findByAutoAddToShelfTrueAndSyncEnabledTrue())
                .thenReturn(List.of(settings1, settings2));
        when(shelfRepository.findByUserIdInAndName(List.of(100L, 200L), ShelfType.KOBO.getName()))
                .thenReturn(List.of(koboShelf1, koboShelf2));

        koboAutoShelfService.autoAddBookToKoboShelves(1L);

        verify(bookRepository).save(testBook);
        assert testBook.getShelves().contains(koboShelf1);
        assert testBook.getShelves().contains(koboShelf2);
        assert testBook.getShelves().size() == 2;
    }

    @Test
    void autoAddBookToKoboShelves_withBookOnAllShelves_shouldNotSave() {
        testBook.getShelves().add(koboShelf1);
        testBook.getShelves().add(koboShelf2);

        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(testBook));
        when(koboCompatibilityService.isBookSupportedForKobo(testBook)).thenReturn(true);
        when(koboUserSettingsRepository.findByAutoAddToShelfTrueAndSyncEnabledTrue())
                .thenReturn(List.of(settings1, settings2));
        when(userRepository.findAllById(List.of(100L, 200L))).thenReturn(List.of(testUser1, testUser2));
        when(shelfRepository.findByUserIdInAndName(List.of(100L, 200L), ShelfType.KOBO.getName()))
                .thenReturn(List.of(koboShelf1, koboShelf2));

        koboAutoShelfService.autoAddBookToKoboShelves(1L);

        verify(bookRepository, never()).save(any());
        assert testBook.getShelves().size() == 2;
    }

    @Test
    void autoAddBookToKoboShelves_withNoKoboShelvesFound_shouldNotSave() {
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(testBook));
        when(koboCompatibilityService.isBookSupportedForKobo(testBook)).thenReturn(true);
        when(koboUserSettingsRepository.findByAutoAddToShelfTrueAndSyncEnabledTrue())
                .thenReturn(List.of(settings1, settings2));
        when(shelfRepository.findByUserIdInAndName(List.of(100L, 200L), ShelfType.KOBO.getName()))
                .thenReturn(Collections.emptyList());

        koboAutoShelfService.autoAddBookToKoboShelves(1L);

        verify(shelfRepository).findByUserIdInAndName(List.of(100L, 200L), ShelfType.KOBO.getName());
        verify(bookRepository, never()).save(any());
        assert testBook.getShelves().isEmpty();
    }

    @Test
    void autoAddBookToKoboShelves_withSingleUser_shouldAddToSingleShelf() {
        when(userRepository.findAllById(List.of(100L))).thenReturn(List.of(testUser1));
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(testBook));
        when(koboCompatibilityService.isBookSupportedForKobo(testBook)).thenReturn(true);
        when(koboUserSettingsRepository.findByAutoAddToShelfTrueAndSyncEnabledTrue())
                .thenReturn(List.of(settings1));
        when(shelfRepository.findByUserIdInAndName(List.of(100L), ShelfType.KOBO.getName()))
                .thenReturn(List.of(koboShelf1));

        koboAutoShelfService.autoAddBookToKoboShelves(1L);

        verify(bookRepository).save(testBook);
        assert testBook.getShelves().contains(koboShelf1);
        assert testBook.getShelves().size() == 1;
    }

    @Test
    void autoAddBookToKoboShelves_withNullShelves_shouldInitializeAndAdd() {
        testBook = BookEntity.builder()
                .id(1L)
                .library(library1)
                .shelves(null)
                .build();

        when(userRepository.findAllById(List.of(100L))).thenReturn(List.of(testUser1));
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(testBook));
        when(koboCompatibilityService.isBookSupportedForKobo(testBook)).thenReturn(true);
        when(koboUserSettingsRepository.findByAutoAddToShelfTrueAndSyncEnabledTrue())
                .thenReturn(List.of(settings1));
        when(shelfRepository.findByUserIdInAndName(List.of(100L), ShelfType.KOBO.getName()))
                .thenReturn(List.of(koboShelf1));

        koboAutoShelfService.autoAddBookToKoboShelves(1L);

        verify(bookRepository).save(testBook);
        assert testBook.getShelves() != null;
        assert testBook.getShelves().contains(koboShelf1);
        assert testBook.getShelves().size() == 1;
    }

    @Test
    void autoAddBookToKoboShelves_shouldRespectLibraryAssignment() {
        testBook = BookEntity.builder()
                .id(1L)
                .library(library2)
                .build();

        when(userRepository.findAllById(List.of(100L))).thenReturn(List.of(testUser1, testUser2));
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(testBook));
        when(koboCompatibilityService.isBookSupportedForKobo(testBook)).thenReturn(true);
        when(koboUserSettingsRepository.findByAutoAddToShelfTrueAndSyncEnabledTrue())
                .thenReturn(List.of(settings1));
        when(shelfRepository.findByUserIdInAndName(List.of(100L), ShelfType.KOBO.getName()))
                .thenReturn(List.of(koboShelf1));

        koboAutoShelfService.autoAddBookToKoboShelves(1L);

        verify(bookRepository, never()).save(testBook);
        assert testBook.getShelves() != null;
        assert testBook.getShelves().isEmpty();
    }

    @Test
    void autoAddBookToKoboShelves_shouldHandleMissingUsers() {
        testBook = BookEntity.builder()
                .id(1L)
                .library(library1)
                .build();

        when(userRepository.findAllById(List.of(100L))).thenReturn(List.of());
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(testBook));
        when(koboCompatibilityService.isBookSupportedForKobo(testBook)).thenReturn(true);
        when(koboUserSettingsRepository.findByAutoAddToShelfTrueAndSyncEnabledTrue())
                .thenReturn(List.of(settings1));
        when(shelfRepository.findByUserIdInAndName(List.of(100L), ShelfType.KOBO.getName()))
                .thenReturn(List.of(koboShelf1));

        koboAutoShelfService.autoAddBookToKoboShelves(1L);

        verify(bookRepository, never()).save(testBook);
        assert testBook.getShelves() != null;
        assert testBook.getShelves().isEmpty();
    }
}
