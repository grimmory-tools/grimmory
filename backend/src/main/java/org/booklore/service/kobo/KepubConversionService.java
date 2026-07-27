package org.booklore.service.kobo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.util.SecureXmlUtils;
import org.springframework.stereotype.Service;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.MediaTypes;
import org.grimmory.epub4j.domain.Resource;
import org.grimmory.epub4j.epub.EpubReader;
import org.grimmory.epub4j.epub.EpubWriter;
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

    private final EpubReader epubReader;
    private final KepubHtmlConversionService kepubHtmlConversionService;

    public KepubConversionService() {
        this(
                new EpubReader(),
                new KepubHtmlConversionService()
        );
    }

    private String getMediaType(Resource resource) {
        if (resource == null || resource.getMediaType() == null) {
            return null;
        }

        return resource.getMediaType().toString().toLowerCase();
    }

    private Resource getTransformedContentResource(Resource contentResource, boolean forceEnableHyphenation) throws IOException {
        var resourceMediaType = getMediaType(contentResource);
        if (resourceMediaType == null || !HTML_MEDIA_TYPES.contains(resourceMediaType)) {
            // We only currently transform HTML.  Everything else, this is a no-op.
            return contentResource;
        }

        try (var inputStream = contentResource.asInputStream()) {
            var newResource = new Resource(
                    contentResource.getId(),
                    kepubHtmlConversionService.transform(
                            inputStream,
                            contentResource.getInputEncoding(),
                            forceEnableHyphenation
                    ).getBytes(StandardCharsets.UTF_8),
                    contentResource.getHref(),
                    MediaTypes.XHTML,
                    "UTF-8"
            );

            newResource.setProperties(contentResource.getProperties());
            newResource.setMediaOverlayId(contentResource.getMediaOverlayId());

            return newResource;
        }
    }

    /**
     * Adds the cover-image property to the cover item in the OPF manifest.
     * Kobo devices will only support the EPUB3 "properties" attribute with
     * the `cover-image` tag.
     * <a href="https://www.w3.org/TR/epub-33/#sec-item-resource-properties">
     *     Read more on the EPUB3 spec.
     * </a>
     */
    private void transformOPFCoverImage(Document opfDoc, String coverImage) {
        if (coverImage == null) {
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
                    if (coverImage.equals(item.getAttribute("href"))) {
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

    private Resource transformOPF(Resource opfResource, Resource cover) throws IOException {
        if (opfResource == null) {
            // Eventually we may want to create an OPF but for now just ignore it
            // if it's missing.
            return null;
        }

        // TransformOPF transforms the OPF document for a KEPUB.
        try {
            var builder = SecureXmlUtils.createSecureDocumentBuilder(true);
            var opfDoc = builder.parse(opfResource.asInputStream());

            String coverImage = cover == null ? null : cover.getHref();
            transformOPFCoverImage(opfDoc, coverImage);

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

            try (var outputStream = new ByteArrayOutputStream()) {
                transformer.transform(new DOMSource(opfDoc), new StreamResult(outputStream));

                return new Resource(
                        opfResource.getId(),
                        outputStream.toByteArray(),
                        opfResource.getHref(),
                        opfResource.getMediaType(),
                        "UTF-8"
                );
            }

        } catch (TransformerException | SAXException | ParserConfigurationException exception) {
            log.error("unable to parse OPF");
            throw new IOException("unable to parse OPF", exception);
        }
    }

    private boolean isIncludedResource(Resource resource) {
        // Because this isn't an actual filesystem it's always "/"
        String[] parts = resource.getHref().split("/");

        if (parts.length == 0) {
            // No empty HREF items allowed.
            return false;
        }

        if (IGNORED_FILENAMES.contains(parts[parts.length - 1].toLowerCase())) {
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

    private Book convertBookToKepub(Book original, boolean forceEnableHyphenation) throws IOException {
        Book kepub = new Book();

        for (var resource : original.getResources().getAll()) {
            if (!isIncludedResource(resource)) {
                continue;
            }

            kepub.addResource(getTransformedContentResource(resource, forceEnableHyphenation));
        }

        kepub.setNavResource(original.getNavResource());
        kepub.setNcxResource(original.getNcxResource());
        kepub.setCoverImage(original.getCoverImage());
        kepub.setCoverPage(original.getCoverPage());
        kepub.setMetadata(original.getMetadata());
        kepub.setTableOfContents(original.getTableOfContents());
        kepub.setSpine(original.getSpine());

        kepub.setOpfResource(
                transformOPF(
                        original.getOpfResource(),
                        original.getCoverImage()
                )
        );

        return kepub;
    }

    public void convertEpubToKepub(
            InputStream inputStream,
            OutputStream outputStream,
            boolean forceEnableHyphenation,
            EpubWriter epubWriter
    ) throws IOException {
        Book originalBook = epubReader.readEpub(inputStream);

        Book kepubBook = convertBookToKepub(originalBook, forceEnableHyphenation);

        epubWriter.write(kepubBook, outputStream);
    }

    private void convertEpubToKepub(Path inputPath, Path outputPath, boolean forceEnableHyphenation) throws IOException {
        validateInputs(inputPath);

        try (var inputStream = Files.newInputStream(inputPath)) {
            try (var outputStream = Files.newOutputStream(outputPath)) {
                convertEpubToKepub(
                        inputStream,
                        outputStream,
                        forceEnableHyphenation,
                        new EpubWriter()
                );
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
        var outputPath = Files.createTempFile(tempDir.getPath(), ".kepub.epub");
        convertEpubToKepub(epubFile.toPath(), outputPath, forceEnableHyphenation);
        return outputPath.toFile();
    }

    private void validateInputs(Path inputPath) {
        if (inputPath == null || !Files.isRegularFile(inputPath)) {
            throw new IllegalArgumentException("Invalid EPUB file: " + inputPath);
        }
    }
}
