package org.grimmory.service.metadata.writer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grimmory.pdfium4j.PdfDocument;
import org.grimmory.pdfium4j.XmpMetadataWriter;
import org.grimmory.pdfium4j.model.MetadataTag;
import org.grimmory.pdfium4j.model.SaveOptions;
import org.grimmory.pdfium4j.model.XmpMetadata;
import org.booklore.model.MetadataClearFlags;
import org.booklore.model.dto.settings.MetadataPersistenceSettings;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.metadata.BookLoreMetadata;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class PdfMetadataWriter implements MetadataWriter {

    private final AppSettingService appSettingService;

    @Override
    public void saveMetadataToFile(File file, BookMetadataEntity metadataEntity, String thumbnailUrl, MetadataClearFlags clear) {
        if (!shouldSaveMetadataToFile(file)) {
            return;
        }

        if (!file.exists() || !file.getName().toLowerCase().endsWith(".pdf")) {
            log.warn("Invalid PDF file: {}", file.getAbsolutePath());
            return;
        }

        Path filePath = file.toPath();
        Path parentDir = filePath.getParent();
        Path backupPath = null;
        boolean backupCreated = false;
        Path tempPath = null;

        try {
            backupPath = Files.createTempFile(parentDir, ".pdfBackup-", ".pdf");
            Files.copy(filePath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            backupCreated = true;
        } catch (IOException e) {
            log.warn("Could not create PDF temp backup for {}: {}", file.getName(), e.getMessage());
        }

        try (PdfDocument doc = PdfDocument.open(filePath)) {
            applyMetadataToDocument(doc, metadataEntity, clear);
            tempPath = Files.createTempFile(parentDir, ".pdfmeta-", ".pdf");
            doc.save(tempPath, SaveOptions.SKIP_VALIDATION);
            Files.move(tempPath, filePath, StandardCopyOption.REPLACE_EXISTING);
            tempPath = null;
            log.info("Successfully embedded metadata into PDF: {}", file.getName());
        } catch (Exception e) {
            log.warn("Failed to write metadata to PDF {}: {}", file.getName(), e.getMessage(), e);
            if (backupCreated) {
                try {
                    Files.copy(backupPath, filePath, StandardCopyOption.REPLACE_EXISTING);
                    log.info("Restored PDF {} from temp backup after failure", file.getName());
                } catch (IOException ex) {
                    log.error("Failed to restore PDF temp backup for {}: {}", file.getName(), ex.getMessage(), ex);
                }
            }
        } finally {
            if (tempPath != null) {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (IOException e) {
                    log.warn("Could not delete PDF temp file: {}", e.getMessage());
                }
            }
            if (backupCreated) {
                try {
                    Files.deleteIfExists(backupPath);
                } catch (IOException e) {
                    log.warn("Could not delete PDF temp backup for {}: {}", file.getName(), e.getMessage());
                }
            }
        }
    }

    @Override
    public BookFileType getSupportedBookType() {
        return BookFileType.PDF;
    }

    public boolean shouldSaveMetadataToFile(File pdfFile) {
        MetadataPersistenceSettings.SaveToOriginalFile settings = appSettingService.getAppSettings().getMetadataPersistenceSettings().getSaveToOriginalFile();

        MetadataPersistenceSettings.FormatSettings pdfSettings = settings.getPdf();
        if (pdfSettings == null || !pdfSettings.isEnabled()) {
            log.debug("PDF metadata writing is disabled. Skipping: {}", pdfFile.getName());
            return false;
        }

        long fileSizeInMb = pdfFile.length() / (1024 * 1024);
        if (fileSizeInMb > pdfSettings.getMaxFileSizeInMb()) {
            log.info("PDF file {} ({} MB) exceeds max size limit ({} MB). Skipping metadata write.", pdfFile.getName(), fileSizeInMb, pdfSettings.getMaxFileSizeInMb());
            return false;
        }

        return true;
    }

    // Maximum length for PDF Info Dictionary keywords (some older PDF specs limit to 255 bytes)
    private static final int MAX_INFO_KEYWORDS_LENGTH = 255;

    private void applyMetadataToDocument(PdfDocument doc, BookMetadataEntity entity, MetadataClearFlags clear) {
        MetadataCopyHelper helper = new MetadataCopyHelper(entity);

        // --- PDF Info Dictionary (legacy) via PDFium4j ---
        StringBuilder keywordsBuilder = new StringBuilder();
        helper.copyCategories(clear != null && clear.isCategories(), cats -> {
            if (cats != null && !cats.isEmpty()) {
                keywordsBuilder.append(String.join("; ", cats));
            }
        });

        helper.copyTitle(clear != null && clear.isTitle(), title -> doc.setMetadata(MetadataTag.TITLE, title != null ? title : ""));
        helper.copyPublisher(clear != null && clear.isPublisher(), pub -> doc.setMetadata(MetadataTag.PRODUCER, pub != null ? pub : ""));
        helper.copyAuthors(clear != null && clear.isAuthors(), authors -> doc.setMetadata(MetadataTag.AUTHOR, authors != null ? String.join(", ", authors) : ""));
        helper.copyPublishedDate(clear != null && clear.isPublishedDate(), date -> {
            if (date != null) {
                // PDF date format: D:YYYYMMDDHHmmSS
                String pdfDate = String.format("D:%04d%02d%02d000000", date.getYear(), date.getMonthValue(), date.getDayOfMonth());
                doc.setMetadata(MetadataTag.CREATION_DATE, pdfDate);
            } else {
                doc.setMetadata(MetadataTag.CREATION_DATE, "");
            }
        });

        String keywords = keywordsBuilder.toString();
        if (keywords.length() > MAX_INFO_KEYWORDS_LENGTH) {
            keywords = keywords.substring(0, MAX_INFO_KEYWORDS_LENGTH - 3) + "...";
            log.debug("PDF keywords truncated from {} to {} characters for legacy compatibility", 
                keywordsBuilder.length(), keywords.length());
        }
        doc.setMetadata(MetadataTag.KEYWORDS, keywords);

        // --- XMP metadata via PDFium4j XmpMetadataWriter (StringBuilder-based, no DOM) ---
        try {
            String newXmp = buildXmpPacket(helper, clear, entity);
            String existingXmp = doc.xmpMetadataString();

            if (!isXmpMetadataDifferent(existingXmp, newXmp)) {
                log.info("XMP metadata unchanged, skipping write");
                return;
            }

            doc.setXmpMetadata(newXmp);
            log.info("XMP metadata updated for PDF");
        } catch (Exception e) {
            log.warn("Failed to embed XMP metadata: {}", e.getMessage(), e);
        }
    }

    /**
     * Builds the complete XMP packet using PDFium4j's StringBuilder-based XmpMetadataWriter.
     * Eliminates all DOM and TransformerFactory overhead from the old approach.
     */
    @SuppressWarnings("unchecked")
    private String buildXmpPacket(MetadataCopyHelper helper, MetadataClearFlags clear, BookMetadataEntity metadata) {
        // Capture DC field values from helper
        String[] title = {null}, description = {null}, publisher = {null}, language = {null}, date = {null};
        List<String>[] authors = new List[]{null};
        List<String>[] subjects = new List[]{null};

        helper.copyTitle(clear != null && clear.isTitle(), t -> title[0] = t);
        helper.copyDescription(clear != null && clear.isDescription(), d -> description[0] = d);
        helper.copyPublisher(clear != null && clear.isPublisher(), p -> publisher[0] = p);
        helper.copyLanguage(clear != null && clear.isLanguage(), l -> {
            if (l != null && !l.isBlank()) language[0] = l;
        });
        helper.copyPublishedDate(clear != null && clear.isPublishedDate(), d -> {
            if (d != null) date[0] = d.toString();
        });
        helper.copyAuthors(clear != null && clear.isAuthors(), a -> {
            if (a != null && !a.isEmpty()) {
                authors[0] = a.stream()
                        .map(name -> name.replaceAll("\\s+", " ").trim())
                        .filter(name -> !name.isBlank())
                        .toList();
            }
        });
        helper.copyCategories(clear != null && clear.isCategories(), c -> {
            if (c != null && !c.isEmpty()) subjects[0] = new ArrayList<>(c);
        });

        // Build custom fields map
        Map<String, String> customFields = new LinkedHashMap<>();

        // XMP Basic (unprefixed → written as xmp: by XmpMetadataWriter)
        customFields.put("CreatorTool", "Booklore");
        String nowIso = ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT);
        customFields.put("MetadataDate", nowIso);
        customFields.put("ModifyDate", nowIso);
        if (date[0] != null) {
            customFields.put("CreateDate", date[0]);
        }

        // Booklore namespace simple fields
        addBookloreSimpleFields(customFields, helper, clear, metadata);

        XmpMetadata xmpMeta = new XmpMetadata(
                Optional.ofNullable(title[0]),
                authors[0] != null ? authors[0] : List.of(),
                Optional.ofNullable(description[0]),
                subjects[0] != null ? subjects[0] : List.of(),
                Optional.ofNullable(publisher[0]),
                Optional.ofNullable(language[0]),
                Optional.ofNullable(date[0]),
                Optional.empty(),
                List.of(),
                Optional.empty(),
                Map.of(),
                customFields
        );

        XmpMetadataWriter xmpWriter = new XmpMetadataWriter()
                .registerNamespace(BookLoreMetadata.NS_PREFIX, BookLoreMetadata.NS_URI);
        String xmpPacket = xmpWriter.write(xmpMeta);

        // Inject RDF Bag elements for tags/moods (not supported as simple custom fields)
        return injectBagElements(xmpPacket, helper, clear);
    }

    private void addBookloreSimpleFields(Map<String, String> customFields, MetadataCopyHelper helper,
                                         MetadataClearFlags clear, BookMetadataEntity metadata) {
        String prefix = BookLoreMetadata.NS_PREFIX + ":";

        if (hasValidSeries(metadata, clear)) {
            customFields.put(prefix + "seriesName", metadata.getSeriesName());
            customFields.put(prefix + "seriesNumber", formatSeriesNumber(metadata.getSeriesNumber()));

            if (metadata.getSeriesTotal() != null && metadata.getSeriesTotal() > 0) {
                helper.copySeriesTotal(clear != null && clear.isSeriesTotal(), total -> {
                    if (total != null && total > 0) {
                        customFields.put(prefix + "seriesTotal", total.toString());
                    }
                });
            }
        }

        helper.copySubtitle(clear != null && clear.isSubtitle(), subtitle -> {
            if (subtitle != null && !subtitle.isBlank()) customFields.put(prefix + "subtitle", subtitle);
        });

        helper.copyIsbn13(clear != null && clear.isIsbn13(), isbn -> {
            if (isbn != null && !isbn.isBlank()) customFields.put(prefix + "isbn13", isbn);
        });
        helper.copyIsbn10(clear != null && clear.isIsbn10(), isbn -> {
            if (isbn != null && !isbn.isBlank()) customFields.put(prefix + "isbn10", isbn);
        });

        helper.copyGoogleId(clear != null && clear.isGoogleId(), id -> {
            if (id != null && !id.isBlank()) customFields.put(prefix + "googleId", id);
        });
        helper.copyGoodreadsId(clear != null && clear.isGoodreadsId(), id -> {
            String normalized = normalizeGoodreadsId(id);
            if (normalized != null && !normalized.isBlank()) customFields.put(prefix + "goodreadsId", normalized);
        });
        helper.copyHardcoverId(clear != null && clear.isHardcoverId(), id -> {
            if (id != null && !id.isBlank()) customFields.put(prefix + "hardcoverId", id);
        });
        helper.copyHardcoverBookId(clear != null && clear.isHardcoverBookId(), id -> {
            if (id != null && !id.isBlank()) customFields.put(prefix + "hardcoverBookId", id);
        });
        helper.copyAsin(clear != null && clear.isAsin(), id -> {
            if (id != null && !id.isBlank()) customFields.put(prefix + "asin", id);
        });
        helper.copyComicvineId(clear != null && clear.isComicvineId(), id -> {
            if (id != null && !id.isBlank()) customFields.put(prefix + "comicvineId", id);
        });
        helper.copyLubimyczytacId(clear != null && clear.isLubimyczytacId(), id -> {
            if (id != null && !id.isBlank()) customFields.put(prefix + "lubimyczytacId", id);
        });
        helper.copyRanobedbId(clear != null && clear.isRanobedbId(), id -> {
            if (id != null && !id.isBlank()) customFields.put(prefix + "ranobedbId", id);
        });

        helper.copyRating(false, rating -> addBookloreRating(customFields, prefix, "rating", rating));
        helper.copyHardcoverRating(clear != null && clear.isHardcoverRating(),
                rating -> addBookloreRating(customFields, prefix, "hardcoverRating", rating));
        helper.copyGoodreadsRating(clear != null && clear.isGoodreadsRating(),
                rating -> addBookloreRating(customFields, prefix, "goodreadsRating", rating));
        helper.copyAmazonRating(clear != null && clear.isAmazonRating(),
                rating -> addBookloreRating(customFields, prefix, "amazonRating", rating));
        helper.copyLubimyczytacRating(clear != null && clear.isLubimyczytacRating(),
                rating -> addBookloreRating(customFields, prefix, "lubimyczytacRating", rating));
        helper.copyRanobedbRating(clear != null && clear.isRanobedbRating(),
                rating -> addBookloreRating(customFields, prefix, "ranobedbRating", rating));

        helper.copyPageCount(false, pageCount -> {
            if (pageCount != null && pageCount > 0) {
                customFields.put(prefix + "pageCount", pageCount.toString());
            }
        });
    }

    private void addBookloreRating(Map<String, String> customFields, String prefix, String name, Double rating) {
        if (rating != null && rating > 0) {
            customFields.put(prefix + name, String.format(Locale.US, "%.1f", rating));
        }
    }

    /**
     * Injects RDF Bag elements for tags/moods into the XMP packet string.
     * XmpMetadataWriter doesn't support RDF Bags in custom namespaces, so we
     * insert them as a separate rdf:Description block before &lt;/rdf:RDF&gt;.
     */
    private String injectBagElements(String xmpPacket, MetadataCopyHelper helper, MetadataClearFlags clear) {
        StringBuilder bags = new StringBuilder();

        helper.copyTags(clear != null && clear.isTags(), tags -> {
            if (tags != null && !tags.isEmpty()) appendBagXml(bags, "tags", tags);
        });
        helper.copyMoods(clear != null && clear.isMoods(), moods -> {
            if (moods != null && !moods.isEmpty()) appendBagXml(bags, "moods", moods);
        });

        if (bags.isEmpty()) return xmpPacket;

        int idx = xmpPacket.lastIndexOf("</rdf:RDF>");
        if (idx < 0) return xmpPacket;
        return xmpPacket.substring(0, idx) + bags + xmpPacket.substring(idx);
    }

    private void appendBagXml(StringBuilder sb, String localName, Set<String> values) {
        sb.append("<rdf:Description rdf:about=\"\"\n");
        sb.append("    xmlns:").append(BookLoreMetadata.NS_PREFIX).append("=\"")
                .append(BookLoreMetadata.NS_URI).append("\">\n");
        sb.append("  <").append(BookLoreMetadata.NS_PREFIX).append(':').append(localName).append(">\n");
        sb.append("    <rdf:Bag>\n");
        for (String v : values.stream().sorted().toList()) {
            if (v != null && !v.isBlank()) {
                sb.append("      <rdf:li>").append(escapeXml(v)).append("</rdf:li>\n");
            }
        }
        sb.append("    </rdf:Bag>\n");
        sb.append("  </").append(BookLoreMetadata.NS_PREFIX).append(':').append(localName).append(">\n");
        sb.append("</rdf:Description>\n");
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static final java.util.regex.Pattern TIMESTAMP_PATTERN = java.util.regex.Pattern.compile(
            "<xmp:(MetadataDate|ModifyDate)>[^<]*</xmp:(MetadataDate|ModifyDate)>");

    private boolean isXmpMetadataDifferent(String existingXmp, String newXmp) {
        if (existingXmp == null || existingXmp.isBlank() || newXmp == null) return true;
        // Strip regenerated timestamps before comparing so the check is deterministic
        String normalizedExisting = TIMESTAMP_PATTERN.matcher(existingXmp).replaceAll("");
        String normalizedNew = TIMESTAMP_PATTERN.matcher(newXmp).replaceAll("");
        return !normalizedExisting.equals(normalizedNew);
    }

    /**
     * Validates that both series name AND series number are present and valid.
     * A series name without a number (or vice versa) is broken/incomplete data and should not be written.
     */
    private boolean hasValidSeries(BookMetadataEntity metadata, MetadataClearFlags clear) {
        // If clearing series, don't write it
        if (clear != null && (clear.isSeriesName() || clear.isSeriesNumber())) {
            return false;
        }
        
        // Check if either field is locked - if so, respect the lock
        if (Boolean.TRUE.equals(metadata.getSeriesNameLocked()) || Boolean.TRUE.equals(metadata.getSeriesNumberLocked())) {
            return false;
        }
        
        // Both name AND number must be valid
        return metadata.getSeriesName() != null 
                && !metadata.getSeriesName().isBlank()
                && metadata.getSeriesNumber() != null 
                && metadata.getSeriesNumber() > 0;
    }

    /**
     * Formats series number nicely: "22" for whole numbers, "1.5" for decimals.
     * Avoids unnecessary ".00" suffix.
     */
    private String formatSeriesNumber(Float number) {
        if (number == null) return "0";
        
        // If it's a whole number, don't show decimal places
        if (number % 1 == 0) {
            return String.valueOf(number.intValue());
        }
        
        // For decimals, show up to 2 decimal places but trim trailing zeros
        String formatted = String.format(Locale.US, "%.2f", number);
        // Remove trailing zeros after decimal point: "1.50" -> "1.5"
        formatted = formatted.replaceAll("0+$", "").replaceAll("\\.$", "");
        return formatted;
    }

    /**
     * Normalizes Goodreads ID to extract just the numeric part.
     * Goodreads URLs/IDs can be in formats like:
     * - "52555538" (just ID)
     * - "52555538-dead-simple-python" (ID with slug)
     * The slug can change but the numeric ID is stable.
     */
    private String normalizeGoodreadsId(String goodreadsId) {
        if (goodreadsId == null || goodreadsId.isBlank()) {
            return null;
        }
        
        // Extract numeric ID from slug format "12345678-book-title" or "12345678.Book_Title"
        int sep = goodreadsId.indexOf('-');
        if (sep < 0) sep = goodreadsId.indexOf('.');
        if (sep > 0) {
            String numericPart = goodreadsId.substring(0, sep);
            if (numericPart.matches("\\d+")) {
                return numericPart;
            }
        }
        
        return goodreadsId;
    }

}
