package org.booklore.browse;

import org.booklore.exception.APIException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SortParserTest {

    private static final Set<String> KEYS = Set.of("id", "title", "seriesName", "seriesNumber");

    @Test
    void blankSortYieldsTiebreakerOnly() {
        assertEquals(List.of(new SortTerm("id", false)), SortParser.parse(null, KEYS));
        assertEquals(List.of(new SortTerm("id", false)), SortParser.parse("   ", KEYS));
    }

    @Test
    void parsesSingleAscendingKeyAndAppendsTiebreaker() {
        List<SortTerm> terms = SortParser.parse("title", KEYS);
        assertEquals(List.of(new SortTerm("title", false), new SortTerm("id", false)), terms);
    }

    @Test
    void dashPrefixMarksDescending() {
        List<SortTerm> terms = SortParser.parse("-title", KEYS);
        assertEquals(List.of(new SortTerm("title", true), new SortTerm("id", false)), terms);
    }

    @Test
    void parsesMultipleTermsInOrder() {
        List<SortTerm> terms = SortParser.parse("seriesName,-seriesNumber", KEYS);
        assertEquals(
                List.of(new SortTerm("seriesName", false), new SortTerm("seriesNumber", true), new SortTerm("id", false)),
                terms);
    }

    @Test
    void trimsWhitespaceAroundTokens() {
        List<SortTerm> terms = SortParser.parse(" title , -seriesNumber ", KEYS);
        assertEquals(
                List.of(new SortTerm("title", false), new SortTerm("seriesNumber", true), new SortTerm("id", false)),
                terms);
    }

    @Test
    void explicitTiebreakerKeyIsNotDuplicated() {
        assertEquals(List.of(new SortTerm("id", false)), SortParser.parse("id", KEYS));
        assertEquals(List.of(new SortTerm("id", true)), SortParser.parse("-id", KEYS));
    }

    @Test
    void explicitTiebreakerMidListSuppressesAppendedOne() {
        List<SortTerm> terms = SortParser.parse("id,title", KEYS);
        assertEquals(List.of(new SortTerm("id", false), new SortTerm("title", false)), terms);
    }

    @Test
    void unknownKeyIsRejected() {
        assertThrows(APIException.class, () -> SortParser.parse("bogus", KEYS));
    }

    @Test
    void emptyTokenIsRejected() {
        assertThrows(APIException.class, () -> SortParser.parse("title,,id", KEYS));
        assertThrows(APIException.class, () -> SortParser.parse("-", KEYS));
    }
}
