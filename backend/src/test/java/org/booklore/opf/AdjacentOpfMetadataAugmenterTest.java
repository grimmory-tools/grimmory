package org.booklore.opf;

import org.booklore.model.dto.BookMetadata;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.service.book.BookCreatorService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AdjacentOpfMetadataAugmenterTest {

    private final BookCreatorService bookCreatorService = mock(BookCreatorService.class);
    private final AdjacentOpfMetadataAugmenter augmenter = new AdjacentOpfMetadataAugmenter(
            mock(AdjacentOpfLocator.class),
            mock(OpfMetadataExtractor.class),
            bookCreatorService
    );

    @Test
    void appliesAllowedFieldsWithoutClearingMissingFields() {
        var book = book();
        book.getMetadata().setDescription("Existing description");

        augmenter.apply(BookMetadata.builder()
                .title("OPF Title")
                .publisher("OPF Publisher")
                .publishedDate(LocalDate.of(2026, 1, 1))
                .language("th")
                .isbn10("1234567890")
                .seriesName("Series")
                .seriesNumber(3.0f)
                .authors(List.of("Author"))
                .categories(Set.of("Category"))
                .build(), book);

        assertThat(book.getMetadata().getTitle()).isEqualTo("OPF Title");
        assertThat(book.getMetadata().getPublisher()).isEqualTo("OPF Publisher");
        assertThat(book.getMetadata().getPublishedDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(book.getMetadata().getLanguage()).isEqualTo("th");
        assertThat(book.getMetadata().getIsbn10()).isEqualTo("1234567890");
        assertThat(book.getMetadata().getSeriesName()).isEqualTo("Series");
        assertThat(book.getMetadata().getSeriesNumber()).isEqualTo(3.0f);
        assertThat(book.getMetadata().getDescription()).isEqualTo("Existing description");
        verify(bookCreatorService).addAuthorsToBook(List.of("Author"), book);
        verify(bookCreatorService).addCategoriesToBook(Set.of("Category"), book);
    }

    @Test
    void respectsLockedFields() {
        var book = book();
        book.getMetadata().setTitle("Locked title");
        book.getMetadata().setTitleLocked(true);

        augmenter.apply(BookMetadata.builder().title("OPF Title").build(), book);

        assertThat(book.getMetadata().getTitle()).isEqualTo("Locked title");
    }

    private BookEntity book() {
        var book = new BookEntity();
        book.setMetadata(new BookMetadataEntity());
        return book;
    }
}
