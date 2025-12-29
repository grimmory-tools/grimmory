package com.adityachandel.booklore.service;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.request.ReadingSessionRequest;
import com.adityachandel.booklore.model.dto.response.CompletionTimelineResponse;
import com.adityachandel.booklore.model.dto.response.FavoriteReadingDaysResponse;
import com.adityachandel.booklore.model.dto.response.GenreStatisticsResponse;
import com.adityachandel.booklore.model.dto.response.PeakReadingHoursResponse;
import com.adityachandel.booklore.model.dto.response.ReadingSessionHeatmapResponse;
import com.adityachandel.booklore.model.dto.response.ReadingSessionResponse;
import com.adityachandel.booklore.model.dto.response.ReadingSessionTimelineResponse;
import com.adityachandel.booklore.model.dto.response.ReadingSpeedResponse;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.model.entity.ReadingSessionEntity;
import com.adityachandel.booklore.model.enums.ReadStatus;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.ReadingSessionRepository;
import com.adityachandel.booklore.repository.UserBookProgressRepository;
import com.adityachandel.booklore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        return readingSessionRepository.findSessionCountsByUserAndYear(userId, year)
                .stream()
                .map(dto -> ReadingSessionHeatmapResponse.builder()
                        .date(dto.getDate())
                        .count(dto.getCount())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ReadingSessionTimelineResponse> getSessionTimelineForWeek(int year, int month, int week) {
        BookLoreUser authenticatedUser = authenticationService.getAuthenticatedUser();
        Long userId = authenticatedUser.getId();

        return readingSessionRepository.findSessionTimelineByUserAndWeek(userId, year, month, week)
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
    public List<PeakReadingHoursResponse> getPeakReadingHours(Integer year, Integer month) {
        BookLoreUser authenticatedUser = authenticationService.getAuthenticatedUser();
        Long userId = authenticatedUser.getId();

        return readingSessionRepository.findPeakReadingHoursByUser(userId, year, month)
                .stream()
                .map(dto -> PeakReadingHoursResponse.builder()
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

        return readingSessionRepository.findFavoriteReadingDaysByUser(userId, year, month)
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
    public Page<ReadingSessionResponse> getReadingSessionsForBook(Long bookId, int page) {
        BookLoreUser authenticatedUser = authenticationService.getAuthenticatedUser();
        Long userId = authenticatedUser.getId();

        bookRepository.findById(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        Pageable pageable = PageRequest.of(page, 5);
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
}
