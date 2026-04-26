package org.grimmory.config.security.oidc;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.booklore.exception.ApiError;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

@Service
public class OidcStateService {

    private static final Cache<String, Boolean> STATE_CACHE = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(500)
            .build();

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public String generateState() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        STATE_CACHE.put(state, Boolean.TRUE);
        return state;
    }

    public void validateAndConsume(String state) {
        if (state == null || state.isBlank()) {
            throw ApiError.OIDC_INVALID_STATE.createException();
        }
        Boolean valid = STATE_CACHE.getIfPresent(state);
        if (valid == null) {
            throw ApiError.OIDC_INVALID_STATE.createException();
        }
        STATE_CACHE.invalidate(state);
    }
}
