package com.adityachandel.booklore.config.security.filter;

import com.adityachandel.booklore.config.security.JwtUtils;
import com.adityachandel.booklore.config.security.service.DynamicOidcJwtProcessor;
import com.adityachandel.booklore.config.security.userdetails.UserAuthenticationDetails;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.custom.BookLoreUserTransformer;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.settings.OidcAutoProvisionDetails;
import com.adityachandel.booklore.model.dto.settings.OidcProviderDetails;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.repository.UserRepository;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.user.UserProvisioningService;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
@AllArgsConstructor
public class DualJwtAuthenticationFilter extends OncePerRequestFilter {

    private final BookLoreUserTransformer bookLoreUserTransformer;
    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final AppSettingService appSettingService;
    private final UserProvisioningService userProvisioningService;
    private static final ConcurrentMap<String, Object> userLocks = new ConcurrentHashMap<>();
    private final DynamicOidcJwtProcessor dynamicOidcJwtProcessor;

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
                authenticateLocalUser(token, request);
            } else if (appSettingService.getAppSettings().isOidcEnabled()) {
                authenticateOidcUser(token, request);
            } else {
                log.debug("OIDC is disabled and token is invalid. Rejecting request.");
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

    private void authenticateLocalUser(String token, HttpServletRequest request) {
        Long userId = jwtUtils.extractUserId(token);
        BookLoreUserEntity entity = userRepository.findById(userId).orElseThrow(() -> new UsernameNotFoundException("User not found with ID: " + userId));
        BookLoreUser user = bookLoreUserTransformer.toDTO(entity);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(user, null, null);
        authentication.setDetails(new UserAuthenticationDetails(request, user.getId()));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void authenticateOidcUser(String token, HttpServletRequest request) {
        try {
            OidcProviderDetails providerDetails = appSettingService.getAppSettings().getOidcProviderDetails();
            JWTClaimsSet claimsSet = dynamicOidcJwtProcessor.getProcessor().process(token, null);

            if (claimsSet.getExpirationTime() == null || claimsSet.getExpirationTime().toInstant().isBefore(Instant.now())) {
                log.warn("OIDC token is expired or missing exp claim");
                throw ApiError.GENERIC_UNAUTHORIZED.createException("Token has expired or is invalid.");
            }

            OidcProviderDetails.ClaimMapping claimMapping = providerDetails.getClaimMapping();
            String username = claimsSet.getStringClaim(claimMapping.getUsername());
            String email = claimsSet.getStringClaim(claimMapping.getEmail());
            String name = claimsSet.getStringClaim(claimMapping.getName());

            OidcAutoProvisionDetails provisionDetails = appSettingService.getAppSettings().getOidcAutoProvisionDetails();
            boolean autoProvision = provisionDetails != null && provisionDetails.isEnableAutoProvisioning();

            BookLoreUserEntity entity = userRepository.findByUsername(username)
                    .orElseGet(() -> {
                        if (!autoProvision) {
                            log.warn("User '{}' not found and auto-provisioning is disabled.", username);
                            throw ApiError.GENERIC_UNAUTHORIZED.createException("User not found and auto-provisioning is disabled.");
                        }
                        Object lock = userLocks.computeIfAbsent(username, k -> new Object());
                        try {
                            synchronized (lock) {
                                return userRepository.findByUsername(username)
                                        .orElseGet(() -> {
                                            log.info("Provisioning new OIDC user '{}'", username);
                                            return userProvisioningService.provisionOidcUser(username, email, name, provisionDetails);
                                        });
                            }
                        } finally {
                            userLocks.remove(username);
                        }
                    });

            BookLoreUser user = bookLoreUserTransformer.toDTO(entity);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(user, null, null);
            authentication.setDetails(new UserAuthenticationDetails(request, user.getId()));
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (Exception e) {
            log.error("OIDC authentication failed", e);
            throw ApiError.GENERIC_UNAUTHORIZED.createException("OIDC JWT validation failed: " + e.getMessage());
        }
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        return (bearer != null && bearer.startsWith("Bearer ")) ? bearer.substring(7) : null;
    }
}