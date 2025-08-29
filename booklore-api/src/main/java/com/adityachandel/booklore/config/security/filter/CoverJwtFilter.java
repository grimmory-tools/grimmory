package com.adityachandel.booklore.config.security.filter;

import com.adityachandel.booklore.config.security.JwtUtils;
import com.adityachandel.booklore.config.security.service.DynamicOidcJwtProcessor;
import com.adityachandel.booklore.config.security.userdetails.UserAuthenticationDetails;
import com.adityachandel.booklore.mapper.custom.BookLoreUserTransformer;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.repository.UserRepository;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@AllArgsConstructor
@Component
public class CoverJwtFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final BookLoreUserTransformer bookLoreUserTransformer;
    private final AppSettingService appSettingService;
    private final DynamicOidcJwtProcessor dynamicOidcJwtProcessor;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/media/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String token = request.getParameter("token");
        if (token != null) {
            try {
                if (jwtUtils.validateToken(token)) {
                    authenticateLocalUser(token, request);
                } else if (appSettingService.getAppSettings().isOidcEnabled()) {
                    authenticateOidcUser(token, request);
                } else {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid token");
                    return;
                }
            } catch (Exception ex) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication failed: " + ex.getMessage());
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private void authenticateLocalUser(String token, HttpServletRequest request) {
        Long userId = jwtUtils.extractUserId(token);
        BookLoreUserEntity entity = userRepository.findById(userId).orElseThrow(() -> new UsernameNotFoundException("User not found with ID: " + userId));
        BookLoreUser user = bookLoreUserTransformer.toDTO(entity);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(user, null, null);
        authentication.setDetails(new UserAuthenticationDetails(request, user.getId()));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void authenticateOidcUser(String token, HttpServletRequest request) throws Exception {
        var processor = dynamicOidcJwtProcessor.getProcessor();
        var claimsSet = processor.process(token, null);

        if (claimsSet.getExpirationTime() == null || claimsSet.getExpirationTime().before(new java.util.Date())) {
            throw new RuntimeException("OIDC token expired or invalid");
        }

        String username = claimsSet.getStringClaim("preferred_username");
        BookLoreUserEntity entity = userRepository.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException("OIDC user not found: " + username));
        BookLoreUser user = bookLoreUserTransformer.toDTO(entity);

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(user, null, null);
        authentication.setDetails(new UserAuthenticationDetails(request, user.getId()));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}