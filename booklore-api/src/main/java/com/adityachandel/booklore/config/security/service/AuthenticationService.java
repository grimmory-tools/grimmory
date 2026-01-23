package com.adityachandel.booklore.config.security.service;

import com.adityachandel.booklore.config.AppProperties;
import com.adityachandel.booklore.config.security.JwtUtils;
import com.adityachandel.booklore.config.security.userdetails.KoreaderUserDetails;
import com.adityachandel.booklore.config.security.userdetails.OpdsUserDetails;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.custom.BookLoreUserTransformer;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.request.UserLoginRequest;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.model.entity.RefreshTokenEntity;
import com.adityachandel.booklore.model.enums.ProvisioningMethod;
import com.adityachandel.booklore.model.enums.UserPermission;
import com.adityachandel.booklore.repository.RefreshTokenRepository;
import com.adityachandel.booklore.repository.UserRepository;
import com.adityachandel.booklore.service.user.DefaultSettingInitializer;
import com.adityachandel.booklore.service.user.UserProvisioningService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        BookLoreUserEntity user = userRepository.findByUsername(loginRequest.getUsername()).orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(loginRequest.getUsername()));

        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPasswordHash())) {
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