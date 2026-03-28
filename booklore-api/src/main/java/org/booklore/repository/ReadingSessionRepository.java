package org.booklore.repository;

import org.booklore.model.dto.*;

import org.booklore.model.entity.ReadingSessionEntity;
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

    @Query(value = """
            SELECT DATE(CONVERT_TZ(start_time, '+00:00', :tzOffset)) as date,
                   COUNT(*) as count
            FROM reading_sessions
            WHERE user_id = :userId
            AND YEAR(CONVERT_TZ(start_time, '+00:00', :tzOffset)) = :year
            GROUP BY DATE(CONVERT_TZ(start_time, '+00:00', :tzOffset))
            ORDER BY date
            """, nativeQuery = true)
    List<ReadingSessionCountDto> findSessionCountsByUserAndYear(
            @Param("userId") Long userId,
            @Param("year") int year,
            @Param("tzOffset") String tzOffset);

    @Query(value = """
            SELECT DATE(CONVERT_TZ(start_time, '+00:00', :tzOffset)) as date,
                   COUNT(*) as count
            FROM reading_sessions
            WHERE user_id = :userId
            AND YEAR(CONVERT_TZ(start_time, '+00:00', :tzOffset)) = :year
            AND MONTH(CONVERT_TZ(start_time, '+00:00', :tzOffset)) = :month
            GROUP BY DATE(CONVERT_TZ(start_time, '+00:00', :tzOffset))
            ORDER BY date
            """, nativeQuery = true)
    List<ReadingSessionCountDto> findSessionCountsByUserAndYearAndMonth(
            @Param("userId") Long userId,
            @Param("year") int year,
            @Param("month") int month,
            @Param("tzOffset") String tzOffset);

        @Query("""
                        SELECT
                                b.id as bookId,
                                COALESCE(b.metadata.title,
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
                CAST(rs.createdAt AS LocalDate) as date,
                AVG(rs.progressDelta / (rs.durationSeconds / 60.0)) as avgProgressPerMinute,
                COUNT(rs) as totalSessions
            FROM ReadingSessionEntity rs
            WHERE rs.user.id = :userId
            AND rs.durationSeconds > 0
            AND rs.progressDelta > 0
            AND YEAR(rs.createdAt) = :year
            GROUP BY CAST(rs.createdAt AS LocalDate)
            ORDER BY date
            """)
    List<ReadingSpeedDto> findReadingSpeedByUserAndYear(@Param("userId") Long userId, @Param("year") int year);

    @Query(value = """
            SELECT
                HOUR(CONVERT_TZ(start_time, '+00:00', :tzOffset)) as hourOfDay,
                COUNT(*) as sessionCount,
                SUM(duration_seconds) as totalDurationSeconds
            FROM reading_sessions
            WHERE user_id = :userId
            AND (:year IS NULL OR YEAR(CONVERT_TZ(start_time, '+00:00', :tzOffset)) = :year)
            AND (:month IS NULL OR MONTH(CONVERT_TZ(start_time, '+00:00', :tzOffset)) = :month)
            GROUP BY HOUR(CONVERT_TZ(start_time, '+00:00', :tzOffset))
            ORDER BY hourOfDay
            """, nativeQuery = true)
    List<PeakReadingHourDto> findPeakReadingHoursByUser(
            @Param("userId") Long userId,
            @Param("year") Integer year,
            @Param("month") Integer month,
            @Param("tzOffset") String tzOffset);

    @Query(value = """
            SELECT
                DAYOFWEEK(CONVERT_TZ(start_time, '+00:00', :tzOffset)) as dayOfWeek,
                COUNT(*) as sessionCount,
                COALESCE(SUM(duration_seconds), 0) as totalDurationSeconds
            FROM reading_sessions
            WHERE user_id = :userId
            AND (:year IS NULL OR YEAR(CONVERT_TZ(start_time, '+00:00', :tzOffset)) = :year)
            AND (:month IS NULL OR MONTH(CONVERT_TZ(start_time, '+00:00', :tzOffset)) = :month)
            GROUP BY DAYOFWEEK(CONVERT_TZ(start_time, '+00:00', :tzOffset))
            ORDER BY dayOfWeek
            """, nativeQuery = true)
    List<FavoriteReadingDayDto> findFavoriteReadingDaysByUser(
            @Param("userId") Long userId,
            @Param("year") Integer year,
            @Param("month") Integer month,
            @Param("tzOffset") String tzOffset);

    @Query("""
            SELECT
                c.name as genre,
                COUNT(DISTINCT b.id) as bookCount,
                COUNT(rs) as totalSessions,
                SUM(rs.durationSeconds) as totalDurationSeconds
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
                COALESCE(b.metadata.title, 'Unknown Book') as bookTitle,
                b.metadata.pageCount as pageCount,
                ubp.personalRating as personalRating,
                COALESCE(ubp.dateFinished, ubp.readStatusModifiedTime, ubp.lastReadTime) as dateFinished,
                rs.startTime as startTime,
                rs.endTime as endTime,
                rs.durationSeconds as durationSeconds
            FROM ReadingSessionEntity rs
            JOIN rs.book b
            JOIN UserBookProgressEntity ubp ON ubp.book.id = b.id AND ubp.user.id = rs.user.id
            WHERE rs.user.id = :userId
            AND ubp.readStatus = org.booklore.model.enums.ReadStatus.READ
            AND COALESCE(ubp.dateFinished, ubp.readStatusModifiedTime, ubp.lastReadTime) IS NOT NULL
            ORDER BY b.id, rs.startTime ASC
            """)
    List<PageTurnerSessionDto> findPageTurnerSessionsByUser(@Param("userId") Long userId);

    @Query("""
            SELECT
                b.id as bookId,
                COALESCE(b.metadata.title, 'Unknown Book') as bookTitle,
                rs.startTime as sessionDate,
                rs.endProgress as endProgress
            FROM ReadingSessionEntity rs
            JOIN rs.book b
            JOIN UserBookProgressEntity ubp ON ubp.book.id = b.id AND ubp.user.id = rs.user.id
            WHERE rs.user.id = :userId
            AND ubp.readStatus = org.booklore.model.enums.ReadStatus.READ
            AND YEAR(COALESCE(ubp.dateFinished, ubp.readStatusModifiedTime, ubp.lastReadTime)) = :year
            AND rs.endProgress IS NOT NULL
            ORDER BY b.id, rs.startTime ASC
            """)
    List<CompletionRaceSessionDto> findCompletionRaceSessionsByUserAndYear(
            @Param("userId") Long userId,
            @Param("year") int year);

    @Query(value = """
            SELECT DATE(CONVERT_TZ(start_time, '+00:00', :tzOffset)) as date,
                   COUNT(*) as count
            FROM reading_sessions
            WHERE user_id = :userId
            GROUP BY DATE(CONVERT_TZ(start_time, '+00:00', :tzOffset))
            ORDER BY date
            """, nativeQuery = true)
    List<ReadingSessionCountDto> findAllSessionCountsByUser(
            @Param("userId") Long userId,
            @Param("tzOffset") String tzOffset);

    @Query(value = """
            SELECT
                HOUR(CONVERT_TZ(rs.start_time, '+00:00', :tzOffset))
                    + MINUTE(CONVERT_TZ(rs.start_time, '+00:00', :tzOffset)) / 60.0 as hourOfDay,
                rs.duration_seconds / 60.0 as durationMinutes,
                DAYOFWEEK(CONVERT_TZ(rs.start_time, '+00:00', :tzOffset)) as dayOfWeek
            FROM reading_sessions rs
            WHERE rs.user_id = :userId
            AND YEAR(CONVERT_TZ(rs.start_time, '+00:00', :tzOffset)) = :year
            ORDER BY rs.start_time DESC
            LIMIT 500
            """, nativeQuery = true)
    List<SessionScatterDto> findSessionScatterByUserAndYear(
            @Param("userId") Long userId,
            @Param("year") int year,
            @Param("tzOffset") String tzOffset);

    // ========================================================================
    // Listening (audiobook) stats
    // ========================================================================

    @Query(value = """
            SELECT DATE(CONVERT_TZ(start_time, '+00:00', :tzOffset)) as date,
                   COUNT(*) as sessions,
                   COALESCE(ROUND(SUM(duration_seconds) / 60.0), 0) as durationMinutes
            FROM reading_sessions
            WHERE user_id = :userId
            AND book_type = 'AUDIOBOOK'
            AND YEAR(CONVERT_TZ(start_time, '+00:00', :tzOffset)) = :year
            AND MONTH(CONVERT_TZ(start_time, '+00:00', :tzOffset)) = :month
            GROUP BY DATE(CONVERT_TZ(start_time, '+00:00', :tzOffset))
            ORDER BY date
            """, nativeQuery = true)
    List<ListeningHeatmapDto> findListeningSessionsByUserAndMonth(
            @Param("userId") Long userId,
            @Param("year") int year,
            @Param("month") int month,
            @Param("tzOffset") String tzOffset);

    @Query(value = """
            SELECT YEAR(CONVERT_TZ(start_time, '+00:00', :tzOffset)) as year,
                   WEEK(CONVERT_TZ(start_time, '+00:00', :tzOffset), 3) as week,
                   COALESCE(SUM(duration_seconds), 0) as totalDurationSeconds,
                   COUNT(*) as sessions
            FROM reading_sessions
            WHERE user_id = :userId
            AND book_type = 'AUDIOBOOK'
            AND CONVERT_TZ(start_time, '+00:00', :tzOffset) >= DATE_SUB(NOW(), INTERVAL :weeks WEEK)
            GROUP BY year, week
            ORDER BY year, week
            """, nativeQuery = true)
    List<WeeklyListeningTrendDto> findWeeklyListeningTrend(
            @Param("userId") Long userId,
            @Param("weeks") int weeks,
            @Param("tzOffset") String tzOffset);

    @Query(value = """
            SELECT rs.book_id as bookId,
                   COALESCE(bm.title, 'Unknown') as title,
                   COALESCE(MAX(rs.end_progress), 0) as maxProgress,
                   COALESCE(MAX(bf.duration_seconds), 0) as totalDurationSeconds,
                   SUM(rs.duration_seconds) as listenedDurationSeconds
            FROM reading_sessions rs
            JOIN book b ON rs.book_id = b.id
            LEFT JOIN book_metadata bm ON bm.book_id = b.id
            LEFT JOIN book_file bf ON bf.book_id = b.id AND bf.book_type = 'AUDIOBOOK'
            WHERE rs.user_id = :userId
            AND rs.book_type = 'AUDIOBOOK'
            GROUP BY rs.book_id, bm.title
            """, nativeQuery = true)
    List<AudiobookProgressDto> findAudiobookProgressByUser(@Param("userId") Long userId);

    @Query(value = """
            SELECT YEAR(COALESCE(ubp.date_finished, ubp.read_status_modified_time)) as year,
                   MONTH(COALESCE(ubp.date_finished, ubp.read_status_modified_time)) as month,
                   COUNT(*) as booksCompleted
            FROM user_book_progress ubp
            WHERE ubp.user_id = :userId
            AND ubp.read_status = 'READ'
            AND COALESCE(ubp.date_finished, ubp.read_status_modified_time) IS NOT NULL
            AND EXISTS (
                SELECT 1 FROM reading_sessions rs
                WHERE rs.book_id = ubp.book_id
                AND rs.user_id = ubp.user_id
                AND rs.book_type = 'AUDIOBOOK'
            )
            GROUP BY year, month
            ORDER BY year DESC, month DESC
            """, nativeQuery = true)
    List<MonthlyCompletedAudiobookDto> findMonthlyCompletedAudiobooks(@Param("userId") Long userId);

    @Query(value = """
            SELECT YEAR(CONVERT_TZ(start_time, '+00:00', :tzOffset)) as year,
                   MONTH(CONVERT_TZ(start_time, '+00:00', :tzOffset)) as month,
                   COALESCE(SUM(duration_seconds), 0) as totalDurationSeconds
            FROM reading_sessions
            WHERE user_id = :userId
            AND book_type = 'AUDIOBOOK'
            GROUP BY year, month
            ORDER BY year DESC, month DESC
            """, nativeQuery = true)
    List<MonthlyListeningDurationDto> findMonthlyListeningDurations(
            @Param("userId") Long userId,
            @Param("tzOffset") String tzOffset);

    @Query(value = """
            SELECT
                HOUR(CONVERT_TZ(start_time, '+00:00', :tzOffset)) as hourOfDay,
                COUNT(*) as sessionCount,
                SUM(duration_seconds) as totalDurationSeconds
            FROM reading_sessions
            WHERE user_id = :userId
            AND book_type = 'AUDIOBOOK'
            AND (:year IS NULL OR YEAR(CONVERT_TZ(start_time, '+00:00', :tzOffset)) = :year)
            AND (:month IS NULL OR MONTH(CONVERT_TZ(start_time, '+00:00', :tzOffset)) = :month)
            GROUP BY HOUR(CONVERT_TZ(start_time, '+00:00', :tzOffset))
            ORDER BY hourOfDay
            """, nativeQuery = true)
    List<PeakReadingHourDto> findListeningPeakHoursByUser(
            @Param("userId") Long userId,
            @Param("year") Integer year,
            @Param("month") Integer month,
            @Param("tzOffset") String tzOffset);

    @Query("""
            SELECT
                c.name as genre,
                COUNT(DISTINCT b.id) as bookCount,
                COUNT(rs) as totalSessions,
                SUM(rs.durationSeconds) as totalDurationSeconds
            FROM ReadingSessionEntity rs
            JOIN rs.book b
            JOIN b.metadata.categories c
            WHERE rs.user.id = :userId
            AND rs.bookType = org.booklore.model.enums.BookFileType.AUDIOBOOK
            GROUP BY c.name
            ORDER BY totalDurationSeconds DESC
            """)
    List<GenreStatisticsDto> findListeningGenreStatisticsByUser(@Param("userId") Long userId);

    @Query(value = """
            SELECT a.name as authorName,
                   COUNT(DISTINCT rs.book_id) as bookCount,
                   COUNT(*) as totalSessions,
                   COALESCE(SUM(rs.duration_seconds), 0) as totalDurationSeconds
            FROM reading_sessions rs
            JOIN book_metadata_author_mapping bam ON bam.book_id = rs.book_id
            JOIN author a ON a.id = bam.author_id
            WHERE rs.user_id = :userId
            AND rs.book_type = 'AUDIOBOOK'
            GROUP BY a.name
            ORDER BY totalDurationSeconds DESC
            """, nativeQuery = true)
    List<ListeningAuthorDto> findListeningAuthorStatsByUser(@Param("userId") Long userId);

    @Query(value = """
            SELECT
                HOUR(CONVERT_TZ(rs.start_time, '+00:00', :tzOffset))
                    + MINUTE(CONVERT_TZ(rs.start_time, '+00:00', :tzOffset)) / 60.0 as hourOfDay,
                rs.duration_seconds / 60.0 as durationMinutes,
                DAYOFWEEK(CONVERT_TZ(rs.start_time, '+00:00', :tzOffset)) as dayOfWeek
            FROM reading_sessions rs
            WHERE rs.user_id = :userId
            AND rs.book_type = 'AUDIOBOOK'
            ORDER BY rs.start_time DESC
            LIMIT 500
            """, nativeQuery = true)
    List<SessionScatterDto> findListeningSessionScatterByUser(
            @Param("userId") Long userId,
            @Param("tzOffset") String tzOffset);

    @Query(value = """
            SELECT rs.book_id as bookId,
                   COALESCE(bm.title, 'Unknown') as title,
                   bm.page_count as pageCount,
                   MIN(CONVERT_TZ(rs.start_time, '+00:00', :tzOffset)) as firstSessionDate,
                   MAX(CONVERT_TZ(rs.end_time, '+00:00', :tzOffset)) as lastSessionDate,
                   COUNT(*) as totalSessions,
                   COALESCE(SUM(rs.duration_seconds), 0) as totalDurationSeconds,
                   COALESCE(MAX(rs.end_progress), 0) / 100.0 as maxProgress,
                   ubp.read_status as readStatus
            FROM reading_sessions rs
            JOIN book b ON rs.book_id = b.id
            LEFT JOIN book_metadata bm ON bm.book_id = b.id
            LEFT JOIN user_book_progress ubp ON ubp.book_id = rs.book_id AND ubp.user_id = rs.user_id
            WHERE rs.user_id = :userId
            AND YEAR(CONVERT_TZ(rs.start_time, '+00:00', :tzOffset)) = :year
            GROUP BY rs.book_id, bm.title, bm.page_count, ubp.read_status
            ORDER BY firstSessionDate
            """, nativeQuery = true)
    List<BookTimelineDto> findBookTimelineByUserAndYear(
            @Param("userId") Long userId,
            @Param("year") int year,
            @Param("tzOffset") String tzOffset);
}
