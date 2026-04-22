package org.booklore.config.security.oidc;

import com.nimbusds.jwt.JWTClaimsSet;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.settings.OidcAutoProvisionDetails;
import org.booklore.model.dto.settings.OidcProviderDetails;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.OidcSessionEntity;
import org.booklore.model.enums.AuditAction;
import org.booklore.model.enums.ProvisioningMethod;
import org.booklore.repository.OidcSessionRepository;
import org.booklore.repository.UserRepository;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.audit.AuditService;
import org.booklore.service.oidc.OidcGroupMappingService;
import org.booklore.service.user.UserProvisioningService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;
import java.text.ParseException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
@AllArgsConstructor
public class OidcAuthService {

    private static final String MOBILE_SCHEME = "booklore://";
    private static final String OAUTH2_CALLBACK_PATH = "/oauth2-callback";

    private final AppSettingService appSettingService;
    private final OidcTokenClient oidcTokenClient;
    private final OidcTokenValidator oidcTokenValidator;
    private final OidcClaimExtractor oidcClaimExtractor;
    private final UserRepository userRepository;
    private final UserProvisioningService userProvisioningService;
    private final AuthenticationService authenticationService;
    private final OidcSessionRepository oidcSessionRepository;
    private final OidcGroupMappingService oidcGroupMappingService;
    private final AuditService auditService;

    private static final ConcurrentMap<String, ReentrantLock> userLocks = new ConcurrentHashMap<>();

    @Transactional
    public ResponseEntity<Map<String, String>> exchangeCodeForTokens(
            String code,
            String codeVerifier,
            String redirectUri,
            String nonce,
            HttpServletRequest httpRequest
    ) {
        if (!appSettingService.getAppSettings().isOidcEnabled()) {
            throw ApiError.FORBIDDEN.createException("OIDC is not enabled");
        }

        OidcProviderDetails providerDetails = appSettingService.getAppSettings().getOidcProviderDetails();
        if (providerDetails == null || providerDetails.getIssuerUri() == null) {
            throw ApiError.FORBIDDEN.createException("OIDC is not properly configured");
        }

        validateRedirectUri(redirectUri, httpRequest);

        var tokenResponse = oidcTokenClient.exchangeAuthorizationCode(code, codeVerifier, redirectUri, providerDetails);

        String idToken = tokenResponse.idToken();
        if (idToken == null) {
            throw ApiError.OIDC_INVALID_TOKEN.createException("No ID token received from OIDC provider");
        }

        var claims = oidcTokenValidator.validateIdToken(
                idToken,
                providerDetails.getIssuerUri(),
                providerDetails.getClientId(),
                nonce,
                tokenResponse.accessToken()
        );

        Map<String, Object> userInfo = Map.of();
        if (tokenResponse.accessToken() != null) {
            userInfo = oidcTokenClient.fetchUserInfo(tokenResponse.accessToken(), providerDetails.getIssuerUri());
        }

        var userClaims = oidcClaimExtractor.extractClaims(claims, providerDetails.getClaimMapping(), userInfo);
        log.info("OIDC login: authenticating user '{}'", userClaims.username());

        BookLoreUserEntity user = findOrProvisionUser(userClaims, providerDetails.getIssuerUri());

        persistOidcSession(user, userClaims.subject(), providerDetails.getIssuerUri(), tokenResponse, idToken, claims);

        String durationStr = appSettingService.getSettingValue("oidc_session_duration_hours");
        ResponseEntity<Map<String, String>> response;
        if (durationStr != null && !durationStr.isBlank()) {
            try {
                long durationMs = Long.parseLong(durationStr) * 3_600_000L;
                response = authenticationService.loginUser(user, durationMs);
            } catch (NumberFormatException e) {
                log.warn("Invalid OIDC session duration setting: {}", durationStr);
                response = authenticationService.loginUser(user);
            }
        } else {
            response = authenticationService.loginUser(user);
        }

        auditService.log(AuditAction.OIDC_LOGIN_SUCCESS, "User", user.getId(), "OIDC login successful for user: " + user.getUsername());
        return response;
    }

    private void validateRedirectUri(String redirectUri, HttpServletRequest httpRequest) {
        if (redirectUri == null || redirectUri.isBlank()) {
            throw ApiError.OIDC_INVALID_REDIRECT_URI.createException();
        }

        if (redirectUri.startsWith(MOBILE_SCHEME)) {
            if (!redirectUri.equals(MOBILE_SCHEME + "oauth2-callback")) {
                throw ApiError.OIDC_INVALID_REDIRECT_URI.createException();
            }
            return;
        }

        try {
            URI uri = URI.create(redirectUri);
            String scheme = uri.getScheme();
            if (!"https".equals(scheme) && !"http".equals(scheme)) {
                throw ApiError.OIDC_INVALID_REDIRECT_URI.createException();
            }
            if (uri.getPath() == null || !uri.getPath().endsWith(OAUTH2_CALLBACK_PATH)) {
                throw ApiError.OIDC_INVALID_REDIRECT_URI.createException();
            }

            String redirectOrigin = scheme + "://" + uri.getHost()
                    + (uri.getPort() != -1 && uri.getPort() != 443 && uri.getPort() != 80
                       ? ":" + uri.getPort() : "");
            String requestOrigin = resolveRequestOrigin(httpRequest);
            if (requestOrigin != null && !requestOrigin.equals(redirectOrigin)) {
                log.warn("OIDC redirect URI origin mismatch: redirect={}, request={}", redirectOrigin, requestOrigin);
                throw ApiError.OIDC_INVALID_REDIRECT_URI.createException();
            }
        } catch (IllegalArgumentException e) {
            throw ApiError.OIDC_INVALID_REDIRECT_URI.createException();
        }
    }

    private String resolveRequestOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.isBlank()) {
            return origin.replaceAll("/+$", "");
        }
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        if ((port == 80 && "http".equals(scheme)) || (port == 443 && "https".equals(scheme))) {
            return scheme + "://" + host;
        }
        return scheme + "://" + host + ":" + port;
    }

    public void validateAppRedirectUri(String appRedirectUri) {
        if (appRedirectUri == null || !appRedirectUri.startsWith(MOBILE_SCHEME)) {
            throw ApiError.OIDC_INVALID_REDIRECT_URI.createException();
        }
    }

    private void persistOidcSession(BookLoreUserEntity user, String subject, String issuer,
                                    OidcTokenClient.TokenResponse tokenResponse, String rawIdToken,
                                    JWTClaimsSet claims) {
        String sessionId = null;
        try {
            sessionId = claims.getStringClaim("sid");
        } catch (ParseException e) {
            log.debug("No 'sid' claim in ID token");
        }

        var session = OidcSessionEntity.builder()
                .user(user)
                .oidcSubject(subject)
                .oidcIssuer(issuer)
                .oidcSessionId(sessionId)
                .idTokenHint(rawIdToken)
                .build();

        oidcSessionRepository.save(session);
        log.debug("Persisted OIDC session for user '{}' (subject: {})", user.getUsername(), subject);
    }

    private BookLoreUserEntity findOrProvisionUser(OidcClaimExtractor.OidcUserClaims userClaims, String issuerUri) {
        OidcAutoProvisionDetails provisionDetails = appSettingService.getAppSettings().getOidcAutoProvisionDetails();
        boolean autoProvision = provisionDetails != null && provisionDetails.isEnableAutoProvisioning();
        boolean allowLocalLinking = provisionDetails != null && provisionDetails.isAllowLocalAccountLinking();

        return userRepository.findByOidcIssuerAndOidcSubject(issuerUri, userClaims.subject())
                .map(user -> syncExistingUser(user, userClaims, issuerUri))
                .or(() -> userRepository.findByUsername(userClaims.username())
                        .filter(user -> user.getProvisioningMethod() == ProvisioningMethod.OIDC || allowLocalLinking)
                        .map(user -> linkExistingUser(user, userClaims, issuerUri)))
                .orElseGet(() -> {
                    if (!autoProvision) {
                        log.warn("OIDC user '{}' not found and auto-provisioning is disabled", userClaims.username());
                        throw ApiError.OIDC_USER_NOT_PROVISIONED.createException(userClaims.username());
                    }

                    ReentrantLock lock = userLocks.computeIfAbsent(userClaims.username(), _ -> new ReentrantLock());
                    lock.lock();
                    try {
                        return userRepository.findByOidcIssuerAndOidcSubject(issuerUri, userClaims.subject())
                                .or(() -> userRepository.findByUsername(userClaims.username()))
                                .orElseGet(() -> {
                                    log.info("OIDC: provisioning new user '{}'", userClaims.username());
                                    BookLoreUserEntity newUser = userProvisioningService.provisionOidcUser(
                                            userClaims.username(),
                                            userClaims.email(),
                                            userClaims.name(),
                                            userClaims.subject(),
                                            issuerUri,
                                            userClaims.pictureUrl(),
                                            provisionDetails
                                    );
                                    oidcGroupMappingService.syncUserGroups(newUser, userClaims.groups());
                                    auditService.log(AuditAction.OIDC_USER_PROVISIONED, "User", newUser.getId(),
                                            "OIDC auto-provisioned user: " + newUser.getUsername());
                                    return newUser;
                                });
                    } finally {
                        lock.unlock();
                    }
                });
    }

    private BookLoreUserEntity linkExistingUser(BookLoreUserEntity user, OidcClaimExtractor.OidcUserClaims userClaims, String issuerUri) {
        if (user.getProvisioningMethod() != ProvisioningMethod.OIDC) {
            log.info("OIDC login: linking local user '{}' (was {}) to OIDC identity",
                    user.getUsername(), user.getProvisioningMethod());
            user.setProvisioningMethod(ProvisioningMethod.OIDC);
            auditService.log(AuditAction.OIDC_ACCOUNT_LINKED, "User", user.getId(),
                    "Local account linked to OIDC for user: " + user.getUsername());
        }
        return syncExistingUser(user, userClaims, issuerUri);
    }

    private BookLoreUserEntity syncExistingUser(BookLoreUserEntity user, OidcClaimExtractor.OidcUserClaims userClaims, String issuerUri) {
        boolean changed = false;

        if (userClaims.email() != null && !userClaims.email().equals(user.getEmail())) {
            user.setEmail(userClaims.email());
            changed = true;
        }
        if (userClaims.name() != null && !userClaims.name().equals(user.getName())) {
            user.setName(userClaims.name());
            changed = true;
        }
        if (userClaims.pictureUrl() != null && !userClaims.pictureUrl().equals(user.getAvatarUrl())) {
            user.setAvatarUrl(userClaims.pictureUrl());
            changed = true;
        }
        if ((user.getOidcSubject() == null || user.getOidcSubject().isBlank()) && userClaims.subject() != null) {
            user.setOidcSubject(userClaims.subject());
            changed = true;
        }
        if ((user.getOidcIssuer() == null || user.getOidcIssuer().isBlank()) && issuerUri != null) {
            user.setOidcIssuer(issuerUri);
            changed = true;
        }

        if (changed) {
            log.info("OIDC sync: saving updated user '{}'", user.getUsername());
            userRepository.save(user);
        }

        oidcGroupMappingService.syncUserGroups(user, userClaims.groups());
        return user;
    }
}
