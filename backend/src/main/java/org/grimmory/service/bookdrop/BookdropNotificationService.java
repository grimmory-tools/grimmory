package org.grimmory.service.bookdrop;

import org.grimmory.model.dto.BookdropFileNotification;
import org.grimmory.model.entity.BookdropFileEntity;
import org.grimmory.model.enums.PermissionType;
import org.grimmory.model.websocket.Topic;
import org.grimmory.repository.BookdropFileRepository;
import org.grimmory.service.NotificationService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
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

        notificationService.sendMessageToPermissions(Topic.BOOKDROP_FILE, summaryNotification, Set.of(PermissionType.ADMIN, PermissionType.MANAGE_LIBRARY));
    }
}
