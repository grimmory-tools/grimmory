package org.booklore.stats;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.booklore.model.enums.BookFileType;

import java.time.Instant;
import java.util.List;

public final class StatsResponses {

    private StatsResponses() {
    }

    public record Distribution(List<Count> rows,
                               @JsonInclude(JsonInclude.Include.NON_NULL) Long other,
                               @JsonInclude(JsonInclude.Include.NON_NULL) Double average) {}

    public record Count(Object value, long books) {}

    public record Rows<T>(List<T> rows) {}

    public record LengthCell(String band, int rating, long books) {}

    public record ComparisonCell(int outside, int rating, long books, double gapSum) {}

    public record DecadeCell(int decade, int rating, long books) {}

    public record BookFlow(List<YearFlow> byYear, List<RatingFlow> byRating) {}

    public record YearFlow(Integer year, String group, long books) {}

    public record RatingFlow(String group, String level, long books) {}

    public record TopName(String name, long books, List<StatusCount> statuses, Double rating) {}

    public record StatusCount(String status, long books) {}

    public record SeriesSummary(List<SeriesBar> top, long total, Double averageCompletion, long measured,
                                long completed, long inProgress, long notStarted) {}

    public record SeriesBar(String series, long books, long read, long reading, long partiallyRead,
                            long paused, long abandoned, long wontRead, long unread, Double rating) {}

    public record SeriesPage(List<SeriesRow> rows, long matching) {}

    public record SeriesRow(String series, String state, long books, long read, long reading, long intended, Double completion,
                            Double rating, String nextUnread) {}

    public record ActivityMonth(String month, long added, long finished,
                                long finishedPages,
                                long backlogChange) {}

    public record Slot(int weekday, int hour, long seconds) {}

    public record Slots(List<Slot> rows, List<Long> days) {}

    public record DayCount(String day, long sessions) {}

    public record Days(List<DayCount> rows, Streaks streaks) {}

    public record Streaks(int current, int longest, int readingDays) {}

    public record GenreTime(String genre, long seconds) {}

    public record Week(List<WeekSession> rows, boolean truncated) {}

    public record WeekSession(long bookId, String title, BookFileType format, Instant start, Instant end) {}

    public record FinishTimes(List<FinishCount> rows, Double median, FinishedBook quickest, FinishedBook longest) {}

    public record FinishCount(String band, long books, List<FinishedBook> titles) {}

    public record FinishedBook(String title, long days) {}

    public record ProfileRow(String profile, String trait, int score) {}
}
