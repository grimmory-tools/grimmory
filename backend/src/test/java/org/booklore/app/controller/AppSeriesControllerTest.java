package org.booklore.app.controller;

import org.booklore.app.dto.*;
import org.booklore.app.service.AppSeriesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppSeriesControllerTest {

    @Mock
    private AppSeriesService mobileSeriesService;

    @InjectMocks
    private AppSeriesController controller;

    @Test
    void getSeries_defaultParams_delegatesCorrectly() {
        AppPageResponse<AppSeriesSummary> expected = buildSeriesPage();
        when(mobileSeriesService.getSeries(0, 20, "recentlyAdded", "desc", null, null, false))
                .thenReturn(expected);

        ResponseEntity<AppPageResponse<AppSeriesSummary>> response =
                controller.getSeries(0, 20, "recentlyAdded", "desc", null, null, null);

        assertEquals(200, response.getStatusCode().value());
        assertSame(expected, response.getBody());
        verify(mobileSeriesService).getSeries(0, 20, "recentlyAdded", "desc", null, null, false);
    }

    @Test
    void getSeries_withLibraryId_passesLibraryId() {
        AppPageResponse<AppSeriesSummary> expected = buildSeriesPage();
        when(mobileSeriesService.getSeries(0, 20, "recentlyAdded", "desc", 5L, null, false))
                .thenReturn(expected);

        ResponseEntity<AppPageResponse<AppSeriesSummary>> response =
                controller.getSeries(0, 20, "recentlyAdded", "desc", 5L, null, null);

        assertEquals(200, response.getStatusCode().value());
        verify(mobileSeriesService).getSeries(0, 20, "recentlyAdded", "desc", 5L, null, false);
    }

    @Test
    void getSeries_withSearch_passesSearch() {
        AppPageResponse<AppSeriesSummary> expected = buildSeriesPage();
        when(mobileSeriesService.getSeries(0, 20, "recentlyAdded", "desc", null, "harry", false))
                .thenReturn(expected);

        ResponseEntity<AppPageResponse<AppSeriesSummary>> response =
                controller.getSeries(0, 20, "recentlyAdded", "desc", null, "harry", null);

        assertEquals(200, response.getStatusCode().value());
        verify(mobileSeriesService).getSeries(0, 20, "recentlyAdded", "desc", null, "harry", false);
    }

    @Test
    void getSeries_withInProgressStatus_setsInProgressTrue() {
        AppPageResponse<AppSeriesSummary> expected = buildSeriesPage();
        when(mobileSeriesService.getSeries(0, 20, "recentlyAdded", "desc", null, null, true))
                .thenReturn(expected);

        ResponseEntity<AppPageResponse<AppSeriesSummary>> response =
                controller.getSeries(0, 20, "recentlyAdded", "desc", null, null, "in-progress");

        assertEquals(200, response.getStatusCode().value());
        verify(mobileSeriesService).getSeries(0, 20, "recentlyAdded", "desc", null, null, true);
    }

    @Test
    void getSeries_withUnknownStatus_treatedAsNotInProgress() {
        AppPageResponse<AppSeriesSummary> expected = buildSeriesPage();
        when(mobileSeriesService.getSeries(0, 20, "recentlyAdded", "desc", null, null, false))
                .thenReturn(expected);

        ResponseEntity<AppPageResponse<AppSeriesSummary>> response =
                controller.getSeries(0, 20, "recentlyAdded", "desc", null, null, "completed");

        assertEquals(200, response.getStatusCode().value());
        verify(mobileSeriesService).getSeries(0, 20, "recentlyAdded", "desc", null, null, false);
    }

    @Test
    void getSeriesBooks_defaultParams_delegatesCorrectly() {
        AppPageResponse<AppBookSummary> expected = buildBookPage();
        when(mobileSeriesService.getSeriesBooks("Dune", 0, 20, "seriesNumber", "asc", null))
                .thenReturn(expected);

        ResponseEntity<AppPageResponse<AppBookSummary>> response =
                controller.getSeriesBooks("Dune", 0, 20, "seriesNumber", "asc", null);

        assertEquals(200, response.getStatusCode().value());
        assertSame(expected, response.getBody());
        verify(mobileSeriesService).getSeriesBooks("Dune", 0, 20, "seriesNumber", "asc", null);
    }

    @Test
    void getSeriesBooks_withLibraryId_passesLibraryId() {
        AppPageResponse<AppBookSummary> expected = buildBookPage();
        when(mobileSeriesService.getSeriesBooks("Dune", 0, 20, "seriesNumber", "asc", 5L))
                .thenReturn(expected);

        ResponseEntity<AppPageResponse<AppBookSummary>> response =
                controller.getSeriesBooks("Dune", 0, 20, "seriesNumber", "asc", 5L);

        assertEquals(200, response.getStatusCode().value());
        verify(mobileSeriesService).getSeriesBooks("Dune", 0, 20, "seriesNumber", "asc", 5L);
    }

    @Test
    void getSeriesBooks_encodedSeriesName_passedAsIs() {
        AppPageResponse<AppBookSummary> expected = buildBookPage();
        when(mobileSeriesService.getSeriesBooks("A Song of Ice and Fire", 0, 20, "seriesNumber", "asc", null))
                .thenReturn(expected);

        ResponseEntity<AppPageResponse<AppBookSummary>> response =
                controller.getSeriesBooks("A Song of Ice and Fire", 0, 20, "seriesNumber", "asc", null);

        assertEquals(200, response.getStatusCode().value());
        verify(mobileSeriesService).getSeriesBooks("A Song of Ice and Fire", 0, 20, "seriesNumber", "asc", null);
    }

    // ---- Helpers ----

    private AppPageResponse<AppSeriesSummary> buildSeriesPage() {
        AppSeriesSummary summary = AppSeriesSummary.builder()
                .seriesName("Test Series")
                .bookCount(5)
                .seriesTotal(5)
                .booksRead(2)
                .authors(List.of("Author A"))
                .latestAddedOn(Instant.now())
                .coverBooks(List.of())
                .build();
        return AppPageResponse.of(List.of(summary), 0, 20, 1);
    }

    private AppPageResponse<AppBookSummary> buildBookPage() {
        AppBookSummary summary = AppBookSummary.builder()
                .id(1L)
                .title("Test Book")
                .authors(List.of("Author A"))
                .seriesName("Dune")
                .seriesNumber(1.0f)
                .build();
        return AppPageResponse.of(List.of(summary), 0, 20, 1);
    }
}
