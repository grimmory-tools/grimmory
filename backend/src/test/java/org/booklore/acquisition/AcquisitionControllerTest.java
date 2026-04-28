package org.booklore.acquisition;

import org.booklore.controller.AcquisitionController;
import org.booklore.crons.BookAcquisitionScheduler;
import org.booklore.exception.APIException;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.acquisition.AddToWantedRequest;
import org.booklore.model.dto.acquisition.JobHistoryDTO;
import org.booklore.model.dto.acquisition.WantedBookDTO;
import org.booklore.model.enums.WantedBookStatus;
import org.booklore.service.acquisition.AcquisitionConfigService;
import org.booklore.service.acquisition.AcquisitionService;
import org.booklore.service.acquisition.BookDiscoveryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AcquisitionControllerTest {

    @Mock
    private AcquisitionConfigService acquisitionConfigService;

    @Mock
    private BookDiscoveryService bookDiscoveryService;

    @Mock
    private AcquisitionService acquisitionService;

    @Mock
    private BookAcquisitionScheduler bookAcquisitionScheduler;

    @InjectMocks
    private AcquisitionController controller;

    // ─── Discovery search ─────────────────────────────────────────────────────

    @Test
    void search_withQuery_returns200() {
        List<BookMetadata> results = List.of(BookMetadata.builder().title("Dune").build());
        when(bookDiscoveryService.searchBooks("dune", 0)).thenReturn(results);

        ResponseEntity<List<BookMetadata>> response = controller.search("dune", null, 0);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getTitle()).isEqualTo("Dune");
    }

    @Test
    void search_withIsbn_returns200() {
        List<BookMetadata> results = List.of(
                BookMetadata.builder().title("Dune").isbn13("9780441013593").build());
        when(bookDiscoveryService.searchByIsbn("9780441013593")).thenReturn(results);

        ResponseEntity<List<BookMetadata>> response = controller.search(null, "9780441013593", 0);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().get(0).getIsbn13()).isEqualTo("9780441013593");
    }

    @Test
    void search_withNoParams_throwsApiException() {
        assertThatThrownBy(() -> controller.search(null, null, 0))
                .isInstanceOf(APIException.class);
    }

    @Test
    void search_withEmptyQuery_throwsApiException() {
        assertThatThrownBy(() -> controller.search("", null, 0))
                .isInstanceOf(APIException.class);
    }

    @Test
    void search_noResults_returns200WithEmptyList() {
        when(bookDiscoveryService.searchBooks("xyzzy unknown", 0)).thenReturn(List.of());

        ResponseEntity<List<BookMetadata>> response = controller.search("xyzzy unknown", null, 0);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void libraryIsbns_returns200() {
        when(bookDiscoveryService.getLibraryIsbn13s()).thenReturn(Set.of("9780441013593"));

        ResponseEntity<Set<String>> response = controller.getLibraryIsbn13s();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("9780441013593");
    }

    // ─── Wanted books ─────────────────────────────────────────────────────────

    @Test
    void addToWanted_returns201() {
        AddToWantedRequest req = new AddToWantedRequest(
                "Dune", "Frank Herbert", "9780441013593", null, "Google", "vol123", null);
        WantedBookDTO dto = new WantedBookDTO(1L, "Dune", "Frank Herbert",
                "9780441013593", null, "Google", "vol123", null,
                WantedBookStatus.WANTED, null, null, null, Instant.now());
        when(acquisitionService.addToWanted(any())).thenReturn(dto);

        ResponseEntity<WantedBookDTO> response = controller.addToWanted(req);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody().title()).isEqualTo("Dune");
    }

    @Test
    void getWantedBooks_returns200() {
        when(acquisitionService.getWantedBooks()).thenReturn(List.of());

        ResponseEntity<List<WantedBookDTO>> response = controller.getWantedBooks();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void removeWanted_returns204() {
        doNothing().when(acquisitionService).removeWanted(1L);

        ResponseEntity<Void> response = controller.removeWanted(1L);

        assertThat(response.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void triggerSearch_returns202() {
        doNothing().when(acquisitionService).triggerSearch(1L);

        ResponseEntity<Void> response = controller.triggerSearch(1L);

        assertThat(response.getStatusCode().value()).isEqualTo(202);
    }

    @Test
    void getJobHistory_returns200() {
        when(acquisitionService.getJobHistory(1L)).thenReturn(List.of());

        ResponseEntity<List<JobHistoryDTO>> response = controller.getJobHistory(1L);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEmpty();
    }
}
