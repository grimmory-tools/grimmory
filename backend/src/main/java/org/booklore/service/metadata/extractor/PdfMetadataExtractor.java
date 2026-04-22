package org.booklore.service.metadata.extractor;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.grimmory.pdfium4j.PdfDocument;
import org.grimmory.pdfium4j.model.MetadataTag;
import org.booklore.model.dto.BookMetadata;
import org.booklore.util.SecureXmlUtils;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.StringReader;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Slf4j
public class PdfMetadataExtractor implements FileMetadataExtractor {


    private static final Pattern COMMA_AMPERSAND_PATTERN = Pattern.compile("[,&]");
    private static final Pattern ISBN_CLEANUP_PATTERN = Pattern.compile("[^0-9Xx]");
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern PDF_DATE_TIME_PATTERN = Pattern.compile("\\d{8,}");
    private static final Pattern YEAR_MONTH_PATTERN = Pattern.compile("\\d{6}");

    @Override
    public byte[] extractCover(File file) {
        try (PdfDocument doc = PdfDocument.open(file.toPath())) {
            return doc.renderPageToBytes(0, 300, "jpeg");
        } catch (Exception e) {
            log.warn("Failed to extract cover from PDF: {}", file.getAbsolutePath(), e);
            return null;
        }
    }

    @Override
    public BookMetadata extractMetadata(File file) {
        if (!file.exists() || !file.isFile()) {
            log.warn("File does not exist or is not a file: {}", file.getPath());
            return BookMetadata.builder().build();
        }

        BookMetadata.BookMetadataBuilder metadataBuilder = BookMetadata.builder();

        try (PdfDocument doc = PdfDocument.open(file.toPath())) {

            String title = doc.metadata(MetadataTag.TITLE).orElse(null);
            if (StringUtils.isNotBlank(title)) {
                metadataBuilder.title(title);
            } else {
                metadataBuilder.title(FilenameUtils.getBaseName(file.getName()));
            }

            String author = doc.metadata(MetadataTag.AUTHOR).orElse(null);
            if (StringUtils.isNotBlank(author)) {
                List<String> authors = parseAuthors(author);
                if (!authors.isEmpty()) {
                    metadataBuilder.authors(authors);
                }
            }

            String subject = doc.metadata(MetadataTag.SUBJECT).orElse(null);
            if (StringUtils.isNotBlank(subject)) {
                metadataBuilder.description(subject);
            }

            String ebxPublisher = doc.metadata("EBX_PUBLISHER").orElse(null);
            if (StringUtils.isNotBlank(ebxPublisher)) {
                metadataBuilder.publisher(ebxPublisher);
            }

            String creationDate = doc.metadata(MetadataTag.CREATION_DATE).orElse(null);
            if (StringUtils.isNotBlank(creationDate)) {
                LocalDate date = parsePdfDate(creationDate);
                if (date != null) {
                    metadataBuilder.publishedDate(date);
                }
            }

            metadataBuilder.pageCount(doc.pageCount());

            String keywords = doc.metadata(MetadataTag.KEYWORDS).orElse(null);
            if (StringUtils.isNotBlank(keywords)) {
                String[] parts;
                if (keywords.contains(";")) {
                    parts = keywords.split(";");
                } else {
                    parts = keywords.split(",");
                }
                Set<String> categories = Arrays.stream(parts)
                        .map(String::trim)
                        .filter(StringUtils::isNotBlank)
                        .collect(java.util.stream.Collectors.toSet());
                if (!categories.isEmpty()) {
                    metadataBuilder.categories(categories);
                }
            }

            String languageValue = doc.metadata("Language").orElse(null);
            if (StringUtils.isNotBlank(languageValue)) {
                metadataBuilder.language(languageValue);
            }

            String rawXmp = doc.xmpMetadataString();

            if (StringUtils.isNotBlank(rawXmp)) {
                try {
                    DocumentBuilder dBuilder = SecureXmlUtils.createSecureDocumentBuilder(true);
                    Document xmpDoc = dBuilder.parse(new InputSource(new StringReader(rawXmp)));

                    XPathFactory xPathfactory = XPathFactory.newInstance();
                    XPath xpath = xPathfactory.newXPath();
                    xpath.setNamespaceContext(new XmpNamespaceContext());

                    extractDublinCoreMetadata(xpath, xmpDoc, metadataBuilder);
                    extractCalibreMetadata(xpath, xmpDoc, metadataBuilder);
                    extractBookloreMetadata(xpath, xmpDoc, metadataBuilder);
                    
                    // Debug logging for troubleshooting extraction issues
                    if (log.isDebugEnabled()) {
                        BookMetadata debugMeta = metadataBuilder.build();
                        log.debug("PDF XMP extraction results - subtitle: '{}', moods: {}, tags: {}", 
                            debugMeta.getSubtitle(), debugMeta.getMoods(), debugMeta.getTags());
                    }

                    Map<String, String> identifiers = extractIdentifiers(xpath, xmpDoc);
                    if (!identifiers.isEmpty()) {
                        // Extract generic ISBN first
                        String isbn = identifiers.get("isbn");
                        if (StringUtils.isNotBlank(isbn)) {
                            isbn = ISBN_CLEANUP_PATTERN.matcher(isbn).replaceAll("");
                            if (isbn.length() == 10) {
                                metadataBuilder.isbn10(isbn);
                            } else if (isbn.length() == 13) {
                                metadataBuilder.isbn13(isbn);
                            } else {
                                metadataBuilder.isbn13(isbn); // Fallback
                            }
                        }

                        // Extract specific ISBN schemes (overwrites generic only if valid)
                        String isbn13 = identifiers.get("isbn13");
                        if (StringUtils.isNotBlank(isbn13)) {
                            String cleaned = ISBN_CLEANUP_PATTERN.matcher(isbn13).replaceAll("");
                            if (!cleaned.isBlank()) {
                                metadataBuilder.isbn13(cleaned);
                            }
                        }

                        String isbn10 = identifiers.get("isbn10");
                        if (StringUtils.isNotBlank(isbn10)) {
                            String cleaned = ISBN_CLEANUP_PATTERN.matcher(isbn10).replaceAll("");
                            if (!cleaned.isBlank()) {
                                metadataBuilder.isbn10(cleaned);
                            }
                        }

                        String google = identifiers.get("google");
                        if (StringUtils.isNotBlank(google)) {
                            metadataBuilder.googleId(google);
                        }

                        String amazon = identifiers.get("amazon");
                        if (StringUtils.isNotBlank(amazon)) {
                            metadataBuilder.asin(amazon);
                        }

                        String goodreads = identifiers.get("goodreads");
                        if (StringUtils.isNotBlank(goodreads)) {
                            metadataBuilder.goodreadsId(goodreads);
                        }

                        String comicvine = identifiers.get("comicvine");
                        if (StringUtils.isNotBlank(comicvine)) {
                            metadataBuilder.comicvineId(comicvine);
                        }

                        String ranobedb = identifiers.get("ranobedb");
                        if (StringUtils.isNotBlank(ranobedb)) {
                            metadataBuilder.ranobedbId(ranobedb);
                        }

                        String hardcover = identifiers.get("hardcover");
                        if (StringUtils.isNotBlank(hardcover)) {
                            metadataBuilder.hardcoverId(hardcover);
                        }

                        String hardcoverBookId = identifiers.get("hardcover_book_id");
                        if (StringUtils.isNotBlank(hardcoverBookId)) {
                            metadataBuilder.hardcoverBookId(hardcoverBookId);
                        }

                        String lubimyczytac = identifiers.get("lubimyczytac");
                        if (StringUtils.isNotBlank(lubimyczytac)) {
                            metadataBuilder.lubimyczytacId(lubimyczytac);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse XMP metadata with XML parser: {}", e.getMessage(), e);
                }
            }

        } catch (Exception e) {
            log.error("Failed to load PDF file: {}", file.getPath(), e);
        }

        return metadataBuilder.build();
    }

    private void extractDublinCoreMetadata(XPath xpath, Document doc, BookMetadata.BookMetadataBuilder builder) throws XPathExpressionException {
        String title = xpathEvaluate(xpath, doc, "//dc:title/rdf:Alt/rdf:li/text()");
        if (StringUtils.isNotBlank(title)) {
            builder.title(title);
        }

        String description = xpathEvaluate(xpath, doc, "//dc:description/rdf:Alt/rdf:li/text()");
        if (StringUtils.isNotBlank(description)) {
            builder.description(description);
        }

        String publisher = xpathEvaluate(xpath, doc, "//dc:publisher/rdf:Bag/rdf:li/text()");
        if (StringUtils.isNotBlank(publisher)) {
            builder.publisher(publisher);
        }

        String language = xpathEvaluate(xpath, doc, "//dc:language/rdf:Bag/rdf:li/text()");
        if (StringUtils.isNotBlank(language)) {
            builder.language(language);
        }

        List<String> creators = xpathEvaluateMultiple(xpath, doc, "//dc:creator/rdf:Seq/rdf:li/text()");
        if (!creators.isEmpty()) {
            builder.authors(creators);
        }

        Set<String> subjects = new HashSet<>(xpathEvaluateMultiple(xpath, doc, "//dc:subject/rdf:Bag/rdf:li/text()"));
        if (!subjects.isEmpty()) {
            Set<String> knownNonCategories = new HashSet<>();
            
            try {
                String moods = xpath.evaluate("//booklore:Moods/text()", doc);
                if (StringUtils.isNotBlank(moods)) {
                    Arrays.stream(moods.split(";")).map(String::trim).forEach(knownNonCategories::add);
                }
                String tags = xpath.evaluate("//booklore:Tags/text()", doc);
                if (StringUtils.isNotBlank(tags)) {
                    Arrays.stream(tags.split(";")).map(String::trim).forEach(knownNonCategories::add);
                }
            } catch (Exception ignored) {}
            
            subjects.removeAll(knownNonCategories);
            
            if (!subjects.isEmpty()) {
                builder.categories(subjects);
            }
        }
    }

    private void extractCalibreMetadata(XPath xpath, Document doc, BookMetadata.BookMetadataBuilder builder) {
        try {
            String series = xpath.evaluate("//calibre:series/rdf:value/text()", doc);
            if (StringUtils.isNotBlank(series)) {
                builder.seriesName(series);
            }

            // Try fully qualified series_index
            String seriesIndex = xpath.evaluate("//calibre:series/calibreSI:series_index/text()", doc);

            if (StringUtils.isBlank(seriesIndex)) {
                // Try without prefix, in case it's missing namespace
                seriesIndex = xpath.evaluate("//calibre:series/*[local-name()='series_index']/text()", doc);
            }

            if (StringUtils.isNotBlank(seriesIndex)) {
                try {
                    builder.seriesNumber(Float.parseFloat(seriesIndex));
                } catch (NumberFormatException e) {
                    log.warn("Invalid series index: {}", seriesIndex);
                }
            }

        } catch (Exception e) {
            log.warn("Failed to extract calibre metadata: {}", e.getMessage(), e);
        }
    }

    private void extractBookloreMetadata(XPath xpath, Document doc, BookMetadata.BookMetadataBuilder builder) {
        try {
            // Series information (now in Booklore namespace, not Calibre)
            String seriesName = extractBookloreField(xpath, doc, "seriesName");
            if (StringUtils.isNotBlank(seriesName)) {
                builder.seriesName(seriesName);
            }

            String seriesNumber = extractBookloreField(xpath, doc, "seriesNumber");
            if (StringUtils.isNotBlank(seriesNumber)) {
                try {
                    builder.seriesNumber(Float.parseFloat(seriesNumber.trim()));
                } catch (NumberFormatException e) {
                    log.warn("Invalid series number: {}", seriesNumber);
                }
            }

            String seriesTotal = extractBookloreField(xpath, doc, "seriesTotal");
            if (StringUtils.isNotBlank(seriesTotal)) {
                try {
                    builder.seriesTotal(Integer.parseInt(seriesTotal.trim()));
                } catch (NumberFormatException e) {
                    log.warn("Invalid series total: {}", seriesTotal);
                }
            }

            // Subtitle (try both old PascalCase and new camelCase)
            String subtitle = extractBookloreField(xpath, doc, "subtitle");
            log.debug("Extracted subtitle (camelCase): '{}'", subtitle);
            if (StringUtils.isBlank(subtitle)) {
                subtitle = xpath.evaluate("//booklore:Subtitle/text()", doc);
                log.debug("Extracted subtitle (PascalCase fallback): '{}'", subtitle);
            }
            if (StringUtils.isNotBlank(subtitle)) {
                builder.subtitle(subtitle.trim());
            }

            // ISBNs from Booklore namespace
            String isbn13 = extractBookloreField(xpath, doc, "isbn13");
            if (StringUtils.isNotBlank(isbn13)) {
                builder.isbn13(isbn13.trim());
            }
            
            String isbn10 = extractBookloreField(xpath, doc, "isbn10");
            if (StringUtils.isNotBlank(isbn10)) {
                builder.isbn10(isbn10.trim());
            }

            // External IDs from Booklore namespace
            String googleId = extractBookloreField(xpath, doc, "googleId");
            if (StringUtils.isNotBlank(googleId)) {
                builder.googleId(googleId.trim());
            }

            String goodreadsId = extractBookloreField(xpath, doc, "goodreadsId");
            if (StringUtils.isNotBlank(goodreadsId)) {
                builder.goodreadsId(goodreadsId.trim());
            }

            String hardcoverId = extractBookloreField(xpath, doc, "hardcoverId");
            if (StringUtils.isNotBlank(hardcoverId)) {
                builder.hardcoverId(hardcoverId.trim());
            }

            String hardcoverBookId = extractBookloreField(xpath, doc, "hardcoverBookId");
            if (StringUtils.isNotBlank(hardcoverBookId)) {
                builder.hardcoverBookId(hardcoverBookId.trim());
            }

            String asin = extractBookloreField(xpath, doc, "asin");
            if (StringUtils.isNotBlank(asin)) {
                builder.asin(asin.trim());
            }

            String comicvineId = extractBookloreField(xpath, doc, "comicvineId");
            if (StringUtils.isNotBlank(comicvineId)) {
                builder.comicvineId(comicvineId.trim());
            }

            String lubimyczytacId = extractBookloreField(xpath, doc, "lubimyczytacId");
            if (StringUtils.isNotBlank(lubimyczytacId)) {
                builder.lubimyczytacId(lubimyczytacId.trim());
            }

            String ranobedbId = extractBookloreField(xpath, doc, "ranobedbId");
            if (StringUtils.isNotBlank(ranobedbId)) {
                builder.ranobedbId(ranobedbId.trim());
            }

            // Page count: Do NOT read from XMP metadata for PDFs.
            // The actual PDF page count (from pdf.getNumberOfPages()) is set earlier
            // and should not be overridden by metadata that describes the original book.

            // Moods (try new RDF Bag format first, then legacy semicolon-separated)
            Set<String> moods = extractBookloreBag(xpath, doc, "moods");
            log.debug("Extracted moods from RDF Bag: {}", moods);
            if (moods.isEmpty()) {
                // Legacy format support
                String moodsLegacy = xpath.evaluate("//booklore:Moods/text()", doc);
                log.debug("Legacy moods string: '{}'", moodsLegacy);
                if (StringUtils.isNotBlank(moodsLegacy)) {
                    moods = Arrays.stream(moodsLegacy.split(";"))
                            .map(String::trim)
                            .filter(StringUtils::isNotBlank)
                            .collect(Collectors.toSet());
                }
            }
            if (!moods.isEmpty()) {
                builder.moods(moods);
            }

            // Tags (try new RDF Bag format first, then legacy semicolon-separated)
            Set<String> tags = extractBookloreBag(xpath, doc, "tags");
            log.debug("Extracted tags from RDF Bag: {}", tags);
            if (tags.isEmpty()) {
                // Legacy format support
                String tagsLegacy = xpath.evaluate("//booklore:Tags/text()", doc);
                log.debug("Legacy tags string: '{}'", tagsLegacy);
                if (StringUtils.isNotBlank(tagsLegacy)) {
                    tags = Arrays.stream(tagsLegacy.split(";"))
                            .map(String::trim)
                            .filter(StringUtils::isNotBlank)
                            .collect(Collectors.toSet());
                }
            }
            if (!tags.isEmpty()) {
                builder.tags(tags);
            }

            // Ratings (try both old PascalCase and new camelCase)
            extractBookloreRating(xpath, doc, "amazonRating", "AmazonRating", builder::amazonRating);
            extractBookloreRating(xpath, doc, "goodreadsRating", "GoodreadsRating", builder::goodreadsRating);
            extractBookloreRating(xpath, doc, "hardcoverRating", "HardcoverRating", builder::hardcoverRating);
            extractBookloreRating(xpath, doc, "lubimyczytacRating", "LubimyczytacRating", builder::lubimyczytacRating);
            extractBookloreRating(xpath, doc, "ranobedbRating", "RanobedbRating", builder::ranobedbRating);
            
            // User rating
            extractBookloreRating(xpath, doc, "rating", "Rating", builder::rating);

        } catch (Exception e) {
            log.warn("Failed to extract booklore metadata: {}", e.getMessage(), e);
        }
    }

    /**
     * Extracts a simple text field from Booklore namespace.
     */
    private String extractBookloreField(XPath xpath, Document doc, String fieldName) {
        try {
            return xpath.evaluate("//booklore:" + fieldName + "/text()", doc);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Extracts an RDF Bag as a Set of strings from Booklore namespace.
     */
    private Set<String> extractBookloreBag(XPath xpath, Document doc, String fieldName) {
        Set<String> values = new HashSet<>();
        try {
            String xpathExpr = "//booklore:" + fieldName + "/rdf:Bag/rdf:li/text()";
            log.debug("Executing XPath for {}: {}", fieldName, xpathExpr);
            NodeList nodes = (NodeList) xpath.evaluate(xpathExpr, doc, XPathConstants.NODESET);
            log.debug("XPath for {} returned {} nodes", fieldName, nodes != null ? nodes.getLength() : 0);
            if (nodes != null) {
                for (int i = 0; i < nodes.getLength(); i++) {
                    String text = nodes.item(i).getNodeValue();
                    if (StringUtils.isNotBlank(text)) {
                        values.add(text.trim());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract RDF Bag for booklore:{}: {}", fieldName, e.getMessage());
        }
        return values;
    }

    /**
     * Extracts a rating field, trying both new camelCase and old PascalCase format.
     */
    private void extractBookloreRating(XPath xpath, Document doc, String newName, String legacyName, 
                                        java.util.function.Consumer<Double> setter) {
        try {
            String value = xpath.evaluate("//booklore:" + newName + "/text()", doc);
            if (StringUtils.isBlank(value)) {
                value = xpath.evaluate("//booklore:" + legacyName + "/text()", doc);
            }
            if (StringUtils.isNotBlank(value)) {
                setter.accept(Double.parseDouble(value.trim()));
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid rating value for {}", newName);
        } catch (Exception e) {
            // Ignore
        }
    }

    private Map<String, String> extractIdentifiers(XPath xpath, Document doc) throws XPathExpressionException {
        Map<String, String> ids = new HashMap<>();
        NodeList idNodes = (NodeList) xpath.evaluate(
                "//xmp:Identifier/rdf:Bag/rdf:li", doc, XPathConstants.NODESET);

        if (idNodes != null) {
            for (int i = 0; i < idNodes.getLength(); i++) {
                String scheme = xpathEvaluate(xpath, idNodes.item(i), "xmpidq:Scheme/text()");
                String value = xpathEvaluate(xpath, idNodes.item(i), "rdf:value/text()");
                if (StringUtils.isNotBlank(scheme) && StringUtils.isNotBlank(value)) {
                    ids.put(scheme.toLowerCase(Locale.ROOT), value);
                }
            }
        }
        return ids;
    }

    private List<String> parseAuthors(String authorString) {
        if (authorString == null) return Collections.emptyList();
        return Arrays.stream(COMMA_AMPERSAND_PATTERN.split(authorString))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .toList();
    }

    /**
     * Parses PDF date strings in the standard D:YYYYMMDDHHmmSS format.
     * Falls back to ISO date parsing if the D: prefix is not present.
     */
    private LocalDate parsePdfDate(String pdfDate) {
        if (pdfDate == null || pdfDate.isBlank()) return null;
        try {
            String s = pdfDate.startsWith("D:") ? pdfDate.substring(2) : pdfDate;
            // Try ISO date format first (e.g. "2021-02-17")
            if (ISO_DATE_PATTERN.matcher(s).matches()) {
                return LocalDate.parse(s);
            }
            // Strip timezone info (e.g. +00'00' or Z)
            int tzIdx = s.indexOf('+');
            if (tzIdx < 0) tzIdx = s.indexOf('-', 8); // skip YYYYMMDD
            if (tzIdx < 0) tzIdx = s.indexOf('Z');
            if (tzIdx > 0) s = s.substring(0, tzIdx);
            if (PDF_DATE_TIME_PATTERN.matcher(s).matches()) {
                int year = Integer.parseInt(s.substring(0, 4));
                int month = Integer.parseInt(s.substring(4, 6));
                int day = Integer.parseInt(s.substring(6, 8));
                return LocalDate.of(year, month, day);
            }
            if (YEAR_MONTH_PATTERN.matcher(s).matches()) {
                return LocalDate.of(
                        Integer.parseInt(s.substring(0, 4)),
                        Integer.parseInt(s.substring(4, 6)),
                        1
                );
            }
            if (s.matches("\\d{4}")) {
                return LocalDate.of(Integer.parseInt(s.substring(0, 4)), 1, 1);
            }
        } catch (Exception e) {
            log.debug("Failed to parse PDF date '{}': {}", pdfDate, e.getMessage());
        }
        return null;
    }

    private String xpathEvaluate(XPath xpath, Document doc, String expression) throws XPathExpressionException {
        String result = xpath.evaluate(expression, doc);
        return result == null ? "" : result.trim();
    }

    private String xpathEvaluate(XPath xpath, org.w3c.dom.Node node, String expression) throws XPathExpressionException {
        String result = xpath.evaluate(expression, node);
        return result == null ? "" : result.trim();
    }

    private List<String> xpathEvaluateMultiple(XPath xpath, Document doc, String expression) throws XPathExpressionException {
        NodeList nodes = (NodeList) xpath.evaluate(expression, doc, XPathConstants.NODESET);
        List<String> results = new ArrayList<>();
        if (nodes != null) {
            for (int i = 0; i < nodes.getLength(); i++) {
                String text = nodes.item(i).getNodeValue();
                if (StringUtils.isNotBlank(text)) {
                    results.add(text.trim());
                }
            }
        }
        return results;
    }

    private static class XmpNamespaceContext implements NamespaceContext {
        private final Map<String, String> prefixMap = new HashMap<>();

        public XmpNamespaceContext() {
            prefixMap.put("dc", "http://purl.org/dc/elements/1.1/");
            prefixMap.put("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
            prefixMap.put("xmp", "http://ns.adobe.com/xap/1.0/");
            prefixMap.put("xmpidq", "http://ns.adobe.com/xmp/Identifier/qual/1.0/");
            prefixMap.put("calibre", "http://calibre-ebook.com/xmp-namespace");
            prefixMap.put("calibreSI", "http://calibre-ebook.com/xmp-namespace/seriesIndex");
            prefixMap.put("booklore", "http://booklore.org/metadata/1.0/");
        }

        @Override
        public String getNamespaceURI(String prefix) {
            return prefixMap.getOrDefault(prefix, "");
        }

        @Override
        public String getPrefix(String namespaceURI) {
            for (Map.Entry<String, String> e : prefixMap.entrySet()) {
                if (e.getValue().equals(namespaceURI)) {
                    return e.getKey();
                }
            }
            return null;
        }

        @Override
        public Iterator<String> getPrefixes(String namespaceURI) {
            return prefixMap.keySet().iterator();
        }
    }
}
