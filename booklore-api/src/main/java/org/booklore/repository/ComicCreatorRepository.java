package org.booklore.repository;

import org.booklore.model.entity.ComicCreatorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ComicCreatorRepository extends JpaRepository<ComicCreatorEntity, Long> {

    Optional<ComicCreatorEntity> findByName(String name);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM ComicCreatorEntity c WHERE c.id NOT IN (SELECT cm.creator.id FROM ComicCreatorMappingEntity cm)")
    void deleteOrphaned();
}
