package org.booklore.util.koreader;

import org.booklore.util.koreader.SidecarAnnotationParser.ParsedHighlight;
import org.booklore.util.koreader.SidecarAnnotationParser.ParsedSidecar;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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

            assertThat(result).isNotNull();
            assertThat(result.username()).isEqualTo("alice");
            assertThat(result.userId()).isEqualTo(7L);
            assertThat(result.bookId()).isEqualTo(42L);
            assertThat(result.schemaVersion()).isEqualTo(1);
        }

        @Test
        void parse_schemaVersionFromComment() {
            ParsedSidecar result = SidecarAnnotationParser.parse(FULL_SIDECAR);
            assertThat(result.schemaVersion()).isEqualTo(1);
        }

        @Test
        void parse_noMeta_returnsNullFields() {
            ParsedSidecar result = SidecarAnnotationParser.parse(KOREADER_SIDECAR);

            assertThat(result).isNotNull();
            assertThat(result.username()).isNull();
            assertThat(result.userId()).isNull();
            assertThat(result.bookId()).isNull();
            assertThat(result.schemaVersion()).isNull();
        }

        @Test
        void parse_nullInput_returnsNull() {
            assertThat(SidecarAnnotationParser.parse(null)).isNull();
        }

        @Test
        void parse_blankInput_returnsNull() {
            assertThat(SidecarAnnotationParser.parse("   ")).isNull();
        }
    }

    @Nested
    class HighlightTests {

        @Test
        void parse_fullSidecar_extractsOneHighlight() {
            ParsedSidecar result = SidecarAnnotationParser.parse(FULL_SIDECAR);
            assertThat(result.highlights()).hasSize(1);
        }

        @Test
        void parse_fullSidecar_koreaderFields() {
            ParsedHighlight h = SidecarAnnotationParser.parse(FULL_SIDECAR).highlights().get(0);

            assertThat(h.pos0()).isEqualTo("/body/DocFragment[1]/body/p/text().5");
            assertThat(h.pos1()).isEqualTo("/body/DocFragment[1]/body/p/text().20");
            assertThat(h.text()).isEqualTo("highlighted text");
            assertThat(h.chapter()).isEqualTo("Chapter One");
            assertThat(h.drawer()).isEqualTo("lighten");
            assertThat(h.datetime()).isEqualTo("2024-01-15 10:30:00");
        }

        @Test
        void parse_fullSidecar_bookloreExtensionFields() {
            ParsedHighlight h = SidecarAnnotationParser.parse(FULL_SIDECAR).highlights().get(0);

            assertThat(h.bookloreId()).isEqualTo(42L);
            assertThat(h.color()).isEqualTo("#FFFF00");
            assertThat(h.style()).isEqualTo("highlight");
            assertThat(h.note()).isEqualTo("a personal note");
            assertThat(h.bookloreVersion()).isEqualTo(3L);
        }

        @Test
        void parse_koreaderSidecar_noExtensionFields() {
            ParsedHighlight h = SidecarAnnotationParser.parse(KOREADER_SIDECAR).highlights().get(0);

            assertThat(h.pos0()).isEqualTo("/body/DocFragment[2]/body/p/text().0");
            assertThat(h.pos1()).isEqualTo("/body/DocFragment[2]/body/p/text().10");
            assertThat(h.drawer()).isEqualTo("underscore");
            assertThat(h.bookloreId()).isNull();
            assertThat(h.color()).isNull();
            assertThat(h.style()).isNull();
            assertThat(h.note()).isNull();
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
            assertThat(result.highlights()).hasSize(2);
            assertThat(result.highlights().get(0).text()).isEqualTo("first");
            assertThat(result.highlights().get(1).text()).isEqualTo("second");
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
            assertThat(h.text()).isEqualTo("text with \"quotes\" and \\ backslash");
        }
    }

}
