package org.booklore.repository;

import org.booklore.model.entity.ComicLocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ComicLocationRepository extends JpaRepository<ComicLocationEntity, Long> {

    Optional<ComicLocationEntity> findByName(String name);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM ComicLocationEntity c WHERE c.id NOT IN (SELECT l.id FROM ComicMetadataEntity m JOIN m.locations l)")
    void deleteOrphaned();
}
