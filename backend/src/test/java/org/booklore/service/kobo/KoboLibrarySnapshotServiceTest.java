package org.booklore.service.kobo;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.mapper.BookEntityToKoboSnapshotBookMapper;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.BookLoreUser.UserPermissions;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.KoboLibrarySnapshotEntity;
import org.booklore.model.entity.KoboSnapshotBookEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.ShelfEntity;
import org.booklore.model.enums.ShelfType;
import org.booklore.repository.KoboDeletedBookProgressRepository;
import org.booklore.repository.KoboLibrarySnapshotRepository;
import org.booklore.repository.KoboSnapshotBookRepository;
import org.booklore.repository.BookRepository;
import org.booklore.repository.MagicShelfRepository;
import org.booklore.repository.ShelfRepository;
import org.booklore.service.opds.MagicShelfBookService;
import org.booklore.model.entity.MagicShelfEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KoboLibrarySnapshotServiceTest {

    @Mock
    private KoboLibrarySnapshotRepository koboLibrarySnapshotRepository;

    @Mock
    private KoboSnapshotBookRepository koboSnapshotBookRepository;

    @Mock
    private ShelfRepository shelfRepository;

    @Mock
    private BookEntityToKoboSnapshotBookMapper mapper;

    @Mock
    private KoboDeletedBookProgressRepository koboDeletedBookProgressRepository;

    @Mock
    private KoboCompatibilityService koboCompatibilityService;

    @Mock
    private AuthenticationService authenticationService;

    @Mock
    private MagicShelfRepository magicShelfRepository;

    @Mock
    private MagicShelfBookService magicShelfBookService;

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private KoboLibrarySnapshotService service;

    private BookLoreUserEntity owner;
    private BookLoreUserEntity otherUser;
    private BookEntity ownersBook;
    private BookEntity otherUsersBook;

    @BeforeEach
    void setUp() {
        owner = BookLoreUserEntity.builder().id(1L).isDefaultPassword(false).build();
        otherUser = BookLoreUserEntity.builder().id(2L).isDefaultPassword(false).build();

        LibraryEntity ownersLibrary = LibraryEntity.builder()
                .users(Set.of(owner))
                .build();

        LibraryEntity othersLibrary = LibraryEntity.builder()
                .users(Set.of(otherUser))
                .build();

        ownersBook = BookEntity.builder()
                .id(101L)
                .library(ownersLibrary)
                .build();

        BookFileEntity ownersPrimaryFile = new BookFileEntity();
        ownersPrimaryFile.setBook(ownersBook);
        ownersBook.setBookFiles(Set.of(ownersPrimaryFile));

        otherUsersBook = BookEntity.builder()
                .id(202L)
                .library(othersLibrary)
                .build();

        BookFileEntity otherPrimaryFile = new BookFileEntity();
        otherPrimaryFile.setBook(otherUsersBook);
        otherUsersBook.setBookFiles(Set.of(otherPrimaryFile));

        UserPermissions userPermissions = new UserPermissions();
        userPermissions.setAdmin(false);

        BookLoreUser mockUser = BookLoreUser.builder()
                .id(owner.getId())
                .permissions(userPermissions)
                .build();
        lenient().when(authenticationService.getAuthenticatedUser()).thenReturn(mockUser);
    }

    @Test
    void create_shouldIncludeOnlyBooksOwnedBySnapshotUser() {
        ShelfEntity shelf = ShelfEntity.builder()
                .name(ShelfType.KOBO.getName())
                .bookEntities(Set.of(ownersBook, otherUsersBook))
                .build();

        when(shelfRepository.findByUserIdAndName(eq(owner.getId()), eq(ShelfType.KOBO.getName())))
                .thenReturn(Optional.of(shelf));

        when(koboCompatibilityService.isBookSupportedForKobo(any())).thenReturn(true);

        doAnswer(invocation -> {
            BookEntity book = invocation.getArgument(0);
            return KoboSnapshotBookEntity.builder().bookId(book.getId()).build();
        }).when(mapper).toKoboSnapshotBook(any(BookEntity.class));

        ArgumentCaptor<KoboLibrarySnapshotEntity> snapshotCaptor = ArgumentCaptor.forClass(KoboLibrarySnapshotEntity.class);
        when(koboLibrarySnapshotRepository.save(snapshotCaptor.capture()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        KoboLibrarySnapshotEntity created = service.create(owner.getId());

        assertThat(created.getUserId()).isEqualTo(owner.getId());
        assertThat(created.getBooks()).extracting(KoboSnapshotBookEntity::getBookId)
                .containsExactly(ownersBook.getId());
        assertThat(created.getBooks()).allMatch(book -> created.equals(book.getSnapshot()));

        KoboLibrarySnapshotEntity saved = snapshotCaptor.getValue();
        assertThat(saved.getBooks()).extracting(KoboSnapshotBookEntity::getBookId)
                .containsExactly(ownersBook.getId());
    }

    @Test
    void create_shouldSkipOwnedBooksThatAreIncompatibleWithKobo() {
        ShelfEntity shelf = ShelfEntity.builder()
                .name(ShelfType.KOBO.getName())
                .bookEntities(Set.of(ownersBook))
                .build();

        when(shelfRepository.findByUserIdAndName(eq(owner.getId()), eq(ShelfType.KOBO.getName())))
                .thenReturn(Optional.of(shelf));

        when(koboCompatibilityService.isBookSupportedForKobo(ownersBook)).thenReturn(false);

        when(koboLibrarySnapshotRepository.save(any(KoboLibrarySnapshotEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        KoboLibrarySnapshotEntity created = service.create(owner.getId());

        assertThat(created.getBooks()).isEmpty();
    }

    private BookEntity ownedBook(long id) {
        BookEntity book = BookEntity.builder().id(id).library(ownersBook.getLibrary()).build();
        BookFileEntity file = new BookFileEntity();
        file.setBook(book);
        book.setBookFiles(Set.of(file));
        return book;
    }

    @Test
    void resolveSyncedBooks_shouldUnionKoboShelfSyncShelvesAndMagicShelves() {
        BookEntity koboShelfBook = ownedBook(1L);
        BookEntity syncShelfBook = ownedBook(2L);
        BookEntity sharedBook = ownedBook(3L);
        BookEntity magicBook = ownedBook(4L);

        ShelfEntity koboShelf = ShelfEntity.builder().name(ShelfType.KOBO.getName())
                .bookEntities(Set.of(koboShelfBook, sharedBook)).build();
        ShelfEntity syncShelf = ShelfEntity.builder().name("Vacation").koboSync(true)
                .bookEntities(Set.of(syncShelfBook, sharedBook)).build();
        MagicShelfEntity magicShelf = MagicShelfEntity.builder().id(9L).name("Historical").koboSync(true).build();

        when(shelfRepository.findByUserIdAndName(owner.getId(), ShelfType.KOBO.getName())).thenReturn(Optional.of(koboShelf));
        when(shelfRepository.findByUserIdAndKoboSyncTrue(owner.getId())).thenReturn(List.of(syncShelf));
        when(magicShelfRepository.findAllByUserIdAndKoboSyncTrue(owner.getId())).thenReturn(List.of(magicShelf));
        when(magicShelfBookService.getBookIdsByMagicShelfId(eq(owner.getId()), eq(9L), eq(KoboLibrarySnapshotService.MAX_MAGIC_SHELF_BOOKS_FOR_KOBO))).thenReturn(List.of(3L, 4L));
        when(bookRepository.findAllById(any())).thenReturn(List.of(magicBook));

        assertThat(service.resolveSyncedBooks(owner.getId()))
                .extracting(BookEntity::getId)
                .containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
    }

    @Test
    void resolveSyncedBooks_shouldWorkWithoutDedicatedKoboShelf() {
        BookEntity magicBook = ownedBook(4L);
        MagicShelfEntity magicShelf = MagicShelfEntity.builder().id(9L).name("Historical").koboSync(true).build();

        when(shelfRepository.findByUserIdAndName(owner.getId(), ShelfType.KOBO.getName())).thenReturn(Optional.empty());
        when(magicShelfRepository.findAllByUserIdAndKoboSyncTrue(owner.getId())).thenReturn(List.of(magicShelf));
        when(magicShelfBookService.getBookIdsByMagicShelfId(eq(owner.getId()), eq(9L), eq(KoboLibrarySnapshotService.MAX_MAGIC_SHELF_BOOKS_FOR_KOBO))).thenReturn(List.of(4L));
        when(bookRepository.findAllById(any())).thenReturn(List.of(magicBook));

        assertThat(service.resolveSyncedBooks(owner.getId()))
                .extracting(BookEntity::getId)
                .containsExactly(4L);
    }

    @Test
    void resolveSyncedBooks_shouldAbortWhenMagicShelfFailsToEvaluate() {
        MagicShelfEntity broken = MagicShelfEntity.builder().id(9L).name("Broken").koboSync(true).build();

        when(shelfRepository.findByUserIdAndName(owner.getId(), ShelfType.KOBO.getName())).thenReturn(Optional.empty());
        when(shelfRepository.findByUserIdAndKoboSyncTrue(owner.getId())).thenReturn(List.of());
        when(magicShelfRepository.findAllByUserIdAndKoboSyncTrue(owner.getId())).thenReturn(List.of(broken));
        when(magicShelfBookService.getBookIdsByMagicShelfId(eq(owner.getId()), eq(9L), eq(KoboLibrarySnapshotService.MAX_MAGIC_SHELF_BOOKS_FOR_KOBO)))
                .thenThrow(new RuntimeException("bad rules"));

        assertThatThrownBy(() -> service.resolveSyncedBooks(owner.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("bad rules");
        verify(koboLibrarySnapshotRepository, never()).save(any());
    }

    @Test
    void resolveSyncedBooks_shouldExcludeBooksOutsideTheUsersLibraries() {
        ShelfEntity syncShelf = ShelfEntity.builder().name("Vacation").koboSync(true)
                .bookEntities(Set.of(ownersBook, otherUsersBook)).build();

        when(shelfRepository.findByUserIdAndName(owner.getId(), ShelfType.KOBO.getName())).thenReturn(Optional.empty());
        when(shelfRepository.findByUserIdAndKoboSyncTrue(owner.getId())).thenReturn(List.of(syncShelf));
        when(magicShelfRepository.findAllByUserIdAndKoboSyncTrue(owner.getId())).thenReturn(List.of());

        assertThat(service.resolveSyncedBooks(owner.getId()))
                .extracting(BookEntity::getId)
                .containsExactly(ownersBook.getId());
    }

    @Test
    void resolveSyncedBooks_shouldRequestExactlyTheMagicShelfBookLimit() {
        MagicShelfEntity magicShelf = MagicShelfEntity.builder().id(9L).name("Big").koboSync(true).build();

        when(shelfRepository.findByUserIdAndName(owner.getId(), ShelfType.KOBO.getName())).thenReturn(Optional.empty());
        when(shelfRepository.findByUserIdAndKoboSyncTrue(owner.getId())).thenReturn(List.of());
        when(magicShelfRepository.findAllByUserIdAndKoboSyncTrue(owner.getId())).thenReturn(List.of(magicShelf));
        when(magicShelfBookService.getBookIdsByMagicShelfId(owner.getId(), 9L, 250)).thenReturn(List.of());

        assertThat(service.resolveSyncedBooks(owner.getId())).isEmpty();
        assertThat(KoboLibrarySnapshotService.MAX_MAGIC_SHELF_BOOKS_FOR_KOBO).isEqualTo(250);
        verify(magicShelfBookService).getBookIdsByMagicShelfId(owner.getId(), 9L, 250);
    }

    @Test
    void create_shouldSyncBooksFromSyncFlaggedShelfWithoutKoboShelf() {
        ShelfEntity syncShelf = ShelfEntity.builder().name("Vacation").koboSync(true)
                .bookEntities(Set.of(ownersBook)).build();

        when(shelfRepository.findByUserIdAndName(owner.getId(), ShelfType.KOBO.getName())).thenReturn(Optional.empty());
        when(shelfRepository.findByUserIdAndKoboSyncTrue(owner.getId())).thenReturn(List.of(syncShelf));
        when(koboCompatibilityService.isBookSupportedForKobo(any())).thenReturn(true);
        doAnswer(invocation -> {
            BookEntity book = invocation.getArgument(0);
            return KoboSnapshotBookEntity.builder().bookId(book.getId()).build();
        }).when(mapper).toKoboSnapshotBook(any(BookEntity.class));
        when(koboLibrarySnapshotRepository.save(any(KoboLibrarySnapshotEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        KoboLibrarySnapshotEntity created = service.create(owner.getId());

        assertThat(created.getBooks()).extracting(KoboSnapshotBookEntity::getBookId)
                .containsExactly(ownersBook.getId());
    }
}
