package org.booklore.repository;

import org.booklore.model.entity.BookMarkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookMarkRepository extends JpaRepository<BookMarkEntity, Long> {

    Optional<BookMarkEntity> findByIdAndUserId(Long id, Long userId);

    @Query("SELECT b FROM BookMarkEntity b WHERE b.bookId = :bookId AND b.userId = :userId ORDER BY b.priority ASC, b.createdAt DESC")
    List<BookMarkEntity> findByBookIdAndUserIdOrderByPriorityAscCreatedAtDesc(@Param("bookId") Long bookId, @Param("userId") Long userId);

    boolean existsByCfiAndBookIdAndUserId(String cfi, Long bookId, Long userId);

    @Query("SELECT COUNT(b) > 0 FROM BookMarkEntity b WHERE b.cfi = :cfi AND b.bookId = :bookId AND b.userId = :userId AND b.id != :excludeId")
    boolean existsByCfiAndBookIdAndUserIdExcludeId(@Param("cfi") String cfi, @Param("bookId") Long bookId, @Param("userId") Long userId, @Param("excludeId") Long excludeId);

    // Audiobook bookmark duplicate check (within 5 seconds of position is considered duplicate)
    @Query("SELECT COUNT(b) > 0 FROM BookMarkEntity b WHERE b.bookId = :bookId AND b.userId = :userId " +
           "AND b.positionMs IS NOT NULL " +
           "AND ABS(b.positionMs - :positionMs) < 5000 " +
           "AND ((:trackIndex IS NULL AND b.trackIndex IS NULL) OR b.trackIndex = :trackIndex)")
    boolean existsByPositionMsNearAndBookIdAndUserId(@Param("positionMs") Long positionMs, @Param("trackIndex") Integer trackIndex, @Param("bookId") Long bookId, @Param("userId") Long userId);

    // New: count bookmarks per book
    long countByBookIdAndUserId(Long bookId, Long userId);

    @Query("SELECT b FROM BookMarkEntity b JOIN FETCH b.book bk JOIN FETCH bk.metadata WHERE b.userId = :userId ORDER BY b.createdAt DESC")
    List<BookMarkEntity> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);
}
