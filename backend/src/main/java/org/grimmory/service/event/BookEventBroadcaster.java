package org.grimmory.service.event;

import org.grimmory.model.dto.Book;
import org.grimmory.model.websocket.LogNotification;
import org.grimmory.model.websocket.Topic;
import org.grimmory.service.user.UserService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@AllArgsConstructor
@Service
public class BookEventBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;
    private final UserService userService;

    public void broadcastBookAddEvent(Book book) {
        Long libraryId = book.getLibraryId();
        userService.getBookLoreUsers().stream()
                .filter(u -> u.getPermissions().isAdmin() || u.getAssignedLibraries().stream()
                        .anyMatch(lib -> lib.getId().equals(libraryId)))
                .forEach(u -> {
                    String username = u.getUsername();
                    messagingTemplate.convertAndSendToUser(username, Topic.BOOK_ADD.getPath(), book);
                    messagingTemplate.convertAndSendToUser(username, Topic.LOG.getPath(), LogNotification.info("Book added: " + (book.getPrimaryFile() != null ? book.getPrimaryFile().getFileName() : "unknown")));
                });
    }
}
