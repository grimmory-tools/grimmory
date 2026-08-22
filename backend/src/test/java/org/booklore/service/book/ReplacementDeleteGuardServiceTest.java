package org.booklore.service.book;

import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.ShelfEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReplacementDeleteGuardServiceTest {
    private static final String ISBN13 = "9780306406157";
    private static final String ISBN10 = "0306406152";
    private static final String OTHER_ISBN10 = "0140328726";

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

    private static BookEntity book(long id, String isbn13, String isbn10) {
        return BookEntity.builder().id(id)
                .metadata(BookMetadataEntity.builder().isbn13(isbn13).isbn10(isbn10).build())
                .build();
    }
}
