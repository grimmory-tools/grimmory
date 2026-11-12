package org.booklore.browse;

import org.booklore.exception.APIException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NumericRangeTest {

    @Test
    void parsesEveryRangeForm() {
        assertThat(NumericRange.parseAll(List.of("4", "2..10", "5..*", "*..3", "4..<4.5"), Double::valueOf, "rating"))
                .containsExactly(
                        new NumericRange<>(4.0, 4.0, false),
                        new NumericRange<>(2.0, 10.0, false),
                        new NumericRange<>(5.0, null, false),
                        new NumericRange<>(null, 3.0, false),
                        new NumericRange<>(4.0, 4.5, true));
    }

    @Test
    void skipsBlankValues() {
        assertThat(NumericRange.parseAll(Arrays.asList("", " ", null), Long::valueOf, "count")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "5..2", "*..*", "5..<*", "2-10"})
    void rejectsInvalidValuesAsBadRequest(String value) {
        assertThatThrownBy(() -> NumericRange.parseAll(List.of(value), Long::valueOf, "count"))
                .isInstanceOfSatisfying(APIException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("Invalid count values: [" + value + "]");
    }
}
