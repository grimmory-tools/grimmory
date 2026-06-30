package org.booklore.util;

import org.booklore.util.AuthorSortName.Config;
import org.booklore.util.AuthorSortName.CopyMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class AuthorSortNameTest {

    private static Stream<Arguments> defaultSortNames() {
        return Stream.of(
            // Simple two-token names invert to "Surname, Firstname"
            arguments("George Orwell", "Orwell, George"),
            arguments("J.R.R. Tolkien", "Tolkien, J.R.R."),
            // Multiple given names all follow the surname
            arguments("John Ronald Reuel Tolkien", "Tolkien, John Ronald Reuel"),
            // A single token is left untouched
            arguments("Plato", "Plato"),
            // Hyphenated surnames stay intact
            arguments("Mary-Kate Olsen", "Olsen, Mary-Kate"),
            // Surname prefixes stay with the given names when the feature is off (Calibre default)
            arguments("Ludwig van Beethoven", "Beethoven, Ludwig van"),
            // A trailing suffix is detached and re-appended at the end
            arguments("Martin Luther King Jr.", "King, Martin Luther Jr."),
            arguments("Jane Smith PhD", "Smith, Jane PhD"),
            // Multiple stacked suffixes are all moved to the end, in order
            arguments("Henry Williams Jr. III", "Williams, Henry Jr. III"),
            // Honorific prefix and trailing suffix are both handled
            arguments("Dr. Henry Jones Jr.", "Jones, Henry Jr."),
            // Honorific prefixes are stripped
            arguments("Dr. Seuss", "Seuss"),
            // When stripping a suffix leaves a single name token, it is re-appended (no inversion)
            arguments("Henry II", "Henry II"),
            // When every token is a prefix/suffix, the original name is returned unchanged
            arguments("Dr. Jr.", "Dr. Jr."),
            // Organisation names (copywords) are copied verbatim, case-insensitively
            arguments("National Geographic Society", "National Geographic Society"),
            arguments("national geographic society", "national geographic society"),
            // Copywords match with or without a trailing dot ("inc." set entry vs "Inc" token)
            arguments("Marvel Comics Inc", "Marvel Comics Inc")
        );
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
    @MethodSource("defaultSortNames")
    void computesDefaultSortName(String input, String expected) {
        assertThat(AuthorSortName.compute(input)).isEqualTo(expected);
    }

    @Test
    void returnsNullForNull() {
        assertThat(AuthorSortName.compute(null)).isNull();
    }

    @Test
    void returnsEmptyForBlank() {
        assertThat(AuthorSortName.compute("   ")).isEmpty();
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertThat(AuthorSortName.compute("  George   Orwell  ")).isEqualTo("Orwell, George");
    }

    @Test
    void collapsesInternalWhitespaceAndTabs() {
        assertThat(AuthorSortName.compute("Ursula\tK.  Le   Guin")).isEqualTo("Guin, Ursula K. Le");
    }

    @Test
    void keepsSurnamePrefixWithSurnameWhenEnabled() {
        Config config = surnamePrefixConfig();
        assertThat(AuthorSortName.compute("Vincent van Gogh", config)).isEqualTo("van Gogh, Vincent");
    }

    @Test
    void absorbsConsecutiveSurnamePrefixesWhenEnabled() {
        Config config = surnamePrefixConfig();
        assertThat(AuthorSortName.compute("Johannes van der Berg", config)).isEqualTo("van der Berg, Johannes");
    }

    @Test
    void copyMethodLeavesNameUntouched() {
        Config config = new Config(CopyMethod.COPY, Set.of(), Set.of(), Set.of(), false, Set.of());
        assertThat(AuthorSortName.compute("George Orwell", config)).isEqualTo("George Orwell");
    }

    @Test
    void nocommaMethodOmitsComma() {
        Config config = new Config(CopyMethod.NOCOMMA, Set.of(), Set.of(), Set.of(), false, Set.of());
        assertThat(AuthorSortName.compute("George Orwell", config)).isEqualTo("Orwell George");
    }

    @Test
    void commaMethodPreservesNamesThatAlreadyContainAComma() {
        Config config = new Config(CopyMethod.COMMA, Set.of(), Set.of(), Set.of(), false, Set.of());
        assertThat(AuthorSortName.compute("Orwell, George", config)).isEqualTo("Orwell, George");
    }

    @Test
    void commaMethodInvertsNamesWithoutAComma() {
        Config config = new Config(CopyMethod.COMMA, Set.of(), Set.of(), Set.of(), false, Set.of());
        assertThat(AuthorSortName.compute("George Orwell", config)).isEqualTo("Orwell, George");
    }

    private static Config surnamePrefixConfig() {
        return new Config(CopyMethod.INVERT, Set.of(), Set.of(), Set.of(), true, Set.of("van", "von", "de", "der"));
    }
}
