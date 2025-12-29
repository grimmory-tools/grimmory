package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.dto.*;
import com.adityachandel.booklore.model.entity.ReadingSessionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReadingSessionRepository extends JpaRepository<ReadingSessionEntity, Long> {

    @Query("""
            SELECT CAST(rs.createdAt AS LocalDate) as date, COUNT(rs) as count
            FROM ReadingSessionEntity rs
            WHERE rs.user.id = :userId
            AND YEAR(rs.createdAt) = :year
            GROUP BY CAST(rs.createdAt AS LocalDate)
            ORDER BY date
            """)
    List<ReadingSessionCountDto> findSessionCountsByUserAndYear(@Param("userId") Long userId, @Param("year") int year);

    @Query("""
            SELECT
                b.id as bookId,
                b.metadata.title as bookTitle,
                rs.bookType as bookFileType,
                MIN(rs.startTime) as startDate,
                MAX(rs.endTime) as endDate,
                COUNT(rs) as totalSessions,
                SUM(rs.durationSeconds) as totalDurationSeconds
            FROM ReadingSessionEntity rs
            JOIN rs.book b
            WHERE rs.user.id = :userId
            AND YEAR(rs.startTime) = :year
            AND MONTH(rs.startTime) = :month
            AND WEEK(rs.startTime) = :week
            GROUP BY b.id, b.metadata.title, rs.bookType
            ORDER BY MIN(rs.startTime)
            """)
    List<ReadingSessionTimelineDto> findSessionTimelineByUserAndWeek(
            @Param("userId") Long userId,
            @Param("year") int year,
            @Param("month") int month,
            @Param("week") int week);

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

    @Query("""
            SELECT
                HOUR(rs.startTime) as hourOfDay,
                COUNT(rs) as sessionCount,
                SUM(rs.durationSeconds) as totalDurationSeconds
            FROM ReadingSessionEntity rs
            WHERE rs.user.id = :userId
            AND (:year IS NULL OR YEAR(rs.startTime) = :year)
            AND (:month IS NULL OR MONTH(rs.startTime) = :month)
            GROUP BY HOUR(rs.startTime)
            ORDER BY hourOfDay
            """)
    List<PeakReadingHourDto> findPeakReadingHoursByUser(
            @Param("userId") Long userId,
            @Param("year") Integer year,
            @Param("month") Integer month);

    @Query("""
            SELECT
                DAYOFWEEK(rs.startTime) as dayOfWeek,
                COUNT(rs) as sessionCount,
                SUM(rs.durationSeconds) as totalDurationSeconds
            FROM ReadingSessionEntity rs
            WHERE rs.user.id = :userId
            AND (:year IS NULL OR YEAR(rs.startTime) = :year)
            AND (:month IS NULL OR MONTH(rs.startTime) = :month)
            GROUP BY DAYOFWEEK(rs.startTime)
            ORDER BY dayOfWeek
            """)
    List<FavoriteReadingDayDto> findFavoriteReadingDaysByUser(
            @Param("userId") Long userId,
            @Param("year") Integer year,
            @Param("month") Integer month);

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

    @Query("""
            SELECT rs
            FROM ReadingSessionEntity rs
            WHERE rs.user.id = :userId
            AND rs.book.id = :bookId
            ORDER BY rs.startTime DESC
            """)
    Page<ReadingSessionEntity> findByUserIdAndBookId(
            @Param("userId") Long userId,
            @Param("bookId") Long bookId,
            Pageable pageable);
}
