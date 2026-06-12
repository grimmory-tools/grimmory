package org.booklore.opf;

import org.apache.commons.lang3.StringUtils;
import org.booklore.model.dto.BookMetadata;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Component
public class OpfMetadataExtractor {

    public Optional<BookMetadata> extract(Path opfPath) {
        try {
            String xml = Files.readString(opfPath, StandardCharsets.UTF_8);
            var document = OpfXmlParser.parse(xml);
            Element root = document.getDocumentElement();
            if (root == null) {
                return Optional.empty();
            }

            BookMetadata.BookMetadataBuilder builder = BookMetadata.builder();
            text(root, "title").ifPresent(builder::title);
            text(root, "publisher").ifPresent(builder::publisher);
            text(root, "description").ifPresent(builder::description);
            text(root, "language").ifPresent(builder::language);
            text(root, "date").flatMap(this::parseDate).ifPresent(builder::publishedDate);

            var authors = texts(root, "creator");
            if (!authors.isEmpty()) {
                builder.authors(authors);
            }

            Set<String> categories = new LinkedHashSet<>(texts(root, "subject"));
            if (!categories.isEmpty()) {
                builder.categories(categories);
            }

            extractIdentifiers(root, builder);
            extractSeries(root, builder);

            BookMetadata metadata = builder.build();
            return hasAnyAllowedField(metadata) ? Optional.of(metadata) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<String> text(Element root, String localName) {
        return texts(root, localName).stream().findFirst();
    }

    private ArrayList<String> texts(Element root, String localName) {
        var result = new ArrayList<String>();
        var nodes = root.getElementsByTagNameNS("*", localName);
        for (int i = 0; i < nodes.getLength(); i++) {
            String value = normalize(nodes.item(i).getTextContent());
            if (StringUtils.isNotBlank(value)) {
                result.add(value);
            }
        }
        return result;
    }

    private void extractIdentifiers(Element root, BookMetadata.BookMetadataBuilder builder) {
        var nodes = root.getElementsByTagNameNS("*", "identifier");
        for (int i = 0; i < nodes.getLength(); i++) {
            if (!(nodes.item(i) instanceof Element element)) {
                continue;
            }
            String scheme = normalize(element.getAttribute("opf:scheme"));
            if (StringUtils.isBlank(scheme)) {
                scheme = normalize(element.getAttribute("scheme"));
            }
            String value = cleanIsbn(element.getTextContent());
            if (StringUtils.isBlank(value)) {
                continue;
            }
            boolean isbnScheme = "isbn".equalsIgnoreCase(scheme) || value.length() == 10 || value.length() == 13;
            if (!isbnScheme) {
                continue;
            }
            if (value.length() == 13) {
                builder.isbn13(value);
            } else if (value.length() == 10) {
                builder.isbn10(value);
            }
        }
    }

    private void extractSeries(Element root, BookMetadata.BookMetadataBuilder builder) {
        var nodes = root.getElementsByTagNameNS("*", "meta");
        for (int i = 0; i < nodes.getLength(); i++) {
            if (!(nodes.item(i) instanceof Element element)) {
                continue;
            }
            String name = normalize(element.getAttribute("name")).toLowerCase(Locale.ROOT);
            String property = normalize(element.getAttribute("property")).toLowerCase(Locale.ROOT);
            String content = normalize(element.getAttribute("content"));
            if (StringUtils.isBlank(content)) {
                content = normalize(element.getTextContent());
            }

            if ("calibre:series".equals(name) || "belongs-to-collection".equals(property)) {
                if (StringUtils.isNotBlank(content)) {
                    builder.seriesName(content);
                }
            }
            if ("calibre:series_index".equals(name) || "group-position".equals(property)) {
                parseFloat(content).ifPresent(builder::seriesNumber);
            }
        }
    }

    private Optional<LocalDate> parseDate(String value) {
        String normalized = normalize(value);
        if (normalized.matches("^\\d{4}$")) {
            return Optional.of(LocalDate.of(Integer.parseInt(normalized), 1, 1));
        }
        if (normalized.matches("^\\d{4}-\\d{2}-\\d{2}.*")) {
            try {
                return Optional.of(LocalDate.parse(normalized.substring(0, 10)));
            } catch (Exception e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private Optional<Float> parseFloat(String value) {
        try {
            return StringUtils.isNotBlank(value) ? Optional.of(Float.parseFloat(value.trim())) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private String cleanIsbn(String value) {
        return normalize(value).replace("-", "").replace(" ", "");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasAnyAllowedField(BookMetadata metadata) {
        return StringUtils.isNotBlank(metadata.getTitle())
                || metadata.getAuthors() != null && !metadata.getAuthors().isEmpty()
                || StringUtils.isNotBlank(metadata.getPublisher())
                || metadata.getPublishedDate() != null
                || StringUtils.isNotBlank(metadata.getDescription())
                || StringUtils.isNotBlank(metadata.getLanguage())
                || metadata.getCategories() != null && !metadata.getCategories().isEmpty()
                || StringUtils.isNotBlank(metadata.getIsbn10())
                || StringUtils.isNotBlank(metadata.getIsbn13())
                || StringUtils.isNotBlank(metadata.getSeriesName())
                || metadata.getSeriesNumber() != null;
    }
}
