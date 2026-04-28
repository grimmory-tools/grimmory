package org.booklore.repository;

import org.booklore.model.entity.AcquisitionClientEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AcquisitionClientRepository extends JpaRepository<AcquisitionClientEntity, Long> {

    List<AcquisitionClientEntity> findByEnabledTrue();
}
