package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.NewPdfViewerPreferencesEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NewPdfViewerPreferencesRepository extends JpaRepository<NewPdfViewerPreferencesEntity, Long> {

    Optional<NewPdfViewerPreferencesEntity> findByBookIdAndUserId(long bookId, Long id);
}
