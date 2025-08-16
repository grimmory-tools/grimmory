package com.adityachandel.booklore.service.bookdrop;

import com.adityachandel.booklore.model.dto.BookdropFileNotification;
import com.adityachandel.booklore.model.entity.BookdropFileEntity;
import com.adityachandel.booklore.model.enums.PermissionType;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.BookdropFileRepository;
import com.adityachandel.booklore.service.NotificationService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
@AllArgsConstructor
public class BookdropNotificationService {

    private final BookdropFileRepository bookdropFileRepository;
    private final NotificationService notificationService;

    public void sendBookdropFileSummaryNotification() {
        long pendingCount = bookdropFileRepository.countByStatus(BookdropFileEntity.Status.PENDING_REVIEW);
        long totalCount = bookdropFileRepository.count();

        BookdropFileNotification summaryNotification = new BookdropFileNotification(
                (int) pendingCount,
                (int) totalCount,
                Instant.now().toString()
        );

        notificationService.sendMessageToPermissions(Topic.BOOKDROP_FILE, summaryNotification, Set.of(PermissionType.ADMIN, PermissionType.MANIPULATE_LIBRARY));
    }
}
