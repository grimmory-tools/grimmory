package org.booklore.repository;

import org.booklore.model.entity.KoboDeletedBookProgressEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface KoboDeletedBookProgressRepository extends JpaRepository<KoboDeletedBookProgressEntity, Long> {

    @Modifying
    @Transactional
    @Query("DELETE FROM KoboDeletedBookProgressEntity p WHERE p.snapshotId = :snapshotId AND p.userId = :userId")
    void deleteBySnapshotIdAndUserId(@Param("snapshotId") String snapshotId, @Param("userId") Long userId);
}
