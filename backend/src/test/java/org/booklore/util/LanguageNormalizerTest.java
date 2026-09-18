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
    @CsvSource({"fr-FR,fr-FR", "en-US,en-US", "pt-BR,pt-BR", "fr_FR,fr-FR", "en-us,en-US", "PT-br,pt-BR"})
    void bcp47Tags_preservesRegionWithCanonicalCasing(String input, String expected) {
        assertEquals(expected, LanguageNormalizer.normalize(input));
    }

    @ParameterizedTest
    @CsvSource({"fre-FR,fr-FR", "eng_US,en-US", "por-BR,pt-BR"})
    void alpha3PrimarySubtag_normalizedToAlpha2KeepingRegion(String input, String expected) {
        assertEquals(expected, LanguageNormalizer.normalize(input));
    }

    @ParameterizedTest
    @CsvSource({"zh-hant,zh-Hant", "zh-hans-cn,zh-Hans-CN", "sr-latn,sr-Latn"})
    void scriptSubtags_canonicalCasing(String input, String expected) {
        assertEquals(expected, LanguageNormalizer.normalize(input));
    }

    @ParameterizedTest
    @CsvSource({"en,en", "fr,fr", "eng,en", "fre,fr", "English,en", "French,fr"})
    void bareCodesAndNames_resolveToAlpha2WithoutRegion(String input, String expected) {
        assertEquals(expected, LanguageNormalizer.normalize(input));
    }

    @Test
    void illFormedSubtags_fallBackToPrimaryLanguage() {
        assertEquals("en", LanguageNormalizer.normalize("en-notarealsubtag"));
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
