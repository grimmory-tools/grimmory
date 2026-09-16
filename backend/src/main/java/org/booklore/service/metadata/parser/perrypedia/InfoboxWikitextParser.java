package org.booklore.service.metadata.parser.perrypedia;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the first top-level {{...}} infobox template call out of a Perrypedia
 * (MediaWiki) article's wikitext.
 * <p>
 * Perrypedia's infobox template name differs per series ("Roman Zyklus 42" for
 * the classic series, "Handlungszusammenfassung Neo Staffel 38" for Neo,
 * "Handlungszusammenfassung Atlan &lt;cycle name&gt;" for Atlan), and the field
 * set differs too, so this parser deliberately does not match on template name
 * or assume a fixed field schema — it returns whatever {@code |Key = Value}
 * pairs the template call actually has.
 */
public final class InfoboxWikitextParser {

    private static final Pattern EVJ_TEMPLATE = Pattern.compile("\\{\\{\\s*EVJ\\s*\\|\\s*([^{}|]*?)\\s*\\}\\}", Pattern.CASE_INSENSITIVE);
    private static final Pattern TODO_TEMPLATE = Pattern.compile("\\{\\{\\s*todo\\s*\\}\\}", Pattern.CASE_INSENSITIVE);
    private static final Pattern REMAINING_TEMPLATE = Pattern.compile("\\{\\{[^{}]*\\}\\}");
    private static final Pattern PIPED_LINK = Pattern.compile("\\[\\[([^\\]|]*)\\|([^\\]]*)]]");
    private static final Pattern PLAIN_LINK = Pattern.compile("\\[\\[([^\\]]*)]]");

    private InfoboxWikitextParser() {
    }

    public record InfoboxBlock(String templateName, Map<String, String> fields) {
    }

    public static Optional<InfoboxBlock> parse(String wikitext) {
        if (wikitext == null) {
            return Optional.empty();
        }

        String block = extractFirstTemplateBlock(wikitext);
        if (block == null) {
            return Optional.empty();
        }

        List<String> parts = splitTopLevel(block, '|');
        if (parts.isEmpty()) {
            return Optional.empty();
        }

        String templateName = parts.getFirst().trim();
        Map<String, String> fields = new LinkedHashMap<>();
        for (int i = 1; i < parts.size(); i++) {
            String part = parts.get(i);
            int eq = indexOfTopLevel(part, '=');
            if (eq < 0) {
                continue;
            }
            String key = part.substring(0, eq).trim();
            String value = cleanValue(part.substring(eq + 1).trim());
            if (!key.isEmpty()) {
                fields.put(key, value);
            }
        }
        return Optional.of(new InfoboxBlock(templateName, fields));
    }

    /**
     * Finds the first {{...}} block via brace-depth counting (not a naive regex) since
     * infobox values contain their own nested templates, e.g. {{EVJ|2019}}.
     */
    private static String extractFirstTemplateBlock(String text) {
        int start = text.indexOf("{{");
        if (start < 0) {
            return null;
        }
        int depth = 0;
        int i = start;
        while (i < text.length() - 1) {
            if (text.startsWith("{{", i)) {
                depth++;
                i += 2;
            } else if (text.startsWith("}}", i)) {
                depth--;
                i += 2;
                if (depth == 0) {
                    return text.substring(start + 2, i - 2);
                }
            } else {
                i++;
            }
        }
        return null;
    }

    /**
     * Splits on the given delimiter, but only at nesting depth 0 — {{...}} and [[...]]
     * spans (which may themselves contain '|') are treated as opaque.
     */
    private static List<String> splitTopLevel(String text, char delimiter) {
        List<String> result = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        int i = 0;
        while (i < text.length()) {
            if (text.startsWith("{{", i) || text.startsWith("[[", i)) {
                depth++;
                current.append(text, i, i + 2);
                i += 2;
            } else if (text.startsWith("}}", i) || text.startsWith("]]", i)) {
                depth = Math.max(0, depth - 1);
                current.append(text, i, i + 2);
                i += 2;
            } else {
                char c = text.charAt(i);
                if (c == delimiter && depth == 0) {
                    result.add(current.toString());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
                i++;
            }
        }
        result.add(current.toString());
        return result;
    }

    private static int indexOfTopLevel(String text, char ch) {
        int depth = 0;
        int i = 0;
        while (i < text.length()) {
            if (text.startsWith("{{", i) || text.startsWith("[[", i)) {
                depth++;
                i += 2;
            } else if (text.startsWith("}}", i) || text.startsWith("]]", i)) {
                depth = Math.max(0, depth - 1);
                i += 2;
            } else {
                if (depth == 0 && text.charAt(i) == ch) {
                    return i;
                }
                i++;
            }
        }
        return -1;
    }

    private static String cleanValue(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw;

        // {{todo}} is Perrypedia's own "field not filled in yet" placeholder — treat as absent.
        s = TODO_TEMPLATE.matcher(s).replaceAll("");

        // {{EVJ|2019}} just wraps a year for hyperlinking — keep the year.
        Matcher evj = EVJ_TEMPLATE.matcher(s);
        s = evj.replaceAll(mr -> Matcher.quoteReplacement(mr.group(1)));

        // Any other nested template (e.g. {{Quote|...}}, {{RZJ Atlan|...}}) isn't needed
        // for the fields this provider maps — strip it rather than surface raw wikitext.
        s = REMAINING_TEMPLATE.matcher(s).replaceAll("");

        s = PIPED_LINK.matcher(s).replaceAll(mr -> Matcher.quoteReplacement(mr.group(2)));
        s = PLAIN_LINK.matcher(s).replaceAll(mr -> Matcher.quoteReplacement(mr.group(1)));

        s = s.replace("&nbsp;", " ").trim();
        return s.isEmpty() ? null : s;
    }
}
