package org.booklore.stats;

import org.booklore.exception.APIException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PeriodTest {

    private static final ZoneId AUCKLAND = ZoneId.of("Pacific/Auckland");

    @Test
    void parsesAllYearMonthAndIsoWeekInTheZone() {
        assertThat(Period.parse("all", AUCKLAND)).isEqualTo(Period.ALL_TIME);
        assertThat(Period.parse("2026", ZoneOffset.UTC))
                .isEqualTo(new Period(Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2027-01-01T00:00:00Z")));
        assertThat(Period.parse("2026-03", AUCKLAND))
                .isEqualTo(new Period(Instant.parse("2026-02-28T11:00:00Z"), Instant.parse("2026-03-31T11:00:00Z")));
        assertThat(Period.parse("2026-W01", ZoneOffset.UTC))
                .isEqualTo(new Period(Instant.parse("2025-12-29T00:00:00Z"), Instant.parse("2026-01-05T00:00:00Z")));
        assertThat(Period.parseWeek("2026-W53", ZoneOffset.UTC).from()).isEqualTo(Instant.parse("2026-12-28T00:00:00Z"));
    }

    @Test
    void anythingElseIsABadRequest() {
        for (Runnable parse : List.<Runnable>of(() -> Period.parse("spring", AUCKLAND), () -> Period.parseYear("2026-03", AUCKLAND),
                () -> Period.parseWeek("2025-W53", AUCKLAND), () -> new StatsQuery("Mars/Olympus_Mons", null, null, null).zone())) {
            assertThatThrownBy(parse::run).isInstanceOfSatisfying(APIException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
    }
}
