package org.booklore.util;

import com.neovisionaries.i18n.LanguageAlpha3Code;
import com.neovisionaries.i18n.LanguageCode;
import lombok.experimental.UtilityClass;

import java.util.Arrays;
import java.util.HashMap;
import java.util.IllformedLocaleException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@UtilityClass
public class LanguageNormalizer {

    private static final Pattern BCP47_SEPARATOR = Pattern.compile("[-_]");

    public String normalize(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String trimmed = input.trim();

        String[] subtags = BCP47_SEPARATOR.split(trimmed);
        String primary = resolveCode(subtags[0]);

        if (primary == null) {
            if (subtags.length > 1) {
                String byFullName = resolveCode(trimmed);
                if (byFullName != null) {
                    return byFullName;
                }
            }
            return trimmed.toLowerCase(Locale.ROOT);
        }

        if (subtags.length == 1) {
            return primary;
        }
        return canonicalizeTag(primary, subtags);
    }

    private String canonicalizeTag(String primary, String[] subtags) {
        String tag = primary + "-" + String.join("-", Arrays.copyOfRange(subtags, 1, subtags.length));
        try {
            return new Locale.Builder().setLanguageTag(tag).build().toLanguageTag();
        } catch (IllformedLocaleException e) {
            return primary;
        }
    }

    private String resolveCode(String input) {
        LanguageCode byCode = LanguageCode.getByCode(input, false);
        if (byCode != null) return byCode.name();

        LanguageAlpha3Code byAlpha3 = LanguageAlpha3Code.getByCode(input, false);
        if (byAlpha3 != null) {
            LanguageCode alpha2 = byAlpha3.getAlpha2();
            if (alpha2 != null) return alpha2.name();
        }

        List<LanguageCode> byName = LanguageCode.findByName("(?i)" + Pattern.quote(input));
        if (!byName.isEmpty()) return byName.getFirst().name();

        return LocalizedNameMap.MAP.get(input.toLowerCase(Locale.ROOT).trim());
    }

    private static final class LocalizedNameMap {
        static final Map<String, String> MAP = build();

        private static Map<String, String> build() {
            Locale[] displayLocales = {
                Locale.of("fr"), Locale.of("de"), Locale.of("it"), Locale.of("ja"),
                Locale.of("es"), Locale.of("pt"), Locale.of("nl"),
                Locale.of("ru"), Locale.of("pl")
            };
            Map<String, String> map = new HashMap<>();
            for (LanguageCode code : LanguageCode.values()) {
                if (code == LanguageCode.undefined) continue;
                Locale locale = Locale.of(code.name());
                for (Locale displayLocale : displayLocales) {
                    String displayName = locale.getDisplayLanguage(displayLocale).toLowerCase(Locale.ROOT);
                    if (displayName.length() > 2) {
                        map.putIfAbsent(displayName, code.name());
                    }
                }
            }
            return Map.copyOf(map);
        }
    }
}
