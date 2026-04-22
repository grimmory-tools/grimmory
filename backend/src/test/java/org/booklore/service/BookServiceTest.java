package org.booklore.service;

import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.ShelfEntity;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookServiceTest {

    @Test
    void bookEntityShelves_shouldPreventDuplicateAssignments() {
        BookEntity book = new BookEntity();
        book.setShelves(new HashSet<>());

        ShelfEntity shelf1 = new ShelfEntity();
        shelf1.setId(1L);
        shelf1.setName("Test Shelf");

        book.getShelves().add(shelf1);
        book.getShelves().add(shelf1);
        book.getShelves().add(shelf1);

        assertEquals(1, book.getShelves().size());
        assertTrue(book.getShelves().contains(shelf1));
    }

    @Test
    void bookEntityShelves_shouldAllowMultipleDifferentShelves() {
        BookEntity book = new BookEntity();
        book.setShelves(new HashSet<>());

        ShelfEntity shelf1 = new ShelfEntity();
        shelf1.setId(1L);
        shelf1.setName("Fiction");

        ShelfEntity shelf2 = new ShelfEntity();
        shelf2.setId(2L);
        shelf2.setName("Science Fiction");

        book.getShelves().add(shelf1);
        book.getShelves().add(shelf2);

        assertEquals(2, book.getShelves().size());
        assertTrue(book.getShelves().contains(shelf1));
        assertTrue(book.getShelves().contains(shelf2));
    }
}
