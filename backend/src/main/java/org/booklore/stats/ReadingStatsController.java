package org.booklore.stats;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.booklore.stats.StatsResponses.ActivityMonth;
import org.booklore.stats.StatsResponses.BookFlow;
import org.booklore.stats.StatsResponses.ComparisonCell;
import org.booklore.stats.StatsResponses.Days;
import org.booklore.stats.StatsResponses.DecadeCell;
import org.booklore.stats.StatsResponses.Distribution;
import org.booklore.stats.StatsResponses.FinishTimes;
import org.booklore.stats.StatsResponses.GenreTime;
import org.booklore.stats.StatsResponses.LengthCell;
import org.booklore.stats.StatsResponses.ProfileRow;
import org.booklore.stats.StatsResponses.Rows;
import org.booklore.stats.StatsResponses.SeriesPage;
import org.booklore.stats.StatsResponses.SeriesSummary;
import org.booklore.stats.StatsResponses.Slots;
import org.booklore.stats.StatsResponses.Week;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@Tag(name = "Stats", description = "Endpoints for library and reading statistics")
@RequestMapping("/api/v1/stats/reading")
@PreAuthorize("@securityUtil.canAccessUserStats() or @securityUtil.isAdmin()")
@RestController
@RequiredArgsConstructor
public class ReadingStatsController {

    private final StatsService statsService;

    @Operation(summary = "Count books by reading dimension", description = "Retrieve book counts per reading status, personal rating or progress reached.")
    @ApiResponse(responseCode = "200", description = "Counts returned successfully")
    @GetMapping("/distribution/{dimension}")
    public ResponseEntity<Distribution> getReadingDistribution(
            @ParameterObject @ModelAttribute StatsQuery query,
            @Parameter(description = "status, rating or stopped") @PathVariable @Pattern(regexp = "status|rating|stopped") String dimension) {
        return ResponseEntity.ok(statsService.distribution(query, dimension));
    }

    @Operation(summary = "Get ratings by book length", description = "Retrieve rated book counts per page count band and personal rating.")
    @ApiResponse(responseCode = "200", description = "Ratings returned successfully")
    @GetMapping("/length")
    public ResponseEntity<Rows<LengthCell>> getLength(@ParameterObject @ModelAttribute StatsQuery query) {
        return ResponseEntity.ok(statsService.length(query));
    }

    @Operation(summary = "Compare personal and outside ratings",
            description = "Retrieve book counts per personal and outside rating, with the total difference between them.")
    @ApiResponse(responseCode = "200", description = "Ratings returned successfully")
    @GetMapping("/comparison")
    public ResponseEntity<Rows<ComparisonCell>> getComparison(@ParameterObject @ModelAttribute StatsQuery query) {
        return ResponseEntity.ok(statsService.comparison(query));
    }

    @Operation(summary = "Get ratings by publication decade", description = "Retrieve rated book counts per publication decade and personal rating.")
    @ApiResponse(responseCode = "200", description = "Ratings returned successfully")
    @GetMapping("/decade")
    public ResponseEntity<Rows<DecadeCell>> getDecade(@ParameterObject @ModelAttribute StatsQuery query) {
        return ResponseEntity.ok(statsService.decade(query));
    }

    @Operation(summary = "Get the book flow", description = "Retrieve book counts by year added and reading status, and by reading status and rating.")
    @ApiResponse(responseCode = "200", description = "Book flow returned successfully")
    @GetMapping("/flow")
    public ResponseEntity<BookFlow> getBookFlow(@ParameterObject @ModelAttribute StatsQuery query) {
        return ResponseEntity.ok(statsService.bookFlow(query));
    }

    @Operation(summary = "Get monthly activity", description = "Retrieve books added, books finished, pages finished and backlog change per month.")
    @ApiResponse(responseCode = "200", description = "Activity returned successfully")
    @GetMapping("/activity")
    public ResponseEntity<Rows<ActivityMonth>> getActivity(@ParameterObject @ModelAttribute StatsQuery query) {
        return ResponseEntity.ok(statsService.activity(query));
    }

    @Operation(summary = "Get the series summary",
            description = "Retrieve the largest series with book counts per status, and totals for completed, in-progress and not-started series.")
    @ApiResponse(responseCode = "200", description = "Series summary returned successfully")
    @GetMapping("/series/summary")
    public ResponseEntity<SeriesSummary> getSeriesSummary(@ParameterObject @ModelAttribute StatsQuery query) {
        return ResponseEntity.ok(statsService.seriesSummary(query));
    }

    @Operation(summary = "List series",
            description = "Retrieve a page of series, filtered by name and state and sorted by progress, rating, book count or name.")
    @ApiResponse(responseCode = "200", description = "Series returned successfully")
    @GetMapping("/series")
    public ResponseEntity<SeriesPage> getSeries(
            @ParameterObject @ModelAttribute StatsQuery query,
            @Parameter(description = "Series name search") @RequestParam(required = false) String search,
            @Parameter(description = "in-progress, paused, not-started, completed or abandoned; default any")
            @RequestParam(required = false) @Pattern(regexp = "in-progress|paused|not-started|completed|abandoned") String state,
            @Parameter(description = "progress, rating, books or name; default progress")
            @RequestParam(defaultValue = "progress") @Pattern(regexp = "progress|rating|books|name") String sort,
            @Parameter(description = "Zero-based page; default 0") @RequestParam(defaultValue = "0") @Min(0) @Max(Integer.MAX_VALUE / SeriesQueries.PAGE_SIZE) int page) {
        return ResponseEntity.ok(statsService.series(query, search, state, sort, page));
    }

    @Operation(summary = "Get the reading profile", description = "Retrieve the Reading DNA and Reading Habits scores.")
    @ApiResponse(responseCode = "200", description = "Profile returned successfully")
    @GetMapping("/profile")
    public ResponseEntity<Rows<ProfileRow>> getProfile(@ParameterObject @ModelAttribute StatsQuery query) {
        return ResponseEntity.ok(statsService.profile(query));
    }

    @Operation(summary = "Get reading time by weekday and hour",
            description = "Retrieve reading time per weekday and hour, and how many of each weekday the period covers.")
    @ApiResponse(responseCode = "200", description = "Reading time returned successfully")
    @GetMapping("/slots")
    public ResponseEntity<Slots> getSlots(@ParameterObject @ModelAttribute StatsQuery query,
                                          @Parameter(description = "all, YYYY, YYYY-MM or YYYY-Www; default all") @RequestParam(defaultValue = "all") String period) {
        return ResponseEntity.ok(statsService.slots(query, period));
    }

    @Operation(summary = "Get sessions per day", description = "Retrieve session counts per day for a year, and reading streaks.")
    @ApiResponse(responseCode = "200", description = "Sessions returned successfully")
    @GetMapping("/days")
    public ResponseEntity<Days> getDays(@ParameterObject @ModelAttribute StatsQuery query,
                                        @Parameter(description = "YYYY") @RequestParam String year) {
        return ResponseEntity.ok(statsService.days(query, year));
    }

    @Operation(summary = "Get reading time per genre", description = "Retrieve reading time for the most-read genres.")
    @ApiResponse(responseCode = "200", description = "Genres returned successfully")
    @GetMapping("/genres")
    public ResponseEntity<Rows<GenreTime>> getGenres(@ParameterObject @ModelAttribute StatsQuery query) {
        return ResponseEntity.ok(statsService.genres(query));
    }

    @Operation(summary = "List a week's sessions", description = "Retrieve the reading sessions in an ISO week.")
    @ApiResponse(responseCode = "200", description = "Sessions returned successfully")
    @GetMapping("/week")
    public ResponseEntity<Week> getWeek(@ParameterObject @ModelAttribute StatsQuery query,
                                        @Parameter(description = "YYYY-Www") @RequestParam String week) {
        return ResponseEntity.ok(statsService.week(query, week));
    }

    @Operation(summary = "Get days to finish",
            description = "Retrieve books finished in a year, grouped by days from first session to finish, with the median, quickest and longest.")
    @ApiResponse(responseCode = "200", description = "Finish times returned successfully")
    @GetMapping("/days-to-finish")
    public ResponseEntity<FinishTimes> getDaysToFinish(@ParameterObject @ModelAttribute StatsQuery query,
                                                       @Parameter(description = "YYYY") @RequestParam String year) {
        return ResponseEntity.ok(statsService.daysToFinish(query, year));
    }
}
