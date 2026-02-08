package org.booklore.repository;

import org.booklore.model.entity.ComicCharacterEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ComicCharacterRepository extends JpaRepository<ComicCharacterEntity, Long> {

    Optional<ComicCharacterEntity> findByName(String name);
}
