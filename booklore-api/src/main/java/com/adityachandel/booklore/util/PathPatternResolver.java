package com.adityachandel.booklore.util;

import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.entity.AuthorEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@UtilityClass
@Slf4j
public class PathPatternResolver {

    private final int MAX_COMPONENT_BYTES = 200;
    private final int MAX_FILESYSTEM_COMPONENT_BYTES = 245; // Left 10 bytes buffer
    private final int MAX_AUTHOR_BYTES = 180;

    private final String TRUNCATION_SUFFIX = " et al.";
    private final int SUFFIX_BYTES = TRUNCATION_SUFFIX.getBytes(StandardCharsets.UTF_8).length;

    private final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private final Pattern CONTROL_CHARACTER_PATTERN = Pattern.compile("\\p{Cntrl}");
    private final Pattern INVALID_CHARS_PATTERN = Pattern.compile("[\\\\/:*?\"<>|]");
    private final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{(.*?)}");
    private final Pattern COMMA_SPACE_PATTERN = Pattern.compile(", ");
    private final Pattern SLASH_PATTERN = Pattern.compile("/");

    public String resolvePattern(BookEntity book, String pattern) {
        String currentFilename = book.getFileName() != null ? book.getFileName().trim() : "";
        return resolvePattern(book.getMetadata(), pattern, currentFilename);
    }

    public String resolvePattern(BookMetadata metadata, String pattern, String filename) {
        MetadataProvider metadataProvider = MetadataProvider.from(metadata);
        return resolvePattern(metadataProvider, pattern, filename);
    }

    public String resolvePattern(BookMetadataEntity metadata, String pattern, String filename) {
        MetadataProvider metadataProvider = MetadataProvider.from(metadata);
        return resolvePattern(metadataProvider, pattern, filename);
    }

    private String resolvePattern(MetadataProvider metadata, String pattern, String filename) {
        if (pattern == null || pattern.isBlank()) {
            return filename;
        }

        String filenameBase = "Untitled";
        if (filename != null && !filename.isBlank()) {
            int lastDot = filename.lastIndexOf('.');
            if (lastDot > 0) {
                filenameBase = filename.substring(0, lastDot);
            } else {
                filenameBase = filename;
            }
        }

        String title = sanitize(metadata != null && metadata.getTitle() != null
                ? metadata.getTitle()
                : filenameBase);

        String subtitle = sanitize(metadata != null ? metadata.getSubtitle() : "");

        String authors = sanitize(
                metadata != null
                        ? truncateAuthorsForFilesystem(String.join(", ", metadata.getAuthors()))
                        : ""
        );
        String year = sanitize(
                metadata != null && metadata.getPublishedDate() != null
                        ? String.valueOf(metadata.getPublishedDate().getYear())
                        : ""
        );
        String series = sanitize(metadata != null ? metadata.getSeriesName() : "");
        String seriesIndex = "";
        if (metadata != null && metadata.getSeriesNumber() != null) {
            Float seriesNumber = metadata.getSeriesNumber();
            seriesIndex = (seriesNumber % 1 == 0)
                    ? String.valueOf(seriesNumber.intValue())
                    : seriesNumber.toString();
            seriesIndex = sanitize(seriesIndex);
        }
        String language = sanitize(metadata != null ? metadata.getLanguage() : "");
        String publisher = sanitize(metadata != null ? metadata.getPublisher() : "");
        String isbn = sanitize(
                metadata != null
                        ? (metadata.getIsbn13() != null
                        ? metadata.getIsbn13()
                        : metadata.getIsbn10() != null
                        ? metadata.getIsbn10()
                        : "")
                        : ""
        );

        Map<String, String> values = new LinkedHashMap<>();
        values.put("authors", authors);
        values.put("title", truncatePathComponent(title, MAX_COMPONENT_BYTES));
        values.put("subtitle", truncatePathComponent(subtitle, MAX_COMPONENT_BYTES));
        values.put("year", year);
        values.put("series", truncatePathComponent(series, MAX_COMPONENT_BYTES));
        values.put("seriesIndex", seriesIndex);
        values.put("language", language);
        values.put("publisher", truncatePathComponent(publisher, MAX_COMPONENT_BYTES));
        values.put("isbn", isbn);
        values.put("currentFilename", filename);

        return resolvePatternWithValues(pattern, values, filename);
    }

    private String resolvePatternWithValues(String pattern, Map<String, String> values, String currentFilename) {
        String extension = "";
        int lastDot = currentFilename.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < currentFilename.length() - 1) {
            extension = sanitize(currentFilename.substring(lastDot + 1));  // e.g. "epub"
        }

        values.put("extension", extension);

        // Handle optional blocks enclosed in <...>
        Pattern optionalBlockPattern = Pattern.compile("<([^<>]*)>");
        Matcher matcher = optionalBlockPattern.matcher(pattern);
        StringBuilder resolved = new StringBuilder();

        while (matcher.find()) {
            String block = matcher.group(1);
            Matcher placeholderMatcher = PLACEHOLDER_PATTERN.matcher(block);
            boolean allHaveValues = true;

            // Check if all placeholders inside optional block have non-blank values
            while (placeholderMatcher.find()) {
                String key = placeholderMatcher.group(1);
                String value = values.getOrDefault(key, "");
                if (value.isBlank()) {
                    allHaveValues = false;
                    break;
                }
            }

            if (allHaveValues) {
                String resolvedBlock = block;
                for (Map.Entry<String, String> entry : values.entrySet()) {
                    resolvedBlock = resolvedBlock.replace("{" + entry.getKey() + "}", entry.getValue());
                }
                matcher.appendReplacement(resolved, Matcher.quoteReplacement(resolvedBlock));
            } else {
                matcher.appendReplacement(resolved, "");
            }
        }
        matcher.appendTail(resolved);

        String result = resolved.toString();

        // Replace known placeholders with values, preserve unknown ones
        Matcher placeholderMatcher = PLACEHOLDER_PATTERN.matcher(result);
        StringBuilder finalResult = new StringBuilder();

        while (placeholderMatcher.find()) {
            String key = placeholderMatcher.group(1);
            if (values.containsKey(key)) {
                String replacement = values.get(key);
                placeholderMatcher.appendReplacement(finalResult, Matcher.quoteReplacement(replacement));
            } else {
                // Preserve unknown placeholders (e.g., {foo})
                placeholderMatcher.appendReplacement(finalResult, Matcher.quoteReplacement("{" + key + "}"));
            }
        }
        placeholderMatcher.appendTail(finalResult);

        result = finalResult.toString();

        boolean usedFallbackFilename = false;
        if (result.isBlank()) {
            result = values.getOrDefault("currentFilename", "untitled");
            usedFallbackFilename = true;
        }

        boolean patternIncludesExtension = pattern.contains("{extension}");
        boolean patternIncludesFullFilename = pattern.contains("{currentFilename}");
        
        if (!usedFallbackFilename && !patternIncludesExtension && !patternIncludesFullFilename && !extension.isBlank()) {
            result += "." + extension;
        }

        return validateFinalPath(result);
    }

    private String sanitize(String input) {
        if (input == null) return "";
        return WHITESPACE_PATTERN.matcher(CONTROL_CHARACTER_PATTERN.matcher(INVALID_CHARS_PATTERN.matcher(input).replaceAll("")).replaceAll("")).replaceAll(" ")
                .trim();
    }

    private String truncateAuthorsForFilesystem(String authors) {
        if (authors == null || authors.isEmpty()) {
            return authors;
        }

        byte[] originalBytes = authors.getBytes(StandardCharsets.UTF_8);
        if (originalBytes.length <= MAX_AUTHOR_BYTES) {
            return authors;
        }

        String[] authorArray = COMMA_SPACE_PATTERN.split(authors);
        StringBuilder result = new StringBuilder();
        int currentBytes = 0;
        int truncationLimit = MAX_AUTHOR_BYTES - SUFFIX_BYTES;

        for (int i = 0; i < authorArray.length; i++) {
            String author = authorArray[i];

            int separatorBytes = (i > 0) ? 2 : 0;
            int authorBytes = author.getBytes(StandardCharsets.UTF_8).length;

            if (currentBytes + separatorBytes + authorBytes > MAX_AUTHOR_BYTES) {
                if (result.isEmpty()) {
                     return truncatePathComponent(author, truncationLimit) + TRUNCATION_SUFFIX;
                }
                return result + TRUNCATION_SUFFIX;
            }

            if (i > 0) {
                result.append(", ");
                currentBytes += 2;
            }
            result.append(author);
            currentBytes += authorBytes;
        }

        return result.toString();
    }

    private String truncatePathComponent(String component, int maxBytes) {
        if (component == null || component.isEmpty()) {
            return component;
        }

        byte[] bytes = component.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return component;
        }

        CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder();
        ByteBuffer buffer = ByteBuffer.allocate(maxBytes);
        CharBuffer charBuffer = CharBuffer.wrap(component);

        encoder.encode(charBuffer, buffer, true);

        String truncated = component.substring(0, charBuffer.position());
        if (!truncated.equals(component)) {
            log.debug("Truncated path component from {} to {} bytes for filesystem safety",
                bytes.length, truncated.getBytes(StandardCharsets.UTF_8).length);
        }
        return truncated;
    }


    private String validateFinalPath(String path) {
        String[] components = SLASH_PATTERN.split(path);
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < components.length; i++) {
            String component = components[i];
            boolean isLastComponent = (i == components.length - 1);

            if (isLastComponent && component.contains(".")) {
                component = truncateFilenameWithExtension(component);
            } else {
                if (component.getBytes(StandardCharsets.UTF_8).length > MAX_FILESYSTEM_COMPONENT_BYTES) {
                    component = truncatePathComponent(component, MAX_FILESYSTEM_COMPONENT_BYTES);
                }
                while (component.endsWith(".")) {
                    component = component.substring(0, component.length() - 1);
                }
            }

            if (i > 0) result.append("/");
            result.append(component);
        }
        return result.toString();
    }

    private String truncateFilenameWithExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == 0) {
            // No extension or dot is at start (hidden file), treat as normal component
            if (filename.getBytes(StandardCharsets.UTF_8).length > MAX_FILESYSTEM_COMPONENT_BYTES) {
                return truncatePathComponent(filename, MAX_FILESYSTEM_COMPONENT_BYTES);
            }
            return filename;
        }

        String extension = filename.substring(lastDotIndex); // includes dot
        String name = filename.substring(0, lastDotIndex);

        int extBytes = extension.getBytes(StandardCharsets.UTF_8).length;

        if (extBytes > 50) {
            log.warn("Unusually long extension detected: {}", extension);
            if (filename.getBytes(StandardCharsets.UTF_8).length > MAX_FILESYSTEM_COMPONENT_BYTES) {
                 return truncatePathComponent(filename, MAX_FILESYSTEM_COMPONENT_BYTES);
            }
            return filename;
        }

        int maxNameBytes = MAX_FILESYSTEM_COMPONENT_BYTES - extBytes;

        if (name.getBytes(StandardCharsets.UTF_8).length > maxNameBytes) {
            String truncatedName = truncatePathComponent(name, maxNameBytes);
            return truncatedName + extension;
        }

        return filename;
    }

    private interface MetadataProvider {
        String getTitle();

        String getSubtitle();

        List<String> getAuthors();

        Integer getYear();

        String getSeriesName();

        Float getSeriesNumber();

        String getLanguage();

        String getPublisher();

        String getIsbn13();

        String getIsbn10();

        LocalDate getPublishedDate();

        static BookMetadataProvider from(BookMetadata metadata) {
            if (metadata == null) {
                return null;
            }

            return new BookMetadataProvider(metadata);
        }

        static BookMetadataEntityProvider from(BookMetadataEntity metadata) {
            if (metadata == null) {
                return null;
            }

            return new BookMetadataEntityProvider(metadata);
        }
    }

    private record BookMetadataProvider(BookMetadata metadata) implements MetadataProvider {

        @Override
        public String getTitle() {
            return metadata.getTitle();
        }

        @Override
        public String getSubtitle() {
            return metadata.getSubtitle();
        }

        @Override
        public List<String> getAuthors() {
            return metadata.getAuthors() != null ? metadata.getAuthors().stream().toList() : Collections.emptyList();
        }

        @Override
        public Integer getYear() {
            return metadata.getPublishedDate() != null ? metadata.getPublishedDate().getYear() : null;
        }

        @Override
        public String getSeriesName() {
            return metadata.getSeriesName();
        }

        @Override
        public Float getSeriesNumber() {
            return metadata.getSeriesNumber();
        }

        @Override
        public String getLanguage() {
            return metadata.getLanguage();
        }

        @Override
        public String getPublisher() {
            return metadata.getPublisher();
        }

        @Override
        public String getIsbn13() {
            return metadata.getIsbn13();
        }

        @Override
        public String getIsbn10() {
            return metadata.getIsbn10();
        }

        @Override
        public LocalDate getPublishedDate() {
            return metadata.getPublishedDate();
        }
    }

    private record BookMetadataEntityProvider(BookMetadataEntity metadata) implements MetadataProvider {

        @Override
        public String getTitle() {
            return metadata.getTitle();
        }

        @Override
        public String getSubtitle() {
            return metadata.getSubtitle();
        }

        @Override
        public List<String> getAuthors() {
            return metadata.getAuthors() != null
                    ? metadata.getAuthors()
                    .stream()
                    .map(AuthorEntity::getName)
                    .toList()
                    : Collections.emptyList();
        }

        @Override
        public Integer getYear() {
            return metadata.getPublishedDate() != null ? metadata.getPublishedDate().getYear() : null;
        }

        @Override
        public String getSeriesName() {
            return metadata.getSeriesName();
        }

        @Override
        public Float getSeriesNumber() {
            return metadata.getSeriesNumber();
        }

        @Override
        public String getLanguage() {
            return metadata.getLanguage();
        }

        @Override
        public String getPublisher() {
            return metadata.getPublisher();
        }

        @Override
        public String getIsbn13() {
            return metadata.getIsbn13();
        }

        @Override
        public String getIsbn10() {
            return metadata.getIsbn10();
        }

        @Override
        public LocalDate getPublishedDate() {
            return metadata.getPublishedDate();
        }
    }
}
