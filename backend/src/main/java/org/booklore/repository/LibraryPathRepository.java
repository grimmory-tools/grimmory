package org.booklore.repository;

import org.booklore.model.entity.LibraryPathEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LibraryPathRepository extends JpaRepository<LibraryPathEntity, Long> {
    Optional<LibraryPathEntity> findByLibraryIdAndPath(Long libraryId, String path);

    @Query("SELECT lp FROM LibraryPathEntity lp JOIN FETCH lp.library")
    List<LibraryPathEntity> findAllWithLibrary();
}
