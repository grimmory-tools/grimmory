package com.adityachandel.booklore.service;

import com.adityachandel.booklore.model.dto.BookdropFileNotification;
import com.adityachandel.booklore.model.entity.BookdropFileEntity;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.BookdropFileRepository;
import com.adityachandel.booklore.service.bookdrop.BookdropNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class BookdropNotificationServiceTest {

    private BookdropFileRepository bookdropFileRepository;
    private NotificationService notificationService;

    private BookdropNotificationService bookdropNotificationService;

    @BeforeEach
    void setup() {
        bookdropFileRepository = mock(BookdropFileRepository.class);
        notificationService = mock(NotificationService.class);

        bookdropNotificationService = new BookdropNotificationService(bookdropFileRepository, notificationService);
    }

    @Test
    void sendBookdropFileSummaryNotification_shouldSendCorrectNotification() {
        long pendingCount = 5L;
        long totalCount = 20L;

        when(bookdropFileRepository.countByStatus(BookdropFileEntity.Status.PENDING_REVIEW)).thenReturn(pendingCount);
        when(bookdropFileRepository.count()).thenReturn(totalCount);

        bookdropNotificationService.sendBookdropFileSummaryNotification();

        ArgumentCaptor<BookdropFileNotification> captor = ArgumentCaptor.forClass(BookdropFileNotification.class);
        verify(notificationService).sendMessageToPermissions(eq(Topic.BOOKDROP_FILE), captor.capture(), anySet());

        BookdropFileNotification sentNotification = captor.getValue();

        assertThat(sentNotification.getPendingCount()).isEqualTo((int) pendingCount);
        assertThat(sentNotification.getTotalCount()).isEqualTo((int) totalCount);
        assertThat(Instant.parse(sentNotification.getLastUpdatedAt())).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void sendBookdropFileSummaryNotification_shouldSendEvenIfCountsAreZero() {
        when(bookdropFileRepository.countByStatus(BookdropFileEntity.Status.PENDING_REVIEW)).thenReturn(0L);
        when(bookdropFileRepository.count()).thenReturn(0L);

        bookdropNotificationService.sendBookdropFileSummaryNotification();

        verify(notificationService).sendMessageToPermissions(eq(Topic.BOOKDROP_FILE), any(BookdropFileNotification.class), anySet());
    }
}