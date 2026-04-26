package org.grimmory.config.security.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grimmory.config.security.oidc.OidcDiscoveryService;
import org.grimmory.exception.ApiError;
import org.grimmory.model.dto.response.LogoutResponse;
import org.grimmory.model.entity.BookLoreUserEntity;
import org.grimmory.model.entity.OidcSessionEntity;
import org.grimmory.model.entity.RefreshTokenEntity;
import org.grimmory.model.enums.AuditAction;
import org.grimmory.model.enums.ProvisioningMethod;
import org.grimmory.repository.OidcSessionRepository;
import org.grimmory.repository.RefreshTokenRepository;
import org.grimmory.repository.UserRepository;
import org.grimmory.service.appsettings.AppSettingService;
import org.grimmory.service.audit.AuditService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class LogoutService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final OidcSessionRepository oidcSessionRepository;
    private final UserRepository userRepository;
    private final AppSettingService appSettingService;
    private final OidcDiscoveryService discoveryService;
    private final AuditService auditService;
    private final AuthenticationService authenticationService;

    @Transactional
    public LogoutResponse logout(Authentication auth, String refreshToken, String origin) {
        BookLoreUserEntity user = resolveUser(auth, refreshToken);

        revokeRefreshToken(user);

        String logoutUrl = null;
        if (user.getProvisioningMethod() == ProvisioningMethod.OIDC && appSettingService.getAppSettings().isOidcEnabled()) {
            logoutUrl = buildOidcLogoutUrl(user, origin);
        }

        auditService.log(AuditAction.LOGOUT, "User", user.getId(), "User logged out: " + user.getUsername());
        return new LogoutResponse(logoutUrl);
    }

    private BookLoreUserEntity resolveUser(Authentication auth, String refreshToken) {
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            var bookLoreUser = authenticationService.getAuthenticatedUser();
            return userRepository.findByUsername(bookLoreUser.getUsername())
                    .orElseThrow(() -> ApiError.GENERIC_UNAUTHORIZED.createException("User not found"));
        }

        if (refreshToken != null && !refreshToken.isBlank()) {
            RefreshTokenEntity tokenEntity = refreshTokenRepository.findByToken(refreshToken)
                    .orElseThrow(() -> ApiError.GENERIC_UNAUTHORIZED.createException("Invalid refresh token"));
            return tokenEntity.getUser();
        }

        throw ApiError.GENERIC_UNAUTHORIZED.createException("No authentication context or refresh token provided");
    }

    private void revokeRefreshToken(BookLoreUserEntity user) {
        List<RefreshTokenEntity> tokens = refreshTokenRepository.findAllByUserAndRevokedFalse(user);
        tokens.forEach(token -> {
            token.setRevoked(true);
            token.setRevocationDate(Instant.now());
        });
        refreshTokenRepository.saveAll(tokens);
    }

    private String buildOidcLogoutUrl(BookLoreUserEntity user, String origin) {
        try {
            var providerDetails = appSettingService.getAppSettings().getOidcProviderDetails();
            var session = oidcSessionRepository.findFirstByUserIdAndRevokedFalseOrderByCreatedAtDesc(user.getId());

            if (session.isPresent()) {
                OidcSessionEntity oidcSession = session.get();
                oidcSession.setRevoked(true);
                oidcSessionRepository.save(oidcSession);

                var discovery = discoveryService.discover(providerDetails.getIssuerUri());
                if (discovery.endSessionEndpoint() != null) {
                    String postLogoutRedirectUri = (origin != null && !origin.isBlank() ? origin : "") + "/login";

                    var builder = UriComponentsBuilder.fromUriString(discovery.endSessionEndpoint())
                            .queryParam("client_id", providerDetails.getClientId())
                            .queryParam("id_token_hint", oidcSession.getIdTokenHint());

                    if (!postLogoutRedirectUri.equals("/login")) {
                        builder.queryParam("post_logout_redirect_uri", postLogoutRedirectUri);
                    }

                    return builder.build().toUriString();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to build OIDC logout URL: {}", e.getMessage());
        }
        return null;
    }
}
