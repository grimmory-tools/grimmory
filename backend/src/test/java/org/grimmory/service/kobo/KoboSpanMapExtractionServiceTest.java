package org.booklore.service.kobo;

import org.booklore.model.dto.kobo.KoboSpanPositionMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KoboSpanMapExtractionServiceTest {

    private final KoboSpanMapExtractionService service = new KoboSpanMapExtractionService();

    @TempDir
    Path tempDir;

    @Test
    void extractFromKepub_ExtractsSpineEntriesAndProgressBoundaries() throws Exception {
        File kepubFile = createKepub(
                "OPS/package.opf",
                """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                          <manifest>
                            <item id="chap1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                            <item id="chap2" href="chapter2.xhtml" media-type="application/xhtml+xml"/>
                          </manifest>
                          <spine>
                            <itemref idref="chap1"/>
                            <itemref idref="chap2"/>
                          </spine>
                        </package>
                        """,
                new ZipResource("OPS/chapter1.xhtml", chapterHtml("kobo.1.1", "kobo.1.2")),
                new ZipResource("OPS/chapter2.xhtml", chapterHtml("kobo.2.1", "kobo.2.2"))
        );

        KoboSpanPositionMap result = service.extractFromKepub(kepubFile);

        assertEquals(2, result.chapters().size());
        assertEquals("OPS/chapter1.xhtml", result.chapters().get(0).sourceHref());
        assertEquals("OPS/chapter2.xhtml", result.chapters().get(1).sourceHref());
        assertEquals("kobo.1.1", result.chapters().get(0).spans().get(0).id());
        assertEquals("kobo.2.2", result.chapters().get(1).spans().get(1).id());
        assertEquals(0f, result.chapters().get(0).globalStartProgress(), 0.0001f);
        assertTrue(result.chapters().get(0).globalEndProgress() > 0f);
        assertEquals(1f, result.chapters().get(1).globalEndProgress(), 0.0001f);
    }

    @Test
    void extractFromKepub_ResolvesEncodedManifestHrefsToActualEntries() throws Exception {
        File kepubFile = createKepub(
                "OPS/package.opf",
                """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                          <manifest>
                            <item id="chap1" href="Text/chapter%202.xhtml" media-type="application/xhtml+xml"/>
                          </manifest>
                          <spine>
                            <itemref idref="chap1"/>
                          </spine>
                        </package>
                        """,
                new ZipResource("OPS/Text/chapter 2.xhtml", chapterHtml("kobo.2.1", "kobo.2.2"))
        );

        KoboSpanPositionMap result = service.extractFromKepub(kepubFile);

        assertEquals(1, result.chapters().size());
        assertEquals("OPS/Text/chapter 2.xhtml", result.chapters().getFirst().sourceHref());
        assertEquals("OPS/Text/chapter 2.xhtml", result.chapters().getFirst().normalizedHref());
    }

    @Test
    void extractFromKepub_PreservesPlusSignsInManifestHrefs() throws Exception {
        File kepubFile = createKepub(
                "OPS/package.opf",
                """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                          <manifest>
                            <item id="chap1" href="Text/chapter%2B1.xhtml" media-type="application/xhtml+xml"/>
                          </manifest>
                          <spine>
                            <itemref idref="chap1"/>
                          </spine>
                        </package>
                        """,
                new ZipResource("OPS/Text/chapter+1.xhtml", chapterHtml("kobo.1.1", "kobo.1.2"))
        );

        KoboSpanPositionMap result = service.extractFromKepub(kepubFile);

        assertEquals(1, result.chapters().size());
        assertEquals("OPS/Text/chapter+1.xhtml", result.chapters().getFirst().sourceHref());
        assertEquals("OPS/Text/chapter+1.xhtml", result.chapters().getFirst().normalizedHref());
    }

    @Test
    void extractFromKepub_KeepsSpanlessChaptersInProgressModel() throws Exception {
        File kepubFile = createKepub(
                "OPS/package.opf",
                """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                          <manifest>
                            <item id="front" href="front.xhtml" media-type="application/xhtml+xml"/>
                            <item id="chap1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                            <item id="chap2" href="chapter2.xhtml" media-type="application/xhtml+xml"/>
                          </manifest>
                          <spine>
                            <itemref idref="front"/>
                            <itemref idref="chap1"/>
                            <itemref idref="chap2"/>
                          </spine>
                        </package>
                        """,
                new ZipResource("OPS/front.xhtml", chapterHtmlWithoutSpans()),
                new ZipResource("OPS/chapter1.xhtml", chapterHtml("kobo.1.1", "kobo.1.2")),
                new ZipResource("OPS/chapter2.xhtml", chapterHtml("kobo.2.1", "kobo.2.2"))
        );

        KoboSpanPositionMap result = service.extractFromKepub(kepubFile);

        assertEquals(3, result.chapters().size());
        assertTrue(result.chapters().getFirst().spans().isEmpty());
        assertTrue(result.chapters().get(1).globalStartProgress() > 0f);
    }

    private File createKepub(String opfPath, String opfContent, ZipResource... resources) throws IOException {
        File kepubFile = tempDir.resolve("test.kepub.epub").toFile();
        try (ZipOutputStream outputStream = new ZipOutputStream(new FileOutputStream(kepubFile))) {
            outputStream.putNextEntry(new ZipEntry("META-INF/container.xml"));
            outputStream.write(containerXml(opfPath).getBytes(StandardCharsets.UTF_8));
            outputStream.closeEntry();

            outputStream.putNextEntry(new ZipEntry(opfPath));
            outputStream.write(opfContent.getBytes(StandardCharsets.UTF_8));
            outputStream.closeEntry();

            for (ZipResource resource : resources) {
                outputStream.putNextEntry(new ZipEntry(resource.path()));
                outputStream.write(resource.content().getBytes(StandardCharsets.UTF_8));
                outputStream.closeEntry();
            }
        }
        return kepubFile;
    }

    private String containerXml(String opfPath) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="%s" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
                """.formatted(opfPath);
    }

    private String chapterHtml(String firstSpanId, String secondSpanId) {
        return """
                <html>
                  <body>
                    <p><span class="koboSpan" id="%s"></span>First paragraph with enough text to move the marker.</p>
                    <p><span class="koboSpan" id="%s"></span>Second paragraph with even more text to shift the marker later.</p>
                  </body>
                </html>
                """.formatted(firstSpanId, secondSpanId);
    }

    private String chapterHtmlWithoutSpans() {
        return """
                <html>
                  <body>
                    <p>Front matter without Kobo span markers but with enough text to count in the book length.</p>
                  </body>
                </html>
                """;
    }

    private record ZipResource(String path, String content) {
    }
}
