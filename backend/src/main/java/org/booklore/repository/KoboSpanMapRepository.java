package org.booklore.repository;

import org.booklore.model.entity.KoboSpanMapEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KoboSpanMapRepository extends JpaRepository<KoboSpanMapEntity, Long> {

    @Query("SELECT ksm FROM KoboSpanMapEntity ksm WHERE ksm.bookFile.id = :bookFileId")
    Optional<KoboSpanMapEntity> findByBookFileId(@Param("bookFileId") Long bookFileId);
}
