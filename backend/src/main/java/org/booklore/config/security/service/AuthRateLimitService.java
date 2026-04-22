package org.booklore.config.security.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.ApiError;
import org.booklore.model.enums.AuditAction;
import org.booklore.service.audit.AuditService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class AuthRateLimitService {

    private static final int MAX_ATTEMPTS = 5;

    private final Cache<String, AtomicInteger> attemptCache;
    private final AuditService auditService;

    public AuthRateLimitService(AuditService auditService) {
        this.auditService = auditService;
        this.attemptCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(Duration.ofMinutes(15))
                .build();
    }

    // --- Login rate limiting ---

    public void checkLoginRateLimit(String ip) {
        checkRateLimit("login:ip:" + ip, AuditAction.LOGIN_RATE_LIMITED, "Login rate limited for IP: " + ip);
    }

    public void checkLoginRateLimitByUsername(String username) {
        String normalizedUsername = normalizeUsername(username);
        checkRateLimit("login:user:" + normalizedUsername, AuditAction.LOGIN_RATE_LIMITED, "Login rate limited for username: " + normalizedUsername);
    }

    public void recordFailedLoginAttempt(String ip) {
        recordFailedAttempt("login:ip:" + ip);
    }

    public void recordFailedLoginAttemptByUsername(String username) {
        String normalizedUsername = normalizeUsername(username);
        recordFailedAttempt("login:user:" + normalizedUsername);
    }

    public void resetLoginAttempts(String ip) {
        resetAttempts("login:ip:" + ip);
    }

    public void resetLoginAttemptsByUsername(String username) {
        String normalizedUsername = normalizeUsername(username);
        resetAttempts("login:user:" + normalizedUsername);
    }

    // --- Refresh token rate limiting ---

    public void checkRefreshRateLimit(String ip) {
        checkRateLimit("refresh:" + ip, AuditAction.REFRESH_RATE_LIMITED, "Refresh rate limited for IP: " + ip);
    }

    public void recordFailedRefreshAttempt(String ip) {
        recordFailedAttempt("refresh:" + ip);
    }

    public void resetRefreshAttempts(String ip) {
        resetAttempts("refresh:" + ip);
    }

    // --- Shared internals ---

    private void checkRateLimit(String key, AuditAction action, String message) {
        AtomicInteger attempts = attemptCache.getIfPresent(key);
        if (attempts != null && attempts.get() >= MAX_ATTEMPTS) {
            auditService.log(action, message);
            throw ApiError.RATE_LIMITED.createException();
        }
    }

    private void recordFailedAttempt(String key) {
        attemptCache.get(key, k -> new AtomicInteger(0)).incrementAndGet();
    }

    private void resetAttempts(String key) {
        attemptCache.invalidate(key);
    }

    private String normalizeUsername(String username) {
        return username != null ? username.trim().toLowerCase() : "";
    }
}
