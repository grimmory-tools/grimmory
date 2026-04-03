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
public class LoginRateLimitService {

    private static final int MAX_ATTEMPTS = 5;

    private final Cache<String, AtomicInteger> attemptCache;
    private final AuditService auditService;

    public LoginRateLimitService(AuditService auditService) {
        this.auditService = auditService;
        this.attemptCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(Duration.ofMinutes(15))
                .build();
    }

    public void checkRateLimit(String ip) {
        AtomicInteger attempts = attemptCache.getIfPresent(ip);
        if (attempts != null && attempts.get() >= MAX_ATTEMPTS) {
            auditService.log(AuditAction.LOGIN_RATE_LIMITED, "Login rate limited for IP: " + ip);
            throw ApiError.RATE_LIMITED.createException();
        }
    }

    public void recordFailedAttempt(String ip) {
        attemptCache.get(ip, k -> new AtomicInteger(0)).incrementAndGet();
    }

    public void resetAttempts(String ip) {
        attemptCache.invalidate(ip);
    }
}
