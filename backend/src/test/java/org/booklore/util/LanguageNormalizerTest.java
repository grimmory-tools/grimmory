package org.booklore.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class LanguageNormalizerTest {

    // --- null / blank ---

    @Test
    void null_returnsNull() {
        assertNull(LanguageNormalizer.normalize(null));
    }

    @Test
    void blank_returnsNull() {
        assertNull(LanguageNormalizer.normalize("   "));
    }

    // --- ISO 639-1 passthrough ---

    @ParameterizedTest
    @CsvSource({"fr,fr", "en,en", "de,de", "es,es", "ja,ja", "zh,zh"})
    void iso6391_passthrough(String input, String expected) {
        assertEquals(expected, LanguageNormalizer.normalize(input));
    }

    @Test
    void iso6391_uppercased_lowercased() {
        assertEquals("fr", LanguageNormalizer.normalize("FR"));
    }

    // --- ISO 639-2 ---

    @ParameterizedTest
    @CsvSource({"fre,fr", "fra,fr", "eng,en", "ger,de", "deu,de", "spa,es", "ita,it", "por,pt", "jpn,ja", "zho,zh", "chi,zh"})
    void iso6392_mapsToIso6391(String input, String expected) {
        assertEquals(expected, LanguageNormalizer.normalize(input));
    }

    // --- Full English names (Amazon / GoodReads) ---

    @ParameterizedTest
    @CsvSource({"French,fr", "English,en", "German,de", "Spanish,es", "Italian,it", "Portuguese,pt", "Japanese,ja", "Chinese,zh", "Polish,pl", "Russian,ru", "Arabic,ar", "Korean,ko"})
    void englishNames_mapsToIso6391(String input, String expected) {
        assertEquals(expected, LanguageNormalizer.normalize(input));
    }

    // --- BCP 47 tags ---

    @ParameterizedTest
    @CsvSource({"fr-FR,fr", "en-US,en", "de-DE,de", "fr_FR,fr", "pt-BR,pt"})
    void bcp47Tags_mapsToIso6391(String input, String expected) {
        assertEquals(expected, LanguageNormalizer.normalize(input));
    }

    // --- Mixed case ---

    @ParameterizedTest
    @CsvSource({"FRENCH,fr", "french,fr", "FRE,fr", "Fr-FR,fr"})
    void mixedCase_normalized(String input, String expected) {
        assertEquals(expected, LanguageNormalizer.normalize(input));
    }

    // --- Unknown fallback ---

    @Test
    void unknown_returnsLowercased() {
        assertEquals("klingon", LanguageNormalizer.normalize("Klingon"));
    }

    @Test
    void unknown_code_returnsLowercased() {
        assertEquals("xx", LanguageNormalizer.normalize("XX"));
    }
}
