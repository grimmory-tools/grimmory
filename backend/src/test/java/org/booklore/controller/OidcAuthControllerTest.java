package org.booklore.controller;

import org.booklore.config.security.oidc.BackchannelLogoutService;
import org.booklore.config.security.oidc.OidcAuthService;
import org.booklore.config.security.oidc.OidcCallbackRequest;
import org.booklore.config.security.oidc.OidcStateService;
import org.booklore.exception.APIException;
import org.booklore.service.audit.AuditService;
import org.booklore.model.enums.AuditAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OidcAuthControllerTest {

    @Mock
    private OidcAuthService oidcAuthService;

    @Mock
    private BackchannelLogoutService backchannelLogoutService;

    @Mock
    private OidcStateService oidcStateService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private OidcAuthController controller;

    private final MockHttpServletRequest httpRequest = new MockHttpServletRequest();

    @Test
    void generateState_returnsStateInResponseBody() {
        when(oidcStateService.generateState()).thenReturn("abc123");

        ResponseEntity<Map<String, String>> response = controller.generateState();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("state", "abc123");
    }

    @Test
    void handleCallback_validatesStateAndReturnsTokens() {
        var request = new OidcCallbackRequest("code1", "verifier1", "https://redirect", "nonce1", "state1");
        var tokenResponse = ResponseEntity.ok(Map.of("accessToken", "at", "refreshToken", "rt"));
        when(oidcAuthService.exchangeCodeForTokens("code1", "verifier1", "https://redirect", "nonce1", httpRequest))
                .thenReturn(tokenResponse);

        ResponseEntity<Map<String, String>> response = controller.handleCallback(request, httpRequest);

        verify(oidcStateService).validateAndConsume("state1");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("accessToken", "at");
    }

    @Test
    void handleCallback_auditsOnExceptionAndRethrows() {
        var request = new OidcCallbackRequest("code1", "verifier1", "https://redirect", "nonce1", "state1");
        var exception = new APIException("fail", HttpStatus.INTERNAL_SERVER_ERROR);
        when(oidcAuthService.exchangeCodeForTokens("code1", "verifier1", "https://redirect", "nonce1", httpRequest))
                .thenThrow(exception);

        assertThatThrownBy(() -> controller.handleCallback(request, httpRequest))
                .isSameAs(exception);

        verify(auditService).log(eq(AuditAction.OIDC_LOGIN_FAILED), any(String.class));
    }

    @Test
    void handleRedirect_returns302WithFragmentContainingTokens() {
        var tokens = Map.of("accessToken", "at123", "refreshToken", "rt456");
        var tokenResponse = ResponseEntity.ok(tokens);
        when(oidcAuthService.exchangeCodeForTokens("code", "verifier", "https://redir", "nonce", httpRequest))
                .thenReturn(tokenResponse);

        ResponseEntity<Void> response = controller.handleRedirect(
                "code", "verifier", "https://redir", "nonce", "state", "https://app.example.com", httpRequest);

        verify(oidcStateService).validateAndConsume("state");
        verify(oidcAuthService).validateAppRedirectUri("https://app.example.com");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);

        String location = response.getHeaders().getLocation().toString();
        assertThat(location).startsWith("https://app.example.com#");
        assertThat(location).contains("access_token=" + URLEncoder.encode("at123", StandardCharsets.UTF_8));
        assertThat(location).contains("refresh_token=" + URLEncoder.encode("rt456", StandardCharsets.UTF_8));
    }

    @Test
    void handleRedirect_includesIsDefaultPasswordInFragmentWhenPresent() {
        var tokens = Map.of("accessToken", "at", "refreshToken", "rt", "isDefaultPassword", "true");
        var tokenResponse = ResponseEntity.ok(tokens);
        when(oidcAuthService.exchangeCodeForTokens("code", "verifier", "https://redir", "nonce", httpRequest))
                .thenReturn(tokenResponse);

        ResponseEntity<Void> response = controller.handleRedirect(
                "code", "verifier", "https://redir", "nonce", "state", "https://app.example.com", httpRequest);

        String location = response.getHeaders().getLocation().toString();
        assertThat(location).contains("is_default_password=" + URLEncoder.encode("true", StandardCharsets.UTF_8));
    }

    @Test
    void handleRedirect_returns302WithErrorFragmentOnException() {
        when(oidcAuthService.exchangeCodeForTokens("code", "verifier", "https://redir", "nonce", httpRequest))
                .thenThrow(new APIException("fail", HttpStatus.INTERNAL_SERVER_ERROR));

        ResponseEntity<Void> response = controller.handleRedirect(
                "code", "verifier", "https://redir", "nonce", "state", "https://app.example.com", httpRequest);

        verify(auditService).log(eq(AuditAction.OIDC_LOGIN_FAILED), any(String.class));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);

        String location = response.getHeaders().getLocation().toString();
        assertThat(location).isEqualTo("https://app.example.com#error=" + URLEncoder.encode("Authentication failed", StandardCharsets.UTF_8));
    }

    @Test
    void handleRedirect_throwsWhenTokenResponseBodyIsNull() {
        ResponseEntity<Map<String, String>> tokenResponse = ResponseEntity.ok(null);
        when(oidcAuthService.exchangeCodeForTokens("code", "verifier", "https://redir", "nonce", httpRequest))
                .thenReturn(tokenResponse);

        ResponseEntity<Void> response = controller.handleRedirect(
                "code", "verifier", "https://redir", "nonce", "state", "https://app.example.com", httpRequest);

        verify(auditService).log(eq(AuditAction.OIDC_LOGIN_FAILED), any(String.class));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        String location = response.getHeaders().getLocation().toString();
        assertThat(location).contains("error=");
    }

    @Test
    void handleMobileCallback_validatesStateAndReturnsTokens() {
        var tokenResponse = ResponseEntity.ok(Map.of("accessToken", "at", "refreshToken", "rt"));
        when(oidcAuthService.exchangeCodeForTokens("code", "verifier", "https://redir", "nonce", httpRequest))
                .thenReturn(tokenResponse);

        ResponseEntity<Map<String, String>> response = controller.handleMobileCallback(
                "code", "verifier", "https://redir", "nonce", "state", httpRequest);

        verify(oidcStateService).validateAndConsume("state");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("accessToken", "at");
    }

    @Test
    void handleMobileCallback_auditsOnExceptionAndRethrows() {
        var exception = new APIException("fail", HttpStatus.INTERNAL_SERVER_ERROR);
        when(oidcAuthService.exchangeCodeForTokens("code", "verifier", "https://redir", "nonce", httpRequest))
                .thenThrow(exception);

        assertThatThrownBy(() -> controller.handleMobileCallback(
                "code", "verifier", "https://redir", "nonce", "state", httpRequest))
                .isSameAs(exception);

        verify(auditService).log(eq(AuditAction.OIDC_LOGIN_FAILED), any(String.class));
    }

    @Test
    void backchannelLogout_returns200OnSuccess() {
        ResponseEntity<Void> response = controller.backchannelLogout("logout-token");

        verify(backchannelLogoutService).handleLogoutToken("logout-token");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void backchannelLogout_returns400OnAnyException() {
        doThrow(new RuntimeException("bad token")).when(backchannelLogoutService).handleLogoutToken("bad-token");

        ResponseEntity<Void> response = controller.backchannelLogout("bad-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
