package org.booklore.service.kobo;

import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.booklore.service.ArchiveService;
import org.booklore.util.epub.CoverDetectorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KepubConversionServiceTest {
    @Mock
    private KepubHtmlConversionService kepubHtmlConversionService;

    @Mock
    private CoverDetectorService coverDetectorService;

    @Mock
    private ArchiveService archiveService;

    @InjectMocks
    private KepubConversionService kepubConversionService;

    @TempDir
    Path tempDir;

    @TempDir
    Path appDir;

    MockedStatic<Files> mockFiles;

    @Captor
    ArgumentCaptor<Predicate<ArchiveService.Entry>> predicateCaptor;

    @BeforeEach
    void setup() throws Exception {
        mockFiles = mockStatic(Files.class, CALLS_REAL_METHODS);
        mockFiles.when(() -> Files.createTempDirectory(anyString())).thenReturn(appDir);

        when(kepubHtmlConversionService.transform(anyString(), eq(true))).then(
                args -> "transformed " + args.getArgument(0)
        );

        when(archiveService.extractToDirectory(any(), any(), any())).then(
                (a) -> {
                    writeExtractedEpub(a.getArgument(1));
                    return List.of();
                }
        );
    }


    @AfterEach
    void tearDown() {
        mockFiles.close();
    }

    @Test
    void convertEpubToKepub_ShouldSkipSomeFiles() throws IOException {
        var ignoredFiles = Set.of(
                "",
                "/example/.DS_STORE",
                "/__MACOSX/bar.txt"
        );

        var acceptedFiles = Set.of(
                "/example/foo.txt",
                "/other.txt"
        );

        Path epubPath = writeFakeEpub("example.epub");
        Path kepubPath = tempDir.resolve("example.epub.kepub");

        kepubConversionService.convertEpubToKepub(
                epubPath,
                kepubPath,
                true
        );

        verify(archiveService).extractToDirectory(any(), any(), predicateCaptor.capture());

        var predicate = predicateCaptor.getValue();

        for (var f : ignoredFiles) {
            assertThat(predicate.test(new ArchiveService.Entry(f, 0)))
                    .withFailMessage("Should ignore file `" + f + "`")
                    .isFalse();
        }

        for (var f : acceptedFiles) {
            assertThat(predicate.test(new ArchiveService.Entry(f, 0)))
                    .withFailMessage("Should include file `" + f + "`")
                    .isTrue();
        }
    }

    @Test
    void convertEpubToKepub_ShouldOnlyTransformHTML() throws Exception {
        Path epubPath = writeFakeEpub("example.epub");
        Path kepubPath = tempDir.resolve("example.epub.kepub");

        kepubConversionService.convertEpubToKepub(
                epubPath,
                kepubPath,
                true
        );

        verify(kepubHtmlConversionService).transform("<html><body></body></html>", true);

        assertThat(kepubPath).exists();
    }

    @Test
    void convertEpubToKepub_ShouldOnlyTransformManifestItems() throws Exception {
        Path epubPath = writeFakeEpub("example.epub");
        Path kepubPath = tempDir.resolve("example.epub.kepub");

        kepubConversionService.convertEpubToKepub(
                epubPath,
                kepubPath,
                true
        );

        verify(kepubHtmlConversionService, never()).transform("<html><body>do not transform</body></html>", true);

        assertThat(kepubPath).exists();
    }

    @Test
    void convertEpubToKepub_ShouldFindCoverPage() throws IOException {
        Path epubPath = writeFakeEpub("example.epub");
        Path kepubPath = tempDir.resolve("example.epub.kepub");

        when(coverDetectorService.detectCoverImagePath(any())).thenReturn("OEBPS/cover.jpg");

        kepubConversionService.convertEpubToKepub(
                epubPath,
                kepubPath,
                true
        );

        mockFiles.verify(() -> Files.writeString(
                any(),
                contains("<item href=\"cover.jpg\" properties=\"cover-image\"/>")
        ));
    }

    private Path writeFakeEpub(String epubName) throws IOException {
        var path = tempDir.resolve(epubName);
        try (
                var os = Files.newOutputStream(path);
                var zos = new ZipArchiveOutputStream(os)
        ) {
            // Do nothing to create an empty zip
        }

        return path;
    }

    private static final String CONTAINER_XML = """
        <?xml version="1.0" encoding="UTF-8"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles>
            <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
            </rootfiles>
        </container>
        """;

    private static final String MINIMAL_OPF = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
              </metadata>
              <manifest>
                <item href="cover.jpg" />
                <item href="ch1.html" />
              </manifest>
              <spine></spine>
            </package>
            """;


    private void writeString(Path path, String content) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeExtractedEpub(Path path) {
        writeString(path.resolve("mimetype"), "application/epub+zip");
        writeString(path.resolve("META-INF/container.xml"), CONTAINER_XML);
        writeString(path.resolve("OEBPS/cover.jpg"), "");
        writeString(path.resolve("OEBPS/content.opf"), MINIMAL_OPF);
        writeString(path.resolve("OEBPS/example.txt"), "example");
        writeString(path.resolve("OEBPS/ch1.html"), "<html><body></body></html>");
        writeString(path.resolve("OEBPS/unknown.html"), "<html><body>do not transform</body></html>");

        mockFiles.clearInvocations();
    }
}