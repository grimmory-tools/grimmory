package org.booklore.repository;

import org.booklore.model.entity.AnnotationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AnnotationRepository extends JpaRepository<AnnotationEntity, Long> {

    Optional<AnnotationEntity> findByIdAndUserId(Long id, Long userId);

    @Query("SELECT a FROM AnnotationEntity a WHERE a.bookId = :bookId AND a.userId = :userId ORDER BY a.createdAt DESC")
    List<AnnotationEntity> findByBookIdAndUserIdOrderByCreatedAtDesc(
            @Param("bookId") Long bookId,
            @Param("userId") Long userId
    );

    boolean existsByCfiAndBookIdAndUserId(String cfi, Long bookId, Long userId);

    @Query("SELECT COUNT(a) > 0 FROM AnnotationEntity a WHERE a.cfi = :cfi AND a.bookId = :bookId AND a.userId = :userId AND a.id != :excludeId")
    boolean existsByCfiAndBookIdAndUserIdExcludeId(
            @Param("cfi") String cfi,
            @Param("bookId") Long bookId,
            @Param("userId") Long userId,
            @Param("excludeId") Long excludeId
    );

    long countByBookIdAndUserId(Long bookId, Long userId);

    void deleteByBookIdAndUserId(Long bookId, Long userId);

    @Query("SELECT a FROM AnnotationEntity a JOIN FETCH a.book b JOIN FETCH b.metadata WHERE a.userId = :userId ORDER BY a.createdAt DESC")
    List<AnnotationEntity> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);
}
