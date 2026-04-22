package org.booklore.repository;

import org.booklore.model.entity.LibraryEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LibraryRepository extends JpaRepository<LibraryEntity, Long>, JpaSpecificationExecutor<LibraryEntity> {

    List<LibraryEntity> findByIdIn(List<Long> ids);

    @EntityGraph(attributePaths = {"bookEntities"})
    @Query("SELECT l FROM LibraryEntity l WHERE l.id = :id")
    Optional<LibraryEntity> findByIdWithBooks(@Param("id") Long id);

    @EntityGraph(attributePaths = {"libraryPaths"})
    @Query("SELECT l FROM LibraryEntity l WHERE l.id = :id")
    Optional<LibraryEntity> findByIdWithPaths(@Param("id") Long id);

    @EntityGraph(attributePaths = {"libraryPaths"})
    @Query("SELECT l FROM LibraryEntity l")
    List<LibraryEntity> findAllWithPaths();
}
