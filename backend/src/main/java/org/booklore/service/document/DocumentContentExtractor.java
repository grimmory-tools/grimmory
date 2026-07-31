package org.booklore.service.document;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.hpsf.PropertySetFactory;
import org.apache.poi.hpsf.SummaryInformation;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.Paragraph;
import org.apache.poi.hwpf.usermodel.Range;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.booklore.model.document.DocumentBlock;
import org.booklore.model.document.DocumentContent;
import org.booklore.model.document.DocumentParseResult;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Turns a Word document into the single {@link DocumentContent} model every consumer reads.
 * <p>
 * Two container formats, one traversal shape: {@code .docx} exposes paragraphs and their style ids
 * through {@link XWPFDocument}, while the legacy OLE2 {@code .doc} exposes the same through an
 * {@link HWPFDocument} range whose style names come off the stylesheet. Blank paragraphs are
 * dropped, and because a block's ordinal is its <em>source</em> paragraph index, dropping one leaves
 * a gap rather than shifting everything after it.
 */
@Slf4j
@Component
public class DocumentContentExtractor {

    static final long MAX_DOC_BYTES = 64L * 1024 * 1024;
    static final long MAX_DOCX_EXPANDED_BYTES = 256L * 1024 * 1024;
    static final long MAX_TEXT_BYTES = 16L * 1024 * 1024;
    static final Duration PARSE_TIMEOUT = Duration.ofSeconds(30);

    private static final int MAX_HEADING_LEVEL = 3;
    private static final int PARSER_THREADS = 2;
    private static final int PARSER_QUEUE_CAPACITY = 16;

    private final ExecutorService parseExecutor;
    private final Duration parseTimeout;
    private final long maxDocBytes;
    private final long maxDocxExpandedBytes;
    private final long maxTextBytes;

    public DocumentContentExtractor() {
        this(new ThreadPoolExecutor(
                        PARSER_THREADS,
                        PARSER_THREADS,
                        0L,
                        TimeUnit.MILLISECONDS,
                        new ArrayBlockingQueue<>(PARSER_QUEUE_CAPACITY),
                        Thread.ofVirtual().name("document-parser-", 0).factory(),
                        new ThreadPoolExecutor.AbortPolicy()),
                PARSE_TIMEOUT,
                MAX_DOC_BYTES,
                MAX_DOCX_EXPANDED_BYTES,
                MAX_TEXT_BYTES);
    }

    DocumentContentExtractor(ExecutorService parseExecutor,
                             Duration parseTimeout,
                             long maxDocBytes,
                             long maxDocxExpandedBytes,
                             long maxTextBytes) {
        this.parseExecutor = parseExecutor;
        this.parseTimeout = parseTimeout;
        this.maxDocBytes = maxDocBytes;
        this.maxDocxExpandedBytes = maxDocxExpandedBytes;
        this.maxTextBytes = maxTextBytes;
    }

    public boolean supports(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".doc") || lower.endsWith(".docx");
    }

    public DocumentParseResult parse(File file) {
        if (!supports(file.getName())) {
            return DocumentParseResult.unreadable();
        }

        Future<DocumentContent> future;
        CountDownLatch started = new CountDownLatch(1);
        try {
            future = parseExecutor.submit(() -> {
                started.countDown();
                return extractBounded(file);
            });
        } catch (RejectedExecutionException e) {
            log.warn("Document parser capacity exhausted for {}", file.getName());
            return DocumentParseResult.indeterminate();
        }

        try {
            if (!started.await(parseTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                cancelAndPurge(future);
                log.warn("Document parse could not start within {} seconds for {}",
                        parseTimeout.toSeconds(), file.getName());
                return DocumentParseResult.indeterminate();
            }
            return DocumentParseResult.readable(future.get(parseTimeout.toMillis(), TimeUnit.MILLISECONDS));
        } catch (TimeoutException e) {
            cancelAndPurge(future);
            log.warn("Document parse timed out after {} seconds for {}", parseTimeout.toSeconds(), file.getName());
        } catch (InterruptedException e) {
            cancelAndPurge(future);
            Thread.currentThread().interrupt();
            log.warn("Document parse interrupted for {}", file.getName());
            return DocumentParseResult.indeterminate();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            log.warn("Document is unreadable ({}): {}", cause.getClass().getSimpleName(), file.getName());
            log.debug("Document parse failure for {}", file.getName(), cause);
        }
        return DocumentParseResult.unreadable();
    }

    public DocumentContent extract(File file) throws IOException {
        DocumentParseResult result = parse(file);
        if (!result.isReadable()) {
            throw new IOException("Document cannot be read");
        }
        return result.content();
    }

    DocumentContent extractBounded(File file) throws IOException {
        String lower = file.getName().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".docx")) {
            verifyDocxExpandedSize(file);
            return extractDocx(file);
        }
        verifyDocSize(file);
        return extractOle2(file);
    }

    private DocumentContent extractDocx(File file) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(Files.newInputStream(file.toPath()))) {
            List<XWPFParagraph> paragraphs = doc.getParagraphs();
            List<DocumentBlock> blocks = new ArrayList<>(paragraphs.size());
            long textBytes = 0;
            for (int i = 0; i < paragraphs.size(); i++) {
                ensureNotInterrupted();
                XWPFParagraph paragraph = paragraphs.get(i);
                textBytes = addBlock(blocks, i, paragraph.getStyleID(), paragraph.getText(), textBytes);
            }
            var core = doc.getProperties().getCoreProperties();
            BoundedText title = boundedText(core.getTitle(), textBytes);
            BoundedText author = boundedText(core.getCreator(), title.totalBytes());
            return new DocumentContent(
                    blocks,
                    title.value(),
                    author.value(),
                    toLocalDate(core.getCreated()));
        }
    }

    private DocumentContent extractOle2(File file) throws IOException {
        try (POIFSFileSystem fs = new POIFSFileSystem(file, true);
             HWPFDocument doc = new HWPFDocument(fs)) {
            Range range = doc.getRange();
            List<DocumentBlock> blocks = new ArrayList<>(range.numParagraphs());
            long textBytes = 0;
            for (int i = 0; i < range.numParagraphs(); i++) {
                ensureNotInterrupted();
                Paragraph paragraph = range.getParagraph(i);
                textBytes = addBlock(blocks, i, styleNameOf(doc, paragraph), paragraph.text(), textBytes);
            }
            SummaryInformation summary = readSummaryInformation(fs);
            BoundedText title = boundedText(summary == null ? null : summary.getTitle(), textBytes);
            BoundedText author = boundedText(summary == null ? null : summary.getAuthor(), title.totalBytes());
            return new DocumentContent(
                    blocks,
                    title.value(),
                    author.value(),
                    summary == null ? null : toLocalDate(summary.getCreateDateTime()));
        }
    }

    private SummaryInformation readSummaryInformation(POIFSFileSystem fs) {
        try {
            if (!fs.getRoot().hasEntry(SummaryInformation.DEFAULT_STREAM_NAME)) {
                return null;
            }
            return (SummaryInformation) PropertySetFactory.create(
                    fs.getRoot(), SummaryInformation.DEFAULT_STREAM_NAME);
        } catch (Exception e) {
            log.debug("Unable to read OLE SummaryInformation", e);
            return null;
        }
    }

    private String styleNameOf(HWPFDocument doc, Paragraph paragraph) {
        try {
            var description = doc.getStyleSheet().getStyleDescription(paragraph.getStyleIndex());
            return description == null ? null : description.getName();
        } catch (RuntimeException e) {
            // A malformed stylesheet costs the heading hierarchy, not the text.
            return null;
        }
    }

    private long addBlock(List<DocumentBlock> blocks,
                          int sourceIndex,
                          String styleName,
                          String rawText,
                          long retainedTextBytes) throws DocumentLimitExceededException {
        String text = StringUtils.trimToNull(rawText == null ? null : rawText.replace('\r', ' '));
        if (text == null) {
            return retainedTextBytes;
        }
        BoundedText bounded = boundedText(text, retainedTextBytes);
        blocks.add(new DocumentBlock(sourceIndex, headingLevel(styleName), text));
        return bounded.totalBytes();
    }

    private BoundedText boundedText(String rawText, long retainedTextBytes) throws DocumentLimitExceededException {
        String text = StringUtils.trimToNull(rawText);
        if (text == null) {
            return new BoundedText(null, retainedTextBytes);
        }
        long remainingBytes = maxTextBytes - retainedTextBytes;
        if (remainingBytes < 0 || text.length() > remainingBytes) {
            throw new DocumentLimitExceededException("extracted text exceeds limit");
        }
        long nextTextBytes = retainedTextBytes + text.getBytes(StandardCharsets.UTF_8).length;
        if (nextTextBytes > maxTextBytes) {
            throw new DocumentLimitExceededException("extracted text exceeds limit");
        }
        return new BoundedText(text, nextTextBytes);
    }

    private void verifyDocSize(File file) throws IOException {
        if (Files.size(file.toPath()) > maxDocBytes) {
            throw new DocumentLimitExceededException("legacy document exceeds input limit");
        }
    }

    private void verifyDocxExpandedSize(File file) throws IOException {
        long expandedBytes = 0;
        try (ZipFile zip = new ZipFile(file)) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ensureNotInterrupted();
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                long size = entry.getSize();
                if (size < 0 || size > maxDocxExpandedBytes - expandedBytes) {
                    throw new DocumentLimitExceededException("expanded OOXML package exceeds limit");
                }
                expandedBytes += size;
            }
        }
    }

    private void ensureNotInterrupted() throws IOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new IOException("document parse interrupted");
        }
    }

    private void cancelAndPurge(Future<?> future) {
        future.cancel(true);
        if (parseExecutor instanceof ThreadPoolExecutor threadPool) {
            threadPool.purge();
        }
    }

    private LocalDate toLocalDate(java.util.Date created) {
        return created == null ? null : created.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
    }

    /**
     * Word writes the built-in outline styles as {@code Heading1} in OOXML style ids and
     * {@code heading 1} in legacy stylesheet names, so both collapse to the same comparison.
     */
    private int headingLevel(String styleName) {
        if (styleName == null) {
            return 0;
        }
        String normalized = styleName.toLowerCase(Locale.ROOT).replace(" ", "");
        if (!normalized.startsWith("heading")) {
            return 0;
        }
        String suffix = normalized.substring("heading".length());
        if (suffix.length() != 1 || !Character.isDigit(suffix.charAt(0))) {
            return 0;
        }
        int level = suffix.charAt(0) - '0';
        return level >= 1 && level <= MAX_HEADING_LEVEL ? level : 0;
    }

    private static final class DocumentLimitExceededException extends IOException {
        private DocumentLimitExceededException(String message) {
            super(message);
        }
    }

    private record BoundedText(String value, long totalBytes) {
    }
}
