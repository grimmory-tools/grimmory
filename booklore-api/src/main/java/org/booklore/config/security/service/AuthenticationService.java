package org.booklore.config.security.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.config.AppProperties;
import org.booklore.config.security.JwtUtils;
import org.booklore.config.security.userdetails.KoreaderUserDetails;
import org.booklore.config.security.userdetails.OpdsUserDetails;
import org.booklore.exception.ApiError;
import org.booklore.mapper.custom.BookLoreUserTransformer;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.request.UserLoginRequest;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.RefreshTokenEntity;
import org.booklore.model.enums.ProvisioningMethod;
import org.booklore.model.enums.UserPermission;
import org.booklore.repository.RefreshTokenRepository;
import org.booklore.repository.UserRepository;
import org.booklore.service.user.DefaultSettingInitializer;
import org.booklore.service.user.UserProvisioningService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.booklore.model.enums.AuditAction;
import org.booklore.service.audit.AuditService;

@Slf4j
@AllArgsConstructor
@Service
public class AuthenticationService {

    private final AppProperties appProperties;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserProvisioningService userProvisioningService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final DefaultSettingInitializer defaultSettingInitializer;
    private final BookLoreUserTransformer bookLoreUserTransformer;
    private final AuditService auditService;

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
        // Handle KoreaderUserDetails principal
        if (principal instanceof KoreaderUserDetails koreaderDetails) {
            Long bookLoreUserId = koreaderDetails.getBookLoreUserId();
            if (bookLoreUserId == null) {
                throw new IllegalStateException("KOReader user is not linked to a BookLore user");
            }
            BookLoreUserEntity userEntity = userRepository.findById(bookLoreUserId)
                    .orElseThrow(() -> new IllegalStateException("BookLore user not found for KOReader user"));
            BookLoreUser user = bookLoreUserTransformer.toDTO(userEntity);
            if (user.getId() != null && user.getId() != -1L) {
                defaultSettingInitializer.ensureDefaultSettings(user);
            }
            return user;
        }
        throw new IllegalStateException("Authenticated principal is not of type BookLoreUser or KoreaderUserDetails");
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

    public ResponseEntity<Map<String, String>> loginUser(UserLoginRequest loginRequest) {
        BookLoreUserEntity user = userRepository.findByUsername(loginRequest.getUsername()).orElseThrow(() -> {
            auditService.log(AuditAction.LOGIN_FAILED, "Login failed for unknown user: " + loginRequest.getUsername());
            return ApiError.USER_NOT_FOUND.createException(loginRequest.getUsername());
        });

        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPasswordHash())) {
            auditService.log(AuditAction.LOGIN_FAILED, "Login failed for user: " + loginRequest.getUsername());
            throw ApiError.INVALID_CREDENTIALS.createException();
        }

        return loginUser(user);
    }

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
        String accessToken = jwtUtils.generateAccessToken(user);
        String refreshToken = jwtUtils.generateRefreshToken(user);

        RefreshTokenEntity refreshTokenEntity = RefreshTokenEntity.builder()
                .user(user)
                .token(refreshToken)
                .expiryDate(Instant.now().plusMillis(jwtUtils.getRefreshTokenExpirationMs()))
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

    public ResponseEntity<Map<String, String>> refreshToken(String token) {
        RefreshTokenEntity storedToken = refreshTokenRepository.findByToken(token).orElseThrow(() -> ApiError.INVALID_CREDENTIALS.createException("Refresh token not found"));

        if (storedToken.isRevoked() || storedToken.getExpiryDate().isBefore(Instant.now()) || !jwtUtils.validateToken(token)) {
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

        return ResponseEntity.ok(Map.of(
                "accessToken", jwtUtils.generateAccessToken(user),
                "refreshToken", newRefreshToken
        ));
    }
}