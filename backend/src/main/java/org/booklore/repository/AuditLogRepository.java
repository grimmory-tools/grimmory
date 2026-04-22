package org.booklore.repository;

import org.booklore.model.entity.AuditLogEntity;
import org.booklore.model.enums.AuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {

    Page<AuditLogEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("""
            SELECT a FROM AuditLogEntity a
            WHERE (:action IS NULL OR a.action = :action)
            AND (:userId IS NULL OR a.userId = :userId)
            AND (:username IS NULL OR a.username = :username)
            AND (:from IS NULL OR a.createdAt >= :from)
            AND (:to IS NULL OR a.createdAt <= :to)
            ORDER BY a.createdAt DESC
            """)
    Page<AuditLogEntity> findFiltered(
            @Param("action") AuditAction action,
            @Param("userId") Long userId,
            @Param("username") String username,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    @Query("SELECT DISTINCT a.username FROM AuditLogEntity a ORDER BY a.username")
    List<String> findDistinctUsernames();
}
