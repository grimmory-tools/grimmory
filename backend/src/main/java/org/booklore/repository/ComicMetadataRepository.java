package org.booklore.repository;

import org.booklore.model.entity.ComicMetadataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ComicMetadataRepository extends JpaRepository<ComicMetadataEntity, Long> {

    @Query("SELECT c FROM ComicMetadataEntity c WHERE c.bookId IN :bookIds")
    List<ComicMetadataEntity> findAllByBookIds(@Param("bookIds") List<Long> bookIds);

    List<ComicMetadataEntity> findAllByStoryArcIgnoreCase(String storyArc);

    List<ComicMetadataEntity> findAllByVolumeNameIgnoreCase(String volumeName);

    @Query("SELECT DISTINCT c FROM ComicMetadataEntity c JOIN c.creatorMappings m WHERE m.creator.name LIKE %:name%")
    List<ComicMetadataEntity> findAllByCreatorName(@Param("name") String name);
}
