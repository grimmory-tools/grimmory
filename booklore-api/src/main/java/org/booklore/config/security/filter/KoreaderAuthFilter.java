package org.booklore.config.security.filter;

import org.booklore.config.security.userdetails.KoreaderUserDetails;
import org.booklore.repository.KoreaderUserRepository;
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

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {

        String path = request.getRequestURI();
        
        if (!path.startsWith("/api/koreader/") && !path.equals("/api/v1/reading-sessions")) {
            chain.doFilter(request, response);
            return;
        }
        
        log.info("KoreaderAuthFilter: Processing request to: {}", path);

        String username = request.getHeader("x-auth-user");
        String key = request.getHeader("x-auth-key");
        
        log.info("KoreaderAuthFilter: x-auth-user={}, x-auth-key present={}", username, key != null);

        if (username != null && key != null) {
            koreaderUserRepository.findByUsername(username).ifPresentOrElse(user -> {
                if (user.getPasswordMD5().equalsIgnoreCase(key)) {
                    log.info("KoreaderAuthFilter: Authentication successful for user: {}", username);
                    
                    Long bookLoreUserId = user.getBookLoreUser() != null ? user.getBookLoreUser().getId() : null;
                    
                    // Create KoreaderUserDetails as principal
                    KoreaderUserDetails koreaderUserDetails = new KoreaderUserDetails(
                        user.getUsername(),
                        user.getPasswordMD5(),
                        user.isSyncEnabled(),
                        user.isSyncWithBookloreReader(),
                        bookLoreUserId,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                    );
                    
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        koreaderUserDetails, 
                        null, 
                        koreaderUserDetails.getAuthorities()
                    );
                    
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    log.info("KoreaderAuthFilter: Set KoreaderUserDetails principal for user: {}", username);
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