package org.booklore.service.metadata.writer;

import jakarta.xml.bind.*;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;

import org.booklore.service.ArchiveService;
import org.xml.sax.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.MetadataClearFlags;
import org.booklore.model.dto.settings.MetadataPersistenceSettings;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.ComicCreatorRole;
import org.booklore.service.appsettings.AppSettingService;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class CbxMetadataWriter implements MetadataWriter {
    private static final String DEFAULT_COMICINFO_XML = "ComicInfo.xml";

    // Cache JAXBContext for performance
    private static final JAXBContext JAXB_CONTEXT;

    static {
        try {
            JAXB_CONTEXT = JAXBContext.newInstance(ComicInfo.class);
        } catch (jakarta.xml.bind.JAXBException e) {
            throw new RuntimeException("Failed to initialize JAXB Context", e);
        }
    }

    private final AppSettingService appSettingService;
    private final ArchiveService archiveService;

    @Override
    public void saveMetadataToFile(File file, BookMetadataEntity metadata, String thumbnailUrl, MetadataClearFlags clearFlags) {
        if (!shouldSaveMetadataToFile(file)) {
            return;
        }

        Path backupPath = createBackupFile(file);
        Path extractDir = null;
        Path tempArchive = null;
        boolean writeSucceeded = false;

        try {
            ComicInfo comicInfo = loadOrCreateComicInfo(file.toPath());
            applyMetadataChanges(comicInfo, metadata, clearFlags);
            byte[] xmlContent = convertToBytes(comicInfo);

            log.debug("CbxMetadataWriter: Writing ComicInfo.xml to CBZ file: {}, XML size: {} bytes", file.getName(), xmlContent.length);
            tempArchive = updateArchive(file, xmlContent);
            writeSucceeded = true;
            log.info("CbxMetadataWriter: Successfully wrote metadata to CBZ file: {}", file.getName());
        } catch (Exception e) {
            restoreOriginalFile(backupPath, file);
            log.warn("Failed to write metadata for {}: {}", file.getName(), e.getMessage(), e);
        } finally {
            cleanupTempFiles(tempArchive, extractDir, backupPath, writeSucceeded);
        }
    }

    public boolean shouldSaveMetadataToFile(File file) {
        MetadataPersistenceSettings.SaveToOriginalFile settings = appSettingService.getAppSettings().getMetadataPersistenceSettings().getSaveToOriginalFile();

        MetadataPersistenceSettings.FormatSettings cbxSettings = settings.getCbx();
        if (cbxSettings == null || !cbxSettings.isEnabled()) {
            log.debug("CBX metadata writing is disabled. Skipping: {}", file.getName());
            return false;
        }

        long fileSizeInMb = file.length() / (1024 * 1024);
        if (fileSizeInMb > cbxSettings.getMaxFileSizeInMb()) {
            log.info("CBX file {} ({} MB) exceeds max size limit ({} MB). Skipping metadata write.", file.getName(), fileSizeInMb, cbxSettings.getMaxFileSizeInMb());
            return false;
        }

        return true;
    }

    private Path createBackupFile(File file) {
        try {
            Path backupPath = Files.createTempFile(file.getParentFile().toPath(), "cbx_backup_", ".bak");
            Files.copy(file.toPath(), backupPath, StandardCopyOption.REPLACE_EXISTING);
            return backupPath;
        } catch (Exception ex) {
            log.warn("Unable to create backup for {}: {}", file.getAbsolutePath(), ex.getMessage(), ex);
            return null;
        }
    }

    private ComicInfo loadOrCreateComicInfo(Path path) {
        String comicInfoEntry = findComicInfoEntryName(path);

        if (comicInfoEntry == null) {
            // If we can't find a comicInfo entry, bail out.
            return new ComicInfo();
        }

        byte[] comicInfoXML;
        try {
            comicInfoXML = archiveService.getEntryBytes(path, comicInfoEntry);
        } catch (Exception e) {
            log.warn("Could not read archive {}: {}", path, e.getMessage());
            return new ComicInfo();
        }

        try (
          ByteArrayInputStream bais = new ByteArrayInputStream(comicInfoXML)
        ) {
            return parseComicInfo(bais);
        } catch (Exception e) {
            log.warn("Could not parse archive ComicInfo {}: {}", path, e.getMessage());
            return new ComicInfo();
        }
    }

    private String findComicInfoEntryName(Path path) {
        try {
            return archiveService.streamEntryNames(path)
                    .filter(CbxMetadataWriter::isComicInfoXml)
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            log.warn("Failed to read archive {}: {}", path.getFileName(), e.getMessage());
        }

        return null;
    }

    private void applyMetadataChanges(ComicInfo info, BookMetadataEntity metadata, MetadataClearFlags clearFlags) {
        MetadataCopyHelper helper = new MetadataCopyHelper(metadata);

        helper.copyTitle(clearFlags != null && clearFlags.isTitle(), info::setTitle);
        
        // Summary: Remove HTML tags safely using Jsoup (handles complex HTML like attributes with '>')
        helper.copyDescription(clearFlags != null && clearFlags.isDescription(), val -> {
            if (val != null) {
                // Jsoup.clean with Safelist.none() removes all HTML tags safely, 
                // handling edge cases like '<a href="...>">' that regex fails on
                String clean = Jsoup.clean(val, Safelist.none()).trim();
                log.debug("CbxMetadataWriter: Setting Summary to: {} (original length: {}, cleaned length: {})", 
                    clean.length() > 50 ? clean.substring(0, 50) + "..." : clean, 
                    val.length(), 
                    clean.length());
                info.setSummary(clean);
            } else {
                log.debug("CbxMetadataWriter: Clearing Summary (null description)");
                info.setSummary(null);
            }
        });
        
        helper.copyPublisher(clearFlags != null && clearFlags.isPublisher(), info::setPublisher);
        helper.copySeriesName(clearFlags != null && clearFlags.isSeriesName(), info::setSeries);
        helper.copySeriesNumber(clearFlags != null && clearFlags.isSeriesNumber(), val -> info.setNumber(formatFloatValue(val)));
        helper.copySeriesTotal(clearFlags != null && clearFlags.isSeriesTotal(), info::setCount);
        
        helper.copyPublishedDate(clearFlags != null && clearFlags.isPublishedDate(), date -> {
             if (date != null) {
                 info.setYear(date.getYear());
                 info.setMonth(date.getMonthValue());
                 info.setDay(date.getDayOfMonth());
             } else {
                 info.setYear(null);
                 info.setMonth(null);
                 info.setDay(null);
             }
        });
        
        helper.copyPageCount(clearFlags != null && clearFlags.isPageCount(), info::setPageCount);
        helper.copyLanguage(clearFlags != null && clearFlags.isLanguage(), info::setLanguageISO);
        
        helper.copyAuthors(clearFlags != null && clearFlags.isAuthors(), set -> {
            info.setWriter(joinStrings(set));
            info.setPenciller(null);
            info.setInker(null);
            info.setColorist(null);
            info.setLetterer(null);
            info.setCoverArtist(null);
        });

        // Genre - categories
        helper.copyCategories(clearFlags != null && clearFlags.isCategories(), set -> {
            info.setGenre(joinStrings(set));
        });
        
        // Tags - separate from Genre per Anansi v2.1
        if (metadata.getTags() != null && !metadata.getTags().isEmpty()) {
            info.setTags(joinStrings(metadata.getTags().stream().map(TagEntity::getName).collect(Collectors.toSet())));
        }

        // CommunityRating - normalized to 0-5 scale
        helper.copyRating(false, rating -> {
            if (rating != null) {
                double normalized = Math.clamp(rating / 2.0, 0.0, 5.0);
                info.setCommunityRating(String.format(Locale.US, "%.1f", normalized));
            } else {
                info.setCommunityRating(null);
            }
        });

        // Web field - pick one primary
        String primaryUrl = null;
        if (metadata.getHardcoverBookId() != null && !metadata.getHardcoverBookId().isBlank()) {
            primaryUrl = "https://hardcover.app/books/" + metadata.getHardcoverBookId();
        } else if (metadata.getComicvineId() != null && !metadata.getComicvineId().isBlank()) {
            String cvId = metadata.getComicvineId();
            if (cvId.startsWith("4050-")) {
                primaryUrl = "https://comicvine.gamespot.com/volume/" + cvId + "/";
            } else if (cvId.startsWith("4000-")) {
                primaryUrl = "https://comicvine.gamespot.com/issue/" + cvId + "/";
            } else if (cvId.matches("\\d+-\\d+")) {
                // Already prefixed with some other Comicvine namespace; preserve as-is.
                primaryUrl = "https://comicvine.gamespot.com/issue/" + cvId + "/";
            } else {
                // Legacy bare-numeric ID — assume issue.
                primaryUrl = "https://comicvine.gamespot.com/issue/4000-" + cvId + "/";
            }
        } else if (metadata.getGoodreadsId() != null && !metadata.getGoodreadsId().isBlank()) {
            primaryUrl = "https://www.goodreads.com/book/show/" + metadata.getGoodreadsId();
        } else if (metadata.getAsin() != null && !metadata.getAsin().isBlank()) {
            primaryUrl = "https://www.amazon.com/dp/" + metadata.getAsin();
        }
        info.setWeb(primaryUrl);

        // Notes - Custom Metadata
        StringBuilder notesBuilder = new StringBuilder();
        String existingNotes = info.getNotes();
        
        // Preserve existing notes that don't start with [BookLore
        if (existingNotes != null && !existingNotes.isBlank()) {
            String preservedRules = existingNotes.lines()
                    .map(String::trim)
                    .filter(line -> !line.startsWith("[BookLore:") && !line.startsWith("[BookLore]"))
                    .collect(Collectors.joining("\n"));
             if (!preservedRules.isEmpty()) {
                 notesBuilder.append(preservedRules);
             }
        }

        if (metadata.getMoods() != null) {
            appendBookLoreTag(notesBuilder, "Moods", joinStrings(metadata.getMoods().stream().map(MoodEntity::getName).collect(Collectors.toSet())));
        }
        if (metadata.getTags() != null) {
            appendBookLoreTag(notesBuilder, "Tags", joinStrings(metadata.getTags().stream().map(TagEntity::getName).collect(Collectors.toSet())));
        }
        appendBookLoreTag(notesBuilder, "Subtitle", metadata.getSubtitle());
        
        if (metadata.getIsbn13() != null && !metadata.getIsbn13().isBlank()) {
            info.setGtin(metadata.getIsbn13());
        }
        appendBookLoreTag(notesBuilder, "ISBN10", metadata.getIsbn10());
        
        appendBookLoreTag(notesBuilder, "AmazonRating", metadata.getAmazonRating());
        appendBookLoreTag(notesBuilder, "GoodreadsRating", metadata.getGoodreadsRating());
        appendBookLoreTag(notesBuilder, "HardcoverRating", metadata.getHardcoverRating());
        appendBookLoreTag(notesBuilder, "LubimyczytacRating", metadata.getLubimyczytacRating());
        appendBookLoreTag(notesBuilder, "RanobedbRating", metadata.getRanobedbRating());

        appendBookLoreTag(notesBuilder, "HardcoverBookId", metadata.getHardcoverBookId());
        appendBookLoreTag(notesBuilder, "HardcoverId", metadata.getHardcoverId());
        appendBookLoreTag(notesBuilder, "LubimyczytacId", metadata.getLubimyczytacId());
        appendBookLoreTag(notesBuilder, "RanobedbId", metadata.getRanobedbId());
        appendBookLoreTag(notesBuilder, "GoogleId", metadata.getGoogleId());
        appendBookLoreTag(notesBuilder, "GoodreadsId", metadata.getGoodreadsId());
        appendBookLoreTag(notesBuilder, "ASIN", metadata.getAsin());
        appendBookLoreTag(notesBuilder, "ComicvineId", metadata.getComicvineId());
        
        // Comic-specific metadata from ComicMetadataEntity
        ComicMetadataEntity comic = metadata.getComicMetadata();
        if (comic != null) {
            // Volume
            if (comic.getVolumeNumber() != null) {
                info.setVolume(comic.getVolumeNumber());
            }
            
            // Alternate Series
            if (comic.getAlternateSeries() != null && !comic.getAlternateSeries().isBlank()) {
                info.setAlternateSeries(comic.getAlternateSeries());
            }
            if (comic.getAlternateIssue() != null && !comic.getAlternateIssue().isBlank()) {
                info.setAlternateNumber(comic.getAlternateIssue());
            }
            
            // Story Arc
            if (comic.getStoryArc() != null && !comic.getStoryArc().isBlank()) {
                info.setStoryArc(comic.getStoryArc());
            }
            
            // Format
            if (comic.getFormat() != null && !comic.getFormat().isBlank()) {
                info.setFormat(comic.getFormat());
            }
            
            // Imprint
            if (comic.getImprint() != null && !comic.getImprint().isBlank()) {
                info.setImprint(comic.getImprint());
            }
            
            // BlackAndWhite (Yes/No)
            if (comic.getBlackAndWhite() != null) {
                info.setBlackAndWhite(comic.getBlackAndWhite() ? "Yes" : "No");
            }
            
            // Manga / Reading Direction
            if (comic.getManga() != null && comic.getManga()) {
                if (comic.getReadingDirection() != null && "RTL".equalsIgnoreCase(comic.getReadingDirection())) {
                    info.setManga("YesAndRightToLeft");
                } else {
                    info.setManga("Yes");
                }
            } else if (comic.getManga() != null) {
                info.setManga("No");
            }
            
            // Characters (comma-separated)
            if (comic.getCharacters() != null && !comic.getCharacters().isEmpty()) {
                String chars = comic.getCharacters().stream()
                        .map(ComicCharacterEntity::getName)
                        .collect(Collectors.joining(", "));
                info.setCharacters(chars);
            }
            
            // Teams (comma-separated)
            if (comic.getTeams() != null && !comic.getTeams().isEmpty()) {
                String teams = comic.getTeams().stream()
                        .map(ComicTeamEntity::getName)
                        .collect(Collectors.joining(", "));
                info.setTeams(teams);
            }
            
            // Locations (comma-separated)
            if (comic.getLocations() != null && !comic.getLocations().isEmpty()) {
                String locs = comic.getLocations().stream()
                        .map(ComicLocationEntity::getName)
                        .collect(Collectors.joining(", "));
                info.setLocations(locs);
            }
            
            // Creators by role (overrides the author-based writer if present)
            if (comic.getCreatorMappings() != null && !comic.getCreatorMappings().isEmpty()) {
                String pencillers = getCreatorsByRole(comic, ComicCreatorRole.PENCILLER);
                String inkers = getCreatorsByRole(comic, ComicCreatorRole.INKER);
                String colorists = getCreatorsByRole(comic, ComicCreatorRole.COLORIST);
                String letterers = getCreatorsByRole(comic, ComicCreatorRole.LETTERER);
                String coverArtists = getCreatorsByRole(comic, ComicCreatorRole.COVER_ARTIST);
                String editors = getCreatorsByRole(comic, ComicCreatorRole.EDITOR);
                
                if (!pencillers.isEmpty()) info.setPenciller(pencillers);
                if (!inkers.isEmpty()) info.setInker(inkers);
                if (!colorists.isEmpty()) info.setColorist(colorists);
                if (!letterers.isEmpty()) info.setLetterer(letterers);
                if (!coverArtists.isEmpty()) info.setCoverArtist(coverArtists);
                if (!editors.isEmpty()) info.setEditor(editors);
            }
            
            // Store comic-specific metadata in notes as well
            appendBookLoreTag(notesBuilder, "VolumeName", comic.getVolumeName());
            appendBookLoreTag(notesBuilder, "StoryArcNumber", comic.getStoryArcNumber());
            appendBookLoreTag(notesBuilder, "IssueNumber", comic.getIssueNumber());
        }
        
        // Age Rating (from BookMetadataEntity - mapped to ComicInfo AgeRating format)
        if (metadata.getAgeRating() != null) {
            info.setAgeRating(mapAgeRatingToComicInfo(metadata.getAgeRating()));
        }
        
        info.setNotes(!notesBuilder.isEmpty() ? notesBuilder.toString() : null);
    }
    
    private String getCreatorsByRole(ComicMetadataEntity comic, ComicCreatorRole role) {
        if (comic.getCreatorMappings() == null) return "";
        return comic.getCreatorMappings().stream()
                .filter(m -> m.getRole() == role)
                .map(m -> m.getCreator().getName())
                .collect(Collectors.joining(", "));
    }
    
    private String mapAgeRatingToComicInfo(Integer ageRating) {
        // Map numeric age rating to ComicInfo AgeRating string values
        if (ageRating == null) return null;
        if (ageRating >= 18) return "Adults Only 18+";
        if (ageRating >= 17) return "Mature 17+";
        if (ageRating >= 15) return "MA15+";
        if (ageRating >= 13) return "Teen";
        if (ageRating >= 10) return "Everyone 10+";
        if (ageRating >= 6) return "Everyone";
        return "Early Childhood";
    }

    private ComicInfo parseComicInfo(InputStream xmlStream) throws SAXException, ParserConfigurationException, JAXBException {
        // Use a SAXSource with an explicitly secured XMLReader to prevent XXE injection.
        // This is more robust than System.setProperty() because it is per-instance and
        // cannot be inadvertently disabled by other code running in the same JVM.
        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setNamespaceAware(true);
        spf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        spf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        spf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        spf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        XMLReader xmlReader = spf.newSAXParser().getXMLReader();
        SAXSource saxSource = new SAXSource(xmlReader, new InputSource(xmlStream));

        Unmarshaller unmarshaller = JAXB_CONTEXT.createUnmarshaller();
        unmarshaller.setEventHandler(event -> {
            if (event.getSeverity() == ValidationEvent.WARNING ||
                event.getSeverity() == ValidationEvent.ERROR) {
                log.warn("JAXB Parsing Issue: {} [Line: {}, Col: {}]",
                    event.getMessage(),
                    event.getLocator().getLineNumber(),
                    event.getLocator().getColumnNumber());
            }
            return true; // Continue processing
        });
        ComicInfo result = (ComicInfo) unmarshaller.unmarshal(saxSource);
        log.debug("CbxMetadataWriter: Parsed ComicInfo - Title: {}, Summary length: {}",
            result.getTitle(),
            result.getSummary() != null ? result.getSummary().length() : 0);
        return result;
    }

    private byte[] convertToBytes(ComicInfo comicInfo) throws Exception {
        Marshaller marshaller = JAXB_CONTEXT.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, false);
        // Ensure 2-space indentation if possible
        try {
            marshaller.setProperty("com.sun.xml.bind.indentString", "  ");
        } catch (Exception _) {
            log.debug("Custom indentation property not supported via 'com.sun.xml.bind.indentString'");
        }
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        marshaller.marshal(comicInfo, outputStream);
        return outputStream.toByteArray();
    }

    private Path updateArchive(File originalFile, byte[] xmlContent) throws Exception {
        // Create temp file in same directory as original for true atomic move on same filesystem
        Path tempArchive = Files.createTempFile(originalFile.toPath().getParent(), ".cbx_edit_", ".cbz");
        rebuildArchiveWithNewXml(originalFile.toPath(), tempArchive, xmlContent);

        Path originalPath = originalFile.toPath().toAbsolutePath();
        Path targetPath = replaceFileExtension(originalPath, "cbz");

        replaceFileAtomic(tempArchive, targetPath);

        if (!originalPath.equals(targetPath)) {
            log.debug("Deleting CBX archive after conversion to CBZ: {}", originalPath);

            try {
                Files.deleteIfExists(originalPath);
            } catch (Exception e) {
                log.warn("Unable to delete original CBX archive {}: {}", originalPath, e.getMessage());
            }
        }

        return null;
    }

    private void restoreOriginalFile(Path backupPath, File targetFile) {
        try {
            if (backupPath != null) {
                Files.copy(backupPath, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                log.info("Restored original file from backup after failure: {}", targetFile.getAbsolutePath());
            }
        } catch (Exception restoreException) {
            log.warn("Failed to restore original file from backup: {} -> {}", backupPath, targetFile.getAbsolutePath(), restoreException);
        }
    }

    private void cleanupTempFiles(Path tempArchive, Path extractDir, Path backupPath, boolean writeSucceeded) {
        if (tempArchive != null) {
            try {
                Files.deleteIfExists(tempArchive);
            } catch (Exception e) {
                log.warn("Failed to delete temp file: {}", tempArchive, e);
            }
        }

        if (extractDir != null) {
            deleteDirectoryRecursively(extractDir);
        }

        if (writeSucceeded && backupPath != null) {
            try {
                Files.deleteIfExists(backupPath);
            } catch (Exception e) {
                log.warn("Failed to delete backup file: {}", backupPath, e);
            }
        }
    }

    private String joinStrings(Set<String> values) {
        return (values == null || values.isEmpty()) ? null : String.join(", ", values);
    }

    private String formatFloatValue(Float value) {
        if (value == null) return null;
        if (value % 1 == 0) return Integer.toString(value.intValue());
        return value.toString();
    }

    private static boolean isComicInfoXml(String entryName) {
        if (entryName == null) return false;
        String normalized = entryName
                .replace('\\', '/')
                .toLowerCase(Locale.ROOT);

        if (normalized.endsWith("/")) {
            // Directories cannot be a comic info XML
            return false;
        }

        String comicInfoFilename = CbxMetadataWriter.DEFAULT_COMICINFO_XML.toLowerCase(Locale.ROOT);

        return comicInfoFilename.equals(normalized) || normalized.endsWith("/" + comicInfoFilename);
    }

    /**
     * Validates a ZIP entry name is safe against zip-slip attacks.
     * Uses {@link Path#normalize()} to collapse traversal segments and rejects entries
     * that are absolute or escape the archive root.
     */
    static boolean isPathSafe(String entryName) {
        if (entryName == null || entryName.isBlank()) return false;
        if (entryName.contains("\0")) return false;
        try {
            String sanitized = entryName.replace('\\', '/');
            // Reject any entry containing ".." as a path segment even if normalization
            // would collapse it to something safe, legitimate ZIP entries never use "..".
            for (String segment : sanitized.split("/", -1)) {
                if ("..".equals(segment)) return false;
            }
            Path parsed = Path.of(sanitized).normalize();
            return !parsed.isAbsolute()
                    && !parsed.toString().isEmpty()
                    && !parsed.startsWith("..");
        } catch (InvalidPathException e) {
            return false;
        }
    }

    private void rebuildArchiveWithNewXml(Path sourceArchive, Path targetZip, byte[] xmlContent) throws Exception {
        String comicInfoEntryName = findComicInfoEntryName(sourceArchive);

        try (
                ZipOutputStream zipOutput = new ZipOutputStream(Files.newOutputStream(targetZip))
        ) {
            for (String entryName : archiveService.getEntryNames(sourceArchive)) {
                if (isComicInfoXml(entryName)) {
                    // Skip copying over any existing comic info entry
                    continue;
                }

                if (!isPathSafe(entryName)) {
                    log.warn("Skipping unsafe CBZ entry name: {}", entryName);
                    continue;
                }

                zipOutput.putNextEntry(new ZipEntry(entryName));

                archiveService.transferEntryTo(sourceArchive, entryName, zipOutput);

                zipOutput.closeEntry();
            }

            String xmlEntryName = (comicInfoEntryName != null ? comicInfoEntryName : CbxMetadataWriter.DEFAULT_COMICINFO_XML);
            zipOutput.putNextEntry(new ZipEntry(xmlEntryName));
            zipOutput.write(xmlContent);
            zipOutput.closeEntry();
        }
    }

    private static void replaceFileAtomic(Path source, Path target) throws Exception {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path replaceFileExtension(Path path, String extension) {
        String filename = path.getFileName().toString();

        if (filename.toLowerCase(Locale.ROOT).endsWith("." + extension.toLowerCase())) {
            // If the file extension is already there, do nothing.
            return path;
        }

        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0) {
            filename = filename.substring(0, lastDot);
        }
        return path.resolveSibling(filename + "." + extension);
    }

    @Override
    public BookFileType getSupportedBookType() {
        return BookFileType.CBX;
    }

    private void deleteDirectoryRecursively(Path directory) {
        try (var pathStream = Files.walk(directory)) {
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
            log.warn("Failed to clean up temporary directory: {}", directory, e);
        }
    }

    private void appendBookLoreTag(StringBuilder sb, String tag, String value) {
        if (value != null && !value.isBlank()) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("[BookLore:").append(tag).append("] ").append(value);
        }
    }

    private void appendBookLoreTag(StringBuilder sb, String tag, Number value) {
        if (value != null) {
            if (sb.length() > 0) sb.append("\n");
            String formatted = (value instanceof Double || value instanceof Float) 
                    ? String.format(Locale.US, "%.2f", value.doubleValue())
                    : value.toString();
            sb.append("[BookLore:").append(tag).append("] ").append(formatted);
        }
    }
}
