package org.booklore.service.kobo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.service.ArchiveService;
import org.booklore.util.MimeDetector;
import org.booklore.util.SecureXmlUtils;
import org.booklore.util.epub.CoverDetectorService;
import org.booklore.util.epub.EpubContentReader;
import org.booklore.util.epub.EpubContentWriter;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class KepubConversionService {
    private static final String OPF_NS = "http://www.idpf.org/2007/opf";

    private static final Set<String> HTML_MEDIA_TYPES = Set.of(
            "text/html",
            "application/xhtml",
            "application/xhtml+xml"
    );

    private static final Set<String> IGNORED_FILENAMES = Set.copyOf(
            Stream.of(
                "",
                ".DS_STORE",
                "iTunesMetadata.plist",
                "iTunesArtwork.plist",
                "calibre_bookmarks.txt",
                "thumbs.db"
            ).map(String::toLowerCase).toList()
    );

    private static final Set<String> IGNORED_DIRECTORIES = Set.copyOf(
            Stream.of(
                "__MACOSX"
            ).map(String::toLowerCase).toList()
    );

    private final ArchiveService archiveService;
    private final KepubHtmlConversionService kepubHtmlConversionService;
    private final CoverDetectorService coverDetectorService;

    private void transformHTML(Path path, boolean forceEnableHyphenation) throws IOException {
        var transformedHtml = kepubHtmlConversionService.transform(
                Files.readString(path),
                forceEnableHyphenation
        );

        Files.writeString(path, transformedHtml, StandardCharsets.UTF_8);
    }

    /**
     * Adds the cover-image property to the cover item in the OPF manifest.
     * Kobo devices will only support the EPUB3 "properties" attribute with
     * the `cover-image` tag.
     * <a href="https://www.w3.org/TR/epub-33/#sec-item-resource-properties">
     *     Read more on the EPUB3 spec.
     * </a>
     */
    private void transformOPFCoverImage(Document opfDoc, String coverHref) {
        if (coverHref == null) {
            return;
        }

        NodeList manifestList = opfDoc.getElementsByTagNameNS(OPF_NS, "manifest");

        if (manifestList.getLength() == 0) {
            return;
        }

        if (manifestList.item(0) instanceof Element manifest) {
            NodeList itemList = manifest.getElementsByTagNameNS(OPF_NS, "item");

            for (int i = 0; i < itemList.getLength(); i++) {
                if (itemList.item(i) instanceof Element item) {
                    if (coverHref.equals(item.getAttribute("href"))) {
                        String properties = item.getAttribute("properties");

                        if (properties.isBlank()) {
                            properties = "cover-image";
                        } else {
                            properties += " cover-image";
                        }

                        item.setAttribute("properties", properties);
                    }
                }
            }
        }
    }

    private void transformOPF(Path path, String coverHref) throws IOException {
        try (var outputStream = new ByteArrayOutputStream()) {
            try (var inputStream = Files.newInputStream(path)){
                var builder = SecureXmlUtils.createSecureDocumentBuilder(true);
                var opfDoc = builder.parse(inputStream);

                transformOPFCoverImage(opfDoc, coverHref);

                Transformer transformer = TransformerFactory.newInstance().newTransformer();
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
                transformer.transform(new DOMSource(opfDoc), new StreamResult(outputStream));
            } catch (TransformerException | SAXException | ParserConfigurationException exception) {
                log.error("unable to parse OPF");
                throw new IOException("unable to parse OPF", exception);
            }

            // After we close the InputStream we can write the file.
            Files.writeString(path, outputStream.toString(StandardCharsets.UTF_8));
        }
    }

    private void transformExtractedEpubHtml(
            Path path,
            boolean forceEnableHyphenation
    ) throws IOException {
        try (var files = Files.walk(path)) {
            var allFiles = files
                    .filter(Files::isRegularFile)
                    .toList();

            for (var file : allFiles) {
                String mediaType = MimeDetector.detect(file);

                if (HTML_MEDIA_TYPES.contains(mediaType)) {
                    transformHTML(file, forceEnableHyphenation);
                }
            }
        }
    }

    private boolean isAcceptedEntry(ArchiveService.Entry entry) {
        String[] parts = entry.name().split("/");

        if (parts.length == 0) {
            // No empty path items allowed.
            return false;
        }

        String filename = parts[parts.length - 1];
        if (IGNORED_FILENAMES.contains(filename.toLowerCase())) {
            return false;
        }

        // Check everything except the "filename" (the last entry)
        for (int i = 0; i < parts.length - 1; i++) {
            if (IGNORED_DIRECTORIES.contains(parts[i].toLowerCase())) {
                return false;
           }
        }

        return true;
    }

    public void convertEpubToKepub(Path inputPath, Path outputPath, boolean forceEnableHyphenation) throws IOException {
        validateInputs(inputPath);

        String coverArchivePath = coverDetectorService.detectCoverImagePath(inputPath);

        var tempDir = Files.createTempDirectory("grimmory-kepubify");
        try {
            archiveService.extractToDirectory(inputPath, tempDir, this::isAcceptedEntry);

            transformExtractedEpubHtml(tempDir, forceEnableHyphenation);

            try {
                Path opfPath = EpubContentReader.findOPFInExtractedEpub(tempDir);

                String coverHref = null;
                if (coverArchivePath != null) {
                    Path coverPath = tempDir.resolve(coverArchivePath);
                    coverHref = opfPath.getParent().relativize(coverPath).toString();
                }

                transformOPF(opfPath, coverHref);
            } catch (Exception e) {
                log.warn("Unable to transform OPF", e);
            }

            EpubContentWriter.createEpubFromDirectory(tempDir, outputPath);
        } finally {
            if (tempDir != null) {
                try {
                    FileSystemUtils.deleteRecursively(tempDir);
                    log.debug("Deleted temporary directory {}", tempDir);
                } catch (Exception e) {
                    log.warn("Failed to delete temporary directory {}: {}", tempDir, e.getMessage());
                }
            }
        }

        log.info(
                "Successfully converted {} to {} (size: {} bytes)",
                inputPath.getFileName(),
                outputPath.getFileName(),
                Files.size(outputPath)
        );
    }

    public File convertEpubToKepub(File epubFile, File tempDir, boolean forceEnableHyphenation) throws IOException {
        var outputPath = Files.createTempFile(tempDir.toPath(), "grimmory", ".kepub.epub");
        convertEpubToKepub(epubFile.toPath(), outputPath, forceEnableHyphenation);
        return outputPath.toFile();
    }

    private void validateInputs(Path inputPath) {
        if (inputPath == null || !Files.isRegularFile(inputPath)) {
            throw new IllegalArgumentException("Invalid EPUB file: " + inputPath);
        }
    }
}
