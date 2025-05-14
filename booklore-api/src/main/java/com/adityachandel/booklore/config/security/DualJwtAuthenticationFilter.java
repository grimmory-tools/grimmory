package com.adityachandel.booklore.config.security;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.custom.BookLoreUserTransformer;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.settings.OidcAutoProvisionDetails;
import com.adityachandel.booklore.model.dto.settings.OidcProviderDetails;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.model.entity.UserPermissionsEntity;
import com.adityachandel.booklore.repository.UserRepository;
import com.adityachandel.booklore.service.AppSettingService;
import com.adityachandel.booklore.service.user.UserProvisioningService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.DefaultJWKSetCache;
import com.nimbusds.jose.jwk.source.JWKSetCache;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@AllArgsConstructor
public class DualJwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final AppSettingService appSettingService;
    private final UserProvisioningService userProvisioningService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        String token = extractToken(request);
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
                log.debug("OIDC is disabled. Skipping OIDC authentication.");
            }
        } catch (Exception ex) {
            log.error("Authentication error: {}", ex.getMessage(), ex);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
        chain.doFilter(request, response);
    }

    private void authenticateLocalUser(String token, HttpServletRequest request) {
        Long userId = jwtUtils.extractUserId(token);
        BookLoreUserEntity entity = userRepository.findById(userId).orElseThrow(() -> new UsernameNotFoundException("User not found with ID: " + userId));
        BookLoreUser user = BookLoreUserTransformer.toDTO(entity);
        List<GrantedAuthority> authorities = getAuthorities(entity.getPermissions());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
        authentication.setDetails(new UserAuthenticationDetails(request, user.getId()));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void authenticateOidcUser(String token, HttpServletRequest request) {
        try {
            OidcProviderDetails providerDetails = appSettingService.getAppSettings().getOidcProviderDetails();
            String jwksUrl = providerDetails.getJwksUrl();
            if (jwksUrl == null || jwksUrl.isEmpty()) {
                log.error("JWKS URL is not configured.");
                throw ApiError.UNAUTHORIZED.createException("JWKS URL is not configured.");
            }

            List<String> defaultOidcUserPermissions = appSettingService.getAppSettings().getOidcAutoProvisionDetails().getDefaultPermissions();
            OidcProviderDetails.ClaimMapping claimMapping = providerDetails.getClaimMapping();
            URL jwksURL = new URI(jwksUrl).toURL();

            DefaultResourceRetriever resourceRetriever = new DefaultResourceRetriever(2000, 2000);
            Duration ttl = Duration.ofHours(6);
            Duration refresh = Duration.ofHours(1);
            JWKSetCache jwkSetCache = new DefaultJWKSetCache(ttl.toMillis(), refresh.toMillis(), TimeUnit.MILLISECONDS);
            JWKSource<SecurityContext> jwkSource = new RemoteJWKSet<>(jwksURL, resourceRetriever, jwkSetCache);

            ConfigurableJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
            JWSKeySelector<SecurityContext> keySelector = new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource);
            jwtProcessor.setJWSKeySelector(keySelector);

            JWTClaimsSet claimsSet = jwtProcessor.process(token, null);
            Date expirationTime = claimsSet.getExpirationTime();
            if (expirationTime == null || expirationTime.before(new Date())) {
                log.warn("OIDC token is expired or missing exp claim");
                throw ApiError.UNAUTHORIZED.createException("Token has expired or is invalid.");
            }

            String username = claimsSet.getStringClaim(claimMapping.getUsername());
            String email = claimsSet.getStringClaim(claimMapping.getEmail());
            String name = claimsSet.getStringClaim(claimMapping.getName());

            boolean autoProvisionOidcUsers = appSettingService.getAppSettings().getOidcAutoProvisionDetails().isEnableAutoProvisioning();
            OidcAutoProvisionDetails oidcAutoProvisionDetails = appSettingService.getAppSettings().getOidcAutoProvisionDetails();

            Optional<BookLoreUserEntity> userOpt = userRepository.findByUsername(username);
            if (userOpt.isEmpty()) {
                if (!autoProvisionOidcUsers) {
                    log.warn("User '{}' not found and auto-provisioning is disabled", username);
                    throw ApiError.UNAUTHORIZED.createException("User not found and auto-provisioning is disabled.");
                }
                log.info("Provisioning new OIDC user '{}'", username);
            }

            BookLoreUserEntity entity = userOpt.orElseGet(() -> userProvisioningService.provisionOidcUser(username, email, name, oidcAutoProvisionDetails));

            BookLoreUser user = BookLoreUserTransformer.toDTO(entity);
            List<GrantedAuthority> authorities = getAuthorities(entity.getPermissions());

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
            authentication.setDetails(new UserAuthenticationDetails(request, user.getId()));
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (Exception e) {
            log.error("OIDC authentication failed", e);
            throw ApiError.UNAUTHORIZED.createException("OIDC JWT validation failed");
        }
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        return (bearer != null && bearer.startsWith("Bearer ")) ? bearer.substring(7) : null;
    }

    private List<GrantedAuthority> getAuthorities(UserPermissionsEntity permissions) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (permissions != null) {
            addAuthorityIfPermissionGranted(authorities, "ROLE_UPLOAD", permissions.isPermissionUpload());
            addAuthorityIfPermissionGranted(authorities, "ROLE_DOWNLOAD", permissions.isPermissionDownload());
            addAuthorityIfPermissionGranted(authorities, "ROLE_EDIT_METADATA", permissions.isPermissionEditMetadata());
            addAuthorityIfPermissionGranted(authorities, "ROLE_MANIPULATE_LIBRARY", permissions.isPermissionManipulateLibrary());
            addAuthorityIfPermissionGranted(authorities, "ROLE_ADMIN", permissions.isPermissionAdmin());
        }
        return authorities;
    }

    private void addAuthorityIfPermissionGranted(List<GrantedAuthority> authorities, String role, boolean permissionGranted) {
        if (permissionGranted) {
            authorities.add(new SimpleGrantedAuthority(role));
        }
    }
}