package org.booklore.service.document;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.crypt.EncryptionMode;
import org.apache.poi.poifs.crypt.Encryptor;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.booklore.model.document.DocumentBlock;
import org.booklore.model.document.DocumentContent;
import org.booklore.model.enums.DocumentParseStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentContentExtractorTest {

    private final DocumentContentExtractor extractor = new DocumentContentExtractor();
    private ExecutorService testExecutor;

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        if (testExecutor != null) {
            testExecutor.shutdownNow();
        }
    }

    private Path writeDocx(String name, DocxBuilder builder) throws Exception {
        Path file = tempDir.resolve(name);
        try (XWPFDocument doc = new XWPFDocument()) {
            builder.build(doc);
            try (OutputStream out = Files.newOutputStream(file)) {
                doc.write(out);
            }
        }
        return file;
    }

    @FunctionalInterface
    private interface DocxBuilder {
        void build(XWPFDocument doc);
    }

    private void paragraph(XWPFDocument doc, String style, String text) {
        XWPFParagraph p = doc.createParagraph();
        if (style != null) {
            p.setStyle(style);
        }
        if (text != null) {
            p.createRun().setText(text);
        }
    }

    @Nested
    class Ordinals {

        @Test
        void skippedParagraphsLeaveAGapRatherThanShiftingWhatFollows() throws Exception {
            Path file = writeDocx("gaps.docx", doc -> {
                paragraph(doc, "Heading1", "Chapter One");
                paragraph(doc, null, null);            // blank, dropped
                paragraph(doc, null, "   ");           // whitespace only, dropped
                paragraph(doc, null, "Body text");
            });

            DocumentContent content = extractor.extract(file.toFile());

            // Source indices 0 and 3 survive: the two dropped paragraphs leave a hole instead of
            // pulling "Body text" down to ordinal 1.
            assertThat(content.blocks()).extracting(DocumentBlock::ordinal).containsExactly(0, 3);
        }
    }

    @Nested
    class Headings {

        @Test
        void readsWordOutlineStylesAsLevels() throws Exception {
            Path file = writeDocx("headings.docx", doc -> {
                paragraph(doc, "Heading1", "Part");
                paragraph(doc, "Heading3", "Detail");
                paragraph(doc, null, "Body");
            });

            DocumentContent content = extractor.extract(file.toFile());

            assertThat(content.blocks()).extracting(DocumentBlock::headingLevel).containsExactly(1, 3, 0);
        }

        @Test
        void treatsUnknownAndDeeperStylesAsBodyText() throws Exception {
            Path file = writeDocx("styles.docx", doc -> {
                paragraph(doc, "Heading7", "Too deep for the reader's contents");
                paragraph(doc, "Quote", "Not a heading at all");
            });

            DocumentContent content = extractor.extract(file.toFile());

            assertThat(content.blocks()).extracting(DocumentBlock::headingLevel).containsOnly(0);
        }
    }

    @Test
    void recognisesBothWordContainerFormats() {
        assertThat(extractor.supports("report.doc")).isTrue();
        assertThat(extractor.supports("report.DOCX")).isTrue();
        assertThat(extractor.supports("report.epub")).isFalse();
    }

    @Test
    void returnsCorePropertiesAndBlocksFromOneDocxParse() throws Exception {
        Path file = writeDocx("properties.docx", doc -> {
            doc.getProperties().getCoreProperties().setTitle("Bounded Parsing");
            doc.getProperties().getCoreProperties().setCreator("BookLib");
            paragraph(doc, null, "Body text");
        });

        var result = extractor.parse(file.toFile());

        assertThat(result.status()).isEqualTo(DocumentParseStatus.READABLE);
        assertThat(result.content().title()).isEqualTo("Bounded Parsing");
        assertThat(result.content().author()).isEqualTo("BookLib");
        assertThat(result.content().blocks()).extracting(DocumentBlock::text).containsExactly("Body text");
    }

    @Test
    void malformedLegacyDocumentReturnsUnreadableOutcome() throws Exception {
        Path file = tempDir.resolve("broken.doc");
        Files.writeString(file, "not an OLE2 document");

        assertThat(extractor.parse(file.toFile()).status()).isEqualTo(DocumentParseStatus.UNREADABLE);
    }

    @Test
    void protectedDocxReturnsUnreadableOutcome() throws Exception {
        Path source = writeDocx("plain.docx", doc -> paragraph(doc, null, "private"));
        Path encrypted = tempDir.resolve("protected.docx");
        try (POIFSFileSystem fs = new POIFSFileSystem();
             OPCPackage pkg = OPCPackage.open(source.toFile())) {
            EncryptionInfo info = new EncryptionInfo(EncryptionMode.agile);
            Encryptor encryptor = info.getEncryptor();
            encryptor.confirmPassword("secret");
            try (OutputStream encryptedData = encryptor.getDataStream(fs)) {
                pkg.save(encryptedData);
            }
            try (OutputStream out = Files.newOutputStream(encrypted)) {
                fs.writeFilesystem(out);
            }
        }

        assertThat(extractor.parse(encrypted.toFile()).status()).isEqualTo(DocumentParseStatus.UNREADABLE);
    }

    @Test
    void rejectsLegacyDocumentBeyondInputBound() throws Exception {
        Path file = tempDir.resolve("large.doc");
        Files.write(file, new byte[2]);
        DocumentContentExtractor bounded = boundedExtractor(Duration.ofSeconds(1), 1, 1024, 1024);

        assertThat(bounded.parse(file.toFile()).status()).isEqualTo(DocumentParseStatus.UNREADABLE);
    }

    @Test
    void rejectsDocxBeyondExpandedPackageBound() throws Exception {
        Path file = writeDocx("expanded.docx", doc -> paragraph(doc, null, "body"));
        DocumentContentExtractor bounded = boundedExtractor(Duration.ofSeconds(1), 1024, 1, 1024);

        assertThat(bounded.parse(file.toFile()).status()).isEqualTo(DocumentParseStatus.UNREADABLE);
    }

    @Test
    void rejectsDocxBeyondRetainedTextBound() throws Exception {
        Path file = writeDocx("text.docx", doc -> paragraph(doc, null, "two bytes"));
        DocumentContentExtractor bounded = boundedExtractor(
                Duration.ofSeconds(1), 1024, 1024 * 1024, 1);

        assertThat(bounded.parse(file.toFile()).status()).isEqualTo(DocumentParseStatus.UNREADABLE);
    }

    @Test
    void includesDocumentPropertiesInRetainedTextBound() throws Exception {
        Path file = writeDocx("properties-limit.docx",
                doc -> doc.getProperties().getCoreProperties().setCreator("oversized"));
        DocumentContentExtractor bounded = boundedExtractor(
                Duration.ofSeconds(1), 1024, 1024 * 1024, 1);

        assertThat(bounded.parse(file.toFile()).status()).isEqualTo(DocumentParseStatus.UNREADABLE);
    }

    @Test
    void leavesVerdictUnknownWhenParseCannotStartBeforeTimeout() throws Exception {
        Path file = writeDocx("queued.docx", doc -> paragraph(doc, null, "body"));
        testExecutor = Executors.newSingleThreadExecutor();
        CountDownLatch occupied = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        testExecutor.submit(() -> {
            occupied.countDown();
            release.await();
            return null;
        });
        occupied.await();
        DocumentContentExtractor bounded = new DocumentContentExtractor(
                testExecutor, Duration.ofMillis(20), 1024, 1024 * 1024, 1024);

        var result = bounded.parse(file.toFile());
        release.countDown();

        assertThat(result.status()).isNull();
    }

    @Test
    void returnsUnreadableAndInterruptsActiveParseAtTimeout() throws Exception {
        Path file = writeDocx("slow.docx", doc -> paragraph(doc, null, "body"));
        testExecutor = Executors.newSingleThreadExecutor();
        CountDownLatch parseStarted = new CountDownLatch(1);
        CountDownLatch parseStopped = new CountDownLatch(1);
        DocumentContentExtractor bounded = new DocumentContentExtractor(
                testExecutor, Duration.ofMillis(20), 1024, 1024 * 1024, 1024) {
            @Override
            DocumentContent extractBounded(java.io.File ignored) throws IOException {
                parseStarted.countDown();
                try {
                    new CountDownLatch(1).await();
                    throw new AssertionError("unreachable");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("cancelled", e);
                } finally {
                    parseStopped.countDown();
                }
            }
        };

        var result = bounded.parse(file.toFile());

        assertThat(parseStarted.getCount()).isZero();
        assertThat(result.status()).isEqualTo(DocumentParseStatus.UNREADABLE);
        assertThat(parseStopped.await(1, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
    }

    private DocumentContentExtractor boundedExtractor(Duration timeout,
                                                       long maxDocBytes,
                                                       long maxDocxExpandedBytes,
                                                       long maxTextBytes) {
        testExecutor = Executors.newSingleThreadExecutor();
        return new DocumentContentExtractor(
                testExecutor, timeout, maxDocBytes, maxDocxExpandedBytes, maxTextBytes);
    }
}
