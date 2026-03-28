package org.booklore.service.kobo;

import lombok.extern.slf4j.Slf4j;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import org.booklore.model.dto.kobo.KoboSpanPositionMap;
import org.booklore.util.SecureXmlUtils;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Service;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.booklore.service.kobo.KoboEpubUtils.clampUnit;
import static org.booklore.service.kobo.KoboEpubUtils.normalizeHref;

@Service
@Slf4j
public class KoboSpanMapExtractionService {

    private static final String CONTAINER_PATH = "META-INF/container.xml";
    private static final String CONTAINER_NS = "urn:oasis:names:tc:opendocument:xmlns:container";
    private static final String OPF_NS = "http://www.idpf.org/2007/opf";

    public KoboSpanPositionMap extractFromKepub(File kepubFile) throws IOException {
        validateKepub(kepubFile);

        try (ZipFile zipFile = new ZipFile(kepubFile)) {
            String opfPath = readOpfPath(zipFile);
            org.w3c.dom.Document opfDocument = parseXmlEntry(zipFile, opfPath);
            String opfRootPath = opfPath.contains("/") ? opfPath.substring(0, opfPath.lastIndexOf('/') + 1) : "";

            Map<String, ManifestItem> manifestById = parseManifest(opfDocument, opfRootPath);
            List<ManifestItem> spineItems = parseSpine(opfDocument, manifestById);
            List<ExtractedChapter> extractedChapters = new ArrayList<>();

            for (int i = 0; i < spineItems.size(); i++) {
                ExtractedChapter chapter = extractChapter(zipFile, spineItems.get(i), i);
                if (chapter != null) {
                    extractedChapters.add(chapter);
                }
            }

            if (extractedChapters.isEmpty()) {
                return new KoboSpanPositionMap(List.of());
            }

            long totalContentLength = extractedChapters.stream()
                    .mapToLong(ExtractedChapter::contentLength)
                    .sum();
            long accumulatedLength = 0L;
            List<KoboSpanPositionMap.Chapter> chapters = new ArrayList<>(extractedChapters.size());

            for (int i = 0; i < extractedChapters.size(); i++) {
                ExtractedChapter chapter = extractedChapters.get(i);
                float startProgress = accumulatedLength / (float) totalContentLength;
                accumulatedLength += chapter.contentLength();
                float endProgress = i == extractedChapters.size() - 1
                        ? 1f
                        : accumulatedLength / (float) totalContentLength;

                chapters.add(new KoboSpanPositionMap.Chapter(
                        chapter.sourceHref(),
                        normalizeHref(chapter.sourceHref()),
                        chapter.spineIndex(),
                        clampUnit(startProgress),
                        clampUnit(endProgress),
                        chapter.spans()));
            }

            return new KoboSpanPositionMap(chapters);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to extract Kobo span map from " + kepubFile.getName(), e);
        }
    }

    private void validateKepub(File kepubFile) {
        if (kepubFile == null || !kepubFile.isFile()) {
            throw new IllegalArgumentException("Invalid KEPUB file: " + kepubFile);
        }
    }

    private String readOpfPath(ZipFile zipFile) throws Exception {
        org.w3c.dom.Document containerDocument = parseXmlEntry(zipFile, CONTAINER_PATH);
        NodeList rootfiles = containerDocument.getElementsByTagNameNS(CONTAINER_NS, "rootfile");
        if (rootfiles.getLength() == 0) {
            rootfiles = containerDocument.getElementsByTagName("rootfile");
        }
        if (rootfiles.getLength() == 0) {
            throw new IOException("No rootfile found in container.xml");
        }

        Element rootfile = (Element) rootfiles.item(0);
        String fullPath = rootfile.getAttribute("full-path");
        if (fullPath == null || fullPath.isEmpty()) {
            throw new IOException("No full-path attribute found in container.xml");
        }
        return fullPath;
    }

    private org.w3c.dom.Document parseXmlEntry(ZipFile zipFile, String entryName) throws Exception {
        FileHeader fileHeader = findFileHeader(zipFile, entryName);
        if (fileHeader == null) {
            throw new IOException("Entry not found in archive: " + entryName);
        }

        try (InputStream inputStream = zipFile.getInputStream(fileHeader)) {
            return SecureXmlUtils.createSecureDocumentBuilder(true).parse(inputStream);
        }
    }

    private Map<String, ManifestItem> parseManifest(org.w3c.dom.Document opfDocument, String opfRootPath) {
        Map<String, ManifestItem> manifestById = new HashMap<>();
        NodeList items = opfDocument.getElementsByTagNameNS(OPF_NS, "item");
        if (items.getLength() == 0) {
            items = opfDocument.getElementsByTagName("item");
        }

        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            String id = item.getAttribute("id");
            String href = resolveHref(item.getAttribute("href"), opfRootPath);
            if (id != null && !id.isBlank() && href != null && !href.isBlank()) {
                manifestById.put(id, new ManifestItem(href));
            }
        }
        return manifestById;
    }

    private List<ManifestItem> parseSpine(org.w3c.dom.Document opfDocument, Map<String, ManifestItem> manifestById) {
        List<ManifestItem> spineItems = new ArrayList<>();
        NodeList itemrefs = opfDocument.getElementsByTagNameNS(OPF_NS, "itemref");
        if (itemrefs.getLength() == 0) {
            itemrefs = opfDocument.getElementsByTagName("itemref");
        }

        for (int i = 0; i < itemrefs.getLength(); i++) {
            Element itemref = (Element) itemrefs.item(i);
            ManifestItem manifestItem = manifestById.get(itemref.getAttribute("idref"));
            if (manifestItem != null) {
                spineItems.add(manifestItem);
            }
        }
        return spineItems;
    }

    private ExtractedChapter extractChapter(ZipFile zipFile, ManifestItem manifestItem, int spineIndex) throws IOException {
        FileHeader fileHeader = findFileHeader(zipFile, manifestItem.href());
        if (fileHeader == null || fileHeader.isDirectory()) {
            log.debug("Skipping missing Kobo span chapter {}", manifestItem.href());
            return null;
        }

        String html;
        try (InputStream inputStream = zipFile.getInputStream(fileHeader)) {
            html = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }

        if (html.isBlank()) {
            return null;
        }

        org.jsoup.nodes.Document document = Jsoup.parse(html, "", Parser.htmlParser().setTrackPosition(true));
        List<KoboSpanPositionMap.Span> spans = document.select("span.koboSpan[id]").stream()
                .map(span -> {
                    if (span.id().isBlank() || span.sourceRange() == null) {
                        return null;
                    }
                    return new KoboSpanPositionMap.Span(
                            span.id(),
                            clampUnit(span.sourceRange().endPos() / (float) Math.max(html.length(), 1)));
                })
                .filter(Objects::nonNull)
                .toList();

        return new ExtractedChapter(
                fileHeader.getFileName(),
                spineIndex,
                Math.max(html.length(), 1),
                spans);
    }

    private FileHeader findFileHeader(ZipFile zipFile, String href) throws IOException {
        String normalizedHref = normalizeHref(href);
        return zipFile.getFileHeaders().stream()
                .filter(header -> !header.isDirectory())
                .filter(header -> {
                    String normalizedEntry = normalizeHref(header.getFileName());
                    return normalizedEntry.equals(normalizedHref)
                            || normalizedEntry.endsWith("/" + normalizedHref);
                })
                .min(Comparator.comparingInt(header -> {
                    String normalizedEntry = normalizeHref(header.getFileName());
                    return normalizedEntry.equals(normalizedHref) ? 0 : normalizedEntry.length();
                }))
                .orElse(null);
    }

    private String resolveHref(String href, String rootPath) {
        if (href == null || href.isBlank()) {
            return null;
        }
        String decodedHref = KoboEpubUtils.decodeHrefPath(href);
        if (decodedHref.startsWith("/")) {
            return decodedHref.substring(1);
        }
        if (rootPath == null || rootPath.isEmpty()) {
            return decodedHref;
        }
        return Path.of(rootPath).resolve(decodedHref).normalize().toString().replace('\\', '/');
    }

    private record ManifestItem(String href) {
    }

    private record ExtractedChapter(String sourceHref,
                                    int spineIndex,
                                    int contentLength,
                                    List<KoboSpanPositionMap.Span> spans) {
    }
}
