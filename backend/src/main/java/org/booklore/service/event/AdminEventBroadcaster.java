package org.booklore.service.event;

import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.websocket.LogNotification;
import org.booklore.model.websocket.Topic;
import org.booklore.service.user.UserService;
import lombok.AllArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@AllArgsConstructor
@Service
public class AdminEventBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;
    private final UserService userService;

    public void broadcastAdminEvent(String message) {
        List<BookLoreUser> admins = userService.getBookLoreUsers().stream()
                .filter(u -> u.getPermissions().isAdmin())
                .toList();
        for (BookLoreUser admin : admins) {
            messagingTemplate.convertAndSendToUser(admin.getUsername(), Topic.LOG.getPath(), LogNotification.info(message));
        }
    }
}
