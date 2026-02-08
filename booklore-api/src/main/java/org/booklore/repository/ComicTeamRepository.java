package org.booklore.repository;

import org.booklore.model.entity.ComicTeamEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ComicTeamRepository extends JpaRepository<ComicTeamEntity, Long> {

    Optional<ComicTeamEntity> findByName(String name);
}
