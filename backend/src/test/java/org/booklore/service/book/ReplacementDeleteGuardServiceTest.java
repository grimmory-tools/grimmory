package org.booklore.service.book;

import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.ShelfEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.dto.Library;
import org.booklore.model.dto.request.ReplacementDeleteGuardRequest;
import org.booklore.model.dto.response.BookDeletionResponse;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.ReadStatus;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserBookProgressRepository;
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
    void shelfWitnessUsesCurrentSuccessorShelfIds() {
        BookEntity successor = book(2, ISBN13, ISBN10);
        successor.setShelves(java.util.Set.of(ShelfEntity.builder().id(7L).build(), ShelfEntity.builder().id(9L).build()));

        assertEquals(java.util.Set.of(7L, 9L), ReplacementDeleteGuardService.shelves(successor));
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
        successor.setBookFiles(new java.util.ArrayList<>(List.of(BookFileEntity.builder().book(successor).bookType(BookFileType.EPUB).build())));
        predecessor.setLibrary(successor.getLibrary());
        BookLoreUser user = BookLoreUser.builder().id(42L).assignedLibraries(List.of()).permissions(adminPermissions()).build();
        UserBookProgressEntity progress = UserBookProgressEntity.builder().readStatus(ReadStatus.UNREAD).build();
        ReplacementDeleteGuardRequest.ReaderState expected = new ReplacementDeleteGuardRequest.ReaderState(ReadStatus.UNREAD, null, null, Set.of(), null, null, null);
        ReplacementDeleteGuardService service = new ReplacementDeleteGuardService(bookRepository, progressRepository, authenticationService, bookService);
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
        predecessor.setBookFiles(new java.util.ArrayList<>(List.of(BookFileEntity.builder().book(predecessor).bookType(BookFileType.EPUB).build())));
        BookEntity successor = book(2, ISBN13, ISBN10);
        successor.setBookFiles(new java.util.ArrayList<>(List.of(BookFileEntity.builder().book(successor).bookType(BookFileType.EPUB).build())));
        when(authenticationService.getAuthenticatedUser()).thenReturn(user());
        when(bookRepository.findAllFullBooksWithFiles()).thenReturn(List.of(predecessor, successor));

        assertThrows(APIException.class, () -> new ReplacementDeleteGuardService(bookRepository, progressRepository, authenticationService, bookService)
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

    private Prepared prepared(boolean predecessorHasFiles) {
        BookEntity predecessor = book(1, ISBN13, null);
        predecessor.setIsPhysical(true);
        if (predecessorHasFiles) predecessor.setBookFiles(new java.util.ArrayList<>(List.of(BookFileEntity.builder().book(predecessor).bookType(BookFileType.EPUB).build())));
        BookEntity successor = book(2, ISBN13, ISBN10);
        successor.setBookFiles(new java.util.ArrayList<>(List.of(BookFileEntity.builder().book(successor).bookType(BookFileType.EPUB).build())));
        when(authenticationService.getAuthenticatedUser()).thenReturn(user());
        when(bookRepository.findAllFullBooksWithFiles()).thenReturn(List.of(predecessor, successor));
        when(progressRepository.findByUserIdAndBookId(42L, 2L)).thenReturn(Optional.of(progress()));
        ReplacementDeleteGuardService service = new ReplacementDeleteGuardService(bookRepository, progressRepository, authenticationService, bookService);
        return new Prepared(service, service.create(request()).guardId());
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

    private record Prepared(ReplacementDeleteGuardService service, String guardId) {}

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
