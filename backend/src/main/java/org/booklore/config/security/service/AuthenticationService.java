package org.booklore.config.security.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.booklore.config.AppProperties;
import org.booklore.config.security.JwtUtils;
import org.booklore.config.security.userdetails.OpdsUserDetails;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.request.UserLoginRequest;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.RefreshTokenEntity;
import org.booklore.model.enums.ProvisioningMethod;
import org.booklore.model.enums.UserPermission;
import org.booklore.repository.RefreshTokenRepository;
import org.booklore.repository.UserRepository;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.user.DefaultSettingInitializer;
import org.booklore.service.user.UserProvisioningService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.booklore.model.enums.AuditAction;
import org.booklore.service.audit.AuditService;
import org.booklore.util.RequestUtils;

@Slf4j
@Service
public class AuthenticationService {

    private String dummyPasswordHash;

    private final AppProperties appProperties;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserProvisioningService userProvisioningService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final DefaultSettingInitializer defaultSettingInitializer;
    private final AuditService auditService;
    private final AuthRateLimitService authRateLimitService;
    private final AppSettingService appSettingService;

    public AuthenticationService(
            AppProperties appProperties,
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            UserProvisioningService userProvisioningService,
            PasswordEncoder passwordEncoder,
            JwtUtils jwtUtils,
            DefaultSettingInitializer defaultSettingInitializer,
            AuditService auditService,
            AuthRateLimitService authRateLimitService,
            @Lazy AppSettingService appSettingService
    ) {
        this.appProperties = appProperties;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userProvisioningService = userProvisioningService;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.defaultSettingInitializer = defaultSettingInitializer;
        this.auditService = auditService;
        this.authRateLimitService = authRateLimitService;
        this.appSettingService = appSettingService;
    }

    @PostConstruct
    void initDummyHash() {
        this.dummyPasswordHash = passwordEncoder.encode("_dummy_placeholder_for_timing_equalization_");
    }

    public BookLoreUser getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof BookLoreUser user) {
            if (user.getId() != null && user.getId() != -1L) {
                defaultSettingInitializer.ensureDefaultSettings(user);
            }
            return user;
        }
        throw new IllegalStateException("Authenticated principal is not of type BookLoreUser");
    }

    public BookLoreUser getSystemUser() {
        return createSystemUser();
    }

    private BookLoreUser createSystemUser() {
        BookLoreUser.UserPermissions permissions = new BookLoreUser.UserPermissions();
        for (UserPermission permission : UserPermission.values()) {
            permission.setInDto(permissions, true);
        }

        return BookLoreUser.builder()
                .id(-1L)
                .username("system")
                .name("System User")
                .email("system@booklore.internal")
                .provisioningMethod(ProvisioningMethod.LOCAL)
                .isDefaultPassword(false)
                .permissions(permissions)
                .assignedLibraries(List.of())
                .userSettings(new BookLoreUser.UserSettings())
                .build();
    }

    public OpdsUserDetails getOpdsUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof OpdsUserDetails opdsUser) {
            return opdsUser;
        }
        throw new IllegalStateException("No OPDS user authenticated");
    }

    @Transactional
    public ResponseEntity<Map<String, String>> loginUser(UserLoginRequest loginRequest) {
        if (appSettingService.getAppSettings().isOidcForceOnlyMode()) {
            BookLoreUserEntity oidcCheckUser = userRepository.findByUsername(loginRequest.getUsername()).orElse(null);
            if (oidcCheckUser == null || !oidcCheckUser.getPermissions().isPermissionAdmin()) {
                throw ApiError.OIDC_ONLY_MODE.createException();
            }
        }

        String ip = RequestUtils.getCurrentRequest().getRemoteAddr();
        String username = loginRequest.getUsername();
        authRateLimitService.checkLoginRateLimit(ip);
        authRateLimitService.checkLoginRateLimitByUsername(username);

        BookLoreUserEntity user = userRepository.findByUsername(username).orElse(null);

        if (user == null) {
            // Constant-time dummy BCrypt check prevents timing-based user enumeration:
            // without this, unknown-user responses are ~3x faster than wrong-password responses.
            passwordEncoder.matches(loginRequest.getPassword(), dummyPasswordHash);
            auditService.log(AuditAction.LOGIN_FAILED, "Login failed for unknown user: " + username);
            authRateLimitService.recordFailedLoginAttempt(ip);
            authRateLimitService.recordFailedLoginAttemptByUsername(username);
            throw ApiError.INVALID_CREDENTIALS.createException();
        }

        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPasswordHash())) {
            auditService.log(AuditAction.LOGIN_FAILED, "Login failed for user: " + username);
            authRateLimitService.recordFailedLoginAttempt(ip);
            authRateLimitService.recordFailedLoginAttemptByUsername(username);
            throw ApiError.INVALID_CREDENTIALS.createException();
        }

        authRateLimitService.resetLoginAttempts(ip);
        authRateLimitService.resetLoginAttemptsByUsername(username);
        return loginUser(user);
    }

    @Transactional
    public ResponseEntity<Map<String, String>> loginRemote(String name, String username, String email, String groups) {
        if (username == null || username.isEmpty()) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("Remote-User header is missing");
        }

        Optional<BookLoreUserEntity> user = userRepository.findByUsername(username);
        if (user.isEmpty() && appProperties.getRemoteAuth().isCreateNewUsers()) {
            user = Optional.of(userProvisioningService.provisionRemoteUserFromHeaders(name, username, email, groups));
        }

        if (user.isEmpty()) {
            throw ApiError.INTERNAL_SERVER_ERROR.createException("User not found and remote user creation is disabled");
        }

        return loginUser(user.get());
    }

    public ResponseEntity<Map<String, String>> loginUser(BookLoreUserEntity user) {
        return loginUser(user, null);
    }

    public ResponseEntity<Map<String, String>> loginUser(BookLoreUserEntity user, Long customRefreshTokenExpirationMs) {
        String accessToken = jwtUtils.generateAccessToken(user);
        String refreshToken = jwtUtils.generateRefreshToken(user);

        long expirationMs = customRefreshTokenExpirationMs != null ? customRefreshTokenExpirationMs : jwtUtils.getRefreshTokenExpirationMs();

        RefreshTokenEntity refreshTokenEntity = RefreshTokenEntity.builder()
                .user(user)
                .token(refreshToken)
                .expiryDate(Instant.now().plusMillis(expirationMs))
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshTokenEntity);
        auditService.log(AuditAction.LOGIN_SUCCESS, "User", user.getId(), "Login successful for user: " + user.getUsername());

        return ResponseEntity.ok(Map.of(
                "accessToken", accessToken,
                "refreshToken", refreshTokenEntity.getToken(),
                "isDefaultPassword", String.valueOf(user.isDefaultPassword())
        ));
    }

    @Transactional
    public ResponseEntity<Map<String, String>> refreshToken(String token) {
        String ip = RequestUtils.getCurrentRequest().getRemoteAddr();
        authRateLimitService.checkRefreshRateLimit(ip);

        RefreshTokenEntity storedToken = refreshTokenRepository.findByToken(token).orElseThrow(() -> {
            authRateLimitService.recordFailedRefreshAttempt(ip);
            return ApiError.INVALID_CREDENTIALS.createException("Refresh token not found");
        });

        if (storedToken.isRevoked() || storedToken.getExpiryDate().isBefore(Instant.now()) || !jwtUtils.validateToken(token)) {
            authRateLimitService.recordFailedRefreshAttempt(ip);
            throw ApiError.INVALID_CREDENTIALS.createException("Invalid or expired refresh token");
        }

        BookLoreUserEntity user = storedToken.getUser();

        storedToken.setRevoked(true);
        storedToken.setRevocationDate(Instant.now());
        refreshTokenRepository.save(storedToken);

        String newRefreshToken = jwtUtils.generateRefreshToken(user);
        RefreshTokenEntity newRefreshTokenEntity = RefreshTokenEntity.builder()
                .user(user)
                .token(newRefreshToken)
                .expiryDate(Instant.now().plusMillis(jwtUtils.getRefreshTokenExpirationMs()))
                .revoked(false)
                .build();

        refreshTokenRepository.save(newRefreshTokenEntity);

        authRateLimitService.resetRefreshAttempts(ip);

        return ResponseEntity.ok(Map.of(
                "accessToken", jwtUtils.generateAccessToken(user),
                "refreshToken", newRefreshToken
        ));
    }
}