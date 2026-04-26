package org.grimmory.repository;

import org.grimmory.model.entity.KoboSpanMapEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface KoboSpanMapRepository extends JpaRepository<KoboSpanMapEntity, Long> {

    @Query("SELECT ksm FROM KoboSpanMapEntity ksm WHERE ksm.bookFile.id = :bookFileId")
    Optional<KoboSpanMapEntity> findByBookFileId(@Param("bookFileId") Long bookFileId);

    @Query("SELECT ksm FROM KoboSpanMapEntity ksm WHERE ksm.bookFile.id IN :bookFileIds")
    List<KoboSpanMapEntity> findByBookFileIdIn(@Param("bookFileIds") Collection<Long> bookFileIds);
}
