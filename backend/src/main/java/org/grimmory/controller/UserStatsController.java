package org.grimmory.controller;

import org.booklore.model.dto.response.*;
import org.booklore.service.ReadingSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/api/v1/user-stats")
@Tag(name = "User Stats", description = "Endpoints for reading and listening analytics derived from user sessions")
public class UserStatsController {

    private final ReadingSessionService readingSessionService;

    @Operation(summary = "Get reading session heatmap for a year", description = "Returns daily reading session counts for the authenticated user for a specific year")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Heatmap data retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/reading/heatmap")
    @PreAuthorize("@securityUtil.canAccessUserStats() or @securityUtil.isAdmin()")
    public ResponseEntity<List<ReadingSessionHeatmapResponse>> getHeatmapForYear(@RequestParam int year) {
        List<ReadingSessionHeatmapResponse> heatmapData = readingSessionService.getSessionHeatmapForYear(year);
        return ResponseEntity.ok(heatmapData);
    }

    @Operation(summary = "Get reading session heatmap for a month", description = "Returns daily reading session counts for the authenticated user for a specific year and month")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Heatmap data retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/reading/heatmap/monthly")
    @PreAuthorize("@securityUtil.canAccessUserStats() or @securityUtil.isAdmin()")
    public ResponseEntity<List<ReadingSessionHeatmapResponse>> getHeatmapForMonth(
            @RequestParam int year,
            @RequestParam int month) {
        List<ReadingSessionHeatmapResponse> heatmapData = readingSessionService.getSessionHeatmapForMonth(year, month);
        return ResponseEntity.ok(heatmapData);
    }

    @Operation(summary = "Get reading session timeline for a week", description = "Returns reading sessions grouped by book for calendar timeline view")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Timeline data retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid week or year"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/reading/timeline")
    @PreAuthorize("@securityUtil.canAccessUserStats() or @securityUtil.isAdmin()")
    public ResponseEntity<List<ReadingSessionTimelineResponse>> getTimelineForWeek(
            @RequestParam int year,
            @RequestParam int week) {
        List<ReadingSessionTimelineResponse> timelineData = readingSessionService.getSessionTimelineForWeek(year, week);
        return ResponseEntity.ok(timelineData);
    }

    @Operation(summary = "Get reading speed analysis", description = "Returns average reading speed (progress per minute) over time for a specific year")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reading speed data retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/reading/speed")
    @PreAuthorize("@securityUtil.canAccessUserStats() or @securityUtil.isAdmin()")
    public ResponseEntity<List<ReadingSpeedResponse>> getReadingSpeedForYear(@RequestParam int year) {
        List<ReadingSpeedResponse> speedData = readingSessionService.getReadingSpeedForYear(year);
        return ResponseEntity.ok(speedData);
    }

    @Operation(summary = "Get peak reading hours", description = "Returns reading activity distribution by hour of day. Can be filtered by year and/or month.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Peak reading hours retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/reading/peak-hours")
    @PreAuthorize("@securityUtil.canAccessUserStats() or @securityUtil.isAdmin()")
    public ResponseEntity<List<PeakHoursResponse>> getPeakReadingHours(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        List<PeakHoursResponse> peakHours = readingSessionService.getPeakReadingHours(year, month);
        return ResponseEntity.ok(peakHours);
    }

    @Operation(summary = "Get favorite reading days", description = "Returns reading activity distribution by day of week. Can be filtered by year and/or month.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Favorite reading days retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/reading/favorite-days")
    @PreAuthorize("@securityUtil.canAccessUserStats() or @securityUtil.isAdmin()")
    public ResponseEntity<List<FavoriteReadingDaysResponse>> getFavoriteReadingDays(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        List<FavoriteReadingDaysResponse> favoriteDays = readingSessionService.getFavoriteReadingDays(year, month);
        return ResponseEntity.ok(favoriteDays);
    }

    @Operation(summary = "Get genre statistics", description = "Returns reading statistics grouped by book genres/categories")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Genre statistics retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/reading/genres")
    @PreAuthorize("@securityUtil.canAccessUserStats() or @securityUtil.isAdmin()")
    public ResponseEntity<List<GenreStatisticsResponse>> getGenreStatistics() {
        List<GenreStatisticsResponse> genreStats = readingSessionService.getGenreStatistics();
        return ResponseEntity.ok(genreStats);
    }

    @Operation(summary = "Get completion timeline", description = "Returns reading completion statistics over time with status breakdown for a specific year")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Completion timeline retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/reading/completion-timeline")
    @PreAuthorize("@securityUtil.canAccessUserStats() or @securityUtil.isAdmin()")
    public ResponseEntity<List<CompletionTimelineResponse>> getCompletionTimeline(@RequestParam int year) {
        List<CompletionTimelineResponse> timeline = readingSessionService.getCompletionTimeline(year);
        return ResponseEntity.ok(timeline);
    }

    @Operation(summary = "Get book completion heatmap", description = "Returns monthly book completion counts for the last 10 years for the authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Book completion heatmap data retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/reading/book-completion-heatmap")
    @PreAuthorize("@securityUtil.canAccessUserStats() or @securityUtil.isAdmin()")
    public ResponseEntity<List<BookCompletionHeatmapResponse>> getBookCompletionHeatmap() {
        List<BookCompletionHeatmapResponse> heatmapData = readingSessionService.getBookCompletionHeatmap();
        return ResponseEntity.ok(heatmapData);
    }

    @Operation(summary = "Get page turner scores", description = "Returns engagement/grip scores for completed books based on reading session patterns")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page turner scores retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/reading/page-turner-scores")
    @PreAuthorize("@securityUtil.canAccessUserStats() or @securityUtil.isAdmin()")
    public ResponseEntity<List<PageTurnerScoreResponse>> getPageTurnerScores() {
        List<PageTurnerScoreResponse> scores = readingSessionService.getPageTurnerScores();
        return ResponseEntity.ok(scores);
    }

    @Operation(summary = "Get completion race data", description = "Returns reading session progress data for completed books in a given year, for visualizing completion races")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Completion race data retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/reading/completion-race")
    @PreAuthorize("@securityUtil.canAccessUserStats() or @securityUtil.isAdmin()")
    public ResponseEntity<List<CompletionRaceResponse>> getCompletionRace(@RequestParam int year) {
        List<CompletionRaceResponse> data = readingSessionService.getCompletionRace(year);
        return ResponseEntity.ok(data);
    }

    @Operation(summary = "Get book distribution statistics", description = "Returns rating, progress, and read status distributions for the authenticated user's library")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Book distributions retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/reading/book-distributions")
    @PreAuthorize("@securityUtil.canAccessUserStats() or @securityUtil.isAdmin()")
    public ResponseEntity<BookDistributionsResponse> getBookDistributions() {
        BookDistributionsResponse data = readingSessionService.getBookDistributions();
        return ResponseEntity.ok(data);
    }

    @Operation(summary = "Get all reading dates", description = "Returns daily reading session counts across all time for the authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reading dates retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/reading/dates")
    @PreAuthorize("@securityUtil.canAccessUserStats() or @securityUtil.isAdmin()")
    public ResponseEntity<List<ReadingSessionHeatmapResponse>> getReadingDates() {
        List<ReadingSessionHeatmapResponse> data = readingSessionService.getReadingDates();
        return ResponseEntity.ok(data);
    }

    @Operation(summary = "Get session scatter data", description = "Returns individual session data points for scatter plot visualization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session scatter data retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/reading/session-scatter")
    @PreAuthorize("@securityUtil.canAccessUserStats() or @securityUtil.isAdmin()")
    public ResponseEntity<List<SessionScatterResponse>> getSessionScatter(@RequestParam int year) {
        List<SessionScatterResponse> data = readingSessionService.getSessionScatter(year);
        return ResponseEntity.ok(data);
    }

    @Operation(summary = "Get reading streak", description = "Returns current streak, longest streak, total reading days, and last 52 weeks of daily reading activity")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reading streak data retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/reading/streak")
    @PreAuthorize("@securityUtil.canAccessUserStats() or @securityUtil.isAdmin()")
    public ResponseEntity<ReadingStreakResponse> getReadingStreak() {
        return ResponseEntity.ok(readingSessionService.getReadingStreak());
    }

    @Operation(summary = "Get book timeline for a year", description = "Returns the reading lifespan of each book the user read in a given year")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Book timeline data retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/reading/book-timeline")
    @PreAuthorize("@securityUtil.canAccessUserStats() or @securityUtil.isAdmin()")
    public ResponseEntity<List<BookTimelineResponse>> getBookTimeline(@RequestParam int year) {
        return ResponseEntity.ok(readingSessionService.getBookTimeline(year));
    }

    // ========================================================================
    // Listening (audiobook) stats
    // ========================================================================

    @Operation(summary = "Get listening heatmap for a month", description = "Returns daily listening session counts and duration for the authenticated user for a specific month")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listening heatmap data retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/listening/heatmap/monthly")
    @PreAuthorize("@securityUtil.canAccessUserStats() or @securityUtil.isAdmin()")
    public ResponseEntity<List<ListeningHeatmapResponse>> getListeningHeatmapForMonth(
            @RequestParam int year,
            @RequestParam int month) {
        return ResponseEntity.ok(readingSessionService.getListeningHeatmapForMonth(year, month));
    }

    @Operation(summary = "Get weekly listening trend", description = "Returns weekly listening hours over a number of past weeks")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Weekly listening trend retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/listening/weekly-trend")
    @PreAuthorize("@securityUtil.canAccessUserStats() or @securityUtil.isAdmin()")
    public ResponseEntity<List<WeeklyListeningTrendResponse>> getWeeklyListeningTrend(
            @RequestParam(defaultValue = "26") int weeks) {
        return ResponseEntity.ok(readingSessionService.getWeeklyListeningTrend(weeks));
    }

    @Operation(summary = "Get audiobook completion progress", description = "Returns in-progress audiobooks with completion percentages and overall library stats")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listening completion data retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/listening/completion")
    @PreAuthorize("@securityUtil.canAccessUserStats() or @securityUtil.isAdmin()")
    public ResponseEntity<ListeningCompletionResponse> getListeningCompletion() {
        return ResponseEntity.ok(readingSessionService.getListeningCompletion());
    }

    @Operation(summary = "Get monthly audiobook pace", description = "Returns audiobooks completed per month with listening duration")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Monthly pace data retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/listening/monthly-pace")
    @PreAuthorize("@securityUtil.canAccessUserStats() or @securityUtil.isAdmin()")
    public ResponseEntity<List<MonthlyPaceResponse>> getMonthlyListeningPace(
            @RequestParam(defaultValue = "12") int months) {
        return ResponseEntity.ok(readingSessionService.getMonthlyListeningPace(months));
    }

    @Operation(summary = "Get audiobook finish rate funnel", description = "Returns how many audiobooks reached each progress milestone (25%, 50%, 75%, finished)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Finish funnel data retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/listening/finish-funnel")
    @PreAuthorize("@securityUtil.canAccessUserStats() or @securityUtil.isAdmin()")
    public ResponseEntity<ListeningFinishFunnelResponse> getListeningFinishFunnel() {
        return ResponseEntity.ok(readingSessionService.getListeningFinishFunnel());
    }

    @Operation(
            summary = "Get listening peak hours",
            description = "Returns listening activity distribution by hour of day. Can be filtered by year and/or month.",
            operationId = "getListeningPeakHours"
    )
    @GetMapping("/listening/peak-hours")
    @PreAuthorize("@securityUtil.canAccessUserStats() or @securityUtil.isAdmin()")
    public ResponseEntity<List<PeakHoursResponse>> getListeningPeakHours(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        return ResponseEntity.ok(readingSessionService.getListeningPeakHours(year, month));
    }

    @Operation(
            summary = "Get listening genre statistics",
            description = "Returns listening statistics grouped by audiobook genres/categories.",
            operationId = "getListeningGenreStatistics"
    )
    @GetMapping("/listening/genres")
    @PreAuthorize("@securityUtil.canAccessUserStats() or @securityUtil.isAdmin()")
    public ResponseEntity<List<GenreStatisticsResponse>> getListeningGenreStatistics() {
        return ResponseEntity.ok(readingSessionService.getListeningGenreStatistics());
    }

    @Operation(
            summary = "Get listening author statistics",
            description = "Returns listening statistics grouped by audiobook authors.",
            operationId = "getListeningAuthorStats"
    )
    @GetMapping("/listening/authors")
    @PreAuthorize("@securityUtil.canAccessUserStats() or @securityUtil.isAdmin()")
    public ResponseEntity<List<ListeningAuthorResponse>> getListeningAuthorStats() {
        return ResponseEntity.ok(readingSessionService.getListeningAuthorStats());
    }

    @Operation(
            summary = "Get listening session scatter data",
            description = "Returns individual listening session points for scatter plot visualizations.",
            operationId = "getListeningSessionScatter"
    )
    @GetMapping("/listening/session-scatter")
    @PreAuthorize("@securityUtil.canAccessUserStats() or @securityUtil.isAdmin()")
    public ResponseEntity<List<SessionScatterResponse>> getListeningSessionScatter() {
        return ResponseEntity.ok(readingSessionService.getListeningSessionScatter());
    }

    @Operation(
            summary = "Get longest listened audiobooks",
            description = "Returns longest audiobooks ranked by listening duration.",
            operationId = "getListeningLongestBooks"
    )
    @GetMapping("/listening/longest-books")
    @PreAuthorize("@securityUtil.canAccessUserStats() or @securityUtil.isAdmin()")
    public ResponseEntity<List<LongestAudiobookResponse>> getListeningLongestBooks() {
        return ResponseEntity.ok(readingSessionService.getListeningLongestBooks());
    }
}
