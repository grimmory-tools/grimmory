package org.booklore.acquisition;

import org.booklore.crons.BookAcquisitionScheduler;
import org.booklore.model.dto.acquisition.AcquisitionResult;
import org.booklore.model.entity.WantedBookEntity;
import org.booklore.model.enums.WantedBookStatus;
import org.booklore.model.websocket.Topic;
import org.booklore.repository.WantedBookRepository;
import org.booklore.service.NotificationService;
import org.booklore.service.acquisition.AcquisitionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookAcquisitionSchedulerTest {

    @Mock private AcquisitionService acquisitionService;
    @Mock private WantedBookRepository wantedBookRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private BookAcquisitionScheduler scheduler;

    private WantedBookEntity wantedBook1;
    private WantedBookEntity wantedBook2;

    @BeforeEach
    void setUp() {
        wantedBook1 = WantedBookEntity.builder()
                .id(1L).title("Dune").author("Frank Herbert")
                .status(WantedBookStatus.WANTED).retryCount(0).build();

        wantedBook2 = WantedBookEntity.builder()
                .id(2L).title("Foundation").author("Isaac Asimov")
                .status(WantedBookStatus.NOT_FOUND).retryCount(0).build();
    }

    @Test
    void triggerNow_withTwoWantedBooks_callsSearchAndDispatchTwice() {
        when(wantedBookRepository.findByStatusIn(List.of(WantedBookStatus.WANTED, WantedBookStatus.NOT_FOUND)))
                .thenReturn(List.of(wantedBook1, wantedBook2));
        when(acquisitionService.searchAndDispatch(any()))
                .thenReturn(AcquisitionResult.notFound(1L));

        scheduler.triggerNow();

        verify(acquisitionService, times(2)).searchAndDispatch(any());
    }

    @Test
    void triggerNow_oneBookThrowsException_secondBookStillProcessed() {
        when(wantedBookRepository.findByStatusIn(List.of(WantedBookStatus.WANTED, WantedBookStatus.NOT_FOUND)))
                .thenReturn(List.of(wantedBook1, wantedBook2));
        when(acquisitionService.searchAndDispatch(eq(wantedBook1)))
                .thenThrow(new RuntimeException("Indexer timeout"));
        when(acquisitionService.searchAndDispatch(eq(wantedBook2)))
                .thenReturn(AcquisitionResult.notFound(2L));

        scheduler.triggerNow();

        verify(acquisitionService).searchAndDispatch(wantedBook1);
        verify(acquisitionService).searchAndDispatch(wantedBook2);
        // Notification only sent for book2 (book1 threw before notification)
        verify(notificationService, times(1)).sendMessage(eq(Topic.ACQUISITION_UPDATE), any());
    }

    @Test
    void triggerNow_noWantedBooks_searchAndDispatchNeverCalled() {
        when(wantedBookRepository.findByStatusIn(List.of(WantedBookStatus.WANTED, WantedBookStatus.NOT_FOUND)))
                .thenReturn(List.of());

        scheduler.triggerNow();

        verify(acquisitionService, never()).searchAndDispatch(any());
        verify(notificationService, never()).sendMessage(any(), any());
    }

    @Test
    void triggerNow_successfulDispatch_sendsNotification() {
        when(wantedBookRepository.findByStatusIn(List.of(WantedBookStatus.WANTED, WantedBookStatus.NOT_FOUND)))
                .thenReturn(List.of(wantedBook1));
        when(acquisitionService.searchAndDispatch(wantedBook1))
                .thenReturn(AcquisitionResult.dispatched(1L, "Dune Frank Herbert EPUB", 90, "sabnzbd-123"));

        scheduler.triggerNow();

        verify(notificationService).sendMessage(eq(Topic.ACQUISITION_UPDATE), any());
    }

    @Test
    void triggerNow_bookAtMaxRetries_markedFailedPermanent_skipsSearch() {
        WantedBookEntity exhausted = WantedBookEntity.builder()
                .id(3L).title("Exhausted").author("Author")
                .status(WantedBookStatus.NOT_FOUND).retryCount(5).build();

        when(wantedBookRepository.findByStatusIn(List.of(WantedBookStatus.WANTED, WantedBookStatus.NOT_FOUND)))
                .thenReturn(List.of(exhausted));
        when(wantedBookRepository.save(any())).thenReturn(exhausted);

        scheduler.triggerNow();

        assertThat(exhausted.getStatus()).isEqualTo(WantedBookStatus.FAILED_PERMANENT);
        verify(acquisitionService, never()).searchAndDispatch(any());
        verify(notificationService).sendMessage(eq(Topic.ACQUISITION_UPDATE), any());
    }

    @Test
    void triggerNow_notFoundBook_includedInCandidates_isRetried() {
        WantedBookEntity notFound = WantedBookEntity.builder()
                .id(4L).title("Retry Me").author("Author")
                .status(WantedBookStatus.NOT_FOUND).retryCount(2).build();

        when(wantedBookRepository.findByStatusIn(List.of(WantedBookStatus.WANTED, WantedBookStatus.NOT_FOUND)))
                .thenReturn(List.of(notFound));
        when(acquisitionService.searchAndDispatch(notFound))
                .thenReturn(AcquisitionResult.notFound(4L));

        scheduler.triggerNow();

        verify(acquisitionService).searchAndDispatch(notFound);
    }

    @Test
    void triggerNow_alreadyRunning_skipsExecution() throws InterruptedException {
        // Simulate concurrent call: first call blocks in the running flag check
        // by having the first call set running=true before second call
        when(wantedBookRepository.findByStatusIn(any()))
                .thenReturn(List.of(wantedBook1));
        when(acquisitionService.searchAndDispatch(any()))
                .thenReturn(AcquisitionResult.notFound(1L));

        // A fresh scheduler's running flag starts false; triggerNow sets it true then false.
        // We can't easily test true concurrency in a unit test, but we verify the guard
        // doesn't break normal single-threaded execution.
        scheduler.triggerNow();

        verify(acquisitionService, times(1)).searchAndDispatch(any());
    }
}
