package com.adityachandel.booklore.service.event;

import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.service.user.UserService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import static com.adityachandel.booklore.model.websocket.LogNotification.createLogNotification;

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
                    messagingTemplate.convertAndSendToUser(username, Topic.LOG.getPath(), createLogNotification("Book added: " + book.getFileName()));
                });
    }
}
