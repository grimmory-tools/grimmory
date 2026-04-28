package org.booklore.acquisition;

import org.booklore.model.dto.BookMetadata;
import org.booklore.repository.BookMetadataRepository;
import org.booklore.service.acquisition.BookDiscoveryService;
import org.booklore.service.metadata.parser.OpenLibraryParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookDiscoveryServiceTest {

    @Mock
    private OpenLibraryParser openLibraryParser;

    @Mock
    private BookMetadataRepository bookMetadataRepository;

    @InjectMocks
    private BookDiscoveryService service;

    @Test
    void searchBooks_delegatesToOpenLibraryParser() {
        List<BookMetadata> expected = List.of(BookMetadata.builder().title("Dune").build());
        when(openLibraryParser.searchByTerm("dune frank herbert", 0)).thenReturn(expected);

        List<BookMetadata> result = service.searchBooks("dune frank herbert", 0);

        assertThat(result).isEqualTo(expected);
        verify(openLibraryParser).searchByTerm("dune frank herbert", 0);
    }

    @Test
    void searchByIsbn_delegatesToOpenLibraryParser() {
        List<BookMetadata> expected = List.of(BookMetadata.builder().isbn13("9780441013593").build());
        when(openLibraryParser.searchByIsbn("9780441013593")).thenReturn(expected);

        List<BookMetadata> result = service.searchByIsbn("9780441013593");

        assertThat(result).isEqualTo(expected);
        verify(openLibraryParser).searchByIsbn("9780441013593");
    }

    @Test
    void searchBooks_page1_forwardsPageToParser() {
        List<BookMetadata> page1 = List.of(BookMetadata.builder().title("Dune Messiah").build());
        when(openLibraryParser.searchByTerm("dune", 1)).thenReturn(page1);

        List<BookMetadata> result = service.searchBooks("dune", 1);

        assertThat(result).isEqualTo(page1);
        verify(openLibraryParser).searchByTerm("dune", 1);
    }

    @Test
    void searchBooks_page0AndPage1_areCachedSeparately() {
        List<BookMetadata> page0 = List.of(BookMetadata.builder().title("Dune").build());
        List<BookMetadata> page1 = List.of(BookMetadata.builder().title("Dune Messiah").build());
        when(openLibraryParser.searchByTerm("dune", 0)).thenReturn(page0);
        when(openLibraryParser.searchByTerm("dune", 1)).thenReturn(page1);

        service.searchBooks("dune", 0);
        service.searchBooks("dune", 0); // cache hit
        service.searchBooks("dune", 1);

        verify(openLibraryParser, times(1)).searchByTerm("dune", 0);
        verify(openLibraryParser, times(1)).searchByTerm("dune", 1);
    }

    @Test
    void searchBooks_emptyQuery_noNpe() {
        when(openLibraryParser.searchByTerm("", 0)).thenReturn(List.of());

        List<BookMetadata> result = service.searchBooks("", 0);

        assertThat(result).isEmpty();
        verify(openLibraryParser).searchByTerm("", 0);
    }

    @Test
    void searchBooks_returnsEmptyList_whenParserReturnsEmpty() {
        when(openLibraryParser.searchByTerm(any(), anyInt())).thenReturn(List.of());

        List<BookMetadata> result = service.searchBooks("completely unknown title xyz", 0);

        assertThat(result).isEmpty();
    }

    @Test
    void getLibraryIsbn13s_returnsSetFromRepository() {
        Set<String> isbns = Set.of("9780441013593", "9780553103540");
        when(bookMetadataRepository.findAllIsbn13s()).thenReturn(isbns);

        Set<String> result = service.getLibraryIsbn13s();

        assertThat(result).containsExactlyInAnyOrder("9780441013593", "9780553103540");
    }

    @Test
    void getLibraryIsbn13s_doesNotContainNulls() {
        when(bookMetadataRepository.findAllIsbn13s()).thenReturn(Set.of("9780441013593"));

        Set<String> result = service.getLibraryIsbn13s();

        assertThat(result).doesNotContainNull();
    }
}
