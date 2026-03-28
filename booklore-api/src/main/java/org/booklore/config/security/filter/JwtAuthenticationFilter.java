package org.booklore.config.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.config.security.JwtUtils;
import org.booklore.config.security.userdetails.UserAuthenticationDetails;
import org.booklore.mapper.custom.BookLoreUserTransformer;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.repository.UserRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@AllArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final BookLoreUserTransformer bookLoreUserTransformer;
    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;

    private static final List<String> WHITELISTED_PATHS = List.of(
            "/api/v1/opds/",
            "/api/v2/opds/",
            "/api/v1/auth/refresh",
            "/api/v1/setup/",
            "/api/kobo/"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        String token = extractToken(request);
        String path = request.getRequestURI();

        boolean isWhitelisted = WHITELISTED_PATHS.stream().anyMatch(path::startsWith);
        if (isWhitelisted) {
            chain.doFilter(request, response);
            return;
        }

        if (token == null) {
            chain.doFilter(request, response);
            return;
        }

        try {
            if (jwtUtils.validateToken(token)) {
                authenticateUser(token, request);
            } else {
                log.debug("Invalid token. Rejecting request.");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        } catch (Exception ex) {
            log.error("Authentication error: {}", ex.getMessage(), ex);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        chain.doFilter(request, response);
    }

    private void authenticateUser(String token, HttpServletRequest request) {
        Long userId = jwtUtils.extractUserId(token);
        BookLoreUserEntity entity = userRepository.findByIdWithDetails(userId).orElseThrow(() -> new UsernameNotFoundException("User not found with ID: " + userId));
        BookLoreUser user = bookLoreUserTransformer.toDTO(entity);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(user, null, null);
        authentication.setDetails(new UserAuthenticationDetails(request, user.getId()));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        return (bearer != null && bearer.startsWith("Bearer ")) ? bearer.substring(7) : null;
    }
}
