package org.booklore.config.security.service;

import org.booklore.config.AppProperties;
import org.booklore.config.security.JwtUtils;
import org.booklore.exception.APIException;
import org.booklore.model.dto.AccessTokenDto;
import org.booklore.model.dto.request.UserLoginRequest;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.RefreshTokenEntity;
import org.booklore.model.entity.UserPermissionsEntity;
import org.booklore.repository.UserRepository;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.audit.AuditService;
import org.booklore.service.user.DefaultSettingInitializer;
import org.booklore.service.user.UserProvisioningService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.time.temporal.ChronoUnit;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock private AppProperties appProperties;
    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private UserProvisioningService userProvisioningService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtils jwtUtils;
    @Mock private DefaultSettingInitializer defaultSettingInitializer;
    @Mock private AuditService auditService;
    @Mock private AuthRateLimitService authRateLimitService;
    @Mock private AppSettingService appSettingService;

    @InjectMocks
    private AuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authenticationService, "dummyPasswordHash", "$2a$10$dummy");

        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setRemoteAddr("127.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockRequest));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void loginUser_oidcOnlyMode_throwsForNonAdminUser() {
        var appSettings = new AppSettings();
        appSettings.setOidcForceOnlyMode(true);
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        var permissions = UserPermissionsEntity.builder().permissionAdmin(false).build();
        var user = BookLoreUserEntity.builder().username("regularuser").permissions(permissions).build();
        when(userRepository.findByUsername("regularuser")).thenReturn(Optional.of(user));

        var request = new UserLoginRequest();
        request.setUsername("regularuser");
        request.setPassword("password");

        assertThatThrownBy(() -> authenticationService.loginUser(request))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("Local login is disabled");
    }

    @Test
    void loginUser_oidcOnlyMode_throwsForUnknownUser() {
        var appSettings = new AppSettings();
        appSettings.setOidcForceOnlyMode(true);
        when(appSettingService.getAppSettings()).thenReturn(appSettings);
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        var request = new UserLoginRequest();
        request.setUsername("unknown");
        request.setPassword("password");

        assertThatThrownBy(() -> authenticationService.loginUser(request))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("Local login is disabled");
    }

    @Test
    void loginUser_oidcOnlyMode_allowsAdminUser() {
        var appSettings = new AppSettings();
        appSettings.setOidcForceOnlyMode(true);
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        var permissions = UserPermissionsEntity.builder().permissionAdmin(true).build();
        var user = BookLoreUserEntity.builder()
                .id(1L)
                .username("admin")
                .passwordHash("$2a$10$hashedpassword")
                .permissions(permissions)
                .build();

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(jwtUtils.generateAccessToken(user)).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(any(BookLoreUserEntity.class))).thenReturn("refresh-token");

        var request = new UserLoginRequest();
        request.setUsername("admin");
        request.setPassword("password");

        ResponseEntity<AccessTokenDto> response = authenticationService.loginUser(request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAccessToken()).isEqualTo("access-token");
        assertThat(response.getBody().getRefreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void loginUser_oidcOnlyModeDisabled_proceedsNormally() {
        var appSettings = new AppSettings();
        appSettings.setOidcForceOnlyMode(false);
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        var permissions = UserPermissionsEntity.builder().permissionAdmin(false).build();
        var user = BookLoreUserEntity.builder()
                .id(2L)
                .username("normaluser")
                .passwordHash("$2a$10$hashedpassword")
                .permissions(permissions)
                .build();

        when(userRepository.findByUsername("normaluser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(jwtUtils.generateAccessToken(user)).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(user)).thenReturn("my-refresh-token");

        var request = new UserLoginRequest();
        request.setUsername("normaluser");
        request.setPassword("password");

        ResponseEntity<AccessTokenDto> response = authenticationService.loginUser(request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAccessToken()).isEqualTo("access-token");
    }

    @Test
    void loginUser_withCustomRefreshTokenExpiration_usesCustomValue() {
        long customExpirationMs = 3600000L;
        var user = BookLoreUserEntity.builder()
                .id(3L)
                .username("testuser")
                .build();

        when(jwtUtils.generateAccessToken(user)).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(eq(user), anyLong())).thenReturn("refresh-token");

        authenticationService.loginUser(user, customExpirationMs);
        verify(refreshTokenService).createRefreshToken(user, customExpirationMs);
    }

    @Test
    void loginUser_withCustomExpiration_returnsCorrectTokenResponse() {
        var user = BookLoreUserEntity.builder()
                .id(5L)
                .username("testuser")
                .isDefaultPassword(false)
                .build();

        when(jwtUtils.generateAccessToken(user)).thenReturn("my-access-token");
        when(refreshTokenService.createRefreshToken(eq(user), anyLong())).thenReturn("my-refresh-token");

        ResponseEntity<AccessTokenDto> response = authenticationService.loginUser(user, 7200000L);

        assertThat(response.getStatusCode().value()).isEqualTo(200);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAccessToken()).isEqualTo("my-access-token");
        assertThat(response.getBody().getRefreshToken()).isEqualTo("my-refresh-token");
        assertThat(response.getBody().getExpires()).isEqualTo(7200);
        assertThat(response.getBody().getIsDefaultPassword()).isFalse();
    }
}
