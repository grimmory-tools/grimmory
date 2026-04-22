package org.booklore.repository;

import org.booklore.model.entity.OidcSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface OidcSessionRepository extends JpaRepository<OidcSessionEntity, Long> {

    Optional<OidcSessionEntity> findFirstByUserIdAndRevokedFalseOrderByCreatedAtDesc(Long userId);

    List<OidcSessionEntity> findByOidcSessionIdAndRevokedFalse(String oidcSessionId);

    List<OidcSessionEntity> findByOidcSubjectAndOidcIssuerAndRevokedFalse(String oidcSubject, String oidcIssuer);

    List<OidcSessionEntity> findByUserIdAndRevokedFalse(Long userId);

    void deleteByRevokedTrueAndCreatedAtBefore(Instant cutoff);

    void deleteByCreatedAtBefore(Instant cutoff);
}
