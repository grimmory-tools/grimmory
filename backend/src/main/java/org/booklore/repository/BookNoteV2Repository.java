package org.booklore.repository;

import org.booklore.model.entity.BookNoteV2Entity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookNoteV2Repository extends JpaRepository<BookNoteV2Entity, Long> {

    Optional<BookNoteV2Entity> findByIdAndUserId(Long id, Long userId);

    @Query("SELECT n FROM BookNoteV2Entity n WHERE n.bookId = :bookId AND n.userId = :userId ORDER BY n.createdAt DESC")
    List<BookNoteV2Entity> findByBookIdAndUserIdOrderByCreatedAtDesc(
            @Param("bookId") Long bookId,
            @Param("userId") Long userId
    );

    boolean existsByCfiAndBookIdAndUserId(String cfi, Long bookId, Long userId);

    @Query("SELECT COUNT(n) > 0 FROM BookNoteV2Entity n WHERE n.cfi = :cfi AND n.bookId = :bookId AND n.userId = :userId AND n.id != :excludeId")
    boolean existsByCfiAndBookIdAndUserIdExcludeId(
            @Param("cfi") String cfi,
            @Param("bookId") Long bookId,
            @Param("userId") Long userId,
            @Param("excludeId") Long excludeId
    );

    long countByBookIdAndUserId(Long bookId, Long userId);

    void deleteByBookIdAndUserId(Long bookId, Long userId);

    @Query("SELECT n FROM BookNoteV2Entity n JOIN FETCH n.book b JOIN FETCH b.metadata WHERE n.userId = :userId ORDER BY n.createdAt DESC")
    List<BookNoteV2Entity> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);
}
