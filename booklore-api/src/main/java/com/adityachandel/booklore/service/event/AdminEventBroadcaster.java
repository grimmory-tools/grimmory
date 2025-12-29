package com.adityachandel.booklore.service.event;

import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.websocket.LogNotification;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.service.user.UserService;
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
