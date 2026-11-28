package org.booklore.repository;

import org.booklore.model.entity.MetadataFetchJobEntity;
import org.booklore.model.enums.MetadataFetchTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Repository
public interface MetadataFetchJobRepository extends JpaRepository<MetadataFetchJobEntity, String> {

    int deleteAllByCompletedAtBefore(Instant cutoff);

    @Modifying
    @Transactional
    @Query("DELETE FROM MetadataFetchJobEntity")
    int deleteAllRecords();

    @Query("SELECT COUNT(m) FROM MetadataFetchJobEntity m")
    long countAll();

    @Query("SELECT DISTINCT t FROM MetadataFetchJobEntity t LEFT JOIN FETCH t.proposals ORDER BY t.startedAt ASC")
    List<MetadataFetchJobEntity> findAllWithProposals();

    List<MetadataFetchJobEntity> findAllByStatus(MetadataFetchTaskStatus status);

    @Modifying
    @Transactional
    @Query("UPDATE MetadataFetchJobEntity t SET t.status = :status, t.statusMessage = :message, t.completedAt = :completedAt, t.completedBooks = :completedBooks WHERE t.taskId = :taskId")
    int updateFailure(String taskId, MetadataFetchTaskStatus status, String message, Instant completedAt, int completedBooks);
}
