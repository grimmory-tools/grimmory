package org.booklore.repository;

import jakarta.persistence.LockModeType;
import org.booklore.model.entity.BulkMetadataJournalEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BulkMetadataJournalRepository extends JpaRepository<BulkMetadataJournalEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT j FROM BulkMetadataJournalEntity j WHERE j.operationId = :operationId")
    Optional<BulkMetadataJournalEntity> findForUpdate(@Param("operationId") String operationId);

    Page<BulkMetadataJournalEntity> findByJobIdOrderByOperationId(String jobId, Pageable pageable);
}
