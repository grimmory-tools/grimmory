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
import org.booklore.repository.ShelfRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
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
        ownersBook.setBookFiles(List.of(ownersPrimaryFile));

        otherUsersBook = BookEntity.builder()
                .id(202L)
                .library(othersLibrary)
                .build();

        BookFileEntity otherPrimaryFile = new BookFileEntity();
        otherPrimaryFile.setBook(otherUsersBook);
        otherUsersBook.setBookFiles(List.of(otherPrimaryFile));

        UserPermissions userPermissions = new UserPermissions();
        userPermissions.setAdmin(false);

        BookLoreUser mockUser = BookLoreUser.builder()
                .id(owner.getId())
                .permissions(userPermissions)
                .build();
        when(authenticationService.getAuthenticatedUser()).thenReturn(mockUser);
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
}
