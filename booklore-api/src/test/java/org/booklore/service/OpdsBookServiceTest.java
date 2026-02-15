package org.booklore.service;

import org.booklore.config.security.userdetails.OpdsUserDetails;
import org.booklore.mapper.BookMapper;
import org.booklore.mapper.custom.BookLoreUserTransformer;
import org.booklore.model.dto.*;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.ShelfEntity;
import org.booklore.model.entity.UserPermissionsEntity;
import org.booklore.repository.BookOpdsRepository;
import org.booklore.repository.ShelfRepository;
import org.booklore.repository.UserRepository;
import org.booklore.service.library.LibraryService;
import org.booklore.service.opds.OpdsBookService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OpdsBookServiceTest {

    @Mock private BookOpdsRepository bookOpdsRepository;
    @Mock private BookMapper bookMapper;
    @Mock private UserRepository userRepository;
    @Mock private BookLoreUserTransformer bookLoreUserTransformer;
    @Mock private ShelfRepository shelfRepository;
    @Mock private LibraryService libraryService;

    @InjectMocks private OpdsBookService opdsBookService;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    private OpdsUserDetails legacyUserDetails() {
        OpdsUserV2 v2 = OpdsUserV2.builder().userId(999L).username("legacy").build();
        return new OpdsUserDetails(v2);
    }

    private OpdsUserDetails v2UserDetails(Long userId, boolean isAdmin, Set<Long> libraryIds) {
        OpdsUserV2 v2 = OpdsUserV2.builder().userId(userId).username("v2user").build();
        BookLoreUserEntity entity = mock(BookLoreUserEntity.class);
        BookLoreUser user = mock(BookLoreUser.class);
        BookLoreUser.UserPermissions perms = mock(BookLoreUser.UserPermissions.class);

        when(userRepository.findById(userId)).thenReturn(Optional.of(entity));
        when(bookLoreUserTransformer.toDTO(entity)).thenReturn(user);
        when(user.getPermissions()).thenReturn(perms);
        when(perms.isAdmin()).thenReturn(isAdmin);
        when(perms.isCanAccessOpds()).thenReturn(true);

        List<Library> libraries = new ArrayList<>();
        for (Long id : libraryIds) {
            libraries.add(Library.builder().id(id).name("Lib" + id).watch(false).build());
        }
        when(user.getAssignedLibraries()).thenReturn(libraries);

        return new OpdsUserDetails(v2);
    }

    @Test
    void getAccessibleLibraries_returnsEmptyList_whenUserIdIsNull() {
        List<Library> result = opdsBookService.getAccessibleLibraries(null);

        assertThat(result).isEmpty();
    }

    @Test
    void getAccessibleLibraries_returnsAssignedLibraries_forNonAdmin() {
        OpdsUserDetails details = v2UserDetails(1L, false, Set.of(2L, 3L));
        BookLoreUserEntity entity = mock(BookLoreUserEntity.class);
        when(userRepository.findById(1L)).thenReturn(Optional.of(entity));
        BookLoreUser user = mock(BookLoreUser.class);
        when(bookLoreUserTransformer.toDTO(entity)).thenReturn(user);
        BookLoreUser.UserPermissions perms = mock(BookLoreUser.UserPermissions.class);
        when(user.getPermissions()).thenReturn(perms);
        when(perms.isAdmin()).thenReturn(false);
        List<Library> assigned = List.of(Library.builder().id(2L).watch(false).build());
        when(user.getAssignedLibraries()).thenReturn(assigned);

        List<Library> result = opdsBookService.getAccessibleLibraries(details.getOpdsUserV2().getUserId());

        assertThat(result).isEqualTo(assigned);
    }

    @Test
    void getAccessibleLibraries_returnsAllLibraries_forAdmin() {
        OpdsUserDetails details = v2UserDetails(1L, true, Set.of(1L));
        List<Library> allLibs = List.of(Library.builder().id(1L).watch(false).build());
        when(libraryService.getAllLibraries()).thenReturn(allLibs);

        List<Library> result = opdsBookService.getAccessibleLibraries(details.getOpdsUserV2().getUserId());

        assertThat(result).isEqualTo(allLibs);
    }

    @Test
    void getBooksPage_legacyUser_delegatesToLegacyMethod() {
        OpdsUserDetails details = legacyUserDetails();
        BookLoreUserEntity entity = mock(BookLoreUserEntity.class);
        var permissionsEntity = mock(UserPermissionsEntity.class);
        when(permissionsEntity.isPermissionAccessOpds()).thenReturn(true);
        when(permissionsEntity.isPermissionAdmin()).thenReturn(false);
        when(entity.getPermissions()).thenReturn(permissionsEntity);
        when(userRepository.findById(999L)).thenReturn(Optional.of(entity));

        BookLoreUser user = mock(BookLoreUser.class);
        BookLoreUser.UserPermissions perms = mock(BookLoreUser.UserPermissions.class);
        when(bookLoreUserTransformer.toDTO(entity)).thenReturn(user);
        when(user.getPermissions()).thenReturn(perms);
        when(perms.isAdmin()).thenReturn(false);
        when(user.getAssignedLibraries()).thenReturn(List.of(Library.builder().id(1L).watch(false).build()));
        when(user.getId()).thenReturn(999L);

        // Mock shelf access
        ShelfEntity shelf = mock(ShelfEntity.class);
        BookLoreUserEntity shelfUser = mock(BookLoreUserEntity.class);
        when(shelfUser.getId()).thenReturn(999L);
        when(shelf.getUser()).thenReturn(shelfUser);
        when(shelfRepository.findById(2L)).thenReturn(Optional.of(shelf));

        when(bookOpdsRepository.findBookIds(any())).thenReturn(Page.empty());
        when(bookOpdsRepository.findRecentBookIds(any())).thenReturn(Page.empty());
        when(bookOpdsRepository.findBookIdsByLibraryIds(anySet(), any())).thenReturn(Page.empty());
        when(bookOpdsRepository.findBookIdsByShelfId(anyLong(), any())).thenReturn(Page.empty());
        when(bookOpdsRepository.findBookIdsByShelfIds(anySet(), any())).thenReturn(Page.empty());
        when(bookOpdsRepository.findBookIdsByMetadataSearch(anyString(), any())).thenReturn(Page.empty());
        when(bookOpdsRepository.findBookIdsByMetadataSearchAndLibraryIds(anyString(), anySet(), any())).thenReturn(Page.empty());
        when(bookOpdsRepository.findBookIdsByMetadataSearchAndShelfIds(anyString(), anySet(), any())).thenReturn(Page.empty());
        when(bookOpdsRepository.findAllWithMetadataByIds(anyList())).thenReturn(List.of());
        when(bookOpdsRepository.findAllWithMetadataByIdsAndLibraryIds(anyList(), anySet())).thenReturn(List.of());
        when(bookOpdsRepository.findAllWithMetadataByIdsAndShelfId(anyList(), anyLong())).thenReturn(List.of());
        when(bookOpdsRepository.findAllWithMetadataByIdsAndShelfIds(anyList(), anySet())).thenReturn(List.of());
        when(bookOpdsRepository.findAllWithFullMetadataByIds(anyList())).thenReturn(List.of());
        when(bookOpdsRepository.findAllWithFullMetadataByIdsAndLibraryIds(anyList(), anySet())).thenReturn(List.of());
        when(bookOpdsRepository.findAllWithFullMetadataByIdsAndShelfIds(anyList(), anySet())).thenReturn(List.of());

        opdsBookService.getBooksPage(details.getOpdsUserV2().getUserId(), "q", 1L, Set.of(2L), 0, 10);
    }

    @Test
    void getBooksPage_v2User_delegatesToV2Method() {
        OpdsUserDetails details = v2UserDetails(1L, true, Set.of(1L));
        when(bookOpdsRepository.findBookIds(any())).thenReturn(Page.empty());
        when(bookOpdsRepository.findRecentBookIds(any())).thenReturn(Page.empty());
        when(bookOpdsRepository.findBookIdsByLibraryIds(anySet(), any())).thenReturn(Page.empty());
        when(bookOpdsRepository.findBookIdsByShelfId(anyLong(), any())).thenReturn(Page.empty());
        when(bookOpdsRepository.findBookIdsByShelfIds(anySet(), any())).thenReturn(Page.empty());
        when(bookOpdsRepository.findBookIdsByMetadataSearch(anyString(), any())).thenReturn(Page.empty());
        when(bookOpdsRepository.findBookIdsByMetadataSearchAndShelfIds(anyString(), anySet(), any())).thenReturn(Page.empty());
        when(bookOpdsRepository.findAllWithMetadataByIds(anyList())).thenReturn(List.of());
        when(bookOpdsRepository.findAllWithMetadataByIdsAndLibraryIds(anyList(), anySet())).thenReturn(List.of());
        when(bookOpdsRepository.findAllWithMetadataByIdsAndShelfId(anyList(), anyLong())).thenReturn(List.of());
        when(bookOpdsRepository.findAllWithMetadataByIdsAndShelfIds(anyList(), anySet())).thenReturn(List.of());
        when(bookOpdsRepository.findAllWithFullMetadataByIds(anyList())).thenReturn(List.of());
        when(bookOpdsRepository.findAllWithFullMetadataByIdsAndLibraryIds(anyList(), anySet())).thenReturn(List.of());
        when(bookOpdsRepository.findAllWithFullMetadataByIdsAndShelfIds(anyList(), anySet())).thenReturn(List.of());

        BookLoreUserEntity entity = mock(BookLoreUserEntity.class);
        var permissionsEntity = mock(UserPermissionsEntity.class);
        when(permissionsEntity.isPermissionAccessOpds()).thenReturn(true);
        when(permissionsEntity.isPermissionAdmin()).thenReturn(true);
        when(entity.getPermissions()).thenReturn(permissionsEntity);
        when(userRepository.findById(1L)).thenReturn(Optional.of(entity));
        BookLoreUser user = mock(BookLoreUser.class);
        BookLoreUser.UserPermissions perms = mock(BookLoreUser.UserPermissions.class);
        when(bookLoreUserTransformer.toDTO(entity)).thenReturn(user);
        when(user.getPermissions()).thenReturn(perms);
        when(perms.isAdmin()).thenReturn(true);
        when(user.getId()).thenReturn(1L);

        ShelfEntity shelf = mock(ShelfEntity.class);
        BookLoreUserEntity shelfUser = mock(BookLoreUserEntity.class);
        when(shelfUser.getId()).thenReturn(1L);
        when(shelf.getUser()).thenReturn(shelfUser);
        when(shelfRepository.findById(anyLong())).thenReturn(Optional.of(shelf));

        opdsBookService.getBooksPage(details.getOpdsUserV2().getUserId(), "q", 1L, Set.of(2L), 0, 10);
    }

    @Test
    void getRecentBooksPage_returnsRecentBooks_forLegacyUser() {
        OpdsUserDetails details = legacyUserDetails();
        BookLoreUserEntity entity = mock(BookLoreUserEntity.class);
        when(userRepository.findById(999L)).thenReturn(Optional.of(entity));
        BookLoreUser user = mock(BookLoreUser.class);
        when(bookLoreUserTransformer.toDTO(entity)).thenReturn(user);
        BookLoreUser.UserPermissions perms = mock(BookLoreUser.UserPermissions.class);
        when(user.getPermissions()).thenReturn(perms);
        when(perms.isAdmin()).thenReturn(true);

        when(bookOpdsRepository.findRecentBookIds(any())).thenReturn(Page.empty());
        when(bookOpdsRepository.findAllWithMetadataByIds(anyList())).thenReturn(List.of());

        opdsBookService.getRecentBooksPage(details.getOpdsUserV2().getUserId(), 0, 10);
    }

    @Test
    void getRecentBooksPage_appliesBookFilters_forNonAdminV2User() {
        OpdsUserDetails details = v2UserDetails(2L, false, Set.of(1L, 2L));
        BookLoreUserEntity entity = mock(BookLoreUserEntity.class);
        when(userRepository.findById(2L)).thenReturn(Optional.of(entity));
        BookLoreUser user = mock(BookLoreUser.class);
        when(bookLoreUserTransformer.toDTO(entity)).thenReturn(user);
        BookLoreUser.UserPermissions perms = mock(BookLoreUser.UserPermissions.class);
        when(user.getPermissions()).thenReturn(perms);
        when(perms.isAdmin()).thenReturn(false);
        List<Library> libs = List.of(Library.builder().id(1L).watch(false).build());
        when(user.getAssignedLibraries()).thenReturn(libs);

        Book book = Book.builder().id(1L).shelves(Set.of(Shelf.builder().userId(2L).build())).build();
        BookEntity bookEntity = mock(BookEntity.class);
        when(bookEntity.getId()).thenReturn(1L);

        when(bookOpdsRepository.findRecentBookIdsByLibraryIds(anySet(), any())).thenReturn(new PageImpl<>(List.of(1L)));
        when(bookOpdsRepository.findAllWithMetadataByIdsAndLibraryIds(anyList(), anySet())).thenReturn(List.of(bookEntity));
        when(bookMapper.toBook(bookEntity)).thenReturn(book);

        Page<Book> result = opdsBookService.getRecentBooksPage(details.getOpdsUserV2().getUserId(), 0, 10);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().getShelves()).allMatch(shelf -> shelf.getUserId().equals(2L));
    }

    @Test
    void getLibraryName_returnsName_whenFound() {
        List<Library> libs = List.of(Library.builder().id(1L).name("Lib1").watch(false).build());
        when(libraryService.getAllLibraries()).thenReturn(libs);

        String name = opdsBookService.getLibraryName(1L);

        assertThat(name).isEqualTo("Lib1");
    }

    @Test
    void getLibraryName_returnsDefault_whenNotFound() {
        when(libraryService.getAllLibraries()).thenReturn(List.of());

        String name = opdsBookService.getLibraryName(99L);

        assertThat(name).isEqualTo("Library Books");
    }

    @Test
    void getShelfName_returnsShelfName_whenFound() {
        ShelfEntity shelf = mock(ShelfEntity.class);
        when(shelf.getName()).thenReturn("Shelf1");
        when(shelfRepository.findById(1L)).thenReturn(Optional.of(shelf));

        String name = opdsBookService.getShelfName(1L);

        assertThat(name).isEqualTo("Shelf1 - Shelf");
    }

    @Test
    void getShelfName_returnsDefault_whenNotFound() {
        when(shelfRepository.findById(1L)).thenReturn(Optional.empty());

        String name = opdsBookService.getShelfName(1L);

        assertThat(name).isEqualTo("Shelf Books");
    }

    @Test
    void getUserShelves_returnsShelves() {
        List<ShelfEntity> shelves = List.of(mock(ShelfEntity.class));
        when(shelfRepository.findByUserId(1L)).thenReturn(shelves);

        List<ShelfEntity> result = opdsBookService.getUserShelves(1L);

        assertThat(result).isEqualTo(shelves);
    }

    @Test
    void getRandomBooks_returnsBooks_whenLibrariesAccessible() {
        OpdsUserDetails details = v2UserDetails(1L, true, Set.of(1L));
        OpdsBookService spy = Mockito.spy(opdsBookService);
        List<Library> libs = List.of(Library.builder().id(1L).watch(false).build());
        doReturn(libs).when(spy).getAccessibleLibraries(details.getOpdsUserV2().getUserId());

        when(bookOpdsRepository.findRandomBookIdsByLibraryIds(anyList())).thenReturn(List.of(1L, 2L));
        BookEntity entity = mock(BookEntity.class);
        when(bookOpdsRepository.findAllWithMetadataByIds(anyList())).thenReturn(List.of(entity));
        Book book = Book.builder().id(1L).build();
        when(bookMapper.toBook(entity)).thenReturn(book);

        List<Book> result = spy.getRandomBooks(details.getOpdsUserV2().getUserId(), 1);

        assertThat(result).hasSize(1);
    }

    @Test
    void getRandomBooks_returnsEmpty_whenNoLibraries() {
        OpdsUserDetails details = v2UserDetails(1L, false, Set.of());
        OpdsBookService spy = Mockito.spy(opdsBookService);
        doReturn(List.of()).when(spy).getAccessibleLibraries(details.getOpdsUserV2().getUserId());

        List<Book> result = spy.getRandomBooks(details.getOpdsUserV2().getUserId(), 1);

        assertThat(result).isEmpty();
    }

    @Test
    void getBooksPageForV2User_throwsForbidden_whenNoPermission() {
        OpdsUserV2 v2 = OpdsUserV2.builder().userId(1L).build();
        BookLoreUserEntity entity = mock(BookLoreUserEntity.class);
        var permissionsEntity = mock(UserPermissionsEntity.class);
        when(permissionsEntity.isPermissionAccessOpds()).thenReturn(false);
        when(permissionsEntity.isPermissionAdmin()).thenReturn(false);
        when(entity.getPermissions()).thenReturn(permissionsEntity);
        when(userRepository.findById(1L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() ->
                opdsBookService.getBooksPage(1L, null, null, null, 0, 10)
        ).hasMessageContaining("You are not allowed to access this resource");
    }

    @Test
    void getBooksPage_withSingleShelfId_returnsShelfBooks() {
        OpdsUserDetails details = v2UserDetails(1L, false, Set.of(1L));
        BookLoreUserEntity entity = mock(BookLoreUserEntity.class);
        var permissionsEntity = mock(UserPermissionsEntity.class);
        when(permissionsEntity.isPermissionAccessOpds()).thenReturn(true);
        when(permissionsEntity.isPermissionAdmin()).thenReturn(false);
        when(entity.getPermissions()).thenReturn(permissionsEntity);
        when(userRepository.findById(1L)).thenReturn(Optional.of(entity));

        BookLoreUser user = mock(BookLoreUser.class);
        BookLoreUser.UserPermissions perms = mock(BookLoreUser.UserPermissions.class);
        when(bookLoreUserTransformer.toDTO(entity)).thenReturn(user);
        when(user.getPermissions()).thenReturn(perms);
        when(perms.isAdmin()).thenReturn(false);
        when(perms.isCanAccessOpds()).thenReturn(true);
        when(user.getId()).thenReturn(1L);
        when(user.getAssignedLibraries()).thenReturn(List.of(Library.builder().id(1L).watch(false).build()));

        ShelfEntity shelf = mock(ShelfEntity.class);
        BookLoreUserEntity shelfUser = mock(BookLoreUserEntity.class);
        when(shelfUser.getId()).thenReturn(1L);
        when(shelf.getUser()).thenReturn(shelfUser);
        when(shelfRepository.findById(10L)).thenReturn(Optional.of(shelf));

        BookEntity bookEntity = mock(BookEntity.class);
        when(bookEntity.getId()).thenReturn(1L);
        Book book = Book.builder().id(1L).build();
        when(bookMapper.toBook(bookEntity)).thenReturn(book);

        when(bookOpdsRepository.findBookIdsByShelfIds(eq(Set.of(10L)), any())).thenReturn(new PageImpl<>(List.of(1L)));
        when(bookOpdsRepository.findAllWithMetadataByIdsAndShelfIds(eq(List.of(1L)), eq(Set.of(10L)))).thenReturn(List.of(bookEntity));

        Page<Book> result = opdsBookService.getBooksPage(1L, null, null, Set.of(10L), 0, 10);

        assertThat(result.getContent()).hasSize(1);
        verify(bookOpdsRepository).findBookIdsByShelfIds(eq(Set.of(10L)), any());
    }

    @Test
    void getBooksPage_withMultipleShelfIds_returnsShelfBooks() {
        OpdsUserDetails details = v2UserDetails(1L, false, Set.of(1L));
        BookLoreUserEntity entity = mock(BookLoreUserEntity.class);
        var permissionsEntity = mock(UserPermissionsEntity.class);
        when(permissionsEntity.isPermissionAccessOpds()).thenReturn(true);
        when(permissionsEntity.isPermissionAdmin()).thenReturn(false);
        when(entity.getPermissions()).thenReturn(permissionsEntity);
        when(userRepository.findById(1L)).thenReturn(Optional.of(entity));

        BookLoreUser user = mock(BookLoreUser.class);
        BookLoreUser.UserPermissions perms = mock(BookLoreUser.UserPermissions.class);
        when(bookLoreUserTransformer.toDTO(entity)).thenReturn(user);
        when(user.getPermissions()).thenReturn(perms);
        when(perms.isAdmin()).thenReturn(false);
        when(perms.isCanAccessOpds()).thenReturn(true);
        when(user.getId()).thenReturn(1L);
        when(user.getAssignedLibraries()).thenReturn(List.of(Library.builder().id(1L).watch(false).build()));

        ShelfEntity shelf1 = mock(ShelfEntity.class);
        ShelfEntity shelf2 = mock(ShelfEntity.class);
        BookLoreUserEntity shelfUser = mock(BookLoreUserEntity.class);
        when(shelfUser.getId()).thenReturn(1L);
        when(shelf1.getUser()).thenReturn(shelfUser);
        when(shelf2.getUser()).thenReturn(shelfUser);
        when(shelfRepository.findById(10L)).thenReturn(Optional.of(shelf1));
        when(shelfRepository.findById(20L)).thenReturn(Optional.of(shelf2));

        BookEntity bookEntity1 = mock(BookEntity.class);
        BookEntity bookEntity2 = mock(BookEntity.class);
        when(bookEntity1.getId()).thenReturn(1L);
        when(bookEntity2.getId()).thenReturn(2L);
        Book book1 = Book.builder().id(1L).build();
        Book book2 = Book.builder().id(2L).build();
        when(bookMapper.toBook(bookEntity1)).thenReturn(book1);
        when(bookMapper.toBook(bookEntity2)).thenReturn(book2);

        when(bookOpdsRepository.findBookIdsByShelfIds(eq(Set.of(10L, 20L)), any())).thenReturn(new PageImpl<>(List.of(1L, 2L)));
        when(bookOpdsRepository.findAllWithMetadataByIdsAndShelfIds(eq(List.of(1L, 2L)), eq(Set.of(10L, 20L)))).thenReturn(List.of(bookEntity1, bookEntity2));

        Page<Book> result = opdsBookService.getBooksPage(1L, null, null, Set.of(10L, 20L), 0, 10);

        assertThat(result.getContent()).hasSize(2);
        verify(bookOpdsRepository).findBookIdsByShelfIds(eq(Set.of(10L, 20L)), any());
    }

    @Test
    void getBooksPage_withShelfIdAndQuery_searchesInShelf() {
        OpdsUserDetails details = v2UserDetails(1L, false, Set.of(1L));
        BookLoreUserEntity entity = mock(BookLoreUserEntity.class);
        var permissionsEntity = mock(UserPermissionsEntity.class);
        when(permissionsEntity.isPermissionAccessOpds()).thenReturn(true);
        when(permissionsEntity.isPermissionAdmin()).thenReturn(false);
        when(entity.getPermissions()).thenReturn(permissionsEntity);
        when(userRepository.findById(1L)).thenReturn(Optional.of(entity));

        BookLoreUser user = mock(BookLoreUser.class);
        BookLoreUser.UserPermissions perms = mock(BookLoreUser.UserPermissions.class);
        when(bookLoreUserTransformer.toDTO(entity)).thenReturn(user);
        when(user.getPermissions()).thenReturn(perms);
        when(perms.isAdmin()).thenReturn(false);
        when(perms.isCanAccessOpds()).thenReturn(true);
        when(user.getId()).thenReturn(1L);
        when(user.getAssignedLibraries()).thenReturn(List.of(Library.builder().id(1L).watch(false).build()));

        ShelfEntity shelf = mock(ShelfEntity.class);
        BookLoreUserEntity shelfUser = mock(BookLoreUserEntity.class);
        when(shelfUser.getId()).thenReturn(1L);
        when(shelf.getUser()).thenReturn(shelfUser);
        when(shelfRepository.findById(10L)).thenReturn(Optional.of(shelf));

        BookEntity bookEntity = mock(BookEntity.class);
        when(bookEntity.getId()).thenReturn(1L);
        Book book = Book.builder().id(1L).build();
        when(bookMapper.toBook(bookEntity)).thenReturn(book);

        when(bookOpdsRepository.findBookIdsByMetadataSearchAndShelfIds(eq("test"), eq(Set.of(10L)), any())).thenReturn(new PageImpl<>(List.of(1L)));
        when(bookOpdsRepository.findAllWithFullMetadataByIdsAndShelfIds(eq(List.of(1L)), eq(Set.of(10L)))).thenReturn(List.of(bookEntity));

        Page<Book> result = opdsBookService.getBooksPage(1L, "test", null, Set.of(10L), 0, 10);

        assertThat(result.getContent()).hasSize(1);
        verify(bookOpdsRepository).findBookIdsByMetadataSearchAndShelfIds(eq("test"), eq(Set.of(10L)), any());
    }

    @Test
    void getBooksPage_withShelfId_throwsForbidden_whenNotOwner() {
        OpdsUserDetails details = v2UserDetails(1L, false, Set.of(1L));
        BookLoreUserEntity entity = mock(BookLoreUserEntity.class);
        var permissionsEntity = mock(UserPermissionsEntity.class);
        when(permissionsEntity.isPermissionAccessOpds()).thenReturn(true);
        when(permissionsEntity.isPermissionAdmin()).thenReturn(false);
        when(entity.getPermissions()).thenReturn(permissionsEntity);
        when(userRepository.findById(1L)).thenReturn(Optional.of(entity));

        BookLoreUser user = mock(BookLoreUser.class);
        BookLoreUser.UserPermissions perms = mock(BookLoreUser.UserPermissions.class);
        when(bookLoreUserTransformer.toDTO(entity)).thenReturn(user);
        when(user.getPermissions()).thenReturn(perms);
        when(perms.isAdmin()).thenReturn(false);
        when(perms.isCanAccessOpds()).thenReturn(true);
        when(user.getId()).thenReturn(1L);
        when(user.getAssignedLibraries()).thenReturn(List.of(Library.builder().id(1L).watch(false).build()));

        ShelfEntity shelf = mock(ShelfEntity.class);
        BookLoreUserEntity shelfUser = mock(BookLoreUserEntity.class);
        when(shelfUser.getId()).thenReturn(999L); // Different user
        when(shelf.getUser()).thenReturn(shelfUser);
        when(shelfRepository.findById(10L)).thenReturn(Optional.of(shelf));

        assertThatThrownBy(() ->
                opdsBookService.getBooksPage(1L, null, null, Set.of(10L), 0, 10)
        ).hasMessageContaining("You are not allowed to access this shelf");
    }

    @Test
    void getBooksPage_withShelfId_allowsAdmin_evenIfNotOwner() {
        OpdsUserDetails details = v2UserDetails(1L, true, Set.of(1L));
        BookLoreUserEntity entity = mock(BookLoreUserEntity.class);
        var permissionsEntity = mock(UserPermissionsEntity.class);
        when(permissionsEntity.isPermissionAccessOpds()).thenReturn(true);
        when(permissionsEntity.isPermissionAdmin()).thenReturn(true);
        when(entity.getPermissions()).thenReturn(permissionsEntity);
        when(userRepository.findById(1L)).thenReturn(Optional.of(entity));

        BookLoreUser user = mock(BookLoreUser.class);
        BookLoreUser.UserPermissions perms = mock(BookLoreUser.UserPermissions.class);
        when(bookLoreUserTransformer.toDTO(entity)).thenReturn(user);
        when(user.getPermissions()).thenReturn(perms);
        when(perms.isAdmin()).thenReturn(true);
        when(perms.isCanAccessOpds()).thenReturn(true);
        when(user.getId()).thenReturn(1L);
        when(user.getAssignedLibraries()).thenReturn(List.of(Library.builder().id(1L).watch(false).build()));

        ShelfEntity shelf = mock(ShelfEntity.class);
        BookLoreUserEntity shelfUser = mock(BookLoreUserEntity.class);
        when(shelfUser.getId()).thenReturn(999L); // Different user, but admin
        when(shelf.getUser()).thenReturn(shelfUser);
        when(shelfRepository.findById(10L)).thenReturn(Optional.of(shelf));

        when(bookOpdsRepository.findBookIdsByShelfIds(eq(Set.of(10L)), any())).thenReturn(Page.empty());

        Page<Book> result = opdsBookService.getBooksPage(1L, null, null, Set.of(10L), 0, 10);

        assertThat(result).isNotNull();
        verify(bookOpdsRepository).findBookIdsByShelfIds(eq(Set.of(10L)), any());
    }

}
