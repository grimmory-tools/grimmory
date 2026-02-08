package org.booklore.repository;

import org.booklore.model.entity.ComicCreatorEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ComicCreatorRepository extends JpaRepository<ComicCreatorEntity, Long> {

    Optional<ComicCreatorEntity> findByName(String name);
}
