package org.booklore.util.epub;

import org.booklore.service.ArchiveService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;


@ExtendWith(MockitoExtension.class)
public class CoverDetectorServiceTest {
    @Spy
    ArchiveService archiveService = new ArchiveService();

    @InjectMocks
    CoverDetectorService coverDetectorService;

    @TempDir
    Path tempDir;

    private Path createEpub(String opfContent, String opfPath, byte[] coverImage) throws IOException {
        Path epub = tempDir.resolve("test.epub");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(epub.toFile()))) {
            zos.putNextEntry(new ZipEntry("mimetype"));
            zos.write("application/epub+zip".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            String containerXml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                      <rootfiles>
                        <rootfile full-path="%s" media-type="application/oebps-package+xml"/>
                      </rootfiles>
                    </container>""".formatted(opfPath);
            zos.putNextEntry(new ZipEntry("META-INF/container.xml"));
            zos.write(containerXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry(opfPath));
            zos.write(opfContent.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            if (coverImage != null) {
                zos.putNextEntry(new ZipEntry("OEBPS/images/cover.jpg"));
                zos.write(coverImage);
                zos.closeEntry();
            }
        }
        return epub;
    }

    private String wrapOpf(String metadataContent, String manifestContent) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
                    %s
                  </metadata>
                  <manifest>
                    %s
                  </manifest>
                </package>""".formatted(metadataContent, manifestContent);
    }

    @Test
    void extractsCoverViaCoverImageProperty() throws IOException {
        byte[] coverBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01, 0x02, 0x03};
        String opf = wrapOpf("", """
                    <item id="cover" href="images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>
                    """);
        Path epub = createEpub(opf, "OEBPS/content.opf", coverBytes);
        byte[] result = coverDetectorService.detectCoverImage(epub);

        assertThat(result).isEqualTo(coverBytes);
    }

    @Test
    void extractsCoverByHeuristicManifestSearch() throws IOException {
        byte[] coverBytes = new byte[]{0x01, 0x02, 0x03, 0x04};
        String opf = wrapOpf("", """
                    <item id="cover-img" href="images/cover.jpg" media-type="image/jpeg"/>
                    """);
        Path epub = createEpub(opf, "OEBPS/content.opf", coverBytes);
        byte[] result = coverDetectorService.detectCoverImage(epub);

        assertThat(result).isEqualTo(coverBytes);
    }

    @Test
    void extractsCoverByZipHeuristic() throws IOException {
        byte[] coverBytes = new byte[]{0x10, 0x20, 0x30};
        Path epub = tempDir.resolve("cover_zip.epub");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(epub.toFile()))) {
            zos.putNextEntry(new ZipEntry("mimetype"));
            zos.write("application/epub+zip".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            String containerXml = """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                          <rootfiles>
                            <rootfile full-path="content.opf" media-type="application/oebps-package+xml"/>
                          </rootfiles>
                        </container>""";
            zos.putNextEntry(new ZipEntry("META-INF/container.xml"));
            zos.write(containerXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            String opf = """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"/>
                          <manifest>
                            <item id="text" href="chapter1.html" media-type="application/xhtml+xml"/>
                          </manifest>
                        </package>""";
            zos.putNextEntry(new ZipEntry("content.opf"));
            zos.write(opf.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("images/cover.jpg"));
            zos.write(coverBytes);
            zos.closeEntry();
        }

        byte[] result = coverDetectorService.detectCoverImage(epub);
        assertThat(result).isEqualTo(coverBytes);
    }

    @Test
    void returnsNullForEpubWithNoCover() throws IOException {
        Path epub = tempDir.resolve("nocover.epub");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(epub.toFile()))) {
            zos.putNextEntry(new ZipEntry("mimetype"));
            zos.write("application/epub+zip".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            String containerXml = """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                          <rootfiles>
                            <rootfile full-path="content.opf" media-type="application/oebps-package+xml"/>
                          </rootfiles>
                        </container>""";
            zos.putNextEntry(new ZipEntry("META-INF/container.xml"));
            zos.write(containerXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            String opf = """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                            <dc:title>No Cover Book</dc:title>
                          </metadata>
                          <manifest>
                            <item id="text" href="chapter1.html" media-type="application/xhtml+xml"/>
                          </manifest>
                        </package>""";
            zos.putNextEntry(new ZipEntry("content.opf"));
            zos.write(opf.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        byte[] result = coverDetectorService.detectCoverImage(epub);
        assertThat(result).isNull();
    }

    @Test
    void resolvesCoverWithParentDirectorySegments() throws IOException {
        byte[] coverBytes = new byte[]{0x01, 0x02};
        Path epub = tempDir.resolve("pathtest.epub");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(epub.toFile()))) {
            zos.putNextEntry(new ZipEntry("mimetype"));
            zos.write("application/epub+zip".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            String containerXml = """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                          <rootfiles>
                            <rootfile full-path="OEBPS/subdir/content.opf" media-type="application/oebps-package+xml"/>
                          </rootfiles>
                        </container>""";
            zos.putNextEntry(new ZipEntry("META-INF/container.xml"));
            zos.write(containerXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            String opf = """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                            <dc:title>Path Test</dc:title>
                          </metadata>
                          <manifest>
                            <item id="cover" href="../images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>
                          </manifest>
                        </package>""";
            zos.putNextEntry(new ZipEntry("OEBPS/subdir/content.opf"));
            zos.write(opf.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("OEBPS/images/cover.jpg"));
            zos.write(coverBytes);
            zos.closeEntry();
        }

        byte[] result = coverDetectorService.detectCoverImage(epub);
        assertThat(result).isEqualTo(coverBytes);
    }

    @Test
    void resolvesAbsoluteHrefInZip() throws IOException {
        byte[] coverBytes = new byte[]{0x0A, 0x0B};
        Path epub = tempDir.resolve("abstest.epub");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(epub.toFile()))) {
            zos.putNextEntry(new ZipEntry("mimetype"));
            zos.write("application/epub+zip".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            String containerXml = """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                          <rootfiles>
                            <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                          </rootfiles>
                        </container>""";
            zos.putNextEntry(new ZipEntry("META-INF/container.xml"));
            zos.write(containerXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            String opf = """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                            <dc:title>Abs Test</dc:title>
                          </metadata>
                          <manifest>
                            <item id="cover" href="/images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>
                          </manifest>
                        </package>""";
            zos.putNextEntry(new ZipEntry("OEBPS/content.opf"));
            zos.write(opf.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("images/cover.jpg"));
            zos.write(coverBytes);
            zos.closeEntry();
        }

        byte[] result = coverDetectorService.detectCoverImage(epub);
        assertThat(result).isEqualTo(coverBytes);
    }

    @Test
    void resolvesDotSegmentsInHref() throws IOException {
        byte[] coverBytes = new byte[]{0x0C};
        Path epub = tempDir.resolve("dottest.epub");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(epub.toFile()))) {
            zos.putNextEntry(new ZipEntry("mimetype"));
            zos.write("application/epub+zip".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            String containerXml = """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                          <rootfiles>
                            <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                          </rootfiles>
                        </container>""";
            zos.putNextEntry(new ZipEntry("META-INF/container.xml"));
            zos.write(containerXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            String opf = """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                            <dc:title>Dot Test</dc:title>
                          </metadata>
                          <manifest>
                            <item id="cover" href="./images/../images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>
                          </manifest>
                        </package>""";
            zos.putNextEntry(new ZipEntry("OEBPS/content.opf"));
            zos.write(opf.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("OEBPS/images/cover.jpg"));
            zos.write(coverBytes);
            zos.closeEntry();
        }

        byte[] result = coverDetectorService.detectCoverImage(epub);
        assertThat(result).isEqualTo(coverBytes);
    }

    @Test
    void nonExistentFileReturnsNull() {
        Path nonExistent = tempDir.resolve("nonexistent.epub");
        assertThat(coverDetectorService.detectCoverImage(nonExistent)).isNull();
    }

    @Test
    void corruptZipReturnsNull() throws IOException {
        Path corrupt = tempDir.resolve("corrupt.epub");
        try (FileOutputStream fos = new FileOutputStream(corrupt.toFile())) {
            fos.write(new byte[]{0x00, 0x01, 0x02, 0x03});
        }
        assertThat(coverDetectorService.detectCoverImage(corrupt)).isNull();
    }

}
