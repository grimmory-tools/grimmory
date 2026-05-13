package org.booklore.service.bookdrop;

import org.booklore.model.dto.BookdropFile;
import org.booklore.model.dto.BookdropFileNotification;
import org.booklore.model.entity.BookdropFileEntity;
import org.booklore.model.enums.PermissionType;
import org.booklore.model.websocket.Topic;
import org.booklore.repository.BookdropFileRepository;
import org.booklore.service.NotificationService;
import org.booklore.mapper.BookdropFileMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

@Slf4j
@Service
@AllArgsConstructor
public class BookdropNotificationService {

    private final BookdropFileRepository bookdropFileRepository;
    private final NotificationService notificationService;
    private final BookdropFileMapper bookdropFileMapper;

    @Transactional(readOnly = true)
    public void sendBookdropFileSummaryNotification() {
        sendBookdropFileSummaryNotification(null);
    }

    @Transactional(readOnly = true)
    public void sendBookdropFileSummaryNotification(BookdropFileEntity addedEntity) {
        log.info("Sending bookdrop file summary notification. Added entity ID: {}", addedEntity != null ? addedEntity.getId() : "none");
        long pendingCount = bookdropFileRepository.countByStatus(BookdropFileEntity.Status.PENDING_REVIEW);
        long totalCount = bookdropFileRepository.count();

        BookdropFileNotification summaryNotification = new BookdropFileNotification(
                (int) pendingCount,
                (int) totalCount,
                Instant.now().toString()
        );

        if (addedEntity != null) {
            summaryNotification.setAddedFile(bookdropFileMapper.toDto(addedEntity));
        }

        notificationService.sendMessageToPermissions(Topic.BOOKDROP_FILE, summaryNotification, Set.of(PermissionType.ADMIN, PermissionType.MANAGE_LIBRARY, PermissionType.ACCESS_BOOKDROP));
    }

    @Transactional(readOnly = true)
    public void sendBookdropFileAddedNotification(BookdropFileEntity entity) {
        log.info("Sending bookdrop file added notification for entity ID: {}", entity.getId());
        BookdropFile dto = bookdropFileMapper.toDto(entity);
        notificationService.sendMessageToPermissions(Topic.BOOKDROP_ADD, dto, Set.of(PermissionType.ADMIN, PermissionType.MANAGE_LIBRARY, PermissionType.ACCESS_BOOKDROP));
    }
}
