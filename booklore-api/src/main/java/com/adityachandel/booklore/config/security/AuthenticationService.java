package com.adityachandel.booklore.config.security;

import com.adityachandel.booklore.config.AppProperties;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.request.UserLoginRequest;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.repository.UserRepository;
import com.adityachandel.booklore.service.user.UserCreatorService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@AllArgsConstructor
@Service
public class AuthenticationService {

    private final AppProperties appProperties;
    private final UserRepository userRepository;
    private final UserCreatorService userCreatorService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    public BookLoreUser getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (BookLoreUser) authentication.getPrincipal();
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
            throw ApiError.BAD_REQUEST.createException("Remote-User header is missing");
        }

        Optional<BookLoreUserEntity> user = userRepository.findByUsername(username);
        if (user.isEmpty() && appProperties.getRemoteAuth().isCreateNewUsers()) {
            user = Optional.of(userCreatorService.createRemoteUser(name, username, email, groups));
        }

        if (user.isEmpty()) {
            throw ApiError.INTERNAL_SERVER_ERROR.createException("User not found and remote user creation is disabled");
        }

        return loginUser(user.get());
    }

    public ResponseEntity<Map<String, String>> loginUser(BookLoreUserEntity user) {
        String accessToken = jwtUtils.generateAccessToken(user);
        String refreshToken = jwtUtils.generateRefreshToken(user);

        return ResponseEntity.ok(Map.of(
                "accessToken", accessToken,
                "refreshToken", refreshToken,
                "isDefaultPassword", String.valueOf(user.isDefaultPassword())
        ));
    }

    public ResponseEntity<Map<String, String>> refreshToken(String refreshToken) {
        if (!jwtUtils.validateToken(refreshToken)) {
            throw ApiError.INVALID_CREDENTIALS.createException();
        }

        String username = jwtUtils.extractUsername(refreshToken);
        BookLoreUserEntity user = userRepository.findByUsername(username).orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(username));

        String newAccessToken = jwtUtils.generateAccessToken(user);
        String newRefreshToken = jwtUtils.generateRefreshToken(user);

        return ResponseEntity.ok(Map.of("accessToken", newAccessToken, "refreshToken", newRefreshToken));
    }
}