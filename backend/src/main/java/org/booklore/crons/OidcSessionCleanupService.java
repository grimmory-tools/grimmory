package org.booklore.crons;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.repository.OidcSessionRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@AllArgsConstructor
public class OidcSessionCleanupService {

    private static final int REVOKED_RETENTION_DAYS = 7;
    private static final int MAX_SESSION_AGE_DAYS = 30;

    private final OidcSessionRepository oidcSessionRepository;

    @Scheduled(fixedDelay = 24, timeUnit = TimeUnit.HOURS)
    @Transactional
    public void cleanupOidcSessions() {
        Instant revokedCutoff = Instant.now().minus(REVOKED_RETENTION_DAYS, ChronoUnit.DAYS);
        oidcSessionRepository.deleteByRevokedTrueAndCreatedAtBefore(revokedCutoff);

        Instant maxAgeCutoff = Instant.now().minus(MAX_SESSION_AGE_DAYS, ChronoUnit.DAYS);
        oidcSessionRepository.deleteByCreatedAtBefore(maxAgeCutoff);

        log.debug("OIDC session cleanup completed");
    }
}
