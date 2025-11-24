package com.adityachandel.booklore.util;

import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.entity.AuthorEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import lombok.experimental.UtilityClass;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@UtilityClass
public class PathPatternResolver {

    private final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private final Pattern FILE_EXTENSION_PATTERN = Pattern.compile(".*\\.[a-zA-Z0-9]+$");
    private final Pattern CONTROL_CHARACTER_PATTERN = Pattern.compile("[\\p{Cntrl}]");
    private final Pattern INVALID_CHARS_PATTERN = Pattern.compile("[\\\\/:*?\"<>|]");
    private final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{(.*?)}");

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

        String title = sanitize(metadata != null && metadata.getTitle() != null
                ? metadata.getTitle()
                : "Untitled");

        String subtitle = sanitize(metadata != null ? metadata.getSubtitle() : "");

        String authors = sanitize(
                metadata != null
                        ? String.join(", ", metadata.getAuthors())
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
        values.put("title", title);
        values.put("subtitle", subtitle);
        values.put("year", year);
        values.put("series", series);
        values.put("seriesIndex", seriesIndex);
        values.put("language", language);
        values.put("publisher", publisher);
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

        if (result.isBlank()) {
            result = values.getOrDefault("currentFilename", "untitled");
        }

        boolean hasExtension = FILE_EXTENSION_PATTERN.matcher(result).matches();
        boolean explicitlySetExtension = pattern.contains("{extension}");

        if (!explicitlySetExtension && !hasExtension && !extension.isBlank()) {
            result += "." + extension;
        }

        return result;
    }

    private String sanitize(String input) {
        if (input == null) return "";
        return WHITESPACE_PATTERN.matcher(CONTROL_CHARACTER_PATTERN.matcher(INVALID_CHARS_PATTERN.matcher(input).replaceAll("")).replaceAll("")).replaceAll(" ")
                .trim();
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
