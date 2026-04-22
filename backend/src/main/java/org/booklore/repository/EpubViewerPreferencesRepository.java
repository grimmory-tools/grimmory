package org.booklore.repository;

import org.booklore.model.entity.EpubViewerPreferencesEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EpubViewerPreferencesRepository extends JpaRepository<EpubViewerPreferencesEntity, Long> {

    Optional<EpubViewerPreferencesEntity> findByBookIdAndUserId(Long bookId, Long userId);

}
