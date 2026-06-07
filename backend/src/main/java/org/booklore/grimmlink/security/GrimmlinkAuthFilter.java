package org.booklore.grimmlink.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.config.security.userdetails.KoreaderUserDetails;
import org.booklore.repository.KoreaderUserRepository;
import org.booklore.repository.UserRepository;
import org.springframework.boot.web.servlet.FilterRegistration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@FilterRegistration(enabled = false)
public class GrimmlinkAuthFilter extends OncePerRequestFilter {

    private final KoreaderUserRepository koreaderUserRepository;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String username = request.getHeader("x-auth-user");
        String key = request.getHeader("x-auth-key");
        if (username == null || username.isBlank() || key == null || key.isBlank()) {
            writeUnauthorized(response, "Missing GrimmLink authentication headers");
            return;
        }

        var koreaderUser = koreaderUserRepository.findByUsername(username).orElse(null);
        if (koreaderUser == null || koreaderUser.getPasswordMD5() == null || !md5Matches(koreaderUser.getPasswordMD5(), key)) {
            writeUnauthorized(response, "Invalid GrimmLink credentials");
            return;
        }
        if (!koreaderUser.isSyncEnabled()) {
            writeForbidden(response, "Sync is disabled for this user");
            return;
        }
        if (koreaderUser.getBookLoreUser() == null || !userRepository.existsById(koreaderUser.getBookLoreUser().getId())) {
            writeUnauthorized(response, "GrimmLink user is not linked to a Grimmory user");
            return;
        }

        Long bookLoreUserId = koreaderUser.getBookLoreUser().getId();
        KoreaderUserDetails userDetails = new KoreaderUserDetails(
                koreaderUser.getUsername(),
                koreaderUser.getPasswordMD5(),
                koreaderUser.isSyncEnabled(),
                koreaderUser.isSyncWithWebReader(),
                bookLoreUserId,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
        chain.doFilter(request, response);
    }

    private boolean md5Matches(String expectedHex, String actualHex) {
        try {
            byte[] expected = HexFormat.of().parseHex(expectedHex);
            byte[] actual = HexFormat.of().parseHex(actualHex);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {
            log.info("Invalid GrimmLink MD5 credential format: {}", e.getMessage());
            return false;
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"status\":\"error\",\"message\":\"" + message + "\"}");
    }

    private void writeForbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"status\":\"error\",\"message\":\"" + message + "\"}");
    }
}
