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
        
        if (!path.startsWith("/api/koreader/") && !path.startsWith("/api/v1/reading-sessions")) {
            chain.doFilter(request, response);
            return;
        }
        
        String username = request.getHeader("x-auth-user");
        String key = request.getHeader("x-auth-key");
        
        // Only log and process if KOReader headers are present
        if (username != null && key != null) {
            log.info("KoreaderAuthFilter: Processing KOReader auth for user: {}", username);
            
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
                } else {
                    log.warn("KOReader auth failed: password mismatch for user '{}'", username);
                }
            }, () -> log.warn("KOReader user '{}' not found", username));
        }
        // If headers are missing, silently skip - JWT auth filter will handle it

        chain.doFilter(request, response);
    }
}