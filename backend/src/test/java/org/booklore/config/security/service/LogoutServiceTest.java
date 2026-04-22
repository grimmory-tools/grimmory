package org.booklore.config.security.service;

import org.booklore.config.security.oidc.OidcDiscoveryService;
import org.booklore.config.security.oidc.OidcDiscoveryService.DiscoveryDocument;
import org.booklore.exception.APIException;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.response.LogoutResponse;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.settings.OidcProviderDetails;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.OidcSessionEntity;
import org.booklore.model.entity.RefreshTokenEntity;
import org.booklore.model.enums.ProvisioningMethod;
import org.booklore.repository.OidcSessionRepository;
import org.booklore.repository.RefreshTokenRepository;
import org.booklore.repository.UserRepository;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogoutServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private OidcSessionRepository oidcSessionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AppSettingService appSettingService;

    @Mock
    private OidcDiscoveryService discoveryService;

    @Mock
    private AuditService auditService;

    @Mock
    private AuthenticationService authenticationService;

    @InjectMocks
    private LogoutService logoutService;

    @Test
    void logout_withAuthenticatedLocalUser_returnsNullLogoutUrl() {
        var user = buildUser(1L, "testuser", ProvisioningMethod.LOCAL);
        stubAuthenticated("testuser", user);
        when(refreshTokenRepository.findAllByUserAndRevokedFalse(user)).thenReturn(List.of());

        LogoutResponse response = logoutService.logout(mockAuth(), null, null);

        assertThat(response.logoutUrl()).isNull();
    }

    @Test
    void logout_withAuthenticatedOidcUser_returnsLogoutUrl() {
        var user = buildUser(1L, "oidcuser", ProvisioningMethod.OIDC);
        stubAuthenticated("oidcuser", user);
        stubFullOidcFlow(user, "https://idp.example.com/logout");
        when(refreshTokenRepository.findAllByUserAndRevokedFalse(user)).thenReturn(List.of());

        LogoutResponse response = logoutService.logout(mockAuth(), null, "https://app.example.com");

        assertThat(response.logoutUrl()).isNotNull();
        assertThat(response.logoutUrl()).contains("https://idp.example.com/logout");
        assertThat(response.logoutUrl()).contains("client_id=my-client");
        assertThat(response.logoutUrl()).contains("id_token_hint=id-token-hint-value");
        assertThat(response.logoutUrl()).contains("post_logout_redirect_uri");
        assertThat(response.logoutUrl()).contains("/login");
    }

    @Test
    void logout_withRefreshToken_resolvesUser() {
        var user = buildUser(2L, "tokenuser", ProvisioningMethod.LOCAL);
        var tokenEntity = new RefreshTokenEntity();
        tokenEntity.setUser(user);

        when(refreshTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(tokenEntity));
        when(refreshTokenRepository.findAllByUserAndRevokedFalse(user)).thenReturn(List.of());

        LogoutResponse response = logoutService.logout(null, "valid-token", null);

        assertThat(response.logoutUrl()).isNull();
        verify(refreshTokenRepository).findByToken("valid-token");
    }

    @Test
    void logout_withNoAuthAndNoRefreshToken_throwsUnauthorized() {
        assertThatThrownBy(() -> logoutService.logout(null, null, null))
                .isInstanceOf(APIException.class);
    }

    @Test
    void logout_withBlankRefreshToken_throwsUnauthorized() {
        assertThatThrownBy(() -> logoutService.logout(null, "   ", null))
                .isInstanceOf(APIException.class);
    }

    @Test
    void logout_withRefreshTokenNotFound_throwsUnauthorized() {
        when(refreshTokenRepository.findByToken("invalid")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> logoutService.logout(null, "invalid", null))
                .isInstanceOf(APIException.class);
    }

    @Test
    void logout_withAuthenticatedUserNotFoundInDb_throwsUnauthorized() {
        var bookLoreUser = mock(BookLoreUser.class);
        when(bookLoreUser.getUsername()).thenReturn("missing");
        when(authenticationService.getAuthenticatedUser()).thenReturn(bookLoreUser);
        when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> logoutService.logout(mockAuth(), null, null))
                .isInstanceOf(APIException.class);
    }

    @Test
    void logout_revokesAllRefreshTokens() {
        var user = buildUser(1L, "testuser", ProvisioningMethod.LOCAL);
        stubAuthenticated("testuser", user);

        var token1 = new RefreshTokenEntity();
        token1.setUser(user);
        var token2 = new RefreshTokenEntity();
        token2.setUser(user);
        when(refreshTokenRepository.findAllByUserAndRevokedFalse(user)).thenReturn(List.of(token1, token2));

        logoutService.logout(mockAuth(), null, null);

        assertThat(token1.isRevoked()).isTrue();
        assertThat(token1.getRevocationDate()).isNotNull();
        assertThat(token2.isRevoked()).isTrue();
        assertThat(token2.getRevocationDate()).isNotNull();
        verify(refreshTokenRepository).saveAll(any());
    }

    @Test
    void logout_revokesOidcSession() {
        var user = buildUser(1L, "oidcuser", ProvisioningMethod.OIDC);
        stubAuthenticated("oidcuser", user);

        var oidcSession = OidcSessionEntity.builder()
                .user(user)
                .idTokenHint("token-hint")
                .build();

        stubOidcWithSession(oidcSession, "https://idp.example.com/logout");
        when(refreshTokenRepository.findAllByUserAndRevokedFalse(user)).thenReturn(List.of());

        logoutService.logout(mockAuth(), null, "https://app.example.com");

        assertThat(oidcSession.isRevoked()).isTrue();
        verify(oidcSessionRepository).save(oidcSession);
    }

    @Test
    void logout_logoutUrlIncludesCorrectParams() {
        var user = buildUser(1L, "oidcuser", ProvisioningMethod.OIDC);
        stubAuthenticated("oidcuser", user);
        stubFullOidcFlow(user, "https://idp.example.com/logout");
        when(refreshTokenRepository.findAllByUserAndRevokedFalse(user)).thenReturn(List.of());

        LogoutResponse response = logoutService.logout(mockAuth(), null, "https://myapp.com");

        String url = response.logoutUrl();
        assertThat(url).startsWith("https://idp.example.com/logout");
        assertThat(url).contains("client_id=my-client");
        assertThat(url).contains("id_token_hint=id-token-hint-value");
        assertThat(url).contains("post_logout_redirect_uri");
    }

    @Test
    void logout_logoutUrlOmitsRedirectUriWhenOriginIsNull() {
        var user = buildUser(1L, "oidcuser", ProvisioningMethod.OIDC);
        stubAuthenticated("oidcuser", user);
        stubFullOidcFlow(user, "https://idp.example.com/logout");
        when(refreshTokenRepository.findAllByUserAndRevokedFalse(user)).thenReturn(List.of());

        LogoutResponse response = logoutService.logout(mockAuth(), null, null);

        assertThat(response.logoutUrl()).doesNotContain("post_logout_redirect_uri");
    }

    @Test
    void logout_returnsNullLogoutUrlWhenNoOidcSession() {
        var user = buildUser(1L, "oidcuser", ProvisioningMethod.OIDC);
        stubAuthenticated("oidcuser", user);
        stubAppSettings(true, buildProviderDetails());
        when(refreshTokenRepository.findAllByUserAndRevokedFalse(user)).thenReturn(List.of());
        when(oidcSessionRepository.findFirstByUserIdAndRevokedFalseOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.empty());

        LogoutResponse response = logoutService.logout(mockAuth(), null, "https://app.example.com");

        assertThat(response.logoutUrl()).isNull();
    }

    @Test
    void logout_returnsNullLogoutUrlWhenNoEndSessionEndpoint() {
        var user = buildUser(1L, "oidcuser", ProvisioningMethod.OIDC);
        stubAuthenticated("oidcuser", user);

        var oidcSession = OidcSessionEntity.builder()
                .user(user)
                .idTokenHint("hint")
                .build();

        stubOidcWithSession(oidcSession, null);
        when(refreshTokenRepository.findAllByUserAndRevokedFalse(user)).thenReturn(List.of());

        LogoutResponse response = logoutService.logout(mockAuth(), null, "https://app.example.com");

        assertThat(response.logoutUrl()).isNull();
    }

    @Test
    void logout_returnsNullLogoutUrlWhenOidcDisabledForOidcUser() {
        var user = buildUser(1L, "oidcuser", ProvisioningMethod.OIDC);
        stubAuthenticated("oidcuser", user);
        stubAppSettings(false, null);
        when(refreshTokenRepository.findAllByUserAndRevokedFalse(user)).thenReturn(List.of());

        LogoutResponse response = logoutService.logout(mockAuth(), null, "https://app.example.com");

        assertThat(response.logoutUrl()).isNull();
        verifyNoInteractions(oidcSessionRepository);
    }

    @Test
    void logout_returnsNullLogoutUrlOnExceptionDuringUrlBuilding() {
        var user = buildUser(1L, "oidcuser", ProvisioningMethod.OIDC);
        stubAuthenticated("oidcuser", user);

        var appSettings = new AppSettings();
        appSettings.setOidcEnabled(true);
        appSettings.setOidcProviderDetails(buildProviderDetails());
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        when(oidcSessionRepository.findFirstByUserIdAndRevokedFalseOrderByCreatedAtDesc(1L))
                .thenThrow(new RuntimeException("db error"));
        when(refreshTokenRepository.findAllByUserAndRevokedFalse(user)).thenReturn(List.of());

        LogoutResponse response = logoutService.logout(mockAuth(), null, "https://app.example.com");

        assertThat(response.logoutUrl()).isNull();
    }

    private BookLoreUserEntity buildUser(Long id, String username, ProvisioningMethod method) {
        return BookLoreUserEntity.builder()
                .id(id)
                .username(username)
                .provisioningMethod(method)
                .build();
    }

    private Authentication mockAuth() {
        var auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn("not-anonymous");
        return auth;
    }

    private void stubAuthenticated(String username, BookLoreUserEntity userEntity) {
        var bookLoreUser = mock(BookLoreUser.class);
        when(bookLoreUser.getUsername()).thenReturn(username);
        when(authenticationService.getAuthenticatedUser()).thenReturn(bookLoreUser);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(userEntity));
    }

    private void stubAppSettings(boolean oidcEnabled, OidcProviderDetails providerDetails) {
        var appSettings = new AppSettings();
        appSettings.setOidcEnabled(oidcEnabled);
        appSettings.setOidcProviderDetails(providerDetails);
        when(appSettingService.getAppSettings()).thenReturn(appSettings);
    }

    private OidcProviderDetails buildProviderDetails() {
        var providerDetails = new OidcProviderDetails();
        providerDetails.setClientId("my-client");
        providerDetails.setIssuerUri("https://idp.example.com");
        return providerDetails;
    }

    private DiscoveryDocument buildDiscovery(String endSessionEndpoint) {
        return new DiscoveryDocument(
                "https://idp.example.com",
                null, null, null, null,
                endSessionEndpoint,
                null, null, null, null
        );
    }

    private void stubFullOidcFlow(BookLoreUserEntity user, String endSessionEndpoint) {
        stubAppSettings(true, buildProviderDetails());

        var oidcSession = OidcSessionEntity.builder()
                .user(user)
                .idTokenHint("id-token-hint-value")
                .build();
        when(oidcSessionRepository.findFirstByUserIdAndRevokedFalseOrderByCreatedAtDesc(user.getId()))
                .thenReturn(Optional.of(oidcSession));
        when(discoveryService.discover("https://idp.example.com")).thenReturn(buildDiscovery(endSessionEndpoint));
    }

    private void stubOidcWithSession(OidcSessionEntity session, String endSessionEndpoint) {
        stubAppSettings(true, buildProviderDetails());

        when(oidcSessionRepository.findFirstByUserIdAndRevokedFalseOrderByCreatedAtDesc(session.getUser().getId()))
                .thenReturn(Optional.of(session));
        when(discoveryService.discover("https://idp.example.com")).thenReturn(buildDiscovery(endSessionEndpoint));
    }
}
