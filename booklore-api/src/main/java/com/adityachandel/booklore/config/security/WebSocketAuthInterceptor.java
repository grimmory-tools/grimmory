package com.adityachandel.booklore.config.security;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@AllArgsConstructor
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtUtils jwtUtils;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            List<String> authHeaders = accessor.getNativeHeader("Authorization");

            if (authHeaders == null || authHeaders.isEmpty()) {
                log.debug("WebSocket connection rejected: No Authorization header");
                throw new IllegalArgumentException("Missing Authorization header");
            }

            String token = authHeaders.get(0).replace("Bearer ", "");
            Authentication auth = authenticateToken(token);

            if (auth == null) {
                log.debug("WebSocket connection rejected: Invalid token");
                throw new IllegalArgumentException("Invalid Authorization token");
            }

            accessor.setUser(auth);
            log.debug("WebSocket authentication successful for user: {}", auth.getName());
        }

        return message;
    }

    private Authentication authenticateToken(String token) {
        if (!jwtUtils.validateToken(token)) {
            return null;
        }
        String username = jwtUtils.extractUsername(token);
        List<SimpleGrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));

        return new UsernamePasswordAuthenticationToken(username, null, authorities);
    }
}