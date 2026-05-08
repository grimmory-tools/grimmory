package org.booklore.repository;

import org.booklore.model.dto.BookCompletionHeatmapDto;
import org.booklore.model.dto.CompletionTimelineDto;
import org.booklore.model.dto.ProgressPercentDto;
import org.booklore.model.dto.RatingDistributionDto;
import org.booklore.model.dto.StatusDistributionDto;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.enums.ReadStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface UserBookProgressRepository extends JpaRepository<UserBookProgressEntity, Long> {

    Optional<UserBookProgressEntity> findByUserIdAndBookId(Long userId, Long bookId);

    @EntityGraph(attributePaths = {"book", "book.bookFiles", "book.library", "book.libraryPath"})
    @Query("""
        SELECT DISTINCT ubp FROM UserBookProgressEntity ubp
        WHERE ubp.user.id = :userId
          AND ubp.book.id = :bookId
    """)
    Optional<UserBookProgressEntity> findByUserIdAndBookIdForKoboSync(
            @Param("userId") Long userId,
            @Param("bookId") Long bookId
    );

    List<UserBookProgressEntity> findByUserIdAndBookIdIn(Long userId, Set<Long> bookIds);

    @EntityGraph(attributePaths = {"book", "book.bookFiles", "book.library", "book.libraryPath"})
    @Query("""
        SELECT DISTINCT ubp FROM UserBookProgressEntity ubp
        WHERE ubp.user.id = :userId
          AND ubp.book.id IN (
              SELECT ksb.bookId FROM KoboSnapshotBookEntity ksb
              WHERE ksb.snapshot.id = :snapshotId
          )
          AND (
              (ubp.readStatusModifiedTime IS NOT NULL AND (
                  ubp.koboStatusSentTime IS NULL
                  OR ubp.readStatusModifiedTime > ubp.koboStatusSentTime
              ))
              OR
              (ubp.koboProgressReceivedTime IS NOT NULL AND (
                  ubp.koboProgressSentTime IS NULL
                  OR ubp.koboProgressReceivedTime > ubp.koboProgressSentTime
              ))
              OR
              (ubp.epubProgressPercent IS NOT NULL
                  AND ubp.epubProgress IS NOT NULL
                  AND (ubp.koboProgressSentTime IS NULL OR ubp.lastReadTime > ubp.koboProgressSentTime))
          )
    """)
    List<UserBookProgressEntity> findAllBooksNeedingKoboSync(
            @Param("userId") Long userId,
            @Param("snapshotId") String snapshotId
    );

    @Query("""
            SELECT
                YEAR(COALESCE(ubp.dateFinished, ubp.readStatusModifiedTime, ubp.lastReadTime)) as year,
                MONTH(COALESCE(ubp.dateFinished, ubp.readStatusModifiedTime, ubp.lastReadTime)) as month,
                ubp.readStatus as readStatus,
                COUNT(ubp) as bookCount
            FROM UserBookProgressEntity ubp
            WHERE ubp.user.id = :userId
            AND ubp.readStatus IS NOT NULL
            AND ubp.readStatus NOT IN (org.booklore.model.enums.ReadStatus.UNSET, org.booklore.model.enums.ReadStatus.UNREAD)
            AND COALESCE(ubp.dateFinished, ubp.readStatusModifiedTime, ubp.lastReadTime) IS NOT NULL
            AND YEAR(COALESCE(ubp.dateFinished, ubp.readStatusModifiedTime, ubp.lastReadTime)) = :year
            GROUP BY YEAR(COALESCE(ubp.dateFinished, ubp.readStatusModifiedTime, ubp.lastReadTime)),
                     MONTH(COALESCE(ubp.dateFinished, ubp.readStatusModifiedTime, ubp.lastReadTime)),
                     ubp.readStatus
            ORDER BY year DESC, month DESC
            """)
    List<CompletionTimelineDto> findCompletionTimelineByUser(@Param("userId") Long userId, @Param("year") int year);

    @Modifying
    @Transactional
    @Query("""
        UPDATE UserBookProgressEntity ubp
        SET ubp.readStatus = :readStatus,
            ubp.readStatusModifiedTime = :modifiedTime,
            ubp.dateFinished = :dateFinished
        WHERE ubp.user.id = :userId
          AND ubp.book.id IN :bookIds
    """)
    int bulkUpdateReadStatus(
            @Param("userId") Long userId,
            @Param("bookIds") List<Long> bookIds,
            @Param("readStatus") ReadStatus readStatus,
            @Param("modifiedTime") Instant modifiedTime,
            @Param("dateFinished") Instant dateFinished
    );

    @Query("""
        SELECT ubp.book.id FROM UserBookProgressEntity ubp
        WHERE ubp.user.id = :userId
          AND ubp.book.id IN :bookIds
    """)
    Set<Long> findExistingProgressBookIds(@Param("userId") Long userId, @Param("bookIds") Set<Long> bookIds);

    @Modifying
    @Transactional
    @Query("""
        UPDATE UserBookProgressEntity ubp
        SET ubp.readStatus = NULL,
            ubp.readStatusModifiedTime = :modifiedTime,
            ubp.lastReadTime = NULL,
            ubp.dateFinished = NULL,
            ubp.pdfProgress = NULL,
            ubp.pdfProgressPercent = NULL,
            ubp.epubProgress = NULL,
            ubp.epubProgressPercent = NULL,
            ubp.cbxProgress = NULL,
            ubp.cbxProgressPercent = NULL
        WHERE ubp.user.id = :userId
          AND ubp.book.id IN :bookIds
    """)
    int bulkResetBookloreProgress(@Param("userId") Long userId, @Param("bookIds") List<Long> bookIds, @Param("modifiedTime") Instant modifiedTime);

    @Modifying
    @Transactional
    @Query("""
        UPDATE UserBookProgressEntity ubp
        SET ubp.koreaderProgress = NULL,
            ubp.koreaderProgressPercent = NULL,
            ubp.koreaderDeviceId = NULL,
            ubp.koreaderDevice = NULL,
            ubp.koreaderLastSyncTime = NULL
        WHERE ubp.user.id = :userId
          AND ubp.book.id IN :bookIds
    """)
    int bulkResetKoreaderProgress(@Param("userId") Long userId, @Param("bookIds") List<Long> bookIds);

    @Modifying
    @Transactional
    @Query("""
        UPDATE UserBookProgressEntity ubp
        SET ubp.koboProgressPercent = NULL,
            ubp.koboLocation = NULL,
            ubp.koboLocationType = NULL,
            ubp.koboLocationSource = NULL,
            ubp.koboProgressReceivedTime = NULL
        WHERE ubp.user.id = :userId
          AND ubp.book.id IN :bookIds
    """)
    int bulkResetKoboProgress(@Param("userId") Long userId, @Param("bookIds") List<Long> bookIds);

    @Modifying
    @Transactional
    @Query("""
        UPDATE UserBookProgressEntity ubp
        SET ubp.personalRating = :rating
        WHERE ubp.user.id = :userId
          AND ubp.book.id IN :bookIds
    """)
    int bulkUpdatePersonalRating(@Param("userId") Long userId, @Param("bookIds") List<Long> bookIds, @Param("rating") Integer rating);

    @Query("""
            SELECT
                YEAR(ubp.dateFinished) as year,
                MONTH(ubp.dateFinished) as month,
                COUNT(ubp) as count
            FROM UserBookProgressEntity ubp
            WHERE ubp.user.id = :userId
            AND ubp.dateFinished IS NOT NULL
            AND YEAR(ubp.dateFinished) >= :startYear
            AND YEAR(ubp.dateFinished) <= :endYear
            GROUP BY YEAR(ubp.dateFinished), MONTH(ubp.dateFinished)
            ORDER BY year ASC, month ASC
            """)
    List<BookCompletionHeatmapDto> findBookCompletionHeatmap(
            @Param("userId") Long userId,
            @Param("startYear") int startYear,
            @Param("endYear") int endYear);

    @Query("""
            SELECT ubp.personalRating as rating, COUNT(ubp) as count
            FROM UserBookProgressEntity ubp
            WHERE ubp.user.id = :userId
            AND ubp.personalRating IS NOT NULL
            GROUP BY ubp.personalRating
            ORDER BY ubp.personalRating
            """)
    List<RatingDistributionDto> findRatingDistributionByUser(@Param("userId") Long userId);

    @Query("""
            SELECT ubp.readStatus as status, COUNT(ubp) as count
            FROM UserBookProgressEntity ubp
            WHERE ubp.user.id = :userId
            AND ubp.readStatus IS NOT NULL
            AND ubp.readStatus <> org.booklore.model.enums.ReadStatus.UNSET
            GROUP BY ubp.readStatus
            """)
    List<StatusDistributionDto> findStatusDistributionByUser(@Param("userId") Long userId);

    @Query("""
            SELECT ubp.koreaderProgressPercent as koreaderProgressPercent,
                   ubp.koboProgressPercent as koboProgressPercent,
                   ubp.epubProgressPercent as epubProgressPercent,
                   ubp.pdfProgressPercent as pdfProgressPercent,
                   ubp.cbxProgressPercent as cbxProgressPercent
            FROM UserBookProgressEntity ubp
            WHERE ubp.user.id = :userId
            """)
    List<ProgressPercentDto> findAllProgressPercentsByUser(@Param("userId") Long userId);

    /**
     * Returns book IDs for in-progress reading (non-audiobook), ordered by most recently read.
     */
    @Query("""
            SELECT DISTINCT ubp.book.id FROM UserBookProgressEntity ubp
            JOIN ubp.book b
            JOIN b.bookFiles bf
            WHERE ubp.user.id = :userId
              AND ubp.readStatus IN (org.booklore.model.enums.ReadStatus.READING, org.booklore.model.enums.ReadStatus.RE_READING)
              AND (b.deleted IS NULL OR b.deleted = false)
              AND bf.isBookFormat = true
              AND bf.bookType <> org.booklore.model.enums.BookFileType.AUDIOBOOK
              AND b.library.id IN :libraryIds
              AND ubp.lastReadTime IS NOT NULL
            ORDER BY ubp.lastReadTime DESC
            """)
    List<Long> findTopContinueReadingBookIds(
            @Param("userId") Long userId,
            @Param("libraryIds") Collection<Long> libraryIds,
            Pageable pageable);

    /**
     * Returns book IDs for in-progress listening (audiobook), ordered by most recently read.
     */
    @Query("""
            SELECT DISTINCT ubp.book.id FROM UserBookProgressEntity ubp
            JOIN ubp.book b
            JOIN b.bookFiles bf
            WHERE ubp.user.id = :userId
              AND ubp.readStatus IN (org.booklore.model.enums.ReadStatus.READING, org.booklore.model.enums.ReadStatus.RE_READING)
              AND (b.deleted IS NULL OR b.deleted = false)
              AND bf.isBookFormat = true
              AND bf.bookType = org.booklore.model.enums.BookFileType.AUDIOBOOK
              AND b.library.id IN :libraryIds
              AND ubp.lastReadTime IS NOT NULL
            ORDER BY ubp.lastReadTime DESC
            """)
    List<Long> findTopContinueListeningBookIds(
            @Param("userId") Long userId,
            @Param("libraryIds") Collection<Long> libraryIds,
            Pageable pageable);
}
