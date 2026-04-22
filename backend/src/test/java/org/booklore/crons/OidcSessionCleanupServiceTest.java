package org.booklore.crons;

import org.booklore.repository.OidcSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OidcSessionCleanupServiceTest {

    @Mock
    private OidcSessionRepository oidcSessionRepository;

    @InjectMocks
    private OidcSessionCleanupService oidcSessionCleanupService;

    @Test
    void cleanupOidcSessions_callsBothCleanupOperations() {
        oidcSessionCleanupService.cleanupOidcSessions();

        ArgumentCaptor<Instant> revokedCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(oidcSessionRepository).deleteByRevokedTrueAndCreatedAtBefore(revokedCaptor.capture());

        ArgumentCaptor<Instant> maxAgeCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(oidcSessionRepository).deleteByCreatedAtBefore(maxAgeCaptor.capture());
    }

    @Test
    void cleanupOidcSessions_revokedCutoffIsApproximately7DaysAgo() {
        oidcSessionCleanupService.cleanupOidcSessions();

        ArgumentCaptor<Instant> revokedCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(oidcSessionRepository).deleteByRevokedTrueAndCreatedAtBefore(revokedCaptor.capture());

        Instant expectedRevoked = Instant.now().minus(7, ChronoUnit.DAYS);
        assertThat(revokedCaptor.getValue()).isBetween(expectedRevoked.minusSeconds(2), expectedRevoked.plusSeconds(2));
    }

    @Test
    void cleanupOidcSessions_maxAgeCutoffIsApproximately30DaysAgo() {
        oidcSessionCleanupService.cleanupOidcSessions();

        ArgumentCaptor<Instant> maxAgeCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(oidcSessionRepository).deleteByCreatedAtBefore(maxAgeCaptor.capture());

        Instant expectedMaxAge = Instant.now().minus(30, ChronoUnit.DAYS);
        assertThat(maxAgeCaptor.getValue()).isBetween(expectedMaxAge.minusSeconds(2), expectedMaxAge.plusSeconds(2));
    }

    @Test
    void cleanupOidcSessions_revokedCleanupCalledBeforeMaxAgeCleanup() {
        oidcSessionCleanupService.cleanupOidcSessions();

        InOrder inOrder = inOrder(oidcSessionRepository);
        inOrder.verify(oidcSessionRepository).deleteByRevokedTrueAndCreatedAtBefore(org.mockito.ArgumentMatchers.any(Instant.class));
        inOrder.verify(oidcSessionRepository).deleteByCreatedAtBefore(org.mockito.ArgumentMatchers.any(Instant.class));
    }
}
