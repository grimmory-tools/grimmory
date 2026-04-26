package org.grimmory.config.security.interceptor;

import org.booklore.config.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Set<String> ALLOWED_DEST_PREFIXES = Set.of("/topic/", "/user/queue/");

    private final JwtUtils jwtUtils;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        switch (accessor.getCommand()) {
            case CONNECT    -> handleConnect(accessor);
            case SUBSCRIBE  -> validateSubscription(accessor);
            case SEND       -> rejectSend(accessor);
            case DISCONNECT -> log.debug("Client disconnected: {}", accessor.getUser());
            default         -> { /* pass through */ }
        }

        return message;
    }

    private void handleConnect(StompHeaderAccessor accessor) {
        List<String> authHeaders = accessor.getNativeHeader("Authorization");
        if (authHeaders == null || authHeaders.isEmpty()) {
            throw new MessagingException("Missing Authorization header");
        }

        String raw = authHeaders.getFirst().trim();
        if (!raw.startsWith(BEARER_PREFIX)) {
            throw new MessagingException("Malformed Authorization header");
        }

        String token = raw.substring(BEARER_PREFIX.length());
        Authentication auth = authenticateToken(token);
        if (auth == null) {
            throw new MessagingException("Invalid or expired token");
        }

        accessor.setUser(auth);
        log.debug("WebSocket authenticated: {}", auth.getName());
    }

    private void validateSubscription(StompHeaderAccessor accessor) {
        requireAuthenticated(accessor);

        String destination = accessor.getDestination();
        if (destination == null || destination.contains("..")) {
            throw new MessagingException("Invalid subscription destination");
        }

        boolean allowed = ALLOWED_DEST_PREFIXES.stream().anyMatch(destination::startsWith);
        if (!allowed) {
            log.warn("Subscription rejected: user={} dest='{}'", accessor.getUser(), destination);
            throw new MessagingException("Destination '" + destination + "' is not allowed");
        }
    }

    private void rejectSend(StompHeaderAccessor accessor) {
        log.warn("SEND rejected: user={} dest='{}'", accessor.getUser(), accessor.getDestination());
        throw new MessagingException("Client SEND is not permitted");
    }

    private void requireAuthenticated(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) {
            throw new MessagingException("Not authenticated");
        }
    }

    private Authentication authenticateToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            if (jwtUtils.validateToken(token)) {
                String username = jwtUtils.extractUsername(token);
                if (username != null && !username.isBlank()) {
                    return new UsernamePasswordAuthenticationToken(username, null, List.of());
                }
            }
        } catch (Exception e) {
            log.debug("Token validation failed: {}", e.getMessage());
        }
        return null;
    }
}
