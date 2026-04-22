package org.booklore.util;

import org.booklore.model.dto.BookMetadata;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookMetadataEntity;
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
    private final Pattern MODIFIER_PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}:]+)(?::([^}]+))?}");
    private final Pattern COMMA_SPACE_PATTERN = Pattern.compile(", ");
    private final Pattern SLASH_PATTERN = Pattern.compile("/");

    public String resolvePattern(BookEntity book, String pattern) {
        BookFileEntity primaryFile = book.getPrimaryBookFile();
        return resolvePattern(book, primaryFile, pattern, primaryFile != null && primaryFile.isFolderBased());
    }

    public String resolvePattern(BookEntity book, BookFileEntity bookFile, String pattern) {
        return resolvePattern(book, bookFile, pattern, bookFile != null && bookFile.isFolderBased());
    }

    public String resolvePattern(BookEntity book, BookFileEntity bookFile, String pattern, boolean folderBased) {
        String currentFilename = bookFile != null && bookFile.getFileName() != null ? bookFile.getFileName().trim() : "";
        return resolvePattern(book.getMetadata(), pattern, currentFilename, folderBased);
    }

    public String resolvePattern(BookMetadata metadata, String pattern, String filename) {
        MetadataProvider metadataProvider = MetadataProvider.from(metadata);
        return resolvePattern(metadataProvider, pattern, filename, false);
    }

    public String resolvePattern(BookMetadataEntity metadata, String pattern, String filename) {
        return resolvePattern(metadata, pattern, filename, false);
    }

    public String resolvePattern(BookMetadataEntity metadata, String pattern, String filename, boolean folderBased) {
        MetadataProvider metadataProvider = MetadataProvider.from(metadata);
        return resolvePattern(metadataProvider, pattern, filename, folderBased);
    }

    private String resolvePattern(MetadataProvider metadata, String pattern, String filename, boolean folderBased) {
        if (pattern == null || pattern.isBlank()) {
            return filename;
        }

        String filenameBase = "Untitled";
        if (filename != null && !filename.isBlank()) {
            if (folderBased) {
                // For folder-based items, don't strip extension from folder name
                filenameBase = filename;
            } else {
                int lastDot = filename.lastIndexOf('.');
                if (lastDot > 0) {
                    filenameBase = filename.substring(0, lastDot);
                } else {
                    filenameBase = filename;
                }
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
            if (seriesNumber % 1 == 0) {
                // Whole number - format with leading zero for 1-9
                seriesIndex = String.format("%02d", seriesNumber.intValue());
            } else {
                // Decimal number - format integer part with leading zero
                int intPart = seriesNumber.intValue();
                String formatted = seriesNumber.toString();
                String decimalPart = formatted.substring(formatted.indexOf('.'));
                seriesIndex = String.format("%02d", intPart) + decimalPart;
            }
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

        return resolvePatternWithValues(pattern, values, filename, folderBased);
    }

    private String resolvePatternWithValues(String pattern, Map<String, String> values, String currentFilename, boolean folderBased) {
        String extension = "";
        if (!folderBased) {
            // Only extract extension for regular files, not for folder-based items
            int lastDot = currentFilename.lastIndexOf('.');
            if (lastDot >= 0 && lastDot < currentFilename.length() - 1) {
                extension = sanitize(currentFilename.substring(lastDot + 1));  // e.g. "epub"
            }
        }

        values.put("extension", extension);

        // Handle optional blocks enclosed in <...>, supporting else clause via pipe: <left|right>
        Pattern optionalBlockPattern = Pattern.compile("<([^<>]*)>");
        Matcher matcher = optionalBlockPattern.matcher(pattern);
        StringBuilder resolved = new StringBuilder(1024);

        while (matcher.find()) {
            String blockContent = matcher.group(1);

            // Split on first unescaped pipe for else clause
            int pipeIndex = blockContent.indexOf('|');
            String primaryBlock = pipeIndex >= 0 ? blockContent.substring(0, pipeIndex) : blockContent;
            String fallbackBlock = pipeIndex >= 0 ? blockContent.substring(pipeIndex + 1) : null;

            boolean allHaveValues = checkAllPlaceholdersHaveValues(primaryBlock, values);

            if (allHaveValues) {
                String resolvedBlock = resolveBlockPlaceholders(primaryBlock, values);
                matcher.appendReplacement(resolved, Matcher.quoteReplacement(resolvedBlock));
            } else if (fallbackBlock != null) {
                String resolvedFallback = resolveBlockPlaceholders(fallbackBlock, values);
                matcher.appendReplacement(resolved, Matcher.quoteReplacement(resolvedFallback));
            } else {
                matcher.appendReplacement(resolved, "");
            }
        }
        matcher.appendTail(resolved);

        String result = resolved.toString();

        // Replace known placeholders (with optional modifiers) with values, preserve unknown ones
        Matcher placeholderMatcher = MODIFIER_PLACEHOLDER_PATTERN.matcher(result);
        StringBuilder finalResult = new StringBuilder(1024);

        while (placeholderMatcher.find()) {
            String fieldName = placeholderMatcher.group(1);
            String modifier = placeholderMatcher.group(2);
            if (values.containsKey(fieldName)) {
                String replacement = values.get(fieldName);
                if (modifier != null && !modifier.isEmpty()) {
                    replacement = applyModifier(replacement, modifier, fieldName);
                }
                placeholderMatcher.appendReplacement(finalResult, Matcher.quoteReplacement(replacement));
            } else {
                // Preserve unknown placeholders (e.g., {foo})
                placeholderMatcher.appendReplacement(finalResult, Matcher.quoteReplacement(placeholderMatcher.group()));
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

        // Don't auto-append extension for folder-based items
        if (!folderBased && !usedFallbackFilename && !patternIncludesExtension && !patternIncludesFullFilename && !extension.isBlank()) {
            result += "." + extension;
        }

        return validateFinalPath(result, folderBased);
    }

    private boolean checkAllPlaceholdersHaveValues(String block, Map<String, String> values) {
        Matcher placeholderMatcher = MODIFIER_PLACEHOLDER_PATTERN.matcher(block);
        while (placeholderMatcher.find()) {
            String fieldName = placeholderMatcher.group(1);
            String value = values.getOrDefault(fieldName, "");
            if (value.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String resolveBlockPlaceholders(String block, Map<String, String> values) {
        Matcher m = MODIFIER_PLACEHOLDER_PATTERN.matcher(block);
        StringBuilder sb = new StringBuilder(256);
        while (m.find()) {
            String fieldName = m.group(1);
            String modifier = m.group(2);
            String replacement = values.getOrDefault(fieldName, "");
            if (modifier != null && !modifier.isEmpty()) {
                replacement = applyModifier(replacement, modifier, fieldName);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String applyModifier(String value, String modifier, String fieldName) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return switch (modifier) {
            case "first" -> {
                String[] parts = COMMA_SPACE_PATTERN.split(value);
                yield parts[0].trim();
            }
            case "sort" -> {
                String firstItem = COMMA_SPACE_PATTERN.split(value)[0].trim();
                int lastSpace = firstItem.lastIndexOf(' ');
                if (lastSpace > 0) {
                    yield firstItem.substring(lastSpace + 1) + ", " + firstItem.substring(0, lastSpace);
                }
                yield firstItem;
            }
            case "initial" -> {
                String target = value;
                if ("authors".equals(fieldName)) {
                    // For authors, use the first letter of the last name of the first author
                    String firstAuthor = COMMA_SPACE_PATTERN.split(value)[0].trim();
                    int lastSpace = firstAuthor.lastIndexOf(' ');
                    target = lastSpace > 0 ? firstAuthor.substring(lastSpace + 1) : firstAuthor;
                }
                yield target.substring(0, 1).toUpperCase();
            }
            case "upper" -> value.toUpperCase();
            case "lower" -> value.toLowerCase();
            default -> value;
        };
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
        StringBuilder result = new StringBuilder(256);
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

    public String truncatePathComponent(String component, int maxBytes) {
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


    private String validateFinalPath(String path, boolean folderBased) {
        String[] components = SLASH_PATTERN.split(path);
        StringBuilder result = new StringBuilder(512);
        boolean first = true;

        for (int i = 0; i < components.length; i++) {
            String component = components[i];
            if (component == null || component.isEmpty()) {
                continue;
            }

            boolean isLastComponent = (i == components.length - 1);

            // For folder-based items, don't treat dots as extension separators
            if (isLastComponent && component.contains(".") && !folderBased) {
                component = truncateFilenameWithExtension(component);
            } else {
                if (component.getBytes(StandardCharsets.UTF_8).length > MAX_FILESYSTEM_COMPONENT_BYTES) {
                    component = truncatePathComponent(component, MAX_FILESYSTEM_COMPONENT_BYTES);
                }
                // Don't strip trailing dots from folder names for folder-based items
                if (!folderBased) {
                    while (component != null && !component.isEmpty() && component.endsWith(".")) {
                        component = component.substring(0, component.length() - 1);
                    }
                }
            }

            if (!first) result.append("/");
            result.append(component);
            first = false;
        }
        return result.toString();
    }

    public String truncateFilenameWithExtension(String filename) {
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
