package org.booklore.service.book;

import org.booklore.mapper.v2.BookMapperV2;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.Shelf;
import org.booklore.model.entity.BookEntity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BookQueryServiceTest {

    @Test
    void mapEntitiesToDto_keepsPublicShelvesForUser() {
        BookMapperV2 bookMapperV2 = mock(BookMapperV2.class);
        BookQueryService bookQueryService = new BookQueryService(null, bookMapperV2, null, null);
        BookEntity bookEntity = BookEntity.builder().id(1L).build();
        Shelf ownShelf = Shelf.builder().id(1L).userId(1L).publicShelf(false).build();
        Shelf otherPrivateShelf = Shelf.builder().id(2L).userId(2L).publicShelf(false).build();
        Shelf otherPublicShelf = Shelf.builder().id(3L).userId(2L).publicShelf(true).build();
        when(bookMapperV2.toDTO(bookEntity)).thenReturn(Book.builder()
                .shelves(Set.of(ownShelf, otherPrivateShelf, otherPublicShelf))
                .build());

        List<Book> result = bookQueryService.mapEntitiesToDto(List.of(bookEntity), true, 1L);

        assertEquals(Set.of(ownShelf, otherPublicShelf), result.getFirst().getShelves());
    }
}
