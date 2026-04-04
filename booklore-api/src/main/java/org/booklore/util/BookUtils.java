package org.booklore.util;

import org.booklore.model.dto.Shelf;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.model.entity.BookMetadataEntity;
import lombok.experimental.UtilityClass;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

@UtilityClass
public class BookUtils {

    public static Set<Shelf> filterShelvesByUserId(Set<Shelf> shelves, Long userId) {
        if (shelves == null) return Collections.emptySet();
        return shelves.stream()
                .filter(shelf -> shelf.isPublicShelf() || userId.equals(shelf.getUserId()))
                .collect(Collectors.toSet());
    }

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern SPECIAL_CHARACTERS_PATTERN = Pattern.compile("[!@$%^&*_=|~`<>?/\"]");
    private static final Pattern DIACRITICAL_MARKS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern PARENTHESIS_PATTERN = Pattern.compile("\\s?\\([^()]*\\)");

    public static String buildSearchText(BookMetadataEntity e) {
        if (e == null) return null;
        
        StringBuilder sb = new StringBuilder(256);
        if (e.getTitle() != null) sb.append(e.getTitle()).append(" ");
        if (e.getSubtitle() != null) sb.append(e.getSubtitle()).append(" ");
        if (e.getSeriesName() != null) sb.append(e.getSeriesName()).append(" ");
        
        try {
            if (e.getAuthors() != null) {
                for (AuthorEntity author : e.getAuthors()) {
                    if (author != null && author.getName() != null) {
                        sb.append(author.getName()).append(" ");
                    }
                }
            }
        } catch (Exception ex) {
            // LazyInitializationException or similar - authors won't be included in search text
        }
        
        return normalizeForSearch(sb.toString().trim());
    }

    public static String normalizeForSearch(String term) {
        if (term == null) {
            return null;
        }
        String s = java.text.Normalizer.normalize(term, java.text.Normalizer.Form.NFD);
        s = DIACRITICAL_MARKS_PATTERN.matcher(s).replaceAll("");
        s = s.replace("ø", "o").replace("Ø", "O")
                .replace("ł", "l").replace("Ł", "L")
                .replace("æ", "ae").replace("Æ", "AE")
                .replace("œ", "oe").replace("Œ", "OE")
                .replace("ß", "ss");
        
        // Use cleanSearchTerm instead of cleanAndTruncateSearchTerm
        s = cleanSearchTerm(s);
        return s.toLowerCase();
    }

    public static String cleanFileName(String fileName) {
        String name = fileName;
        if (name == null) {
            return null;
        }
        name = name.replace("(Z-Library)", "").trim();
        
        String previous;
        do {
            previous = name;
            name = PARENTHESIS_PATTERN.matcher(name).replaceAll("").trim();
        } while (!name.equals(previous));
        
        int dotIndex = name.lastIndexOf('.'); // Remove the file extension (e.g., .pdf, .docx)
        if (dotIndex > 0) {
            name = name.substring(0, dotIndex).trim();
        }
        
        name = WHITESPACE_PATTERN.matcher(name).replaceAll(" ").trim();
        
        return name;
    }

    public static String cleanSearchTerm(String term) {
        if (term == null) {
            return "";
        }
        String s = term;
        s = SPECIAL_CHARACTERS_PATTERN.matcher(s).replaceAll("").trim();
        s = WHITESPACE_PATTERN.matcher(s).replaceAll(" ");
        return s;
    }

    public static String cleanAndTruncateSearchTerm(String term) {
        String s = cleanSearchTerm(term);
        if (s.length() > 60) {
            String[] words = WHITESPACE_PATTERN.split(s);
            if (words.length > 1) {
                StringBuilder truncated = new StringBuilder(64);
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
    
    public static String isbn10To13(String isbn10) {
        if (isbn10 == null || isbn10.length() != 10) {
            return null;
        }
        String isbn13 = "978" + isbn10.substring(0, 9);
        boolean oneThree = false;
        int total = 0;
        for (char c : isbn13.toCharArray()) {
            total += (c - '0') * (oneThree ? 3 : 1);
            oneThree = !oneThree;
        }
        int checkDigit = 10 - (total % 10);
        isbn13 += checkDigit;
        return isbn13;
    }
    
    public static String isbn13to10(String isbn13) {
        if (isbn13 == null || isbn13.length() != 13 || !"978".equals(isbn13.substring(0, 3))) {
            // Only ISBN-13s that start with "978" have an equivalent ISBN-10
            return null;
        }
        String isbn10 = isbn13.substring(3, 12);
        int mult = 10;
        int total = 0;
        for (char c : isbn10.toCharArray()) {
            total += (c - '0') * mult;
            mult--;
        }
        int checkDigit = (11 - (total % 11)) % 11;
        if (checkDigit == 10) {
            isbn10 += "X";
        } else {
            isbn10 += checkDigit;
        }
        return isbn10;
    }
}
