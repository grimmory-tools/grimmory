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
import org.springframework.boot.web.servlet.FilterRegistration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@AllArgsConstructor
@FilterRegistration(enabled = false)
public class QueryParameterJwtFilter extends OncePerRequestFilter {

    protected final JwtUtils jwtUtils;
    protected final UserRepository userRepository;
    protected final BookLoreUserTransformer bookLoreUserTransformer;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() != null &&
                SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            chain.doFilter(request, response);
            return;
        }
        String token = request.getParameter("token");

        if (token == null || token.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        try {
            if (jwtUtils.validateToken(token)) {
                authenticateUser(token, request);
            } else {
                log.debug("Invalid token. Rejecting request.");
            }
        } catch (Exception ex) {
            log.error("Authentication error: {}", ex.getMessage(), ex);
        }

        chain.doFilter(request, response);
    }

    protected void authenticateUser(String token, HttpServletRequest request) {
        Long userId = jwtUtils.extractUserId(token);
        BookLoreUserEntity entity = userRepository.findByIdWithDetails(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with ID: " + userId));
        BookLoreUser user = bookLoreUserTransformer.toDTO(entity);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, null);
        authentication.setDetails(new UserAuthenticationDetails(request, user.getId()));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
