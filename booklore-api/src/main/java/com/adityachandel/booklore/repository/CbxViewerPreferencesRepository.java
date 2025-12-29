package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.CbxViewerPreferencesEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CbxViewerPreferencesRepository extends JpaRepository<CbxViewerPreferencesEntity, Long> {

    Optional<CbxViewerPreferencesEntity> findByBookIdAndUserId(long bookId, Long id);
}
