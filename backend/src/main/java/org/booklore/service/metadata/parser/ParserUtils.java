package org.booklore.service.metadata.parser;

import java.util.regex.Pattern;

public class ParserUtils {

    private static final Pattern NON_ISBN_CHAR_PATTERN = Pattern.compile("[^0-9Xx]");

    public static String cleanIsbn(String isbn) {
        if (isbn == null) return null;
        String cleaned = NON_ISBN_CHAR_PATTERN.matcher(isbn).replaceAll("");
        // Normalize 'x' to 'X' for ISBN-10 check digit
        if (cleaned.length() == 10 && cleaned.endsWith("x")) {
            cleaned = cleaned.substring(0, 9) + "X";
        }
        return cleaned;
    }
}
