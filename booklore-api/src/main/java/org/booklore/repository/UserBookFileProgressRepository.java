package org.booklore.repository;

import org.booklore.model.entity.UserBookFileProgressEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserBookFileProgressRepository extends JpaRepository<UserBookFileProgressEntity, Long> {

    Optional<UserBookFileProgressEntity> findByUserIdAndBookFileId(Long userId, Long bookFileId);

    @Query("""
        SELECT ubfp FROM UserBookFileProgressEntity ubfp
        WHERE ubfp.user.id = :userId
          AND ubfp.bookFile.book.id = :bookId
    """)
    List<UserBookFileProgressEntity> findByUserIdAndBookFileBookId(
            @Param("userId") Long userId,
            @Param("bookId") Long bookId
    );

    @Query("""
        SELECT ubfp FROM UserBookFileProgressEntity ubfp
        WHERE ubfp.user.id = :userId
          AND ubfp.bookFile.book.id = :bookId
        ORDER BY ubfp.lastReadTime DESC
        LIMIT 1
    """)
    Optional<UserBookFileProgressEntity> findMostRecentByUserIdAndBookId(
            @Param("userId") Long userId,
            @Param("bookId") Long bookId
    );

    @Query("""
        SELECT ubfp FROM UserBookFileProgressEntity ubfp
        WHERE ubfp.user.id = :userId
          AND ubfp.bookFile.book.id = :bookId
          AND ubfp.bookFile.bookType = org.booklore.model.enums.BookFileType.AUDIOBOOK
        ORDER BY ubfp.lastReadTime DESC
        LIMIT 1
    """)
    Optional<UserBookFileProgressEntity> findMostRecentAudiobookProgressByUserIdAndBookId(
            @Param("userId") Long userId,
            @Param("bookId") Long bookId
    );

    @EntityGraph(attributePaths = {"bookFile", "bookFile.book"})
    @Query("""
        SELECT ubfp FROM UserBookFileProgressEntity ubfp
        WHERE ubfp.user.id = :userId
          AND ubfp.bookFile.book.id IN :bookIds
    """)
    List<UserBookFileProgressEntity> findByUserIdAndBookFileBookIdIn(
            @Param("userId") Long userId,
            @Param("bookIds") Iterable<Long> bookIds
    );

    @Modifying
    @Transactional
    @Query("""
        DELETE FROM UserBookFileProgressEntity ubfp
        WHERE ubfp.user.id = :userId
          AND ubfp.bookFile.book.id IN :bookIds
    """)
    int deleteByUserIdAndBookIds(@Param("userId") Long userId, @Param("bookIds") Iterable<Long> bookIds);
}
