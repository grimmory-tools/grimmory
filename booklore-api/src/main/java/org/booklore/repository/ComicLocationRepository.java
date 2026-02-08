package org.booklore.repository;

import org.booklore.model.entity.ComicLocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ComicLocationRepository extends JpaRepository<ComicLocationEntity, Long> {

    Optional<ComicLocationEntity> findByName(String name);
}
