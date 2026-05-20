package org.booklore.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class LanguageNormalizerTest {

    @Test
    void null_returnsNull() {
        assertNull(LanguageNormalizer.normalize(null));
    }

    @ParameterizedTest
    @CsvSource({"''", "'   '"})
    void emptyOrBlank_returnsNull(String input) {
        assertNull(LanguageNormalizer.normalize(input));
    }

    @Test
    void numeric_returnsLowercasedAsIs() {
        assertEquals("123", LanguageNormalizer.normalize("123"));
    }

    @ParameterizedTest
    @CsvSource({"fr-FR,fr", "en-US,en", "pt-BR,pt", "fr_FR,fr"})
    void bcp47Tags_extractsPrimarySubtag(String input, String expected) {
        assertEquals(expected, LanguageNormalizer.normalize(input));
    }

    @ParameterizedTest
    @CsvSource({"français,fr", "anglais,en", "espagnol,es", "allemand,de", "russe,ru", "polonais,pl"})
    void frenchLocalizedNames_mapsToIso6391(String input, String expected) {
        assertEquals(expected, LanguageNormalizer.normalize(input));
    }

    @ParameterizedTest
    @CsvSource({"Französisch,fr", "Englisch,en", "Spanisch,es"})
    void germanLocalizedNames_mapsToIso6391(String input, String expected) {
        assertEquals(expected, LanguageNormalizer.normalize(input));
    }

    @ParameterizedTest
    @CsvSource({"inglés,en", "alemán,de", "francés,fr"})
    void spanishLocalizedNames_mapsToIso6391(String input, String expected) {
        assertEquals(expected, LanguageNormalizer.normalize(input));
    }

    @Test
    void unknown_returnsLowercasedAsIs() {
        assertEquals("klingon", LanguageNormalizer.normalize("Klingon"));
    }
}
