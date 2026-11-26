package org.booklore.service.metadata.writer;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.booklore.model.MetadataClearFlags;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.settings.MetadataPersistenceSettings;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.service.ArchiveService;
import org.booklore.service.appsettings.AppSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import java.net.URLDecoder;

@ExtendWith(MockitoExtension.class)
class EpubMetadataWriterTest {
    @Mock
    private AppSettingService appSettingService;

    @Spy
    private ArchiveService archiveService = new ArchiveService();

    private EpubMetadataWriter writer;
    private BookMetadataEntity metadata;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        appSettingService = mock(AppSettingService.class);
        MetadataPersistenceSettings.FormatSettings epubFormatSettings = MetadataPersistenceSettings.FormatSettings.builder()
                .enabled(true)
                .maxFileSizeInMb(100)
                .build();
        MetadataPersistenceSettings.SaveToOriginalFile saveToOriginalFile = MetadataPersistenceSettings.SaveToOriginalFile.builder()
                .epub(epubFormatSettings)
                .build();
        MetadataPersistenceSettings metadataPersistenceSettings = new MetadataPersistenceSettings();
        metadataPersistenceSettings.setSaveToOriginalFile(saveToOriginalFile);

        AppSettings appSettings = mock(AppSettings.class);
        when(appSettings.getMetadataPersistenceSettings()).thenReturn(metadataPersistenceSettings);
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        writer = new EpubMetadataWriter(appSettingService, archiveService);
        metadata = new BookMetadataEntity();
        metadata.setTitle("Test Book");
        AuthorEntity author = new AuthorEntity();
        author.setName("Test Author");
        metadata.setAuthors(List.of(author));
    }

    @Nested
    @DisplayName("Metadata writing Tests")
    class MetadataWritingTests {
        @Test
        void preparationRetainsValidatedCandidateAndBackupWithoutReplacingSource() throws Exception {
            File epub = createEpubWithOpf("""
                    <package xmlns="http://www.idpf.org/2007/opf">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Original</dc:title>
                      </metadata>
                      <manifest/><spine/>
                    </package>""", "prepare.epub");
            Path candidate = tempDir.resolve("prepare.candidate");
            Path backup = tempDir.resolve("prepare.backup");
            byte[] original = Files.readAllBytes(epub.toPath());

            String candidateHash = writer.prepareMetadataCandidate(epub.toPath(), candidate, backup,
                    EpubArchiveIntegrity.sha256(epub.toPath()), metadata, new MetadataClearFlags());

            assertThat(Files.readAllBytes(epub.toPath())).isEqualTo(original);
            assertThat(Files.readAllBytes(backup)).isEqualTo(original);
            assertThat(readOpfContent(candidate.toFile())).contains("Test Book");
            assertThat(candidateHash).isEqualTo(EpubArchiveIntegrity.sha256(candidate));
            EpubArchiveIntegrity.validateMetadataRewrite(backup, candidate);
        }

        @Test
        void preparationCreatesIdenticalCandidateWhenNothingChanges() throws Exception {
            File epub = createEpubWithOpf("""
                    <package xmlns="http://www.idpf.org/2007/opf">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Original</dc:title>
                      </metadata>
                      <manifest/><spine/>
                    </package>""", "unchanged.epub");
            Path candidate = tempDir.resolve("unchanged.candidate");
            Path backup = tempDir.resolve("unchanged.backup");
            byte[] original = Files.readAllBytes(epub.toPath());
            BookMetadataEntity unchanged = new BookMetadataEntity();
            unchanged.setAuthors(null);
            unchanged.setCategories(null);

            String candidateHash = writer.prepareMetadataCandidate(epub.toPath(), candidate, backup,
                    EpubArchiveIntegrity.sha256(epub.toPath()), unchanged, new MetadataClearFlags());

            assertThat(Files.readAllBytes(epub.toPath())).isEqualTo(original);
            assertThat(Files.readAllBytes(backup)).isEqualTo(original);
            assertThat(Files.readAllBytes(candidate)).isEqualTo(original);
            assertThat(candidateHash).isEqualTo(EpubArchiveIntegrity.sha256(epub.toPath()));
        }

        @Test
        void preparationKeepsEvidenceIfSourceChangesWhilePreparing() throws Exception {
            File epub = createEpubWithOpf("""
                    <package xmlns="http://www.idpf.org/2007/opf">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Original</dc:title>
                      </metadata>
                      <manifest/><spine/>
                    </package>""", "preparation-conflict.epub");
            Path candidate = tempDir.resolve("conflict.candidate");
            Path backup = tempDir.resolve("conflict.backup");
            byte[] original = Files.readAllBytes(epub.toPath());
            String originalHash = EpubArchiveIntegrity.sha256(epub.toPath());
            byte[] changed = "external change".getBytes(StandardCharsets.UTF_8);
            Mockito.doAnswer(invocation -> {
                Object extracted = invocation.callRealMethod();
                Files.write(epub.toPath(), changed);
                return extracted;
            }).when(archiveService).extractToDirectory(Mockito.any(), Mockito.any());

            assertThatThrownBy(() -> writer.prepareMetadataCandidate(
                    epub.toPath(), candidate, backup, originalHash, metadata, new MetadataClearFlags()))
                    .isInstanceOf(IOException.class)
                    .hasRootCauseMessage("EPUB changed during metadata update");
            assertThat(Files.readAllBytes(epub.toPath())).isEqualTo(changed);
            assertThat(Files.readAllBytes(backup)).isEqualTo(original);
            assertThat(candidate).exists();
        }

        @Test
        void preparationDoesNotOverwriteExistingCandidate() throws Exception {
            File epub = createEpubWithOpf("""
                    <package xmlns="http://www.idpf.org/2007/opf">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Original</dc:title>
                      </metadata>
                      <manifest/><spine/>
                    </package>""", "preparation-collision.epub");
            Path candidate = tempDir.resolve("collision.candidate");
            Path backup = tempDir.resolve("collision.backup");
            byte[] original = Files.readAllBytes(epub.toPath());
            Files.writeString(candidate, "existing");
            String originalHash = EpubArchiveIntegrity.sha256(epub.toPath());

            assertThatThrownBy(() -> writer.prepareMetadataCandidate(
                    epub.toPath(), candidate, backup, originalHash, metadata, new MetadataClearFlags()))
                    .isInstanceOf(IOException.class);
            assertThat(Files.readString(candidate)).isEqualTo("existing");
            assertThat(Files.readAllBytes(epub.toPath())).isEqualTo(original);
            assertThat(Files.readAllBytes(backup)).isEqualTo(original);
        }

        @Test
        void preparationRejectsOutdatedRecordedHashWithoutCreatingArtifacts() throws Exception {
            File epub = createEpubWithOpf("""
                    <package xmlns="http://www.idpf.org/2007/opf">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Original</dc:title>
                      </metadata>
                      <manifest/><spine/>
                    </package>""", "stale-original.epub");
            Path candidate = tempDir.resolve("stale.candidate");
            Path backup = tempDir.resolve("stale.backup");
            byte[] original = Files.readAllBytes(epub.toPath());

            assertThatThrownBy(() -> writer.prepareMetadataCandidate(
                    epub.toPath(), candidate, backup, "a".repeat(64), metadata, new MetadataClearFlags()))
                    .isInstanceOf(IOException.class)
                    .hasMessage("EPUB does not match the recorded original hash");
            assertThat(Files.readAllBytes(epub.toPath())).isEqualTo(original);
            assertThat(candidate).doesNotExist();
            assertThat(backup).doesNotExist();
        }

        @Test
        void strictWriteRejectsCorruptEntryWithoutChangingTheEpub() throws Exception {
            Path epub = tempDir.resolve("corrupt-strict.epub");
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(epub))) {
                for (var entry : List.of(
                        Map.entry("mimetype", "application/epub+zip"),
                        Map.entry("META-INF/container.xml", """
                                <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                                  <rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles>
                                </container>"""),
                        Map.entry("OEBPS/content.opf", """
                                <package xmlns="http://www.idpf.org/2007/opf">
                                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                                    <dc:title>Original title</dc:title>
                                  </metadata>
                                </package>"""))) {
                    byte[] content = entry.getValue().getBytes(StandardCharsets.UTF_8);
                    var crc = new CRC32();
                    crc.update(content);
                    var zipEntry = new ZipEntry(entry.getKey());
                    zipEntry.setMethod(ZipEntry.STORED);
                    zipEntry.setSize(content.length);
                    zipEntry.setCompressedSize(content.length);
                    zipEntry.setCrc(crc.getValue());
                    zip.putNextEntry(zipEntry);
                    zip.write(content);
                    zip.closeEntry();
                }
            }
            byte[] original = Files.readAllBytes(epub);
            byte[] marker = "Original title".getBytes(StandardCharsets.UTF_8);
            int offset = -1;
            for (int i = 0; i <= original.length - marker.length; i++) {
                if (Arrays.equals(original, i, i + marker.length, marker, 0, marker.length)) {
                    offset = i;
                    break;
                }
            }
            assertThat(offset).isGreaterThanOrEqualTo(0);
            original[offset] ^= 1;
            Files.write(epub, original);

            assertThatThrownBy(() -> writer.saveMetadataToFileStrict(epub.toFile(), metadata, new MetadataClearFlags()))
                    .isInstanceOf(IOException.class).hasMessageContaining("integrity check");
            assertThat(Files.readAllBytes(epub)).isEqualTo(original);
            assertThat(epub.resolveSibling(epub.getFileName() + ".bak")).doesNotExist();
            assertThat(epub.resolveSibling(epub.getFileName() + ".tmp")).doesNotExist();
        }

        @Test
        void strictWriteRejectsMalformedPackageWithoutChangingTheEpub() throws Exception {
            File epub = createEpubWithOpf("<package><metadata>", "malformed-strict.epub");
            byte[] original = Files.readAllBytes(epub.toPath());

            assertThatThrownBy(() -> writer.saveMetadataToFileStrict(epub, metadata, new MetadataClearFlags()))
                    .isInstanceOf(IOException.class).hasMessage("EPUB XML is invalid");
            assertThat(Files.readAllBytes(epub.toPath())).isEqualTo(original);
            assertThat(epub.toPath().resolveSibling(epub.getName() + ".bak")).doesNotExist();
        }

        @Test
        void strictWriteRejectsUnsafeArchivePathWithoutChangingTheEpub() throws Exception {
            Path epub = tempDir.resolve("unsafe-strict.epub");
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(epub))) {
                zip.putNextEntry(new ZipEntry("../outside.txt"));
                zip.write("unsafe".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            byte[] original = Files.readAllBytes(epub);

            assertThatThrownBy(() -> writer.saveMetadataToFileStrict(epub.toFile(), metadata, new MetadataClearFlags()))
                    .isInstanceOf(IOException.class).hasMessageContaining("invalid or duplicate");
            assertThat(Files.readAllBytes(epub)).isEqualTo(original);
            Mockito.verify(archiveService, Mockito.never()).extractToDirectory(Mockito.any(), Mockito.any());
            assertThat(epub.resolveSibling(epub.getFileName() + ".bak")).doesNotExist();
        }

        @Test
        void strictWriteRejectsUnexpectedArchiveContentBeforeReplacingOriginal() throws Exception {
            File epub = createEpubWithOpf("""
                    <package xmlns="http://www.idpf.org/2007/opf">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Original</dc:title>
                      </metadata>
                      <manifest/><spine/>
                    </package>""", "unexpected-entry.epub");
            byte[] original = Files.readAllBytes(epub.toPath());
            Mockito.doAnswer(invocation -> {
                Object extracted = invocation.callRealMethod();
                Path directory = invocation.getArgument(1);
                Files.writeString(directory.resolve("OEBPS").resolve("unexpected.xhtml"), "unexpected");
                return extracted;
            }).when(archiveService).extractToDirectory(Mockito.any(), Mockito.any());

            assertThatThrownBy(() -> writer.saveMetadataToFileStrict(epub, metadata, new MetadataClearFlags()))
                    .isInstanceOf(IOException.class)
                    .hasRootCauseMessage("EPUB file entries changed during metadata update");
            assertThat(Files.readAllBytes(epub.toPath())).isEqualTo(original);
            assertThat(epub.toPath().resolveSibling(epub.getName() + ".bak")).doesNotExist();
            assertThat(epub.toPath().resolveSibling(epub.getName() + ".tmp")).doesNotExist();
        }

        @Test
        void strictWriteDoesNotOverwriteAConcurrentFileChange() throws Exception {
            File epub = createEpubWithOpf("""
                    <package xmlns="http://www.idpf.org/2007/opf">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Original</dc:title>
                      </metadata>
                      <manifest/><spine/>
                    </package>""", "concurrent-change.epub");
            byte[] changed = "external change".getBytes(StandardCharsets.UTF_8);
            Mockito.doAnswer(invocation -> {
                Object extracted = invocation.callRealMethod();
                Files.write(epub.toPath(), changed);
                return extracted;
            }).when(archiveService).extractToDirectory(Mockito.any(), Mockito.any());

            assertThatThrownBy(() -> writer.saveMetadataToFileStrict(epub, metadata, new MetadataClearFlags()))
                    .isInstanceOf(IOException.class)
                    .hasRootCauseMessage("EPUB changed during metadata update");
            assertThat(Files.readAllBytes(epub.toPath())).isEqualTo(changed);
        }

        @Test
        void strictWriteRejectsChangesToPackageManifest() throws Exception {
            File epub = createEpubWithOpf("""
                    <package xmlns="http://www.idpf.org/2007/opf">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Original</dc:title>
                      </metadata>
                      <manifest><item id="chapter" href="chapter.xhtml"/></manifest><spine/>
                    </package>""", "changed-manifest.epub");
            byte[] original = Files.readAllBytes(epub.toPath());
            Mockito.doAnswer(invocation -> {
                Object extracted = invocation.callRealMethod();
                Path opf = ((Path) invocation.getArgument(1)).resolve("OEBPS").resolve("content.opf");
                Files.writeString(opf, Files.readString(opf).replace("chapter.xhtml", "other.xhtml"));
                return extracted;
            }).when(archiveService).extractToDirectory(Mockito.any(), Mockito.any());

            assertThatThrownBy(() -> writer.saveMetadataToFileStrict(epub, metadata, new MetadataClearFlags()))
                    .isInstanceOf(IOException.class)
                    .hasRootCauseMessage("EPUB package content outside metadata changed during metadata update");
            assertThat(Files.readAllBytes(epub.toPath())).isEqualTo(original);
        }

        @Test
        void strictWriteRejectsChangedNonMetadataEntry() throws Exception {
            File epub = createEpubWithOpf("""
                    <package xmlns="http://www.idpf.org/2007/opf">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Original</dc:title>
                      </metadata>
                      <manifest/><spine/>
                    </package>""", "changed-entry.epub");
            byte[] original = Files.readAllBytes(epub.toPath());
            Mockito.doAnswer(invocation -> {
                Object extracted = invocation.callRealMethod();
                Path container = ((Path) invocation.getArgument(1)).resolve("META-INF").resolve("container.xml");
                Files.writeString(container, Files.readString(container).replace("version=\"1.0\" xmlns=", "version=\"2.0\" xmlns="));
                return extracted;
            }).when(archiveService).extractToDirectory(Mockito.any(), Mockito.any());

            assertThatThrownBy(() -> writer.saveMetadataToFileStrict(epub, metadata, new MetadataClearFlags()))
                    .isInstanceOf(IOException.class)
                    .hasRootCauseMessage("EPUB non-metadata content changed during metadata update: META-INF/container.xml");
            assertThat(Files.readAllBytes(epub.toPath())).isEqualTo(original);
        }

        @Test
        @MockitoSettings(strictness = Strictness.LENIENT)
        void existingBackupIsNotOverwritten() throws Exception {
            File epub = createEpubWithOpf("""
                    <package xmlns="http://www.idpf.org/2007/opf">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Original</dc:title>
                      </metadata>
                    </package>""", "backup-collision.epub");
            byte[] original = Files.readAllBytes(epub.toPath());
            Path backup = epub.toPath().resolveSibling(epub.getName() + ".bak");
            Files.writeString(backup, "existing backup");

            assertThatThrownBy(() -> writer.writeMetadataToFile(epub, metadata, new MetadataClearFlags()))
                    .isInstanceOf(java.nio.file.FileAlreadyExistsException.class);
            assertThat(Files.readAllBytes(epub.toPath())).isEqualTo(original);
            assertThat(Files.readString(backup)).isEqualTo("existing backup");
        }

        @Test
        @MockitoSettings(strictness = Strictness.LENIENT)
        void existingTemporaryFileIsNotOverwrittenDuringMetadataUpdate() throws Exception {
            File epub = createEpubWithOpf("""
                    <package xmlns="http://www.idpf.org/2007/opf">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Original</dc:title>
                      </metadata>
                    </package>""", "metadata-collision.epub");
            byte[] original = Files.readAllBytes(epub.toPath());
            Path temporary = epub.toPath().resolveSibling(epub.getName() + ".tmp");
            Files.writeString(temporary, "existing temporary file");

            assertThatThrownBy(() -> writer.writeMetadataToFile(epub, metadata, new MetadataClearFlags()))
                    .isInstanceOf(IOException.class)
                    .hasCauseInstanceOf(java.nio.file.FileAlreadyExistsException.class);
            assertThat(Files.readAllBytes(epub.toPath())).isEqualTo(original);
            assertThat(Files.readString(temporary)).isEqualTo("existing temporary file");
            assertThat(epub.toPath().resolveSibling(epub.getName() + ".bak")).doesNotExist();
        }

        @Test
        @MockitoSettings(strictness = Strictness.LENIENT)
        void metadataUpdateRestoresBackupAfterUncertainReplacement() throws Exception {
            File epub = createEpubWithOpf("""
                    <package xmlns="http://www.idpf.org/2007/opf">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Original</dc:title>
                      </metadata>
                    </package>""", "uncertain-replacement.epub");
            byte[] original = Files.readAllBytes(epub.toPath());
            Path temporary = epub.toPath().resolveSibling(epub.getName() + ".tmp");
            try (var files = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
                files.when(() -> Files.move(temporary, epub.toPath(),
                                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING))
                        .thenAnswer(invocation -> {
                            invocation.callRealMethod();
                            throw new IOException("Replacement acknowledgement lost");
                        });
                assertThatThrownBy(() -> writer.writeMetadataToFile(epub, metadata, new MetadataClearFlags()))
                        .isInstanceOf(IOException.class).hasMessage("Failed to write EPUB metadata")
                        .hasCauseInstanceOf(IOException.class);
            }
            assertThat(Files.readAllBytes(epub.toPath())).isEqualTo(original);
            assertThat(epub.toPath().resolveSibling(epub.getName() + ".bak")).doesNotExist();
        }

        @Test
        @MockitoSettings(strictness = Strictness.LENIENT)
        void metadataUpdateKeepsBackupWhenRestorationFails() throws Exception {
            File epub = createEpubWithOpf("""
                    <package xmlns="http://www.idpf.org/2007/opf">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Original</dc:title>
                      </metadata>
                    </package>""", "failed-restoration.epub");
            byte[] original = Files.readAllBytes(epub.toPath());
            Path temporary = epub.toPath().resolveSibling(epub.getName() + ".tmp");
            try (var files = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
                files.when(() -> Files.move(temporary, epub.toPath(),
                                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING))
                        .thenAnswer(invocation -> {
                            invocation.callRealMethod();
                            throw new IOException("Replacement acknowledgement lost");
                        });
                files.when(() -> Files.move(argThat(path -> path != null
                                        && path.getFileName().toString().startsWith(".grimmory-restore-")),
                                eq(epub.toPath()), eq(StandardCopyOption.ATOMIC_MOVE), eq(StandardCopyOption.REPLACE_EXISTING)))
                        .thenThrow(new IOException("Restoration unavailable"));
                assertThatThrownBy(() -> writer.writeMetadataToFile(epub, metadata, new MetadataClearFlags()))
                        .isInstanceOf(IOException.class)
                        .satisfies(error -> assertThat(error.getCause().getSuppressed())
                                .anySatisfy(suppressed -> assertThat(suppressed).hasMessage("Restoration unavailable")));
            }
            assertThat(Files.readAllBytes(epub.toPath().resolveSibling(epub.getName() + ".bak")))
                    .isEqualTo(original);
        }

        @Test
        void fullMetadataUpdatePreservesUnknownBookloreFields() throws Exception {
            String opf = """
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0"
                             prefix="booklore: http://booklore.org/metadata/1.0/">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Old title</dc:title>
                        <meta property="booklore:page_count">123</meta>
                        <meta id="future" property="booklore:future_field">Keep this</meta>
                        <meta refines="#future" property="display-seq">1</meta>
                      </metadata>
                    </package>""";
            File epub = createEpubWithOpf(opf, "unknown-booklore.epub");
            metadata.setPageCount(456);

            writer.saveMetadataToFile(epub, metadata, new MetadataClearFlags());

            String content = readOpfContent(epub);
            assertThat(content).contains("booklore:future_field", "Keep this", "refines=\"#future\"",
                    "booklore:page_count", ">456<").doesNotContain(">123<");
        }

        @Test
        void unknownBookloreFieldAloneDoesNotTriggerRewrite() throws Exception {
            String opf = """
                    <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Book</dc:title>
                        <meta name="booklore:future_field" content="Keep this"/>
                      </metadata>
                    </package>""";
            File epub = createEpubWithOpf(opf, "unknown-only.epub");
            byte[] original = Files.readAllBytes(epub.toPath());
            BookMetadataEntity unchanged = new BookMetadataEntity();
            unchanged.setAuthors(null);
            unchanged.setCategories(null);
            unchanged.setMoods(null);
            unchanged.setTags(null);

            writer.saveMetadataToFile(epub, unchanged, new MetadataClearFlags());

            assertThat(Files.readAllBytes(epub.toPath())).isEqualTo(original);
        }

        @Test
        void titleUpdatePreservesTitleIdentifiersAndOtherTitles() throws Exception {
            String opfContent = """
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title id="subtitle">Existing subtitle</dc:title>
                        <dc:title id="main" xml:lang="fr">Old main title</dc:title>
                        <dc:title id="alternate">Alternate title</dc:title>
                        <meta refines="#subtitle" property="title-type">subtitle</meta>
                        <meta refines="#main" property="title-type">main</meta>
                        <meta refines="#main" property="file-as">Sort title</meta>
                      </metadata>
                    </package>
                    """;
            File epubFile = createEpubWithOpf(opfContent, "preserve-titles.epub");
            BookMetadataEntity titleOnly = new BookMetadataEntity();
            titleOnly.setTitle("Updated main title");
            titleOnly.setAuthors(null);
            titleOnly.setCategories(null);
            titleOnly.setMoods(null);
            titleOnly.setTags(null);

            writer.saveMetadataToFile(epubFile, titleOnly, new MetadataClearFlags());

            Document doc = parseOpf(epubFile);
            NodeList titles = doc.getElementsByTagNameNS("http://purl.org/dc/elements/1.1/", "title");
            assertThat(titles.getLength()).isEqualTo(3);
            Element subtitle = (Element) titles.item(0);
            Element main = (Element) titles.item(1);
            Element alternate = (Element) titles.item(2);
            assertThat(subtitle.getAttribute("id")).isEqualTo("subtitle");
            assertThat(subtitle.getTextContent()).isEqualTo("Existing subtitle");
            assertThat(main.getAttribute("id")).isEqualTo("main");
            assertThat(main.getAttributeNS("http://www.w3.org/XML/1998/namespace", "lang")).isEqualTo("fr");
            assertThat(main.getTextContent()).isEqualTo("Updated main title");
            assertThat(alternate.getTextContent()).isEqualTo("Alternate title");
            assertThat(readOpfContent(epubFile)).contains("refines=\"#main\"", "Sort title",
                    "refines=\"#subtitle\"", ">subtitle<");
        }

        @Test
        void titleUpdatePreservesUnrelatedMetadata() throws Exception {
            for (String version : List.of("2.0", "3.0")) {
                String opfContent = """
                        <package xmlns="http://www.idpf.org/2007/opf" version="%s"
                                 prefix="calibre: http://calibre.kovidgoyal.net/2009/metadata"
                                 unique-identifier="book-id">
                          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"
                                    xmlns:calibre="http://calibre.kovidgoyal.net/2009/metadata"
                                    xmlns:extra="urn:example">
                            <dc:identifier id="book-id">urn:calibre:123</dc:identifier>
                            <dc:title>Original title</dc:title>
                            <dc:contributor id="contributor">calibre editor</dc:contributor>
                            <dc:rights xml:lang="en">Rights statement</dc:rights>
                            <dc:source>Source record</dc:source>
                            <meta refines="#contributor" property="role">bkp</meta>
                            <meta name="calibre:rating" content="4"/>
                            <extra:record extra:kind="example">Extension value</extra:record>
                            <!-- keep this comment -->
                          </metadata>
                        </package>
                        """.formatted(version);
                File epubFile = createEpubWithOpf(opfContent, "preserve-" + version + ".epub");
                BookMetadataEntity titleOnly = new BookMetadataEntity();
                titleOnly.setTitle("Updated title");
                titleOnly.setAuthors(null);
                titleOnly.setCategories(null);
                titleOnly.setMoods(null);
                titleOnly.setTags(null);

                writer.saveMetadataToFile(epubFile, titleOnly, new MetadataClearFlags());

                Document doc = parseOpf(epubFile);
                String dc = "http://purl.org/dc/elements/1.1/";
                assertThat(doc.getElementsByTagNameNS(dc, "title").item(0).getTextContent()).isEqualTo("Updated title");
                assertThat(doc.getElementsByTagNameNS(dc, "identifier").item(0).getTextContent()).isEqualTo("urn:calibre:123");
                assertThat(doc.getElementsByTagNameNS(dc, "contributor").item(0).getTextContent()).isEqualTo("calibre editor");
                Element rights = (Element) doc.getElementsByTagNameNS(dc, "rights").item(0);
                assertThat(rights.getTextContent()).isEqualTo("Rights statement");
                assertThat(rights.getAttributeNS("http://www.w3.org/XML/1998/namespace", "lang")).isEqualTo("en");
                assertThat(doc.getElementsByTagNameNS(dc, "source").item(0).getTextContent()).isEqualTo("Source record");
                assertThat(doc.getElementsByTagNameNS("urn:example", "record").item(0).getTextContent()).isEqualTo("Extension value");
                String content = readOpfContent(epubFile);
                assertThat(content).contains("refines=\"#contributor\"", "calibre:rating", "keep this comment",
                        "calibre: http://calibre.kovidgoyal.net/2009/metadata");
            }
        }

        @Test
        @DisplayName("Should only overwrite authors of EPUB metadata")
        void writeMetadata_withAuthor_onlyAuthor() throws IOException {
            StringBuilder existingMetadata = new StringBuilder();
            existingMetadata.append("<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">");
            existingMetadata.append("<dc:creator id=\"creator02\">Alice</dc:creator>");
            existingMetadata.append("<meta property=\"role\" refines=\"#creator02\">ill</meta>");
            existingMetadata.append("</metadata>");
            String opfContent = String.format("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                        %s
                    </package>
                    """, existingMetadata);
            File epubFile = createEpubWithOpf(opfContent, "test-metadata-" + System.nanoTime() + ".epub");

            assertDoesNotThrow(() -> writer.saveMetadataToFile(epubFile, metadata, new MetadataClearFlags()));

            assertTrue(epubFile.exists());
            assertTrue(epubFile.length() > 0);
            try (ZipFile zf = new ZipFile(epubFile)) {
                ZipEntry ze = zf.getEntry("OEBPS/content.opf");
                try (InputStream is = zf.getInputStream(ze)) {
                    byte[] fileBytes = is.readAllBytes();
                    String fileString = new String(fileBytes);
                    assertTrue(fileString.contains("id=\"creator02\""));
                }
            }
        }

        @Test
        @DisplayName("Should remove extraneous refines in EPUB3")
        void writeMetadata_removesMissingRefines() throws Exception {
            String opfContent = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                        <other id="example">Example</other>

                        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                            <meta refines="#example" property="identifier-type">wizard</meta>
                            <meta refines="#example2" property="source-of">magic</meta>
                        </metadata>
                    </package>""";

            File epubFile = createEpubWithOpf(opfContent, "test-epub3-role-" + System.nanoTime() + ".epub");
            writer.saveMetadataToFile(epubFile, metadata, new MetadataClearFlags());

            String content = readOpfContent(epubFile);
            assertThat(content).doesNotContain("refines=\"#example2\"");
            assertThat(content).contains("refines=\"#example\"");
        }

        @Test
        @DisplayName("Should sort package children in EPUB3")
        void writeMetadata_shouldSortPackageChildren() throws Exception {
            String opfContent = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                        <manifest></manifest>
                        <collection></collection>
                        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        </metadata>
                    </package>""";

            File epubFile = createEpubWithOpf(opfContent, "test-sort-package-" + System.nanoTime() + ".epub");
            writer.saveMetadataToFile(epubFile, metadata, new MetadataClearFlags());

            String content = readOpfContent(epubFile);

            int manifestIndex = content.indexOf("<manifest");
            int metadataIndex = content.indexOf("<metadata");
            int spineIndex = content.indexOf("<spine");
            int collectionIndex = content.indexOf("<collection");

            assertThat(metadataIndex).isLessThan(manifestIndex);
            assertThat(manifestIndex).isLessThan(spineIndex);
            assertThat(spineIndex).isLessThan(collectionIndex);
        }

        @Test
        @DisplayName("Should not flatten collections in EPUB3")
        void writeMetadata_shouldNotFlattenCollections() throws Exception {
            String opfContent = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                        <manifest></manifest>
                        <collection><collection></collection></collection>
                        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        </metadata>
                    </package>""";

            File epubFile = createEpubWithOpf(opfContent, "test-sort-package-" + System.nanoTime() + ".epub");
            writer.saveMetadataToFile(epubFile, metadata, new MetadataClearFlags());

            String content = readOpfContent(epubFile);

            assertThat(content.replaceAll("([\\s\n])+", " "))
                    .contains("<collection> <collection/> </collection>");
        }
    }

    @Nested
    @DisplayName("URL Decoding Tests")
    class UrlDecodingTests {

        @Test
        @DisplayName("Should properly handle URL-encoded href values in manifest")
        void writeMetadataToFile_withUnicodeHref_handlesDecoding() throws IOException {
            byte[] epubContent = createEpubWithUnicodeCoverHref();
            File epubFile = tempDir.resolve("test_unicode.epub").toFile();
            Files.write(epubFile.toPath(), epubContent);

            assertDoesNotThrow(() -> writer.saveMetadataToFile(epubFile, metadata, new MetadataClearFlags()));

            assertTrue(epubFile.exists());
            assertTrue(epubFile.length() > 0);
        }
    }

    @Nested
    @DisplayName("Whitespace Tests")
    class WhitespaceTests {
        @Test
        @DisplayName("Should not add extra whitespace lines on repeated saves")
        void saveMetadataToFile_repeatedSaves_shouldNotInflateWhitespace() throws IOException {
            String initialOpfContent = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                            <dc:title>Original Title</dc:title>
                            <dc:creator>Original Author</dc:creator>
                        </metadata>
                    </package>""";

            File epubFile = createEpubWithOpf(initialOpfContent, "test-whitespace-" + System.nanoTime() + ".epub");

            BookMetadataEntity newMeta = new BookMetadataEntity();
            newMeta.setTitle("Updated Title");
            AuthorEntity author = new AuthorEntity();
            author.setName("Updated Author");
            newMeta.setAuthors(List.of(author));

            writer.saveMetadataToFile(epubFile, newMeta, new MetadataClearFlags());
            String contentAfterFirstSave = readOpfContent(epubFile);

            newMeta.setTitle("Updated Title 2"); // Change title to force write
            writer.saveMetadataToFile(epubFile, newMeta, new MetadataClearFlags());
            String contentAfterSecondSave = readOpfContent(epubFile);

            long lines1 = contentAfterFirstSave.lines().count();
            long lines2 = contentAfterSecondSave.lines().count();

            assertTrue(Math.abs(lines2 - lines1) <= 2, "Line count should be stable");
            assertTrue(!contentAfterSecondSave.contains("\n\n"), "Should not contain double newlines");
        }
    }

    @Nested
    @DisplayName("EPUB3 Creator Metadata Tests")
    class Epub3CreatorTests {

        @Test
        @DisplayName("Should use meta refines for file-as in EPUB3")
        void epub3_shouldUseMetaRefines_forFileAs() throws Exception {
            String opfContent = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                            <dc:title>Test Book</dc:title>
                        </metadata>
                    </package>""";

            File epubFile = createEpubWithOpf(opfContent, "test-epub3-creator-" + System.nanoTime() + ".epub");
            writer.saveMetadataToFile(epubFile, metadata, new MetadataClearFlags());

            Document doc = parseOpf(epubFile);
            NodeList creators = doc.getElementsByTagNameNS("http://purl.org/dc/elements/1.1/", "creator");
            assertThat(creators.getLength()).isGreaterThan(0);

            Element creator = (Element) creators.item(0);
            assertThat(creator.getTextContent()).isEqualTo("Test Author");
            assertThat(creator.getAttribute("id")).isNotEmpty();

            // Should NOT have opf:file-as or opf:role attributes
            String fileAsAttr = creator.getAttributeNS("http://www.idpf.org/2007/opf", "file-as");
            String roleAttr = creator.getAttributeNS("http://www.idpf.org/2007/opf", "role");
            assertThat(fileAsAttr).isEmpty();
            assertThat(roleAttr).isEmpty();

            // Should have meta refines elements instead
            String creatorId = creator.getAttribute("id");
            String opfContent2 = readOpfContent(epubFile);
            assertThat(opfContent2).contains("refines=\"#" + creatorId + "\"");
            assertThat(opfContent2).contains("property=\"file-as\"");
            assertThat(opfContent2).contains("property=\"role\"");
        }

        @Test
        @DisplayName("Should include role with marc:relators scheme in EPUB3")
        void epub3_shouldIncludeRoleScheme() throws Exception {
            String opfContent = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                            <dc:title>Test Book</dc:title>
                        </metadata>
                    </package>""";

            File epubFile = createEpubWithOpf(opfContent, "test-epub3-role-" + System.nanoTime() + ".epub");
            writer.saveMetadataToFile(epubFile, metadata, new MetadataClearFlags());

            String content = readOpfContent(epubFile);
            assertThat(content).contains("scheme=\"marc:relators\"");
            assertThat(content).contains(">aut<");
        }
    }

    @Nested
    @DisplayName("EPUB2 Creator Metadata Tests")
    class Epub2CreatorTests {

        @Test
        @DisplayName("Should use opf:file-as attribute on dc:creator in EPUB2")
        void epub2_shouldUseOpfAttributes() throws Exception {
            String opfContent = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
                            <dc:title>Test Book</dc:title>
                        </metadata>
                    </package>""";

            File epubFile = createEpubWithOpf(opfContent, "test-epub2-creator-" + System.nanoTime() + ".epub");
            writer.saveMetadataToFile(epubFile, metadata, new MetadataClearFlags());

            String content = readOpfContent(epubFile);
            assertThat(content).contains("opf:file-as=");
            assertThat(content).contains("opf:role=");
            // Should NOT have meta refines for creators
            assertThat(content).doesNotContain("property=\"file-as\"");
            assertThat(content).doesNotContain("property=\"role\"");
        }

        @Test
        @DisplayName("Should preserve EPUB2 structure without EPUB3 constructs")
        void epub2_shouldNotContainEpub3Constructs() throws Exception {
            String opfContent = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
                            <dc:title>EPUB2 Book</dc:title>
                        </metadata>
                    </package>""";

            metadata.setSubtitle("A Subtitle");
            metadata.setSeriesName("Test Series");
            metadata.setSeriesNumber(3.0f);
            metadata.setPageCount(200);

            File epubFile = createEpubWithOpf(opfContent, "test-epub2-no-epub3-" + System.nanoTime() + ".epub");
            writer.saveMetadataToFile(epubFile, metadata, new MetadataClearFlags());

            String content = readOpfContent(epubFile);
            // EPUB2 should not have prefix attribute on package
            assertThat(content).doesNotContain("prefix=");
            // EPUB2 should not have property= metas for series
            assertThat(content).doesNotContain("property=\"belongs-to-collection\"");
            assertThat(content).doesNotContain("property=\"collection-type\"");
            assertThat(content).doesNotContain("property=\"group-position\"");
            // EPUB2 should not have property= metas for subtitles
            assertThat(content).doesNotContain("property=\"title-type\"");
        }
    }

    @Nested
    @DisplayName("ZIP Entry Tests")
    class MimetypeZipTests {

        @Test
        @DisplayName("Should handle duplicate entries in zip")
        void saveMetadata_handlesDuplicateCovers() throws Exception {
            String opfContent = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                            <dc:title>Original Title</dc:title>
                            <meta name="cover" content="cover-image"/>
                        </metadata>
                        <manifest>
                            <item id="cover-image" href="cover.png" media-type="text/plain" properties="cover-image"/>
                        </manifest>
                    </package>""";

            byte[] coverImageA = new byte[]{0x0A};
            byte[] coverImageB = new byte[]{0x0B};
            byte[] coverImageC = new byte[]{0x0C};

            File epubFile = tempDir.resolve("test-duplicate-" + System.nanoTime() + ".epub").toFile();

            try (var zos = new ZipArchiveOutputStream(new FileOutputStream(epubFile))) {
                String containerXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                    <rootfiles>
                        <rootfile full-path="content.opf" media-type="application/oebps-package+xml"/>
                    </rootfiles>
                </container>
                """;

                zos.putArchiveEntry(new ZipArchiveEntry("mimetype"));
                zos.write("application/epub+zip".getBytes(StandardCharsets.UTF_8));
                zos.closeArchiveEntry();

                zos.putArchiveEntry(new ZipArchiveEntry("META-INF/container.xml"));
                zos.write(containerXml.getBytes(StandardCharsets.UTF_8));
                zos.closeArchiveEntry();

                zos.putArchiveEntry(new ZipArchiveEntry("content.opf"));
                zos.write(opfContent.getBytes(StandardCharsets.UTF_8));
                zos.closeArchiveEntry();

                zos.putArchiveEntry(new ZipArchiveEntry("cover.png"));
                zos.write(coverImageA);
                zos.closeArchiveEntry();

                zos.putArchiveEntry(new ZipArchiveEntry("cover.png"));
                zos.write(coverImageB);
                zos.closeArchiveEntry();
            }

            writer.replaceCoverImageFromBytes(epubFile, coverImageC);

            try (ZipFile zf = new ZipFile(epubFile)) {
                var entry = zf.getEntry("cover.png");

                try (InputStream is = zf.getInputStream(entry)) {
                    assertThat(is.readAllBytes()).isEqualTo(coverImageC);
                }
            }
        }

        @Test
        @DisplayName("Should store mimetype as first uncompressed entry in ZIP")
        void saveMetadata_shouldStoreMimetypeFirst() throws Exception {
            String opfContent = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                            <dc:title>Original Title</dc:title>
                        </metadata>
                    </package>""";

            File epubFile = createEpubWithOpf(opfContent, "test-mimetype-" + System.nanoTime() + ".epub");
            writer.saveMetadataToFile(epubFile, metadata, new MetadataClearFlags());

            try (ZipFile zf = new ZipFile(epubFile)) {
                Enumeration<? extends ZipEntry> entries = zf.entries();
                assertThat(entries.hasMoreElements()).isTrue();

                ZipEntry firstEntry = entries.nextElement();
                assertThat(firstEntry.getName()).isEqualTo("mimetype");
                assertThat(firstEntry.getMethod()).isEqualTo(ZipEntry.STORED);

                // Verify mimetype content
                try (InputStream is = zf.getInputStream(firstEntry)) {
                    String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    assertThat(content).isEqualTo("application/epub+zip");
                }
            }
        }

        @Test
        @DisplayName("Should not duplicate mimetype entry in ZIP")
        void saveMetadata_shouldNotDuplicateMimetype() throws Exception {
            String opfContent = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                            <dc:title>Original Title</dc:title>
                        </metadata>
                    </package>""";

            File epubFile = createEpubWithOpf(opfContent, "test-no-dup-mimetype-" + System.nanoTime() + ".epub");
            writer.saveMetadataToFile(epubFile, metadata, new MetadataClearFlags());

            try (ZipFile zf = new ZipFile(epubFile)) {
                int mimetypeCount = 0;
                Enumeration<? extends ZipEntry> entries = zf.entries();
                while (entries.hasMoreElements()) {
                    if ("mimetype".equals(entries.nextElement().getName())) {
                        mimetypeCount++;
                    }
                }
                assertThat(mimetypeCount).isEqualTo(1);
            }
        }

        @Test
        @DisplayName("Should handle file names with a literal plus sign")
        void saveMetadata_handlesPlus() throws Exception {
            String opfContent = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                            <dc:title>Original Title</dc:title>
                            <meta name="cover" content="cover-image"/>
                        </metadata>
                        <manifest>
                            <item id="cover-image" href="cover+example.bin" media-type="application/octet-stream" properties="cover-image"/>
                        </manifest>
                    </package>""";

            byte[] coverImageA = new byte[]{0x0A};
            byte[] coverImageB = new byte[]{0x0B};

            File epubFile = tempDir.resolve("test-duplicate-" + System.nanoTime() + ".epub").toFile();

            try (var zos = new ZipArchiveOutputStream(new FileOutputStream(epubFile))) {
                String containerXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                    <rootfiles>
                        <rootfile full-path="content.opf" media-type="application/oebps-package+xml"/>
                    </rootfiles>
                </container>
                """;

                zos.putArchiveEntry(new ZipArchiveEntry("mimetype"));
                zos.write("application/epub+zip".getBytes(StandardCharsets.UTF_8));
                zos.closeArchiveEntry();

                zos.putArchiveEntry(new ZipArchiveEntry("META-INF/container.xml"));
                zos.write(containerXml.getBytes(StandardCharsets.UTF_8));
                zos.closeArchiveEntry();

                zos.putArchiveEntry(new ZipArchiveEntry("content.opf"));
                zos.write(opfContent.getBytes(StandardCharsets.UTF_8));
                zos.closeArchiveEntry();

                zos.putArchiveEntry(new ZipArchiveEntry("cover+example.bin"));
                zos.write(coverImageA);
                zos.closeArchiveEntry();
            }

            writer.replaceCoverImageFromBytes(epubFile, coverImageB);

            try (ZipFile zf = new ZipFile(epubFile)) {
                var entry = zf.getEntry("cover+example.bin");

                try (InputStream is = zf.getInputStream(entry)) {
                    assertThat(is.readAllBytes()).isEqualTo(coverImageB);
                }
            }
        }

    }

    @Nested
    @DisplayName("EPUB3 Series Metadata Tests")
    class Epub3SeriesTests {

        private final String collectionsOpf = """
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Book</dc:title>
                    <meta id="old-series" property="belongs-to-collection">Old series</meta>
                    <meta id="old-type" refines="#old-series" property="collection-type">series</meta>
                    <meta refines="#old-type" property="custom">Old type refinement</meta>
                    <meta refines="#old-series" property="group-position">4</meta>
                    <meta id="set" property="belongs-to-collection">Unrelated set</meta>
                    <meta refines="#set" property="collection-type">set</meta>
                    <meta refines="#set" property="group-position">9</meta>
                    <meta id="untyped" property="belongs-to-collection">Untyped collection</meta>
                    <meta id="second-series" property="belongs-to-collection">Other series</meta>
                    <meta refines="#second-series" property="collection-type">series</meta>
                  </metadata>
                </package>""";

        @Test
        void replacingSeriesPreservesOtherCollections() throws Exception {
            File epub = createEpubWithOpf(collectionsOpf, "collections.epub");
            metadata.setSeriesName("New series");
            metadata.setSeriesNumber(2f);

            writer.saveMetadataToFile(epub, metadata, new MetadataClearFlags());

            String content = readOpfContent(epub);
            assertThat(content).contains("New series", "Unrelated set", "Untyped collection", "Other series",
                    "refines=\"#set\"", "refines=\"#second-series\"", ">9<", "Old type refinement",
                    "refines=\"#old-type\"").doesNotContain("Old series");

            metadata.setSeriesName("Updated series again");
            writer.saveMetadataToFile(epub, metadata, new MetadataClearFlags());
            content = readOpfContent(epub);
            assertThat(content).contains("Updated series again", "Other series", "refines=\"#second-series\"")
                    .doesNotContain("New series");
        }

        @Test
        void clearingSeriesPreservesOtherCollectionsEvenIfMetadataContainsSeries() throws Exception {
            File epub = createEpubWithOpf(collectionsOpf, "clear-series.epub");
            metadata.setTitle(null);
            metadata.setAuthors(null);
            metadata.setCategories(null);
            metadata.setSeriesName("Old series");
            metadata.setSeriesNumber(4f);
            MetadataClearFlags clear = new MetadataClearFlags();
            clear.setSeriesName(true);
            clear.setSeriesNumber(true);

            writer.saveMetadataToFile(epub, metadata, clear);

            String content = readOpfContent(epub);
            assertThat(content).contains("Unrelated set", "Untyped collection", "refines=\"#set\"")
                    .doesNotContain("Old series", "refines=\"#old-series\"");
        }

        @Test
        void updatingSeriesNumberKeepsExistingSeriesName() throws Exception {
            File epub = createEpubWithOpf(collectionsOpf, "number-only.epub");
            metadata.setTitle(null);
            metadata.setAuthors(null);
            metadata.setCategories(null);
            metadata.setSeriesNumber(7f);

            writer.saveMetadataToFile(epub, metadata, new MetadataClearFlags());

            String content = readOpfContent(epub);
            assertThat(content).contains("Old series", ">7<", "Unrelated set", "Untyped collection");
            assertThat(content).contains("refines=\"#old-series\"");
        }

        @Test
        void updatingSeriesNameKeepsExistingSeriesNumber() throws Exception {
            File epub = createEpubWithOpf(collectionsOpf, "name-only.epub");
            metadata.setTitle(null);
            metadata.setAuthors(null);
            metadata.setCategories(null);
            metadata.setSeriesName("New series");

            writer.saveMetadataToFile(epub, metadata, new MetadataClearFlags());

            String content = readOpfContent(epub);
            assertThat(content).contains("New series", ">4<", "Unrelated set", "Untyped collection");
            assertThat(content).doesNotContain("Old series");
        }

        @Test
        @DisplayName("Should use belongs-to-collection for series in EPUB3")
        void epub3_shouldUseBelongsToCollection() throws Exception {
            String opfContent = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                            <dc:title>Series Book</dc:title>
                        </metadata>
                    </package>""";

            metadata.setSeriesName("The Dark Tower");
            metadata.setSeriesNumber(3.0f);

            File epubFile = createEpubWithOpf(opfContent, "test-epub3-series-" + System.nanoTime() + ".epub");
            writer.saveMetadataToFile(epubFile, metadata, new MetadataClearFlags());

            String content = readOpfContent(epubFile);
            assertThat(content).contains("property=\"belongs-to-collection\"");
            assertThat(content).contains("The Dark Tower");
            assertThat(content).contains("property=\"collection-type\"");
            assertThat(content).contains("series");
            assertThat(content).contains("property=\"group-position\"");
            assertThat(content).contains(">3<");
        }
    }

    @Nested
    @DisplayName("EPUB2 Series Metadata Tests")
    class Epub2SeriesTests {

        @Test
        void clearingSeriesNumberKeepsExistingSeriesName() throws Exception {
            String opf = """
                    <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Book</dc:title>
                        <meta name="calibre:series" content="Existing series"/>
                        <meta name="calibre:series_index" content="4"/>
                      </metadata>
                    </package>""";
            File epub = createEpubWithOpf(opf, "clear-number.epub");
            metadata.setTitle(null);
            metadata.setAuthors(null);
            metadata.setCategories(null);
            MetadataClearFlags clear = new MetadataClearFlags();
            clear.setSeriesNumber(true);

            writer.saveMetadataToFile(epub, metadata, clear);

            String content = readOpfContent(epub);
            assertThat(content).contains("Existing series", "calibre:series")
                    .doesNotContain("calibre:series_index");
        }

        @Test
        @DisplayName("Should use calibre:series convention for series in EPUB2")
        void epub2_shouldUseCalibreSeries() throws Exception {
            String opfContent = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
                            <dc:title>Series Book</dc:title>
                        </metadata>
                    </package>""";

            metadata.setSeriesName("Wheel of Time");
            metadata.setSeriesNumber(5.0f);

            File epubFile = createEpubWithOpf(opfContent, "test-epub2-series-" + System.nanoTime() + ".epub");
            writer.saveMetadataToFile(epubFile, metadata, new MetadataClearFlags());

            String content = readOpfContent(epubFile);
            assertThat(content).contains("name=\"calibre:series\"");
            assertThat(content).contains("content=\"Wheel of Time\"");
            assertThat(content).contains("name=\"calibre:series_index\"");
            assertThat(content).contains("content=\"5\"");
            // Should NOT contain EPUB3 series constructs
            assertThat(content).doesNotContain("belongs-to-collection");
            assertThat(content).doesNotContain("collection-type");
            assertThat(content).doesNotContain("group-position");
        }
    }

    @Nested
    @DisplayName("EPUB3 Subtitle Tests")
    class Epub3SubtitleTests {

        @Test
        @DisplayName("Should add subtitle as separate dc:title with title-type refinement in EPUB3")
        void epub3_shouldAddSubtitleWithRefinement() throws Exception {
            String opfContent = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                            <dc:title>Main Title</dc:title>
                        </metadata>
                    </package>""";

            metadata.setTitle("Main Title");
            metadata.setSubtitle("A Great Subtitle");

            File epubFile = createEpubWithOpf(opfContent, "test-epub3-subtitle-" + System.nanoTime() + ".epub");
            writer.saveMetadataToFile(epubFile, metadata, new MetadataClearFlags());

            String content = readOpfContent(epubFile);
            assertThat(content).contains("A Great Subtitle");
            assertThat(content).contains("property=\"title-type\"");
            assertThat(content).contains(">subtitle<");
        }

        @Test
        @DisplayName("Should remove refinement when subtitle is removed")
        void epub3_shouldRemoveRefinementWhenRemovingSubtitle() throws Exception {
            String opfContent = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                            <dc:title id="#subtitle-12b45ebf">Subtitle</dc:title>
                            <opf:meta xmlns:opf="http://www.idpf.org/2007/opf" property="title-type" refines="#subtitle-12b45ebf">subtitle</opf:meta>
                        </metadata>
                    </package>""";

            metadata.setSubtitle(null);

            File epubFile = createEpubWithOpf(opfContent, "test-epub3-subtitle-" + System.nanoTime() + ".epub");
            writer.saveMetadataToFile(epubFile, metadata, new MetadataClearFlags());

            String content = readOpfContent(epubFile);
            assertThat(content).doesNotContain(">subtitle</opf:meta>");
        }

        @Test
        @DisplayName("Should do nothing when no subtitle and subtitle is removed")
        void epub3_shouldSaveNoSubtitleWhenAlreadyNoSubtitle() throws Exception {
            String opfContent = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        </metadata>
                    </package>""";

            metadata.setSubtitle(null);

            File epubFile = createEpubWithOpf(opfContent, "test-epub3-subtitle-" + System.nanoTime() + ".epub");
            writer.saveMetadataToFile(epubFile, metadata, new MetadataClearFlags());

            String content = readOpfContent(epubFile);
            assertThat(content).doesNotContain(">subtitle</opf:meta>");
        }
    }

    @Nested
    @DisplayName("EPUB2 Subtitle Tests")
    class Epub2SubtitleTests {

        @Test
        @DisplayName("Should store subtitle via booklore:subtitle metadata in EPUB2 without modifying dc:title")
        void epub2_shouldStoreSubtitleInBookloreMeta() throws Exception {
            String opfContent = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
                            <dc:title>Main Title</dc:title>
                        </metadata>
                    </package>""";

            metadata.setTitle("Main Title");
            metadata.setSubtitle("A Great Subtitle");

            File epubFile = createEpubWithOpf(opfContent, "test-epub2-subtitle-" + System.nanoTime() + ".epub");
            writer.saveMetadataToFile(epubFile, metadata, new MetadataClearFlags());

            String content = readOpfContent(epubFile);
            // Title should remain unchanged (not appended with subtitle)
            assertThat(content).contains(">Main Title<");
            assertThat(content).doesNotContain("Main Title: A Great Subtitle");
            // Subtitle should be stored via booklore:subtitle metadata
            assertThat(content).contains("name=\"booklore:subtitle\"");
            assertThat(content).contains("content=\"A Great Subtitle\"");
            // Should NOT have EPUB3 title-type refinement
            assertThat(content).doesNotContain("property=\"title-type\"");
        }
    }

    @Nested
    @DisplayName("EPUB3 Booklore Metadata Tests")
    class Epub3BookloreMetadataTests {

        @Test
        @DisplayName("Should use property attribute for booklore metadata in EPUB3")
        void epub3_shouldUsePropertyAttribute() throws Exception {
            String opfContent = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                            <dc:title>Test Book</dc:title>
                        </metadata>
                    </package>""";

            metadata.setPageCount(350);

            File epubFile = createEpubWithOpf(opfContent, "test-epub3-booklore-" + System.nanoTime() + ".epub");
            writer.saveMetadataToFile(epubFile, metadata, new MetadataClearFlags());

            String content = readOpfContent(epubFile);
            assertThat(content).contains("property=\"booklore:page_count\"");
            assertThat(content).contains(">350<");
            assertThat(content).contains("prefix=");
            assertThat(content).contains("booklore:");
        }
    }

    @Nested
    @DisplayName("EPUB2 Booklore Metadata Tests")
    class Epub2BookloreMetadataTests {

        @Test
        @DisplayName("Should use name/content attributes for booklore metadata in EPUB2")
        void epub2_shouldUseNameContentAttributes() throws Exception {
            String opfContent = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
                            <dc:title>Test Book</dc:title>
                        </metadata>
                    </package>""";

            metadata.setPageCount(350);

            File epubFile = createEpubWithOpf(opfContent, "test-epub2-booklore-" + System.nanoTime() + ".epub");
            writer.saveMetadataToFile(epubFile, metadata, new MetadataClearFlags());

            String content = readOpfContent(epubFile);
            assertThat(content).contains("name=\"booklore:page_count\"");
            assertThat(content).contains("content=\"350\"");
            // Should NOT have EPUB3 prefix attribute
            assertThat(content).doesNotContain("prefix=");
            // Should NOT use property= form
            assertThat(content).doesNotContain("property=\"booklore:");
        }
    }

    @Nested
    @DisplayName("Cover Tests")
    class CoverTests {
        @Test
        @MockitoSettings(strictness = Strictness.LENIENT)
        void existingTemporaryFileIsNotOverwrittenDuringCoverUpdate() throws Exception {
            File epub = createEpubWithOpf("""
                    <package xmlns="http://www.idpf.org/2007/opf">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Original</dc:title>
                      </metadata>
                    </package>""", "cover-collision.epub");
            byte[] original = Files.readAllBytes(epub.toPath());
            Path temporary = epub.toPath().resolveSibling(epub.getName() + ".tmp");
            Files.writeString(temporary, "existing temporary file");

            assertThatThrownBy(() -> writer.writeCoverToFile(epub, new byte[]{1, 2, 3}))
                    .isInstanceOf(IOException.class)
                    .hasCauseInstanceOf(java.nio.file.FileAlreadyExistsException.class);
            assertThat(Files.readAllBytes(epub.toPath())).isEqualTo(original);
            assertThat(Files.readString(temporary)).isEqualTo("existing temporary file");
        }

        @Test
        void shouldCreateManifestItemIfNoneExist() throws Exception {
            var thumbnailData = new byte[]{0x01, 0x02, 0x03};

            File epubFile = createEpubWithOpf(
                    """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
                            <dc:title>Test Book</dc:title>
                        </metadata>
                    </package>
                    """,
                    "no-cover-" + System.nanoTime() + ".epub"
            );

            writer.replaceCoverImageFromBytes(epubFile, thumbnailData);

            String content = readOpfContent(epubFile);
            assertThat(content).contains("href=\"cover.");
        }

        @Test
        void shouldAddEpub3CoverImageTag() throws Exception {
            var thumbnailData = new byte[]{0x01, 0x02, 0x03};

            File epubFile = createEpubWithOpf(
                    """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
                            <dc:title>Test Book</dc:title>
                        </metadata>
                    </package>
                    """,
                    "no-cover-" + System.nanoTime() + ".epub"
            );

            writer.replaceCoverImageFromBytes(epubFile, thumbnailData);

            String content = readOpfContent(epubFile);
            assertThat(content).contains("properties=\"cover-image\"");
        }

        @Test
        void shouldAllowOnlyOneCoverImagePropertiesEpub3() throws Exception {
            var thumbnailData = new byte[]{0x01, 0x02, 0x03};

            File epubFile = createEpubWithOpf(
                    """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
                            <dc:title>Test Book</dc:title>
                        </metadata>
                        <manifest>
                            <item href="foo" properties="cover-image" media-type="application/octet-stream" />
                            <item href="bar" properties="cover-image" media-type="application/octet-stream" />
                        </manifest>
                    </package>
                    """,
                    "cover-" + System.nanoTime() + ".epub"
            );

            writer.replaceCoverImageFromBytes(epubFile, thumbnailData);

            String content = readOpfContent(epubFile);
            var pattern = Pattern.compile("properties=\"cover-image\"");
            var matcher = pattern.matcher(content);
            int count = 0;
            while (matcher.find()) {
                count++;
            }
            assertThat(count).isEqualTo(1);
        }

        @Test
        void shouldMitigateMediaTypeMismatch() throws Exception {
            var thumbnailData = new byte[]{0x01, 0x02, 0x03};

            File epubFile = createEpubWithOpf(
                    """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
                        </metadata>
                        <manifest>
                            <item href="cover.bin" id="cover" properties="cover-image" media-type="image/png" />
                        </manifest>
                    </package>
                    """,
                    "no-cover-" + System.nanoTime() + ".epub"
            );

            writer.replaceCoverImageFromBytes(epubFile, thumbnailData);

            String content = readOpfContent(epubFile);

            // Removes properties from existing
            assertThat(content).contains("<item href=\"cover.bin\" id=\"cover\" media-type=\"image/png\"/>");

            // And creates a new item with the prop
            assertThat(content).containsPattern("<item href=\"cover-[^\"]+\\.bin\" id=\"cover-[^\"]+\" media-type=\"application/octet-stream\" properties=\"cover-image\"/>.*");
        }

    }

    private Document parseOpf(File epubFile) throws Exception {
        try (ZipFile zf = new ZipFile(epubFile)) {
            ZipEntry ze = zf.getEntry("OEBPS/content.opf");
            try (InputStream is = zf.getInputStream(ze)) {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                DocumentBuilder builder = factory.newDocumentBuilder();
                return builder.parse(is);
            }
        }
    }

    private String readOpfContent(File epubFile) throws IOException {
        try (ZipFile zf = new ZipFile(epubFile)) {
            ZipEntry ze = zf.getEntry("OEBPS/content.opf");
            try (InputStream is = zf.getInputStream(ze)) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    private File createEpubWithOpf(String opfContent, String filename) throws IOException {
        File epubFile = tempDir.resolve(filename).toFile();

        String containerXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                    <rootfiles>
                        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                    </rootfiles>
                </container>
                """;

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(epubFile))) {
            zos.putNextEntry(new ZipEntry("mimetype"));
            zos.write("application/epub+zip".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("META-INF/container.xml"));
            zos.write(containerXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("OEBPS/content.opf"));
            zos.write(opfContent.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        return epubFile;
    }

    private byte[] createEpubWithUnicodeCoverHref() throws IOException {
        String opfContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Test Book with Unicode Cover</dc:title>
                        <dc:creator>Test Author</dc:creator>
                        <meta name="cover" content="cover-image"/>
                    </metadata>
                    <manifest>
                        <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                        <item id="cover-image" href="cover%C3%A1.png" media-type="image/png" properties="cover-image"/>
                        <item id="text" href="index.html" media-type="application/xhtml+xml"/>
                    </manifest>
                    <spine toc="ncx">
                        <itemref idref="text"/>
                    </spine>
                </package>
                """;

        String containerXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                    <rootfiles>
                        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                    </rootfiles>
                </container>
                """;

        String htmlContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE html>
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head>
                    <title>Test</title>
                </head>
                <body>
                    <h1>Test Content</h1>
                </body>
                </html>
                """;

        String ncxContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE ncx PUBLIC "-//NISO//DTD ncx 2005-1//EN"
                    "http://www.daisy.org/z3986/2005/ncx-2005-1.dtd">
                <ncx version="2005-1" xml:lang="en">
                    <head>
                        <meta name="dtb:uid" content="test-book"/>
                    </head>
                    <docTitle>
                        <text>Test Book</text>
                    </docTitle>
                    <navMap>
                        <navPoint id="navpoint-1" playOrder="1">
                            <navLabel>
                                <text>Test</text>
                            </navLabel>
                            <content src="index.html"/>
                        </navPoint>
                    </navMap>
                </ncx>
                """;

        byte[] coverImage = createMinimalPngImage();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {

            zos.putNextEntry(new ZipEntry("mimetype"));
            zos.write("application/epub+zip".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("META-INF/container.xml"));
            zos.write(containerXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("OEBPS/content.opf"));
            zos.write(opfContent.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("OEBPS/index.html"));
            zos.write(htmlContent.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("OEBPS/toc.ncx"));
            zos.write(ncxContent.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            String decodedCoverPath = URLDecoder.decode("cover%C3%A1.png", StandardCharsets.UTF_8);
            zos.putNextEntry(new ZipEntry("OEBPS/" + decodedCoverPath));
            zos.write(coverImage);
            zos.closeEntry();
        }

        return baos.toByteArray();
    }

    private byte[] createMinimalPngImage() {
        return new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D,
                0x49, 0x48, 0x44, 0x52,
                0x00, 0x00, 0x00, 0x01,
                0x00, 0x00, 0x00, 0x01,
                0x08, 0x06,
                0x00, 0x00, 0x00,
                (byte) 0x90, (byte) 0x77, (byte) 0x53, (byte) 0xDE,
                0x00, 0x00, 0x00, 0x0A,
                0x49, 0x44, 0x41, 0x54,
                0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00, 0x05,
                0x00, 0x01,
                0x0D, (byte) 0x0A, 0x2D, (byte) 0xB4,
                0x00, 0x00, 0x00, 0x00,
                0x49, 0x45, 0x4E, 0x44,
                (byte) 0xAE, 0x42, 0x60, (byte) 0x82
        };
    }
}

