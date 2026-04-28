package org.booklore.repository;

import org.booklore.model.entity.AcquisitionJobHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AcquisitionJobHistoryRepository extends JpaRepository<AcquisitionJobHistoryEntity, Long> {

    List<AcquisitionJobHistoryEntity> findByWantedBookIdOrderByAttemptedAtDesc(Long wantedBookId);
}
