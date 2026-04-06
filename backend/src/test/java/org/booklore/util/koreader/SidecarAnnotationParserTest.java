package org.booklore.util.koreader;

import org.booklore.util.koreader.SidecarAnnotationParser.ParsedHighlight;
import org.booklore.util.koreader.SidecarAnnotationParser.ParsedSidecar;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class SidecarAnnotationParserTest {

    // Full Grimmory-written sidecar with booklore_meta and booklore entry block
    private static final String FULL_SIDECAR = """
            -- KOReader bookmark file
            -- version: 1
            -- booklore:schema_version=1
            {
                ["booklore_meta"] = {
                    ["username"] = "alice",
                    ["user_id"] = 7,
                    ["book_id"] = 42,
                    ["exported_at"] = "2024-01-15 10:30:00",
                    ["schema_version"] = 1,
                },
                ["highlights"] = {
                    [1] = {
                        ["chapter"] = "Chapter One",
                        ["datetime"] = "2024-01-15 10:30:00",
                        ["drawer"] = "lighten",
                        ["notes"] = "highlighted text",
                        ["page"] = "/body/DocFragment[1]/body/p",
                        ["pos0"] = "/body/DocFragment[1]/body/p/text().5",
                        ["pos1"] = "/body/DocFragment[1]/body/p/text().20",
                        ["booklore"] = {
                            ["id"] = 42,
                            ["color"] = "#FFFF00",
                            ["style"] = "highlight",
                            ["note"] = "a personal note",
                            ["version"] = 3,
                        },
                    },
                },
                ["bookmarks"] = {},
            }
            """;

    // Sidecar without booklore blocks — as if written by KOReader itself
    private static final String KOREADER_SIDECAR = """
            -- KOReader bookmark file
            -- version: 1
            {
                ["highlights"] = {
                    [1] = {
                        ["chapter"] = "Intro",
                        ["datetime"] = "2024-03-01 08:00:00",
                        ["drawer"] = "underscore",
                        ["notes"] = "some text",
                        ["page"] = "/body/DocFragment[2]/body/p",
                        ["pos0"] = "/body/DocFragment[2]/body/p/text().0",
                        ["pos1"] = "/body/DocFragment[2]/body/p/text().10",
                    },
                },
                ["bookmarks"] = {},
            }
            """;

    @Nested
    class MetadataTests {

        @Test
        void parse_fullSidecar_extractsMeta() {
            ParsedSidecar result = SidecarAnnotationParser.parse(FULL_SIDECAR);

            assertNotNull(result);
            assertEquals("alice", result.username());
            assertEquals(7L, result.userId());
            assertEquals(42L, result.bookId());
            assertEquals(1, result.schemaVersion());
        }

        @Test
        void parse_schemaVersionFromComment() {
            ParsedSidecar result = SidecarAnnotationParser.parse(FULL_SIDECAR);
            assertEquals(1, result.schemaVersion());
        }

        @Test
        void parse_noMeta_returnsNullFields() {
            ParsedSidecar result = SidecarAnnotationParser.parse(KOREADER_SIDECAR);

            assertNotNull(result);
            assertNull(result.username());
            assertNull(result.userId());
            assertNull(result.bookId());
            assertNull(result.schemaVersion());
        }

        @Test
        void parse_nullInput_returnsNull() {
            assertNull(SidecarAnnotationParser.parse(null));
        }

        @Test
        void parse_blankInput_returnsNull() {
            assertNull(SidecarAnnotationParser.parse("   "));
        }
    }

    @Nested
    class HighlightTests {

        @Test
        void parse_fullSidecar_extractsOneHighlight() {
            ParsedSidecar result = SidecarAnnotationParser.parse(FULL_SIDECAR);
            assertEquals(1, result.highlights().size());
        }

        @Test
        void parse_fullSidecar_koreaderFields() {
            ParsedHighlight h = SidecarAnnotationParser.parse(FULL_SIDECAR).highlights().get(0);

            assertEquals("/body/DocFragment[1]/body/p/text().5", h.pos0());
            assertEquals("/body/DocFragment[1]/body/p/text().20", h.pos1());
            assertEquals("highlighted text", h.text());
            assertEquals("Chapter One", h.chapter());
            assertEquals("lighten", h.drawer());
            assertEquals("2024-01-15 10:30:00", h.datetime());
        }

        @Test
        void parse_fullSidecar_bookloreExtensionFields() {
            ParsedHighlight h = SidecarAnnotationParser.parse(FULL_SIDECAR).highlights().get(0);

            assertEquals(42L, h.bookloreId());
            assertEquals("#FFFF00", h.color());
            assertEquals("highlight", h.style());
            assertEquals("a personal note", h.note());
            assertEquals(3L, h.bookloreVersion());
        }

        @Test
        void parse_koreaderSidecar_noExtensionFields() {
            ParsedHighlight h = SidecarAnnotationParser.parse(KOREADER_SIDECAR).highlights().get(0);

            assertEquals("/body/DocFragment[2]/body/p/text().0", h.pos0());
            assertEquals("/body/DocFragment[2]/body/p/text().10", h.pos1());
            assertEquals("underscore", h.drawer());
            assertNull(h.bookloreId());
            assertNull(h.color());
            assertNull(h.style());
            assertNull(h.note());
        }

        @Test
        void parse_multipleHighlights_allExtracted() {
            String lua = """
                    {
                        ["highlights"] = {
                            [1] = {
                                ["pos0"] = "/body/DocFragment[1]/body/p/text().0",
                                ["pos1"] = "/body/DocFragment[1]/body/p/text().5",
                                ["notes"] = "first",
                                ["drawer"] = "lighten",
                            },
                            [2] = {
                                ["pos0"] = "/body/DocFragment[2]/body/p/text().0",
                                ["pos1"] = "/body/DocFragment[2]/body/p/text().8",
                                ["notes"] = "second",
                                ["drawer"] = "underscore",
                            },
                        },
                        ["bookmarks"] = {},
                    }
                    """;

            ParsedSidecar result = SidecarAnnotationParser.parse(lua);
            assertEquals(2, result.highlights().size());
            assertEquals("first", result.highlights().get(0).text());
            assertEquals("second", result.highlights().get(1).text());
        }

        @Test
        void parse_emptyHighlights_returnsEmptyList() {
            String lua = """
                    {
                        ["highlights"] = {
                        },
                        ["bookmarks"] = {},
                    }
                    """;

            ParsedSidecar result = SidecarAnnotationParser.parse(lua);
            assertThat(result.highlights()).isEmpty();
        }

        @Test
        void parse_escapedStrings_unescapedCorrectly() {
            String lua = """
                    {
                        ["highlights"] = {
                            [1] = {
                                ["pos0"] = "/body/p/text().0",
                                ["pos1"] = "/body/p/text().5",
                                ["notes"] = "text with \\"quotes\\" and \\\\ backslash",
                                ["drawer"] = "lighten",
                            },
                        },
                        ["bookmarks"] = {},
                    }
                    """;

            ParsedHighlight h = SidecarAnnotationParser.parse(lua).highlights().get(0);
            assertEquals("text with \"quotes\" and \\ backslash", h.text());
        }
    }

}
