package org.booklore.config.security.oidc;

import com.nimbusds.jwt.JWTClaimsSet;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.settings.OidcProviderDetails;
import org.booklore.model.entity.OidcSessionEntity;
import org.booklore.model.enums.AuditAction;
import org.booklore.model.websocket.Topic;
import org.booklore.repository.OidcSessionRepository;
import org.booklore.repository.RefreshTokenRepository;
import org.booklore.service.NotificationService;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.audit.AuditService;
import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@AllArgsConstructor
public class BackchannelLogoutService {

    private static final Cache<String, Instant> PROCESSED_JTIS = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(1))
            .maximumSize(1_000)
            .build();

    private final AppSettingService appSettingService;
    private final OidcTokenValidator oidcTokenValidator;
    private final OidcSessionRepository oidcSessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public void handleLogoutToken(String logoutToken) {
        if (!appSettingService.getAppSettings().isOidcEnabled()) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("OIDC is not enabled");
        }

        OidcProviderDetails providerDetails = appSettingService.getAppSettings().getOidcProviderDetails();
        if (providerDetails == null || providerDetails.getIssuerUri() == null) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("OIDC is not configured");
        }

        JWTClaimsSet claims = oidcTokenValidator.validateLogoutToken(
                logoutToken, providerDetails.getIssuerUri(), providerDetails.getClientId()
        );

        String jti = claims.getJWTID();
        if (jti == null || jti.isBlank()) {
            throw ApiError.OIDC_LOGOUT_MISSING_JTI.createException();
        }
        if (PROCESSED_JTIS.getIfPresent(jti) != null) {
            throw ApiError.OIDC_LOGOUT_REPLAY.createException();
        }
        PROCESSED_JTIS.put(jti, Instant.now());

        String sid = null;
        String sub = null;
        try {
            sid = claims.getStringClaim("sid");
            sub = claims.getSubject();
        } catch (ParseException e) {
            log.debug("Failed to parse sid claim: {}", e.getMessage());
        }

        if (sid == null && sub == null) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("Logout token must contain either 'sub' or 'sid' claim");
        }

        List<OidcSessionEntity> sessions;
        if (sid != null) {
            sessions = oidcSessionRepository.findByOidcSessionIdAndRevokedFalse(sid);
        } else {
            sessions = oidcSessionRepository.findByOidcSubjectAndOidcIssuerAndRevokedFalse(sub, providerDetails.getIssuerUri());
        }

        for (OidcSessionEntity session : sessions) {
            session.setRevoked(true);
            oidcSessionRepository.save(session);

            var user = session.getUser();
            refreshTokenRepository.findAllByUserAndRevokedFalse(user).forEach(token -> {
                token.setRevoked(true);
                token.setRevocationDate(Instant.now());
                refreshTokenRepository.save(token);
            });

            notificationService.sendMessageToUser(
                    user.getUsername(),
                    Topic.SESSION_REVOKED,
                    Map.of("reason", "backchannel_logout")
            );

            auditService.log(AuditAction.BACKCHANNEL_LOGOUT, "User", user.getId(),
                    "Back-channel logout for user: " + user.getUsername());

            log.info("Back-channel logout: revoked session for user '{}'", user.getUsername());
        }
    }
}
