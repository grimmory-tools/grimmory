package org.grimmory.repository;

import org.grimmory.model.entity.ComicCreatorMappingEntity;
import org.grimmory.model.enums.ComicCreatorRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComicCreatorMappingRepository extends JpaRepository<ComicCreatorMappingEntity, Long> {

    List<ComicCreatorMappingEntity> findByComicMetadataBookId(Long bookId);

    List<ComicCreatorMappingEntity> findByComicMetadataBookIdAndRole(Long bookId, ComicCreatorRole role);

    void deleteByComicMetadataBookId(Long bookId);
}
