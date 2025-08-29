package com.adityachandel.booklore.config.security.filter;

import com.adityachandel.booklore.config.security.userdetails.KoreaderUserDetails;
import com.adityachandel.booklore.repository.KoreaderUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
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
        if (!path.startsWith("/api/koreader/")) {
            chain.doFilter(request, response);
            return;
        }

        String username = request.getHeader("x-auth-user");
        String key = request.getHeader("x-auth-key");

        if (username != null && key != null) {
            koreaderUserRepository.findByUsername(username).ifPresentOrElse(user -> {
                if (user.getPasswordMD5().equalsIgnoreCase(key)) {
                    Long bookLoreUserId = null;
                    if (user.getBookLoreUser() != null) {
                        bookLoreUserId = user.getBookLoreUser().getId();
                    }

                    UserDetails userDetails = new KoreaderUserDetails(
                            user.getUsername(),
                            user.getPasswordMD5(),
                            user.isSyncEnabled(),
                            bookLoreUserId,
                            List.of(new SimpleGrantedAuthority("ROLE_USER"))
                    );

                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

                    SecurityContextHolder.getContext().setAuthentication(auth);
                } else {
                    log.warn("KOReader auth failed: password mismatch for user '{}'", username);
                }
            }, () -> log.warn("KOReader user '{}' not found", username));
        } else {
            log.warn("Missing KOReader headers");
        }

        chain.doFilter(request, response);
    }
}