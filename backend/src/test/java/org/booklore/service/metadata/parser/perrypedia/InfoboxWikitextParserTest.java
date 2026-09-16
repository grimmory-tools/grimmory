package org.booklore.service.metadata.parser.perrypedia;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers all three Perrypedia series families the parser has to handle, since
 * both the infobox template name and its field set differ per series — a
 * test against only one series would not catch a regression in the other
 * two.
 */
class InfoboxWikitextParserTest {

    private String readFixture(String name) throws IOException {
        String filename = "perrypedia/" + name + ".fixture";
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(filename)) {
            assert is != null;
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void parse_ClassicSeries_MythosErde() throws IOException {
        Optional<InfoboxWikitextParser.InfoboxBlock> result = InfoboxWikitextParser.parse(readFixture("mythos_erde"));

        assertThat(result).isPresent();
        InfoboxWikitextParser.InfoboxBlock block = result.get();
        assertThat(block.templateName()).isEqualTo("Roman Zyklus 42");

        Map<String, String> fields = block.fields();
        assertThat(fields.get("Nummer")).isEqualTo("3000");
        assertThat(fields.get("Titel")).isEqualTo("Mythos Erde");
        assertThat(fields.get("Untertitel")).isEqualTo("Die Zeit verändert alles");
        assertThat(fields.get("Autor")).isEqualTo("Christian Montillon, Wim Vandemaan");
        assertThat(fields.get("Erscheinungsdatum")).isEqualTo("Freitag, 15. Februar 2019");
    }

    @Test
    void parse_NeoSeries_WennSterneBluten() throws IOException {
        Optional<InfoboxWikitextParser.InfoboxBlock> result = InfoboxWikitextParser.parse(readFixture("wenn_sterne_bluten"));

        assertThat(result).isPresent();
        InfoboxWikitextParser.InfoboxBlock block = result.get();
        assertThat(block.templateName()).isEqualTo("Handlungszusammenfassung Neo Staffel 38");

        Map<String, String> fields = block.fields();
        assertThat(fields.get("Nummer")).isEqualTo("389");
        assertThat(fields.get("Titel")).isEqualTo("Wenn Sterne bluten");
        assertThat(fields.get("Autor")).isEqualTo("Rüdiger Schäfer");
        assertThat(fields.get("Erscheinungsdatum")).isEqualTo("Freitag, 14. August 2026");
        // {{todo}} is Perrypedia's own "not filled in yet" placeholder — must clean to null, not literal text.
        assertThat(fields.get("Handlungszeitraum")).isNull();
        assertThat(fields.get("Handlungsort")).isNull();
    }

    @Test
    void parse_AtlanSeries_DieZeitfestung() throws IOException {
        Optional<InfoboxWikitextParser.InfoboxBlock> result = InfoboxWikitextParser.parse(readFixture("die_zeitfestung"));

        assertThat(result).isPresent();
        InfoboxWikitextParser.InfoboxBlock block = result.get();
        assertThat(block.templateName()).isEqualTo("Handlungszusammenfassung Atlan Im Auftrag der Kosmokraten");

        Map<String, String> fields = block.fields();
        assertThat(fields.get("Nummer")).isEqualTo("800");
        assertThat(fields.get("Titel")).isEqualTo("Die Zeitfestung");
        assertThat(fields.get("Untertitel")).isEqualTo("Überraschung im Intern-Kosmos");
        assertThat(fields.get("Autor")).isEqualTo("H. G. Ewers");
        assertThat(fields.get("Erscheinungsdatum")).isEqualTo("Dienstag, 27. Januar 1987");
        // Atlan uses SonstigesWas/SonstigesInhalt instead of the classic series' Besonderes.
        assertThat(fields.get("SonstigesWas")).isEqualTo("Atlans Extrasinn");
        assertThat(fields.get("SonstigesInhalt")).isEqualTo("Probleme mit der Zeit");
    }

    @Test
    void parse_NoTemplateBlock_ReturnsEmpty() {
        assertThat(InfoboxWikitextParser.parse("Just some prose, no template here.")).isEmpty();
    }

    @Test
    void parse_Null_ReturnsEmpty() {
        assertThat(InfoboxWikitextParser.parse(null)).isEmpty();
    }
}
