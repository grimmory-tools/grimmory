package org.booklore.browse;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.booklore.exception.ApiError;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public record NumericRange<T extends Comparable<? super T>>(T min, T max, boolean maxExclusive) {

    public static <T extends Comparable<? super T>> List<NumericRange<T>> parseAll(
            List<String> values, Function<String, T> parser, String name) {
        List<String> invalid = new ArrayList<>();
        List<NumericRange<T>> ranges = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            NumericRange<T> range = parse(value.trim(), parser);
            if (range == null) {
                invalid.add(value.trim());
            } else {
                ranges.add(range);
            }
        }
        if (!invalid.isEmpty()) {
            throw ApiError.INVALID_FACET.createException("Invalid " + name + " values: " + invalid
                    + ". Expected a number, min..max, min..*, *..max, or min..<max to exclude max. Minimum must not exceed maximum.");
        }
        return ranges;
    }

    public static <T extends Comparable<? super T>> Predicate anyOf(
            CriteriaBuilder cb, Expression<T> value, List<NumericRange<T>> ranges) {
        return cb.or(ranges.stream().map(range -> range.toPredicate(cb, value)).toArray(Predicate[]::new));
    }

    public Predicate toPredicate(CriteriaBuilder cb, Expression<T> value) {
        if (max == null) {
            return cb.greaterThanOrEqualTo(value, min);
        }
        Predicate upper = maxExclusive ? cb.lessThan(value, max) : cb.lessThanOrEqualTo(value, max);
        return min == null ? upper : cb.and(cb.greaterThanOrEqualTo(value, min), upper);
    }

    private static <T extends Comparable<? super T>> NumericRange<T> parse(String value, Function<String, T> parser) {
        try {
            int separator = value.indexOf("..");
            if (separator < 0) {
                T exact = parser.apply(value);
                return new NumericRange<>(exact, exact, false);
            }
            String minPart = value.substring(0, separator).trim();
            String maxPart = value.substring(separator + 2).trim();
            boolean maxExclusive = maxPart.startsWith("<");
            if (maxExclusive) {
                maxPart = maxPart.substring(1).trim();
            }
            if (maxPart.equals("*") && (maxExclusive || minPart.equals("*"))) {
                return null;
            }
            T min = minPart.equals("*") ? null : parser.apply(minPart);
            T max = maxPart.equals("*") ? null : parser.apply(maxPart);
            if (min != null && max != null && min.compareTo(max) > 0) {
                return null;
            }
            return new NumericRange<>(min, max, maxExclusive);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
