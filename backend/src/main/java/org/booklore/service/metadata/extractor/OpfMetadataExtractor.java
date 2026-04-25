package org.booklore.service.metadata.extractor;

import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.BookMetadata;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.StringReader;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
public class OpfMetadataExtractor implements FileMetadataExtractor {

    private static final String DC_NS = "http://purl.org/dc/elements/1.1/";

    @Override
    public BookMetadata extractMetadata(File file) {
        BookMetadata.BookMetadataBuilder builder = BookMetadata.builder();

        if (file == null || !file.isFile()) {
            return builder.build();
        }

        try {
            Document document = parseDocument(file);
            Element root = document.getDocumentElement();

            builder.title(singleValue(root, "title"));
            builder.publisher(singleValue(root, "publisher"));
            builder.publishedDate(parseDate(singleValue(root, "date")));
            builder.description(singleValue(root, "description"));
            builder.language(singleValue(root, "language"));

            List<String> authors = values(root, "creator");
            if (!authors.isEmpty()) {
                builder.authors(authors);
            }

            Set<String> categories = new LinkedHashSet<>(values(root, "subject"));
            if (!categories.isEmpty()) {
                builder.categories(categories);
            }

            applyIdentifiers(root, builder);
            applySeriesMetadata(root, builder);

            return builder.build();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse OPF metadata: " + file.getAbsolutePath(), e);
        }
    }

    @Override
    public byte[] extractCover(File file) {
        return null;
    }

    private Document parseDocument(File file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        setFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        setFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
        setFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
        setFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        setAttribute(factory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
        setAttribute(factory, XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        try (var reader = Files.newBufferedReader(file.toPath())) {
            return builder.parse(new InputSource(reader));
        }
    }

    private void setFeature(DocumentBuilderFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (Exception ignored) {
            log.debug("XML parser feature '{}' is not supported", feature);
        }
    }

    private void setAttribute(DocumentBuilderFactory factory, String name, String value) {
        try {
            factory.setAttribute(name, value);
        } catch (Exception ignored) {
            log.debug("XML parser attribute '{}' is not supported", name);
        }
    }

    private List<String> values(Element root, String localName) {
        NodeList nodes = root.getElementsByTagNameNS(DC_NS, localName);
        List<String> values = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            String value = normalize(nodes.item(i).getTextContent());
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }

    private String singleValue(Element root, String localName) {
        List<String> values = values(root, localName);
        return values.isEmpty() ? null : values.getFirst();
    }

    private void applyIdentifiers(Element root, BookMetadata.BookMetadataBuilder builder) {
        NodeList identifiers = root.getElementsByTagNameNS(DC_NS, "identifier");
        String isbn10 = null;
        String isbn13 = null;
        boolean specific10 = false;
        boolean specific13 = false;

        for (int i = 0; i < identifiers.getLength(); i++) {
            Node node = identifiers.item(i);
            if (!(node instanceof Element identifier)) {
                continue;
            }

            String rawValue = normalize(identifier.getTextContent());
            if (rawValue == null) {
                continue;
            }

            String scheme = normalizeScheme(
                    firstNonBlank(
                            attributeByLocalName(identifier, "scheme"),
                            attributeByLocalName(identifier, "identifier-type"),
                            attributeByLocalName(identifier, "opf:scheme")
                    )
            );

            String candidate = normalizeIsbn(rawValue);
            if (candidate == null) {
                continue;
            }

            if ("isbn13".equals(scheme)) {
                isbn13 = candidate;
                specific13 = true;
                continue;
            }
            if ("isbn10".equals(scheme)) {
                isbn10 = candidate;
                specific10 = true;
                continue;
            }

            boolean genericIsbn = "isbn".equals(scheme) || rawValue.toLowerCase(Locale.ROOT).startsWith("urn:isbn:");
            if (!genericIsbn && candidate.length() != 10 && candidate.length() != 13) {
                continue;
            }

            if (candidate.length() == 13 && !specific13 && isbn13 == null) {
                isbn13 = candidate;
            } else if (candidate.length() == 10 && !specific10 && isbn10 == null) {
                isbn10 = candidate;
            }
        }

        if (isbn10 != null) {
            builder.isbn10(isbn10);
        }
        if (isbn13 != null) {
            builder.isbn13(isbn13);
        }
    }

    private void applySeriesMetadata(Element root, BookMetadata.BookMetadataBuilder builder) {
        NodeList metaNodes = root.getElementsByTagNameNS("*", "meta");
        for (int i = 0; i < metaNodes.getLength(); i++) {
            Node node = metaNodes.item(i);
            if (!(node instanceof Element meta)) {
                continue;
            }

            String name = normalize(attributeByLocalName(meta, "name"));
            String content = normalize(attributeByLocalName(meta, "content"));
            if (name == null || content == null) {
                continue;
            }

            if ("calibre:series".equalsIgnoreCase(name)) {
                builder.seriesName(content);
            } else if ("calibre:series_index".equalsIgnoreCase(name)) {
                try {
                    builder.seriesNumber(Float.parseFloat(content));
                } catch (NumberFormatException ignored) {
                    log.debug("Ignoring invalid calibre series index '{}'", content);
                }
            }
        }
    }

    private String attributeByLocalName(Element element, String localName) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            String candidateLocalName = attribute.getLocalName();
            String candidateNodeName = attribute.getNodeName();
            if (localName.equalsIgnoreCase(candidateLocalName) || localName.equalsIgnoreCase(candidateNodeName)) {
                return attribute.getNodeValue();
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = normalize(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String normalizeScheme(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeIsbn(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        if (cleaned.toLowerCase(Locale.ROOT).startsWith("urn:isbn:")) {
            cleaned = cleaned.substring("urn:isbn:".length());
        }
        cleaned = cleaned.replaceAll("[^0-9Xx]", "");
        if (cleaned.isBlank()) {
            return null;
        }
        if (cleaned.length() == 10) {
            return cleaned.substring(0, 9) + cleaned.substring(9).toUpperCase(Locale.ROOT);
        }
        return cleaned;
    }

    private LocalDate parseDate(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.matches("^\\d{4}$")) {
            return LocalDate.of(Integer.parseInt(trimmed), 1, 1);
        }
        if (trimmed.matches("^\\d{4}-\\d{2}$")) {
            return LocalDate.parse(trimmed + "-01", DateTimeFormatter.ISO_LOCAL_DATE);
        }
        if (trimmed.length() >= 10) {
            String datePrefix = trimmed.substring(0, 10);
            try {
                return LocalDate.parse(datePrefix, DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException ignored) {
                // Fall through to full parser attempts below.
            }
        }

        try {
            return LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(trimmed, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return ZonedDateTime.parse(trimmed, DateTimeFormatter.ISO_ZONED_DATE_TIME).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }

        log.debug("Ignoring unsupported OPF date '{}'", value);
        return null;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().replaceAll("\\s+", " ");
        return trimmed.isEmpty() ? null : trimmed;
    }
}
