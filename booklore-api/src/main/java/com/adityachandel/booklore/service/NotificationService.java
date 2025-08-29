package com.adityachandel.booklore.service;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.model.entity.UserPermissionsEntity;
import com.adityachandel.booklore.model.enums.PermissionType;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.UserRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.adityachandel.booklore.util.UserPermissionUtils.hasPermission;

@Slf4j
@Service
@AllArgsConstructor
public class NotificationService {

    private final SimpMessagingTemplate messagingTemplate;
    private final AuthenticationService authenticationService;
    private final UserRepository userRepository;

    public void sendMessage(Topic topic, Object message) {
        try {
            var user = authenticationService.getAuthenticatedUser();
            if (user == null) {
                log.warn("No authenticated user found. Message not sent: {}", topic);
                return;
            }
            String username = user.getUsername();
            messagingTemplate.convertAndSendToUser(username, topic.getPath(), message);
        } catch (Exception e) {
            log.error("Error sending message to topic {}: {}", topic, e.getMessage(), e);
        }
    }

    public void sendMessageToPermissions(Topic topic, Object message, Set<PermissionType> permissionTypes) {
        if (permissionTypes == null || permissionTypes.isEmpty()) return;

        Set<PermissionType> permissionSet = EnumSet.noneOf(PermissionType.class);
        permissionSet.addAll(permissionTypes);

        try {
            List<BookLoreUserEntity> users = userRepository.findAll();
            for (BookLoreUserEntity user : users) {
                UserPermissionsEntity perms = user.getPermissions();
                if (perms != null) {
                    for (PermissionType p : permissionSet) {
                        if (hasPermission(perms, p)) {
                            messagingTemplate.convertAndSendToUser(user.getUsername(), topic.getPath(), message);
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error sending message to users with permissions {}: {}", permissionSet, e.getMessage(), e);
        }
    }
}