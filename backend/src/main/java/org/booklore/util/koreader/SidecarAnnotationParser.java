package org.booklore.util.koreader;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Grimmory's KOReader-format annotation sidecar files back into structured data.
 *
 * <p>Understands both the KOReader fields ({@code pos0}, {@code pos1}, {@code notes}, etc.)
 * and the Grimmory {@code booklore_meta} / per-entry {@code booklore} extension blocks that
 * enable lossless round-trip import.
 *
 * <p>Files written by KOReader itself (without a {@code booklore} block) are parsed
 * gracefully — the KOReader fields are used as-is and the extension fields are null.
 */
@Slf4j
public class SidecarAnnotationParser {

    private static final Pattern SCHEMA_COMMENT =
            Pattern.compile("--\\s*booklore:schema_version=(\\d+)");
    private static final Pattern INDEXED_ENTRY_HEADER =
            Pattern.compile("\\[\\d+]\\s*=\\s*\\{");
    private static final Pattern STRING_FIELD =
            Pattern.compile("\\[\"(\\w+)\"]\\s*=\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern NUMBER_FIELD =
            Pattern.compile("\\[\"(\\w+)\"]\\s*=\\s*(-?\\d+)");

    public record ParsedSidecar(
            String username,
            Long userId,
            Long bookId,
            Integer schemaVersion,
            List<ParsedHighlight> highlights
    ) {}

    public record ParsedHighlight(
            String pos0,
            String pos1,
            String text,       // highlighted text — KOReader "notes" field
            String chapter,
            String datetime,
            String drawer,     // KOReader drawer value (lighten/underscore/strikeout)
            // Grimmory extension fields (null when parsed from a native KOReader sidecar)
            Long bookloreId,
            String color,
            String style,      // original Grimmory style — preferred over drawer when present
            String note,       // user's comment — distinct from highlighted text
            Long bookloreVersion
    ) {}

    public static ParsedSidecar parse(String lua) {
        if (lua == null || lua.isBlank()) {
            return null;
        }

        Integer schemaVersion = extractSchemaVersionFromComment(lua);

        String metaBlock = extractNamedBlock(lua, "booklore_meta");
        String username = null;
        Long userId = null;
        Long bookId = null;

        if (metaBlock != null) {
            Map<String, String> strings = extractStringFields(metaBlock);
            Map<String, Long> numbers = extractNumberFields(metaBlock);
            username = strings.get("username");
            userId = numbers.get("user_id");
            bookId = numbers.get("book_id");
            if (schemaVersion == null) {
                Long sv = numbers.get("schema_version");
                schemaVersion = sv != null ? sv.intValue() : null;
            }
        }

        List<ParsedHighlight> highlights = new ArrayList<>();
        String highlightsBlock = extractNamedBlock(lua, "highlights");
        if (highlightsBlock != null) {
            for (String entryBlock : extractIndexedBlocks(highlightsBlock)) {
                try {
                    ParsedHighlight h = parseHighlight(entryBlock);
                    if (h != null) {
                        highlights.add(h);
                    }
                } catch (Exception e) {
                    log.warn("Skipping unparseable highlight entry: {}", e.getMessage());
                }
            }
        }

        return new ParsedSidecar(username, userId, bookId, schemaVersion, highlights);
    }

    private static ParsedHighlight parseHighlight(String block) {
        // Field names don't collide between KOReader and booklore blocks, so
        // parsing the full entry (including nested booklore sub-block) with flat
        // regex is safe and avoids the need to strip the sub-block first.
        Map<String, String> strings = extractStringFields(block);
        Map<String, Long> numbers = extractNumberFields(block);

        String pos0 = strings.get("pos0");
        String pos1 = strings.get("pos1");
        if (pos0 == null || pos1 == null) {
            return null;
        }

        return new ParsedHighlight(
                pos0,
                pos1,
                strings.get("notes"),
                strings.get("chapter"),
                strings.get("datetime"),
                strings.get("drawer"),
                numbers.get("id"),
                strings.get("color"),
                strings.get("style"),
                strings.get("note"),
                numbers.get("version")
        );
    }

    /**
     * Finds {@code ["key"] = { ... }} in {@code content} and returns the text
     * inside the braces, handling arbitrary nesting depth.
     */
    static String extractNamedBlock(String content, String key) {
        String marker = "[\"" + key + "\"]";
        int markerPos = content.indexOf(marker);
        if (markerPos < 0) {
            return null;
        }
        int bracePos = content.indexOf('{', markerPos + marker.length());
        if (bracePos < 0) {
            return null;
        }
        return extractBlockFrom(content, bracePos);
    }

    /**
     * Extracts all {@code [N] = { ... }} blocks from an array-like Lua table body.
     */
    private static List<String> extractIndexedBlocks(String content) {
        List<String> result = new ArrayList<>();
        Matcher m = INDEXED_ENTRY_HEADER.matcher(content);
        int searchFrom = 0;
        while (m.find(searchFrom)) {
            // The pattern ends with '{', so the brace is at m.end() - 1
            int bracePos = m.end() - 1;
            String block = extractBlockFrom(content, bracePos);
            if (block != null) {
                result.add(block);
                searchFrom = bracePos + block.length() + 2; // skip past closing '}'
            } else {
                searchFrom = m.end();
            }
        }
        return result;
    }

    /**
     * Returns the content INSIDE the matching braces starting at {@code braceStart}.
     * Returns null if the braces are unbalanced.
     */
    private static String extractBlockFrom(String content, int braceStart) {
        int depth = 0;
        for (int i = braceStart; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return content.substring(braceStart + 1, i);
                }
            }
        }
        return null;
    }

    private static Map<String, String> extractStringFields(String block) {
        Map<String, String> result = new HashMap<>();
        Matcher m = STRING_FIELD.matcher(block);
        while (m.find()) {
            // Unescape Lua escape sequences we produce: \\ and \"
            String value = m.group(2).replace("\\\"", "\"").replace("\\\\", "\\");
            result.put(m.group(1), value);
        }
        return result;
    }

    private static Map<String, Long> extractNumberFields(String block) {
        Map<String, Long> result = new HashMap<>();
        Matcher m = NUMBER_FIELD.matcher(block);
        while (m.find()) {
            result.put(m.group(1), Long.parseLong(m.group(2)));
        }
        return result;
    }

    private static Integer extractSchemaVersionFromComment(String lua) {
        Matcher m = SCHEMA_COMMENT.matcher(lua);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }
}
