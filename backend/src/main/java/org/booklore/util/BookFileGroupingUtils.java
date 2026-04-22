package org.booklore.util;

import org.booklore.model.dto.settings.LibraryFile;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.similarity.FuzzyScore;

import java.util.*;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@UtilityClass
@Slf4j
public class BookFileGroupingUtils {

    private static final Pattern FORMAT_INDICATOR_PATTERN = Pattern.compile(
            "[\\(\\[]\\s*(?:pdf|epub|mobi|azw3?|fb2|cbz|cbr|cb7|m4b|m4a|mp3|audiobook|audio)\\s*[\\)\\]]",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private static final Pattern UNDERSCORE_PATTERN = Pattern.compile("_");

    // Matches trailing author: " - J.R.R. Tolkien", " - George Orwell", " - J.K. Rowling"
    private static final Pattern TRAILING_AUTHOR_PATTERN = Pattern.compile(
            "\\s*[-–—]\\s+(?:[A-Z](?:\\.[A-Z])*\\.\\s+)?[A-Z][a-z]+(?:\\s+[A-Z](?:\\.[A-Z])*\\.?)?(?:\\s+[A-Z][a-z]+)*\\s*$"
    );

    // Matches ", The" or ", A" or ", An" at end
    private static final Pattern ARTICLE_SUFFIX_PATTERN = Pattern.compile(
            ",\\s*(The|A|An)\\s*$", Pattern.CASE_INSENSITIVE
    );

    // Series patterns: "Book 1", "Vol. 2", "Part 3", "#4", etc.
    private static final Pattern SERIES_NUMBER_PATTERN = Pattern.compile(
            "(?:book|vol(?:ume)?|part|chapter|episode|#|no\\.?)\\s*(\\d+)",
            Pattern.CASE_INSENSITIVE
    );

    // Trailing number pattern: "book1", "book 2", "title_03"
    private static final Pattern TRAILING_NUMBER_PATTERN = Pattern.compile(
            "[\\s_-]?(\\d{1,3})\\s*$"
    );

    // Part/disc indicators: "(part 1)", "[pt 2]", "- disc 1", "cd 3", etc.
    // "part" requires brackets or dash prefix to avoid conflict with series labels ("Part 1" = series entry).
    // "pt", "disc", "disk", "cd" are unambiguous and can be matched bare.
    private static final Pattern PART_INDICATOR_PATTERN = Pattern.compile(
            "\\s*(?:" +
                    "[\\(\\[]\\s*(?:part|pt|dis[ck]|cd)\\s*\\d+\\s*[\\)\\]]" +
                    "|-\\s*(?:part|pt|dis[ck]|cd)\\s*\\d+" +
                    "|(?:pt|dis[ck]|cd)\\s*\\d+" +
                    ")\\s*$",
            Pattern.CASE_INSENSITIVE
    );

    // Bare-number prefix: "1. Title", "01 - Title" (NOT "101 Dalmatians" or "1984")
    private static final Pattern BARE_NUMBER_PREFIX_PATTERN = Pattern.compile(
            "^(\\d{1,3})(?:\\.|\\s*-)\\s+(.+)$"
    );

    // Edition/version patterns that should NOT prevent grouping
    // Includes format indicators (audiobook, ebook) and edition descriptors
    private static final Pattern EDITION_PATTERN = Pattern.compile(
            "(?:tenth|first|second|third|\\d+(?:st|nd|rd|th)?)\\s*(?:anniversary|edition|ed\\.?)|" +
                    "(?:unabridged|abridged|complete|full\\s*cast|deluxe|special|collector)|" +
                    "(?:audiobook|audio\\s*book|ebook|e-book)",
            Pattern.CASE_INSENSITIVE
    );

    private static final double FOLDER_MATCH_THRESHOLD = 0.6;
    private static final double FUZZY_CLUSTER_THRESHOLD = 0.7;

    // Known book file extensions - only strip these, not arbitrary dots in folder names
    private static final Set<String> KNOWN_EXTENSIONS = Set.of(
            "pdf", "epub", "cbz", "cbr", "cb7", "mobi", "azw3", "azw", "fb2",
            "m4b", "m4a", "mp3"
    );

    public String extractGroupingKey(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }

        int lastDot = fileName.lastIndexOf('.');
        String baseName;
        if (lastDot > 0) {
            String possibleExtension = fileName.substring(lastDot + 1).toLowerCase();
            // Only strip if it's a known book file extension
            if (KNOWN_EXTENSIONS.contains(possibleExtension)) {
                baseName = fileName.substring(0, lastDot);
            } else {
                baseName = fileName;
            }
        } else {
            baseName = fileName;
        }

        // Convert underscores to spaces
        baseName = UNDERSCORE_PATTERN.matcher(baseName).replaceAll(" ");

        baseName = FORMAT_INDICATOR_PATTERN.matcher(baseName).replaceAll("");

        // Strip part/disc indicators (e.g., "(part 1)", "[cd 2]")
        baseName = PART_INDICATOR_PATTERN.matcher(baseName).replaceAll("");

        // Strip trailing author names (after dash)
        baseName = TRAILING_AUTHOR_PATTERN.matcher(baseName).replaceAll("");

        // Reposition articles ("Hobbit, The" -> "The Hobbit")
        Matcher articleMatcher = ARTICLE_SUFFIX_PATTERN.matcher(baseName);
        if (articleMatcher.find()) {
            String article = articleMatcher.group(1);
            baseName = article + " " + baseName.substring(0, articleMatcher.start());
        }

        baseName = WHITESPACE_PATTERN.matcher(baseName.trim()).replaceAll(" ");

        return baseName.toLowerCase().trim();
    }

    public double calculateSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null || s1.isEmpty() || s2.isEmpty()) {
            return 0;
        }
        FuzzyScore fuzzyScore = new FuzzyScore(Locale.ENGLISH);
        int score = fuzzyScore.fuzzyScore(s1, s2);
        int maxScore = Math.max(
                fuzzyScore.fuzzyScore(s1, s1),
                fuzzyScore.fuzzyScore(s2, s2)
        );
        return maxScore > 0 ? (double) score / maxScore : 0;
    }

    public String generateDirectoryGroupKey(String fileSubPath, String fileName) {
        String safeSubPath = (fileSubPath == null) ? "" : fileSubPath;
        return safeSubPath + ":" + extractGroupingKey(fileName);
    }

    /**
     * Groups library files using folder-centric strategy with fuzzy matching.
     * <p>
     * Strategy:
     * 1. Group files by folder (libraryPathId + fileSubPath)
     * 2. For files in subfolders: use folder name as reference title
     *    - Files matching folder name (substring/fuzzy) → group together
     *    - Series entries (numbered) → keep separate
     *    - Unrelated files → separate books
     * 3. For root-level files: use exact grouping key matching
     */
    public Map<String, List<LibraryFile>> groupByBaseName(List<LibraryFile> libraryFiles) {
        Map<String, List<LibraryFile>> result = new LinkedHashMap<>();

        // First, group files by folder
        Map<String, List<LibraryFile>> byFolder = new LinkedHashMap<>();
        for (LibraryFile file : libraryFiles) {
            String folderKey = file.getLibraryPathEntity().getId() + ":" +
                    (file.getFileSubPath() == null ? "" : file.getFileSubPath());
            byFolder.computeIfAbsent(folderKey, k -> new ArrayList<>()).add(file);
        }

        // Process each folder
        for (Map.Entry<String, List<LibraryFile>> folderEntry : byFolder.entrySet()) {
            List<LibraryFile> filesInFolder = folderEntry.getValue();
            String folderKey = folderEntry.getKey();

            if (filesInFolder.isEmpty()) {
                continue;
            }

            String fileSubPath = filesInFolder.get(0).getFileSubPath();
            Long libraryPathId = filesInFolder.get(0).getLibraryPathEntity().getId();

            // Root-level files: use exact grouping
            if (fileSubPath == null || fileSubPath.isEmpty()) {
                for (LibraryFile file : filesInFolder) {
                    String key = libraryPathId + "::" + extractGroupingKey(file.getFileName());
                    result.computeIfAbsent(key, k -> new ArrayList<>()).add(file);
                }
                continue;
            }

            // Files in subfolder: use folder-centric grouping
            Map<String, List<LibraryFile>> folderGroups = groupFilesInFolder(filesInFolder, fileSubPath, libraryPathId);
            result.putAll(folderGroups);
        }

        return result;
    }

    /**
     * Groups files within a folder using the folder name as reference.
     */
    private Map<String, List<LibraryFile>> groupFilesInFolder(List<LibraryFile> files, String fileSubPath, Long libraryPathId) {
        Map<String, List<LibraryFile>> result = new LinkedHashMap<>();

        // Extract folder name (last component of subPath)
        String folderName = extractFolderName(fileSubPath);
        String folderKey = extractGroupingKey(folderName);

        // Categorize files
        Map<String, List<LibraryFile>> matchesFolderByNumber = new LinkedHashMap<>();
        Map<String, List<LibraryFile>> seriesEntries = new LinkedHashMap<>();
        List<LibraryFile> unmatched = new ArrayList<>();

        for (LibraryFile file : files) {
            String fileKey = extractGroupingKey(file.getFileName());

            // Check for series numbering (e.g., "Harry Potter Book 1")
            SeriesInfo seriesInfo = extractSeriesInfo(fileKey);
            if (seriesInfo != null && seriesInfo.number != null) {
                // Check if the base title (without number) matches folder
                if (matchesFolderName(seriesInfo.baseTitle, folderKey)) {
                    // This is a series entry - keep separate by number
                    String seriesGroupKey = libraryPathId + ":" + fileSubPath + ":series:" + seriesInfo.baseTitle + ":" + seriesInfo.number;
                    seriesEntries.computeIfAbsent(seriesGroupKey, k -> new ArrayList<>()).add(file);
                    continue;
                }
            }

            // Check if file matches folder name
            if (matchesFolderName(fileKey, folderKey)) {
                // Extract trailing number to differentiate "book1" vs "book2"
                String trailingNum = extractTrailingNumber(fileKey);
                String numberKey = trailingNum != null ? trailingNum : "none";
                matchesFolderByNumber.computeIfAbsent(numberKey, k -> new ArrayList<>()).add(file);
            } else {
                unmatched.add(file);
            }
        }

        // Group files that match folder name, separating by trailing number
        for (Map.Entry<String, List<LibraryFile>> entry : matchesFolderByNumber.entrySet()) {
            List<LibraryFile> filesWithNumber = entry.getValue();
            String numberSuffix = "none".equals(entry.getKey()) ? "" : ":" + entry.getKey();
            String groupKey = libraryPathId + ":" + fileSubPath + ":folder:" + folderKey + numberSuffix;
            result.put(groupKey, filesWithNumber);
            log.debug("Grouped {} files matching folder '{}'{}: {}", filesWithNumber.size(), folderName,
                    numberSuffix.isEmpty() ? "" : " (num=" + entry.getKey() + ")",
                    filesWithNumber.stream().map(LibraryFile::getFileName).toList());
        }

        // Add series entries as separate groups
        result.putAll(seriesEntries);

        // Handle unmatched files - try to cluster by similarity
        if (!unmatched.isEmpty()) {
            Map<String, List<LibraryFile>> clusters = clusterBySimilarity(unmatched, libraryPathId, fileSubPath);
            result.putAll(clusters);
        }

        return result;
    }

    /**
     * Extracts the trailing number from a key, if present.
     */
    private String extractTrailingNumber(String key) {
        Matcher m = TRAILING_NUMBER_PATTERN.matcher(key);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    /**
     * Extracts the folder name from a subPath (last component).
     */
    private String extractFolderName(String fileSubPath) {
        if (fileSubPath == null || fileSubPath.isEmpty()) {
            return "";
        }
        int lastSlash = fileSubPath.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < fileSubPath.length() - 1) {
            return fileSubPath.substring(lastSlash + 1);
        }
        return fileSubPath;
    }

    /**
     * Checks if a file's grouping key matches the folder name.
     * Uses substring matching and fuzzy similarity.
     */
    private boolean matchesFolderName(String fileKey, String folderKey) {
        if (fileKey.isEmpty() || folderKey.isEmpty()) {
            return false;
        }

        // Exact match
        if (fileKey.equals(folderKey)) {
            return true;
        }

        // Substring match (either direction)
        if (fileKey.contains(folderKey) || folderKey.contains(fileKey)) {
            return true;
        }

        // Strip edition info and compare
        String fileKeyClean = stripEditionInfo(fileKey);
        String folderKeyClean = stripEditionInfo(folderKey);
        if (fileKeyClean.equals(folderKeyClean)) {
            return true;
        }
        if (fileKeyClean.contains(folderKeyClean) || folderKeyClean.contains(fileKeyClean)) {
            return true;
        }

        // Fuzzy match
        double similarity = calculateSimilarity(fileKeyClean, folderKeyClean);
        return similarity >= FOLDER_MATCH_THRESHOLD;
    }

    /**
     * Strips edition/version information from a key for comparison.
     */
    private String stripEditionInfo(String key) {
        String result = EDITION_PATTERN.matcher(key).replaceAll("");
        return WHITESPACE_PATTERN.matcher(result.trim()).replaceAll(" ").trim();
    }

    /**
     * Extracts series information (base title and number) from a grouping key.
     */
    private SeriesInfo extractSeriesInfo(String key) {
        Matcher matcher = SERIES_NUMBER_PATTERN.matcher(key);
        if (matcher.find()) {
            String number = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (number != null) {
                // Remove the series number pattern to get base title
                String baseTitle = SERIES_NUMBER_PATTERN.matcher(key).replaceAll("").trim();
                baseTitle = WHITESPACE_PATTERN.matcher(baseTitle).replaceAll(" ").trim();
                // Only consider it a series if there's a meaningful base title
                if (baseTitle.length() >= 3) {
                    return new SeriesInfo(baseTitle, number);
                }
            }
        }

        // Fallback: bare-number prefix like "1. title" or "01 - title"
        Matcher bareMatcher = BARE_NUMBER_PREFIX_PATTERN.matcher(key);
        if (bareMatcher.matches()) {
            String number = bareMatcher.group(1);
            String baseTitle = bareMatcher.group(2).trim();
            baseTitle = WHITESPACE_PATTERN.matcher(baseTitle).replaceAll(" ").trim();
            if (baseTitle.length() >= 3) {
                return new SeriesInfo(baseTitle, number);
            }
        }

        return null;
    }

    /**
     * Clusters unmatched files by fuzzy similarity.
     * Uses union-find to group similar files together.
     * Files with different trailing numbers are kept separate (e.g., "book1" vs "book2").
     */
    private Map<String, List<LibraryFile>> clusterBySimilarity(List<LibraryFile> files, Long libraryPathId, String fileSubPath) {
        Map<String, List<LibraryFile>> result = new LinkedHashMap<>();

        if (files.size() == 1) {
            LibraryFile file = files.get(0);
            String key = libraryPathId + ":" + fileSubPath + ":single:" + extractGroupingKey(file.getFileName());
            result.put(key, new ArrayList<>(List.of(file)));
            return result;
        }

        // Extract keys for all files
        List<String> keys = files.stream()
                .map(f -> extractGroupingKey(f.getFileName()))
                .toList();

        // Union-Find for clustering
        int[] parent = new int[files.size()];
        for (int i = 0; i < parent.length; i++) {
            parent[i] = i;
        }

        // Compare each pair and union if similar
        for (int i = 0; i < files.size(); i++) {
            for (int j = i + 1; j < files.size(); j++) {
                String key1 = stripEditionInfo(keys.get(i));
                String key2 = stripEditionInfo(keys.get(j));

                // Don't cluster files that have different trailing numbers
                // This prevents "book1" and "book2" from being grouped
                if (hasDifferentTrailingNumbers(key1, key2)) {
                    continue;
                }

                // Check substring match or fuzzy similarity
                boolean shouldGroup = key1.contains(key2) || key2.contains(key1) ||
                        calculateSimilarity(key1, key2) >= FUZZY_CLUSTER_THRESHOLD;

                if (shouldGroup) {
                    union(parent, i, j);
                }
            }
        }

        // Group files by their root
        Map<Integer, List<LibraryFile>> clusters = new LinkedHashMap<>();
        for (int i = 0; i < files.size(); i++) {
            int root = find(parent, i);
            clusters.computeIfAbsent(root, k -> new ArrayList<>()).add(files.get(i));
        }

        // Convert to result format
        for (Map.Entry<Integer, List<LibraryFile>> cluster : clusters.entrySet()) {
            List<LibraryFile> clusterFiles = cluster.getValue();
            // Use the first file's key as the group key
            String groupKey = libraryPathId + ":" + fileSubPath + ":cluster:" +
                    extractGroupingKey(clusterFiles.get(0).getFileName());
            result.put(groupKey, clusterFiles);

            if (clusterFiles.size() > 1) {
                log.debug("Clustered {} unmatched files: {}", clusterFiles.size(),
                        clusterFiles.stream().map(LibraryFile::getFileName).toList());
            }
        }

        return result;
    }

    /**
     * Checks if two keys have different trailing numbers.
     * Returns true if both have trailing numbers AND they're different.
     * This prevents grouping "book1" with "book2".
     */
    private boolean hasDifferentTrailingNumbers(String key1, String key2) {
        Matcher m1 = TRAILING_NUMBER_PATTERN.matcher(key1);
        Matcher m2 = TRAILING_NUMBER_PATTERN.matcher(key2);

        boolean has1 = m1.find();
        boolean has2 = m2.find();

        // If both have trailing numbers, check if they're different
        if (has1 && has2) {
            String num1 = m1.group(1);
            String num2 = m2.group(1);
            return !num1.equals(num2);
        }

        // If only one has a number, they might still be different books
        // e.g., "book" vs "book2" - let other heuristics decide
        return false;
    }

    private int find(int[] parent, int i) {
        if (parent[i] != i) {
            parent[i] = find(parent, parent[i]);
        }
        return parent[i];
    }

    private void union(int[] parent, int i, int j) {
        int rootI = find(parent, i);
        int rootJ = find(parent, j);
        if (rootI != rootJ) {
            parent[rootJ] = rootI;
        }
    }

    /**
     * Helper class to hold series information.
     */
    private static class SeriesInfo {
        final String baseTitle;
        final String number;

        SeriesInfo(String baseTitle, String number) {
            this.baseTitle = baseTitle;
            this.number = number;
        }
    }
}
