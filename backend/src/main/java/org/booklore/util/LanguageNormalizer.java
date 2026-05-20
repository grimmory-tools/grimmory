package org.booklore.util;

import com.neovisionaries.i18n.LanguageAlpha3Code;
import com.neovisionaries.i18n.LanguageCode;

import java.util.List;
import java.util.regex.Pattern;

public class LanguageNormalizer {

    private LanguageNormalizer() {}

    public static String normalize(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String trimmed = input.trim();

        // Handle BCP 47 / IETF tags ("fr-FR", "fr_FR") — take the primary subtag
        String primary = trimmed;
        if (trimmed.contains("-") || trimmed.contains("_")) {
            primary = trimmed.split("[-_]")[0];
        }

        String result = resolveCode(primary);
        if (result == null && !primary.equals(trimmed)) {
            result = resolveCode(trimmed);
        }

        return result != null ? result : trimmed.toLowerCase().strip();
    }

    private static String resolveCode(String input) {
        // ISO 639-1 (case-insensitive): "fr", "FR"
        LanguageCode byCode = LanguageCode.getByCode(input, false);
        if (byCode != null) {
            return byCode.name();
        }

        // ISO 639-2 alpha-3 (case-insensitive): "fre", "fra", "eng"
        LanguageAlpha3Code byAlpha3 = LanguageAlpha3Code.getByCode(input, false);
        if (byAlpha3 != null) {
            LanguageCode alpha2 = byAlpha3.getAlpha2();
            if (alpha2 != null) {
                return alpha2.name();
            }
        }

        // English name match (case-insensitive): "French", "German", etc.
        List<LanguageCode> byName = LanguageCode.findByName("(?i)" + Pattern.quote(input));
        if (!byName.isEmpty()) {
            return byName.get(0).name();
        }

        return null;
    }
}
