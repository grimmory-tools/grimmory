package org.booklore.repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Modifying;

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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("DELETE FROM OidcSessionEntity s WHERE s.revoked = true AND s.createdAt < :cutoff")
    void deleteByRevokedTrueAndCreatedAtBefore(@Param("cutoff") Instant cutoff);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("DELETE FROM OidcSessionEntity s WHERE s.createdAt < :cutoff")
    void deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);
}
