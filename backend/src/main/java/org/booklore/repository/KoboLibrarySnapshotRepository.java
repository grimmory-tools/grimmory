package org.booklore.repository;

import org.booklore.model.entity.KoboLibrarySnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KoboLibrarySnapshotRepository extends JpaRepository<KoboLibrarySnapshotEntity, String> {

    Optional<KoboLibrarySnapshotEntity> findByIdAndUserId(String id, Long userId);

    Optional<KoboLibrarySnapshotEntity> findTopByUserIdOrderByCreatedDateDesc(Long userId);

}
