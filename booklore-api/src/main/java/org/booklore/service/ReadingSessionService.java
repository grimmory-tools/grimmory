package org.booklore.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.CompletionRaceSessionDto;
import org.booklore.model.dto.request.ReadingSessionRequest;
import org.booklore.model.dto.PageTurnerSessionDto;
import org.booklore.model.dto.BookTimelineDto;
import org.booklore.model.dto.ProgressPercentDto;
import org.booklore.model.dto.ReadingSessionCountDto;
import org.booklore.model.dto.response.*;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.CategoryEntity;
import org.booklore.model.entity.ReadingSessionEntity;
import org.booklore.model.enums.ReadStatus;
import org.booklore.repository.BookRepository;
import org.booklore.repository.ReadingSessionRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.booklore.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReadingSessionService {

    private final AuthenticationService authenticationService;
    private final ReadingSessionRepository readingSessionRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final UserBookProgressRepository userBookProgressRepository;

    private String getTimezoneOffset() {
        ZoneOffset offset = ZoneId.systemDefault().getRules().getOffset(Instant.now());
        return offset.getId().equals("Z") ? "+00:00" : offset.getId();
    }

    @Transactional
    public void recordSession(ReadingSessionRequest request) {
        BookLoreUser authenticatedUser = authenticationService.getAuthenticatedUser();
        Long userId = authenticatedUser.getId();

        BookLoreUserEntity userEntity = userRepository.findById(userId).orElseThrow(() -> new UsernameNotFoundException("User not found with ID: " + userId));
        BookEntity book = bookRepository.findById(request.getBookId()).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(request.getBookId()));

        ReadingSessionEntity session = ReadingSessionEntity.builder()
                .user(userEntity)
                .book(book)
                .bookType(request.getBookType())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .durationSeconds(request.getDurationSeconds())
                .durationFormatted(request.getDurationFormatted())
                .startProgress(request.getStartProgress())
                .endProgress(request.getEndProgress())
                .progressDelta(request.getProgressDelta())
                .startLocation(request.getStartLocation())
                .endLocation(request.getEndLocation())
                .build();

        readingSessionRepository.save(session);

        log.info("Reading session persisted successfully: sessionId={}, userId={}, bookId={}, duration={}s", session.getId(), userId, request.getBookId(), request.getDurationSeconds());
    }

    @Transactional(readOnly = true)
    public List<ReadingSessionHeatmapResponse> getSessionHeatmapForYear(int year) {
        BookLoreUser authenticatedUser = authenticationService.getAuthenticatedUser();
        Long userId = authenticatedUser.getId();

        return readingSessionRepository.findSessionCountsByUserAndYear(userId, year, getTimezoneOffset())
                .stream()
                .map(dto -> ReadingSessionHeatmapResponse.builder()
                        .date(dto.getDate())
                        .count(dto.getCount())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ReadingSessionHeatmapResponse> getSessionHeatmapForMonth(int year, int month) {
        BookLoreUser authenticatedUser = authenticationService.getAuthenticatedUser();
        Long userId = authenticatedUser.getId();

        return readingSessionRepository.findSessionCountsByUserAndYearAndMonth(userId, year, month, getTimezoneOffset())
                .stream()
                .map(dto -> ReadingSessionHeatmapResponse.builder()
                        .date(dto.getDate())
                        .count(dto.getCount())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ReadingSessionTimelineResponse> getSessionTimelineForWeek(int year, int week) {
        BookLoreUser authenticatedUser = authenticationService.getAuthenticatedUser();
        Long userId = authenticatedUser.getId();

        LocalDate date = LocalDate.of(year, 1, 1)
                .with(WeekFields.ISO.weekOfYear(), week);
        LocalDateTime startOfWeek = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
        LocalDateTime endOfWeek = date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).plusDays(1).atStartOfDay();

        return readingSessionRepository.findSessionTimelineByUserAndWeek(userId, startOfWeek.atZone(ZoneId.systemDefault()).toInstant(), endOfWeek.atZone(ZoneId.systemDefault()).toInstant())
                .stream()
                .map(dto -> ReadingSessionTimelineResponse.builder()
                        .bookId(dto.getBookId())
                        .bookType(dto.getBookFileType())
                        .bookTitle(dto.getBookTitle())
                        .startDate(dto.getStartDate())
                        .endDate(dto.getEndDate())
                        .totalSessions(dto.getTotalSessions())
                        .totalDurationSeconds(dto.getTotalDurationSeconds())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ReadingSpeedResponse> getReadingSpeedForYear(int year) {
        BookLoreUser authenticatedUser = authenticationService.getAuthenticatedUser();
        Long userId = authenticatedUser.getId();

        return readingSessionRepository.findReadingSpeedByUserAndYear(userId, year)
                .stream()
                .map(dto -> ReadingSpeedResponse.builder()
                        .date(dto.getDate())
                        .avgProgressPerMinute(dto.getAvgProgressPerMinute())
                        .totalSessions(dto.getTotalSessions())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PeakHoursResponse> getPeakReadingHours(Integer year, Integer month) {
        BookLoreUser authenticatedUser = authenticationService.getAuthenticatedUser();
        Long userId = authenticatedUser.getId();

        return readingSessionRepository.findPeakReadingHoursByUser(userId, year, month, getTimezoneOffset())
                .stream()
                .map(dto -> PeakHoursResponse.builder()
                        .hourOfDay(dto.getHourOfDay())
                        .sessionCount(dto.getSessionCount())
                        .totalDurationSeconds(dto.getTotalDurationSeconds())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<FavoriteReadingDaysResponse> getFavoriteReadingDays(Integer year, Integer month) {
        BookLoreUser authenticatedUser = authenticationService.getAuthenticatedUser();
        Long userId = authenticatedUser.getId();

        String[] dayNames = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};

        return readingSessionRepository.findFavoriteReadingDaysByUser(userId, year, month, getTimezoneOffset())
                .stream()
                .map(dto -> FavoriteReadingDaysResponse.builder()
                        .dayOfWeek(dto.getDayOfWeek())
                        .dayName(dayNames[dto.getDayOfWeek() - 1])
                        .sessionCount(dto.getSessionCount())
                        .totalDurationSeconds(dto.getTotalDurationSeconds())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<GenreStatisticsResponse> getGenreStatistics() {
        BookLoreUser authenticatedUser = authenticationService.getAuthenticatedUser();
        Long userId = authenticatedUser.getId();

        return readingSessionRepository.findGenreStatisticsByUser(userId)
                .stream()
                .map(dto -> {
                    double avgSessionsPerBook = dto.getBookCount() > 0
                            ? (double) dto.getTotalSessions() / dto.getBookCount()
                            : 0.0;

                    return GenreStatisticsResponse.builder()
                            .genre(dto.getGenre())
                            .bookCount(dto.getBookCount())
                            .totalSessions(dto.getTotalSessions())
                            .totalDurationSeconds(dto.getTotalDurationSeconds())
                            .averageSessionsPerBook(Math.round(avgSessionsPerBook * 100.0) / 100.0)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CompletionTimelineResponse> getCompletionTimeline(int year) {
        BookLoreUser authenticatedUser = authenticationService.getAuthenticatedUser();
        Long userId = authenticatedUser.getId();
        Map<String, EnumMap<ReadStatus, Long>> timelineMap = new HashMap<>();

        userBookProgressRepository.findCompletionTimelineByUser(userId, year).forEach(dto -> {
            String key = dto.getYear() + "-" + dto.getMonth();
            timelineMap.computeIfAbsent(key, k -> new EnumMap<>(ReadStatus.class))
                    .put(dto.getReadStatus(), dto.getBookCount());
        });

        return timelineMap.entrySet().stream()
                .map(entry -> {
                    String[] parts = entry.getKey().split("-");
                    int yearPart = Integer.parseInt(parts[0]);
                    int month = Integer.parseInt(parts[1]);
                    Map<ReadStatus, Long> statusBreakdown = entry.getValue();

                    long totalBooks = statusBreakdown.values().stream().mapToLong(Long::longValue).sum();
                    long finishedBooks = statusBreakdown.getOrDefault(ReadStatus.READ, 0L);
                    double completionRate = totalBooks > 0 ? (finishedBooks * 100.0 / totalBooks) : 0.0;

                    return CompletionTimelineResponse.builder()
                            .year(yearPart)
                            .month(month)
                            .totalBooks(totalBooks)
                            .statusBreakdown(statusBreakdown)
                            .finishedBooks(finishedBooks)
                            .completionRate(Math.round(completionRate * 100.0) / 100.0)
                            .build();
                })
                .sorted((a, b) -> {
                    int cmp = b.getYear().compareTo(a.getYear());
                    return cmp != 0 ? cmp : b.getMonth().compareTo(a.getMonth());
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<ReadingSessionResponse> getReadingSessionsForBook(Long bookId, int page, int size) {
        BookLoreUser authenticatedUser = authenticationService.getAuthenticatedUser();
        Long userId = authenticatedUser.getId();

        if (!bookRepository.existsById(bookId)) {
            throw ApiError.BOOK_NOT_FOUND.createException(bookId);
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<ReadingSessionEntity> sessions = readingSessionRepository.findByUserIdAndBookId(userId, bookId, pageable);

        return sessions.map(session -> ReadingSessionResponse.builder()
                .id(session.getId())
                .bookId(session.getBook().getId())
                .bookTitle(session.getBook().getMetadata().getTitle())
                .bookType(session.getBookType())
                .startTime(session.getStartTime())
                .endTime(session.getEndTime())
                .durationSeconds(session.getDurationSeconds())
                .startProgress(session.getStartProgress())
                .endProgress(session.getEndProgress())
                .progressDelta(session.getProgressDelta())
                .startLocation(session.getStartLocation())
                .endLocation(session.getEndLocation())
                .createdAt(session.getCreatedAt())
                .build());
    }

    @Transactional(readOnly = true)
    public List<BookCompletionHeatmapResponse> getBookCompletionHeatmap() {
        BookLoreUser authenticatedUser = authenticationService.getAuthenticatedUser();
        Long userId = authenticatedUser.getId();

        int currentYear = LocalDate.now().getYear();
        int startYear = currentYear - 9;

        return userBookProgressRepository.findBookCompletionHeatmap(userId, startYear, currentYear)
                .stream()
                .map(dto -> BookCompletionHeatmapResponse.builder()
                        .year(dto.getYear())
                        .month(dto.getMonth())
                        .count(dto.getCount())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PageTurnerScoreResponse> getPageTurnerScores() {
        BookLoreUser authenticatedUser = authenticationService.getAuthenticatedUser();
        Long userId = authenticatedUser.getId();

        var sessions = readingSessionRepository.findPageTurnerSessionsByUser(userId);

        Map<Long, List<PageTurnerSessionDto>> sessionsByBook = sessions.stream()
                .collect(Collectors.groupingBy(PageTurnerSessionDto::getBookId, LinkedHashMap::new, Collectors.toList()));

        Set<Long> bookIds = sessionsByBook.keySet();
        Map<Long, List<String>> bookCategories = new HashMap<>();
        if (!bookIds.isEmpty()) {
            bookRepository.findAllWithMetadataByIds(bookIds).forEach(book -> {
                List<String> categories = book.getMetadata() != null && book.getMetadata().getCategories() != null
                        ? book.getMetadata().getCategories().stream()
                        .map(CategoryEntity::getName)
                        .sorted()
                        .collect(Collectors.toList())
                        : List.of();
                bookCategories.put(book.getId(), categories);
            });
        }

        return sessionsByBook.entrySet().stream()
                .filter(entry -> entry.getValue().size() >= 2)
                .map(entry -> {
                    Long bookId = entry.getKey();
                    List<PageTurnerSessionDto> bookSessions = entry.getValue();
                    PageTurnerSessionDto first = bookSessions.getFirst();

                    List<Double> durations = bookSessions.stream()
                            .map(s -> s.getDurationSeconds() != null ? s.getDurationSeconds().doubleValue() : 0.0)
                            .collect(Collectors.toList());

                    List<Double> gaps = new ArrayList<>();
                    for (int i = 1; i < bookSessions.size(); i++) {
                        Instant prevEnd = bookSessions.get(i - 1).getEndTime();
                        Instant currStart = bookSessions.get(i).getStartTime();
                        if (prevEnd != null && currStart != null) {
                            gaps.add((double) ChronoUnit.HOURS.between(prevEnd, currStart));
                        }
                    }

                    double sessionAcceleration = linearRegressionSlope(durations);
                    double gapReduction = gaps.size() >= 2 ? linearRegressionSlope(gaps) : 0.0;

                    int totalSessions = bookSessions.size();
                    int lastQuarterStart = (int) Math.floor(totalSessions * 0.75);
                    double firstThreeQuartersAvg = durations.subList(0, lastQuarterStart).stream()
                            .mapToDouble(Double::doubleValue).average().orElse(0);
                    double lastQuarterAvg = durations.subList(lastQuarterStart, totalSessions).stream()
                            .mapToDouble(Double::doubleValue).average().orElse(0);
                    boolean finishBurst = lastQuarterAvg > firstThreeQuartersAvg;

                    double accelScore = Math.min(1.0, Math.max(0.0, (sessionAcceleration + 50) / 100.0));
                    double gapScore = Math.min(1.0, Math.max(0.0, (-gapReduction + 50) / 100.0));
                    double burstScore = finishBurst ? 1.0 : 0.0;

                    int gripScore = (int) Math.round(
                            Math.min(100, Math.max(0, accelScore * 35 + gapScore * 35 + burstScore * 30)));

                    double avgDuration = durations.stream().mapToDouble(Double::doubleValue).average().orElse(0);

                    return PageTurnerScoreResponse.builder()
                            .bookId(bookId)
                            .bookTitle(first.getBookTitle())
                            .categories(bookCategories.getOrDefault(bookId, List.of()))
                            .pageCount(first.getPageCount())
                            .personalRating(first.getPersonalRating())
                            .gripScore(gripScore)
                            .totalSessions((long) totalSessions)
                            .avgSessionDurationSeconds(Math.round(avgDuration * 100.0) / 100.0)
                            .sessionAcceleration(Math.round(sessionAcceleration * 100.0) / 100.0)
                            .gapReduction(Math.round(gapReduction * 100.0) / 100.0)
                            .finishBurst(finishBurst)
                            .build();
                })
                .sorted(Comparator.comparingInt(PageTurnerScoreResponse::getGripScore).reversed())
                .collect(Collectors.toList());
    }

    private static final int COMPLETION_RACE_BOOK_LIMIT = 10;

    @Transactional(readOnly = true)
    public List<CompletionRaceResponse> getCompletionRace(int year) {
        BookLoreUser authenticatedUser = authenticationService.getAuthenticatedUser();
        Long userId = authenticatedUser.getId();

        var allSessions = readingSessionRepository.findCompletionRaceSessionsByUserAndYear(userId, year);

        // Collect unique book IDs in order of appearance, take last N (most recently finished)
        LinkedHashSet<Long> allBookIds = allSessions.stream()
                .map(CompletionRaceSessionDto::getBookId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<Long> limitedBookIds;
        if (allBookIds.size() > COMPLETION_RACE_BOOK_LIMIT) {
            limitedBookIds = allBookIds.stream()
                    .skip(allBookIds.size() - COMPLETION_RACE_BOOK_LIMIT)
                    .collect(Collectors.toSet());
        } else {
            limitedBookIds = allBookIds;
        }

        return allSessions.stream()
                .filter(dto -> limitedBookIds.contains(dto.getBookId()))
                .map(dto -> CompletionRaceResponse.builder()
                        .bookId(dto.getBookId())
                        .bookTitle(dto.getBookTitle())
                        .sessionDate(dto.getSessionDate())
                        .endProgress(dto.getEndProgress())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ReadingSessionHeatmapResponse> getReadingDates() {
        BookLoreUser authenticatedUser = authenticationService.getAuthenticatedUser();
        Long userId = authenticatedUser.getId();

        return readingSessionRepository.findAllSessionCountsByUser(userId, getTimezoneOffset())
                .stream()
                .map(dto -> ReadingSessionHeatmapResponse.builder()
                        .date(dto.getDate())
                        .count(dto.getCount())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BookDistributionsResponse getBookDistributions() {
        BookLoreUser authenticatedUser = authenticationService.getAuthenticatedUser();
        Long userId = authenticatedUser.getId();

        // Rating distribution
        List<BookDistributionsResponse.RatingBucket> ratingBuckets = userBookProgressRepository.findRatingDistributionByUser(userId)
                .stream()
                .map(dto -> BookDistributionsResponse.RatingBucket.builder()
                        .rating(dto.getRating())
                        .count(dto.getCount())
                        .build())
                .collect(Collectors.toList());

        // Status distribution
        List<BookDistributionsResponse.StatusBucket> statusBuckets = userBookProgressRepository.findStatusDistributionByUser(userId)
                .stream()
                .map(dto -> BookDistributionsResponse.StatusBucket.builder()
                        .status(dto.getStatus().name())
                        .count(dto.getCount())
                        .build())
                .collect(Collectors.toList());

        // Progress distribution — coalesce to max across sources, then bucket
        List<ProgressPercentDto> progressRows = userBookProgressRepository.findAllProgressPercentsByUser(userId);
        long[] bucketCounts = new long[6]; // Not Started, Just Started, Getting Into It, Halfway Through, Almost Done, Completed

        for (ProgressPercentDto row : progressRows) {
            float maxPercent = maxProgress(row);
            int pct = Math.round(maxPercent * 100);
            if (pct <= 0) bucketCounts[0]++;
            else if (pct <= 25) bucketCounts[1]++;
            else if (pct <= 50) bucketCounts[2]++;
            else if (pct <= 75) bucketCounts[3]++;
            else if (pct < 100) bucketCounts[4]++;
            else bucketCounts[5]++;
        }

        String[][] bucketDefs = {
                {"Not Started", "0", "0"},
                {"Just Started", "1", "25"},
                {"Getting Into It", "26", "50"},
                {"Halfway Through", "51", "75"},
                {"Almost Done", "76", "99"},
                {"Completed", "100", "100"}
        };

        List<BookDistributionsResponse.ProgressBucket> progressBuckets = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            progressBuckets.add(BookDistributionsResponse.ProgressBucket.builder()
                    .range(bucketDefs[i][0])
                    .min(Integer.parseInt(bucketDefs[i][1]))
                    .max(Integer.parseInt(bucketDefs[i][2]))
                    .count(bucketCounts[i])
                    .build());
        }

        return BookDistributionsResponse.builder()
                .ratingDistribution(ratingBuckets)
                .progressDistribution(progressBuckets)
                .statusDistribution(statusBuckets)
                .build();
    }

    private float maxProgress(ProgressPercentDto row) {
        float max = 0f;
        if (row.getKoreaderProgressPercent() != null) max = Math.max(max, row.getKoreaderProgressPercent());
        if (row.getKoboProgressPercent() != null) max = Math.max(max, row.getKoboProgressPercent());
        if (row.getEpubProgressPercent() != null) max = Math.max(max, row.getEpubProgressPercent());
        if (row.getPdfProgressPercent() != null) max = Math.max(max, row.getPdfProgressPercent());
        if (row.getCbxProgressPercent() != null) max = Math.max(max, row.getCbxProgressPercent());
        return max;
    }

    @Transactional(readOnly = true)
    public List<SessionScatterResponse> getSessionScatter(int year) {
        BookLoreUser authenticatedUser = authenticationService.getAuthenticatedUser();
        Long userId = authenticatedUser.getId();

        return readingSessionRepository.findSessionScatterByUserAndYear(userId, year, getTimezoneOffset())
                .stream()
                .map(dto -> SessionScatterResponse.builder()
                        .hourOfDay(dto.getHourOfDay())
                        .durationMinutes(dto.getDurationMinutes())
                        .dayOfWeek(dto.getDayOfWeek())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ReadingStreakResponse getReadingStreak() {
        BookLoreUser authenticatedUser = authenticationService.getAuthenticatedUser();
        Long userId = authenticatedUser.getId();

        List<ReadingSessionCountDto> allDates = readingSessionRepository.findAllSessionCountsByUser(userId, getTimezoneOffset());
        Set<LocalDate> readingDays = allDates.stream()
                .map(ReadingSessionCountDto::getDate)
                .collect(Collectors.toCollection(TreeSet::new));

        LocalDate today = LocalDate.now();

        // Current streak: consecutive days backwards from today (allow yesterday as last active day)
        int currentStreak = 0;
        LocalDate checkDate = today;
        if (!readingDays.contains(today)) {
            // If user hasn't read today, start checking from yesterday
            checkDate = today.minusDays(1);
        }
        while (readingDays.contains(checkDate)) {
            currentStreak++;
            checkDate = checkDate.minusDays(1);
        }

        // Longest streak: find the longest consecutive run in the date set
        int longestStreak = 0;
        int streak = 0;
        LocalDate prevDate = null;
        for (LocalDate date : readingDays) {
            if (prevDate != null && date.equals(prevDate.plusDays(1))) {
                streak++;
            } else {
                streak = 1;
            }
            longestStreak = Math.max(longestStreak, streak);
            prevDate = date;
        }

        int totalReadingDays = readingDays.size();

        // Last 52 weeks: generate all dates from (today - 364 days) to today
        LocalDate startDate = today.minusDays(364);
        List<ReadingStreakResponse.ReadingStreakDay> last52Weeks = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(today); date = date.plusDays(1)) {
            last52Weeks.add(ReadingStreakResponse.ReadingStreakDay.builder()
                    .date(date)
                    .active(readingDays.contains(date))
                    .build());
        }

        return ReadingStreakResponse.builder()
                .currentStreak(currentStreak)
                .longestStreak(longestStreak)
                .totalReadingDays(totalReadingDays)
                .last52Weeks(last52Weeks)
                .build();
    }

    @Transactional(readOnly = true)
    public List<BookTimelineResponse> getBookTimeline(int year) {
        BookLoreUser authenticatedUser = authenticationService.getAuthenticatedUser();
        Long userId = authenticatedUser.getId();
        String tzOffset = getTimezoneOffset();

        ZoneId zone = ZoneId.systemDefault();

        return readingSessionRepository.findBookTimelineByUserAndYear(userId, year, tzOffset)
                .stream()
                .map(dto -> BookTimelineResponse.builder()
                        .bookId(dto.getBookId())
                        .title(dto.getTitle())
                        .pageCount(dto.getPageCount())
                        .firstSessionDate(dto.getFirstSessionDate() != null
                                ? dto.getFirstSessionDate().toLocalDate()
                                : null)
                        .lastSessionDate(dto.getLastSessionDate() != null
                                ? dto.getLastSessionDate().toLocalDate()
                                : null)
                        .totalSessions(dto.getTotalSessions())
                        .totalDurationSeconds(dto.getTotalDurationSeconds())
                        .maxProgress(dto.getMaxProgress())
                        .readStatus(dto.getReadStatus())
                        .build())
                .collect(Collectors.toList());
    }

    private double linearRegressionSlope(List<Double> values) {
        int n = values.size();
        if (n < 2) return 0.0;

        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += values.get(i);
            sumXY += i * values.get(i);
            sumX2 += (double) i * i;
        }

        double denominator = n * sumX2 - sumX * sumX;
        if (denominator == 0) return 0.0;

        return (n * sumXY - sumX * sumY) / denominator;
    }

    // ========================================================================
    // Listening (audiobook) stats
    // ========================================================================

    @Transactional(readOnly = true)
    public List<ListeningHeatmapResponse> getListeningHeatmapForMonth(int year, int month) {
        Long userId = authenticationService.getAuthenticatedUser().getId();
        return readingSessionRepository.findListeningSessionsByUserAndMonth(userId, year, month, getTimezoneOffset())
                .stream()
                .map(dto -> ListeningHeatmapResponse.builder()
                        .date(dto.getDate())
                        .sessions(dto.getSessions())
                        .durationMinutes(dto.getDurationMinutes())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<WeeklyListeningTrendResponse> getWeeklyListeningTrend(int weeks) {
        Long userId = authenticationService.getAuthenticatedUser().getId();
        return readingSessionRepository.findWeeklyListeningTrend(userId, weeks, getTimezoneOffset())
                .stream()
                .map(dto -> WeeklyListeningTrendResponse.builder()
                        .year(dto.getYear())
                        .week(dto.getWeek())
                        .totalDurationSeconds(dto.getTotalDurationSeconds())
                        .sessions(dto.getSessions())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ListeningCompletionResponse getListeningCompletion() {
        Long userId = authenticationService.getAuthenticatedUser().getId();
        var progressList = readingSessionRepository.findAudiobookProgressByUser(userId);

        int totalAudiobooks = progressList.size();
        int completed = 0;
        List<ListeningCompletionResponse.AudiobookCompletionEntry> inProgress = new ArrayList<>();

        for (var dto : progressList) {
            float maxProg = dto.getMaxProgress() != null ? dto.getMaxProgress() : 0f;
            if (maxProg >= 0.98f) {
                completed++;
            } else if (maxProg > 0f) {
                inProgress.add(ListeningCompletionResponse.AudiobookCompletionEntry.builder()
                        .bookId(dto.getBookId())
                        .title(dto.getTitle())
                        .progressPercent(Math.round(maxProg * 10.0) / 10.0)
                        .totalDurationSeconds(dto.getTotalDurationSeconds() != null ? dto.getTotalDurationSeconds() : 0L)
                        .listenedDurationSeconds(dto.getListenedDurationSeconds() != null ? dto.getListenedDurationSeconds() : 0L)
                        .build());
            }
        }

        // Sort in-progress by most recently listened (highest listened duration as proxy)
        inProgress.sort((a, b) -> Long.compare(b.getListenedDurationSeconds(), a.getListenedDurationSeconds()));

        int inProgressCount = inProgress.size();

        return ListeningCompletionResponse.builder()
                .totalAudiobooks(totalAudiobooks)
                .completed(completed)
                .inProgressCount(inProgressCount)
                .inProgress(inProgress.stream().limit(10).collect(Collectors.toList()))
                .build();
    }

    @Transactional(readOnly = true)
    public List<MonthlyPaceResponse> getMonthlyListeningPace(int months) {
        Long userId = authenticationService.getAuthenticatedUser().getId();

        // Get completed audiobooks by month
        var completedByMonth = readingSessionRepository.findMonthlyCompletedAudiobooks(userId);

        // Get listening durations by month
        var durationsByMonth = readingSessionRepository.findMonthlyListeningDurations(userId, getTimezoneOffset());
        Map<String, Long> durationMap = new HashMap<>();
        for (var durDto : durationsByMonth) {
            durationMap.put(durDto.getYear() + "-" + durDto.getMonth(), durDto.getTotalDurationSeconds());
        }

        // Merge and limit to N months
        return completedByMonth.stream()
                .limit(months)
                .map(dto -> {
                    String key = dto.getYear() + "-" + dto.getMonth();
                    Long listeningSeconds = durationMap.getOrDefault(key, 0L);
                    return MonthlyPaceResponse.builder()
                            .year(dto.getYear())
                            .month(dto.getMonth())
                            .booksCompleted(dto.getBooksCompleted())
                            .totalListeningSeconds(listeningSeconds)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ListeningFinishFunnelResponse getListeningFinishFunnel() {
        Long userId = authenticationService.getAuthenticatedUser().getId();
        var progressList = readingSessionRepository.findAudiobookProgressByUser(userId);

        long totalStarted = 0;
        long reached25 = 0;
        long reached50 = 0;
        long reached75 = 0;
        long completed = 0;

        for (var dto : progressList) {
            float maxProg = dto.getMaxProgress() != null ? dto.getMaxProgress() : 0f;
            if (maxProg > 0f) {
                totalStarted++;
                if (maxProg >= 0.25f) reached25++;
                if (maxProg >= 0.50f) reached50++;
                if (maxProg >= 0.75f) reached75++;
                if (maxProg >= 0.98f) completed++;
            }
        }

        return ListeningFinishFunnelResponse.builder()
                .totalStarted(totalStarted)
                .reached25(reached25)
                .reached50(reached50)
                .reached75(reached75)
                .completed(completed)
                .build();
    }

    @Transactional(readOnly = true)
    public List<PeakHoursResponse> getListeningPeakHours(Integer year, Integer month) {
        Long userId = authenticationService.getAuthenticatedUser().getId();
        return readingSessionRepository.findListeningPeakHoursByUser(userId, year, month, getTimezoneOffset())
                .stream()
                .map(dto -> PeakHoursResponse.builder()
                        .hourOfDay(dto.getHourOfDay())
                        .sessionCount(dto.getSessionCount())
                        .totalDurationSeconds(dto.getTotalDurationSeconds())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<GenreStatisticsResponse> getListeningGenreStatistics() {
        Long userId = authenticationService.getAuthenticatedUser().getId();
        return readingSessionRepository.findListeningGenreStatisticsByUser(userId)
                .stream()
                .map(dto -> {
                    double avgSessionsPerBook = dto.getBookCount() > 0
                            ? (double) dto.getTotalSessions() / dto.getBookCount()
                            : 0.0;

                    return GenreStatisticsResponse.builder()
                            .genre(dto.getGenre())
                            .bookCount(dto.getBookCount())
                            .totalSessions(dto.getTotalSessions())
                            .totalDurationSeconds(dto.getTotalDurationSeconds())
                            .averageSessionsPerBook(Math.round(avgSessionsPerBook * 100.0) / 100.0)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ListeningAuthorResponse> getListeningAuthorStats() {
        Long userId = authenticationService.getAuthenticatedUser().getId();
        return readingSessionRepository.findListeningAuthorStatsByUser(userId)
                .stream()
                .map(dto -> ListeningAuthorResponse.builder()
                        .author(dto.getAuthorName())
                        .bookCount(dto.getBookCount())
                        .totalSessions(dto.getTotalSessions())
                        .totalDurationSeconds(dto.getTotalDurationSeconds())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SessionScatterResponse> getListeningSessionScatter() {
        Long userId = authenticationService.getAuthenticatedUser().getId();
        return readingSessionRepository.findListeningSessionScatterByUser(userId, getTimezoneOffset())
                .stream()
                .map(dto -> SessionScatterResponse.builder()
                        .hourOfDay(dto.getHourOfDay())
                        .durationMinutes(dto.getDurationMinutes())
                        .dayOfWeek(dto.getDayOfWeek())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LongestAudiobookResponse> getListeningLongestBooks() {
        Long userId = authenticationService.getAuthenticatedUser().getId();
        return readingSessionRepository.findAudiobookProgressByUser(userId)
                .stream()
                .sorted((a, b) -> Long.compare(
                        b.getTotalDurationSeconds() != null ? b.getTotalDurationSeconds() : 0L,
                        a.getTotalDurationSeconds() != null ? a.getTotalDurationSeconds() : 0L))
                .limit(10)
                .map(dto -> {
                    float maxProg = dto.getMaxProgress() != null ? dto.getMaxProgress() : 0f;
                    return LongestAudiobookResponse.builder()
                            .bookId(dto.getBookId())
                            .title(dto.getTitle())
                            .totalDurationSeconds(dto.getTotalDurationSeconds() != null ? dto.getTotalDurationSeconds() : 0L)
                            .listenedDurationSeconds(dto.getListenedDurationSeconds() != null ? dto.getListenedDurationSeconds() : 0L)
                            .progressPercent(Math.round(maxProg * 10.0) / 10.0)
                            .build();
                })
                .collect(Collectors.toList());
    }
}
