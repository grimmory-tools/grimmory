package org.booklore.service.metadata.writer;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.booklore.model.MetadataClearFlags;
import org.booklore.model.dto.settings.MetadataPersistenceSettings;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.service.ArchiveService;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.util.MimeDetector;
import org.booklore.util.SecureXmlUtils;
import org.booklore.util.epub.EpubContentReader;
import org.booklore.util.epub.EpubContentWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;
import java.util.function.Predicate;
import java.util.function.Supplier;

@Slf4j
@Component
public class EpubMetadataWriter implements MetadataWriter {

    private static final String OPF_NS = "http://www.idpf.org/2007/opf";
    private static final Set<String> MANAGED_BOOKLORE_METADATA = Set.of(
            "booklore:subtitle", "booklore:page_count", "booklore:series_total",
            "booklore:amazon_rating", "booklore:amazon_review_count",
            "booklore:goodreads_rating", "booklore:goodreads_review_count",
            "booklore:hardcover_rating", "booklore:hardcover_review_count",
            "booklore:lubimyczytac_rating", "booklore:ranobedb_rating",
            "booklore:applebooks_rating", "booklore:applebooks_review_count",
            "booklore:moods", "booklore:tags", "booklore:age_rating", "booklore:content_rating");
    private final Supplier<MetadataPersistenceSettings> metadataSettings;
    private final ArchiveService archiveService;

    @Autowired
    public EpubMetadataWriter(AppSettingService appSettingService, ArchiveService archiveService) {
        this(() -> appSettingService.getAppSettings().getMetadataPersistenceSettings(), archiveService);
    }

    public EpubMetadataWriter(Supplier<MetadataPersistenceSettings> metadataSettings, ArchiveService archiveService) {
        this.metadataSettings = Objects.requireNonNull(metadataSettings);
        this.archiveService = Objects.requireNonNull(archiveService);
    }

    @Override
    public void saveMetadataToFile(File epubFile, BookMetadataEntity metadata, MetadataClearFlags clear) {
        if (!shouldSaveMetadataToFile(epubFile)) {
            return;
        }

        try {
            writeMetadataToFile(epubFile, metadata, clear);
        } catch (IOException e) {
            log.warn("Failed to write metadata to EPUB file {}: {}", epubFile.getName(), e.getMessage(), e);
        }
    }

    @Override
    public void saveMetadataToFileStrict(File epubFile, BookMetadataEntity metadata, MetadataClearFlags clear)
            throws IOException {
        if (!shouldSaveMetadataToFile(epubFile)) {
            throw new IOException("EPUB metadata persistence is disabled or the file exceeds its configured size limit");
        }
        writeMetadataToFile(epubFile, metadata, clear);
    }

    void writeMetadataToFile(File epubFile, BookMetadataEntity metadata, MetadataClearFlags clear) throws IOException {
        writeMetadataToFile(epubFile, metadata, clear, false);
    }

    void writeTitleAndAuthorsToFile(File epubFile, BookMetadataEntity metadata) throws IOException {
        writeMetadataToFile(epubFile, metadata, null, true);
    }

    private void writeMetadataToFile(File epubFile, BookMetadataEntity metadata, MetadataClearFlags clear,
                                     boolean preserveBookloreMetadata) throws IOException {
        File backupFile = new File(epubFile.getParentFile(), epubFile.getName() + ".bak");
        Files.copy(epubFile.toPath(), backupFile.toPath());
        Path tempDir = null;
        Path tempEpub = null;
        boolean keepBackup = false;
        boolean replacementStarted = false;
        try {
            tempDir = Files.createTempDirectory("epub_edit_" + UUID.randomUUID());
            archiveService.extractToDirectory(epubFile.toPath(), tempDir);

            Path opfPath = findOpfPath(tempDir);

            DocumentBuilder builder = SecureXmlUtils.createSecureDocumentBuilder(true);
            Document opfDoc = builder.parse(opfPath.toFile());

            Element metadataElement = getOrCreateMetadataElement(opfDoc);
            final String DC_NS = "http://purl.org/dc/elements/1.1/";

            boolean[] hasChanges = {false};
            MetadataCopyHelper helper = new MetadataCopyHelper(metadata);

            helper.copyTitle(clear != null && clear.isTitle(), val -> {
                replaceTitleAndTrackChange(opfDoc, metadataElement, val, hasChanges);
                if (StringUtils.isNotBlank(metadata.getSubtitle())) {
                    addSubtitleToTitle(metadataElement, opfDoc, metadata.getSubtitle());
                }
            });
            helper.copyDescription(clear != null && clear.isDescription(), val -> replaceAndTrackChange(opfDoc, metadataElement, "description", DC_NS, val, hasChanges));
            helper.copyPublisher(clear != null && clear.isPublisher(), val -> replaceAndTrackChange(opfDoc, metadataElement, "publisher", DC_NS, val, hasChanges));
            helper.copyPublishedDate(clear != null && clear.isPublishedDate(), val -> replaceAndTrackChange(opfDoc, metadataElement, "date", DC_NS, val != null ? val.toString() : null, hasChanges));
            helper.copyLanguage(clear != null && clear.isLanguage(), val -> replaceAndTrackChange(opfDoc, metadataElement, "language", DC_NS, val, hasChanges));

            helper.copyAuthors(clear != null && clear.isAuthors(), names -> {
                removeCreatorsByRole(metadataElement, "");
                removeCreatorsByRole(metadataElement, "aut");
                if (names != null) {
                    for (String name : names) {
                        String[] parts = name.split(" ", 2);
                        String first = parts.length > 1 ? parts[0] : "";
                        String last = parts.length > 1 ? parts[1] : parts[0];
                        String fileAs = last + ", " + first;
                        metadataElement.appendChild(createCreatorElement(opfDoc, metadataElement, name, fileAs, "aut"));
                    }
                }
                hasChanges[0] = true;
            });

            helper.copyCategories(clear != null && clear.isCategories(), categories -> {
                removeElementsByTagNameNS(metadataElement, DC_NS, "subject");
                if (categories != null) {
                    for (String cat : categories.stream().map(String::trim).distinct().toList()) {
                        metadataElement.appendChild(createSubjectElement(opfDoc, cat));
                    }
                }
                hasChanges[0] = true;
            });

            String[] seriesName = {null};
            String[] seriesNumber = {null};
            boolean[] seriesFieldsChanged = {false, false};
            helper.copySeriesName(clear != null && clear.isSeriesName(), val -> {
                seriesName[0] = val;
                seriesFieldsChanged[0] = true;
            });
            helper.copySeriesNumber(clear != null && clear.isSeriesNumber(), val -> {
                if (val != null && val > 0) {
                    seriesNumber[0] = val % 1.0f == 0 ? String.format("%.0f", val) : String.valueOf(val);
                }
                seriesFieldsChanged[1] = true;
            });
            if (seriesFieldsChanged[0] || seriesFieldsChanged[1]) {
                ExistingSeries existingSeries = readExistingSeries(metadataElement);
                replaceBelongsToCollection(metadataElement, opfDoc,
                        seriesFieldsChanged[0] ? seriesName[0] : existingSeries.name(),
                        seriesFieldsChanged[1] ? seriesNumber[0] : existingSeries.number(),
                        existingSeries.id(), hasChanges);
            }

            helper.copyIsbn13(clear != null && clear.isIsbn13(), val -> {
                removeIdentifierByUrn(metadataElement, "isbn");
                if (val != null && !val.isBlank()) {
                    metadataElement.appendChild(createIdentifierElement(opfDoc, "isbn", val));
                }
                hasChanges[0] = true;
            });
            helper.copyIsbn10(clear != null && clear.isIsbn10(), val -> {
                if (val != null && !val.isBlank()) {
                    metadataElement.appendChild(createIdentifierElement(opfDoc, "isbn", val));
                }
                hasChanges[0] = true;
            });
            helper.copyAsin(clear != null && clear.isAsin(), val -> {
                removeIdentifierByUrn(metadataElement, "amazon");
                if (val != null && !val.isBlank()) {
                    metadataElement.appendChild(createIdentifierElement(opfDoc, "amazon", val));
                }
                hasChanges[0] = true;
            });
            helper.copyOpenlibraryId(clear != null && clear.isOpenlibraryId(), val -> {
                removeIdentifierByUrn(metadataElement, "openlibrary");
                if (val != null && !val.isBlank()) {
                    metadataElement.appendChild(createIdentifierElement(opfDoc, "openlibrary", val));
                }
                hasChanges[0] = true;
            });
            helper.copyGoodreadsId(clear != null && clear.isGoodreadsId(), val -> {
                removeIdentifierByUrn(metadataElement, "goodreads");
                if (val != null && !val.isBlank()) {
                    metadataElement.appendChild(createIdentifierElement(opfDoc, "goodreads", val));
                }
                hasChanges[0] = true;
            });
            helper.copyGoogleId(clear != null && clear.isGoogleId(), val -> {
                removeIdentifierByUrn(metadataElement, "google");
                if (val != null && !val.isBlank()) {
                    metadataElement.appendChild(createIdentifierElement(opfDoc, "google", val));
                }
                hasChanges[0] = true;
            });
            helper.copyComicvineId(clear != null && clear.isComicvineId(), val -> {
                removeIdentifierByUrn(metadataElement, "comicvine");
                if (val != null && !val.isBlank()) {
                    metadataElement.appendChild(createIdentifierElement(opfDoc, "comicvine", val));
                }
                hasChanges[0] = true;
            });
            helper.copyHardcoverId(clear != null && clear.isHardcoverId(), val -> {
                removeIdentifierByUrn(metadataElement, "hardcover");
                if (val != null && !val.isBlank()) {
                    metadataElement.appendChild(createIdentifierElement(opfDoc, "hardcover", val));
                }
                hasChanges[0] = true;
            });
            helper.copyHardcoverBookId(clear != null && clear.isHardcoverBookId(), val -> {
                removeIdentifierByUrn(metadataElement, "hardcoverbook");
                if (val != null && !val.isBlank()) {
                    metadataElement.appendChild(createIdentifierElement(opfDoc, "hardcoverbook", val));
                }
                hasChanges[0] = true;
            });
            helper.copyLubimyczytacId(clear != null && clear.isLubimyczytacId(), val -> {
                removeIdentifierByUrn(metadataElement, "lubimyczytac");
                if (val != null && !val.isBlank()) {
                    metadataElement.appendChild(createIdentifierElement(opfDoc, "lubimyczytac", val));
                }
                hasChanges[0] = true;
            });
            helper.copyRanobedbId(clear != null && clear.isRanobedbId(), val -> {
                removeIdentifierByUrn(metadataElement, "ranobedb");
                if (val != null && !val.isBlank()) {
                    metadataElement.appendChild(createIdentifierElement(opfDoc, "ranobedb", val));
                }
                hasChanges[0] = true;
            });
            helper.copyApplebooksId(clear != null && clear.isApplebooksId(), val -> {
                removeIdentifierByUrn(metadataElement, "applebooks");
                if (val != null && !val.isBlank()) {
                    metadataElement.appendChild(createIdentifierElement(opfDoc, "applebooks", val));
                }
                hasChanges[0] = true;
            });

            if (!preserveBookloreMetadata && !hasChanges[0] && hasBookloreMetadataChanges(metadataElement, metadata)) {
                hasChanges[0] = true;
            }

            if (hasChanges[0]) {
                if (!preserveBookloreMetadata) {
                    addBookloreMetadata(metadataElement, opfDoc, metadata);
                }
                organizeMetadataElements(metadataElement);
                removeInvalidMetaRefines(metadataElement, opfDoc);
                removeEmptyTextNodes(opfDoc);
                organizePackageElements(opfDoc);
                Transformer transformer = TransformerFactory.newInstance().newTransformer();
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
                transformer.transform(new DOMSource(opfDoc), new StreamResult(opfPath.toFile()));

                tempEpub = Files.createFile(epubFile.toPath().resolveSibling(epubFile.getName() + ".tmp"));
                EpubContentWriter.createEpubFromDirectory(tempDir, tempEpub);

                replacementStarted = true;
                Files.move(tempEpub, epubFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                tempEpub = null;

                log.info("Metadata updated in EPUB: {}", epubFile.getName());
            } else {
                log.info("No changes detected. Skipping EPUB write for: {}", epubFile.getName());
            }
        } catch (Exception e) {
            if (replacementStarted && backupFile.exists()) {
                try {
                    if (!Files.isRegularFile(epubFile.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)
                            || Files.mismatch(backupFile.toPath(), epubFile.toPath()) != -1) {
                        restoreBackup(backupFile.toPath(), epubFile.toPath());
                        log.info("Restored EPUB from backup: {}", epubFile.getName());
                    }
                } catch (IOException io) {
                    keepBackup = true;
                    e.addSuppressed(io);
                    log.error("Failed to restore EPUB from backup for {}: {}", epubFile.getName(), io.getMessage(), io);
                }
            }
            throw new IOException("Failed to write EPUB metadata", e);
        } finally {
            if (tempDir != null) {
                deleteDirectoryRecursively(tempDir);
            }
            if (tempEpub != null) {
                try {
                    Files.deleteIfExists(tempEpub);
                } catch (IOException ex) {
                    log.warn("Failed to delete temporary EPUB for {}: {}", epubFile.getName(), ex.getMessage());
                }
            }
            if (!keepBackup && backupFile.exists()) {
                try {
                    Files.delete(backupFile.toPath());
                } catch (IOException ex) {
                    log.warn("Failed to delete backup for {}: {}", epubFile.getName(), ex.getMessage());
                }
            }
        }
    }

    private static void restoreBackup(Path backup, Path target) throws IOException {
        Path temporary = Files.createTempFile(target.toAbsolutePath().getParent(), ".grimmory-restore-", ".tmp");
        try {
            Files.copy(backup, temporary, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupError) {
                e.addSuppressed(cleanupError);
            }
            throw e;
        }
    }

    private void updateIdentifier(Element metadataElement, Document opfDoc, String scheme, String idValue, boolean[] hasChanges) {
        removeIdentifierByScheme(metadataElement, scheme);
        if (idValue != null && !idValue.isBlank()) {
            metadataElement.appendChild(createIdentifierElement(opfDoc, scheme, idValue));
        }
        hasChanges[0] = true;
    }

    private void replaceAndTrackChange(Document doc, Element parent, String tag, String ns, String val, boolean[] flag) {
        if (replaceElementText(doc, parent, tag, ns, val, false)) flag[0] = true;
    }

    private void replaceTitleAndTrackChange(Document doc, Element metadata, String value, boolean[] changed) {
        String dc = "http://purl.org/dc/elements/1.1/";
        if (value == null) {
            replaceAndTrackChange(doc, metadata, "title", dc, null, changed);
            return;
        }

        List<Element> titles = getChildren(metadata, dc, "title");
        if (titles.isEmpty()) {
            replaceAndTrackChange(doc, metadata, "title", dc, value, changed);
            return;
        }

        Element selected = titles.getFirst();
        for (Element meta : getChildren(metadata, OPF_NS, "meta")) {
            if (!"title-type".equals(meta.getAttribute("property"))
                    || !"main".equalsIgnoreCase(meta.getTextContent().trim())) {
                continue;
            }
            String refines = meta.getAttribute("refines");
            Optional<Element> mainTitle = titles.stream()
                    .filter(title -> !title.getAttribute("id").isEmpty()
                            && refines.equals("#" + title.getAttribute("id")))
                    .findFirst();
            if (mainTitle.isPresent()) {
                selected = mainTitle.get();
                break;
            }
        }
        if (!Objects.equals(selected.getTextContent(), value)) {
            selected.setTextContent(value);
            changed[0] = true;
        }
    }

    private Optional<Element> getChild(Element parent, String namespaceUri, String tagName) {
        var children = getChildren(parent, namespaceUri, tagName);

        if (children.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(children.getFirst());
    }

    private List<Element> getChildren(Element parent, String namespaceUri, String tagName) {
        NodeList metadataElements = parent.getChildNodes();

        List<Element> elements = new ArrayList<>();
        for (int i = 0; i < metadataElements.getLength(); i++ ) {
            if (metadataElements.item(i) instanceof Element element) {
                if (element.getNamespaceURI() == null || !element.getNamespaceURI().equals(namespaceUri)) {
                    continue;
                }

                if (!element.getLocalName().equals(tagName)) {
                    continue;
                }

                elements.add(element);
            }
        }

        return elements;
    }

    private Element getOrCreateChild(Element parent, String namespace, String tagName) {
        return getChild(parent, namespace, tagName)
                .orElseGet(() -> {
                    Element element = parent.getOwnerDocument().createElementNS(namespace, tagName);
                    parent.appendChild(element);
                    return element;
                });
    }

    private Element getOrCreateMetadataElement(Document doc) {
        return getOrCreateChild(doc.getDocumentElement(), OPF_NS, "metadata");
    }

    public Element getOrCreateManifestElement(Document doc) {
        return getOrCreateChild(doc.getDocumentElement(), OPF_NS, "manifest");
    }

    public Element getOrCreateSpineElement(Document doc) {
        return getOrCreateChild(doc.getDocumentElement(), OPF_NS, "spine");
    }

    private Element upsertMetaElement(Document doc, String name, String content) {
        var metadata = getOrCreateMetadataElement(doc);

        NodeList metaElements = metadata.getElementsByTagName("meta");
        for (int i = 0; i < metaElements.getLength(); i++) {
            if (metaElements.item(i) instanceof Element element) {
                if (!name.equals(element.getAttribute("name"))) {
                    continue;
                }

                // Found target.
                element.setAttribute("content", content);
                return element;
            }
        }

        // We couldn't find any meta element so we can create it.
        Element element = doc.createElement("meta");
        element.setAttribute("name", name);
        element.setAttribute("content", content);
        metadata.appendChild(element);
        return element;
    }

    private boolean replaceElementText(Document doc, Element parent, String tagName, String namespaceURI, String newValue, boolean restoreMode) {
        NodeList nodes = parent.getElementsByTagNameNS(namespaceURI, tagName);
        String currentValue = null;
        if (nodes.getLength() > 0) {
            currentValue = nodes.item(0).getTextContent();
        }

        boolean changed = !Objects.equals(currentValue, newValue);

        for (int i = nodes.getLength() - 1; i >= 0; i--) {
            parent.removeChild(nodes.item(i));
        }

        if (newValue != null) {
            Element newElem = doc.createElementNS(namespaceURI, tagName);
            newElem.setPrefix("dc");
            newElem.setTextContent(newValue);
            parent.appendChild(newElem);
        } else if (restoreMode) {
            changed = true;
        }

        return changed;
    }

    @Override
    public void replaceCoverImageFromBytes(File epubFile, byte[] contents) {
        if (!shouldSaveMetadataToFile(epubFile)) {
            return;
        }

        try {
            writeCoverToFile(epubFile, contents);
        } catch (IOException e) {
            log.warn("Failed to update EPUB cover image: {}", e.getMessage(), e);
        }
    }

    void writeCoverToFile(File epubFile, byte[] contents) throws IOException {
        Path tempDir = null;
        Path tempEpub = null;
        try {
            tempDir = Files.createTempDirectory("epub_cover_" + UUID.randomUUID());

            archiveService.extractToDirectory(epubFile.toPath(), tempDir);

            Path opfPath = findOpfPath(tempDir);

            DocumentBuilder builder = SecureXmlUtils.createSecureDocumentBuilder(true);
            Document opfDoc = builder.parse(opfPath.toFile());

            applyCoverImageToEpub(tempDir, opfDoc, contents);

            removeEmptyTextNodes(opfDoc);
            organizePackageElements(opfDoc);
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.transform(new DOMSource(opfDoc), new StreamResult(opfPath.toFile()));

            tempEpub = Files.createFile(epubFile.toPath().resolveSibling(epubFile.getName() + ".tmp"));
            EpubContentWriter.createEpubFromDirectory(tempDir, tempEpub);

            Files.move(tempEpub, epubFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            tempEpub = null;

            log.info("Cover image updated in EPUB: {}", epubFile.getName());

        } catch (Exception e) {
            throw new IOException("Failed to update EPUB cover image", e);
        } finally {
            if (tempDir != null) {
                deleteDirectoryRecursively(tempDir);
            }
            if (tempEpub != null) {
                try {
                    Files.deleteIfExists(tempEpub);
                } catch (IOException ex) {
                    log.warn("Failed to delete temporary EPUB for {}: {}", epubFile.getName(), ex.getMessage());
                }
            }
        }
    }

    @Override
    public BookFileType getSupportedBookType() {
        return BookFileType.EPUB;
    }

    private String generateCoverId(Document doc, Path dir, String extension) {
        // Generate an ID / file combination that does not exist yet.
        var ids = getDocumentIds(doc);
        String coverId = "cover";
        while(ids.contains(coverId) || Files.exists(dir.resolve(coverId + extension))) {
            coverId = "cover-" + UUID.randomUUID().toString();
        }
        return coverId;
    }

    private Path getManifestItemPath(Path tempDir, Path opfDir, Element element) throws IOException {
        String href = element.getAttribute("href");

        // Technically, epub specification only refers to `href` as supporting percent-encoding.
        // Unfortunately, the Java URLDecoder.decode method will also do `+` -> space decoding.
        // To work around this, we can replace `+` with the percent-encoded version of a plus.
        href = href.replaceAll("\\+", "%2b");

        String decodedHref = URLDecoder.decode(href, StandardCharsets.UTF_8);
        if (decodedHref == null || decodedHref.isBlank()) {
            throw new IOException("Manifest item has no href attribute");
        }

        Path filePath = opfDir.resolve(decodedHref).normalize();

        if (!filePath.startsWith(tempDir)) {
            throw new IOException("Manifest item file may not be outside the epub archive");
        }

        return filePath;
    }

    private void applyCoverImageToEpub(Path tempDir, Document opfDoc, byte[] coverData) throws IOException {
        String mediaType = MimeDetector.detect(new ByteArrayInputStream(coverData));
        String extension = MimeDetector.getExtension(mediaType);

        Element metadataElement = getOrCreateMetadataElement(opfDoc);
        Element manifestElement = getOrCreateManifestElement(opfDoc);
        Element existingCoverItem = null;

        // First, try to find cover via metadata reference (EPUB 3 style)
        String coverItemId = getMetaContentByName(metadataElement, "cover");
        if (coverItemId != null && !coverItemId.isBlank()) {
            // Find the item with this id
            NodeList items = manifestElement.getElementsByTagNameNS(OPF_NS, "item");
            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);
                if (coverItemId.equals(item.getAttribute("id"))) {
                    existingCoverItem = item;
                    break;
                }
            }
        }

        // If not found, try looking for properties="cover-image" (EPUB 3)
        if (existingCoverItem == null) {
            NodeList items = manifestElement.getElementsByTagNameNS(OPF_NS, "item");
            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);
                String properties = item.getAttribute("properties");
                if (properties != null && properties.contains("cover-image")) {
                    existingCoverItem = item;
                    break;
                }
            }
        }

        // If still not found, try common id values (EPUB 2 fallback)
        if (existingCoverItem == null) {
            NodeList items = manifestElement.getElementsByTagNameNS(OPF_NS, "item");
            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);
                String itemId = item.getAttribute("id");
                if ("cover-image".equals(itemId) || "cover".equals(itemId) || "coverimg".equals(itemId)) {
                    existingCoverItem = item;
                    break;
                }
            }
        }

        Path opfPath;
        try {
            opfPath = findOpfPath(tempDir);
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("Failed to parse container.xml to locate OPF path", e);
        }

        if (existingCoverItem != null) {
            String existingMediaType = existingCoverItem.getAttribute("media-type");
            if (!existingMediaType.equals(mediaType)) {
                // If the existing cover item exists but has a different media type we can't
                // use it.  However, because it's _possible_ that the cover MAY be used elsewhere
                // in the epub (such as referenced in HTML) we cannot delete the manifest item
                // or the file.  All we can do is discard it as the existing cover manifest item,
                // and treat the epub as if it has no cover, and place a new cover manifest item.
                existingCoverItem = null;
            }
        }

        Path opfDir = opfPath.getParent();

        if (existingCoverItem != null) {
            try {
                getManifestItemPath(tempDir, opfDir, existingCoverItem);
            } catch (IOException e) {
                log.warn("Invalid cover for epub, ignoring", e);
                existingCoverItem = null;
            }
        }

        if (existingCoverItem == null) {
            // If there is no existing cover item, we should create one.
            String coverId = generateCoverId(opfDoc, opfDir, extension);

            existingCoverItem = opfDoc.createElementNS(OPF_NS, "item");
            existingCoverItem.setAttribute("id", coverId);
            existingCoverItem.setAttribute("media-type", mediaType);
            existingCoverItem.setAttribute("href", coverId + extension);

            // Add the item and set the
            manifestElement.appendChild(existingCoverItem);
            upsertMetaElement(opfDoc, "cover", coverId);
        }

        // Remove all item's epub3 `properties` containing `cover-image`.
        NodeList items = manifestElement.getElementsByTagNameNS(OPF_NS, "item");
        for (int i = 0; i < items.getLength(); i++) {
            if (items.item(i) instanceof Element item) {
                // Remember: the `properties` attribute is a space delimited list,
                // so we can't clear it entirely.
                String properties = Arrays.stream(
                            item.getAttribute("properties")
                                    .trim()
                                    .split("\\s+")
                        )
                        .filter(s -> !"cover-image".equals(s))
                        .collect(Collectors.joining(" "));

                if (properties.isBlank()) {
                    item.removeAttribute("properties");
                } else {
                    item.setAttribute("properties", properties);
                }
            }
        }

        boolean isEpub3 = isEpub3(opfDoc);

        if (isEpub3) {
            // Add epub3 properties cover-image back to the target cover item.
            String properties = existingCoverItem.getAttribute("properties")
                    .trim();

            if (properties.isBlank()) {
                properties = "cover-image";
            } else {
                properties += " cover-image";
            }

            existingCoverItem.setAttribute("properties", properties);
        }

        Path coverFilePath = getManifestItemPath(tempDir, opfDir, existingCoverItem);

        Files.createDirectories(coverFilePath.getParent());
        Files.write(coverFilePath, coverData);
    }

    private Path findOpfPath(Path tempDir) throws IOException, ParserConfigurationException, SAXException {
        return EpubContentReader.findOPFInExtractedEpub(tempDir);
    }

    private void removeMetaByName(Element metadataElement, String name) {
        NodeList metas = metadataElement.getElementsByTagNameNS("*", "meta");
        for (int i = metas.getLength() - 1; i >= 0; i--) {
            Element meta = (Element) metas.item(i);
            if (name.equals(meta.getAttribute("name"))) {
                metadataElement.removeChild(meta);
            }
        }
    }

    private Set<String> getDocumentIds(Document doc) {
        Set<String> ids = new HashSet<>();

        NodeList nodes = doc.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            var node = nodes.item(i);

            if (node instanceof Element element) {
                var idAttribute = element.getAttribute("id");
                if (!idAttribute.isBlank()) {
                    ids.add(idAttribute);
                }
            }
        }

        return ids;
    }

    private void removeInvalidMetaRefines(Element metadataElement, Document doc) {
        // In an ideal world we could set the `validating` flag or
        // otherwise set the `isId` attribute tag correctly for `id`
        // but because we cannot, we can't use `getElementById()` on
        // the document.  With that in mind, we area going to read all
        // tags, check if they have an `id`, and store that in a `Set`
        var ids = getDocumentIds(doc);

        NodeList metas = metadataElement.getElementsByTagNameNS("*", "meta");
        for (int i = metas.getLength() - 1; i >= 0; i--) {
            Element meta = (Element) metas.item(i);
            String refines = meta.getAttribute("refines");

            if (refines.startsWith("#")) {
                String refinesId = refines.substring(1);

                if (!ids.contains(refinesId)) {
                    metadataElement.removeChild(meta);
                }
            }
        }
    }

    private void removeMetaByRefines(Element metadataElement, String refines) {
        NodeList metas = metadataElement.getElementsByTagNameNS("*", "meta");
        for (int i = metas.getLength() - 1; i >= 0; i--) {
            Element meta = (Element) metas.item(i);
            if (refines.equals(meta.getAttribute("refines"))) {
                metadataElement.removeChild(meta);
            }
        }
    }

    private Element createMetaElement(Document doc, String name, String content) {
        Element meta = doc.createElementNS(doc.getDocumentElement().getNamespaceURI(), "meta");
        meta.setAttribute("name", name);
        meta.setAttribute("content", content);
        return meta;
    }

    private void removeIdentifierByScheme(Element metadataElement, String scheme) {
        NodeList identifiers = metadataElement.getElementsByTagNameNS("*", "identifier");
        for (int i = identifiers.getLength() - 1; i >= 0; i--) {
            Element idElement = (Element) identifiers.item(i);
            if (scheme.equalsIgnoreCase(idElement.getAttributeNS(OPF_NS, "scheme"))) {
                metadataElement.removeChild(idElement);
            }
        }
    }
    private void removeIdentifierByUrn(Element metadataElement, String urnScheme) {
        NodeList identifiers = metadataElement.getElementsByTagNameNS("*", "identifier");
        String urnPrefix = "urn:" + urnScheme.toLowerCase() + ":";
        String oldPrefix = urnScheme.toLowerCase() + ":";
        for (int i = identifiers.getLength() - 1; i >= 0; i--) {
            Element idElement = (Element) identifiers.item(i);
            String content = idElement.getTextContent().trim().toLowerCase();
            if (content.startsWith(urnPrefix) || content.startsWith(oldPrefix)) {
                metadataElement.removeChild(idElement);
            }
        }
    }

    private Element createIdentifierElement(Document doc, String scheme, String value) {
        Element id = doc.createElementNS("http://purl.org/dc/elements/1.1/", "identifier");
        id.setPrefix("dc");
        id.setTextContent("urn:" + scheme.toLowerCase() + ":" + value);
        return id;
    }

    private void removeElementsByTagNameNS(Element parent, String namespaceURI, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS(namespaceURI, localName);
        for (int i = nodes.getLength() - 1; i >= 0; i--) {
            parent.removeChild(nodes.item(i));
        }
    }

    private void removeCreatorsByRole(Element metadataElement, String role) {
        NodeList creators = metadataElement.getElementsByTagNameNS("*", "creator");
        for (int i = creators.getLength() - 1; i >= 0; i--) {
            Element creatorElement = (Element) creators.item(i);
            String id = creatorElement.getAttribute("id");
            String creatorRole = creatorElement.getAttributeNS(OPF_NS, "role");
            if (StringUtils.isNotBlank(id) && StringUtils.isBlank(creatorRole)) {
                // Finds any matching role meta tags for this creator ID
                Element meta = getMetaElementByFilter(metadataElement, el -> ("role".equals(el.getAttribute("property")) && "#".concat(id).equals(el.getAttribute("refines"))));
                if (meta != null) {
                    creatorRole = meta.hasAttribute("content") ? meta.getAttribute("content").trim() : meta.getTextContent().trim();
                }
            }
            if (role.equalsIgnoreCase(creatorRole)) {
                metadataElement.removeChild(creatorElement);
                if (StringUtils.isNotBlank(id)) {
                    removeMetaByRefines(metadataElement, "#".concat(id));
                }
            }
        }
    }

    private Element createCreatorElement(Document doc, Element metadataElement, String fullName, String fileAs, String role) {
        Element creator = doc.createElementNS("http://purl.org/dc/elements/1.1/", "creator");
        creator.setPrefix("dc");
        creator.setTextContent(fullName);

        boolean isEpub3 = isEpub3(doc);

        if (isEpub3) {
            // EPUB3: use <meta refines="#id"> elements instead of opf: attributes
            String creatorId = "creator-" + UUID.randomUUID().toString().substring(0, 8);
            creator.setAttribute("id", creatorId);

            if (fileAs != null) {
                Element fileAsMeta = doc.createElementNS(OPF_NS, "meta");
                fileAsMeta.setPrefix("opf");
                fileAsMeta.setAttribute("refines", "#" + creatorId);
                fileAsMeta.setAttribute("property", "file-as");
                fileAsMeta.setTextContent(fileAs);
                metadataElement.appendChild(fileAsMeta);
            }
            if (role != null) {
                Element roleMeta = doc.createElementNS(OPF_NS, "meta");
                roleMeta.setPrefix("opf");
                roleMeta.setAttribute("refines", "#" + creatorId);
                roleMeta.setAttribute("property", "role");
                roleMeta.setAttribute("scheme", "marc:relators");
                roleMeta.setTextContent(role);
                metadataElement.appendChild(roleMeta);
            }
        } else {
            // EPUB2: use opf: attributes directly on dc:creator
            if (fileAs != null) {
                creator.setAttributeNS(OPF_NS, "opf:file-as", fileAs);
            }
            if (role != null) {
                creator.setAttributeNS(OPF_NS, "opf:role", role);
            }
        }
        return creator;
    }

    private Element createSubjectElement(Document doc, String subject) {
        Element subj = doc.createElementNS("http://purl.org/dc/elements/1.1/", "subject");
        subj.setPrefix("dc");
        subj.setTextContent(subject);
        return subj;
    }

    private Element getMetaElementByFilter(Element metadataElement, Predicate<Element> filter) {
        NodeList metas = metadataElement.getElementsByTagNameNS("*", "meta");
        for (int i = 0; i < metas.getLength(); i++) {
            Element meta = (Element) metas.item(i);
            if (filter.test(meta)) {
                return meta;
            }
        }
        return null;
    }

    private String getMetaContentByName(Element metadataElement, String name) {
        Element meta = getMetaElementByFilter(metadataElement, el -> name.equals(el.getAttribute("name")));
        if (meta != null) {
            return meta.getAttribute("content");
        }
        return null;
    }

    private void deleteDirectoryRecursively(Path dir) {
        try (var pathStream = Files.walk(dir)) {
            pathStream
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            log.warn("Failed to delete temp file/directory: {}", path, e);
                        }
                    });
        } catch (IOException e) {
            log.warn("Failed to clean up temporary directory: {}", dir, e);
        }
    }

    public boolean shouldSaveMetadataToFile(File epubFile) {
        MetadataPersistenceSettings.SaveToOriginalFile settings = metadataSettings.get().getSaveToOriginalFile();

        MetadataPersistenceSettings.FormatSettings epubSettings = settings.getEpub();
        if (epubSettings == null || !epubSettings.isEnabled()) {
            log.debug("EPUB metadata writing is disabled. Skipping: {}", epubFile.getName());
            return false;
        }

        long fileSizeInMb = epubFile.length() / (1024 * 1024);
        if (fileSizeInMb > epubSettings.getMaxFileSizeInMb()) {
            log.info("EPUB file {} ({} MB) exceeds max size limit ({} MB). Skipping metadata write.", epubFile.getName(), fileSizeInMb, epubSettings.getMaxFileSizeInMb());
            return false;
        }

        return true;
    }

    private void removeEmptyTextNodes(Document doc) {
        try {
            XPath xpath = XPathFactory.newInstance().newXPath();
            NodeList emptyTextNodes = (NodeList) xpath.evaluate("//text()[normalize-space(.)='']", doc, XPathConstants.NODESET);

            for (int i = 0; i < emptyTextNodes.getLength(); i++) {
                Node emptyTextNode = emptyTextNodes.item(i);
                emptyTextNode.getParentNode().removeChild(emptyTextNode);
            }
        } catch (Exception e) {
            log.warn("Failed to remove empty text nodes", e);
        }
    }

    private boolean isEpub3(Document doc) {
        String version = doc.getDocumentElement().getAttribute("version");
        return version != null && version.trim().startsWith("3");
    }

    private boolean hasBookloreMetadataChanges(Element metadataElement, BookMetadataEntity metadata) {
        Map<String, String> existing = new TreeMap<>();
        NodeList metas = metadataElement.getElementsByTagNameNS("*", "meta");
        for (int i = 0; i < metas.getLength(); i++) {
            Element meta = (Element) metas.item(i);
            String property = meta.getAttribute("property");
            String name = meta.getAttribute("name");
            String key = MANAGED_BOOKLORE_METADATA.contains(property) ? property
                    : (MANAGED_BOOKLORE_METADATA.contains(name) ? name : null);
            if (key != null) {
                String value = meta.getAttribute("content").isEmpty() ? meta.getTextContent() : meta.getAttribute("content");
                if (!isEffectivelyZeroOrBlank(value)) {
                    existing.put(key, value);
                }
            }
        }

        Map<String, String> expected = new TreeMap<>();
        if (StringUtils.isNotBlank(metadata.getSubtitle())) {
            expected.put("booklore:subtitle", metadata.getSubtitle());
        }
        if (metadata.getPageCount() != null && metadata.getPageCount() > 0) {
            expected.put("booklore:page_count", String.valueOf(metadata.getPageCount()));
        }
        if (metadata.getSeriesTotal() != null && metadata.getSeriesTotal() > 0) {
            expected.put("booklore:series_total", String.valueOf(metadata.getSeriesTotal()));
        }
        if (metadata.getAmazonRating() != null && metadata.getAmazonRating() > 0) {
            expected.put("booklore:amazon_rating", String.valueOf(metadata.getAmazonRating()));
        }
        if (metadata.getAmazonReviewCount() != null && metadata.getAmazonReviewCount() > 0) {
            expected.put("booklore:amazon_review_count", String.valueOf(metadata.getAmazonReviewCount()));
        }
        if (metadata.getGoodreadsRating() != null && metadata.getGoodreadsRating() > 0) {
            expected.put("booklore:goodreads_rating", String.valueOf(metadata.getGoodreadsRating()));
        }
        if (metadata.getGoodreadsReviewCount() != null && metadata.getGoodreadsReviewCount() > 0) {
            expected.put("booklore:goodreads_review_count", String.valueOf(metadata.getGoodreadsReviewCount()));
        }
        if (metadata.getHardcoverRating() != null && metadata.getHardcoverRating() > 0) {
            expected.put("booklore:hardcover_rating", String.valueOf(metadata.getHardcoverRating()));
        }
        if (metadata.getHardcoverReviewCount() != null && metadata.getHardcoverReviewCount() > 0) {
            expected.put("booklore:hardcover_review_count", String.valueOf(metadata.getHardcoverReviewCount()));
        }
        if (metadata.getLubimyczytacRating() != null && metadata.getLubimyczytacRating() > 0) {
            expected.put("booklore:lubimyczytac_rating", String.valueOf(metadata.getLubimyczytacRating()));
        }
        if (metadata.getRanobedbRating() != null && metadata.getRanobedbRating() > 0) {
            expected.put("booklore:ranobedb_rating", String.valueOf(metadata.getRanobedbRating()));
        }
        if (metadata.getApplebooksRating() != null && metadata.getApplebooksRating() > 0) {
            expected.put("booklore:applebooks_rating", String.valueOf(metadata.getApplebooksRating()));
        }
        if (metadata.getApplebooksReviewCount() != null && metadata.getApplebooksReviewCount() > 0) {
            expected.put("booklore:applebooks_review_count", String.valueOf(metadata.getApplebooksReviewCount()));
        }
        if (metadata.getMoods() != null && !metadata.getMoods().isEmpty()) {
            String moodsJson = "[" + metadata.getMoods().stream()
                .map(mood -> "\"" + mood.getName().replace("\"", "\\\"") + "\"")
                .sorted()
                .collect(Collectors.joining(", ")) + "]";
            expected.put("booklore:moods", moodsJson);
        }
        if (metadata.getTags() != null && !metadata.getTags().isEmpty()) {
            String tagsJson = "[" + metadata.getTags().stream()
                .map(tag -> "\"" + tag.getName().replace("\"", "\\\"") + "\"")
                .sorted()
                .collect(Collectors.joining(", ")) + "]";
            expected.put("booklore:tags", tagsJson);
        }
        if (metadata.getAgeRating() != null) {
            expected.put("booklore:age_rating", String.valueOf(metadata.getAgeRating()));
        }
        if (StringUtils.isNotBlank(metadata.getContentRating())) {
            expected.put("booklore:content_rating", metadata.getContentRating());
        }

        return !existing.equals(expected);
    }

    private static boolean isEffectivelyZeroOrBlank(String value) {
        if (value == null || value.isBlank()) return true;
        try {
            return Double.parseDouble(value) <= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void removeManagedBookloreMetadata(Element metadataElement) {
        NodeList metas = metadataElement.getElementsByTagNameNS("*", "meta");
        for (int i = metas.getLength() - 1; i >= 0; i--) {
            Element meta = (Element) metas.item(i);
            String property = meta.getAttribute("property");
            String name = meta.getAttribute("name");
            if (MANAGED_BOOKLORE_METADATA.contains(property) || MANAGED_BOOKLORE_METADATA.contains(name)) {
                metadataElement.removeChild(meta);
            }
        }
    }

    private record ExistingSeries(String name, String number, String id) {}

    private Set<String> seriesCollectionIds(Element metadataElement) {
        Set<String> references = new HashSet<>();
        NodeList metas = metadataElement.getElementsByTagNameNS("*", "meta");
        for (int i = 0; i < metas.getLength(); i++) {
            Element meta = (Element) metas.item(i);
            String refines = meta.getAttribute("refines");
            if ("collection-type".equals(meta.getAttribute("property"))
                    && "series".equals(meta.getTextContent().trim())
                    && refines.startsWith("#") && refines.length() > 1) {
                references.add(refines.substring(1));
            }
        }
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < metas.getLength(); i++) {
            Element meta = (Element) metas.item(i);
            if ("belongs-to-collection".equals(meta.getAttribute("property"))
                    && references.contains(meta.getAttribute("id"))) {
                ids.add(meta.getAttribute("id"));
            }
        }
        return ids;
    }

    private ExistingSeries readExistingSeries(Element metadataElement) {
        Set<String> seriesIds = seriesCollectionIds(metadataElement);
        NodeList metas = metadataElement.getElementsByTagNameNS("*", "meta");
        String id = null;
        String name = null;
        String number = null;
        for (int i = 0; i < metas.getLength(); i++) {
            Element meta = (Element) metas.item(i);
            if ("belongs-to-collection".equals(meta.getAttribute("property"))
                    && seriesIds.contains(meta.getAttribute("id"))) {
                id = meta.getAttribute("id");
                name = meta.getTextContent();
                break;
            }
        }
        for (int i = 0; i < metas.getLength(); i++) {
            Element meta = (Element) metas.item(i);
            if (id != null && ("#" + id).equals(meta.getAttribute("refines"))
                    && "group-position".equals(meta.getAttribute("property"))) {
                number = meta.getTextContent();
            } else if (id == null && "calibre:series".equals(meta.getAttribute("name"))) {
                name = meta.getAttribute("content");
            } else if (id == null && "calibre:series_index".equals(meta.getAttribute("name"))) {
                number = meta.getAttribute("content");
            }
        }
        return new ExistingSeries(name, number, id);
    }

    private void replaceBelongsToCollection(Element metadataElement, Document doc, String seriesName,
                                            String seriesNumber, String previousSeriesId, boolean[] hasChanges) {
        boolean epub3 = isEpub3(doc);
        if (epub3 && previousSeriesId != null && StringUtils.isNotBlank(seriesName)) {
            updateExistingSeries(metadataElement, doc, previousSeriesId, seriesName, seriesNumber);
            hasChanges[0] = true;
            return;
        }
        NodeList metas = metadataElement.getElementsByTagNameNS("*", "meta");
        List<Element> remove = new ArrayList<>();
        for (int i = 0; i < metas.getLength(); i++) {
            Element meta = (Element) metas.item(i);
            String name = meta.getAttribute("name");
            if (("belongs-to-collection".equals(meta.getAttribute("property"))
                    && previousSeriesId != null && previousSeriesId.equals(meta.getAttribute("id")))
                    || "calibre:series".equals(name) || "calibre:series_index".equals(name)) {
                remove.add(meta);
            }
        }
        if (!remove.isEmpty()) {
            removeMetasAndRefinements(metadataElement, remove);
            hasChanges[0] = true;
        }

        if (StringUtils.isNotBlank(seriesName)) {
            if (epub3) {
                // EPUB3: use belongs-to-collection with refines
                String collectionId = "collection-" + UUID.randomUUID().toString().substring(0, 8);

                Element collectionMeta = doc.createElementNS(OPF_NS, "meta");
                collectionMeta.setPrefix("opf");
                collectionMeta.setAttribute("id", collectionId);
                collectionMeta.setAttribute("property", "belongs-to-collection");
                collectionMeta.setTextContent(seriesName);
                metadataElement.appendChild(collectionMeta);

                Element typeMeta = doc.createElementNS(OPF_NS, "meta");
                typeMeta.setPrefix("opf");
                typeMeta.setAttribute("property", "collection-type");
                typeMeta.setAttribute("refines", "#" + collectionId);
                typeMeta.setTextContent("series");
                metadataElement.appendChild(typeMeta);

                if (StringUtils.isNotBlank(seriesNumber)) {
                    Element positionMeta = doc.createElementNS(OPF_NS, "meta");
                    positionMeta.setPrefix("opf");
                    positionMeta.setAttribute("property", "group-position");
                    positionMeta.setAttribute("refines", "#" + collectionId);
                    positionMeta.setTextContent(seriesNumber);
                    metadataElement.appendChild(positionMeta);
                }
            } else {
                // EPUB2: use calibre:series convention (widely supported by e-readers)
                Element seriesMeta = doc.createElementNS(doc.getDocumentElement().getNamespaceURI(), "meta");
                seriesMeta.setAttribute("name", "calibre:series");
                seriesMeta.setAttribute("content", seriesName);
                metadataElement.appendChild(seriesMeta);

                if (StringUtils.isNotBlank(seriesNumber)) {
                    Element indexMeta = doc.createElementNS(doc.getDocumentElement().getNamespaceURI(), "meta");
                    indexMeta.setAttribute("name", "calibre:series_index");
                    indexMeta.setAttribute("content", seriesNumber);
                    metadataElement.appendChild(indexMeta);
                }
            }
            
            hasChanges[0] = true;
        }
    }

    private void updateExistingSeries(Element metadataElement, Document doc, String id, String name, String number) {
        NodeList metas = metadataElement.getElementsByTagNameNS("*", "meta");
        List<Element> remove = new ArrayList<>();
        boolean foundPosition = false;
        for (int i = 0; i < metas.getLength(); i++) {
            Element meta = (Element) metas.item(i);
            if ("belongs-to-collection".equals(meta.getAttribute("property")) && id.equals(meta.getAttribute("id"))) {
                meta.setTextContent(name);
            } else if (("#" + id).equals(meta.getAttribute("refines"))
                    && "group-position".equals(meta.getAttribute("property"))) {
                if (StringUtils.isNotBlank(number)) {
                    meta.setTextContent(number);
                    foundPosition = true;
                } else {
                    remove.add(meta);
                }
            } else if ("calibre:series".equals(meta.getAttribute("name"))
                    || "calibre:series_index".equals(meta.getAttribute("name"))) {
                remove.add(meta);
            }
        }
        removeMetasAndRefinements(metadataElement, remove);
        if (!foundPosition && StringUtils.isNotBlank(number)) {
            Element position = doc.createElementNS(OPF_NS, "meta");
            position.setPrefix("opf");
            position.setAttribute("property", "group-position");
            position.setAttribute("refines", "#" + id);
            position.setTextContent(number);
            metadataElement.appendChild(position);
        }
    }

    private void removeMetasAndRefinements(Element metadataElement, List<Element> remove) {
        Set<String> removedIds = remove.stream().map(meta -> meta.getAttribute("id"))
                .filter(StringUtils::isNotBlank).collect(Collectors.toSet());
        NodeList metas = metadataElement.getElementsByTagNameNS("*", "meta");
        boolean found;
        do {
            found = false;
            for (int i = 0; i < metas.getLength(); i++) {
                Element meta = (Element) metas.item(i);
                String refines = meta.getAttribute("refines");
                if (refines.startsWith("#") && removedIds.contains(refines.substring(1)) && !remove.contains(meta)) {
                    remove.add(meta);
                    if (StringUtils.isNotBlank(meta.getAttribute("id"))) {
                        removedIds.add(meta.getAttribute("id"));
                    }
                    found = true;
                }
            }
        } while (found);
        remove.forEach(metadataElement::removeChild);
    }

    private void addSubtitleToTitle(Element metadataElement, Document doc, String subtitle) {
        final String DC_NS = "http://purl.org/dc/elements/1.1/";
        boolean epub3 = isEpub3(doc);

        // Remove existing subtitle elements (both EPUB2 and EPUB3 forms)
        NodeList metas = metadataElement.getElementsByTagNameNS("*", "meta");
        for (int i = metas.getLength() - 1; i >= 0; i--) {
            Element meta = (Element) metas.item(i);
            String property = meta.getAttribute("property");
            String refines = meta.getAttribute("refines");
            if ("title-type".equals(property) && "subtitle".equals(meta.getTextContent())) {
                if (StringUtils.isNotBlank(refines)) {
                    NodeList titles = metadataElement.getElementsByTagNameNS(DC_NS, "title");
                    for (int j = titles.getLength() - 1; j >= 0; j--) {
                        Element title = (Element) titles.item(j);
                        if (("#" + title.getAttribute("id")).equals(refines)) {
                            metadataElement.removeChild(title);
                            break;
                        }
                    }
                }
                metadataElement.removeChild(meta);
            }
        }

        if (epub3) {
            // EPUB3: add subtitle as separate dc:title with title-type refinement
            String subtitleId = "subtitle-" + UUID.randomUUID().toString().substring(0, 8);
            Element subtitleElement = doc.createElementNS(DC_NS, "title");
            subtitleElement.setPrefix("dc");
            subtitleElement.setAttribute("id", subtitleId);
            subtitleElement.setTextContent(subtitle);
            metadataElement.appendChild(subtitleElement);

            Element typeMeta = doc.createElementNS(OPF_NS, "meta");
            typeMeta.setPrefix("opf");
            typeMeta.setAttribute("refines", "#" + subtitleId);
            typeMeta.setAttribute("property", "title-type");
            typeMeta.setTextContent("subtitle");
            metadataElement.appendChild(typeMeta);
        }
        // EPUB2: subtitle is stored only via booklore:subtitle metadata (written in addBookloreMetadata).
        // No modification to dc:title is needed — this preserves round-trip fidelity.
    }

    private void addBookloreMetadata(Element metadataElement, Document doc, BookMetadataEntity metadata) {
        boolean epub3 = isEpub3(doc);

        if (epub3) {
            Element packageElement = doc.getDocumentElement();
            String existingPrefix = packageElement.getAttribute("prefix");
            String bookloreNamespace = "booklore: http://booklore.org/metadata/1.0/";

            if (!existingPrefix.contains("booklore:")) {
                if (existingPrefix.isEmpty()) {
                    packageElement.setAttribute("prefix", bookloreNamespace);
                } else {
                    packageElement.setAttribute("prefix", existingPrefix.trim() + " " + bookloreNamespace);
                }
            }
        }
        
        removeManagedBookloreMetadata(metadataElement);
        
        if (StringUtils.isNotBlank(metadata.getSubtitle())) {
            metadataElement.appendChild(createBookloreMetaElement(doc, "subtitle", metadata.getSubtitle(), epub3));
        }
        
        if (metadata.getPageCount() != null && metadata.getPageCount() > 0) {
            metadataElement.appendChild(createBookloreMetaElement(doc, "page_count", String.valueOf(metadata.getPageCount()), epub3));
        }
        
        if (metadata.getSeriesTotal() != null && metadata.getSeriesTotal() > 0) {
            metadataElement.appendChild(createBookloreMetaElement(doc, "series_total", String.valueOf(metadata.getSeriesTotal()), epub3));
        }
        
        if (metadata.getAmazonRating() != null && metadata.getAmazonRating() > 0) {
            metadataElement.appendChild(createBookloreMetaElement(doc, "amazon_rating", String.valueOf(metadata.getAmazonRating()), epub3));
        }
        
        if (metadata.getAmazonReviewCount() != null && metadata.getAmazonReviewCount() > 0) {
            metadataElement.appendChild(createBookloreMetaElement(doc, "amazon_review_count", String.valueOf(metadata.getAmazonReviewCount()), epub3));
        }
        
        if (metadata.getGoodreadsRating() != null && metadata.getGoodreadsRating() > 0) {
            metadataElement.appendChild(createBookloreMetaElement(doc, "goodreads_rating", String.valueOf(metadata.getGoodreadsRating()), epub3));
        }
        
        if (metadata.getGoodreadsReviewCount() != null && metadata.getGoodreadsReviewCount() > 0) {
            metadataElement.appendChild(createBookloreMetaElement(doc, "goodreads_review_count", String.valueOf(metadata.getGoodreadsReviewCount()), epub3));
        }
        
        if (metadata.getHardcoverRating() != null && metadata.getHardcoverRating() > 0) {
            metadataElement.appendChild(createBookloreMetaElement(doc, "hardcover_rating", String.valueOf(metadata.getHardcoverRating()), epub3));
        }
        
        if (metadata.getHardcoverReviewCount() != null && metadata.getHardcoverReviewCount() > 0) {
            metadataElement.appendChild(createBookloreMetaElement(doc, "hardcover_review_count", String.valueOf(metadata.getHardcoverReviewCount()), epub3));
        }
        
        if (metadata.getLubimyczytacRating() != null && metadata.getLubimyczytacRating() > 0) {
            metadataElement.appendChild(createBookloreMetaElement(doc, "lubimyczytac_rating", String.valueOf(metadata.getLubimyczytacRating()), epub3));
        }
        
        if (metadata.getRanobedbRating() != null && metadata.getRanobedbRating() > 0) {
            metadataElement.appendChild(createBookloreMetaElement(doc, "ranobedb_rating", String.valueOf(metadata.getRanobedbRating()), epub3));
        }

        if (metadata.getApplebooksReviewCount() != null && metadata.getApplebooksReviewCount() > 0) {
            metadataElement.appendChild(createBookloreMetaElement(doc, "applebooks_review_count", String.valueOf(metadata.getApplebooksReviewCount()), epub3));
        }

        if (metadata.getApplebooksRating() != null && metadata.getApplebooksRating() > 0) {
            metadataElement.appendChild(createBookloreMetaElement(doc, "applebooks_rating", String.valueOf(metadata.getApplebooksRating()), epub3));
        }

        if (metadata.getMoods() != null && !metadata.getMoods().isEmpty()) {
            String moodsJson = "[" + metadata.getMoods().stream()
                .map(mood -> "\"" + mood.getName().replace("\"", "\\\"") + "\"")
                .sorted()
                .collect(Collectors.joining(", ")) + "]";
            metadataElement.appendChild(createBookloreMetaElement(doc, "moods", moodsJson, epub3));
        }

        if (metadata.getTags() != null && !metadata.getTags().isEmpty()) {
            String tagsJson = "[" + metadata.getTags().stream()
                .map(tag -> "\"" + tag.getName().replace("\"", "\\\"") + "\"")
                .sorted()
                .collect(Collectors.joining(", ")) + "]";
            metadataElement.appendChild(createBookloreMetaElement(doc, "tags", tagsJson, epub3));
        }

        if (metadata.getAgeRating() != null) {
            metadataElement.appendChild(createBookloreMetaElement(doc, "age_rating", String.valueOf(metadata.getAgeRating()), epub3));
        }

        if (StringUtils.isNotBlank(metadata.getContentRating())) {
            metadataElement.appendChild(createBookloreMetaElement(doc, "content_rating", metadata.getContentRating(), epub3));
        }
    }

    private Element createBookloreMetaElement(Document doc, String property, String value, boolean epub3) {
        if (epub3) {
            Element meta = doc.createElementNS(OPF_NS, "meta");
            meta.setPrefix("opf");
            meta.setAttribute("property", "booklore:" + property);
            meta.setTextContent(value);
            return meta;
        } else {
            // EPUB2: use name/content attribute form
            Element meta = doc.createElementNS(doc.getDocumentElement().getNamespaceURI(), "meta");
            meta.setAttribute("name", "booklore:" + property);
            meta.setAttribute("content", value);
            return meta;
        }
    }

    private void organizePackageElements(Document document) {
        // Per the epubcheck dtd:
        // <package> must have as children elements, in this order:
        //     <metadata>, <manifest>, and <spine>, and optionally may
        //     include <tours> and/or <guide>, then `<collection>` items.
        Element metadata = getOrCreateMetadataElement(document);
        Element manifest = getOrCreateManifestElement(document);
        Element spine = getOrCreateSpineElement(document);
        Optional<Element> tours = getChild(document.getDocumentElement(), OPF_NS, "tours");
        Optional<Element> guide = getChild(document.getDocumentElement(), OPF_NS, "guide");
        List<Element> collections = getChildren(document.getDocumentElement(), OPF_NS, "collection");

        Element packageElement = document.getDocumentElement();

        packageElement.appendChild(metadata);
        packageElement.appendChild(manifest);
        packageElement.appendChild(spine);
        tours.ifPresent(packageElement::appendChild);
        guide.ifPresent(packageElement::appendChild);
        for (var collection : collections) {
            packageElement.appendChild(collection);
        }
    }

    private void organizeMetadataElements(Element metadataElement) {
        final String DC_NS = "http://purl.org/dc/elements/1.1/";
        List<Element> identifiers = new ArrayList<>();
        List<Element> titles = new ArrayList<>();
        List<Element> creators = new ArrayList<>();
        List<Element> contributors = new ArrayList<>();
        List<Element> languages = new ArrayList<>();
        List<Element> dates = new ArrayList<>();
        List<Element> publishers = new ArrayList<>();
        List<Element> descriptions = new ArrayList<>();
        List<Element> subjects = new ArrayList<>();
        List<Element> seriesMetas = new ArrayList<>();
        List<Element> bookloreMetas = new ArrayList<>();
        List<Element> modifiedMetas = new ArrayList<>();
        List<Element> otherMetas = new ArrayList<>();
        List<Node> otherNodes = new ArrayList<>();
        
        NodeList allChildren = metadataElement.getChildNodes();
        for (int i = 0; i < allChildren.getLength(); i++) {
            Node node = allChildren.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                if (node.getNodeType() != Node.TEXT_NODE || !node.getTextContent().isBlank()) {
                    otherNodes.add(node);
                }
                continue;
            }
            Element elem = (Element) node;
            String localName = elem.getLocalName();
            String ns = elem.getNamespaceURI();
            
            if (DC_NS.equals(ns)) {
                switch (localName) {
                    case "identifier" -> identifiers.add(elem);
                    case "title" -> titles.add(elem);
                    case "creator" -> creators.add(elem);
                    case "contributor" -> contributors.add(elem);
                    case "language" -> languages.add(elem);
                    case "date" -> dates.add(elem);
                    case "publisher" -> publishers.add(elem);
                    case "description" -> descriptions.add(elem);
                    case "subject" -> subjects.add(elem);
                    default -> otherNodes.add(elem);
                }
            } else if ("meta".equals(localName)) {
                String property = elem.getAttribute("property");
                String name = elem.getAttribute("name");
                if (property.startsWith("booklore:") || name.startsWith("booklore:")) {
                    bookloreMetas.add(elem);
                } else if (property.equals("dcterms:modified") || property.equals("calibre:timestamp")) {
                    modifiedMetas.add(elem);
                } else if (property.equals("belongs-to-collection") || property.equals("collection-type") || property.equals("group-position")
                        || "calibre:series".equals(name) || "calibre:series_index".equals(name)) {
                    seriesMetas.add(elem);
                } else {
                    otherMetas.add(elem);
                }
            } else {
                otherNodes.add(elem);
            }
        }
        
        while (metadataElement.hasChildNodes()) {
            metadataElement.removeChild(metadataElement.getFirstChild());
        }
        
        identifiers.forEach(metadataElement::appendChild);
        titles.forEach(metadataElement::appendChild);
        creators.forEach(metadataElement::appendChild);
        contributors.forEach(metadataElement::appendChild);
        languages.forEach(metadataElement::appendChild);
        dates.forEach(metadataElement::appendChild);
        publishers.forEach(metadataElement::appendChild);
        descriptions.forEach(metadataElement::appendChild);
        subjects.forEach(metadataElement::appendChild);
        seriesMetas.forEach(metadataElement::appendChild);
        modifiedMetas.forEach(metadataElement::appendChild);
        otherMetas.forEach(metadataElement::appendChild);
        bookloreMetas.forEach(metadataElement::appendChild);
        otherNodes.forEach(metadataElement::appendChild);
    }
}
