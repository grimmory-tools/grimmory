package org.booklore.util;

import lombok.experimental.UtilityClass;

import java.text.Normalizer;

/**
 * Utility for Unicode path normalization, ensuring consistent NFC form
 * across macOS (NFD), Linux (NFC), and web uploads.
 *
 * <p>This prevents lookup mismatches and constraint violations when filenames
 * contain non-ASCII characters such as German umlauts, accents, or CJK glyphs.</p>
 */
@UtilityClass
public class PathNormalizer {

    /**
     * Normalize a raw path string to NFC (precomposed) Unicode form.
     * This guarantees that umlauts ('ä', 'ö', 'ü', 'ß', 'é') and other
     * diacritics match consistently across OS, DB, and web APIs.
     *
     * @param rawPath the raw path string (may be null)
     * @return NFC-normalized string, or null if input was null
     */
    public static String normalizePathString(String rawPath) {
        if (rawPath == null) return null;
        return Normalizer.normalize(rawPath, Normalizer.Form.NFC);
    }

    /**
     * Normalize a filename to NFC form and strip non-printable control characters.
     *
     * @param originalFilename the original filename (may be null)
     * @return cleaned NFC-normalized filename, or null if input was null
     */
    public static String normalizeFileName(String originalFilename) {
        if (originalFilename == null) return null;
        String normalized = Normalizer.normalize(originalFilename, Normalizer.Form.NFC);
        return normalized.replaceAll("[\\p{Cntrl}]", "");
    }
}
