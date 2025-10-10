package com.adityachandel.booklore.service.metadata.writer;

import com.adityachandel.booklore.model.MetadataClearFlags;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

@Slf4j
@Component
public class CbxMetadataWriter implements MetadataWriter {

    @Override
    public void writeMetadataToFile(File file, BookMetadataEntity metadata, String thumbnailUrl, MetadataClearFlags clearFlags) {
        Path backup = null;
        Path tempDir = null;
        Path tempFile = null;
        boolean writeSucceeded = false;
        try {
            // Create a backup next to the source file (temp name, safe to delete later)
            backup = Files.createTempFile(file.getParentFile().toPath(), "cbx_backup_", ".bak");
            Files.copy(file.toPath(), backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ex) {
            log.warn("Unable to create backup for {}: {}", file.getAbsolutePath(), ex.getMessage(), ex);
        }
        try {
            String nameLower = file.getName().toLowerCase(Locale.ROOT);
            boolean isCbz = nameLower.endsWith(".cbz");
            boolean isCbr = nameLower.endsWith(".cbr");
            boolean isCb7 = nameLower.endsWith(".cb7");

            if (!isCbz && !isCbr && !isCb7) {
                log.warn("Unsupported file type for CBX writer: {}", file.getName());
                return;
            }

            // Build (or load and update) ComicInfo.xml as a Document
            Document doc;
            if (isCbz) {
                try (ZipFile zipFile = new ZipFile(file)) {
                    ZipEntry existing = findComicInfoEntry(zipFile);
                    if (existing != null) {
                        try (InputStream is = zipFile.getInputStream(existing)) {
                            doc = buildSecureDocument(is);
                        }
                    } else {
                        doc = newEmptyComicInfo();
                    }
                }
            } else if (isCb7) {
                try (SevenZFile sevenZ = new SevenZFile(file)) {
                    SevenZArchiveEntry existing = null;
                    for (SevenZArchiveEntry e : sevenZ.getEntries()) {
                        if (e != null && !e.isDirectory() && isComicInfoName(e.getName())) {
                            existing = e;
                            break;
                        }
                    }
                    if (existing != null) {
                        try (InputStream is = sevenZ.getInputStream(existing)) {
                            doc = buildSecureDocument(is);
                        }
                    } else {
                        doc = newEmptyComicInfo();
                    }
                }
            } else { // CBR
                try (Archive archive = new Archive(file)) {
                    FileHeader existing = findComicInfoHeader(archive);
                    if (existing != null) {
                        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                            archive.extractFile(existing, baos);
                            try (InputStream is = new java.io.ByteArrayInputStream(baos.toByteArray())) {
                                doc = buildSecureDocument(is);
                            }
                        }
                    } else {
                        doc = newEmptyComicInfo();
                    }
                }
            }

            // Apply metadata to the Document
            Element root = doc.getDocumentElement();
            MetadataCopyHelper helper = new MetadataCopyHelper(metadata);
            helper.copyTitle(clearFlags != null && clearFlags.isTitle(), val -> setElement(doc, root, "Title", val));
            helper.copyDescription(clearFlags != null && clearFlags.isDescription(), val -> {
                setElement(doc, root, "Summary", val);
                removeElement(root, "Description");
            });
            helper.copyPublisher(clearFlags != null && clearFlags.isPublisher(), val -> setElement(doc, root, "Publisher", val));
            helper.copySeriesName(clearFlags != null && clearFlags.isSeriesName(), val -> setElement(doc, root, "Series", val));
            helper.copySeriesNumber(clearFlags != null && clearFlags.isSeriesNumber(), val -> setElement(doc, root, "Number", formatFloat(val)));
            helper.copySeriesTotal(clearFlags != null && clearFlags.isSeriesTotal(), val -> setElement(doc, root, "Count", val != null ? val.toString() : null));
            helper.copyPublishedDate(clearFlags != null && clearFlags.isPublishedDate(), date -> setDateElements(doc, root, date));
            helper.copyPageCount(clearFlags != null && clearFlags.isPageCount(), val -> setElement(doc, root, "PageCount", val != null ? val.toString() : null));
            helper.copyLanguage(clearFlags != null && clearFlags.isLanguage(), val -> setElement(doc, root, "LanguageISO", val));
            helper.copyAuthors(clearFlags != null && clearFlags.isAuthors(), set -> {
                setElement(doc, root, "Writer", join(set));
                removeElement(root, "Penciller");
                removeElement(root, "Inker");
                removeElement(root, "Colorist");
                removeElement(root, "Letterer");
                removeElement(root, "CoverArtist");
            });
            helper.copyCategories(clearFlags != null && clearFlags.isCategories(), set -> {
                setElement(doc, root, "Genre", join(set));
                removeElement(root, "Tags");
            });

            // Serialize ComicInfo.xml
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            ByteArrayOutputStream xmlBaos = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(doc), new StreamResult(xmlBaos));
            byte[] xmlBytes = xmlBaos.toByteArray();

            // Repack depending on container type; always write to a temp target then atomic move
            if (isCbz) {
                tempFile = Files.createTempFile("cbx_edit", ".cbz");
                repackZipReplacingComicInfo(file.toPath(), tempFile, xmlBytes);
                atomicReplace(tempFile, file.toPath());
                tempFile = null; // Successfully moved, don't delete in finally
                writeSucceeded = true;
                return;
            }

            if (isCb7) {
                // Convert to CBZ with updated ComicInfo.xml
                tempFile = Files.createTempFile("cbx_edit", ".cbz");
                try (SevenZFile sevenZ = new SevenZFile(file);
                     ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempFile))) {
                    for (SevenZArchiveEntry e : sevenZ.getEntries()) {
                        if (e.isDirectory()) continue;
                        String entryName = e.getName();
                        if (isComicInfoName(entryName)) continue; // skip old
                        if (!isSafeEntryName(entryName)) {
                            log.warn("Skipping unsafe 7z entry name: {}", entryName);
                            continue;
                        }
                        zos.putNextEntry(new ZipEntry(entryName));
                        try (InputStream is = sevenZ.getInputStream(e)) {
                            if (is != null) is.transferTo(zos);
                        }
                        zos.closeEntry();
                    }
                    zos.putNextEntry(new ZipEntry("ComicInfo.xml"));
                    zos.write(xmlBytes);
                    zos.closeEntry();
                }
                Path target = file.toPath().resolveSibling(stripExtension(file.getName()) + ".cbz");
                atomicReplace(tempFile, target);
                tempFile = null; // Successfully moved, don't delete in finally
                try {
                    Files.deleteIfExists(file.toPath());
                } catch (Exception ignored) {
                }
                writeSucceeded = true;
                return;
            }

            // CBR path
            String rarBin = System.getenv().getOrDefault("BOOKLORE_RAR_BIN", "rar");
            boolean rarAvailable = isRarAvailable(rarBin);

            if (rarAvailable) {
                tempDir = Files.createTempDirectory("cbx_rar_");
                try {
                    // Extract entire RAR into a temp directory
                    try (Archive archive = new Archive(file)) {
                        for (FileHeader fh : archive.getFileHeaders()) {
                            String name = fh.getFileName();
                            if (name == null || name.isBlank()) continue;
                            if (!isSafeEntryName(name)) {
                                log.warn("Skipping unsafe RAR entry name: {}", name);
                                continue;
                            }
                            Path out = tempDir.resolve(name).normalize();
                            if (!out.startsWith(tempDir)) {
                                log.warn("Skipping traversal entry outside tempDir: {}", name);
                                continue;
                            }
                            if (fh.isDirectory()) {
                                Files.createDirectories(out);
                            } else {
                                Files.createDirectories(out.getParent());
                                try (OutputStream os = Files.newOutputStream(out, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                                    archive.extractFile(fh, os);
                                }
                            }
                        }
                    }

                    // Write/replace ComicInfo.xml in extracted tree root
                    Path comicInfo = tempDir.resolve("ComicInfo.xml");
                    Files.write(comicInfo, xmlBytes);

                    // Rebuild RAR in-place (replace original file)
                    Path targetRar = file.toPath().toAbsolutePath().normalize();
                    String rarExec = isSafeExecutable(rarBin) ? rarBin : "rar"; // prefer validated path, then PATH lookup
                    ProcessBuilder pb = new ProcessBuilder(rarExec, "a", "-idq", "-ep1", "-ma5", targetRar.toString(), ".");
                    pb.directory(tempDir.toFile());
                    Process p = pb.start();
                    int code = p.waitFor();
                    if (code == 0) {
                        writeSucceeded = true;
                        return;
                    } else {
                        log.warn("RAR creation failed with exit code {}. Falling back to CBZ conversion for {}", code, file.getName());
                    }
                } finally {
                    // tempDir cleanup will be handled in outer finally block
                }
            } else {
                log.warn("`rar` binary not found. Falling back to CBZ conversion for {}", file.getName());
            }

            // Fallback: convert the CBR to CBZ containing updated ComicInfo.xml
            tempFile = Files.createTempFile("cbx_edit", ".cbz");
            try (Archive archive = new Archive(file);
                 ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempFile))) {
                for (FileHeader fh : archive.getFileHeaders()) {
                    if (fh.isDirectory()) continue;
                    String entryName = fh.getFileName();
                    if (isComicInfoName(entryName)) continue; // skip old
                    if (!isSafeEntryName(entryName)) {
                        log.warn("Skipping unsafe RAR entry name: {}", entryName);
                        continue;
                    }
                    zos.putNextEntry(new ZipEntry(entryName));
                    archive.extractFile(fh, zos);
                    zos.closeEntry();
                }
                zos.putNextEntry(new ZipEntry("ComicInfo.xml"));
                zos.write(xmlBytes);
                zos.closeEntry();
            }
            Path target = file.toPath().resolveSibling(stripExtension(file.getName()) + ".cbz");
            atomicReplace(tempFile, target);
            tempFile = null; // Successfully moved, don't delete in finally
            try {
                Files.deleteIfExists(file.toPath());
            } catch (Exception ignored) {
            }
            writeSucceeded = true;
        } catch (Exception e) {
            // Attempt to restore the original file from backup
            try {
                if (backup != null) {
                    Files.copy(backup, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    log.info("Restored original file from backup after failure: {}", file.getAbsolutePath());
                }
            } catch (Exception restoreEx) {
                log.warn("Failed to restore original file from backup: {} -> {}", backup, file.getAbsolutePath(), restoreEx);
            }
            log.warn("Failed to write metadata for {}: {}", file.getName(), e.getMessage(), e);
        } finally {
            // Clean up temporary file if it wasn't successfully moved
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception e) {
                    log.warn("Failed to delete temp file: {}", tempFile, e);
                }
            }

            // Clean up temporary directory if it was created
            if (tempDir != null) {
                deleteDirectoryRecursively(tempDir);
            }

            // Clean up backup file if write succeeded
            if (writeSucceeded && backup != null) {
                try {
                    Files.deleteIfExists(backup);
                } catch (Exception e) {
                    log.warn("Failed to delete backup file: {}", backup, e);
                }
            }
        }
    }

    // ----------------------- helpers -----------------------

    private ZipEntry findComicInfoEntry(ZipFile zipFile) {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String n = entry.getName();
            if (isComicInfoName(n)) return entry;
        }
        return null;
    }

    private FileHeader findComicInfoHeader(Archive archive) {
        for (FileHeader fh : archive.getFileHeaders()) {
            String name = fh.getFileName();
            if (name != null && isComicInfoName(name)) return fh;
        }
        return null;
    }

    private Document buildSecureDocument(InputStream is) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(is);
    }

    private Document newEmptyComicInfo() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();
        doc.appendChild(doc.createElement("ComicInfo"));
        return doc;
    }

    private void setElement(Document doc, Element root, String tag, String value) {
        removeElement(root, tag);
        if (value != null && !value.isBlank()) {
            Element el = doc.createElement(tag);
            el.setTextContent(value);
            root.appendChild(el);
        }
    }

    private void removeElement(Element root, String tag) {
        NodeList nodes = root.getElementsByTagName(tag);
        for (int i = nodes.getLength() - 1; i >= 0; i--) {
            root.removeChild(nodes.item(i));
        }
    }

    private void setDateElements(Document doc, Element root, LocalDate date) {
        if (date == null) {
            removeElement(root, "Year");
            removeElement(root, "Month");
            removeElement(root, "Day");
            return;
        }
        setElement(doc, root, "Year", Integer.toString(date.getYear()));
        setElement(doc, root, "Month", Integer.toString(date.getMonthValue()));
        setElement(doc, root, "Day", Integer.toString(date.getDayOfMonth()));
    }

    private String join(Set<String> set) {
        return (set == null || set.isEmpty()) ? null : String.join(", ", set);
    }

    private String formatFloat(Float val) {
        if (val == null) return null;
        if (val % 1 == 0) return Integer.toString(val.intValue());
        return val.toString();
    }

    private static boolean isComicInfoName(String name) {
        if (name == null) return false;
        String n = name.replace('\\', '/');
        if (n.endsWith("/")) return false;
        String lower = n.toLowerCase(Locale.ROOT);
        return lower.equals("comicinfo.xml") || lower.endsWith("/comicinfo.xml");
    }

    private static boolean isSafeEntryName(String name) {
        if (name == null || name.isBlank()) return false;
        String n = name.replace('\\', '/');
        if (n.startsWith("/")) return false; // absolute
        if (n.contains("../")) return false; // traversal
        if (n.contains("\0")) return false; // NUL
        return true;
    }

    private void repackZipReplacingComicInfo(Path sourceZip, Path targetZip, byte[] xmlBytes) throws Exception {
        try (ZipFile zipFile = new ZipFile(sourceZip.toFile());
             ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(targetZip))) {
            ZipEntry existing = findComicInfoEntry(zipFile);
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String entryName = entry.getName();
                if (existing != null && entryName.equals(existing.getName())) {
                    continue; // skip old ComicInfo.xml
                }
                if (!isSafeEntryName(entryName)) {
                    log.warn("Skipping unsafe ZIP entry name: {}", entryName);
                    continue;
                }
                zos.putNextEntry(new ZipEntry(entryName));
                try (InputStream is = zipFile.getInputStream(entry)) {
                    is.transferTo(zos);
                }
                zos.closeEntry();
            }
            String entryName = (existing != null ? existing.getName() : "ComicInfo.xml");
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(xmlBytes);
            zos.closeEntry();
        }
    }

    private static void atomicReplace(Path temp, Path target) throws Exception {
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            // Fallback if filesystem doesn't support ATOMIC_MOVE
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private boolean isRarAvailable(String rarBin) {
        try {
            String exec = isSafeExecutable(rarBin) ? rarBin : "rar";
            Process check = new ProcessBuilder(exec, "--help").redirectErrorStream(true).start();
            int exitCode = check.waitFor();
            return (exitCode == 0);
        } catch (Exception ex) {
            log.warn("RAR binary check failed: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * Returns true if the provided executable reference is a simple name or sanitized absolute/relative path.
     * No spaces or shell meta chars; passed as argv to ProcessBuilder (no shell).
     */
    private boolean isSafeExecutable(String exec) {
        if (exec == null || exec.isBlank()) return false;
        // allow word chars, dot, slash, backslash, dash and underscore (no spaces or shell metas)
        return exec.matches("^[\\w./\\\\-]+$");
    }

    private static String stripExtension(String filename) {
        int i = filename.lastIndexOf('.');
        if (i > 0) return filename.substring(0, i);
        return filename;
    }

    @Override
    public BookFileType getSupportedBookType() {
        return BookFileType.CBX;
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