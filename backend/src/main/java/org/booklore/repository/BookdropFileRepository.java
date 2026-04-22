package org.booklore.repository;


import org.booklore.model.entity.BookdropFileEntity;
import org.booklore.model.entity.BookdropFileEntity.Status;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookdropFileRepository extends JpaRepository<BookdropFileEntity, Long> {

    Optional<BookdropFileEntity> findByFilePath(String filePath);

    Page<BookdropFileEntity> findAllByStatus(Status status, Pageable pageable);

    long countByStatus(Status status);

    @Transactional
    @Modifying
    @Query("DELETE FROM BookdropFileEntity f WHERE f.filePath LIKE CONCAT(:prefix, '%')")
    int deleteAllByFilePathStartingWith(@Param("prefix") String prefix);

    @Query("SELECT f.id FROM BookdropFileEntity f WHERE f.id NOT IN :excludedIds")
    List<Long> findAllExcludingIdsFlat(@Param("excludedIds") List<Long> excludedIds);

    @Query("SELECT f.id FROM BookdropFileEntity f")
    List<Long> findAllIds();

    @Query("SELECT f.filePath FROM BookdropFileEntity f WHERE f.filePath IN :filePaths")
    List<String> findAllFilePathsIn(@Param("filePaths") List<String> filePaths);
}
