package org.booklore.service.metadata.extractor;

import org.booklore.model.dto.BookMetadata;
import org.booklore.util.SecureXmlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

@Slf4j
@Component
public class Fb2MetadataExtractor implements FileMetadataExtractor {

    private static final String FB2_NAMESPACE = "http://www.gribuser.ru/xml/fictionbook/2.0";
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\d{4}");
    private static final Pattern ISBN_PATTERN = Pattern.compile("\\d{9}[\\dXx]");
    private static final Pattern KEYWORD_SEPARATOR_PATTERN = Pattern.compile("[,;]");
    private static final Pattern ISBN_CLEANER_PATTERN = Pattern.compile("[^0-9Xx]");
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    @Override
    public byte[] extractCover(File file) {
        try (InputStream inputStream = getInputStream(file)) {
            DocumentBuilder builder = SecureXmlUtils.createSecureDocumentBuilder(true);
            Document doc = builder.parse(inputStream);

            // Look for cover image in binary elements
            NodeList binaries = doc.getElementsByTagNameNS(FB2_NAMESPACE, "binary");
            for (int i = 0; i < binaries.getLength(); i++) {
                Element binary = (Element) binaries.item(i);
                String id = binary.getAttribute("id");

                if (id != null && id.toLowerCase().contains("cover")) {
                    String contentType = binary.getAttribute("content-type");
                    if (contentType != null && contentType.startsWith("image/")) {
                        String base64Data = binary.getTextContent().trim();
                        return Base64.getDecoder().decode(base64Data);
                    }
                }
            }

            // If no cover found by name, try to find the first referenced image in title-info
            Element titleInfo = getFirstElementByTagNameNS(doc, FB2_NAMESPACE, "title-info");
            if (titleInfo != null) {
                NodeList coverPages = titleInfo.getElementsByTagNameNS(FB2_NAMESPACE, "coverpage");
                if (coverPages.getLength() > 0) {
                    Element coverPage = (Element) coverPages.item(0);
                    NodeList images = coverPage.getElementsByTagNameNS(FB2_NAMESPACE, "image");
                    if (images.getLength() > 0) {
                        Element image = (Element) images.item(0);
                        String href = image.getAttributeNS("http://www.w3.org/1999/xlink", "href");
                        if (href != null && href.startsWith("#")) {
                            String imageId = href.substring(1);
                            // Find the binary with this ID
                            for (int i = 0; i < binaries.getLength(); i++) {
                                Element binary = (Element) binaries.item(i);
                                if (imageId.equals(binary.getAttribute("id"))) {
                                    String base64Data = binary.getTextContent().trim();
                                    return Base64.getDecoder().decode(base64Data);
                                }
                            }
                        }
                    }
                }
            }

            return null;
        } catch (Exception e) {
            log.warn("Failed to extract cover from FB2: {}", file.getName(), e);
            return null;
        }
    }

    @Override
    public BookMetadata extractMetadata(File file) {
        try (InputStream inputStream = getInputStream(file)) {
            DocumentBuilder builder = SecureXmlUtils.createSecureDocumentBuilder(true);
            Document doc = builder.parse(inputStream);

            BookMetadata.BookMetadataBuilder metadataBuilder = BookMetadata.builder();
            List<String> authors = new ArrayList<>();
            Set<String> categories = new HashSet<>();

            // Extract title-info (main metadata section)
            Element titleInfo = getFirstElementByTagNameNS(doc, FB2_NAMESPACE, "title-info");
            if (titleInfo != null) {
                extractTitleInfo(titleInfo, metadataBuilder, authors, categories);
            }

            // Extract publish-info (publisher, year, ISBN)
            Element publishInfo = getFirstElementByTagNameNS(doc, FB2_NAMESPACE, "publish-info");
            if (publishInfo != null) {
                extractPublishInfo(publishInfo, metadataBuilder);
            }

            // Extract document-info (optional metadata)
            Element documentInfo = getFirstElementByTagNameNS(doc, FB2_NAMESPACE, "document-info");
            if (documentInfo != null) {
                extractDocumentInfo(documentInfo, metadataBuilder);
            }

            metadataBuilder.authors(authors);
            metadataBuilder.categories(categories);

            return metadataBuilder.build();
        } catch (Exception e) {
            log.warn("Failed to extract metadata from FB2: {}", file.getName(), e);
            return null;
        }
    }

    private void extractTitleInfo(Element titleInfo, BookMetadata.BookMetadataBuilder builder,
                                   List<String> authors, Set<String> categories) {
        // Extract genres (categories)
        NodeList genres = titleInfo.getElementsByTagNameNS(FB2_NAMESPACE, "genre");
        for (int i = 0; i < genres.getLength(); i++) {
            String genre = genres.item(i).getTextContent().trim();
            if (StringUtils.isNotBlank(genre)) {
                categories.add(genre);
            }
        }

        // Extract authors
        NodeList authorNodes = titleInfo.getElementsByTagNameNS(FB2_NAMESPACE, "author");
        for (int i = 0; i < authorNodes.getLength(); i++) {
            Element author = (Element) authorNodes.item(i);
            String authorName = extractPersonName(author);
            if (StringUtils.isNotBlank(authorName)) {
                authors.add(authorName);
            }
        }

        // Extract book title
        Element bookTitle = getFirstElementByTagNameNS(titleInfo, FB2_NAMESPACE, "book-title");
        if (bookTitle != null) {
            builder.title(bookTitle.getTextContent().trim());
        }

        // Extract annotation (description)
        Element annotation = getFirstElementByTagNameNS(titleInfo, FB2_NAMESPACE, "annotation");
        if (annotation != null) {
            String description = extractTextFromElement(annotation);
            if (StringUtils.isNotBlank(description)) {
                builder.description(description);
            }
        }

        // Extract keywords (additional categories/tags)
        Element keywords = getFirstElementByTagNameNS(titleInfo, FB2_NAMESPACE, "keywords");
        if (keywords != null) {
            String keywordsText = keywords.getTextContent().trim();
            if (StringUtils.isNotBlank(keywordsText)) {
                for (String keyword : KEYWORD_SEPARATOR_PATTERN.split(keywordsText)) {
                    String trimmed = keyword.trim();
                    if (StringUtils.isNotBlank(trimmed)) {
                        categories.add(trimmed);
                    }
                }
            }
        }

        // Extract date
        Element date = getFirstElementByTagNameNS(titleInfo, FB2_NAMESPACE, "date");
        if (date != null) {
            String dateValue = date.getAttribute("value");
            if (StringUtils.isBlank(dateValue)) {
                dateValue = date.getTextContent().trim();
            }
            LocalDate publishedDate = parseDate(dateValue);
            if (publishedDate != null) {
                builder.publishedDate(publishedDate);
            }
        }

        // Extract language
        Element lang = getFirstElementByTagNameNS(titleInfo, FB2_NAMESPACE, "lang");
        if (lang != null) {
            builder.language(lang.getTextContent().trim());
        }

        // Extract sequence (series information)
        Element sequence = getFirstElementByTagNameNS(titleInfo, FB2_NAMESPACE, "sequence");
        if (sequence != null) {
            String seriesName = sequence.getAttribute("name");
            if (StringUtils.isNotBlank(seriesName)) {
                builder.seriesName(seriesName.trim());
            }
            String seriesNumber = sequence.getAttribute("number");
            if (StringUtils.isNotBlank(seriesNumber)) {
                try {
                    builder.seriesNumber(Float.parseFloat(seriesNumber));
                } catch (NumberFormatException e) {
                    log.debug("Failed to parse series number: {}", seriesNumber);
                }
            }
        }
    }

    private void extractPublishInfo(Element publishInfo, BookMetadata.BookMetadataBuilder builder) {
        // Extract publisher
        Element publisher = getFirstElementByTagNameNS(publishInfo, FB2_NAMESPACE, "publisher");
        if (publisher != null) {
            builder.publisher(publisher.getTextContent().trim());
        }

        // Extract publication year
        Element year = getFirstElementByTagNameNS(publishInfo, FB2_NAMESPACE, "year");
        if (year != null) {
            String yearText = year.getTextContent().trim();
            Matcher matcher = YEAR_PATTERN.matcher(yearText);
            if (matcher.find()) {
                try {
                    int yearValue = Integer.parseInt(matcher.group());
                    builder.publishedDate(LocalDate.of(yearValue, 1, 1));
                } catch (NumberFormatException e) {
                    log.debug("Failed to parse year: {}", yearText);
                }
            }
        }

        // Extract ISBN
        Element isbn = getFirstElementByTagNameNS(publishInfo, FB2_NAMESPACE, "isbn");
        if (isbn != null) {
            String isbnText = ISBN_CLEANER_PATTERN.matcher(isbn.getTextContent().trim()).replaceAll("");
            if (isbnText.length() == 13) {
                builder.isbn13(isbnText);
            } else if (isbnText.length() == 10) {
                builder.isbn10(isbnText);
            } else if (ISBN_PATTERN.matcher(isbnText).find()) {
                // Extract the first valid ISBN pattern found
                Matcher matcher = ISBN_PATTERN.matcher(isbnText);
                if (matcher.find()) {
                    builder.isbn10(matcher.group());
                }
            }
        }
    }

    private void extractDocumentInfo(Element documentInfo, BookMetadata.BookMetadataBuilder builder) {
        // Extract document ID (can be used as an identifier)
        Element id = getFirstElementByTagNameNS(documentInfo, FB2_NAMESPACE, "id");
        if (id != null) {
            // Could potentially map this to a custom identifier field if needed
            log.debug("FB2 document ID: {}", id.getTextContent().trim());
        }
    }

    private String extractPersonName(Element personElement) {
        Element firstName = getFirstElementByTagNameNS(personElement, FB2_NAMESPACE, "first-name");
        Element middleName = getFirstElementByTagNameNS(personElement, FB2_NAMESPACE, "middle-name");
        Element lastName = getFirstElementByTagNameNS(personElement, FB2_NAMESPACE, "last-name");
        Element nickname = getFirstElementByTagNameNS(personElement, FB2_NAMESPACE, "nickname");

        StringBuilder name = new StringBuilder(64);

        if (firstName != null) {
            name.append(firstName.getTextContent().trim());
        }
        if (middleName != null) {
            if (!name.isEmpty()) name.append(" ");
            name.append(middleName.getTextContent().trim());
        }
        if (lastName != null) {
            if (!name.isEmpty()) name.append(" ");
            name.append(lastName.getTextContent().trim());
        }

        // If no name parts found, try nickname
        if (name.isEmpty() && nickname != null) {
            name.append(nickname.getTextContent().trim());
        }

        return name.toString();
    }

    private String extractTextFromElement(Element element) {
        StringBuilder text = new StringBuilder();
        NodeList children = element.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE) {
                text.append(child.getTextContent().trim()).append(" ");
            } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if ("p".equals(childElement.getLocalName())) {
                    text.append(childElement.getTextContent().trim()).append("\n\n");
                } else {
                    text.append(extractTextFromElement(childElement));
                }
            }
        }

        return text.toString().trim();
    }

    private LocalDate parseDate(String dateString) {
        if (StringUtils.isBlank(dateString)) {
            return null;
        }

        try {
            // Try parsing ISO date format (YYYY-MM-DD)
            if (ISO_DATE_PATTERN.matcher(dateString).matches()) {
                return LocalDate.parse(dateString);
            }

            // Try extracting year only
            Matcher matcher = YEAR_PATTERN.matcher(dateString);
            if (matcher.find()) {
                int year = Integer.parseInt(matcher.group());
                return LocalDate.of(year, 1, 1);
            }
        } catch (Exception e) {
            log.debug("Failed to parse date: {}", dateString, e);
        }

        return null;
    }

    private Element getFirstElementByTagNameNS(Node parent, String namespace, String localName) {
        NodeList nodes;
        if (parent instanceof Document document) {
            nodes = document.getElementsByTagNameNS(namespace, localName);
        } else if (parent instanceof Element element) {
            nodes = element.getElementsByTagNameNS(namespace, localName);
        } else {
            return null;
        }
        return nodes.getLength() > 0 ? (Element) nodes.item(0) : null;
    }

    private InputStream getInputStream(File file) throws Exception {
        FileInputStream fis = new FileInputStream(file);
        if (file.getName().toLowerCase().endsWith(".gz")) {
            try {
                return new GZIPInputStream(fis);
            } catch (Exception e) {
                fis.close();
                throw e;
            }
        }
        return fis;
    }
}
