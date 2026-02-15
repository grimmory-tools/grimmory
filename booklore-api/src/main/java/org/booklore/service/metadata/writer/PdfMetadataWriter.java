package org.booklore.service.metadata.writer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.xmpbox.XMPMetadata;
import org.apache.xmpbox.schema.DublinCoreSchema;
import org.apache.xmpbox.xml.XmpSerializer;
import org.booklore.model.MetadataClearFlags;
import org.booklore.model.dto.settings.MetadataPersistenceSettings;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.util.SecureXmlUtils;
import org.booklore.service.metadata.BookLoreMetadata;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
        Path backupPath = null;
        boolean backupCreated = false;
        File tempFile = null;

        try {
            String prefix = "pdfBackup-" + UUID.randomUUID() + "-";
            backupPath = Files.createTempFile(prefix, ".pdf");
            Files.copy(filePath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            backupCreated = true;
        } catch (IOException e) {
            log.warn("Could not create PDF temp backup for {}: {}", file.getName(), e.getMessage());
        }

        try (PDDocument pdf = Loader.loadPDF(file, IOUtils.createTempFileOnlyStreamCache())) {
            pdf.setAllSecurityToBeRemoved(true);
            applyMetadataToDocument(pdf, metadataEntity, clear);
            tempFile = File.createTempFile("pdfmeta-", ".pdf");
            // PDFBox 3.x saves in compressed mode by default
            pdf.save(tempFile);
            Files.move(tempFile.toPath(), filePath, StandardCopyOption.REPLACE_EXISTING);
            tempFile = null; // Prevent deletion in finally block after successful move
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
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
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

    private void applyMetadataToDocument(PDDocument pdf, BookMetadataEntity entity, MetadataClearFlags clear) {
        PDDocumentInformation info = pdf.getDocumentInformation();
        MetadataCopyHelper helper = new MetadataCopyHelper(entity);

        // Build categories-only keywords for PDF legacy compatibility (Info Dictionary)
        // Moods and tags are stored separately in XMP booklore namespace, so they should NOT be in Info Dict keywords
        StringBuilder keywordsBuilder = new StringBuilder();
        helper.copyCategories(clear != null && clear.isCategories(), cats -> {
            if (cats != null && !cats.isEmpty()) {
                keywordsBuilder.append(String.join("; ", cats));
            }
        });

        helper.copyTitle(clear != null && clear.isTitle(), title -> info.setTitle(title != null ? title : ""));
        helper.copyPublisher(clear != null && clear.isPublisher(), pub -> info.setProducer(pub != null ? pub : ""));
        helper.copyAuthors(clear != null && clear.isAuthors(), authors -> info.setAuthor(authors != null ? String.join(", ", authors) : ""));
        helper.copyPublishedDate(clear != null && clear.isPublishedDate(), date -> {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis((date != null ? date : ZonedDateTime.now().toLocalDate())
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
            info.setCreationDate(cal);
        });
        
        // Truncate keywords for legacy PDF Info Dictionary (255 byte limit in older specs)
        String keywords = keywordsBuilder.toString();
        if (keywords.length() > MAX_INFO_KEYWORDS_LENGTH) {
            keywords = keywords.substring(0, MAX_INFO_KEYWORDS_LENGTH - 3) + "...";
            log.debug("PDF keywords truncated from {} to {} characters for legacy compatibility", 
                keywordsBuilder.length(), keywords.length());
        }
        info.setKeywords(keywords);

        try {
            XMPMetadata xmp = XMPMetadata.createXMPMetadata();
            DublinCoreSchema dc = xmp.createAndAddDublinCoreSchema();

            helper.copyTitle(clear != null && clear.isTitle(), title -> dc.setTitle(title != null ? title : ""));
            helper.copyDescription(clear != null && clear.isDescription(), desc -> dc.setDescription(desc != null ? desc : ""));
            helper.copyPublisher(clear != null && clear.isPublisher(), pub -> dc.addPublisher(pub != null ? pub : ""));
            
            // Write language as provided by user
            helper.copyLanguage(clear != null && clear.isLanguage(), lang -> {
                if (lang != null && !lang.isBlank()) {
                    dc.addLanguage(lang);
                }
            });
            
            // Use date-only format for dc:date (YYYY-MM-DD)
            helper.copyPublishedDate(clear != null && clear.isPublishedDate(), date -> {
                if (date != null) {
                    // XMPBox requires Calendar, but we can create one with just the date (no time)
                    Calendar cal = Calendar.getInstance();
                    cal.clear(); // Clear time fields
                    cal.set(date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth());
                    dc.addDate(cal);
                }
            });

            // Clean author names (normalize whitespace)
            helper.copyAuthors(clear != null && clear.isAuthors(), authors -> {
                if (authors != null && !authors.isEmpty()) {
                    authors.stream()
                        .map(name -> name.replaceAll("\\s+", " ").trim())
                        .filter(name -> !name.isBlank())
                        .forEach(dc::addCreator);
                }
            });

            // Add categories as dc:subject
            helper.copyCategories(clear != null && clear.isCategories(), cats -> {
                if (cats != null && !cats.isEmpty()) {
                    cats.forEach(dc::addSubject);
                }
            });
            
            // Note: BookLore custom fields (subtitle, ratings, moods, tags as separate field) 
            // are added via raw XML manipulation in addCustomIdentifiersToXmp to avoid XMPBox namespace issues
            // Moods and tags are stored separately in booklore namespace to avoid confusion with categories

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            new XmpSerializer().serialize(xmp, baos, true);
            byte[] baseXmpBytes = baos.toByteArray();

            byte[] newXmpBytes = addCustomIdentifiersToXmp(baseXmpBytes, entity, helper, clear);

            byte[] existingXmpBytes = null;
            PDMetadata existingMetadata = pdf.getDocumentCatalog().getMetadata();
            if (existingMetadata != null) {
                try {
                    existingXmpBytes = existingMetadata.toByteArray();
                } catch (IOException ignore) {
                }
            }

            if (!isXmpMetadataDifferent(existingXmpBytes, newXmpBytes)) {
                log.info("XMP metadata unchanged, skipping write");
                return;
            }

            PDMetadata pdMetadata = new PDMetadata(pdf);
            pdMetadata.importXMPMetadata(newXmpBytes);
            pdf.getDocumentCatalog().setMetadata(pdMetadata);

            log.info("XMP metadata updated for PDF");
        } catch (Exception e) {
            log.warn("Failed to embed XMP metadata: {}", e.getMessage(), e);
        }
    }


    /**
     * Adds custom metadata to XMP using Booklore namespace for all custom fields.
     * <p>
     * Namespace strategy:
     * - Dublin Core (dc:) for title, description, creator, publisher, date, subject, language
     * - XMP Basic (xmp:) for metadata dates, creator tool
     * - Booklore (booklore:) for series, subtitle, ISBNs, external IDs, ratings, moods, tags, page count
     */
    private byte[] addCustomIdentifiersToXmp(byte[] xmpBytes, BookMetadataEntity metadata, MetadataCopyHelper helper, MetadataClearFlags clear) throws Exception {
        DocumentBuilder builder = SecureXmlUtils.createSecureDocumentBuilder(true);
        Document doc = builder.parse(new ByteArrayInputStream(xmpBytes));

        Element rdfRoot = (Element) doc.getElementsByTagNameNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "RDF").item(0);
        if (rdfRoot == null) throw new IllegalStateException("RDF root missing in XMP");

        // XMP Basic namespace for tool and date info
        Element xmpBasicDescription = doc.createElementNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "rdf:Description");
        xmpBasicDescription.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:xmp", "http://ns.adobe.com/xap/1.0/");
        xmpBasicDescription.setAttributeNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "rdf:about", "");

        xmpBasicDescription.appendChild(createXmpElement(doc, "xmp:CreatorTool", "Booklore"));
        // Use ISO-8601 format for current timestamps
        String nowIso = ZonedDateTime.now().format(java.time.format.DateTimeFormatter.ISO_INSTANT);
        xmpBasicDescription.appendChild(createXmpElement(doc, "xmp:MetadataDate", nowIso));
        xmpBasicDescription.appendChild(createXmpElement(doc, "xmp:ModifyDate", nowIso));
        if (metadata.getPublishedDate() != null) {
            // Use date-only format (YYYY-MM-DD) when we only have a date, not a full timestamp
            xmpBasicDescription.appendChild(createXmpElement(doc, "xmp:CreateDate", 
                    metadata.getPublishedDate().toString()));
        }

        rdfRoot.appendChild(xmpBasicDescription);

        // Booklore namespace for all custom metadata
        Element bookloreDescription = doc.createElementNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "rdf:Description");
        bookloreDescription.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:" + BookLoreMetadata.NS_PREFIX, BookLoreMetadata.NS_URI);
        bookloreDescription.setAttributeNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "rdf:about", "");

        // Series Information - ONLY write if BOTH name AND number are valid
        // A series name without a number is broken/incomplete data
        if (hasValidSeries(metadata, clear)) {
            appendBookloreElement(doc, bookloreDescription, "seriesName", metadata.getSeriesName());
            appendBookloreElement(doc, bookloreDescription, "seriesNumber", formatSeriesNumber(metadata.getSeriesNumber()));
            
            // Series total is optional, only write if > 0
            if (metadata.getSeriesTotal() != null && metadata.getSeriesTotal() > 0) {
                helper.copySeriesTotal(clear != null && clear.isSeriesTotal(), total -> {
                    if (total != null && total > 0) {
                        appendBookloreElement(doc, bookloreDescription, "seriesTotal", total.toString());
                    }
                });
            }
        }

        // Subtitle
        helper.copySubtitle(clear != null && clear.isSubtitle(), subtitle -> {
            if (subtitle != null && !subtitle.isBlank()) {
                appendBookloreElement(doc, bookloreDescription, "subtitle", subtitle);
            }
        });

        // ISBN Identifiers
        helper.copyIsbn13(clear != null && clear.isIsbn13(), isbn -> {
            if (isbn != null && !isbn.isBlank()) {
                appendBookloreElement(doc, bookloreDescription, "isbn13", isbn);
            }
        });

        helper.copyIsbn10(clear != null && clear.isIsbn10(), isbn -> {
            if (isbn != null && !isbn.isBlank()) {
                appendBookloreElement(doc, bookloreDescription, "isbn10", isbn);
            }
        });

        // External IDs (only if not blank)
        helper.copyGoogleId(clear != null && clear.isGoogleId(), id -> {
            if (id != null && !id.isBlank()) {
                appendBookloreElement(doc, bookloreDescription, "googleId", id);
            }
        });

        helper.copyGoodreadsId(clear != null && clear.isGoodreadsId(), id -> {
            String normalizedId = normalizeGoodreadsId(id);
            if (normalizedId != null && !normalizedId.isBlank()) {
                appendBookloreElement(doc, bookloreDescription, "goodreadsId", normalizedId);
            }
        });

        helper.copyHardcoverId(clear != null && clear.isHardcoverId(), id -> {
            if (id != null && !id.isBlank()) {
                appendBookloreElement(doc, bookloreDescription, "hardcoverId", id);
            }
        });

        helper.copyHardcoverBookId(clear != null && clear.isHardcoverBookId(), id -> {
            if (id != null && !id.isBlank()) {
                appendBookloreElement(doc, bookloreDescription, "hardcoverBookId", id);
            }
        });

        helper.copyAsin(clear != null && clear.isAsin(), id -> {
            if (id != null && !id.isBlank()) {
                appendBookloreElement(doc, bookloreDescription, "asin", id);
            }
        });

        helper.copyComicvineId(clear != null && clear.isComicvineId(), id -> {
            if (id != null && !id.isBlank()) {
                appendBookloreElement(doc, bookloreDescription, "comicvineId", id);
            }
        });

        helper.copyLubimyczytacId(clear != null && clear.isLubimyczytacId(), id -> {
            if (id != null && !id.isBlank()) {
                appendBookloreElement(doc, bookloreDescription, "lubimyczytacId", id);
            }
        });

        helper.copyRanobedbId(clear != null && clear.isRanobedbId(), id -> {
            if (id != null && !id.isBlank()) {
                appendBookloreElement(doc, bookloreDescription, "ranobedbId", id);
            }
        });

        // Ratings (only if > 0)
        helper.copyRating(false, rating -> appendBookloreRating(doc, bookloreDescription, "rating", rating));
        helper.copyHardcoverRating(clear != null && clear.isHardcoverRating(), rating -> appendBookloreRating(doc, bookloreDescription, "hardcoverRating", rating));
        helper.copyGoodreadsRating(clear != null && clear.isGoodreadsRating(), rating -> appendBookloreRating(doc, bookloreDescription, "goodreadsRating", rating));
        helper.copyAmazonRating(clear != null && clear.isAmazonRating(), rating -> appendBookloreRating(doc, bookloreDescription, "amazonRating", rating));
        helper.copyLubimyczytacRating(clear != null && clear.isLubimyczytacRating(), rating -> appendBookloreRating(doc, bookloreDescription, "lubimyczytacRating", rating));
        helper.copyRanobedbRating(clear != null && clear.isRanobedbRating(), rating -> appendBookloreRating(doc, bookloreDescription, "ranobedbRating", rating));

        // Tags (as RDF Bag)
        helper.copyTags(clear != null && clear.isTags(), tags -> {
            if (tags != null && !tags.isEmpty()) {
                appendBookloreBag(doc, bookloreDescription, "tags", tags);
            }
        });

        // Moods (as RDF Bag)
        helper.copyMoods(clear != null && clear.isMoods(), moods -> {
            if (moods != null && !moods.isEmpty()) {
                appendBookloreBag(doc, bookloreDescription, "moods", moods);
            }
        });

        // Page Count
        helper.copyPageCount(false, pageCount -> {
            if (pageCount != null && pageCount > 0) {
                appendBookloreElement(doc, bookloreDescription, "pageCount", pageCount.toString());
            }
        });

        if (bookloreDescription.hasChildNodes()) {
            rdfRoot.appendChild(bookloreDescription);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Transformer tf = TransformerFactory.newInstance().newTransformer();
        tf.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        tf.setOutputProperty(OutputKeys.INDENT, "yes");
        tf.transform(new DOMSource(doc), new StreamResult(baos));
        return baos.toByteArray();
    }

    private Element createXmpElement(Document doc, String name, String content) {
        Element el = doc.createElementNS("http://ns.adobe.com/xap/1.0/", name);
        el.setTextContent(content);
        return el;
    }

    private void appendBookloreElement(Document doc, Element parent, String localName, String value) {
        Element elem = doc.createElementNS(BookLoreMetadata.NS_URI, BookLoreMetadata.NS_PREFIX + ":" + localName);
        elem.setTextContent(value);
        parent.appendChild(elem);
    }

    private void appendBookloreRating(Document doc, Element parent, String localName, Double rating) {
        if (rating != null && rating > 0) {
            Element elem = doc.createElementNS(BookLoreMetadata.NS_URI, BookLoreMetadata.NS_PREFIX + ":" + localName);
            elem.setTextContent(String.format(Locale.US, "%.1f", rating));
            parent.appendChild(elem);
        }
    }

    private void appendBookloreBag(Document doc, Element parent, String localName, Set<String> values) {
        Element elem = doc.createElementNS(BookLoreMetadata.NS_URI, BookLoreMetadata.NS_PREFIX + ":" + localName);
        Element rdfBag = doc.createElementNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "rdf:Bag");
        
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                Element li = doc.createElementNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "rdf:li");
                li.setTextContent(value);
                rdfBag.appendChild(li);
            }
        }
        
        elem.appendChild(rdfBag);
        parent.appendChild(elem);
    }

    private boolean isXmpMetadataDifferent(byte[] existingBytes, byte[] newBytes) {
        if (existingBytes == null || newBytes == null) return true;
        try {
            DocumentBuilder builder = SecureXmlUtils.createSecureDocumentBuilder(false);
            Document doc1 = builder.parse(new ByteArrayInputStream(existingBytes));
            Document doc2 = builder.parse(new ByteArrayInputStream(newBytes));
            return !Objects.equals(
                    doc1.getDocumentElement().getTextContent().trim(),
                    doc2.getDocumentElement().getTextContent().trim()
            );
        } catch (Exception e) {
            log.warn("XMP diff failed: {}", e.getMessage());
            return true;
        }
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
        
        // Extract numeric ID from slug format "12345678-book-title"
        int dashIndex = goodreadsId.indexOf('-');
        if (dashIndex > 0) {
            String numericPart = goodreadsId.substring(0, dashIndex);
            // Validate it's actually numeric
            if (numericPart.matches("\\d+")) {
                return numericPart;
            }
        }
        
        // Already just the ID, or return as-is if it's all numeric
        return goodreadsId.matches("\\d+") ? goodreadsId : goodreadsId;
    }

}
