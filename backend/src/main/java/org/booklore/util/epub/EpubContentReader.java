package org.booklore.util.epub;

import org.booklore.util.SecureXmlUtils;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.MediaType;
import org.grimmory.epub4j.domain.MediaTypes;
import org.grimmory.epub4j.domain.Resource;
import org.grimmory.epub4j.domain.Spine;
import org.grimmory.epub4j.epub.EpubReader;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class EpubContentReader {
    private static final String CONTAINER_NS = "urn:oasis:names:tc:opendocument:xmlns:container";

    private static final List<MediaType> MEDIA_TYPES = new ArrayList<>();
    static {
        MEDIA_TYPES.addAll(Arrays.asList(MediaTypes.mediaTypes));
        MEDIA_TYPES.add(null);
    }

    private EpubContentReader() {
    }

    public static String getSpineItemContent(File epubFile, int spineIndex) {
        try {
            Book epub = new EpubReader().readEpubLazy(epubFile.toPath(), "UTF-8", MEDIA_TYPES);

            Spine spine = epub.getSpine();
            if (spine == null || spine.size() == 0) {
                throw new EpubReadException("EPUB has no spine: " + epubFile.getName());
            }

            if (spineIndex < 0 || spineIndex >= spine.size()) {
                throw new EpubReadException(
                        String.format("Spine index %d out of bounds (0-%d) for: %s",
                                spineIndex, spine.size() - 1, epubFile.getName()));
            }

            Resource resource = spine.getResource(spineIndex);
            if (resource == null) {
                throw new EpubReadException(
                        String.format("Spine item %d has no resource in: %s", spineIndex, epubFile.getName()));
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            resource.writeTo(baos);
            return baos.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new EpubReadException("Failed to read EPUB file: " + epubFile.getName(), e);
        }
    }

    public static String getSpineItemContent(Path epubPath, int spineIndex) {
        return getSpineItemContent(epubPath.toFile(), spineIndex);
    }

    public static int getSpineSize(File epubFile) {
        try {
            Book epub = new EpubReader().readEpubLazy(epubFile.toPath(), "UTF-8", MEDIA_TYPES);
            Spine spine = epub.getSpine();
            return spine != null ? spine.size() : 0;
        } catch (IOException e) {
            throw new EpubReadException("Failed to read EPUB file: " + epubFile.getName(), e);
        }
    }

    public static String getSpineItemHref(File epubFile, int spineIndex) {
        try {
            Book epub = new EpubReader().readEpubLazy(epubFile.toPath(), "UTF-8", MEDIA_TYPES);

            Spine spine = epub.getSpine();
            if (spine == null || spineIndex < 0 || spineIndex >= spine.size()) {
                return null;
            }

            Resource resource = spine.getResource(spineIndex);
            return resource != null ? resource.getHref() : null;
        } catch (IOException e) {
            log.warn("Failed to get spine item href from EPUB: {}", epubFile.getName(), e);
            return null;
        }
    }

    public static List<String> getAllSpineItemHrefs(File epubFile) {
        List<String> hrefs = new ArrayList<>();
        try {
            Book epub = new EpubReader().readEpubLazy(epubFile.toPath(), "UTF-8", MEDIA_TYPES);

            Spine spine = epub.getSpine();
            if (spine != null) {
                for (int i = 0; i < spine.size(); i++) {
                    Resource resource = spine.getResource(i);
                    hrefs.add(resource != null ? resource.getHref() : null);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to get spine items from EPUB: {}", epubFile.getName(), e);
        }
        return hrefs;
    }

    public static Path findOPFInExtractedEpub(Path path) throws IOException {
        Path containerXml = path.resolve("META-INF/container.xml");
        if (!Files.exists(containerXml)) {
            throw new IOException("container.xml not found at expected location: " + containerXml);
        }

        String opfHref;
        try (var is = Files.newInputStream(containerXml)) {
            opfHref = getOPFHref(is);
        }

        Path opfPath = path.resolve(opfHref).normalize();

        if (!opfPath.startsWith(path)) {
            throw new IOException("OPF Path is outside of extracted root");
        }

        return opfPath;
    }

    public static String getOPFHref(InputStream xmlInputStream) throws IOException {
        Document containerDoc;
        try {
            DocumentBuilder builder = SecureXmlUtils.createSecureDocumentBuilder(true);
            containerDoc = builder.parse(xmlInputStream);
        } catch (SAXException | ParserConfigurationException e) {
            throw new IOException("Unable to parse container.xml", e);
        }

        Node rootfile = containerDoc.getElementsByTagNameNS(CONTAINER_NS, "rootfile").item(0);
        if (rootfile == null) {
            throw new IOException("No <rootfile> found in container.xml");
        }

        String opfPathAttribute = ((Element) rootfile).getAttribute("full-path");
        if (opfPathAttribute.isBlank()) {
            throw new IOException("Missing or empty 'full-path' attribute in <rootfile>");
        }

        // The `full-path` attribute may be percent encoded.
        opfPathAttribute = opfPathAttribute.replaceAll("\\+", "%2b");
        opfPathAttribute = URLDecoder.decode(opfPathAttribute, StandardCharsets.UTF_8);

        return opfPathAttribute;
    }

    public static class EpubReadException extends RuntimeException {
        public EpubReadException(String message) {
            super(message);
        }

        public EpubReadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
