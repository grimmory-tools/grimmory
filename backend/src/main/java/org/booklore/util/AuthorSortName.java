package org.booklore.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Derives a sortable form of an author name ("Firstname Lastname" -> "Lastname, Firstname").
 *
 * <p>Adapted from Calibre's {@code author_to_author_sort}, written by Kovid Goyal and the Calibre
 * contributors. Calibre is licensed under GPL-3.0, compatible with this project's AGPL-3.0 license.
 *
 * @see <a href="https://github.com/kovidgoyal/calibre/blob/v9.10.0/src/calibre/ebooks/metadata/__init__.py">calibre/ebooks/metadata/__init__.py</a>
 */
public final class AuthorSortName {

    public enum CopyMethod {
        // "Firstname Lastname" -> "Lastname, Firstname"
        INVERT,
        // Leave the name untouched
        COPY,
        // Invert only when the name has no comma already
        COMMA,
        // Invert without inserting a comma
        NOCOMMA
    }

    public record Config(
            CopyMethod copyMethod,
            Set<String> prefixes,
            Set<String> suffixes,
            Set<String> copyWords,
            boolean useSurnamePrefixes,
            Set<String> surnamePrefixes
    ) {
        public Config {
            prefixes = withDotVariants(prefixes);
            suffixes = withDotVariants(suffixes);
            copyWords = lowercased(copyWords);
            surnamePrefixes = lowercased(surnamePrefixes);
        }
    }

    public static final Config DEFAULT = new Config(
            CopyMethod.INVERT,
            Set.of("mr", "mrs", "ms", "dr", "prof"),
            Set.of("jr", "sr", "inc", "ph.d", "phd", "md", "m.d", "i", "ii", "iii", "iv", "junior", "senior"),
            Set.of("agency", "corporation", "council", "committee", "inc.", "institute", "national", "society", "club", "team"),
            false,
            Set.of("da", "de", "der", "den", "des", "di", "du", "la", "le", "van", "von", "ter", "ten", "vande", "vanden", "vander")
    );

    private AuthorSortName() {
    }

    public static String compute(String name) {
        return compute(name, DEFAULT);
    }

    public static String compute(String name, Config config) {
        if (name == null) {
            return null;
        }
        var trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }

        var tokens = new ArrayList<>(List.of(trimmed.split("\\s+")));
        if (tokens.size() < 2) {
            return trimmed;
        }

        var lowerTokens = new HashSet<String>();
        for (var token : tokens) {
            lowerTokens.add(token.toLowerCase(Locale.ROOT));
        }
        if (config.copyMethod() == CopyMethod.COPY || intersects(lowerTokens, config.copyWords())) {
            return trimmed;
        }

        var deque = new ArrayDeque<>(tokens);
        while (!deque.isEmpty() && config.prefixes().contains(deque.peekFirst().toLowerCase(Locale.ROOT))) {
            deque.removeFirst();
        }

        var suffix = new ArrayDeque<String>();
        while (!deque.isEmpty() && config.suffixes().contains(deque.peekLast().toLowerCase(Locale.ROOT))) {
            suffix.addFirst(deque.removeLast());
        }

        if (deque.isEmpty()) {
            return trimmed;
        }

        var remaining = new ArrayList<>(deque);
        if (config.copyMethod() == CopyMethod.COMMA && String.join(" ", remaining).contains(",")) {
            return trimmed;
        }

        // Absorb consecutive surname prefixes into the surname, e.g. "van der Berg" stays together
        while (config.useSurnamePrefixes() && remaining.size() > 1
                && config.surnamePrefixes().contains(remaining.get(remaining.size() - 2).toLowerCase(Locale.ROOT))) {
            var merged = remaining.remove(remaining.size() - 2) + " " + remaining.get(remaining.size() - 1);
            remaining.set(remaining.size() - 1, merged);
        }

        if (remaining.size() < 2) {
            // Only a surname survives after stripping prefixes/suffixes
            var single = new ArrayList<>(remaining);
            single.addAll(suffix);
            return String.join(" ", single);
        }

        var surname = remaining.remove(remaining.size() - 1);
        var result = new ArrayList<String>();
        result.add(config.copyMethod() == CopyMethod.NOCOMMA ? surname : surname + ",");
        result.addAll(remaining);
        result.addAll(suffix);
        return String.join(" ", result);
    }

    private static boolean intersects(Set<String> a, Set<String> b) {
        for (var value : a) {
            if (b.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> withDotVariants(Set<String> values) {
        var result = new HashSet<String>();
        for (var value : values) {
            var lower = value.toLowerCase(Locale.ROOT);
            result.add(lower);
            result.add(lower.endsWith(".") ? lower : lower + ".");
        }
        return Set.copyOf(result);
    }

    private static Set<String> lowercased(Set<String> values) {
        var result = new HashSet<String>();
        for (var value : values) {
            result.add(value.toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(result);
    }
}
