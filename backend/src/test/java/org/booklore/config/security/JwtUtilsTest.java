package org.booklore.config.security;

import io.jsonwebtoken.Claims;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.service.security.JwtSecretService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtUtilsTest {

    @Mock
    private JwtSecretService jwtSecretService;

    @InjectMocks
    private JwtUtils jwtUtils;

    private BookLoreUserEntity user;
    private final String SECRET = "mySuperSecretKeyThatNeedsToBeVeryLongForHS256Algorithm1234567890";

    @BeforeEach
    void setUp() {
        user = new BookLoreUserEntity();
        user.setId(123L);
        user.setUsername("testuser");
        user.setDefaultPassword(false);
    }

    @Test
    void testGenerateAndValidateAccessToken() {
        when(jwtSecretService.getSecret()).thenReturn(SECRET);

        String token = jwtUtils.generateAccessToken(user);
        assertThat(token).isNotBlank();

        boolean isValid = jwtUtils.validateToken(token);
        assertThat(isValid).isTrue();

        String username = jwtUtils.extractUsername(token);
        assertThat(username).isEqualTo("testuser");

        Long userId = jwtUtils.extractUserId(token);
        assertThat(userId).isEqualTo(123L);
    }

    @Test
    void testGenerateAndValidateRefreshToken() {
        when(jwtSecretService.getSecret()).thenReturn(SECRET);

        String token = jwtUtils.generateRefreshToken(user);
        assertThat(token).isNotBlank();

        boolean isValid = jwtUtils.validateToken(token);
        assertThat(isValid).isTrue();

        Claims claims = jwtUtils.extractClaims(token);
        assertThat(claims.getSubject()).isEqualTo("testuser");
        assertThat(claims.get("userId", Long.class)).isEqualTo(123L);
        assertThat(claims.get("isDefaultPassword", Boolean.class)).isFalse();
    }
}
