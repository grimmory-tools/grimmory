package org.booklore.config.security.oidc;

import com.nimbusds.jwt.JWTClaimsSet;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.settings.OidcAutoProvisionDetails;
import org.booklore.model.dto.settings.OidcProviderDetails;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.enums.ProvisioningMethod;
import org.booklore.repository.OidcSessionRepository;
import org.booklore.repository.UserRepository;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.audit.AuditService;
import org.booklore.service.oidc.OidcGroupMappingService;
import org.booklore.service.user.UserProvisioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OidcAuthServiceTest {

    @Mock private AppSettingService appSettingService;
    @Mock private OidcTokenClient oidcTokenClient;
    @Mock private OidcTokenValidator oidcTokenValidator;
    @Mock private OidcClaimExtractor oidcClaimExtractor;
    @Mock private UserRepository userRepository;
    @Mock private UserProvisioningService userProvisioningService;
    @Mock private AuthenticationService authenticationService;
    @Mock private OidcSessionRepository oidcSessionRepository;
    @Mock private OidcGroupMappingService oidcGroupMappingService;
    @Mock private AuditService auditService;

    @InjectMocks private OidcAuthService oidcAuthService;

    private static final String ISSUER_URI = "https://idp.example.com";
    private static final String CLIENT_ID = "client-id";
    private static final String CODE = "auth-code";
    private static final String CODE_VERIFIER = "verifier";
    private static final String REDIRECT_URI = "https://example.com/oauth2-callback";
    private static final String NONCE = "test-nonce";

    @BeforeEach
    void setUp() throws Exception {
        var field = OidcAuthService.class.getDeclaredField("userLocks");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var locks = (java.util.concurrent.ConcurrentMap<String, ?>) field.get(null);
        locks.clear();
    }

    // --- exchangeCodeForTokens ---

    @Test
    void exchangeCodeForTokens_successfulFullFlow() {
        var settings = enabledSettings();
        var tokenResponse = tokenResponse("access-token", "id-token");
        var claims = new JWTClaimsSet.Builder().subject("sub-123").build();
        var userClaims = userClaims("jdoe", "sub-123");
        var user = existingOidcUser("jdoe", "sub-123");
        var expectedResponse = ResponseEntity.ok(Map.of("token", "jwt"));

        when(appSettingService.getAppSettings()).thenReturn(settings);
        when(oidcTokenClient.exchangeAuthorizationCode(CODE, CODE_VERIFIER, REDIRECT_URI, settings.getOidcProviderDetails()))
                .thenReturn(tokenResponse);
        when(oidcTokenValidator.validateIdToken("id-token", ISSUER_URI, CLIENT_ID, NONCE, "access-token"))
                .thenReturn(claims);
        when(oidcTokenClient.fetchUserInfo("access-token", ISSUER_URI)).thenReturn(Map.of());
        when(oidcClaimExtractor.extractClaims(eq(claims), any(), eq(Map.of()))).thenReturn(userClaims);
        when(userRepository.findByOidcIssuerAndOidcSubject(ISSUER_URI, "sub-123")).thenReturn(Optional.of(user));
        when(appSettingService.getSettingValue("oidc_session_duration_hours")).thenReturn(null);
        when(authenticationService.loginUser(user)).thenReturn(expectedResponse);

        var result = oidcAuthService.exchangeCodeForTokens(CODE, CODE_VERIFIER, REDIRECT_URI, NONCE, mockRequest());

        assertThat(result).isEqualTo(expectedResponse);
        verify(oidcSessionRepository).save(any());
        verify(auditService).log(any(), eq("User"), eq(1L), contains("OIDC login successful"));
    }

    @Test
    void exchangeCodeForTokens_throwsForbiddenWhenOidcNotEnabled() {
        var settings = new AppSettings();
        settings.setOidcEnabled(false);
        when(appSettingService.getAppSettings()).thenReturn(settings);

        assertThatThrownBy(() -> oidcAuthService.exchangeCodeForTokens(CODE, CODE_VERIFIER, REDIRECT_URI, NONCE, mockRequest()))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("OIDC is not enabled");
    }

    @Test
    void exchangeCodeForTokens_throwsForbiddenWhenProviderDetailsNull() {
        var settings = new AppSettings();
        settings.setOidcEnabled(true);
        settings.setOidcProviderDetails(null);
        when(appSettingService.getAppSettings()).thenReturn(settings);

        assertThatThrownBy(() -> oidcAuthService.exchangeCodeForTokens(CODE, CODE_VERIFIER, REDIRECT_URI, NONCE, mockRequest()))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("OIDC is not properly configured");
    }

    @Test
    void exchangeCodeForTokens_throwsForbiddenWhenIssuerUriNull() {
        var settings = new AppSettings();
        settings.setOidcEnabled(true);
        var provider = new OidcProviderDetails();
        provider.setIssuerUri(null);
        settings.setOidcProviderDetails(provider);
        when(appSettingService.getAppSettings()).thenReturn(settings);

        assertThatThrownBy(() -> oidcAuthService.exchangeCodeForTokens(CODE, CODE_VERIFIER, REDIRECT_URI, NONCE, mockRequest()))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("OIDC is not properly configured");
    }

    @Test
    void exchangeCodeForTokens_throwsWhenIdTokenNull() {
        var settings = enabledSettings();
        var tokenResponse = tokenResponse("access-token", null);

        when(appSettingService.getAppSettings()).thenReturn(settings);
        when(oidcTokenClient.exchangeAuthorizationCode(CODE, CODE_VERIFIER, REDIRECT_URI, settings.getOidcProviderDetails()))
                .thenReturn(tokenResponse);

        assertThatThrownBy(() -> oidcAuthService.exchangeCodeForTokens(CODE, CODE_VERIFIER, REDIRECT_URI, NONCE, mockRequest()))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("No ID token received");
    }

    @Test
    void exchangeCodeForTokens_skipsUserInfoWhenAccessTokenNull() {
        var settings = enabledSettings();
        var tokenResponse = tokenResponse(null, "id-token");
        var claims = new JWTClaimsSet.Builder().subject("sub-123").build();
        var userClaims = userClaims("jdoe", "sub-123");
        var user = existingOidcUser("jdoe", "sub-123");

        when(appSettingService.getAppSettings()).thenReturn(settings);
        when(oidcTokenClient.exchangeAuthorizationCode(CODE, CODE_VERIFIER, REDIRECT_URI, settings.getOidcProviderDetails()))
                .thenReturn(tokenResponse);
        when(oidcTokenValidator.validateIdToken("id-token", ISSUER_URI, CLIENT_ID, NONCE, null))
                .thenReturn(claims);
        when(oidcClaimExtractor.extractClaims(eq(claims), any(), eq(Map.of()))).thenReturn(userClaims);
        when(userRepository.findByOidcIssuerAndOidcSubject(ISSUER_URI, "sub-123")).thenReturn(Optional.of(user));
        when(appSettingService.getSettingValue("oidc_session_duration_hours")).thenReturn(null);
        when(authenticationService.loginUser(user)).thenReturn(ResponseEntity.ok(Map.of()));

        oidcAuthService.exchangeCodeForTokens(CODE, CODE_VERIFIER, REDIRECT_URI, NONCE, mockRequest());

        verify(oidcTokenClient, never()).fetchUserInfo(anyString(), anyString());
    }

    @Test
    void exchangeCodeForTokens_customSessionDurationApplied() {
        var settings = enabledSettings();
        var tokenResponse = tokenResponse("access-token", "id-token");
        var claims = new JWTClaimsSet.Builder().subject("sub-123").build();
        var userClaims = userClaims("jdoe", "sub-123");
        var user = existingOidcUser("jdoe", "sub-123");

        when(appSettingService.getAppSettings()).thenReturn(settings);
        when(oidcTokenClient.exchangeAuthorizationCode(CODE, CODE_VERIFIER, REDIRECT_URI, settings.getOidcProviderDetails()))
                .thenReturn(tokenResponse);
        when(oidcTokenValidator.validateIdToken("id-token", ISSUER_URI, CLIENT_ID, NONCE, "access-token"))
                .thenReturn(claims);
        when(oidcTokenClient.fetchUserInfo("access-token", ISSUER_URI)).thenReturn(Map.of());
        when(oidcClaimExtractor.extractClaims(eq(claims), any(), eq(Map.of()))).thenReturn(userClaims);
        when(userRepository.findByOidcIssuerAndOidcSubject(ISSUER_URI, "sub-123")).thenReturn(Optional.of(user));
        when(appSettingService.getSettingValue("oidc_session_duration_hours")).thenReturn("8");
        when(authenticationService.loginUser(user, 8 * 3_600_000L)).thenReturn(ResponseEntity.ok(Map.of()));

        oidcAuthService.exchangeCodeForTokens(CODE, CODE_VERIFIER, REDIRECT_URI, NONCE, mockRequest());

        verify(authenticationService).loginUser(user, 8 * 3_600_000L);
        verify(authenticationService, never()).loginUser(user);
    }

    @Test
    void exchangeCodeForTokens_fallsBackToDefaultWhenSessionDurationInvalid() {
        var settings = enabledSettings();
        var tokenResponse = tokenResponse("access-token", "id-token");
        var claims = new JWTClaimsSet.Builder().subject("sub-123").build();
        var userClaims = userClaims("jdoe", "sub-123");
        var user = existingOidcUser("jdoe", "sub-123");

        when(appSettingService.getAppSettings()).thenReturn(settings);
        when(oidcTokenClient.exchangeAuthorizationCode(CODE, CODE_VERIFIER, REDIRECT_URI, settings.getOidcProviderDetails()))
                .thenReturn(tokenResponse);
        when(oidcTokenValidator.validateIdToken("id-token", ISSUER_URI, CLIENT_ID, NONCE, "access-token"))
                .thenReturn(claims);
        when(oidcTokenClient.fetchUserInfo("access-token", ISSUER_URI)).thenReturn(Map.of());
        when(oidcClaimExtractor.extractClaims(eq(claims), any(), eq(Map.of()))).thenReturn(userClaims);
        when(userRepository.findByOidcIssuerAndOidcSubject(ISSUER_URI, "sub-123")).thenReturn(Optional.of(user));
        when(appSettingService.getSettingValue("oidc_session_duration_hours")).thenReturn("not-a-number");
        when(authenticationService.loginUser(user)).thenReturn(ResponseEntity.ok(Map.of()));

        oidcAuthService.exchangeCodeForTokens(CODE, CODE_VERIFIER, REDIRECT_URI, NONCE, mockRequest());

        verify(authenticationService).loginUser(user);
    }

    @Test
    void exchangeCodeForTokens_fallsBackToDefaultWhenSessionDurationNull() {
        var settings = enabledSettings();
        var tokenResponse = tokenResponse("access-token", "id-token");
        var claims = new JWTClaimsSet.Builder().subject("sub-123").build();
        var userClaims = userClaims("jdoe", "sub-123");
        var user = existingOidcUser("jdoe", "sub-123");

        when(appSettingService.getAppSettings()).thenReturn(settings);
        when(oidcTokenClient.exchangeAuthorizationCode(CODE, CODE_VERIFIER, REDIRECT_URI, settings.getOidcProviderDetails()))
                .thenReturn(tokenResponse);
        when(oidcTokenValidator.validateIdToken("id-token", ISSUER_URI, CLIENT_ID, NONCE, "access-token"))
                .thenReturn(claims);
        when(oidcTokenClient.fetchUserInfo("access-token", ISSUER_URI)).thenReturn(Map.of());
        when(oidcClaimExtractor.extractClaims(eq(claims), any(), eq(Map.of()))).thenReturn(userClaims);
        when(userRepository.findByOidcIssuerAndOidcSubject(ISSUER_URI, "sub-123")).thenReturn(Optional.of(user));
        when(appSettingService.getSettingValue("oidc_session_duration_hours")).thenReturn(null);
        when(authenticationService.loginUser(user)).thenReturn(ResponseEntity.ok(Map.of()));

        oidcAuthService.exchangeCodeForTokens(CODE, CODE_VERIFIER, REDIRECT_URI, NONCE, mockRequest());

        verify(authenticationService).loginUser(user);
    }

    // --- validateAppRedirectUri ---

    @Test
    void validateAppRedirectUri_validMobileRedirect() {
        oidcAuthService.validateAppRedirectUri("booklore://some-path");
    }

    @Test
    void validateAppRedirectUri_nullThrows() {
        assertThatThrownBy(() -> oidcAuthService.validateAppRedirectUri(null))
                .isInstanceOf(APIException.class);
    }

    @Test
    void validateAppRedirectUri_nonMobileSchemeThrows() {
        assertThatThrownBy(() -> oidcAuthService.validateAppRedirectUri("https://example.com"))
                .isInstanceOf(APIException.class);
    }

    // --- findOrProvisionUser (indirect) ---

    @Test
    void findOrProvisionUser_existingOidcUserFoundBySubjectIsSynced() {
        var settings = enabledSettings();
        var tokenResponse = tokenResponse("access-token", "id-token");
        var claims = new JWTClaimsSet.Builder().subject("sub-123").build();
        var userClaims = userClaims("jdoe", "sub-123");
        var user = existingOidcUser("jdoe", "sub-123");
        user.setEmail("old@example.com");

        when(appSettingService.getAppSettings()).thenReturn(settings);
        when(oidcTokenClient.exchangeAuthorizationCode(CODE, CODE_VERIFIER, REDIRECT_URI, settings.getOidcProviderDetails()))
                .thenReturn(tokenResponse);
        when(oidcTokenValidator.validateIdToken("id-token", ISSUER_URI, CLIENT_ID, NONCE, "access-token"))
                .thenReturn(claims);
        when(oidcTokenClient.fetchUserInfo("access-token", ISSUER_URI)).thenReturn(Map.of());
        when(oidcClaimExtractor.extractClaims(eq(claims), any(), eq(Map.of()))).thenReturn(userClaims);
        when(userRepository.findByOidcIssuerAndOidcSubject(ISSUER_URI, "sub-123")).thenReturn(Optional.of(user));
        when(appSettingService.getSettingValue("oidc_session_duration_hours")).thenReturn(null);
        when(authenticationService.loginUser(user)).thenReturn(ResponseEntity.ok(Map.of()));

        oidcAuthService.exchangeCodeForTokens(CODE, CODE_VERIFIER, REDIRECT_URI, NONCE, mockRequest());

        verify(userRepository).save(user);
        assertThat(user.getEmail()).isEqualTo("jdoe@example.com");
    }

    @Test
    void findOrProvisionUser_existingLocalUserLinkedWhenAllowLocalLinking() {
        var settings = enabledSettings();
        withAutoProvision(settings, false, true);
        var tokenResponse = tokenResponse("access-token", "id-token");
        var claims = new JWTClaimsSet.Builder().subject("sub-123").build();
        var userClaims = userClaims("jdoe", "sub-123");
        var user = BookLoreUserEntity.builder()
                .id(1L).username("jdoe").email("jdoe@example.com").name("John Doe")
                .provisioningMethod(ProvisioningMethod.LOCAL)
                .build();

        when(appSettingService.getAppSettings()).thenReturn(settings);
        when(oidcTokenClient.exchangeAuthorizationCode(CODE, CODE_VERIFIER, REDIRECT_URI, settings.getOidcProviderDetails()))
                .thenReturn(tokenResponse);
        when(oidcTokenValidator.validateIdToken("id-token", ISSUER_URI, CLIENT_ID, NONCE, "access-token"))
                .thenReturn(claims);
        when(oidcTokenClient.fetchUserInfo("access-token", ISSUER_URI)).thenReturn(Map.of());
        when(oidcClaimExtractor.extractClaims(eq(claims), any(), eq(Map.of()))).thenReturn(userClaims);
        when(userRepository.findByOidcIssuerAndOidcSubject(ISSUER_URI, "sub-123")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("jdoe")).thenReturn(Optional.of(user));
        when(appSettingService.getSettingValue("oidc_session_duration_hours")).thenReturn(null);
        when(authenticationService.loginUser(user)).thenReturn(ResponseEntity.ok(Map.of()));

        oidcAuthService.exchangeCodeForTokens(CODE, CODE_VERIFIER, REDIRECT_URI, NONCE, mockRequest());

        assertThat(user.getProvisioningMethod()).isEqualTo(ProvisioningMethod.OIDC);
        verify(auditService).log(any(), eq("User"), eq(1L), contains("linked to OIDC"));
    }

    @Test
    void findOrProvisionUser_localUserNotLinkedWhenLinkingDisabled() {
        var settings = enabledSettings();
        withAutoProvision(settings, false, false);
        var tokenResponse = tokenResponse("access-token", "id-token");
        var claims = new JWTClaimsSet.Builder().subject("sub-123").build();
        var userClaims = userClaims("jdoe", "sub-123");
        var user = BookLoreUserEntity.builder()
                .id(1L).username("jdoe").provisioningMethod(ProvisioningMethod.LOCAL).build();

        when(appSettingService.getAppSettings()).thenReturn(settings);
        when(oidcTokenClient.exchangeAuthorizationCode(CODE, CODE_VERIFIER, REDIRECT_URI, settings.getOidcProviderDetails()))
                .thenReturn(tokenResponse);
        when(oidcTokenValidator.validateIdToken("id-token", ISSUER_URI, CLIENT_ID, NONCE, "access-token"))
                .thenReturn(claims);
        when(oidcTokenClient.fetchUserInfo("access-token", ISSUER_URI)).thenReturn(Map.of());
        when(oidcClaimExtractor.extractClaims(eq(claims), any(), eq(Map.of()))).thenReturn(userClaims);
        when(userRepository.findByOidcIssuerAndOidcSubject(ISSUER_URI, "sub-123")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("jdoe")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> oidcAuthService.exchangeCodeForTokens(CODE, CODE_VERIFIER, REDIRECT_URI, NONCE, mockRequest()))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("not provisioned");
    }

    @Test
    void findOrProvisionUser_newUserAutoProvisioned() {
        var settings = enabledSettings();
        var provisionDetails = withAutoProvision(settings, true, false);
        var tokenResponse = tokenResponse("access-token", "id-token");
        var claims = new JWTClaimsSet.Builder().subject("sub-new").build();
        var userClaims = userClaims("newuser", "sub-new");
        var newUser = BookLoreUserEntity.builder()
                .id(2L).username("newuser").provisioningMethod(ProvisioningMethod.OIDC)
                .oidcSubject("sub-new").oidcIssuer(ISSUER_URI).build();

        when(appSettingService.getAppSettings()).thenReturn(settings);
        when(oidcTokenClient.exchangeAuthorizationCode(CODE, CODE_VERIFIER, REDIRECT_URI, settings.getOidcProviderDetails()))
                .thenReturn(tokenResponse);
        when(oidcTokenValidator.validateIdToken("id-token", ISSUER_URI, CLIENT_ID, NONCE, "access-token"))
                .thenReturn(claims);
        when(oidcTokenClient.fetchUserInfo("access-token", ISSUER_URI)).thenReturn(Map.of());
        when(oidcClaimExtractor.extractClaims(eq(claims), any(), eq(Map.of()))).thenReturn(userClaims);
        when(userRepository.findByOidcIssuerAndOidcSubject(ISSUER_URI, "sub-new")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("newuser")).thenReturn(Optional.empty());
        when(userProvisioningService.provisionOidcUser("newuser", "newuser@example.com", "New User", "sub-new", ISSUER_URI, null, provisionDetails))
                .thenReturn(newUser);
        when(appSettingService.getSettingValue("oidc_session_duration_hours")).thenReturn(null);
        when(authenticationService.loginUser(newUser)).thenReturn(ResponseEntity.ok(Map.of()));

        oidcAuthService.exchangeCodeForTokens(CODE, CODE_VERIFIER, REDIRECT_URI, NONCE, mockRequest());

        verify(userProvisioningService).provisionOidcUser(eq("newuser"), eq("newuser@example.com"), eq("New User"),
                eq("sub-new"), eq(ISSUER_URI), isNull(), eq(provisionDetails));
        verify(oidcGroupMappingService).syncUserGroups(newUser, List.of("group1"));
        verify(auditService).log(any(), eq("User"), eq(2L), contains("auto-provisioned"));
    }

    @Test
    void findOrProvisionUser_throwsWhenAutoProvisioningDisabled() {
        var settings = enabledSettings();
        withAutoProvision(settings, false, false);
        var tokenResponse = tokenResponse("access-token", "id-token");
        var claims = new JWTClaimsSet.Builder().subject("sub-unknown").build();
        var userClaims = userClaims("unknown", "sub-unknown");

        when(appSettingService.getAppSettings()).thenReturn(settings);
        when(oidcTokenClient.exchangeAuthorizationCode(CODE, CODE_VERIFIER, REDIRECT_URI, settings.getOidcProviderDetails()))
                .thenReturn(tokenResponse);
        when(oidcTokenValidator.validateIdToken("id-token", ISSUER_URI, CLIENT_ID, NONCE, "access-token"))
                .thenReturn(claims);
        when(oidcTokenClient.fetchUserInfo("access-token", ISSUER_URI)).thenReturn(Map.of());
        when(oidcClaimExtractor.extractClaims(eq(claims), any(), eq(Map.of()))).thenReturn(userClaims);
        when(userRepository.findByOidcIssuerAndOidcSubject(ISSUER_URI, "sub-unknown")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> oidcAuthService.exchangeCodeForTokens(CODE, CODE_VERIFIER, REDIRECT_URI, NONCE, mockRequest()))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("not provisioned");
    }

    // --- syncExistingUser (indirect) ---

    @Test
    void syncExistingUser_updatesFieldsWhenClaimsDiffer() {
        var settings = enabledSettings();
        var tokenResponse = tokenResponse("access-token", "id-token");
        var claims = new JWTClaimsSet.Builder().subject("sub-123").build();
        var userClaims = userClaims("jdoe", "sub-123");
        var user = existingOidcUser("jdoe", "sub-123");
        user.setEmail("old@example.com");
        user.setName("Old Name");
        user.setAvatarUrl("old-url");

        when(appSettingService.getAppSettings()).thenReturn(settings);
        when(oidcTokenClient.exchangeAuthorizationCode(CODE, CODE_VERIFIER, REDIRECT_URI, settings.getOidcProviderDetails()))
                .thenReturn(tokenResponse);
        when(oidcTokenValidator.validateIdToken("id-token", ISSUER_URI, CLIENT_ID, NONCE, "access-token"))
                .thenReturn(claims);
        when(oidcTokenClient.fetchUserInfo("access-token", ISSUER_URI)).thenReturn(Map.of());
        when(oidcClaimExtractor.extractClaims(eq(claims), any(), eq(Map.of()))).thenReturn(userClaims);
        when(userRepository.findByOidcIssuerAndOidcSubject(ISSUER_URI, "sub-123")).thenReturn(Optional.of(user));
        when(appSettingService.getSettingValue("oidc_session_duration_hours")).thenReturn(null);
        when(authenticationService.loginUser(user)).thenReturn(ResponseEntity.ok(Map.of()));

        oidcAuthService.exchangeCodeForTokens(CODE, CODE_VERIFIER, REDIRECT_URI, NONCE, mockRequest());

        assertThat(user.getEmail()).isEqualTo("jdoe@example.com");
        assertThat(user.getName()).isEqualTo("John Doe");
        verify(userRepository).save(user);
    }

    @Test
    void syncExistingUser_notSavedWhenClaimsUnchanged() {
        var settings = enabledSettings();
        var tokenResponse = tokenResponse("access-token", "id-token");
        var claims = new JWTClaimsSet.Builder().subject("sub-123").build();
        var userClaims = userClaims("jdoe", "sub-123");
        var user = BookLoreUserEntity.builder()
                .id(1L).username("jdoe").email("jdoe@example.com").name("John Doe")
                .provisioningMethod(ProvisioningMethod.OIDC)
                .oidcSubject("sub-123").oidcIssuer(ISSUER_URI)
                .build();

        when(appSettingService.getAppSettings()).thenReturn(settings);
        when(oidcTokenClient.exchangeAuthorizationCode(CODE, CODE_VERIFIER, REDIRECT_URI, settings.getOidcProviderDetails()))
                .thenReturn(tokenResponse);
        when(oidcTokenValidator.validateIdToken("id-token", ISSUER_URI, CLIENT_ID, NONCE, "access-token"))
                .thenReturn(claims);
        when(oidcTokenClient.fetchUserInfo("access-token", ISSUER_URI)).thenReturn(Map.of());
        when(oidcClaimExtractor.extractClaims(eq(claims), any(), eq(Map.of()))).thenReturn(userClaims);
        when(userRepository.findByOidcIssuerAndOidcSubject(ISSUER_URI, "sub-123")).thenReturn(Optional.of(user));
        when(appSettingService.getSettingValue("oidc_session_duration_hours")).thenReturn(null);
        when(authenticationService.loginUser(user)).thenReturn(ResponseEntity.ok(Map.of()));

        oidcAuthService.exchangeCodeForTokens(CODE, CODE_VERIFIER, REDIRECT_URI, NONCE, mockRequest());

        verify(userRepository, never()).save(any());
    }

    @Test
    void syncExistingUser_setsOidcSubjectAndIssuerWhenBlank() {
        var settings = enabledSettings();
        withAutoProvision(settings, false, true);
        var tokenResponse = tokenResponse("access-token", "id-token");
        var claims = new JWTClaimsSet.Builder().subject("sub-123").build();
        var userClaims = userClaims("jdoe", "sub-123");
        var user = BookLoreUserEntity.builder()
                .id(1L).username("jdoe").email("jdoe@example.com").name("John Doe")
                .provisioningMethod(ProvisioningMethod.OIDC)
                .oidcSubject(null).oidcIssuer(null)
                .build();

        when(appSettingService.getAppSettings()).thenReturn(settings);
        when(oidcTokenClient.exchangeAuthorizationCode(CODE, CODE_VERIFIER, REDIRECT_URI, settings.getOidcProviderDetails()))
                .thenReturn(tokenResponse);
        when(oidcTokenValidator.validateIdToken("id-token", ISSUER_URI, CLIENT_ID, NONCE, "access-token"))
                .thenReturn(claims);
        when(oidcTokenClient.fetchUserInfo("access-token", ISSUER_URI)).thenReturn(Map.of());
        when(oidcClaimExtractor.extractClaims(eq(claims), any(), eq(Map.of()))).thenReturn(userClaims);
        when(userRepository.findByOidcIssuerAndOidcSubject(ISSUER_URI, "sub-123")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("jdoe")).thenReturn(Optional.of(user));
        when(appSettingService.getSettingValue("oidc_session_duration_hours")).thenReturn(null);
        when(authenticationService.loginUser(user)).thenReturn(ResponseEntity.ok(Map.of()));

        oidcAuthService.exchangeCodeForTokens(CODE, CODE_VERIFIER, REDIRECT_URI, NONCE, mockRequest());

        assertThat(user.getOidcSubject()).isEqualTo("sub-123");
        assertThat(user.getOidcIssuer()).isEqualTo(ISSUER_URI);
        verify(userRepository).save(user);
    }

    // --- validateRedirectUri (indirect) ---

    @Test
    void validateRedirectUri_validHttpsRedirectPasses() {
        var settings = enabledSettings();
        var tokenResponse = tokenResponse(null, "id-token");
        var claims = new JWTClaimsSet.Builder().subject("sub-123").build();
        var userClaims = userClaims("jdoe", "sub-123");
        var user = existingOidcUser("jdoe", "sub-123");

        when(appSettingService.getAppSettings()).thenReturn(settings);
        when(oidcTokenClient.exchangeAuthorizationCode(eq(CODE), eq(CODE_VERIFIER), eq("https://example.com/oauth2-callback"), any()))
                .thenReturn(tokenResponse);
        when(oidcTokenValidator.validateIdToken("id-token", ISSUER_URI, CLIENT_ID, NONCE, null))
                .thenReturn(claims);
        when(oidcClaimExtractor.extractClaims(eq(claims), any(), eq(Map.of()))).thenReturn(userClaims);
        when(userRepository.findByOidcIssuerAndOidcSubject(ISSUER_URI, "sub-123")).thenReturn(Optional.of(user));
        when(appSettingService.getSettingValue("oidc_session_duration_hours")).thenReturn(null);
        when(authenticationService.loginUser(user)).thenReturn(ResponseEntity.ok(Map.of()));

        var result = oidcAuthService.exchangeCodeForTokens(CODE, CODE_VERIFIER, "https://example.com/oauth2-callback", NONCE, mockRequest());

        assertThat(result.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void validateRedirectUri_validMobileRedirectPasses() {
        var settings = enabledSettings();
        var tokenResponse = tokenResponse(null, "id-token");
        var claims = new JWTClaimsSet.Builder().subject("sub-123").build();
        var userClaims = userClaims("jdoe", "sub-123");
        var user = existingOidcUser("jdoe", "sub-123");

        when(appSettingService.getAppSettings()).thenReturn(settings);
        when(oidcTokenClient.exchangeAuthorizationCode(eq(CODE), eq(CODE_VERIFIER), eq("booklore://oauth2-callback"), any()))
                .thenReturn(tokenResponse);
        when(oidcTokenValidator.validateIdToken("id-token", ISSUER_URI, CLIENT_ID, NONCE, null))
                .thenReturn(claims);
        when(oidcClaimExtractor.extractClaims(eq(claims), any(), eq(Map.of()))).thenReturn(userClaims);
        when(userRepository.findByOidcIssuerAndOidcSubject(ISSUER_URI, "sub-123")).thenReturn(Optional.of(user));
        when(appSettingService.getSettingValue("oidc_session_duration_hours")).thenReturn(null);
        when(authenticationService.loginUser(user)).thenReturn(ResponseEntity.ok(Map.of()));

        var result = oidcAuthService.exchangeCodeForTokens(CODE, CODE_VERIFIER, "booklore://oauth2-callback", NONCE, mockRequest());

        assertThat(result.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void validateRedirectUri_invalidMobileRedirectThrows() {
        var settings = enabledSettings();
        when(appSettingService.getAppSettings()).thenReturn(settings);

        assertThatThrownBy(() -> oidcAuthService.exchangeCodeForTokens(CODE, CODE_VERIFIER, "booklore://evil-path", NONCE, mockRequest()))
                .isInstanceOf(APIException.class);
    }

    @Test
    void validateRedirectUri_nonHttpSchemeThrows() {
        var settings = enabledSettings();
        when(appSettingService.getAppSettings()).thenReturn(settings);

        assertThatThrownBy(() -> oidcAuthService.exchangeCodeForTokens(CODE, CODE_VERIFIER, "ftp://example.com/oauth2-callback", NONCE, mockRequest()))
                .isInstanceOf(APIException.class);
    }

    @Test
    void validateRedirectUri_missingCallbackPathThrows() {
        var settings = enabledSettings();
        when(appSettingService.getAppSettings()).thenReturn(settings);

        assertThatThrownBy(() -> oidcAuthService.exchangeCodeForTokens(CODE, CODE_VERIFIER, "https://example.com/login", NONCE, mockRequest()))
                .isInstanceOf(APIException.class);
    }

    @Test
    void validateRedirectUri_originMismatchThrows() {
        var settings = enabledSettings();
        when(appSettingService.getAppSettings()).thenReturn(settings);

        var request = new MockHttpServletRequest();
        request.setScheme("https");
        request.setServerName("other-host.com");
        request.setServerPort(443);

        assertThatThrownBy(() -> oidcAuthService.exchangeCodeForTokens(CODE, CODE_VERIFIER, "https://example.com/oauth2-callback", NONCE, request))
                .isInstanceOf(APIException.class);
    }

    // --- Helpers ---

    private MockHttpServletRequest mockRequest() {
        var request = new MockHttpServletRequest();
        request.setScheme("https");
        request.setServerName("example.com");
        request.setServerPort(443);
        return request;
    }

    private AppSettings enabledSettings() {
        var settings = new AppSettings();
        settings.setOidcEnabled(true);
        var provider = new OidcProviderDetails();
        provider.setIssuerUri(ISSUER_URI);
        provider.setClientId(CLIENT_ID);
        settings.setOidcProviderDetails(provider);
        return settings;
    }

    private OidcAutoProvisionDetails withAutoProvision(AppSettings settings, boolean autoProvision, boolean allowLocalLinking) {
        var details = new OidcAutoProvisionDetails();
        details.setEnableAutoProvisioning(autoProvision);
        details.setAllowLocalAccountLinking(allowLocalLinking);
        settings.setOidcAutoProvisionDetails(details);
        return details;
    }

    private OidcTokenClient.TokenResponse tokenResponse(String accessToken, String idToken) {
        return new OidcTokenClient.TokenResponse(accessToken, idToken, "refresh-token", "Bearer", 3600);
    }

    private OidcClaimExtractor.OidcUserClaims userClaims(String username, String subject) {
        return new OidcClaimExtractor.OidcUserClaims(
                username, username + "@example.com", username.equals("jdoe") ? "John Doe" : "New User",
                subject, null, List.of("group1")
        );
    }

    private BookLoreUserEntity existingOidcUser(String username, String subject) {
        return BookLoreUserEntity.builder()
                .id(1L).username(username).email(username + "@example.com").name("John Doe")
                .provisioningMethod(ProvisioningMethod.OIDC)
                .oidcSubject(subject).oidcIssuer(ISSUER_URI)
                .build();
    }
}
