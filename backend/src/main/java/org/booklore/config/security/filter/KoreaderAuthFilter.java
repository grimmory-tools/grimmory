package org.booklore.config.security.filter;

import org.booklore.config.security.userdetails.KoreaderUserDetails;
import org.booklore.repository.KoreaderUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

@RequiredArgsConstructor
@Component
@Slf4j
@FilterRegistration(enabled = false)
public class KoreaderAuthFilter extends OncePerRequestFilter {

    private final KoreaderUserRepository koreaderUserRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String username = request.getHeader("x-auth-user");
        String key = request.getHeader("x-auth-key");

        if (username == null || key == null) {
            log.info("Missing KOReader headers");
            chain.doFilter(request, response);
            return;
        }

        var user = koreaderUserRepository.findByUsername(username).orElse(null);
        if (user == null) {
            log.info("KOReader user not found");
            chain.doFilter(request, response);
            return;
        }

        try {
            byte[] expectedMD5Bytes = HexFormat.of().parseHex(user.getPasswordMD5());
            byte[] actualMD5Bytes = HexFormat.of().parseHex(key);

            if (!MessageDigest.isEqual(expectedMD5Bytes, actualMD5Bytes)) {
                log.info("KOReader user password not match");
                chain.doFilter(request, response);
                return;
            }
        } catch (IllegalArgumentException e) {
            log.info("Problem comparing Koreader md5 checksums: {}", e.getMessage());
            chain.doFilter(request, response);
            return;
        }

        Long bookLoreUserId = null;
        if (user.getBookLoreUser() != null) {
            bookLoreUserId = user.getBookLoreUser().getId();
        }

        UserDetails userDetails = new KoreaderUserDetails(
                user.getUsername(),
                user.getPasswordMD5(),
                user.isSyncEnabled(),
                user.isSyncWithBookloreReader(),
                bookLoreUserId,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        chain.doFilter(request, response);
    }
}