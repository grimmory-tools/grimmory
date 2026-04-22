package org.booklore.repository;

import org.booklore.model.entity.ComicCreatorMappingEntity;
import org.booklore.model.enums.ComicCreatorRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComicCreatorMappingRepository extends JpaRepository<ComicCreatorMappingEntity, Long> {

    List<ComicCreatorMappingEntity> findByComicMetadataBookId(Long bookId);

    List<ComicCreatorMappingEntity> findByComicMetadataBookIdAndRole(Long bookId, ComicCreatorRole role);

    void deleteByComicMetadataBookId(Long bookId);
}
