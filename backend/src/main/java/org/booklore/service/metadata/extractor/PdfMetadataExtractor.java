package org.booklore.service.metadata.extractor;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.grimmory.pdfium4j.PdfDocument;
import org.grimmory.pdfium4j.XmpMetadataParser;
import org.grimmory.pdfium4j.model.MetadataTag;
import org.grimmory.pdfium4j.model.XmpMetadata;
import org.booklore.model.dto.BookMetadata;
import org.booklore.util.SecureXmlUtils;
import org.springframework.stereotype.Component;
import org.grimmory.pdfium4j.model.XmpMetadata.QualifiedIdentifier;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

@Component
@Slf4j
public class PdfMetadataExtractor implements FileMetadataExtractor {

    private static final String DC_NAMESPACE = "http://purl.org/dc/elements/1.1/";
    private static final String BOOKLORE_NAMESPACE = "http://booklore.org/metadata/1.0/";
    private static final String RDF_NAMESPACE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";


    private static final Pattern COMMA_AMPERSAND_PATTERN = Pattern.compile("[,&]");
    private static final Pattern ISBN_CLEANUP_PATTERN = Pattern.compile("[^0-9Xx]");
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern PDF_DATE_TIME_PATTERN = Pattern.compile("\\d{8,}");
    private static final Pattern YEAR_MONTH_PATTERN = Pattern.compile("\\d{6}");
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\d{4}");

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
                        .collect(Collectors.toSet());
                if (!categories.isEmpty()) {
                    metadataBuilder.categories(categories);
                }
            }

            String languageValue = doc.metadata("Language").orElse(null);
            if (StringUtils.isNotBlank(languageValue)) {
                metadataBuilder.language(languageValue);
            }

            XmpMetadata xmp = XmpMetadataParser.parseFrom(doc);
        Optional<RawXmpMetadata> rawXmp = parseRawXmp(doc);

            // Dublin Core
        preferStructuredValue(xmp.title(), rawXmp.flatMap(xmpData -> xmpData.readFirstText(DC_NAMESPACE, "title")))
            .ifPresent(metadataBuilder::title);
        preferStructuredValue(xmp.description(), rawXmp.flatMap(xmpData -> xmpData.readFirstText(DC_NAMESPACE, "description")))
            .ifPresent(metadataBuilder::description);
        preferStructuredValue(xmp.publisher(), rawXmp.flatMap(xmpData -> xmpData.readFirstText(DC_NAMESPACE, "publisher")))
            .ifPresent(metadataBuilder::publisher);
        preferStructuredValue(xmp.language(), rawXmp.flatMap(xmpData -> xmpData.readFirstText(DC_NAMESPACE, "language")))
            .ifPresent(metadataBuilder::language);

        List<String> creators = !xmp.creators().isEmpty()
            ? xmp.creators()
            : rawXmp.map(xmpData -> xmpData.readList(DC_NAMESPACE, "creator")).orElseGet(List::of);
        if (!creators.isEmpty()) {
        metadataBuilder.authors(creators);
        }

            rawXmp.flatMap(xmpData -> xmpData.readFirstText(DC_NAMESPACE, "date"))
                .map(this::parsePdfDate)
                .ifPresent(metadataBuilder::publishedDate);

            // Categories, filtering out moods and tags that might be in dc:subject
        List<String> subjects = !xmp.subjects().isEmpty()
            ? xmp.subjects()
            : rawXmp.map(xmpData -> xmpData.readList(DC_NAMESPACE, "subject")).orElseGet(List::of);
        if (!subjects.isEmpty()) {
        Set<String> categories = new HashSet<>(subjects);
                Set<String> knownNonCategories = new HashSet<>();
                
                // Get moods and tags from booklore namespace to filter them out of subjects
        List<String> moods = findCustomListField(xmp, rawXmp, "moods");
                knownNonCategories.addAll(moods);

        List<String> tags = findCustomListField(xmp, rawXmp, "tags");
                knownNonCategories.addAll(tags);

                // Custom semicolon-separated strings fallback
        findCustomField(xmp, rawXmp, "moods")
            .ifPresent(m -> Arrays.stream(m.split(";")).map(String::trim).forEach(knownNonCategories::add));
        findCustomField(xmp, rawXmp, "tags")
            .ifPresent(t -> Arrays.stream(t.split(";")).map(String::trim).forEach(knownNonCategories::add));

                categories.removeAll(knownNonCategories);
                if (!categories.isEmpty()) {
                    metadataBuilder.categories(categories);
                }
            }

            // Calibre
            xmp.calibreSeries().ifPresent(metadataBuilder::seriesName);
            xmp.calibreSeriesIndex().ifPresent(idx -> metadataBuilder.seriesNumber(idx.floatValue()));

            // Booklore
            findCustomField(xmp, rawXmp, "seriesName").ifPresent(metadataBuilder::seriesName);
            findCustomField(xmp, rawXmp, "seriesNumber").ifPresent(val -> {
                try { metadataBuilder.seriesNumber(Float.parseFloat(val)); } catch (Exception _) {}
            });
            findCustomField(xmp, rawXmp, "seriesTotal").ifPresent(val -> {
                try { metadataBuilder.seriesTotal(Integer.parseInt(val)); } catch (Exception _) {}
            });
            
            findCustomField(xmp, rawXmp, "subtitle").ifPresent(metadataBuilder::subtitle);

            // Identifiers
            findCustomField(xmp, rawXmp, "isbn13").ifPresent(val -> metadataBuilder.isbn13(ISBN_CLEANUP_PATTERN.matcher(val).replaceAll("")));
            findCustomField(xmp, rawXmp, "isbn10").ifPresent(val -> metadataBuilder.isbn10(ISBN_CLEANUP_PATTERN.matcher(val).replaceAll("")));
            findCustomField(xmp, rawXmp, "googleId").ifPresent(metadataBuilder::googleId);
            findCustomField(xmp, rawXmp, "goodreadsId").ifPresent(metadataBuilder::goodreadsId);
            findCustomField(xmp, rawXmp, "amazonId").ifPresent(metadataBuilder::asin);
            findCustomField(xmp, rawXmp, "asin").ifPresent(metadataBuilder::asin);
            findCustomField(xmp, rawXmp, "comicvineId").ifPresent(metadataBuilder::comicvineId);
            findCustomField(xmp, rawXmp, "ranobedbId").ifPresent(metadataBuilder::ranobedbId);
            findCustomField(xmp, rawXmp, "lubimyczytacId").ifPresent(metadataBuilder::lubimyczytacId);
            findCustomField(xmp, rawXmp, "hardcoverId").ifPresent(metadataBuilder::hardcoverId);
            findCustomField(xmp, rawXmp, "hardcoverBookId").ifPresent(metadataBuilder::hardcoverBookId);

            // XMP Qualified Identifiers
            for (QualifiedIdentifier qi : xmp.xmpIdentifiers()) {
                String scheme = qi.scheme().toLowerCase(Locale.ROOT);
                String value = qi.value();
                switch (scheme) {
                    case "isbn" -> {
                        String cleaned = ISBN_CLEANUP_PATTERN.matcher(value).replaceAll("");
                        if (cleaned.length() == 13) metadataBuilder.isbn13(cleaned);
                        else if (cleaned.length() == 10) metadataBuilder.isbn10(cleaned);
                        else metadataBuilder.isbn13(cleaned); // Fallback for odd lengths
                    }
                    case "isbn13" -> metadataBuilder.isbn13(ISBN_CLEANUP_PATTERN.matcher(value).replaceAll(""));
                    case "isbn10" -> metadataBuilder.isbn10(ISBN_CLEANUP_PATTERN.matcher(value).replaceAll(""));
                    case "google" -> metadataBuilder.googleId(value);
                    case "amazon", "asin", "amazonid" -> metadataBuilder.asin(value);
                    case "goodreads" -> metadataBuilder.goodreadsId(value);
                    case "comicvine" -> metadataBuilder.comicvineId(value);
                    case "ranobedb" -> metadataBuilder.ranobedbId(value);
                    case "lubimyczytac" -> metadataBuilder.lubimyczytacId(value);
                    case "hardcover" -> metadataBuilder.hardcoverId(value);
                    case "hardcover_book_id" -> metadataBuilder.hardcoverBookId(value);
                }
            }

            // Calibre fallback for un-prefixed series_index (some tools write it like this, and library skips them)
            if (xmp.calibreSeriesIndex().isEmpty()) {
                byte[] rawXmpBytes = doc.xmpMetadata();
                if (rawXmpBytes != null) {
                    String xmpStr = new String(rawXmpBytes, StandardCharsets.UTF_8);
                    Matcher siMatcher = Pattern.compile("<series_index>([^<]+)</series_index>").matcher(xmpStr);
                    if (siMatcher.find()) {
                        try { metadataBuilder.seriesNumber(Float.parseFloat(siMatcher.group(1).trim())); } catch (Exception _) {}
                    }
                }
            }

            // Ratings
            mapRating(xmp, rawXmp, "rating", "Rating", metadataBuilder::rating);
            mapRating(xmp, rawXmp, "amazonRating", "AmazonRating", metadataBuilder::amazonRating);
            mapRating(xmp, rawXmp, "goodreadsRating", "GoodreadsRating", metadataBuilder::goodreadsRating);
            mapRating(xmp, rawXmp, "hardcoverRating", "HardcoverRating", metadataBuilder::hardcoverRating);
            mapRating(xmp, rawXmp, "lubimyczytacRating", "LubimyczytacRating", metadataBuilder::lubimyczytacRating);
            mapRating(xmp, rawXmp, "ranobedbRating", "RanobedbRating", metadataBuilder::ranobedbRating);

            // Moods and Tags (List/Bag with semicolon fallback)
            Set<String> moodsSet = new LinkedHashSet<>(findCustomListField(xmp, rawXmp, "moods"));
            if (moodsSet.isEmpty()) {
                findCustomField(xmp, rawXmp, "moods").ifPresent(m ->
                    Arrays.stream(m.split(";")).map(String::trim).filter(StringUtils::isNotBlank).forEach(moodsSet::add));
            }
            if (!moodsSet.isEmpty()) metadataBuilder.moods(moodsSet);

            Set<String> tagsSet = new LinkedHashSet<>(findCustomListField(xmp, rawXmp, "tags"));
            if (tagsSet.isEmpty()) {
                findCustomField(xmp, rawXmp, "tags").ifPresent(t ->
                    Arrays.stream(t.split(";")).map(String::trim).filter(StringUtils::isNotBlank).forEach(tagsSet::add));
            }
            if (!tagsSet.isEmpty()) metadataBuilder.tags(tagsSet);

        } catch (Exception e) {
            log.error("Failed to load PDF file: {}", file.getPath(), e);
        }

        return metadataBuilder.build();
    }


    private Optional<String> findCustomField(XmpMetadata xmp, Optional<RawXmpMetadata> rawXmp, String name) {
        Optional<String> val = xmp.findField(name);
        if (val.isPresent()) return val;
        // Try PascalCase
        String pascal = name.substring(0, 1).toUpperCase() + name.substring(1);
        val = xmp.findField(pascal);
        if (val.isPresent()) return val;
        return rawXmp.flatMap(xmpData -> xmpData.readFirstText(BOOKLORE_NAMESPACE, name)
                .or(() -> xmpData.readFirstText(BOOKLORE_NAMESPACE, pascal)));
    }

    private List<String> findCustomListField(XmpMetadata xmp, Optional<RawXmpMetadata> rawXmp, String name) {
        List<String> values = xmp.findListField(name);
        if (!values.isEmpty()) {
            return values;
        }

        String pascal = name.substring(0, 1).toUpperCase() + name.substring(1);
        values = xmp.findListField(pascal);
        if (!values.isEmpty()) {
            return values;
        }

        return rawXmp.map(xmpData -> {
            List<String> rawValues = xmpData.readList(BOOKLORE_NAMESPACE, name);
            if (!rawValues.isEmpty()) {
                return rawValues;
            }
            return xmpData.readList(BOOKLORE_NAMESPACE, pascal);
        }).orElseGet(List::of);
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
            if (YEAR_PATTERN.matcher(s).matches()) {
                return LocalDate.of(Integer.parseInt(s.substring(0, 4)), 1, 1);
            }
        } catch (Exception e) {
            log.debug("Failed to parse PDF date '{}': {}", pdfDate, e.getMessage());
        }
        return null;
    }

    private void mapRating(XmpMetadata xmp, Optional<RawXmpMetadata> rawXmp, String name, String fallbackName, Consumer<Double> setter) {
        Optional<String> val = findCustomField(xmp, rawXmp, name);
        if (val.isEmpty()) val = findCustomField(xmp, rawXmp, fallbackName);
        val.ifPresent(v -> {
            try {
                setter.accept(Double.parseDouble(v));
            } catch (Exception _) {
                // Ignore invalid ratings
            }
        });
    }

    private Optional<String> preferStructuredValue(Optional<String> structured, Optional<String> fallback) {
        return structured.filter(StringUtils::isNotBlank)
                .or(() -> fallback.filter(StringUtils::isNotBlank));
    }

    private Optional<RawXmpMetadata> parseRawXmp(PdfDocument doc) {
        byte[] rawXmp = doc.xmpMetadata();
        if (rawXmp == null || rawXmp.length == 0) {
            return Optional.empty();
        }

        try {
            Document document = SecureXmlUtils.createSecureDocumentBuilder(true)
                    .parse(new ByteArrayInputStream(rawXmp));
            return Optional.of(new RawXmpMetadata(document));
        } catch (Exception e) {
            log.debug("Failed to parse raw XMP metadata for PDF fallback extraction: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private record RawXmpMetadata(Document document) {

        private Optional<String> readFirstText(String namespace, String localName) {
            NodeList nodes = document.getElementsByTagNameNS(namespace, localName);
            for (int index = 0; index < nodes.getLength(); index++) {
                if (nodes.item(index) instanceof Element element) {
                    Optional<String> value = extractScalarValue(element);
                    if (value.isPresent()) {
                        return value;
                    }
                }
            }
            return Optional.empty();
        }

        private List<String> readList(String namespace, String localName) {
            NodeList nodes = document.getElementsByTagNameNS(namespace, localName);
            List<String> values = new ArrayList<>();

            for (int index = 0; index < nodes.getLength(); index++) {
                if (nodes.item(index) instanceof Element element) {
                    values.addAll(extractListValues(element));
                }
            }

            return values.stream()
                    .map(String::trim)
                    .filter(StringUtils::isNotBlank)
                    .distinct()
                    .toList();
        }

        private Optional<String> extractScalarValue(Element element) {
            List<Element> listItems = childElements(element, RDF_NAMESPACE, "li");
            if (!listItems.isEmpty()) {
                for (Element item : listItems) {
                    String lang = item.getAttribute("xml:lang");
                    if ("x-default".equalsIgnoreCase(lang)) {
                        return textValue(item);
                    }
                }
                return textValue(listItems.getFirst());
            }

            List<Element> rdfValues = childElements(element, RDF_NAMESPACE, "value");
            if (!rdfValues.isEmpty()) {
                return textValue(rdfValues.getFirst());
            }

            return textValue(element);
        }

        private List<String> extractListValues(Element element) {
            return childElements(element, RDF_NAMESPACE, "li").stream()
                    .map(RawXmpMetadata::textValue)
                    .flatMap(Optional::stream)
                    .toList();
        }

        private List<Element> childElements(Element parent, String namespace, String localName) {
            List<Element> elements = new ArrayList<>();
            NodeList children = parent.getChildNodes();
            for (int index = 0; index < children.getLength(); index++) {
                Node child = children.item(index);
                if (child instanceof Element element
                        && namespace.equals(element.getNamespaceURI())
                        && localName.equals(element.getLocalName())) {
                    elements.add(element);
                }
            }
            return elements;
        }

        private static Optional<String> textValue(Element element) {
            String text = element.getTextContent();
            if (text == null) {
                return Optional.empty();
            }

            String trimmed = text.trim();
            return trimmed.isEmpty() ? Optional.empty() : Optional.of(trimmed);
        }
    }
}
