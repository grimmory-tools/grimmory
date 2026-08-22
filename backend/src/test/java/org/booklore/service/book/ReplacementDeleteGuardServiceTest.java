package org.booklore.service.book;

import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.ShelfEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.entity.UserBookFileProgressEntity;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.dto.Library;
import org.booklore.model.dto.request.ReplacementDeleteGuardRequest;
import org.booklore.model.dto.response.BookDeletionResponse;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.ReadStatus;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.booklore.repository.UserBookFileProgressRepository;
import org.booklore.repository.UserContentRestrictionRepository;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.booklore.exception.APIException;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReplacementDeleteGuardServiceTest {
    private static final String ISBN13 = "9780306406157";
    private static final String ISBN10 = "0306406152";
    private static final String OTHER_ISBN10 = "0140328726";

    @Mock private BookRepository bookRepository;
    @Mock private UserBookProgressRepository progressRepository;
    @Mock private UserBookFileProgressRepository fileProgressRepository;
    @Mock private UserContentRestrictionRepository restrictionRepository;
    @Mock private AuthenticationService authenticationService;
    @Mock private BookService bookService;

    @Test
    void unrelatedIsbnBookDoesNotJoinPopulation() {
        List<BookEntity> population = ReplacementDeleteGuardService.matchingPopulation(
                List.of(book(1, ISBN13, null), book(2, null, ISBN10), book(3, null, OTHER_ISBN10)), ISBN13);

        assertEquals(List.of(1L, 2L), population.stream().map(BookEntity::getId).toList());
    }

    @Test
    void matchingIsbn13WithDifferentValidIsbn10IsCollision() {
        BookEntity collision = book(1, ISBN13, OTHER_ISBN10);

        assertTrue(ReplacementDeleteGuardService.mentionsIsbn(collision, ISBN13));
        assertFalse(ReplacementDeleteGuardService.consistentIsbn(collision, ISBN13));
    }

    @Test
    void extraCollisionRecordRemainsInPopulationAndBlocksExactPair() {
        List<BookEntity> population = ReplacementDeleteGuardService.matchingPopulation(
                List.of(book(1, ISBN13, null), book(2, null, ISBN10), book(3, ISBN13, OTHER_ISBN10)), ISBN13);

        assertEquals(3, population.size());
        assertFalse(ReplacementDeleteGuardService.consistentIsbn(population.get(2), ISBN13));
    }

    @Test
    void canonicalValidIsbn10AndIsbn13PairIsStrictlyConsistent() {
        BookEntity pair = book(1, ISBN13, ISBN10);

        assertTrue(ReplacementDeleteGuardService.mentionsIsbn(pair, ISBN13));
        assertTrue(ReplacementDeleteGuardService.consistentIsbn(pair, ISBN13));
    }

    @Test
    void invalidOrBlankSiblingIsFailClosed() {
        assertFalse(ReplacementDeleteGuardService.consistentIsbn(book(1, ISBN13, " "), ISBN13));
        assertFalse(ReplacementDeleteGuardService.consistentIsbn(book(1, ISBN13, "not-an-isbn"), ISBN13));
    }

    @Test
    void shelfWitnessUsesOnlyShelvesVisibleToAuthenticatedUser() {
        BookEntity successor = book(2, ISBN13, ISBN10);
        BookLoreUserEntity owner = BookLoreUserEntity.builder().id(42L).build();
        BookLoreUserEntity otherUser = BookLoreUserEntity.builder().id(99L).build();
        successor.setShelves(java.util.Set.of(
                ShelfEntity.builder().id(7L).user(owner).build(),
                ShelfEntity.builder().id(8L).user(otherUser).build(),
                ShelfEntity.builder().id(9L).isPublic(true).user(otherUser).build()));

        assertEquals(java.util.Set.of(7L, 9L), ReplacementDeleteGuardService.shelves(successor, 42L));
    }

    @Test
    void privateOtherUserShelfDoesNotBlockCreateAndConsume() {
        BookEntity predecessor = book(1, ISBN13, null);
        predecessor.setIsPhysical(true);
        BookEntity successor = book(2, ISBN13, ISBN10);
        successor.setBookFiles(Set.of(BookFileEntity.builder().book(successor).bookType(BookFileType.EPUB).build()));
        successor.setShelves(Set.of(ShelfEntity.builder().id(8L)
                .user(BookLoreUserEntity.builder().id(99L).build()).build()));
        when(authenticationService.getAuthenticatedUser()).thenReturn(user());
        when(bookRepository.findAllFullBooksWithFiles()).thenReturn(List.of(predecessor, successor));
        when(progressRepository.findByUserIdAndBookId(42L, 2L)).thenReturn(Optional.of(progress()));
        when(fileProgressRepository.existsByBookFileBookId(2L)).thenReturn(false);
        ReplacementDeleteGuardService service = new ReplacementDeleteGuardService(bookRepository, progressRepository,
                fileProgressRepository, restrictionRepository, authenticationService, bookService);

        String guardId = service.create(request()).guardId();
        when(bookService.deleteBooks(Set.of(1L))).thenReturn(ResponseEntity.ok(new BookDeletionResponse(Set.of(1L), List.of())));

        assertEquals("deleted", service.consume(guardId).get("status"));
    }

    @Test
    void nonOwnerCannotConsumeOrInvalidateGuard() {
        BookLoreUser owner = user();
        BookLoreUser otherUser = BookLoreUser.builder().id(99L).assignedLibraries(List.of()).permissions(adminPermissions()).build();
        when(authenticationService.getAuthenticatedUser()).thenReturn(owner, otherUser, owner);
        BookEntity predecessor = book(1, ISBN13, null);
        predecessor.setIsPhysical(true);
        BookEntity successor = book(2, ISBN13, ISBN10);
        successor.setBookFiles(Set.of(BookFileEntity.builder().book(successor).bookType(BookFileType.EPUB).build()));
        when(bookRepository.findAllFullBooksWithFiles()).thenReturn(List.of(predecessor, successor));
        when(progressRepository.findByUserIdAndBookId(42L, 2L)).thenReturn(Optional.of(progress()));
        when(fileProgressRepository.existsByBookFileBookId(2L)).thenReturn(false);
        ReplacementDeleteGuardService service = new ReplacementDeleteGuardService(bookRepository, progressRepository,
                fileProgressRepository, restrictionRepository, authenticationService, bookService);
        String guardId = service.create(request()).guardId();
        when(bookService.deleteBooks(Set.of(1L))).thenReturn(ResponseEntity.ok(new BookDeletionResponse(Set.of(1L), List.of())));

        APIException error = assertThrows(APIException.class, () -> service.consume(guardId));
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        verifyNoInteractions(bookService);
        assertEquals("deleted", service.consume(guardId).get("status"));
        verify(bookService).deleteBooks(Set.of(1L));
    }

    @Test
    void visibleShelfChangeAfterIssuanceRejectsConsume() {
        Prepared prepared = prepared(false);
        BookLoreUserEntity owner = BookLoreUserEntity.builder().id(42L).build();
        prepared.successor().setShelves(Set.of(ShelfEntity.builder().id(7L).user(owner).build()));
        ReplacementDeleteGuardRequest expected = new ReplacementDeleteGuardRequest(1L, 2L, ISBN13,
                new ReplacementDeleteGuardRequest.ReaderState(ReadStatus.UNREAD, null, null, Set.of(7L), null, null, null));
        // Re-issue with the visible shelf as the witnessed state, then mutate that visible shelf.
        String guardId = prepared.service().create(expected).guardId();
        prepared.successor().setShelves(Set.of(ShelfEntity.builder().id(8L).user(owner).build()));

        APIException error = assertThrows(APIException.class, () -> prepared.service().consume(guardId));
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        verifyNoInteractions(bookService);
    }

    @Test
    void unsupportedProgressRepresentationIsRejectedFailClosed() {
        UserBookProgressEntity progress = UserBookProgressEntity.builder().koboProgressPercent(42f).build();

        assertFalse(ReplacementDeleteGuardService.supportedProgress(progress));
    }

    @Test
    void consumeRejectsSuccessorThatLostItsLastFileWithoutDeletingPredecessor() {
        BookEntity predecessor = book(1, ISBN13, null);
        predecessor.setIsPhysical(true);
        BookEntity successor = book(2, ISBN13, ISBN10);
        successor.setLibrary(LibraryEntity.builder().id(7L).build());
        successor.setBookFiles(new java.util.HashSet<>(Set.of(BookFileEntity.builder().book(successor).bookType(BookFileType.EPUB).build())));
        predecessor.setLibrary(successor.getLibrary());
        BookLoreUser user = BookLoreUser.builder().id(42L).assignedLibraries(List.of()).permissions(adminPermissions()).build();
        UserBookProgressEntity progress = UserBookProgressEntity.builder().readStatus(ReadStatus.UNREAD).build();
        ReplacementDeleteGuardRequest.ReaderState expected = new ReplacementDeleteGuardRequest.ReaderState(ReadStatus.UNREAD, null, null, Set.of(), null, null, null);
        ReplacementDeleteGuardService service = new ReplacementDeleteGuardService(bookRepository, progressRepository, fileProgressRepository, restrictionRepository, authenticationService, bookService);
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(bookRepository.findAllFullBooksWithFiles()).thenReturn(List.of(predecessor, successor));
        when(progressRepository.findByUserIdAndBookId(42L, 2L)).thenReturn(Optional.of(progress));

        String guardId = service.create(new ReplacementDeleteGuardRequest(1L, 2L, ISBN13, expected)).guardId();
        successor.getBookFiles().clear();

        APIException error = assertThrows(APIException.class, () -> service.consume(guardId));
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        verifyNoInteractions(bookService);
    }

    @Test
    void filelessPhysicalPredecessorRequiresAndAcceptsExactOrdinaryDeleteResponse() {
        Prepared prepared = prepared(false);
        when(bookService.deleteBooks(Set.of(1L))).thenReturn(ResponseEntity.ok(
                new BookDeletionResponse(Set.of(1L), List.of())));

        assertEquals("deleted", prepared.service().consume(prepared.guardId()).get("status"));
        verify(bookService).deleteBooks(Set.of(1L));
    }

    @Test
    void physicalPredecessorWithFileIsRejectedAtCreate() {
        BookEntity predecessor = book(1, ISBN13, null);
        predecessor.setIsPhysical(true);
        predecessor.setBookFiles(Set.of(BookFileEntity.builder().book(predecessor).bookType(BookFileType.EPUB).build()));
        BookEntity successor = book(2, ISBN13, ISBN10);
        successor.setBookFiles(Set.of(BookFileEntity.builder().book(successor).bookType(BookFileType.EPUB).build()));
        when(authenticationService.getAuthenticatedUser()).thenReturn(user());
        when(bookRepository.findAllFullBooksWithFiles()).thenReturn(List.of(predecessor, successor));

        assertThrows(APIException.class, () -> new ReplacementDeleteGuardService(bookRepository, progressRepository, fileProgressRepository, restrictionRepository, authenticationService, bookService)
                .create(request()));
        verifyNoInteractions(bookService);
    }

    @Test
    void unexpectedOrdinaryDeleteResultsAreIndeterminateAndGuardCannotReplay() {
        List<ResponseEntity<BookDeletionResponse>> results = List.of(
                ResponseEntity.status(HttpStatus.MULTI_STATUS).body(new BookDeletionResponse(Set.of(1L), List.of(1L))),
                ResponseEntity.ok(null),
                ResponseEntity.ok(new BookDeletionResponse(Set.of(2L), List.of())),
                ResponseEntity.ok(new BookDeletionResponse(Set.of(1L), List.of(1L))));
        for (ResponseEntity<BookDeletionResponse> result : results) {
            Prepared prepared = prepared(false);
            when(bookService.deleteBooks(Set.of(1L))).thenReturn(result);

            APIException error = assertThrows(APIException.class, () -> prepared.service().consume(prepared.guardId()));
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, error.getStatus());
            assertTrue(error.getMessage().contains("indeterminate"));
            assertThrows(APIException.class, () -> prepared.service().consume(prepared.guardId()));
            reset(bookService);
        }
    }

    @Test
    void deletionExceptionIsIndeterminateAndConsumedGuardCannotBeReplayed() {
        Prepared prepared = prepared(false);
        when(bookService.deleteBooks(Set.of(1L))).thenThrow(new RuntimeException("response lost"));

        APIException error = assertThrows(APIException.class, () -> prepared.service().consume(prepared.guardId()));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, error.getStatus());
        assertTrue(error.getMessage().contains("indeterminate"));
        assertThrows(APIException.class, () -> prepared.service().consume(prepared.guardId()));
    }

    @Test
    void restrictedPairIsNotVisibleToNonAdminGuard() {
        BookLoreUser regular = BookLoreUser.builder().id(42L).assignedLibraries(List.of(Library.builder().id(7L).build()))
                .permissions(new BookLoreUser.UserPermissions()).build();
        when(authenticationService.getAuthenticatedUser()).thenReturn(regular);
        when(restrictionRepository.findByUserId(42L)).thenReturn(List.of(mock(org.booklore.model.entity.UserContentRestrictionEntity.class)));
        when(bookRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class))).thenReturn(List.of());

        ReplacementDeleteGuardService service = new ReplacementDeleteGuardService(bookRepository, progressRepository,
                fileProgressRepository, restrictionRepository, authenticationService, bookService);
        assertThrows(APIException.class, () -> service.create(request()));
        verifyNoInteractions(bookService);
    }

    @Test
    void successorFileProgressAddedAfterIssuanceRejectsConsume() {
        Prepared prepared = prepared(false);
        // create used the empty default result; this replacement represents a row added after issuance.
        when(fileProgressRepository.existsByBookFileBookId(2L)).thenReturn(true);

        APIException error = assertThrows(APIException.class, () -> prepared.service().consume(prepared.guardId()));
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        verifyNoInteractions(bookService);
    }

    @Test
    void successorFileProgressRejectsCreate() {
        BookEntity predecessor = book(1, ISBN13, null);
        predecessor.setIsPhysical(true);
        BookEntity successor = book(2, ISBN13, ISBN10);
        successor.setBookFiles(Set.of(BookFileEntity.builder().book(successor).bookType(BookFileType.EPUB).build()));
        when(authenticationService.getAuthenticatedUser()).thenReturn(user());
        when(bookRepository.findAllFullBooksWithFiles()).thenReturn(List.of(predecessor, successor));
        when(fileProgressRepository.existsByBookFileBookId(2L)).thenReturn(true);

        ReplacementDeleteGuardService service = new ReplacementDeleteGuardService(bookRepository, progressRepository,
                fileProgressRepository, restrictionRepository, authenticationService, bookService);
        assertThrows(APIException.class, () -> service.create(request()));
        verifyNoInteractions(bookService);
    }

    @Test
    void hiddenCatalogCollisionRejectsCreateBeforeDeletion() {
        BookEntity predecessor = physicalPredecessor();
        BookEntity successor = digitalSuccessor();
        BookEntity collision = book(3, ISBN13, OTHER_ISBN10);
        BookLoreUser regular = BookLoreUser.builder().id(42L)
                .assignedLibraries(List.of(Library.builder().id(7L).build()))
                .permissions(new BookLoreUser.UserPermissions()).build();
        when(authenticationService.getAuthenticatedUser()).thenReturn(regular);
        when(bookRepository.findAllFullBooksWithFiles()).thenReturn(List.of(predecessor, successor, collision));
        when(restrictionRepository.findByUserId(42L)).thenReturn(List.of());
        when(bookRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(predecessor, successor));

        assertThrows(APIException.class, () -> new ReplacementDeleteGuardService(bookRepository, progressRepository,
                fileProgressRepository, restrictionRepository, authenticationService, bookService).create(request()));
        verifyNoInteractions(bookService);
    }

    @Test
    void collisionAddedAfterIssuanceRejectsConsumeBeforeDeletion() {
        BookEntity predecessor = physicalPredecessor();
        BookEntity successor = digitalSuccessor();
        List<BookEntity> catalog = new java.util.ArrayList<>(List.of(predecessor, successor));
        when(authenticationService.getAuthenticatedUser()).thenReturn(user());
        when(bookRepository.findAllFullBooksWithFiles()).thenAnswer(invocation -> catalog);
        when(progressRepository.findByUserIdAndBookId(42L, 2L)).thenReturn(Optional.of(progress()));
        when(fileProgressRepository.existsByBookFileBookId(2L)).thenReturn(false);
        ReplacementDeleteGuardService service = new ReplacementDeleteGuardService(bookRepository, progressRepository,
                fileProgressRepository, restrictionRepository, authenticationService, bookService);

        String guardId = service.create(request()).guardId();
        catalog.add(book(3, ISBN13, OTHER_ISBN10));

        assertThrows(APIException.class, () -> service.consume(guardId));
        verifyNoInteractions(bookService);
    }

    @Test
    void exactCatalogPairStillRejectsWhenPairIsHiddenFromViewer() {
        BookEntity predecessor = physicalPredecessor();
        BookEntity successor = digitalSuccessor();
        BookLoreUser regular = BookLoreUser.builder().id(42L)
                .assignedLibraries(List.of(Library.builder().id(7L).build()))
                .permissions(new BookLoreUser.UserPermissions()).build();
        when(authenticationService.getAuthenticatedUser()).thenReturn(regular);
        when(bookRepository.findAllFullBooksWithFiles()).thenReturn(List.of(predecessor, successor));
        when(restrictionRepository.findByUserId(42L)).thenReturn(List.of());
        when(bookRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class))).thenReturn(List.of());

        assertThrows(APIException.class, () -> new ReplacementDeleteGuardService(bookRepository, progressRepository,
                fileProgressRepository, restrictionRepository, authenticationService, bookService).create(request()));
        verifyNoInteractions(bookService);
    }

    private Prepared prepared(boolean predecessorHasFiles) {
        BookEntity predecessor = book(1, ISBN13, null);
        predecessor.setIsPhysical(true);
        if (predecessorHasFiles) predecessor.setBookFiles(Set.of(BookFileEntity.builder().book(predecessor).bookType(BookFileType.EPUB).build()));
        BookEntity successor = book(2, ISBN13, ISBN10);
        successor.setBookFiles(Set.of(BookFileEntity.builder().book(successor).bookType(BookFileType.EPUB).build()));
        when(authenticationService.getAuthenticatedUser()).thenReturn(user());
        when(bookRepository.findAllFullBooksWithFiles()).thenReturn(List.of(predecessor, successor));
        when(progressRepository.findByUserIdAndBookId(42L, 2L)).thenReturn(Optional.of(progress()));
        when(fileProgressRepository.existsByBookFileBookId(2L)).thenReturn(false);
        ReplacementDeleteGuardService service = new ReplacementDeleteGuardService(bookRepository, progressRepository, fileProgressRepository, restrictionRepository, authenticationService, bookService);
        return new Prepared(service, service.create(request()).guardId(), successor);
    }

    private BookEntity physicalPredecessor() {
        BookEntity predecessor = book(1, ISBN13, null);
        predecessor.setIsPhysical(true);
        predecessor.setLibrary(LibraryEntity.builder().id(7L).build());
        return predecessor;
    }

    private BookEntity digitalSuccessor() {
        BookEntity successor = book(2, ISBN13, ISBN10);
        successor.setBookFiles(Set.of(BookFileEntity.builder().book(successor).bookType(BookFileType.EPUB).build()));
        successor.setLibrary(LibraryEntity.builder().id(7L).build());
        return successor;
    }

    private ReplacementDeleteGuardRequest request() {
        return new ReplacementDeleteGuardRequest(1L, 2L, ISBN13,
                new ReplacementDeleteGuardRequest.ReaderState(ReadStatus.UNREAD, null, null, Set.of(), null, null, null));
    }

    private BookLoreUser user() {
        return BookLoreUser.builder().id(42L).assignedLibraries(List.of()).permissions(adminPermissions()).build();
    }

    private UserBookProgressEntity progress() {
        return UserBookProgressEntity.builder().readStatus(ReadStatus.UNREAD).build();
    }

    private record Prepared(ReplacementDeleteGuardService service, String guardId, BookEntity successor) {}

    private static BookLoreUser.UserPermissions adminPermissions() {
        BookLoreUser.UserPermissions permissions = new BookLoreUser.UserPermissions();
        permissions.setAdmin(true);
        return permissions;
    }

    private static BookEntity book(long id, String isbn13, String isbn10) {
        return BookEntity.builder().id(id)
                .metadata(BookMetadataEntity.builder().isbn13(isbn13).isbn10(isbn10).build())
                .build();
    }
}
