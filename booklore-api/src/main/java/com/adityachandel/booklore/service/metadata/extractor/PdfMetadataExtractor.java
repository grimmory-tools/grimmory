package com.adityachandel.booklore.service.metadata.extractor;

import com.adityachandel.booklore.model.dto.BookMetadata;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.springframework.messaging.rsocket.MetadataExtractor;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class PdfMetadataExtractor implements FileMetadataExtractor {

    public BookMetadata extractMetadata(File file) {
        if (!file.exists() || !file.isFile()) {
            log.warn("File does not exist or is not a file: {}", file.getPath());
            return BookMetadata.builder().build();
        }

        BookMetadata.BookMetadataBuilder metadataBuilder = BookMetadata.builder();

        try (PDDocument pdf = Loader.loadPDF(file)) {
            PDDocumentInformation info = pdf.getDocumentInformation();

            if (info != null) {
                if (StringUtils.isNotBlank(info.getTitle())) {
                    metadataBuilder.title(info.getTitle());
                } else {
                    metadataBuilder.title(file.getName());
                }

                if (StringUtils.isNotBlank(info.getAuthor())) {
                    Set<String> authors = parseAuthors(info.getAuthor());
                    if (!authors.isEmpty()) {
                        metadataBuilder.authors(authors);
                    }
                }

                if (StringUtils.isNotBlank(info.getSubject())) {
                    metadataBuilder.description(info.getSubject());
                }

                COSDictionary cosDict = info.getCOSObject();
                if (cosDict != null && cosDict.containsKey(COSName.getPDFName("EBX_PUBLISHER"))) {
                    String ebxPublisher = cosDict.getString(COSName.getPDFName("EBX_PUBLISHER"));
                    if (StringUtils.isNotBlank(ebxPublisher)) {
                        metadataBuilder.publisher(ebxPublisher);
                    }
                }

                if (info.getCreationDate() != null) {
                    LocalDate createdDate = convertCalendarToLocalDate(info.getCreationDate());
                    if (createdDate != null) {
                        metadataBuilder.publishedDate(createdDate);
                    }
                }

                if (StringUtils.isNotBlank(info.getKeywords())) {
                    Set<String> categories = Arrays.stream(info.getKeywords().split(","))
                            .map(String::trim)
                            .filter(StringUtils::isNotBlank)
                            .collect(Collectors.toSet());
                    if (!categories.isEmpty()) {
                        metadataBuilder.categories(categories);
                    }
                }

                String languageValue = info.getCustomMetadataValue("Language");
                if (StringUtils.isNotBlank(languageValue)) {
                    metadataBuilder.language(languageValue);
                }
            }

            PDMetadata metadata = pdf.getDocumentCatalog().getMetadata();

            if (metadata != null) {
                try (InputStream is = metadata.createInputStream()) {
                    if (is == null) {
                        log.warn("PDMetadata InputStream is null");
                    } else {
                        String rawXmp = IOUtils.toString(is, StandardCharsets.UTF_8);
                        if (StringUtils.isNotBlank(rawXmp)) {
                            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                            dbFactory.setNamespaceAware(true);
                            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                            Document doc = dBuilder.parse(new ByteArrayInputStream(rawXmp.getBytes(StandardCharsets.UTF_8)));

                            XPathFactory xPathfactory = XPathFactory.newInstance();
                            XPath xpath = xPathfactory.newXPath();
                            xpath.setNamespaceContext(new XmpNamespaceContext());

                            extractDublinCoreMetadata(xpath, doc, metadataBuilder);
                            extractCalibreMetadata(xpath, doc, metadataBuilder);

                            Map<String, String> identifiers = extractIdentifiers(xpath, doc);
                            if (!identifiers.isEmpty()) {
                                String isbn = identifiers.get("isbn");
                                if (StringUtils.isNotBlank(isbn)) {
                                    isbn = isbn.replaceAll("[^0-9Xx]", "");
                                    if (isbn.length() == 10) {
                                        metadataBuilder.isbn10(isbn);
                                    } else if (isbn.length() == 13) {
                                        metadataBuilder.isbn13(isbn);
                                    } else {
                                        metadataBuilder.isbn13(isbn);
                                        log.warn("ISBN length not 10 or 13: {}", isbn);
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

                                String hardcover = identifiers.get("hardcover");
                                if (StringUtils.isNotBlank(hardcover)) {
                                    metadataBuilder.hardcoverId(hardcover);
                                }
                            }
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

        Set<String> creators = xpathEvaluateMultiple(xpath, doc, "//dc:creator/rdf:Seq/rdf:li/text()");
        if (!creators.isEmpty()) {
            builder.authors(creators);
        }

        Set<String> subjects = xpathEvaluateMultiple(xpath, doc, "//dc:subject/rdf:Bag/rdf:li/text()");
        if (!subjects.isEmpty()) {
            builder.categories(subjects);
        }
    }

    private void extractCalibreMetadata(XPath xpath, Document doc, BookMetadata.BookMetadataBuilder builder) {
        try {
            String rating = xpath.evaluate("//calibre:rating/text()", doc);
            if (StringUtils.isNotBlank(rating)) {
                builder.personalRating(Double.valueOf(rating));
            }

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

    private Set<String> parseAuthors(String authorString) {
        if (authorString == null) return Collections.emptySet();
        return Arrays.stream(authorString.split("[,&]"))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
    }

    private LocalDate convertCalendarToLocalDate(Calendar calendar) {
        if (calendar == null) return null;
        return calendar.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private String xpathEvaluate(XPath xpath, Document doc, String expression) throws XPathExpressionException {
        String result = xpath.evaluate(expression, doc);
        return result == null ? "" : result.trim();
    }

    private String xpathEvaluate(XPath xpath, org.w3c.dom.Node node, String expression) throws XPathExpressionException {
        String result = xpath.evaluate(expression, node);
        return result == null ? "" : result.trim();
    }

    private Set<String> xpathEvaluateMultiple(XPath xpath, Document doc, String expression) throws XPathExpressionException {
        NodeList nodes = (NodeList) xpath.evaluate(expression, doc, XPathConstants.NODESET);
        Set<String> results = new HashSet<>();
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