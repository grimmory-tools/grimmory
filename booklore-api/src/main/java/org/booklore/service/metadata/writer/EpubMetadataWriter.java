package org.booklore.service.metadata.writer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.booklore.model.MetadataClearFlags;
import org.booklore.model.dto.settings.MetadataPersistenceSettings;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.util.MimeDetector;
import org.grimmory.epub4j.native_parsing.NativeImageProcessor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Slf4j
@Component
public class EpubMetadataWriter implements MetadataWriter {

    private static final String OPF_NS = "http://www.idpf.org/2007/opf";
    private static final String DC_NS = "http://purl.org/dc/elements/1.1/";
    private static final Map<String, String> LANGUAGE_NAME_TO_CODE;
    private static final Set<String> ISO_LANGUAGES;
    private static final DateTimeFormatter EPUB_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);
    private static final Set<String> SUPPORTED_EPUB_IMAGE_TYPES =
            Set.of("image/jpeg", "image/png", "image/gif", "image/webp", "image/svg+xml");
    private static final Pattern PREFIX = Pattern.compile("calibre:\\s*https?://\\S+");

    static {
        Map<String, String> map = new HashMap<>();
        for (Locale locale : Locale.getAvailableLocales()) {
            String language = locale.getLanguage();
            if (language.isEmpty()) continue;
            map.putIfAbsent(locale.getDisplayLanguage(Locale.ENGLISH).toLowerCase(), language);
            map.putIfAbsent(locale.getDisplayLanguage(locale).toLowerCase(), language);
        }
        LANGUAGE_NAME_TO_CODE = Collections.unmodifiableMap(map);

        Set<String> langs = new HashSet<>();
        for (String iso : Locale.getISOLanguages()) {
            langs.add(iso.toLowerCase());
        }
        ISO_LANGUAGES = Collections.unmodifiableSet(langs);
    }

    private static final int MAX_COVER_BYTES = 20 * 1024 * 1024; // 20 MiB

    private final AppSettingService appSettingService;

    private final RestTemplate coverRestTemplate;

    public EpubMetadataWriter(
            AppSettingService appSettingService,
            @Qualifier("coverDownloadRestTemplate") RestTemplate coverRestTemplate) {
        this.appSettingService = appSettingService;
        this.coverRestTemplate = coverRestTemplate;
    }

    @Override
    public void saveMetadataToFile(File epubFile, BookMetadataEntity metadata, String thumbnailUrl, MetadataClearFlags clear) {
        if (!shouldSaveMetadataToFile(epubFile)) return;

        try {
            // Track whether any pass actually wrote changes
            boolean[] anyChanges = {false};

            // PRE-FETCH COVER
            byte[] rawCoverData = null;
            if (StringUtils.isNotBlank(thumbnailUrl)) {
                rawCoverData = loadImage(thumbnailUrl);
            }
            final byte[] finalCoverData = rawCoverData != null ? optimizeCoverImage(rawCoverData) : null;
            final String newCoverMediaType = finalCoverData != null ? detectMediaType(finalCoverData) : null;

            org.grimmory.epub4j.epub.EpubMetadataWriter.updateMetadata(epubFile.toPath(), opfDoc -> {
                Element metadataElement = getMetadataElement(opfDoc);
                if (metadataElement == null) return;

                boolean[] hasChanges = {false};
                MetadataCopyHelper helper = new MetadataCopyHelper(metadata);

                applyDcMetadata(opfDoc, metadataElement, metadata, helper, clear, hasChanges);
                applyAuthors(opfDoc, metadataElement, helper, clear, hasChanges);
                applyCategories(opfDoc, metadataElement, helper, clear, hasChanges);
                applySeries(opfDoc, metadataElement, metadata, helper, clear, hasChanges);
                applyIdentifiers(opfDoc, metadataElement, helper, clear, hasChanges);

                if (finalCoverData != null) {
                    hasChanges[0] = true;
                }

                if (!hasChanges[0] && hasBookloreMetadataChanges(metadataElement, metadata)) {
                    hasChanges[0] = true;
                }

                if (!hasChanges[0] && hasOrphanedRefines(metadataElement)) {
                    hasChanges[0] = true;
                }

                if (hasChanges[0]) {
                    addBookloreMetadata(metadataElement, opfDoc, metadata);
                    cleanupCalibreArtifacts(metadataElement, opfDoc);
                    organizeMetadataElements(metadataElement);
                    removeEmptyTextNodes(opfDoc);
                    anyChanges[0] = true;
                }

                // If we have cover data, update manifest
                if (newCoverMediaType != null) {
                    updateCoverManifestEntry(opfDoc, newCoverMediaType);
                }
            });

            if (finalCoverData != null) {
                org.grimmory.epub4j.epub.EpubMetadataWriter.replaceCoverImage(epubFile.toPath(), finalCoverData);
                anyChanges[0] = true;
            }

            // Normalize cover media-type to match actual image content
            if (fixCoverMediaTypeMismatch(epubFile)) {
                anyChanges[0] = true;
            }

            if (anyChanges[0]) {
                log.info("Metadata updated in EPUB: {}", epubFile.getName());
            } else {
                log.info("No changes detected. Skipping EPUB write for: {}", epubFile.getName());
            }
        } catch (Exception e) {
            log.warn("Failed to write metadata to EPUB file {}: {}", epubFile.getName(), e.getMessage(), e);
        }
    }

    public void replaceCoverImageFromBytes(BookEntity bookEntity, byte[] file) {
        if (!shouldSaveMetadataToFile(bookEntity.getFullFilePath().toFile())) return;
        if (file == null || file.length == 0) {
            log.warn("Cover update failed: empty or null byte array.");
            return;
        }
        replaceCoverImageInternal(bookEntity, file, "byte array");
    }

    public void replaceCoverImageFromUpload(BookEntity bookEntity, MultipartFile multipartFile) {
        if (!shouldSaveMetadataToFile(bookEntity.getFullFilePath().toFile())) return;
        if (multipartFile == null || multipartFile.isEmpty()) {
            log.warn("Cover upload failed: empty or null file.");
            return;
        }
        try {
            replaceCoverImageInternal(bookEntity, multipartFile.getBytes(), "upload");
        } catch (IOException e) {
            log.warn("Failed to read uploaded cover image: {}", e.getMessage(), e);
        }
    }

    @Override
    public void replaceCoverImageFromUrl(BookEntity bookEntity, String url) {
        if (!shouldSaveMetadataToFile(bookEntity.getFullFilePath().toFile())) return;
        if (url == null || url.isBlank()) {
            log.warn("Cover update via URL failed: empty or null URL.");
            return;
        }
        byte[] coverData = loadImage(url);
        if (coverData == null) {
            log.warn("Failed to load image from URL: {}", url);
            return;
        }
        replaceCoverImageInternal(bookEntity, coverData, "URL");
    }

    @Override
    public BookFileType getSupportedBookType() {
        return BookFileType.EPUB;
    }

    private void replaceCoverImageInternal(BookEntity bookEntity, byte[] coverData, String source) {
        try {
            Path epubPath = Path.of(bookEntity.getFullFilePath().toUri());
            byte[] optimized = optimizeCoverImage(coverData);
            String newMediaType = detectMediaType(optimized);

            // First pass: replace the cover image bytes in the ZIP
            // not FQN, for some reason complain when i import this
            org.grimmory.epub4j.epub.EpubMetadataWriter.replaceCoverImage(epubPath, optimized);

            // Second pass: update manifest media-type/href and dcterms:modified
            org.grimmory.epub4j.epub.EpubMetadataWriter.updateMetadata(epubPath, opfDoc -> {
                // Update manifest media-type and href if image format changed
                if (newMediaType != null) {
                    updateCoverManifestEntry(opfDoc, newMediaType);
                }

                Element metadataElement = getMetadataElement(opfDoc);
                if (metadataElement != null && isEpub3(opfDoc)) {
                    removeDctermsModifiedMeta(metadataElement);
                    removeCalibreTimestampMeta(metadataElement);

                    Element modified = opfDoc.createElementNS(OPF_NS, "meta");
                    modified.setAttribute("property", "dcterms:modified");
                    modified.setTextContent(ZonedDateTime.now().format(EPUB_DATE_FORMATTER));
                    metadataElement.appendChild(modified);
                    removeEmptyTextNodes(opfDoc);
                }
            });

            log.info("Cover image updated in EPUB from {}: {}", source, epubPath.getFileName());
        } catch (Exception e) {
            log.warn("Failed to update EPUB cover image from {}: {}", source, e.getMessage(), e);
        }
    }

    private void updateCoverManifestEntry(Document opfDoc, String newMediaType) {
        NodeList manifestList = opfDoc.getElementsByTagNameNS(OPF_NS, "manifest");
        if (manifestList.getLength() == 0) return;

        Element manifest = (Element) manifestList.item(0);
        Element coverItem = findCoverItem(opfDoc, manifest);
        if (coverItem == null) return;

        String currentMediaType = coverItem.getAttribute("media-type");
        if (!newMediaType.equals(currentMediaType)) {
            coverItem.setAttribute("media-type", newMediaType);
        }
    }

    private Element findCoverItem(Document opfDoc, Element manifest) {
        // Check metadata <meta name="cover" content="item-id"/>
        Element metadataElement = getMetadataElement(opfDoc);
        if (metadataElement != null) {
            String coverItemId = getMetaContentByName(metadataElement);
            if (coverItemId != null && !coverItemId.isBlank()) {
                NodeList items = manifest.getElementsByTagNameNS(OPF_NS, "item");
                for (int i = 0; i < items.getLength(); i++) {
                    Element item = (Element) items.item(i);
                    if (coverItemId.equals(item.getAttribute("id"))) return item;
                }
            }
        }

        // Check properties="cover-image" (EPUB 3)
        NodeList items = manifest.getElementsByTagNameNS(OPF_NS, "item");
        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            if (item.getAttribute("properties").contains("cover-image")) return item;
        }

        // Fallback: common cover id values
        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            String id = item.getAttribute("id");
            if ("cover-image".equals(id) || "cover".equals(id) || "coverimg".equals(id)) return item;
        }

        return null;
    }

    private byte[] optimizeCoverImage(byte[] coverData) {
        if (!NativeImageProcessor.isAvailable()) return coverData;

        String mediaType = detectMediaType(coverData);
        if ("image/jpeg".equals(mediaType)) {
            try (NativeImageProcessor.ImageData optimized = NativeImageProcessor.compressJpeg(coverData, 85, true)) {
                byte[] result = optimized.toByteArray();
                if (result.length < coverData.length) {
                    log.debug("Native JPEG optimization: {} → {} bytes", coverData.length, result.length);
                    return result;
                }
            } catch (Exception e) {
                log.warn("Native JPEG optimization failed: {}", e.getMessage());
            }
        } else if ("image/png".equals(mediaType)) {
            try (NativeImageProcessor.ImageData optimized = NativeImageProcessor.optimizePng(coverData, true)) {
                byte[] result = optimized.toByteArray();
                if (result.length < coverData.length) {
                    log.debug("Native PNG optimization: {} → {} bytes", coverData.length, result.length);
                    return result;
                }
            } catch (Exception e) {
                log.warn("Native PNG optimization failed: {}", e.getMessage());
            }
        }
        return coverData;
    }

    private void applyDcMetadata(Document opfDoc, Element metadataElement, BookMetadataEntity metadata,
                                 MetadataCopyHelper helper, MetadataClearFlags clear, boolean[] hasChanges) {
        helper.copyTitle(flagSet(clear, MetadataClearFlags::isTitle), val -> {
            replaceAndTrackChange(opfDoc, metadataElement, "title", DC_NS, val, hasChanges);
            if (StringUtils.isNotBlank(metadata.getSubtitle())) {
                addSubtitleToTitle(metadataElement, opfDoc, metadata.getSubtitle());
            }
        });
        helper.copyDescription(flagSet(clear, MetadataClearFlags::isDescription),
                val -> replaceAndTrackChange(opfDoc, metadataElement, "description", DC_NS, val, hasChanges));
        helper.copyPublisher(flagSet(clear, MetadataClearFlags::isPublisher),
                val -> replaceAndTrackChange(opfDoc, metadataElement, "publisher", DC_NS, val, hasChanges));
        helper.copyPublishedDate(flagSet(clear, MetadataClearFlags::isPublishedDate),
                val -> replaceAndTrackChange(opfDoc, metadataElement, "date", DC_NS, val != null ? val.toString() : null, hasChanges));
        helper.copyLanguage(flagSet(clear, MetadataClearFlags::isLanguage), val -> {
            String normalized = normalizeLanguage(val);
            replaceAndTrackChange(opfDoc, metadataElement, "language", DC_NS, normalized, hasChanges);
        });
    }

    private void applyAuthors(Document opfDoc, Element metadataElement,
                              MetadataCopyHelper helper, MetadataClearFlags clear, boolean[] hasChanges) {
        helper.copyAuthors(flagSet(clear, MetadataClearFlags::isAuthors), names -> {
            removeCreatorsByRole(metadataElement, "");
            removeCreatorsByRole(metadataElement, "aut");
            if (names != null) {
                for (String name : names) {
                    int sp = name.lastIndexOf(' ');
                    String fileAs;
                    if (sp <= 0) {
                        fileAs = name; // single-token or leading-space names
                    } else {
                        fileAs = name.substring(sp + 1) + ", " + name.substring(0, sp);
                    }
                    metadataElement.appendChild(createCreatorElement(opfDoc, metadataElement, name, fileAs));
                }
            }
            hasChanges[0] = true;
        });
    }

    private void applyCategories(Document opfDoc, Element metadataElement,
                                 MetadataCopyHelper helper, MetadataClearFlags clear, boolean[] hasChanges) {
        helper.copyCategories(flagSet(clear, MetadataClearFlags::isCategories), categories -> {
            removeElementsByTagNameNS(metadataElement);
            if (categories != null) {
                categories.stream().map(String::trim).distinct()
                        .forEach(cat -> metadataElement.appendChild(createSubjectElement(opfDoc, cat)));
            }
            hasChanges[0] = true;
        });
    }

    private void applySeries(Document opfDoc, Element metadataElement, BookMetadataEntity metadata,
                             MetadataCopyHelper helper, MetadataClearFlags clear, boolean[] hasChanges) {
        helper.copySeriesName(flagSet(clear, MetadataClearFlags::isSeriesName),
                val -> replaceBelongsToCollection(metadataElement, opfDoc, metadata.getSeriesName(), metadata.getSeriesNumber(), hasChanges));
        helper.copySeriesNumber(flagSet(clear, MetadataClearFlags::isSeriesNumber),
                val -> replaceBelongsToCollection(metadataElement, opfDoc, metadata.getSeriesName(), metadata.getSeriesNumber(), hasChanges));
    }

    private void applyIdentifiers(Document opfDoc, Element metadataElement,
                                  MetadataCopyHelper helper, MetadataClearFlags clear, boolean[] hasChanges) {
        copyIdentifier(helper::copyIsbn13, flagSet(clear, MetadataClearFlags::isIsbn13), opfDoc, metadataElement, "isbn", hasChanges);
        copyIdentifier(helper::copyIsbn10, flagSet(clear, MetadataClearFlags::isIsbn10), opfDoc, metadataElement, null, hasChanges);
        copyIdentifier(helper::copyAsin, flagSet(clear, MetadataClearFlags::isAsin), opfDoc, metadataElement, "amazon", hasChanges);
        copyIdentifier(helper::copyGoodreadsId, flagSet(clear, MetadataClearFlags::isGoodreadsId), opfDoc, metadataElement, "goodreads", hasChanges);
        copyIdentifier(helper::copyGoogleId, flagSet(clear, MetadataClearFlags::isGoogleId), opfDoc, metadataElement, "google", hasChanges);
        copyIdentifier(helper::copyComicvineId, flagSet(clear, MetadataClearFlags::isComicvineId), opfDoc, metadataElement, "comicvine", hasChanges);
        copyIdentifier(helper::copyHardcoverId, flagSet(clear, MetadataClearFlags::isHardcoverId), opfDoc, metadataElement, "hardcover", hasChanges);
        copyIdentifier(helper::copyHardcoverBookId, flagSet(clear, MetadataClearFlags::isHardcoverBookId), opfDoc, metadataElement, "hardcoverbook", hasChanges);
        copyIdentifier(helper::copyLubimyczytacId, flagSet(clear, MetadataClearFlags::isLubimyczytacId), opfDoc, metadataElement, "lubimyczytac", hasChanges);
        copyIdentifier(helper::copyRanobedbId, flagSet(clear, MetadataClearFlags::isRanobedbId), opfDoc, metadataElement, "ranobedb", hasChanges);
    }

    @FunctionalInterface
    private interface IdentifierCopyMethod {
        void copy(boolean clear, Consumer<String> consumer);
    }

    private void copyIdentifier(IdentifierCopyMethod method, boolean clearFlag,
                                Document opfDoc, Element metadataElement, String scheme, boolean[] hasChanges) {
        method.copy(clearFlag, val -> {
            if (scheme != null) removeIdentifierByUrn(metadataElement, scheme);
            if (val != null && !val.isBlank()) {
                String urnScheme = scheme != null ? scheme : "isbn";
                metadataElement.appendChild(createIdentifierElement(opfDoc, urnScheme, val));
            }
            hasChanges[0] = true;
        });
    }

    private static boolean flagSet(MetadataClearFlags clear, Predicate<MetadataClearFlags> getter) {
        return clear != null && getter.test(clear);
    }

    private void replaceAndTrackChange(Document doc, Element parent, String tag, String ns, String val, boolean[] flag) {
        if (replaceElementText(doc, parent, tag, ns, val)) flag[0] = true;
    }

    private boolean replaceElementText(Document doc, Element parent, String tagName, String namespaceURI, String newValue) {
        NodeList nodes = parent.getElementsByTagNameNS(namespaceURI, tagName);
        String currentValue = null;
        List<Node> preservedAttributes = new ArrayList<>();
        boolean epub3 = isEpub3(doc);

        if (nodes.getLength() > 0) {
            Element existing = (Element) nodes.item(0);
            currentValue = existing.getTextContent();
            var attrMap = existing.getAttributes();
            for (int i = 0; i < attrMap.getLength(); i++) {
                Node attr = attrMap.item(i);
                // Skip opf: namespace attributes when targeting EPUB3, they are EPUB2-only
                // and would produce invalid metadata if carried over to an EPUB3 document.
                if (epub3 && OPF_NS.equals(attr.getNamespaceURI())) continue;
                // Skip xmlns declarations, they are managed by the DOM serializer automatically.
                if (attr.getNodeName().startsWith("xmlns")) continue;
                preservedAttributes.add(attr);
            }
        }

        boolean changed = !Objects.equals(currentValue, newValue);

        for (int i = nodes.getLength() - 1; i >= 0; i--) {
            parent.removeChild(nodes.item(i));
        }

        if (newValue != null) {
            Element newElem = doc.createElementNS(namespaceURI, tagName);
            newElem.setPrefix("dc");
            newElem.setTextContent(newValue);
            for (Node attr : preservedAttributes) {
                if (attr.getNamespaceURI() != null) {
                    newElem.setAttributeNS(attr.getNamespaceURI(), attr.getNodeName(), attr.getNodeValue());
                } else {
                    newElem.setAttribute(attr.getNodeName(), attr.getNodeValue());
                }
            }
            parent.appendChild(newElem);
        }

        return changed;
    }

    private void removeElementsByTagNameNS(Element parent) {
        NodeList nodes = parent.getElementsByTagNameNS(EpubMetadataWriter.DC_NS, "subject");
        for (int i = nodes.getLength() - 1; i >= 0; i--) {
            Element subject = (Element) nodes.item(i);
            String id = subject.getAttribute("id");
            if (StringUtils.isNotBlank(id)) {
                removeMetaByRefines(parent, "#" + id);
            }
            parent.removeChild(subject);
        }
    }

    /** Removes {@code <meta name="calibre:timestamp" .../>} elements from metadata. */
    private void removeCalibreTimestampMeta(Element metadataElement) {
        NodeList metas = metadataElement.getElementsByTagNameNS("*", "meta");
        for (int i = metas.getLength() - 1; i >= 0; i--) {
            Element meta = (Element) metas.item(i);
            if ("calibre:timestamp".equals(meta.getAttribute("name"))) {
                metadataElement.removeChild(meta);
            }
        }
    }

    /** Removes {@code <meta property="dcterms:modified" .../>} elements from metadata. */
    private void removeDctermsModifiedMeta(Element metadataElement) {
        NodeList metas = metadataElement.getElementsByTagNameNS("*", "meta");
        for (int i = metas.getLength() - 1; i >= 0; i--) {
            Element meta = (Element) metas.item(i);
            if ("dcterms:modified".equals(meta.getAttribute("property"))) {
                metadataElement.removeChild(meta);
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

    private Element findMetaElement(Element metadataElement, Predicate<Element> filter) {
        NodeList metas = metadataElement.getElementsByTagNameNS("*", "meta");
        for (int i = 0; i < metas.getLength(); i++) {
            Element meta = (Element) metas.item(i);
            if (filter.test(meta)) return meta;
        }
        return null;
    }

    private String getMetaContentByName(Element metadataElement) {
        Element meta = findMetaElement(metadataElement, el -> "cover".equals(el.getAttribute("name")));
        return meta != null ? meta.getAttribute("content") : null;
    }

    private void removeIdentifierByUrn(Element metadataElement, String urnScheme) {
        NodeList identifiers = metadataElement.getElementsByTagNameNS("*", "identifier");
        String urnPrefix = "urn:" + urnScheme.toLowerCase() + ":";
        String barePrefix = urnScheme.toLowerCase() + ":";
        for (int i = identifiers.getLength() - 1; i >= 0; i--) {
            Element idElement = (Element) identifiers.item(i);
            String content = idElement.getTextContent().trim().toLowerCase();
            if (content.startsWith(urnPrefix) || content.startsWith(barePrefix)) {
                metadataElement.removeChild(idElement);
            }
        }
    }

    private Element createIdentifierElement(Document doc, String scheme, String value) {
        Element id = doc.createElementNS(DC_NS, "identifier");
        id.setPrefix("dc");
        id.setTextContent("urn:" + scheme.toLowerCase() + ":" + value);
        return id;
    }

    private void removeCreatorsByRole(Element metadataElement, String role) {
        NodeList creators = metadataElement.getElementsByTagNameNS("*", "creator");
        for (int i = creators.getLength() - 1; i >= 0; i--) {
            Element creatorElement = (Element) creators.item(i);
            String id = creatorElement.getAttribute("id");
            String creatorRole = creatorElement.getAttributeNS(OPF_NS, "role");
            if (StringUtils.isNotBlank(id) && StringUtils.isBlank(creatorRole)) {
                Element meta = findMetaElement(metadataElement,
                        el -> "role".equals(el.getAttribute("property")) && ("#" + id).equals(el.getAttribute("refines")));
                if (meta != null) {
                    creatorRole = meta.hasAttribute("content") ? meta.getAttribute("content").trim() : meta.getTextContent().trim();
                }
            }
            if (role.equalsIgnoreCase(creatorRole)) {
                metadataElement.removeChild(creatorElement);
                if (StringUtils.isNotBlank(id)) {
                    removeMetaByRefines(metadataElement, "#" + id);
                }
            }
        }
    }

    private Element createCreatorElement(Document doc, Element metadataElement, String fullName, String fileAs) {
        Element creator = doc.createElementNS(DC_NS, "creator");
        creator.setPrefix("dc");
        creator.setTextContent(fullName);

        if (isEpub3(doc)) {
            String creatorId = "creator-" + UUID.randomUUID().toString().substring(0, 8);
            creator.setAttribute("id", creatorId);

            if (fileAs != null) {
                Element fileAsMeta = doc.createElementNS(OPF_NS, "meta");
                fileAsMeta.setAttribute("refines", "#" + creatorId);
                fileAsMeta.setAttribute("property", "file-as");
                fileAsMeta.setTextContent(fileAs);
                metadataElement.appendChild(fileAsMeta);
            }

            Element roleMeta = doc.createElementNS(OPF_NS, "meta");
            roleMeta.setAttribute("refines", "#" + creatorId);
            roleMeta.setAttribute("property", "role");
            roleMeta.setAttribute("scheme", "marc:relators");
            roleMeta.setTextContent("aut");
            metadataElement.appendChild(roleMeta);
        } else {
            if (fileAs != null) creator.setAttributeNS(OPF_NS, "opf:file-as", fileAs);
            creator.setAttributeNS(OPF_NS, "opf:role", "aut");
        }
        return creator;
    }

    private Element createSubjectElement(Document doc, String subject) {
        Element subj = doc.createElementNS(DC_NS, "subject");
        subj.setPrefix("dc");
        subj.setTextContent(subject);
        return subj;
    }

    private void replaceBelongsToCollection(Element metadataElement, Document doc,
                                            String seriesName, Float seriesNumber, boolean[] hasChanges) {
        boolean epub3 = isEpub3(doc);

        // Remove existing series metadata (both EPUB3 and EPUB2 forms)
        NodeList metas = metadataElement.getElementsByTagNameNS("*", "meta");
        for (int i = metas.getLength() - 1; i >= 0; i--) {
            Element meta = (Element) metas.item(i);
            String property = meta.getAttribute("property");
            String name = meta.getAttribute("name");
            if ("belongs-to-collection".equals(property) || "collection-type".equals(property) || "group-position".equals(property)) {
                String id = meta.getAttribute("id");
                metadataElement.removeChild(meta);
                if (StringUtils.isNotBlank(id)) removeMetaByRefines(metadataElement, "#" + id);
            }
            if ("calibre:series".equals(name) || "calibre:series_index".equals(name)) {
                metadataElement.removeChild(meta);
            }
        }

        if (StringUtils.isBlank(seriesName)) return;

        if (epub3) {
            String collectionId = "collection-" + UUID.randomUUID().toString().substring(0, 8);

            Element collectionMeta = doc.createElementNS(OPF_NS, "meta");
            collectionMeta.setAttribute("id", collectionId);
            collectionMeta.setAttribute("property", "belongs-to-collection");
            collectionMeta.setTextContent(seriesName);
            metadataElement.appendChild(collectionMeta);

            Element typeMeta = doc.createElementNS(OPF_NS, "meta");
            typeMeta.setAttribute("property", "collection-type");
            typeMeta.setAttribute("refines", "#" + collectionId);
            typeMeta.setTextContent("series");
            metadataElement.appendChild(typeMeta);

            if (seriesNumber != null && seriesNumber > 0) {
                Element positionMeta = doc.createElementNS(OPF_NS, "meta");
                positionMeta.setAttribute("property", "group-position");
                positionMeta.setAttribute("refines", "#" + collectionId);
                positionMeta.setTextContent(formatSeriesNumber(seriesNumber));
                metadataElement.appendChild(positionMeta);
            }
        } else {
            Element seriesMeta = doc.createElementNS(doc.getDocumentElement().getNamespaceURI(), "meta");
            seriesMeta.setAttribute("name", "calibre:series");
            seriesMeta.setAttribute("content", seriesName);
            metadataElement.appendChild(seriesMeta);

            if (seriesNumber != null && seriesNumber > 0) {
                Element indexMeta = doc.createElementNS(doc.getDocumentElement().getNamespaceURI(), "meta");
                indexMeta.setAttribute("name", "calibre:series_index");
                indexMeta.setAttribute("content", formatSeriesNumber(seriesNumber));
                metadataElement.appendChild(indexMeta);
            }
        }

        hasChanges[0] = true;
    }

    private static String formatSeriesNumber(float seriesNumber) {
        return seriesNumber % 1.0f == 0 ? String.format("%.0f", seriesNumber) : String.valueOf(seriesNumber);
    }

    private void addSubtitleToTitle(Element metadataElement, Document doc, String subtitle) {
        boolean epub3 = isEpub3(doc);

        // Remove existing subtitle elements
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
            String subtitleId = "subtitle-" + UUID.randomUUID().toString().substring(0, 8);
            Element subtitleElement = doc.createElementNS(DC_NS, "title");
            subtitleElement.setPrefix("dc");
            subtitleElement.setAttribute("id", subtitleId);
            subtitleElement.setTextContent(subtitle);
            metadataElement.appendChild(subtitleElement);

            Element typeMeta = doc.createElementNS(OPF_NS, "meta");
            typeMeta.setAttribute("refines", "#" + subtitleId);
            typeMeta.setAttribute("property", "title-type");
            typeMeta.setTextContent("subtitle");
            metadataElement.appendChild(typeMeta);
        }
    }

    private void addBookloreMetadata(Element metadataElement, Document doc, BookMetadataEntity metadata) {
        boolean epub3 = isEpub3(doc);

        if (epub3) {
            Element packageElement = doc.getDocumentElement();
            String existingPrefix = packageElement.getAttribute("prefix");
            String bookloreNamespace = "booklore: http://booklore.org/metadata/1.0/";

            if (!existingPrefix.contains("booklore:")) {
                packageElement.setAttribute("prefix",
                        existingPrefix.isEmpty() ? bookloreNamespace : existingPrefix.trim() + " " + bookloreNamespace);
            }

            removeDctermsModifiedMeta(metadataElement);
            removeCalibreTimestampMeta(metadataElement);

            Element modified = doc.createElementNS(OPF_NS, "meta");
            modified.setAttribute("property", "dcterms:modified");
            modified.setTextContent(ZonedDateTime.now().format(EPUB_DATE_FORMATTER));
            metadataElement.appendChild(modified);
        }

        removeAllBookloreMetadata(metadataElement);

        appendBookloreMeta(metadataElement, doc, epub3, "subtitle", metadata.getSubtitle());
        appendBookloreMetaPositive(metadataElement, doc, epub3, "page_count", metadata.getPageCount());
        appendBookloreMetaPositive(metadataElement, doc, epub3, "series_total", metadata.getSeriesTotal());
        appendBookloreMetaPositive(metadataElement, doc, epub3, "amazon_rating", metadata.getAmazonRating());
        appendBookloreMetaPositive(metadataElement, doc, epub3, "amazon_review_count", metadata.getAmazonReviewCount());
        appendBookloreMetaPositive(metadataElement, doc, epub3, "goodreads_rating", metadata.getGoodreadsRating());
        appendBookloreMetaPositive(metadataElement, doc, epub3, "goodreads_review_count", metadata.getGoodreadsReviewCount());
        appendBookloreMetaPositive(metadataElement, doc, epub3, "hardcover_rating", metadata.getHardcoverRating());
        appendBookloreMetaPositive(metadataElement, doc, epub3, "hardcover_review_count", metadata.getHardcoverReviewCount());
        appendBookloreMetaPositive(metadataElement, doc, epub3, "lubimyczytac_rating", metadata.getLubimyczytacRating());
        appendBookloreMetaPositive(metadataElement, doc, epub3, "ranobedb_rating", metadata.getRanobedbRating());

        if (metadata.getMoods() != null && !metadata.getMoods().isEmpty()) {
            String json = "[" + metadata.getMoods().stream()
                    .map(m -> "\"" + m.getName().replace("\"", "\\\"") + "\"")
                    .sorted().collect(Collectors.joining(", ")) + "]";
            metadataElement.appendChild(createBookloreMetaElement(doc, "moods", json, epub3));
        }
        if (metadata.getTags() != null && !metadata.getTags().isEmpty()) {
            String json = "[" + metadata.getTags().stream()
                    .map(t -> "\"" + t.getName().replace("\"", "\\\"") + "\"")
                    .sorted().collect(Collectors.joining(", ")) + "]";
            metadataElement.appendChild(createBookloreMetaElement(doc, "tags", json, epub3));
        }
        if (metadata.getAgeRating() != null) {
            metadataElement.appendChild(createBookloreMetaElement(doc, "age_rating", String.valueOf(metadata.getAgeRating()), epub3));
        }
        if (StringUtils.isNotBlank(metadata.getContentRating())) {
            metadataElement.appendChild(createBookloreMetaElement(doc, "content_rating", metadata.getContentRating(), epub3));
        }
    }

    private void appendBookloreMeta(Element metadataElement, Document doc, boolean epub3, String property, String value) {
        if (StringUtils.isNotBlank(value)) {
            metadataElement.appendChild(createBookloreMetaElement(doc, property, value, epub3));
        }
    }

    private void appendBookloreMetaPositive(Element metadataElement, Document doc, boolean epub3, String property, Number value) {
        if (value != null && value.doubleValue() > 0) {
            metadataElement.appendChild(createBookloreMetaElement(doc, property, String.valueOf(value), epub3));
        }
    }

    private Element createBookloreMetaElement(Document doc, String property, String value, boolean epub3) {
        if (epub3) {
            Element meta = doc.createElementNS(OPF_NS, "meta");
            meta.setAttribute("property", "booklore:" + property);
            meta.setTextContent(value);
            return meta;
        } else {
            Element meta = doc.createElementNS(doc.getDocumentElement().getNamespaceURI(), "meta");
            meta.setAttribute("name", "booklore:" + property);
            meta.setAttribute("content", value);
            return meta;
        }
    }

    private void removeAllBookloreMetadata(Element metadataElement) {
        NodeList metas = metadataElement.getElementsByTagNameNS("*", "meta");
        for (int i = metas.getLength() - 1; i >= 0; i--) {
            Element meta = (Element) metas.item(i);
            if (meta.getAttribute("property").startsWith("booklore:") || meta.getAttribute("name").startsWith("booklore:")) {
                metadataElement.removeChild(meta);
            }
        }
    }

    private boolean hasBookloreMetadataChanges(Element metadataElement, BookMetadataEntity metadata) {
        Map<String, String> existing = new TreeMap<>();
        NodeList metas = metadataElement.getElementsByTagNameNS("*", "meta");
        for (int i = 0; i < metas.getLength(); i++) {
            Element meta = (Element) metas.item(i);
            String property = meta.getAttribute("property");
            String name = meta.getAttribute("name");
            String key = property.startsWith("booklore:") ? property : (name.startsWith("booklore:") ? name : null);
            if (key != null) {
                String value = meta.getAttribute("content").isEmpty() ? meta.getTextContent() : meta.getAttribute("content");
                if (!isEffectivelyZeroOrBlank(value)) existing.put(key, value);
            }
        }

        Map<String, String> expected = new TreeMap<>();
        if (StringUtils.isNotBlank(metadata.getSubtitle())) expected.put("booklore:subtitle", metadata.getSubtitle());
        if (metadata.getPageCount() != null && metadata.getPageCount() > 0) expected.put("booklore:page_count", String.valueOf(metadata.getPageCount()));
        if (metadata.getSeriesTotal() != null && metadata.getSeriesTotal() > 0) expected.put("booklore:series_total", String.valueOf(metadata.getSeriesTotal()));
        if (metadata.getAmazonRating() != null && metadata.getAmazonRating() > 0) expected.put("booklore:amazon_rating", String.valueOf(metadata.getAmazonRating()));
        if (metadata.getAmazonReviewCount() != null && metadata.getAmazonReviewCount() > 0) expected.put("booklore:amazon_review_count", String.valueOf(metadata.getAmazonReviewCount()));
        if (metadata.getGoodreadsRating() != null && metadata.getGoodreadsRating() > 0) expected.put("booklore:goodreads_rating", String.valueOf(metadata.getGoodreadsRating()));
        if (metadata.getGoodreadsReviewCount() != null && metadata.getGoodreadsReviewCount() > 0) expected.put("booklore:goodreads_review_count", String.valueOf(metadata.getGoodreadsReviewCount()));
        if (metadata.getHardcoverRating() != null && metadata.getHardcoverRating() > 0) expected.put("booklore:hardcover_rating", String.valueOf(metadata.getHardcoverRating()));
        if (metadata.getHardcoverReviewCount() != null && metadata.getHardcoverReviewCount() > 0) expected.put("booklore:hardcover_review_count", String.valueOf(metadata.getHardcoverReviewCount()));
        if (metadata.getLubimyczytacRating() != null && metadata.getLubimyczytacRating() > 0) expected.put("booklore:lubimyczytac_rating", String.valueOf(metadata.getLubimyczytacRating()));
        if (metadata.getRanobedbRating() != null && metadata.getRanobedbRating() > 0) expected.put("booklore:ranobedb_rating", String.valueOf(metadata.getRanobedbRating()));
        if (metadata.getMoods() != null && !metadata.getMoods().isEmpty()) {
            String json = "[" + metadata.getMoods().stream()
                    .map(m -> "\"" + m.getName().replace("\"", "\\\"") + "\"")
                    .sorted().collect(Collectors.joining(", ")) + "]";
            expected.put("booklore:moods", json);
        }
        if (metadata.getTags() != null && !metadata.getTags().isEmpty()) {
            String json = "[" + metadata.getTags().stream()
                    .map(t -> "\"" + t.getName().replace("\"", "\\\"") + "\"")
                    .sorted().collect(Collectors.joining(", ")) + "]";
            expected.put("booklore:tags", json);
        }
        if (metadata.getAgeRating() != null) expected.put("booklore:age_rating", String.valueOf(metadata.getAgeRating()));
        if (StringUtils.isNotBlank(metadata.getContentRating())) expected.put("booklore:content_rating", metadata.getContentRating());

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


    private void cleanupCalibreArtifacts(Element metadataElement, Document doc) {
        Element packageElement = doc.getDocumentElement();
        if (packageElement.hasAttribute("prefix")) {
            String prefix = packageElement.getAttribute("prefix");
            if (prefix.contains("calibre:")) {
                prefix = PREFIX.matcher(prefix).replaceAll("").trim();
                if (prefix.isEmpty()) packageElement.removeAttribute("prefix");
                else packageElement.setAttribute("prefix", prefix);
            }
        }

        if (metadataElement.hasAttribute("xmlns:calibre")) {
            metadataElement.removeAttribute("xmlns:calibre");
        }

        NodeList identifiers = metadataElement.getElementsByTagNameNS(DC_NS, "identifier");
        for (int i = identifiers.getLength() - 1; i >= 0; i--) {
            Element idElement = (Element) identifiers.item(i);
            String content = idElement.getTextContent().trim().toLowerCase();
            if (content.startsWith("calibre:") || content.startsWith("urn:calibre:")) {
                metadataElement.removeChild(idElement);
            }
        }

        NodeList contributors = metadataElement.getElementsByTagNameNS(DC_NS, "contributor");
        for (int i = contributors.getLength() - 1; i >= 0; i--) {
            Element contributor = (Element) contributors.item(i);
            if (contributor.getTextContent().toLowerCase().contains("calibre")) {
                String id = contributor.getAttribute("id");
                metadataElement.removeChild(contributor);
                if (StringUtils.isNotBlank(id)) removeMetaByRefines(metadataElement, "#" + id);
            }
        }

        NodeList metas = metadataElement.getElementsByTagNameNS("*", "meta");
        for (int i = metas.getLength() - 1; i >= 0; i--) {
            Element meta = (Element) metas.item(i);
            String property = meta.getAttribute("property");
            String name = meta.getAttribute("name");
            boolean isCalibreSeries = !isEpub3(doc) && ("calibre:series".equals(name) || "calibre:series_index".equals(name));
            if (!isCalibreSeries && (property.startsWith("calibre:") || name.startsWith("calibre:"))) {
                metadataElement.removeChild(meta);
            }
        }
    }

    private String normalizeLanguage(String lang) {
        if (lang == null || lang.isBlank()) return lang;

        String trimmed = lang.trim();

        String code = LANGUAGE_NAME_TO_CODE.get(trimmed.toLowerCase());
        if (code != null) return code;

        Locale tagLocale = Locale.forLanguageTag(trimmed);
        String tagLang = tagLocale.getLanguage();
        if (!tagLang.isEmpty() && ISO_LANGUAGES.contains(tagLang.toLowerCase())) {
            return tagLocale.toLanguageTag();
        }

        return trimmed;
    }

    public boolean shouldSaveMetadataToFile(File epubFile) {
        MetadataPersistenceSettings.SaveToOriginalFile settings =
                appSettingService.getAppSettings().getMetadataPersistenceSettings().getSaveToOriginalFile();
        MetadataPersistenceSettings.FormatSettings epubSettings = settings.getEpub();
        if (epubSettings == null || !epubSettings.isEnabled()) {
            log.debug("EPUB metadata writing is disabled. Skipping: {}", epubFile.getName());
            return false;
        }
        long fileSizeInMb = epubFile.length() / (1024 * 1024);
        if (fileSizeInMb > epubSettings.getMaxFileSizeInMb()) {
            log.info("EPUB file {} ({} MB) exceeds max size limit ({} MB). Skipping.",
                    epubFile.getName(), fileSizeInMb, epubSettings.getMaxFileSizeInMb());
            return false;
        }
        return true;
    }

    byte[] loadImage(String url) {
        if (url == null || url.isBlank()) return null;

        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            log.warn("Rejected malformed cover URL: {}", e.getMessage());
            return null;
        }

        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            log.warn("Rejected cover URL with disallowed scheme: {}", scheme);
            return null;
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            log.warn("Rejected cover URL with missing host");
            return null;
        }
        InetAddress resolvedAddress;
        try {
            resolvedAddress = InetAddress.getByName(host);
            if (resolvedAddress.isLoopbackAddress() || resolvedAddress.isLinkLocalAddress() || resolvedAddress.isSiteLocalAddress()) {
                log.warn("Rejected cover URL targeting private/internal address for host: {}", host);
                return null;
            }
        } catch (Exception e) {
            log.warn("Rejected cover URL — hostname could not be resolved: {}", host);
            return null;
        }
        URI safeUri;
        try {
            safeUri = new URI(
                    scheme.toLowerCase(),
                    null,
                    resolvedAddress.getHostAddress(),
                    uri.getPort(),
                    uri.getPath(),
                    uri.getQuery(),
                    null
            );
        } catch (Exception e) {
            log.warn("Rejected cover URL — could not reconstruct safe URI: {}", e.getMessage());
            return null;
        }

        try {
            return coverRestTemplate.execute(safeUri, HttpMethod.GET, null, response -> {
                MediaType contentType = response.getHeaders().getContentType();
                if (contentType == null || !contentType.getType().equalsIgnoreCase("image")) {
                    log.warn("Rejected cover response with non-image Content-Type: {}", contentType);
                    return null;
                }
                try (InputStream in = response.getBody()) {
                    byte[] data = in.readNBytes(MAX_COVER_BYTES + 1);
                    if (data.length > MAX_COVER_BYTES) {
                        log.warn("Rejected cover response exceeding {} MiB size cap", MAX_COVER_BYTES / (1024 * 1024));
                        return null;
                    }
                    return data;
                }
            });
        } catch (Exception e) {
            log.warn("Failed to fetch cover image from URL: {}", e.getMessage());
            return null;
        }
    }

    private String detectMediaType(byte[] data) {
        if (data == null || data.length == 0) return null;
        try {
            String mimeType = MimeDetector.detect(new ByteArrayInputStream(data));
            if (mimeType != null && SUPPORTED_EPUB_IMAGE_TYPES.contains(mimeType)) return mimeType;
        } catch (IOException e) {
            log.warn("Failed to detect media type: {}", e.getMessage());
        }
        return null;
    }

    private Element getMetadataElement(Document opfDoc) {
        NodeList metadataList = opfDoc.getElementsByTagNameNS(OPF_NS, "metadata");
        return metadataList.getLength() > 0 ? (Element) metadataList.item(0) : null;
    }

    private boolean isEpub3(Document doc) {
        String version = doc.getDocumentElement().getAttribute("version");
        return !version.trim().isEmpty() && version.trim().charAt(0) == '3';
    }

    private void removeEmptyTextNodes(Document doc) {
        try {
            XPath xpath = XPathFactory.newInstance().newXPath();
            NodeList emptyTextNodes = (NodeList) xpath.evaluate("//text()[normalize-space(.)='']", doc, XPathConstants.NODESET);
            for (int i = 0; i < emptyTextNodes.getLength(); i++) {
                Node node = emptyTextNodes.item(i);
                node.getParentNode().removeChild(node);
            }
        } catch (Exception e) {
            log.warn("Failed to remove empty text nodes", e);
        }
    }

    private boolean hasOrphanedRefines(Element metadataElement) {
        Set<String> ids = new HashSet<>();
        NodeList children = metadataElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() != Node.ELEMENT_NODE) continue;
            String id = ((Element) children.item(i)).getAttribute("id");
            if (!id.isEmpty()) ids.add(id);
        }
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() != Node.ELEMENT_NODE) continue;
            Element elem = (Element) children.item(i);
            if (!"meta".equals(elem.getLocalName())) continue;
            String refines = elem.getAttribute("refines");
            if (!refines.isEmpty() && refines.charAt(0) == '#' && !ids.contains(refines.substring(1))) {
                return true;
            }
        }
        return false;
    }

    private boolean fixCoverMediaTypeMismatch(File epubFile) {
        String actualType;
        String declaredType;

        try (ZipFile zip = new ZipFile(epubFile)) {
            ZipEntry containerEntry = zip.getEntry("META-INF/container.xml");
            if (containerEntry == null) return false;

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder db = dbf.newDocumentBuilder();

            Document containerDoc;
            try (InputStream is = zip.getInputStream(containerEntry)) {
                containerDoc = db.parse(is);
            }
            NodeList rootfiles = containerDoc.getElementsByTagName("rootfile");
            if (rootfiles.getLength() == 0) return false;
            String opfPath = ((Element) rootfiles.item(0)).getAttribute("full-path");
            if (opfPath == null || opfPath.isEmpty()) return false;

            ZipEntry opfEntry = zip.getEntry(opfPath);
            if (opfEntry == null) return false;
            Document opfDoc;
            try (InputStream is = zip.getInputStream(opfEntry)) {
                opfDoc = db.parse(is);
            }

            NodeList manifestList = opfDoc.getElementsByTagNameNS(OPF_NS, "manifest");
            if (manifestList.getLength() == 0) return false;
            Element manifest = (Element) manifestList.item(0);
            Element coverItem = findCoverItem(opfDoc, manifest);
            if (coverItem == null) return false;

            declaredType = coverItem.getAttribute("media-type");
            String href = coverItem.getAttribute("href");
            if (href.isEmpty() || declaredType.isEmpty()) return false;
            if (!declaredType.startsWith("image/")) return false;

            String basePath = opfPath.contains("/") ? opfPath.substring(0, opfPath.lastIndexOf('/') + 1) : "";
            String coverPath = basePath + URLDecoder.decode(href, StandardCharsets.UTF_8);

            ZipEntry coverEntry = zip.getEntry(coverPath);
            if (coverEntry == null) return false;

            byte[] coverBytes;
            try (InputStream is = zip.getInputStream(coverEntry)) {
                coverBytes = is.readAllBytes();
            }

            actualType = detectMediaType(coverBytes);
            if (actualType == null || actualType.equals(declaredType)) return false;
        } catch (Exception e) {
            log.debug("Cover media-type check skipped for {}: {}", epubFile.getName(), e.getMessage());
            return false;
        }

        try {
            org.grimmory.epub4j.epub.EpubMetadataWriter.updateMetadata(epubFile.toPath(), doc -> {
                updateCoverManifestEntry(doc, actualType);
                removeEmptyTextNodes(doc);
            });
            log.info("Fixed cover media-type mismatch in {}: {} → {}", epubFile.getName(), declaredType, actualType);
            return true;
        } catch (Exception e) {
            log.warn("Failed to fix cover media-type in {}: {}", epubFile.getName(), e.getMessage());
            return false;
        }
    }

    private void organizeMetadataElements(Element metadataElement) {
        // Canonical DC ordering per EPUB specification
        List<String> DC_ORDER = List.of(
                "identifier", "title", "creator", "contributor", "language",
                "date", "publisher", "description", "subject");

        Map<String, List<Element>> dcBuckets = new LinkedHashMap<>();
        for (String tag : DC_ORDER) dcBuckets.put(tag, new ArrayList<>());
        List<Element> otherDcElements = new ArrayList<>();
        List<Element> seriesMetas = new ArrayList<>();
        List<Element> bookloreMetas = new ArrayList<>();
        List<Element> modifiedMetas = new ArrayList<>();
        List<Element> otherMetas = new ArrayList<>();
        List<Element> linkElements = new ArrayList<>();
        List<Element> uncategorized = new ArrayList<>();

        // Build ID refines-meta lookup
        Set<String> allIds = new HashSet<>();
        Map<String, List<Element>> refinesMap = new HashMap<>();

        NodeList allChildren = metadataElement.getChildNodes();
        for (int i = 0; i < allChildren.getLength(); i++) {
            if (allChildren.item(i).getNodeType() != Node.ELEMENT_NODE) continue;
            String id = ((Element) allChildren.item(i)).getAttribute("id");
            if (!id.isEmpty()) allIds.add(id);
        }

        // First pass: identify refines metas
        for (int i = 0; i < allChildren.getLength(); i++) {
            if (allChildren.item(i).getNodeType() != Node.ELEMENT_NODE) continue;
            Element elem = (Element) allChildren.item(i);
            if ("meta".equals(elem.getLocalName())) {
                String refines = elem.getAttribute("refines");
                if (!refines.isEmpty() && refines.charAt(0) == '#' && allIds.contains(refines.substring(1))) {
                    refinesMap.computeIfAbsent(refines.substring(1), k -> new ArrayList<>()).add(elem);
                }
            }
        }

        // Second pass: categorize
        Set<Element> handledRefines = new HashSet<>();
        for (int i = 0; i < allChildren.getLength(); i++) {
            if (allChildren.item(i).getNodeType() != Node.ELEMENT_NODE) continue;
            Element elem = (Element) allChildren.item(i);
            String localName = elem.getLocalName();
            String ns = elem.getNamespaceURI();

            if (DC_NS.equals(ns)) {
                dcBuckets.getOrDefault(localName, otherDcElements).add(elem);
            } else if ("meta".equals(localName)) {
                String refines = elem.getAttribute("refines");
                if (!refines.isEmpty() && refines.charAt(0) == '#') {
                    if (allIds.contains(refines.substring(1))) {
                        handledRefines.add(elem);
                    }
                    // Either handled with parent or orphaned, skip either way
                    continue;
                }
                String property = elem.getAttribute("property");
                String name = elem.getAttribute("name");
                if (property.startsWith("booklore:") || name.startsWith("booklore:")) {
                    bookloreMetas.add(elem);
                } else if ("dcterms:modified".equals(property) || "calibre:timestamp".equals(property)) {
                    modifiedMetas.add(elem);
                } else if ("belongs-to-collection".equals(property) || "collection-type".equals(property)
                        || "group-position".equals(property) || "calibre:series".equals(name) || "calibre:series_index".equals(name)) {
                    seriesMetas.add(elem);
                } else {
                    otherMetas.add(elem);
                }
            } else if ("link".equals(localName)) {
                linkElements.add(elem);
            } else {
                uncategorized.add(elem);
            }
        }

        // Clear and re-append in canonical order
        while (metadataElement.hasChildNodes()) {
            metadataElement.removeChild(metadataElement.getFirstChild());
        }

        Consumer<Element> appendWithRefines = elem -> {
            metadataElement.appendChild(elem);
            String id = elem.getAttribute("id");
            if (!id.isEmpty()) {
                List<Element> refs = refinesMap.get(id);
                if (refs != null) refs.forEach(metadataElement::appendChild);
            }
        };

        dcBuckets.values().forEach(list -> list.forEach(appendWithRefines));
        otherDcElements.forEach(appendWithRefines);
        seriesMetas.forEach(appendWithRefines);
        modifiedMetas.forEach(metadataElement::appendChild);
        otherMetas.forEach(metadataElement::appendChild);
        linkElements.forEach(metadataElement::appendChild);
        bookloreMetas.forEach(metadataElement::appendChild);
        uncategorized.forEach(metadataElement::appendChild);
    }
}
