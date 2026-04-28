package org.booklore.repository;

import org.booklore.model.entity.AcquisitionIndexerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AcquisitionIndexerRepository extends JpaRepository<AcquisitionIndexerEntity, Long> {

    List<AcquisitionIndexerEntity> findByEnabledTrueOrderByPriorityAsc();
}
