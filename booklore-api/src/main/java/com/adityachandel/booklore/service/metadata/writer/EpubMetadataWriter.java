package com.adityachandel.booklore.service.metadata.writer;

import com.adityachandel.booklore.model.MetadataClearFlags;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import lombok.extern.slf4j.Slf4j;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Component
public class EpubMetadataWriter implements MetadataWriter {

    private static final String OPF_NS = "http://www.idpf.org/2007/opf";

    @Override
    public void writeMetadataToFile(File epubFile, BookMetadataEntity metadata, String thumbnailUrl, MetadataClearFlags clear) {
        File backupFile = new File(epubFile.getParentFile(), epubFile.getName() + ".bak");
        try {
            Files.copy(epubFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            log.warn("Failed to create backup of EPUB {}: {}", epubFile.getName(), ex.getMessage());
            return;
        }
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("epub_edit_" + UUID.randomUUID());
            ZipFile zipFile = new ZipFile(epubFile);
            zipFile.extractAll(tempDir.toString());

            File opfFile = findOpfFile(tempDir.toFile());
            if (opfFile == null) {
                log.warn("Could not locate OPF file in EPUB");
                return;
            }

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder builder = dbf.newDocumentBuilder();
            Document opfDoc = builder.parse(opfFile);

            NodeList metadataList = opfDoc.getElementsByTagNameNS(OPF_NS, "metadata");
            Element metadataElement = (Element) metadataList.item(0);
            final String DC_NS = "http://purl.org/dc/elements/1.1/";

            boolean[] hasChanges = {false};
            MetadataCopyHelper helper = new MetadataCopyHelper(metadata);

            helper.copyTitle(clear != null && clear.isTitle(), val -> replaceAndTrackChange(opfDoc, metadataElement, "title", DC_NS, val, hasChanges));
            helper.copyDescription(clear != null && clear.isDescription(), val -> replaceAndTrackChange(opfDoc, metadataElement, "description", DC_NS, val, hasChanges));
            helper.copyPublisher(clear != null && clear.isPublisher(), val -> replaceAndTrackChange(opfDoc, metadataElement, "publisher", DC_NS, val, hasChanges));
            helper.copyPublishedDate(clear != null && clear.isPublishedDate(), val -> replaceAndTrackChange(opfDoc, metadataElement, "date", DC_NS, val != null ? val.toString() : null, hasChanges));
            helper.copyLanguage(clear != null && clear.isLanguage(), val -> replaceAndTrackChange(opfDoc, metadataElement, "language", DC_NS, val, hasChanges));

            helper.copyAuthors(clear != null && clear.isAuthors(), names -> {
                removeElementsByTagNameNS(metadataElement, DC_NS, "creator");
                if (names != null) {
                    for (String name : names) {
                        String[] parts = name.split(" ", 2);
                        String first = parts.length > 1 ? parts[0] : "";
                        String last = parts.length > 1 ? parts[1] : parts[0];
                        String fileAs = last + ", " + first;
                        metadataElement.appendChild(createCreatorElement(opfDoc, name, fileAs, "aut"));
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

            helper.copySeriesName(clear != null && clear.isSeriesName(), val -> {
                replaceMetaElement(metadataElement, opfDoc, "calibre:series", val, hasChanges);
            });

            helper.copySeriesNumber(clear != null && clear.isSeriesNumber(), val -> {
                String formatted = val != null ? String.format("%.1f", val) : null;
                replaceMetaElement(metadataElement, opfDoc, "calibre:series_index", formatted, hasChanges);
            });

            helper.copyPersonalRating(clear != null && clear.isPersonalRating(), val -> {
                String formatted = val != null ? String.format("%.1f", val) : null;
                replaceMetaElement(metadataElement, opfDoc, "calibre:rating", formatted, hasChanges);
            });

            List<String> schemes = List.of("AMAZON", "GOOGLE", "GOODREADS", "HARDCOVER", "ISBN");

            for (String scheme : schemes) {

                boolean clearFlag = clear != null && switch (scheme) {
                    case "AMAZON" -> clear.isAsin();
                    case "GOOGLE" -> clear.isGoogleId();
                    case "COMICVINE" -> clear.isComicvineId();
                    case "GOODREADS" -> clear.isGoodreadsId();
                    case "HARDCOVER" -> clear.isHardcoverId();
                    case "ISBN" -> clear.isIsbn10();
                    default -> false;
                };

                switch (scheme) {
                    case "AMAZON" -> helper.copyAsin(clearFlag, idValue -> {
                        updateIdentifier(metadataElement, opfDoc, scheme, idValue, hasChanges);
                    });
                    case "GOOGLE" -> helper.copyGoogleId(clearFlag, idValue -> {
                        updateIdentifier(metadataElement, opfDoc, scheme, idValue, hasChanges);
                    });
                    case "GOODREADS" -> helper.copyGoodreadsId(clearFlag, idValue -> {
                        updateIdentifier(metadataElement, opfDoc, scheme, idValue, hasChanges);
                    });
                    case "COMICVINE" -> helper.copyComicvineId(clearFlag, idValue -> {
                        updateIdentifier(metadataElement, opfDoc, scheme, idValue, hasChanges);
                    });
                    case "HARDCOVER" -> helper.copyHardcoverId(clearFlag, idValue -> {
                        updateIdentifier(metadataElement, opfDoc, scheme, idValue, hasChanges);
                    });
                    case "ISBN" -> helper.copyIsbn13(clearFlag, idValue -> {
                        updateIdentifier(metadataElement, opfDoc, scheme, idValue, hasChanges);
                    });
                }
            }

            if (StringUtils.isNotBlank(thumbnailUrl)) {
                byte[] coverData = loadImage(thumbnailUrl);
                if (coverData != null) {
                    applyCoverImageToEpub(tempDir, opfDoc, coverData);
                    hasChanges[0] = true;
                }
            }

            if (hasChanges[0]) {
                Transformer transformer = TransformerFactory.newInstance().newTransformer();
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
                transformer.transform(new DOMSource(opfDoc), new StreamResult(opfFile));

                File tempEpub = new File(epubFile.getParentFile(), epubFile.getName() + ".tmp");
                addFolderContentsToZip(new ZipFile(tempEpub), tempDir.toFile(), tempDir.toFile());

                if (!epubFile.delete()) throw new IOException("Could not delete original EPUB");
                if (!tempEpub.renameTo(epubFile)) throw new IOException("Could not rename temp EPUB");

                log.info("Metadata updated in EPUB: {}", epubFile.getName());
            } else {
                log.info("No changes detected. Skipping EPUB write for: {}", epubFile.getName());
            }
        } catch (Exception e) {
            log.warn("Failed to write metadata to EPUB file {}: {}", epubFile.getName(), e.getMessage(), e);
            if (backupFile.exists()) {
                try {
                    Files.copy(backupFile.toPath(), epubFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    log.info("Restored EPUB from backup: {}", epubFile.getName());
                } catch (IOException io) {
                    log.error("Failed to restore EPUB from backup for {}: {}", epubFile.getName(), io.getMessage(), io);
                }
            }
        } finally {
            if (tempDir != null) {
                deleteDirectoryRecursively(tempDir);
            }
            if (backupFile.exists()) {
                try {
                    Files.delete(backupFile.toPath());
                } catch (IOException ex) {
                    log.warn("Failed to delete backup for {}: {}", epubFile.getName(), ex.getMessage());
                }
            }
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

    private void replaceMetaElement(Element metadataElement, Document doc, String name, String newVal, boolean[] flag) {
        String existing = getMetaContentByName(metadataElement, name);
        if (!Objects.equals(existing, newVal)) {
            removeMetaByName(metadataElement, name);
            if (newVal != null) metadataElement.appendChild(createMetaElement(doc, name, newVal));
            flag[0] = true;
        }
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

    public void replaceCoverImageFromUpload(BookEntity bookEntity, MultipartFile multipartFile) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            log.warn("Cover upload failed: empty or null file.");
            return;
        }

        Path tempDir = null;
        try {
            File epubFile = new File(bookEntity.getFullFilePath().toUri());
            tempDir = Files.createTempDirectory("epub_cover_" + UUID.randomUUID());
            new ZipFile(epubFile).extractAll(tempDir.toString());

            File opfFile = findOpfFile(tempDir.toFile());
            if (opfFile == null) {
                log.warn("OPF file not found in EPUB: {}", epubFile.getName());
                return;
            }

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder builder = dbf.newDocumentBuilder();
            Document opfDoc = builder.parse(opfFile);

            byte[] coverData = multipartFile.getBytes();
            applyCoverImageToEpub(tempDir, opfDoc, coverData);

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.transform(new DOMSource(opfDoc), new StreamResult(opfFile));

            File tempEpub = new File(epubFile.getParentFile(), epubFile.getName() + ".tmp");
            addFolderContentsToZip(new ZipFile(tempEpub), tempDir.toFile(), tempDir.toFile());

            if (!epubFile.delete()) throw new IOException("Could not delete original EPUB");
            if (!tempEpub.renameTo(epubFile)) throw new IOException("Could not rename temp EPUB");

            log.info("Cover image updated in EPUB: {}", epubFile.getName());

        } catch (Exception e) {
            log.warn("Failed to update EPUB with uploaded cover image: {}", e.getMessage(), e);
        } finally {
            if (tempDir != null) {
                deleteDirectoryRecursively(tempDir);
            }
        }
    }

    @Override
    public void replaceCoverImageFromUrl(BookEntity bookEntity, String url) {
        if (url == null || url.isBlank()) {
            log.warn("Cover update via URL failed: empty or null URL.");
            return;
        }
        Path tempDir = null;
        try {
            File epubFile = new File(bookEntity.getFullFilePath().toUri());
            tempDir = Files.createTempDirectory("epub_cover_url_" + UUID.randomUUID());
            new ZipFile(epubFile).extractAll(tempDir.toString());

            File opfFile = findOpfFile(tempDir.toFile());
            if (opfFile == null) {
                log.warn("OPF file not found in EPUB: {}", epubFile.getName());
                return;
            }

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder builder = dbf.newDocumentBuilder();
            Document opfDoc = builder.parse(opfFile);

            byte[] coverData = loadImage(url);
            if (coverData == null) {
                log.warn("Failed to load image from URL: {}", url);
                return;
            }

            applyCoverImageToEpub(tempDir, opfDoc, coverData);

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.transform(new DOMSource(opfDoc), new StreamResult(opfFile));

            File tempEpub = new File(epubFile.getParentFile(), epubFile.getName() + ".tmp");
            addFolderContentsToZip(new ZipFile(tempEpub), tempDir.toFile(), tempDir.toFile());

            if (!epubFile.delete()) throw new IOException("Could not delete original EPUB");
            if (!tempEpub.renameTo(epubFile)) throw new IOException("Could not rename temp EPUB");

            log.info("Cover image updated in EPUB via URL: {}", epubFile.getName());
        } catch (Exception e) {
            log.warn("Failed to update EPUB with cover from URL: {}", e.getMessage(), e);
        } finally {
            if (tempDir != null) {
                deleteDirectoryRecursively(tempDir);
            }
        }
    }

    @Override
    public BookFileType getSupportedBookType() {
        return BookFileType.EPUB;
    }

    private void applyCoverImageToEpub(Path tempDir, Document opfDoc, byte[] coverData) throws IOException {
        NodeList manifestList = opfDoc.getElementsByTagNameNS(OPF_NS, "manifest");
        if (manifestList.getLength() == 0) {
            throw new IOException("No <manifest> element found in OPF document.");
        }

        Element manifest = (Element) manifestList.item(0);
        Element existingCoverItem = null;

        NodeList items = manifest.getElementsByTagNameNS(OPF_NS, "item");
        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            if ("cover-image".equals(item.getAttribute("id"))) {
                existingCoverItem = item;
                break;
            }
        }

        String coverHref = existingCoverItem != null ? existingCoverItem.getAttribute("href") : "images/cover.jpg";

        Path opfPath;
        try {
            opfPath = findOpfPath(tempDir);
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("Failed to parse container.xml to locate OPF path", e);
        }

        Path opfDir = opfPath.getParent();
        Path coverFilePath = opfDir.resolve(coverHref).normalize();
        Files.createDirectories(coverFilePath.getParent());
        Files.write(coverFilePath, coverData);

        if (existingCoverItem != null) {
            manifest.removeChild(existingCoverItem);
        }

        Element newItem = opfDoc.createElementNS(OPF_NS, "item");
        newItem.setAttribute("id", "cover-image");
        newItem.setAttribute("href", coverHref);
        newItem.setAttribute("media-type", "image/jpeg");
        manifest.appendChild(newItem);

        NodeList metadataList = opfDoc.getElementsByTagNameNS(OPF_NS, "metadata");
        if (metadataList.getLength() == 0) {
            throw new IOException("No <metadata> element found in OPF document.");
        }

        Element metadataElement = (Element) metadataList.item(0);
        removeMetaByName(metadataElement, "cover");

        Element meta = opfDoc.createElementNS(OPF_NS, "meta");
        meta.setAttribute("name", "cover");
        meta.setAttribute("content", "cover-image");
        metadataElement.appendChild(meta);
    }

    private Path findOpfPath(Path tempDir) throws IOException, ParserConfigurationException, SAXException {
        Path containerXml = tempDir.resolve("META-INF/container.xml");
        if (!Files.exists(containerXml)) {
            throw new IOException("container.xml not found at expected location: " + containerXml);
        }

        Document containerDoc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(containerXml.toFile());
        Node rootfile = containerDoc.getElementsByTagName("rootfile").item(0);
        if (rootfile == null) {
            throw new IOException("No <rootfile> found in container.xml");
        }

        String opfPath = ((Element) rootfile).getAttribute("full-path");
        if (opfPath.isBlank()) {
            throw new IOException("Missing or empty 'full-path' attribute in <rootfile>");
        }

        return tempDir.resolve(opfPath).normalize();
    }

    private File findOpfFile(File rootDir) {
        File[] matches = rootDir.listFiles(path -> path.isFile() && path.getName().endsWith(".opf"));
        if (matches != null && matches.length > 0) return matches[0];
        for (File file : Objects.requireNonNull(rootDir.listFiles())) {
            if (file.isDirectory()) {
                File child = findOpfFile(file);
                if (child != null) return child;
            }
        }
        return null;
    }

    private byte[] loadImage(String pathOrUrl) {
        try (InputStream stream = pathOrUrl.startsWith("http") ? new URL(pathOrUrl).openStream() : new FileInputStream(pathOrUrl)) {
            return stream.readAllBytes();
        } catch (IOException e) {
            log.warn("Failed to load image from {}: {}", pathOrUrl, e.getMessage());
            return null;
        }
    }

    private void addFolderContentsToZip(ZipFile zipFile, File baseDir, File currentDir) throws IOException {
        File[] files = Objects.requireNonNull(currentDir.listFiles());
        for (File file : files) {
            if (file.isDirectory()) {
                addFolderContentsToZip(zipFile, baseDir, file);
            } else {
                ZipParameters params = new ZipParameters();
                params.setFileNameInZip(baseDir.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/'));
                zipFile.addFile(file, params);
            }
        }
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

    private Element createIdentifierElement(Document doc, String scheme, String value) {
        Element id = doc.createElementNS("http://purl.org/dc/elements/1.1/", "identifier");
        id.setPrefix("dc");
        id.setAttributeNS(OPF_NS, "opf:scheme", scheme);
        id.setTextContent(value);
        return id;
    }

    private void removeElementsByTagNameNS(Element parent, String namespaceURI, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS(namespaceURI, localName);
        for (int i = nodes.getLength() - 1; i >= 0; i--) {
            parent.removeChild(nodes.item(i));
        }
    }

    private Element createCreatorElement(Document doc, String fullName, String fileAs, String role) {
        Element creator = doc.createElementNS("http://purl.org/dc/elements/1.1/", "creator");
        creator.setPrefix("dc");
        creator.setTextContent(fullName);
        if (fileAs != null) {
            creator.setAttributeNS(OPF_NS, "opf:file-as", fileAs);
        }
        if (role != null) {
            creator.setAttributeNS(OPF_NS, "opf:role", role);
        }
        return creator;
    }

    private Element createSubjectElement(Document doc, String subject) {
        Element subj = doc.createElementNS("http://purl.org/dc/elements/1.1/", "subject");
        subj.setPrefix("dc");
        subj.setTextContent(subject);
        return subj;
    }

    private String getMetaContentByName(Element metadataElement, String name) {
        NodeList metas = metadataElement.getElementsByTagNameNS("*", "meta");
        for (int i = 0; i < metas.getLength(); i++) {
            Element meta = (Element) metas.item(i);
            if (name.equals(meta.getAttribute("name"))) {
                return meta.getAttribute("content");
            }
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
}
