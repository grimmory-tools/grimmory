package org.booklore.browse;

import org.booklore.exception.APIException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SortParserTest {

    private static final Set<String> KEYS = Set.of("id", "title", "seriesName", "seriesNumber");

    @Test
    void blankSortYieldsTiebreakerOnly() {
        assertThat(SortParser.parse(null, KEYS)).containsExactly(new SortTerm("id", false));
        assertThat(SortParser.parse("   ", KEYS)).containsExactly(new SortTerm("id", false));
    }

    @Test
    void parsesSingleAscendingKeyAndAppendsTiebreaker() {
        assertThat(SortParser.parse("title", KEYS))
                .containsExactly(new SortTerm("title", false), new SortTerm("id", false));
    }

    @Test
    void dashPrefixMarksDescending() {
        assertThat(SortParser.parse("-title", KEYS))
                .containsExactly(new SortTerm("title", true), new SortTerm("id", false));
    }

    @Test
    void parsesMultipleTermsInOrder() {
        assertThat(SortParser.parse("seriesName,-seriesNumber", KEYS))
                .containsExactly(new SortTerm("seriesName", false), new SortTerm("seriesNumber", true), new SortTerm("id", false));
    }

    @Test
    void trimsWhitespaceAroundTokens() {
        assertThat(SortParser.parse(" title , -seriesNumber ", KEYS))
                .containsExactly(new SortTerm("title", false), new SortTerm("seriesNumber", true), new SortTerm("id", false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "title", "-title", "seriesName,-seriesNumber"})
    void alwaysEndsWithIdAscendingTiebreaker(String sort) {
        List<SortTerm> terms = SortParser.parse(sort.isEmpty() ? null : sort, KEYS);
        assertThat(terms).endsWith(new SortTerm("id", false));
    }

    @Test
    void idIsRejectedLikeAnUnknownSortKey() {
        assertThatThrownBy(() -> SortParser.parse("id", KEYS)).isInstanceOf(APIException.class);
        assertThatThrownBy(() -> SortParser.parse("-id", KEYS)).isInstanceOf(APIException.class);
        assertThatThrownBy(() -> SortParser.parse("id,title", KEYS)).isInstanceOf(APIException.class);
    }

    @Test
    void unknownKeyIsRejected() {
        assertThatThrownBy(() -> SortParser.parse("bogus", KEYS)).isInstanceOf(APIException.class);
    }

    @Test
    void emptyTokenIsRejected() {
        assertThatThrownBy(() -> SortParser.parse("title,,id", KEYS)).isInstanceOf(APIException.class);
        assertThatThrownBy(() -> SortParser.parse("-", KEYS)).isInstanceOf(APIException.class);
    }
}
