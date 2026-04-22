package org.booklore.repository;

import org.booklore.model.entity.ComicTeamEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ComicTeamRepository extends JpaRepository<ComicTeamEntity, Long> {

    Optional<ComicTeamEntity> findByName(String name);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM ComicTeamEntity t WHERE t.id NOT IN (SELECT t2.id FROM ComicMetadataEntity m JOIN m.teams t2)")
    void deleteOrphaned();
}
