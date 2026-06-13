package org.booklore.util;

import org.grimmory.pdfium4j.XmpMetadataParser;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SecureXmlUtilsTest {

    @Test
    void normalizesMissingXmpNamespacesBeforeParsing() throws Exception {
        String xml = """
                <rdf:RDF>
                  <rdf:Description>
                    <dc:title>Recovered title</dc:title>
                  </rdf:Description>
                </rdf:RDF>
                """;

        String normalized = SecureXmlUtils.normalizeMissingXmpNamespaces(xml);
        var document = SecureXmlUtils.parseXml(normalized, true);

        assertThat(normalized)
                .contains("xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"")
                .contains("xmlns:dc=\"http://purl.org/dc/elements/1.1/\"");
        assertThat(document.getElementsByTagNameNS("*", "title").item(0).getTextContent())
                .isEqualTo("Recovered title");
    }

    @Test
    void normalizesMissingDcNamespaceWhenRdfIsDeclared() throws Exception {
        String xml = """
                <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                  <rdf:Description>
                    <dc:title>Recovered title</dc:title>
                  </rdf:Description>
                </rdf:RDF>
                """;

        String normalized = SecureXmlUtils.normalizeMissingXmpNamespaces(xml);
        var document = SecureXmlUtils.parseXml(normalized, true);

        assertThat(normalized).contains("xmlns:dc=\"http://purl.org/dc/elements/1.1/\"");
        assertThat(document.getElementsByTagNameNS("*", "title").item(0).getTextContent())
                .isEqualTo("Recovered title");
    }

    @Test
    void normalizesMissingXmpNamespace() throws Exception {
        String xml = """
                <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                         xmlns:dc="http://purl.org/dc/elements/1.1/">
                  <rdf:Description>
                    <xmp:CreatorTool>Adobe Acrobat</xmp:CreatorTool>
                    <dc:title>Document Title</dc:title>
                  </rdf:Description>
                </rdf:RDF>
                """;

        String normalized = SecureXmlUtils.normalizeMissingXmpNamespaces(xml);
        var document = SecureXmlUtils.parseXml(normalized, true);

        assertThat(normalized).contains("xmlns:xmp=\"http://ns.adobe.com/xap/1.0/\"");
        assertThat(document.getElementsByTagNameNS("*", "CreatorTool").item(0).getTextContent())
                .isEqualTo("Adobe Acrobat");
        assertThat(document.getElementsByTagNameNS("*", "title").item(0).getTextContent())
                .isEqualTo("Document Title");
    }

    @Test
    void normalizesMissingXmpNamespaceWithOnlyXmpUsage() throws Exception {
        String xml = """
                <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                         xmlns:dc="http://purl.org/dc/elements/1.1/">
                  <rdf:Description>
                    <xmp:CreatorTool>Adobe Acrobat</xmp:CreatorTool>
                  </rdf:Description>
                </rdf:RDF>
                """;

        String normalized = SecureXmlUtils.normalizeMissingXmpNamespaces(xml);

        assertThat(normalized).contains("xmlns:xmp=\"http://ns.adobe.com/xap/1.0/\"");
    }

    @Test
    void leavesDeclaredXmpNamespacesUnchanged() {
        String xml = """
                <rdf:RDF
                    xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                    xmlns:dc="http://purl.org/dc/elements/1.1/">
                  <dc:title>Title</dc:title>
                </rdf:RDF>
                """;

        assertThat(SecureXmlUtils.normalizeMissingXmpNamespaces(xml)).isEqualTo(xml);
    }

    @Test
    void normalizedXmlDoesNotEmitFatalErrorsFromPdfiumXmpParser() {
        String xml = """
                <x:xmpmeta xmlns:x="adobe:ns:meta/">
                  <rdf:RDF>
                    <rdf:Description>
                      <dc:title>Recovered title</dc:title>
                    </rdf:Description>
                  </rdf:RDF>
                </x:xmpmeta>
                """;
        ByteArrayOutputStream parserErrors = new ByteArrayOutputStream();

        var metadata = capturePdfiumParserErrors(xml, parserErrors);

        assertThat(metadata.title()).contains("Recovered title");
        assertThat(parserErrors.toString(StandardCharsets.UTF_8)).doesNotContain("[Fatal Error]");
    }

    private org.grimmory.pdfium4j.model.XmpMetadata capturePdfiumParserErrors(
            String xml,
            ByteArrayOutputStream parserErrors) {
        synchronized (SecureXmlUtilsTest.class) {
            PrintStream originalError = System.err;
            try (PrintStream capturedError = new PrintStream(parserErrors, true, StandardCharsets.UTF_8)) {
                System.setErr(capturedError);
                String normalized = SecureXmlUtils.normalizeMissingXmpNamespaces(xml);
                return XmpMetadataParser.parse(normalized.getBytes(StandardCharsets.UTF_8));
            } finally {
                System.setErr(originalError);
            }
        }
    }
}
