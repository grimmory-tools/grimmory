package com.adityachandel.booklore.util;

import lombok.experimental.UtilityClass;

import java.util.regex.Pattern;

@UtilityClass
public class BookUtils {

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern SPECIAL_CHARACTERS_PATTERN = Pattern.compile("[.,\\-\\[\\]{}()!@#$%^&*_=+|~`<>?/\";:]");
    private static final Pattern PARENTHESES_WITH_OPTIONAL_SPACE_PATTERN = Pattern.compile("\\s?\\(.*?\\)");

    public static String cleanFileName(String fileName) {
        String name = fileName;
        if (name == null) {
            return null;
        }
        name = name.replace("(Z-Library)", "").trim();
        name = PARENTHESES_WITH_OPTIONAL_SPACE_PATTERN.matcher(name).replaceAll("").trim(); // Remove the author name inside parentheses (e.g. (Jon Yablonski))
        int dotIndex = name.lastIndexOf('.'); // Remove the file extension (e.g., .pdf, .docx)
        if (dotIndex > 0) {
            name = name.substring(0, dotIndex).trim();
        }
        return name;
    }

    public static String cleanAndTruncateSearchTerm(String term) {
        if (term == null) {
            return "";
        }
        String s = term;
        s = SPECIAL_CHARACTERS_PATTERN.matcher(s).replaceAll("").trim();
        s = WHITESPACE_PATTERN.matcher(s).replaceAll(" ");
        if (s.length() > 60) {
            String[] words = WHITESPACE_PATTERN.split(s);
            if (words.length > 1) {
                StringBuilder truncated = new StringBuilder();
                for (String word : words) {
                    if (truncated.length() + word.length() + 1 > 60) break;
                    if (!truncated.isEmpty()) truncated.append(" ");
                    truncated.append(word);
                }
                s = truncated.toString();
            } else {
                s = s.substring(0, Math.min(60, s.length()));
            }
        }
        return s;
    }
}
