package com.adityachandel.booklore.service.bookdrop;

import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.request.BookdropPatternExtractRequest;
import com.adityachandel.booklore.model.dto.response.BookdropPatternExtractResult;
import com.adityachandel.booklore.model.entity.BookdropFileEntity;
import com.adityachandel.booklore.repository.BookdropFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PreDestroy;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Slf4j
@Service
@RequiredArgsConstructor
public class FilenamePatternExtractor {

    private static final Pattern PATTERN = Pattern.compile("[,;&]");
    private final BookdropFileRepository bookdropFileRepository;
    private final BookdropMetadataHelper metadataHelper;
    private final ExecutorService regexExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        return thread;
    });
    
    private static final int PREVIEW_FILE_LIMIT = 5;
    private static final long REGEX_TIMEOUT_SECONDS = 5;
    private static final int TWO_DIGIT_YEAR_CUTOFF = 50;
    private static final int TWO_DIGIT_YEAR_CENTURY_BASE = 1900;
    private static final int FOUR_DIGIT_YEAR_LENGTH = 4;
    private static final int TWO_DIGIT_YEAR_LENGTH = 2;
    private static final int COMPACT_DATE_LENGTH = 8;

    private static final Map<String, PlaceholderConfig> PLACEHOLDER_CONFIGS = Map.ofEntries(
            Map.entry("SeriesName", new PlaceholderConfig("(.+?)", "seriesName")),
            Map.entry("Title", new PlaceholderConfig("(.+?)", "title")),
            Map.entry("Subtitle", new PlaceholderConfig("(.+?)", "subtitle")),
            Map.entry("Authors", new PlaceholderConfig("(.+?)", "authors")),
            Map.entry("SeriesNumber", new PlaceholderConfig("(\\d+(?:\\.\\d+)?)", "seriesNumber")),
            Map.entry("Published", new PlaceholderConfig("(.+?)", "publishedDate")),
            Map.entry("Publisher", new PlaceholderConfig("(.+?)", "publisher")),
            Map.entry("Language", new PlaceholderConfig("([a-zA-Z]+)", "language")),
            Map.entry("SeriesTotal", new PlaceholderConfig("(\\d+)", "seriesTotal")),
            Map.entry("ISBN10", new PlaceholderConfig("(\\d{9}[0-9Xx])", "isbn10")),
            Map.entry("ISBN13", new PlaceholderConfig("([0-9]{13})", "isbn13")),
            Map.entry("ASIN", new PlaceholderConfig("(B[A-Za-z0-9]{9}|\\d{9}[0-9Xx])", "asin"))
    );

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{(\\w+)(?::(.*?))?}|\\*");
    
    private static final Pattern FOUR_DIGIT_YEAR_PATTERN = Pattern.compile("\\d{4}");
    private static final Pattern TWO_DIGIT_YEAR_PATTERN = Pattern.compile("\\d{2}");
    private static final Pattern COMPACT_DATE_PATTERN = Pattern.compile("\\d{8}");
    private static final Pattern FLEXIBLE_DATE_PATTERN = Pattern.compile("(\\d{1,4})([^\\d])(\\d{1,2})\\2(\\d{1,4})");

    @Transactional
    public BookdropPatternExtractResult bulkExtract(BookdropPatternExtractRequest request) {
        List<Long> fileIds = metadataHelper.resolveFileIds(
                Boolean.TRUE.equals(request.getSelectAll()),
                request.getExcludedIds(),
                request.getSelectedIds()
        );
        
        boolean isPreview = Boolean.TRUE.equals(request.getPreview());
        ParsedPattern cachedPattern = parsePattern(request.getPattern());
        
        if (cachedPattern == null) {
            log.error("Failed to parse pattern: '{}'", request.getPattern());
            return buildEmptyResult(fileIds.size());
        }

        return isPreview 
                ? processPreviewExtraction(fileIds, cachedPattern)
                : processFullExtractionInBatches(fileIds, cachedPattern);
    }

    private BookdropPatternExtractResult processPreviewExtraction(List<Long> fileIds, ParsedPattern pattern) {
        List<Long> limitedFileIds = fileIds.size() > PREVIEW_FILE_LIMIT
                ? fileIds.subList(0, PREVIEW_FILE_LIMIT)
                : fileIds;
        
        List<BookdropFileEntity> previewFiles = bookdropFileRepository.findAllById(limitedFileIds);
        List<BookdropPatternExtractResult.FileExtractionResult> results = new ArrayList<>();
        int successCount = 0;
        
        for (BookdropFileEntity file : previewFiles) {
            BookdropPatternExtractResult.FileExtractionResult result = extractFromFile(file, pattern);
            results.add(result);
            if (result.isSuccess()) {
                successCount++;
            }
        }
        
        int failureCount = previewFiles.size() - successCount;
        
        return BookdropPatternExtractResult.builder()
                .totalFiles(fileIds.size())
                .successfullyExtracted(successCount)
                .failed(failureCount)
                .results(results)
                .build();
    }

    private BookdropPatternExtractResult processFullExtractionInBatches(List<Long> fileIds, ParsedPattern pattern) {
        final int BATCH_SIZE = 500;
        List<BookdropPatternExtractResult.FileExtractionResult> allResults = new ArrayList<>();
        int totalSuccessCount = 0;
        int totalFailureCount = 0;
        int totalFiles = fileIds.size();
        
        for (int batchStart = 0; batchStart < fileIds.size(); batchStart += BATCH_SIZE) {
            int batchEnd = Math.min(batchStart + BATCH_SIZE, fileIds.size());
            
            BatchExtractionResult batchResult = processSingleExtractionBatch(fileIds, batchStart, batchEnd, pattern);
            
            allResults.addAll(batchResult.results());
            totalSuccessCount += batchResult.successCount();
            totalFailureCount += batchResult.failureCount();
            
            log.debug("Processed pattern extraction batch {}-{} of {}: {} successful, {} failed", 
                    batchStart, batchEnd, totalFiles, batchResult.successCount(), batchResult.failureCount());
        }
        
        return BookdropPatternExtractResult.builder()
                .totalFiles(totalFiles)
                .successfullyExtracted(totalSuccessCount)
                .failed(totalFailureCount)
                .results(allResults)
                .build();
    }

    private BatchExtractionResult processSingleExtractionBatch(List<Long> allFileIds, int batchStart, 
                                                                int batchEnd, ParsedPattern pattern) {
        List<Long> batchIds = allFileIds.subList(batchStart, batchEnd);
        List<BookdropFileEntity> batchFiles = bookdropFileRepository.findAllById(batchIds);
        List<BookdropPatternExtractResult.FileExtractionResult> batchResults = new ArrayList<>();
        
        for (BookdropFileEntity file : batchFiles) {
            BookdropPatternExtractResult.FileExtractionResult result = extractFromFile(file, pattern);
            batchResults.add(result);
        }
        
        persistExtractedMetadata(batchResults, batchFiles);
        
        int successCount = (int) batchResults.stream().filter(BookdropPatternExtractResult.FileExtractionResult::isSuccess).count();
        int failureCount = batchFiles.size() - successCount;
        return new BatchExtractionResult(batchResults, successCount, failureCount);
    }
    
    private BookdropPatternExtractResult buildEmptyResult(int totalFiles) {
        return BookdropPatternExtractResult.builder()
                .totalFiles(totalFiles)
                .successfullyExtracted(0)
                .failed(totalFiles)
                .results(Collections.emptyList())
                .build();
    }

    public BookMetadata extractFromFilename(String filename, String pattern) {
        ParsedPattern parsedPattern = parsePattern(pattern);
        if (parsedPattern == null) {
            return null;
        }
        
        return extractFromFilenameWithParsedPattern(filename, parsedPattern);
    }
    
    private BookMetadata extractFromFilenameWithParsedPattern(String filename, ParsedPattern parsedPattern) {
        String nameOnly = FilenameUtils.getBaseName(filename);
        
        Optional<Matcher> matcherResult = executeRegexMatchingWithTimeout(parsedPattern.compiledPattern(), nameOnly);
        
        if (matcherResult.isEmpty()) {
            return null;
        }
        
        Matcher matcher = matcherResult.get();
        return buildMetadataFromMatch(matcher, parsedPattern.placeholderOrder());
    }

    private Optional<Matcher> executeRegexMatchingWithTimeout(Pattern pattern, String input) {
        Future<Optional<Matcher>> future = regexExecutor.submit(() -> {
            Matcher matcher = pattern.matcher(input);
            return matcher.find() ? Optional.of(matcher) : Optional.empty();
        });
        
        try {
            return future.get(REGEX_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("Pattern matching exceeded {} second timeout for: {}", 
                    REGEX_TIMEOUT_SECONDS, input.substring(0, Math.min(50, input.length())));
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (ExecutionException e) {
            log.error("Pattern matching failed: {}", e.getCause() != null ? e.getCause().getMessage() : "Unknown");
            return Optional.empty();
        }
    }

    @PreDestroy
    public void shutdownRegexExecutor() {
        regexExecutor.shutdown();
        try {
            if (!regexExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                regexExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            regexExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private BookdropPatternExtractResult.FileExtractionResult extractFromFile(
            BookdropFileEntity file, 
            ParsedPattern parsedPattern) {
        try {
            BookMetadata extracted = extractFromFilenameWithParsedPattern(file.getFileName(), parsedPattern);
            
            if (extracted == null) {
                String errorMsg = "Pattern did not match filename structure. Check if the pattern aligns with the filename format.";
                log.debug("Pattern mismatch for file '{}'", file.getFileName());
                return BookdropPatternExtractResult.FileExtractionResult.builder()
                        .fileId(file.getId())
                        .fileName(file.getFileName())
                        .success(false)
                        .errorMessage(errorMsg)
                        .build();
            }

            return BookdropPatternExtractResult.FileExtractionResult.builder()
                    .fileId(file.getId())
                    .fileName(file.getFileName())
                    .success(true)
                    .extractedMetadata(extracted)
                    .build();

        } catch (RuntimeException e) {
            String errorMsg = "Extraction failed: " + e.getMessage();
            log.debug("Pattern extraction failed for file '{}': {}", file.getFileName(), e.getMessage());
            return BookdropPatternExtractResult.FileExtractionResult.builder()
                    .fileId(file.getId())
                    .fileName(file.getFileName())
                    .success(false)
                    .errorMessage(errorMsg)
                    .build();
        }
    }

    private ParsedPattern parsePattern(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return null;
        }

        List<PlaceholderMatch> placeholderMatches = findAllPlaceholders(pattern);
        StringBuilder regexBuilder = new StringBuilder();
        List<String> placeholderOrder = new ArrayList<>();
        int lastEnd = 0;

        for (int i = 0; i < placeholderMatches.size(); i++) {
            PlaceholderMatch placeholderMatch = placeholderMatches.get(i);
            
            String literalTextBeforePlaceholder = pattern.substring(lastEnd, placeholderMatch.start);
            regexBuilder.append(Pattern.quote(literalTextBeforePlaceholder));

            String placeholderName = placeholderMatch.name;
            String formatParameter = placeholderMatch.formatParameter;
            
            boolean isLastPlaceholder = (i == placeholderMatches.size() - 1);
            boolean hasTextAfterPlaceholder = (placeholderMatch.end < pattern.length());
            boolean shouldUseGreedyMatching = isLastPlaceholder && !hasTextAfterPlaceholder;

            String regexForPlaceholder;
            if ("*".equals(placeholderName)) {
                regexForPlaceholder = shouldUseGreedyMatching ? "(.+)" : "(.+?)";
            } else if ("Published".equals(placeholderName) && formatParameter != null) {
                regexForPlaceholder = buildRegexForDateFormat(formatParameter);
            } else {
                PlaceholderConfig config = PLACEHOLDER_CONFIGS.get(placeholderName);
                regexForPlaceholder = determineRegexForPlaceholder(config, shouldUseGreedyMatching);
            }
            
            regexBuilder.append(regexForPlaceholder);
            
            String placeholderWithFormat = formatParameter != null ? placeholderName + ":" + formatParameter : placeholderName;
            placeholderOrder.add(placeholderWithFormat);
            lastEnd = placeholderMatch.end;
        }

        String literalTextAfterLastPlaceholder = pattern.substring(lastEnd);
        regexBuilder.append(Pattern.quote(literalTextAfterLastPlaceholder));
        
        try {
            Pattern compiledPattern = Pattern.compile(regexBuilder.toString());
            return new ParsedPattern(compiledPattern, placeholderOrder);
        } catch (PatternSyntaxException e) {
            log.error("Invalid regex syntax from user input '{}': {}", pattern, e.getMessage());
            return null;
        }
    }

    private List<PlaceholderMatch> findAllPlaceholders(String pattern) {
        List<PlaceholderMatch> placeholderMatches = new ArrayList<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(pattern);
        
        while (matcher.find()) {
            String placeholderName;
            String formatParameter = null;
            
            if (matcher.group(0).equals("*")) {
                placeholderName = "*";
            } else {
                placeholderName = matcher.group(1);
                formatParameter = matcher.group(2);
            }
            
            placeholderMatches.add(new PlaceholderMatch(
                matcher.start(),
                matcher.end(),
                placeholderName,
                formatParameter
            ));
        }
        
        return placeholderMatches;
    }

    private String buildRegexForDateFormat(String dateFormat) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        
        while (i < dateFormat.length()) {
            if (dateFormat.startsWith("yyyy", i)) {
                result.append("\\d{4}");
                i += 4;
            } else if (dateFormat.startsWith("yy", i)) {
                result.append("\\d{2}");
                i += 2;
            } else if (dateFormat.startsWith("MM", i)) {
                result.append("\\d{2}");
                i += 2;
            } else if (i < dateFormat.length() && dateFormat.charAt(i) == 'M') {
                result.append("\\d{1,2}");
                i += 1;
            } else if (dateFormat.startsWith("dd", i)) {
                result.append("\\d{2}");
                i += 2;
            } else if (i < dateFormat.length() && dateFormat.charAt(i) == 'd') {
                result.append("\\d{1,2}");
                i += 1;
            } else {
                result.append(Pattern.quote(String.valueOf(dateFormat.charAt(i))));
                i++;
            }
        }
        
        return "(" + result.toString() + ")";
    }

    private String determineRegexForPlaceholder(PlaceholderConfig config, boolean shouldUseGreedyMatching) {
        if (config != null) {
            String configuredRegex = config.regex();
            boolean isNonGreedyTextPattern = configuredRegex.equals("(.+?)");
            
            if (shouldUseGreedyMatching && isNonGreedyTextPattern) {
                return "(.+)";
            }
            return configuredRegex;
        }
        
        return shouldUseGreedyMatching ? "(.+)" : "(.+?)";
    }

    private BookMetadata buildMetadataFromMatch(Matcher matcher, List<String> placeholderOrder) {
        BookMetadata metadata = new BookMetadata();

        for (int i = 0; i < placeholderOrder.size(); i++) {
            String placeholderWithFormat = placeholderOrder.get(i);
            String[] parts = placeholderWithFormat.split(":", 2);
            String placeholderName = parts[0];
            String formatParameter = parts.length > 1 ? parts[1] : null;
            
            if ("*".equals(placeholderName)) {
                continue;
            }
            
            String value = matcher.group(i + 1).trim();
            applyValueToMetadata(metadata, placeholderName, value, formatParameter);
        }

        return metadata;
    }

    private void applyValueToMetadata(BookMetadata metadata, String placeholderName, String value, String formatParameter) {
        if (value == null || value.isBlank()) {
            return;
        }

        switch (placeholderName) {
            case "SeriesName" -> metadata.setSeriesName(value);
            case "Title" -> metadata.setTitle(value);
            case "Subtitle" -> metadata.setSubtitle(value);
            case "Authors" -> metadata.setAuthors(parseAuthors(value));
            case "SeriesNumber" -> setSeriesNumber(metadata, value);
            case "Published" -> setPublishedDate(metadata, value, formatParameter);
            case "Publisher" -> metadata.setPublisher(value);
            case "Language" -> metadata.setLanguage(value);
            case "SeriesTotal" -> setSeriesTotal(metadata, value);
            case "ISBN10" -> metadata.setIsbn10(value);
            case "ISBN13" -> metadata.setIsbn13(value);
            case "ASIN" -> metadata.setAsin(value);
        }
    }

    private Set<String> parseAuthors(String value) {
        String[] parts = PATTERN.split(value);
        Set<String> authors = new LinkedHashSet<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                authors.add(trimmed);
            }
        }
        return authors;
    }

    private void setSeriesNumber(BookMetadata metadata, String value) {
        try {
            metadata.setSeriesNumber(Float.parseFloat(value));
        } catch (NumberFormatException ignored) {
        }
    }

    private void setPublishedDate(BookMetadata metadata, String value, String dateFormat) {
        String detectedFormat = (dateFormat == null || dateFormat.isBlank()) 
                ? detectDateFormat(value) 
                : dateFormat;
        
        if (detectedFormat == null) {
            log.warn("Could not detect date format for value: '{}'", value);
            return;
        }
        
        try {
            if ("yyyy".equals(detectedFormat) || "yy".equals(detectedFormat)) {
                int year = Integer.parseInt(value);
                if ("yy".equals(detectedFormat) && year < 100) {
                    year += (year < TWO_DIGIT_YEAR_CUTOFF) ? 2000 : TWO_DIGIT_YEAR_CENTURY_BASE;
                }
                metadata.setPublishedDate(LocalDate.of(year, 1, 1));
                return;
            }
            
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(detectedFormat);
            LocalDate date = LocalDate.parse(value, formatter);
            metadata.setPublishedDate(date);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse year value '{}': {}", value, e.getMessage());
        } catch (DateTimeParseException e) {
            log.warn("Failed to parse date '{}' with format '{}': {}", value, detectedFormat, e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid date format '{}' for value '{}': {}", detectedFormat, value, e.getMessage());
        }
    }

    private String detectDateFormat(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        
        String trimmed = value.trim();
        int length = trimmed.length();
        
        if (length == FOUR_DIGIT_YEAR_LENGTH && FOUR_DIGIT_YEAR_PATTERN.matcher(trimmed).matches()) {
            return "yyyy";
        }
        
        if (length == TWO_DIGIT_YEAR_LENGTH && TWO_DIGIT_YEAR_PATTERN.matcher(trimmed).matches()) {
            return "yy";
        }
        
        if (length == COMPACT_DATE_LENGTH && COMPACT_DATE_PATTERN.matcher(trimmed).matches()) {
            return "yyyyMMdd";
        }
        
        Matcher flexibleMatcher = FLEXIBLE_DATE_PATTERN.matcher(trimmed);
        if (flexibleMatcher.matches()) {
            String separator = flexibleMatcher.group(2);
            return determineFlexibleDateFormat(flexibleMatcher, separator);
        }
        
        return null;
    }
    
    private String determineFlexibleDateFormat(Matcher matcher, String separator) {
        String part1 = matcher.group(1);
        String part2 = matcher.group(3);
        String part3 = matcher.group(4);
        
        int val1, val2, val3;
        try {
            val1 = Integer.parseInt(part1);
            val2 = Integer.parseInt(part2);
            val3 = Integer.parseInt(part3);
        } catch (NumberFormatException e) {
            return null;
        }
        
        String format1, format2, format3;
        
        if (isYearValue(part1, val1)) {
            format1 = buildYearFormat(part1);
            if (val2 <= 12 && val3 > 12) {
                format2 = buildMonthFormat(part2);
                format3 = buildDayFormat(part3);
            } else if (val3 <= 12 && val2 > 12) {
                format2 = buildDayFormat(part2);
                format3 = buildMonthFormat(part3);
            } else {
                format2 = buildMonthFormat(part2);
                format3 = buildDayFormat(part3);
            }
        } else if (isYearValue(part3, val3)) {
            format3 = buildYearFormat(part3);
            if (val1 <= 12 && val2 > 12) {
                format1 = buildMonthFormat(part1);
                format2 = buildDayFormat(part2);
            } else if (val2 <= 12 && val1 > 12) {
                format1 = buildDayFormat(part1);
                format2 = buildMonthFormat(part2);
            } else {
                format1 = buildDayFormat(part1);
                format2 = buildMonthFormat(part2);
            }
        } else {
            format1 = buildDayFormat(part1);
            format2 = buildMonthFormat(part2);
            format3 = part3.length() == 2 ? "yy" : "y";
        }
        
        return format1 + separator + format2 + separator + format3;
    }
    
    private boolean isYearValue(String part, int value) {
        return part.length() == 4 || value > 31;
    }
    
    private String buildYearFormat(String part) {
        return part.length() == 4 ? "yyyy" : "yy";
    }
    
    private String buildMonthFormat(String part) {
        return part.length() == 2 ? "MM" : "M";
    }
    
    private String buildDayFormat(String part) {
        return part.length() == 2 ? "dd" : "d";
    }

    private void setSeriesTotal(BookMetadata metadata, String value) {
        try {
            metadata.setSeriesTotal(Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
        }
    }

    private void persistExtractedMetadata(List<BookdropPatternExtractResult.FileExtractionResult> results, List<BookdropFileEntity> files) {
        Map<Long, BookdropFileEntity> fileMap = new HashMap<>();
        for (BookdropFileEntity file : files) {
            fileMap.put(file.getId(), file);
        }

        Set<Long> failedFileIds = new HashSet<>();

        for (BookdropPatternExtractResult.FileExtractionResult result : results) {
            if (!result.isSuccess() || result.getExtractedMetadata() == null) {
                continue;
            }

            BookdropFileEntity file = fileMap.get(result.getFileId());
            if (file == null) {
                continue;
            }

            try {
                BookMetadata currentMetadata = metadataHelper.getCurrentMetadata(file);
                BookMetadata extractedMetadata = result.getExtractedMetadata();
                metadataHelper.mergeMetadata(currentMetadata, extractedMetadata);
                metadataHelper.updateFetchedMetadata(file, currentMetadata);

            } catch (RuntimeException e) {
                log.error("Error persisting extracted metadata for file {} ({}): {}", 
                         file.getId(), file.getFileName(), e.getMessage(), e);
                failedFileIds.add(file.getId());
                result.setSuccess(false);
                result.setErrorMessage("Failed to save metadata: " + e.getMessage());
            }
        }

        List<BookdropFileEntity> filesToSave = files.stream()
                .filter(file -> !failedFileIds.contains(file.getId()))
                .toList();

        if (!filesToSave.isEmpty()) {
            bookdropFileRepository.saveAll(filesToSave);
        }
    }

    private record PlaceholderConfig(String regex, String metadataField) {}

    private record ParsedPattern(Pattern compiledPattern, List<String> placeholderOrder) {}
    
    private record PlaceholderMatch(int start, int end, String name, String formatParameter) {}
    
    private record BatchExtractionResult(List<BookdropPatternExtractResult.FileExtractionResult> results, 
                                         int successCount, int failureCount) {}
}
