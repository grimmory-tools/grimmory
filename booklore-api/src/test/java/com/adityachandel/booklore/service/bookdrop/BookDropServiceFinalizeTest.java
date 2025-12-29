package com.adityachandel.booklore.service.bookdrop;

import com.adityachandel.booklore.config.AppProperties;
import com.adityachandel.booklore.model.dto.request.BookdropFinalizeRequest;
import com.adityachandel.booklore.repository.BookdropFileRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.file.FileMovingHelper;
import com.adityachandel.booklore.service.monitoring.MonitoringRegistrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookDropServiceFinalizeTest {

    @Mock
    private BookdropFileRepository bookdropFileRepository;
    @Mock
    private BookdropMonitoringService bookdropMonitoringService;
    @Mock
    private LibraryRepository libraryRepository;
    @Mock
    private MonitoringRegistrationService monitoringRegistrationService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private FileMovingHelper fileMovingHelper;
    @Mock
    private AppProperties appProperties;
    @Mock
    private BookdropNotificationService bookdropNotificationService;

    @InjectMocks
    private BookDropService bookDropService;

    @Test
    void finalizeImport_selectAll_emptyExcludedIds_shouldCallFindAllIds() {
        BookdropFinalizeRequest request = new BookdropFinalizeRequest();
        request.setSelectAll(true);
        request.setExcludedIds(Collections.emptyList());
        request.setDefaultLibraryId(1L);
        request.setDefaultPathId(1L);

        when(bookdropFileRepository.findAllIds()).thenReturn(List.of(1L, 2L));
        when(bookdropFileRepository.findAllById(anyList())).thenReturn(Collections.emptyList()); // Mock chunk processing

        bookDropService.finalizeImport(request);

        verify(bookdropFileRepository).findAllIds();
        verify(bookdropFileRepository, never()).findAllExcludingIdsFlat(anyList());
    }

    @Test
    void finalizeImport_selectAll_withExcludedIds_shouldCallFindAllExcludingIdsFlat() {
        BookdropFinalizeRequest request = new BookdropFinalizeRequest();
        request.setSelectAll(true);
        request.setExcludedIds(List.of(3L));
        request.setDefaultLibraryId(1L);
        request.setDefaultPathId(1L);

        when(bookdropFileRepository.findAllExcludingIdsFlat(anyList())).thenReturn(List.of(1L, 2L));
        when(bookdropFileRepository.findAllById(anyList())).thenReturn(Collections.emptyList()); // Mock chunk processing

        bookDropService.finalizeImport(request);

        verify(bookdropFileRepository).findAllExcludingIdsFlat(List.of(3L));
        verify(bookdropFileRepository, never()).findAllIds();
    }
}
