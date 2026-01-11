package org.booklore.config.security.filter;

import org.booklore.config.security.userdetails.KoreaderUserDetails;
import org.booklore.mapper.custom.BookLoreUserTransformer;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.repository.KoreaderUserRepository;
import org.booklore.repository.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
@Component
@Slf4j
public class KoreaderAuthFilter extends OncePerRequestFilter {

    private final KoreaderUserRepository koreaderUserRepository;
    private final UserRepository userRepository;
    private final BookLoreUserTransformer bookLoreUserTransformer;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {

        String path = request.getRequestURI();
        log.info("KoreaderAuthFilter: Processing request to: {}", path);

        if (!path.startsWith("/api/koreader/") && !path.equals("/api/v1/reading-sessions")) {
            log.info("KoreaderAuthFilter: Path does not match /api/koreader/** or /api/v1/reading-sessions, passing through");
            chain.doFilter(request, response);
            return;
        }

        String username = request.getHeader("x-auth-user");
        String key = request.getHeader("x-auth-key");

        log.info("KoreaderAuthFilter: x-auth-user={}, x-auth-key present={}", username, key != null);

        if (username != null && key != null) {
            koreaderUserRepository.findByUsername(username).ifPresentOrElse(user -> {
                if (user.getPasswordMD5().equalsIgnoreCase(key)) {
                    log.info("KoreaderAuthFilter: Authentication successful for user: {}", username);

                    if (user.getBookLoreUser() != null) {
                        Long bookLoreUserId = user.getBookLoreUser().getId();

                        // Load the full BookLoreUser entity and convert to DTO
                        userRepository.findById(bookLoreUserId).ifPresent(bookLoreUserEntity -> {
                            BookLoreUser bookLoreUser = bookLoreUserTransformer.toDTO(bookLoreUserEntity);

                            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                bookLoreUser,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))
                            );

                            SecurityContextHolder.getContext().setAuthentication(auth);
                            log.info("KoreaderAuthFilter: Set BookLoreUser principal for user ID: {}", bookLoreUserId);
                        });
                    } else {
                        log.warn("KoreaderAuthFilter: KOReader user '{}' has no linked BookLore user", username);
                    }
                } else {
                    log.warn("KOReader auth failed: password mismatch for user '{}'", username);
                }
            }, () -> log.warn("KOReader user '{}' not found", username));
        } else {
            log.warn("Missing KOReader headers - x-auth-user: {}, x-auth-key: {}", username != null, key != null);
        }

        chain.doFilter(request, response);
    }
}
