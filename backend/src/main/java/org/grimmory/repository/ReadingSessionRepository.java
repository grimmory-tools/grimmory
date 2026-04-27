package org.grimmory.repository;

import org.grimmory.model.dto.*;

import org.grimmory.model.entity.ReadingSessionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ReadingSessionRepository extends JpaRepository<ReadingSessionEntity, Long> {

    @Query("""
            SELECT rs.startTime
            FROM ReadingSessionEntity rs
            WHERE rs.user.id = :userId
            AND rs.startTime >= :periodStart AND rs.startTime < :periodEnd
            ORDER BY rs.startTime
            """)
    List<Instant> findSessionStartTimesByUserAndPeriod(
            @Param("userId") Long userId,
            @Param("periodStart") Instant periodStart,
            @Param("periodEnd") Instant periodEnd);

    @Query("""
            SELECT
                    b.id as bookId,
                    coalesce(b.metadata.title,
                            (SELECT bf.fileName FROM BookFileEntity bf WHERE bf.book.id = b.id ORDER BY bf.id ASC LIMIT 1),
                            'Unknown Book') as bookTitle,
                    rs.bookType as bookFileType,
                    rs.startTime as startDate,
                    rs.endTime as endDate,
                    1L as totalSessions,
                    rs.durationSeconds as totalDurationSeconds
            FROM ReadingSessionEntity rs
            JOIN rs.book b
            WHERE rs.user.id = :userId
            AND rs.startTime >= :startOfWeek AND rs.startTime < :endOfWeek
            ORDER BY rs.startTime
            """)
    List<ReadingSessionTimelineDto> findSessionTimelineByUserAndWeek(
            @Param("userId") Long userId,
            @Param("startOfWeek") Instant startOfWeek,
            @Param("endOfWeek") Instant endOfWeek);

    @Query("""
            SELECT
                cast(rs.createdAt as LocalDate) as date,
                avg(rs.progressDelta / (rs.durationSeconds / 60.0)) as avgProgressPerMinute,
                count(rs) as totalSessions
            FROM ReadingSessionEntity rs
            WHERE rs.user.id = :userId
            AND rs.durationSeconds > 0
            AND rs.progressDelta > 0
            AND rs.createdAt >= :periodStart AND rs.createdAt < :periodEnd
            GROUP BY cast(rs.createdAt as LocalDate)
            ORDER BY date
            """)
    List<ReadingSpeedDto> findReadingSpeedByUserAndYear(
            @Param("userId") Long userId,
            @Param("periodStart") java.time.LocalDateTime periodStart,
            @Param("periodEnd") java.time.LocalDateTime periodEnd);

    @Query("""
            SELECT rs.startTime as startTime,
                   coalesce(rs.durationSeconds, 0) as durationSeconds
            FROM ReadingSessionEntity rs
            WHERE rs.user.id = :userId
            AND (:periodStart IS NULL OR rs.startTime >= :periodStart)
            AND (:periodEnd IS NULL OR rs.startTime < :periodEnd)
            ORDER BY rs.startTime
            """)
    List<SessionTimestampDto> findSessionTimestampsByUser(
            @Param("userId") Long userId,
            @Param("periodStart") Instant periodStart,
            @Param("periodEnd") Instant periodEnd);

    @Query("""
            SELECT rs.startTime as startTime,
                   coalesce(rs.durationSeconds, 0) as durationSeconds
            FROM ReadingSessionEntity rs
            WHERE rs.user.id = :userId
            AND rs.startTime >= :periodStart AND rs.startTime < :periodEnd
            ORDER BY rs.startTime DESC
            """)
    List<SessionTimestampDto> findSessionTimestampsByUserInPeriod(
            @Param("userId") Long userId,
            @Param("periodStart") Instant periodStart,
            @Param("periodEnd") Instant periodEnd,
            Pageable pageable);

    @Query("""
            SELECT
                c.name as genre,
                count(distinct b.id) as bookCount,
                count(rs) as totalSessions,
                sum(rs.durationSeconds) as totalDurationSeconds
            FROM ReadingSessionEntity rs
            JOIN rs.book b
            JOIN b.metadata.categories c
            WHERE rs.user.id = :userId
            GROUP BY c.name
            ORDER BY totalSessions DESC
            """)
    List<GenreStatisticsDto> findGenreStatisticsByUser(@Param("userId") Long userId);

    @Query(value = """
            SELECT rs
            FROM ReadingSessionEntity rs
            JOIN FETCH rs.book b
            LEFT JOIN FETCH b.metadata
            WHERE rs.user.id = :userId
            AND b.id = :bookId
            ORDER BY rs.startTime DESC
            """,
            countQuery = """
            SELECT COUNT(rs)
            FROM ReadingSessionEntity rs
            WHERE rs.user.id = :userId
            AND rs.book.id = :bookId
            """)
    Page<ReadingSessionEntity> findByUserIdAndBookId(
            @Param("userId") Long userId,
            @Param("bookId") Long bookId,
            Pageable pageable);

    @Query("""
            SELECT
                b.id as bookId,
                coalesce(b.metadata.title, 'Unknown Book') as bookTitle,
                b.metadata.pageCount as pageCount,
                ubp.personalRating as personalRating,
                coalesce(ubp.dateFinished, ubp.readStatusModifiedTime, ubp.lastReadTime) as dateFinished,
                rs.startTime as startTime,
                rs.endTime as endTime,
                coalesce(rs.durationSeconds, 0) as durationSeconds
            FROM ReadingSessionEntity rs
            JOIN rs.book b
            JOIN UserBookProgressEntity ubp ON ubp.book.id = b.id AND ubp.user.id = rs.user.id
            WHERE rs.user.id = :userId
            AND ubp.readStatus = org.grimmory.model.enums.ReadStatus.READ
            AND coalesce(ubp.dateFinished, ubp.readStatusModifiedTime, ubp.lastReadTime) IS NOT NULL
            ORDER BY b.id, rs.startTime ASC
            """)
    List<PageTurnerSessionDto> findPageTurnerSessionsByUser(@Param("userId") Long userId);

    @Query("""
            SELECT
                b.id as bookId,
                coalesce(b.metadata.title, 'Unknown Book') as bookTitle,
                rs.startTime as sessionDate,
                rs.endProgress as endProgress
            FROM ReadingSessionEntity rs
            JOIN rs.book b
            JOIN UserBookProgressEntity ubp ON ubp.book.id = b.id AND ubp.user.id = rs.user.id
            WHERE rs.user.id = :userId
            AND ubp.readStatus = org.grimmory.model.enums.ReadStatus.READ
            AND year(coalesce(ubp.dateFinished, ubp.readStatusModifiedTime, ubp.lastReadTime)) = :year
            AND rs.endProgress IS NOT NULL
            ORDER BY b.id, rs.startTime ASC
            """)
    List<CompletionRaceSessionDto> findCompletionRaceSessionsByUserAndYear(
            @Param("userId") Long userId,
            @Param("year") int year);

    @Query("""
            SELECT rs.startTime
            FROM ReadingSessionEntity rs
            WHERE rs.user.id = :userId
            ORDER BY rs.startTime
            """)
    List<Instant> findAllSessionStartTimesByUser(@Param("userId") Long userId);

    // ========================================================================
    // Listening (audiobook) stats
    // ========================================================================

    @Query("""
            SELECT rs.startTime as startTime,
                   coalesce(rs.durationSeconds, 0) as durationSeconds
            FROM ReadingSessionEntity rs
            WHERE rs.user.id = :userId
            AND rs.bookType = org.grimmory.model.enums.BookFileType.AUDIOBOOK
            AND (:periodStart IS NULL OR rs.startTime >= :periodStart)
            AND (:periodEnd IS NULL OR rs.startTime < :periodEnd)
            ORDER BY rs.startTime
            """)
    List<SessionTimestampDto> findListeningTimestampsByUser(
            @Param("userId") Long userId,
            @Param("periodStart") Instant periodStart,
            @Param("periodEnd") Instant periodEnd);

    @Query("""
            SELECT rs.startTime as startTime,
                   coalesce(rs.durationSeconds, 0) as durationSeconds
            FROM ReadingSessionEntity rs
            WHERE rs.user.id = :userId
            AND rs.bookType = org.grimmory.model.enums.BookFileType.AUDIOBOOK
            ORDER BY rs.startTime DESC
            """)
    List<SessionTimestampDto> findListeningTimestampsByUserPaged(
            @Param("userId") Long userId,
            Pageable pageable);

    @Query("""
            SELECT b.id as bookId,
                   coalesce(bm.title, 'Unknown') as title,
                   max(rs.endProgress) as maxProgress,
                   coalesce(max(bf.durationSeconds), 0L) as totalDurationSeconds,
                   sum(rs.durationSeconds) as listenedDurationSeconds
            FROM ReadingSessionEntity rs
            JOIN rs.book b
            LEFT JOIN b.metadata bm
            LEFT JOIN b.bookFiles bf ON bf.bookType = org.grimmory.model.enums.BookFileType.AUDIOBOOK
            WHERE rs.user.id = :userId
            AND rs.bookType = org.grimmory.model.enums.BookFileType.AUDIOBOOK
            GROUP BY b.id, bm.title
            """)
    List<AudiobookProgressDto> findAudiobookProgressByUser(@Param("userId") Long userId);

    @Query("""
            SELECT year(coalesce(ubp.dateFinished, ubp.readStatusModifiedTime)) as year,
                   month(coalesce(ubp.dateFinished, ubp.readStatusModifiedTime)) as month,
                   count(ubp) as booksCompleted
            FROM UserBookProgressEntity ubp
            WHERE ubp.user.id = :userId
            AND ubp.readStatus = org.grimmory.model.enums.ReadStatus.READ
            AND coalesce(ubp.dateFinished, ubp.readStatusModifiedTime) IS NOT NULL
            AND EXISTS (
                SELECT 1 FROM ReadingSessionEntity rs
                WHERE rs.book.id = ubp.book.id
                AND rs.user.id = ubp.user.id
                AND rs.bookType = org.grimmory.model.enums.BookFileType.AUDIOBOOK
            )
            GROUP BY year(coalesce(ubp.dateFinished, ubp.readStatusModifiedTime)),
                     month(coalesce(ubp.dateFinished, ubp.readStatusModifiedTime))
            ORDER BY year DESC, month DESC
            """)
    List<MonthlyCompletedAudiobookDto> findMonthlyCompletedAudiobooks(@Param("userId") Long userId);

    @Query("""
            SELECT
                c.name as genre,
                count(distinct b.id) as bookCount,
                count(rs) as totalSessions,
                sum(rs.durationSeconds) as totalDurationSeconds
            FROM ReadingSessionEntity rs
            JOIN rs.book b
            JOIN b.metadata.categories c
            WHERE rs.user.id = :userId
            AND rs.bookType = org.grimmory.model.enums.BookFileType.AUDIOBOOK
            GROUP BY c.name
            ORDER BY totalDurationSeconds DESC
            """)
    List<GenreStatisticsDto> findListeningGenreStatisticsByUser(@Param("userId") Long userId);

    @Query("""
            SELECT a.name as authorName,
                   count(distinct rs.book.id) as bookCount,
                   count(rs) as totalSessions,
                   coalesce(sum(rs.durationSeconds), 0L) as totalDurationSeconds
            FROM ReadingSessionEntity rs
            JOIN rs.book b
            JOIN b.metadata m
            JOIN m.authors a
            WHERE rs.user.id = :userId
            AND rs.bookType = org.grimmory.model.enums.BookFileType.AUDIOBOOK
            GROUP BY a.name
            ORDER BY totalDurationSeconds DESC
            """)
    List<ListeningAuthorDto> findListeningAuthorStatsByUser(@Param("userId") Long userId);

    @Query("""
            SELECT b.id as bookId,
                   coalesce(bm.title, 'Unknown') as title,
                   bm.pageCount as pageCount,
                   min(rs.startTime) as firstSessionStart,
                   max(rs.endTime) as lastSessionEnd,
                   count(rs) as totalSessions,
                   coalesce(sum(rs.durationSeconds), 0L) as totalDurationSeconds,
                   coalesce(max(rs.endProgress), 0.0) / 100.0 as maxProgress,
                   ubp.readStatus as readStatus
            FROM ReadingSessionEntity rs
            JOIN rs.book b
            LEFT JOIN b.metadata bm
            LEFT JOIN b.userBookProgress ubp ON ubp.user.id = rs.user.id
            WHERE rs.user.id = :userId
            AND rs.startTime >= :periodStart AND rs.startTime < :periodEnd
            GROUP BY b.id, bm.title, bm.pageCount, ubp.readStatus
            ORDER BY firstSessionStart
            """)
    List<BookTimelineDto> findBookTimelineByUserAndYear(
            @Param("userId") Long userId,
            @Param("periodStart") Instant periodStart,
            @Param("periodEnd") Instant periodEnd);
}
