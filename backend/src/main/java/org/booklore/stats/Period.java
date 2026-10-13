package org.booklore.stats;

import org.booklore.exception.APIException;
import org.booklore.exception.ApiError;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

record Period(Instant from, Instant to) {

    static final Period ALL_TIME = new Period(null, null);

    private static final Pattern YEAR = Pattern.compile("(\\d{4})");
    private static final Pattern MONTH = Pattern.compile("(\\d{4})-(\\d{2})");
    private static final Pattern WEEK = Pattern.compile("(\\d{4})-W(\\d{2})");

    static Period parse(String text, ZoneId zone) {
        String value = text.trim();
        if (value.equals("all")) {
            return ALL_TIME;
        }
        Matcher match;
        if ((match = YEAR.matcher(value)).matches()) {
            return year(zone, number(match, 1));
        }
        if ((match = MONTH.matcher(value)).matches()) {
            return month(zone, number(match, 1), number(match, 2));
        }
        if ((match = WEEK.matcher(value)).matches()) {
            return isoWeek(zone, number(match, 1), number(match, 2));
        }
        throw invalid("Period must be all, YYYY, YYYY-MM or YYYY-Www: " + value);
    }

    static Period parseYear(String text, ZoneId zone) {
        if (!YEAR.matcher(text.trim()).matches()) {
            throw invalid("Year must be YYYY: " + text);
        }
        return parse(text, zone);
    }

    static Period parseWeek(String text, ZoneId zone) {
        if (!WEEK.matcher(text.trim()).matches()) {
            throw invalid("Period must be an ISO week, YYYY-Www: " + text);
        }
        return parse(text, zone);
    }

    private static Period year(ZoneId zone, int year) {
        LocalDate first = LocalDate.of(checkYear(year), 1, 1);
        return between(zone, first, first.plusYears(1));
    }

    private static Period month(ZoneId zone, int year, int month) {
        if (month < 1 || month > 12) {
            throw invalid("Month must be between 1 and 12");
        }
        LocalDate first = LocalDate.of(checkYear(year), month, 1);
        return between(zone, first, first.plusMonths(1));
    }

    private static Period isoWeek(ZoneId zone, int weekYear, int week) {
        LocalDate midYear = LocalDate.of(checkYear(weekYear), 6, 1);
        if (!IsoFields.WEEK_OF_WEEK_BASED_YEAR.rangeRefinedBy(midYear).isValidValue(week)) {
            throw invalid("Week " + week + " does not exist in " + weekYear);
        }
        LocalDate monday = midYear.with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week).with(DayOfWeek.MONDAY);
        return between(zone, monday, monday.plusWeeks(1));
    }

    private static Period between(ZoneId zone, LocalDate first, LocalDate end) {
        return new Period(first.atStartOfDay(zone).toInstant(), end.atStartOfDay(zone).toInstant());
    }

    private static int checkYear(int year) {
        if (year < 1 || year > 9999) {
            throw invalid("Year must be between 1 and 9999");
        }
        return year;
    }

    private static int number(Matcher match, int group) {
        return Integer.parseInt(match.group(group));
    }

    private static APIException invalid(String message) {
        return ApiError.INVALID_INPUT.createException(message);
    }
}
