package org.booklore.app.controller;

import org.booklore.app.dto.AppFilterOptions;
import org.booklore.app.service.AppBookService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppFilterControllerTest {

    @Mock
    private AppBookService mobileBookService;

    @InjectMocks
    private AppFilterController controller;

    @Test
    void getFilterOptions_noParams_delegatesWithNulls() {
        AppFilterOptions expected = buildOptions(List.of("Author A"), List.of("EPUB"), List.of("en"));
        when(mobileBookService.getFilterOptions(null, null, null)).thenReturn(expected);

        ResponseEntity<AppFilterOptions> response = controller.getFilterOptions(null, null, null);

        assertEquals(200, response.getStatusCode().value());
        assertSame(expected, response.getBody());
        verify(mobileBookService).getFilterOptions(null, null, null);
    }

    @Test
    void getFilterOptions_withLibraryId_passesLibraryId() {
        AppFilterOptions expected = buildOptions(List.of("Author B"), List.of("PDF"), List.of("fr"));
        when(mobileBookService.getFilterOptions(5L, null, null)).thenReturn(expected);

        ResponseEntity<AppFilterOptions> response = controller.getFilterOptions(5L, null, null);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().authors().size());
        assertEquals("Author B", response.getBody().authors().getFirst().name());
        verify(mobileBookService).getFilterOptions(5L, null, null);
    }

    @Test
    void getFilterOptions_withShelfId_passesShelfId() {
        AppFilterOptions expected = buildOptions(List.of(), List.of("EPUB"), List.of());
        when(mobileBookService.getFilterOptions(null, 10L, null)).thenReturn(expected);

        ResponseEntity<AppFilterOptions> response = controller.getFilterOptions(null, 10L, null);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().authors().isEmpty());
        verify(mobileBookService).getFilterOptions(null, 10L, null);
    }

    @Test
    void getFilterOptions_withMagicShelfId_passesMagicShelfId() {
        AppFilterOptions expected = buildOptions(List.of("Author C"), List.of("MOBI"), List.of("de"));
        when(mobileBookService.getFilterOptions(null, null, 7L)).thenReturn(expected);

        ResponseEntity<AppFilterOptions> response = controller.getFilterOptions(null, null, 7L);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("Author C", response.getBody().authors().getFirst().name());
        verify(mobileBookService).getFilterOptions(null, null, 7L);
    }

    private AppFilterOptions buildOptions(List<String> authorNames, List<String> fileTypes, List<String> langCodes) {
        List<AppFilterOptions.CountedOption> authors = authorNames.stream()
                .map(name -> new AppFilterOptions.CountedOption(name, 1L))
                .toList();
        List<AppFilterOptions.LanguageOption> languages = langCodes.stream()
                .map(code -> new AppFilterOptions.LanguageOption(code, code, 1L))
                .toList();
        return AppFilterOptions.builder()
                .authors(authors)
                .languages(languages)
                .fileTypes(fileTypes)
                .readStatuses(List.of("READ", "READING", "UNREAD"))
                .build();
    }
}
