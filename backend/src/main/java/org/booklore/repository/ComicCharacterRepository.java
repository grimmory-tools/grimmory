package org.booklore.repository;

import org.booklore.model.entity.ComicCharacterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ComicCharacterRepository extends JpaRepository<ComicCharacterEntity, Long> {

    Optional<ComicCharacterEntity> findByName(String name);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM ComicCharacterEntity c WHERE c.id NOT IN (SELECT cc.id FROM ComicMetadataEntity m JOIN m.characters cc)")
    void deleteOrphaned();
}
