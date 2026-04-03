package org.booklore.service.metadata.parser;

import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.request.FetchMetadataRequest;
import org.booklore.model.dto.response.GoogleBooksApiResponse;
import org.booklore.model.dto.settings.MetadataProviderSettings;
import org.booklore.model.enums.MetadataProvider;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.util.BookUtils;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class GoogleParser implements BookParser {

    private static final Pattern FOUR_DIGIT_YEAR_PATTERN = Pattern.compile("^(\\d{4})$");
    private static final Pattern YEAR_MONTH_PATTERN = Pattern.compile("^(\\d{4})-(\\d{2})$");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern SPECIAL_CHARACTERS_PATTERN = Pattern.compile("[.,\\-\\[\\]{}()!@#$%^&*_=+|~`<>?/\";:]");
    private static final long MIN_REQUEST_INTERVAL_MS = 1500;
    private static final int MAX_SEARCH_TERM_LENGTH = 60;
    private static final int MAX_RESULTS = 20;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final ObjectMapper objectMapper;
    private final AppSettingService appSettingService;
    private final HttpClient httpClient;
    private static final String GOOGLE_BOOKS_API_URL = "https://www.googleapis.com/books/v1/volumes";
    private final AtomicLong lastRequestTime = new AtomicLong(0);

    public GoogleParser(ObjectMapper objectMapper, AppSettingService appSettingService, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.appSettingService = appSettingService;
        this.httpClient = httpClient;
    }

    @Override
    public BookMetadata fetchTopMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        List<BookMetadata> fetchedBookMetadata = fetchMetadata(book, fetchMetadataRequest);
        return fetchedBookMetadata.isEmpty() ? null : fetchedBookMetadata.getFirst();
    }

    @Override
    public List<BookMetadata> fetchMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        // 1. Try ISBN Search
        if (fetchMetadataRequest.getIsbn() != null && !fetchMetadataRequest.getIsbn().isBlank()) {
            List<BookMetadata> isbnResults = getMetadataListByIsbn(ParserUtils.cleanIsbn(fetchMetadataRequest.getIsbn()));
            if (!isbnResults.isEmpty()) {
                return isbnResults;
            }
            log.info("Google Books: ISBN search returned no results, falling back to Title+Author search.");
        }

        String title = fetchMetadataRequest.getTitle();
        String author = fetchMetadataRequest.getAuthor();
        String fileName = book.getPrimaryFile() != null ? book.getPrimaryFile().getFileName() : null;

        List<BookMetadata> results = Collections.emptyList();

        // 2. Try Title + Author Search
        if (title != null && !title.isBlank() && author != null && !author.isBlank()) {
            String term = buildSearchTerm(title, author);
            log.info("Google Books: Searching with Title + Author: {}", term);
            results = getMetadataListByTerm(term);
        }

        // 3. Try Title Only Search (if Title+Author failed or wasn't attempted)
        if (results.isEmpty()) {
            String term = null;
            if (title != null && !title.isBlank()) {
                term = buildSearchTerm(title, null);
                log.info("Google Books: Searching with Title Only: {}", term);
            } else if (fileName != null && !fileName.isBlank()) {
                term = buildSearchTerm(BookUtils.cleanFileName(fileName), null);
                log.info("Google Books: Searching with Filename: {}", term);
            }

            if (term != null) {
                results = getMetadataListByTerm(term);
            }
        }

        return results;
    }

    private String buildSearchTerm(String title, String author) {
        String searchTerm = SPECIAL_CHARACTERS_PATTERN.matcher(title).replaceAll("").trim();
        searchTerm = "intitle:" + truncateToMaxWords(searchTerm);

        if (author != null && !author.isBlank()) {
            searchTerm += " inauthor:" + author;
        }

        return searchTerm;
    }

    private List<BookMetadata> getMetadataListByIsbn(String isbn) {
        // Try ISBN-13 format first, then ISBN-10
        String cleanIsbn = isbn.replace("-", "");
        List<BookMetadata> results = fetchFromApi("isbn:" + cleanIsbn, true);
        
        if (results.isEmpty() && cleanIsbn.length() == 13) {
            // If ISBN-13 failed, try searching without the prefix for broader results
            log.info("ISBN-13 search returned no results, trying general search with ISBN");
            results = fetchFromApi(cleanIsbn, true);
        }
        
        return results;
    }

    public List<BookMetadata> getMetadataListByTerm(String term) {
        return fetchFromApi(term);
    }

    private List<BookMetadata> fetchFromApi(String query) {
        return fetchFromApi(query, false);
    }

    private List<BookMetadata> fetchFromApi(String query, boolean isIsbnSearch) {
        try {
            waitForRateLimit();

            // Use smaller maxResults for ISBN searches (typically return 1-3 results)
            // Use larger maxResults for title/author searches to find best match
            int maxResults = isIsbnSearch ? 5 : MAX_RESULTS;

            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(getApiUrl())
                    .queryParam("q", query)
                    .queryParam("maxResults", maxResults);
            
            URI uri = uriBuilder.build().toUri();

            log.info("Google Books API URL: {}", uri);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            return handleApiResponse(response);
        } catch (IOException e) {
            log.error("IO error while fetching metadata from Google Books API: {}", e.getMessage());
            return List.of();
        } catch (InterruptedException e) {
            log.error("Request to Google Books API was interrupted");
            Thread.currentThread().interrupt();
            return List.of();
        }
    }

    private List<BookMetadata> handleApiResponse(HttpResponse<String> response) throws IOException {
        int statusCode = response.statusCode();
        
        if (statusCode == 200) {
            List<BookMetadata> results = parseGoogleBooksApiResponse(response.body());
            List<BookMetadata> filtered = filterIrrelevantResults(results);
            return sortByCompleteness(filtered);
        }
        
        if (statusCode == 429) {
            log.warn("Google Books API rate limit exceeded. Consider increasing request interval.");
            return List.of();
        }
        
        if (statusCode >= 500) {
            log.error("Google Books API server error. Status: {}", statusCode);
            return List.of();
        }
        
        log.error("Google Books API request failed. Status: {}, Response: {}",
                statusCode, response.body());
        return List.of();
    }

    private List<BookMetadata> filterIrrelevantResults(List<BookMetadata> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        
        return results.stream()
                .filter(this::isRelevantResult)
                .collect(Collectors.toList());
    }

    private boolean isRelevantResult(BookMetadata metadata) {
        // Must have a title - this is the bare minimum
        if (metadata.getTitle() == null || metadata.getTitle().isBlank()) {
            return false;
        }
        
        // Filter out items with extremely short titles (likely garbage data)
        if (metadata.getTitle().trim().length() < 2) {
            return false;
        }
        
        // Must have at least some identifying information beyond just a title
        // (either author, ISBN, description, or publisher)
        boolean hasAuthor = metadata.getAuthors() != null && !metadata.getAuthors().isEmpty();
        boolean hasIsbn = (metadata.getIsbn13() != null && !metadata.getIsbn13().isBlank()) ||
                          (metadata.getIsbn10() != null && !metadata.getIsbn10().isBlank());
        boolean hasDescription = metadata.getDescription() != null && metadata.getDescription().length() > 10;
        boolean hasPublisher = metadata.getPublisher() != null && !metadata.getPublisher().isBlank();
        boolean hasGoogleId = metadata.getGoogleId() != null && !metadata.getGoogleId().isBlank();
        
        return hasAuthor || hasIsbn || hasDescription || hasPublisher || hasGoogleId;
    }

    private List<BookMetadata> parseGoogleBooksApiResponse(String responseBody) throws IOException {
        GoogleBooksApiResponse googleBooksApiResponse = objectMapper.readValue(responseBody, GoogleBooksApiResponse.class);
        if (googleBooksApiResponse != null && googleBooksApiResponse.getItems() != null) {
            return googleBooksApiResponse.getItems().stream()
                    .map(this::convertToFetchedBookMetadata)
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    private BookMetadata convertToFetchedBookMetadata(GoogleBooksApiResponse.Item item) {
        GoogleBooksApiResponse.Item.VolumeInfo volumeInfo = item.getVolumeInfo();
        Map<String, String> isbns = extractISBNs(volumeInfo.getIndustryIdentifiers());

        String cleanDescription = cleanDescription(volumeInfo.getDescription());
        
        Integer pageCount = volumeInfo.getPrintedPageCount() != null && volumeInfo.getPrintedPageCount() > 0
                ? volumeInfo.getPrintedPageCount()
                : volumeInfo.getPageCount();
        
        // Filter out invalid page counts
        if (pageCount != null && pageCount <= 0) {
            pageCount = null;
        }

        String externalUrl = getExternalUrl(volumeInfo);
        
        SeriesData seriesData = extractSeriesInfo(volumeInfo);

        return BookMetadata.builder()
                .provider(MetadataProvider.Google)
                .googleId(item.getId())
                .title(cleanTitle(volumeInfo.getTitle()))
                .subtitle(volumeInfo.getSubtitle())
                .publisher(volumeInfo.getPublisher())
                .publishedDate(parseDate(volumeInfo.getPublishedDate()))
                .description(cleanDescription)
                .authors(cleanAuthors(volumeInfo.getAuthors()))
                .categories(cleanCategories(volumeInfo.getCategories()))
                .isbn13(isbns.get("ISBN_13"))
                .isbn10(isbns.get("ISBN_10"))
                .pageCount(pageCount)
                .thumbnailUrl(extractBestCoverImage(volumeInfo.getImageLinks()))
                .language(normalizeLanguage(volumeInfo.getLanguage()))
                .externalUrl(externalUrl)
                .seriesName(seriesData.name)
                .seriesNumber(seriesData.number)
                .build();
    }

    private record SeriesData(String name, Float number) {}

    private SeriesData extractSeriesInfo(GoogleBooksApiResponse.Item.VolumeInfo volumeInfo) {
        if (volumeInfo.getSeriesInfo() != null) {
            GoogleBooksApiResponse.Item.SeriesInfo seriesInfo = volumeInfo.getSeriesInfo();
            String seriesTitle = seriesInfo.getShortSeriesBookTitle();
            Float seriesNumber = null;
            
            if (seriesInfo.getVolumeSeries() != null && !seriesInfo.getVolumeSeries().isEmpty()) {
                Integer orderNum = seriesInfo.getVolumeSeries().get(0).getOrderNumber();
                if (orderNum != null) {
                    seriesNumber = orderNum.floatValue();
                }
            }
            
            if (seriesNumber == null && seriesInfo.getBookDisplayNumber() != null) {
                try {
                    seriesNumber = Float.parseFloat(seriesInfo.getBookDisplayNumber());
                } catch (NumberFormatException ignored) {
                    // Not a valid number, ignore
                }
            }
            
            if (seriesTitle != null || seriesNumber != null) {
                return new SeriesData(seriesTitle, seriesNumber);
            }
        }
        
        String title = volumeInfo.getTitle();
        if (title != null) {
            // Try patterns like "Title, Vol. 1", "Title Vol 1", "Title Volume 1"
            SeriesData fromTitle = extractSeriesFromTitle(title);
            if (fromTitle.number != null) {
                return fromTitle;
            }
        }
        
        return new SeriesData(null, null);
    }

    /**
     * Extract series number from common title patterns.
     * Handles formats like:
     * - "One Piece, Vol. 100"
     * - "Naruto Volume 5"
     * - "Batman #123"
     * - "Spider-Man 2099 2"
     * - "Title (Band 5)"
     */
    private SeriesData extractSeriesFromTitle(String title) {
        if (title == null || title.isBlank()) {
            return new SeriesData(null, null);
        }
        
        Pattern volPattern = Pattern.compile("(.+?)(?:,\\s*)?(?:Vol\\.?|Volume)\\s*(\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
        Matcher volMatcher = volPattern.matcher(title);
        if (volMatcher.find()) {
            String seriesName = volMatcher.group(1).trim();
            Float number = parseSeriesNumber(volMatcher.group(2));
            return new SeriesData(seriesName, number);
        }
        
        Pattern issuePattern = Pattern.compile("(.+?)\\s*#(\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
        Matcher issueMatcher = issuePattern.matcher(title);
        if (issueMatcher.find()) {
            String seriesName = issueMatcher.group(1).trim();
            Float number = parseSeriesNumber(issueMatcher.group(2));
            return new SeriesData(seriesName, number);
        }
        
        Pattern bandPattern = Pattern.compile("(.+?)(?:,\\s*)?Band\\s*(\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
        Matcher bandMatcher = bandPattern.matcher(title);
        if (bandMatcher.find()) {
            String seriesName = bandMatcher.group(1).trim();
            Float number = parseSeriesNumber(bandMatcher.group(2));
            return new SeriesData(seriesName, number);
        }
        
        Pattern endNumberPattern = Pattern.compile("^(.+?)\\s+(\\d+)$");
        Matcher endNumberMatcher = endNumberPattern.matcher(title);
        if (endNumberMatcher.find()) {
            String seriesName = endNumberMatcher.group(1).trim();
            Float number = parseSeriesNumber(endNumberMatcher.group(2));
            if (number != null && number >= 1 && number <= 9999) {
                return new SeriesData(seriesName, number);
            }
        }
        
        return new SeriesData(null, null);
    }

    private Float parseSeriesNumber(String numberStr) {
        if (numberStr == null || numberStr.isBlank()) {
            return null;
        }
        try {
            return Float.parseFloat(numberStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String cleanTitle(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        // Remove excessive whitespace
        return WHITESPACE_PATTERN.matcher(title.trim()).replaceAll(" ");
    }

    private List<String> cleanAuthors(List<String> authors) {
        if (authors == null || authors.isEmpty()) {
            return List.of();
        }
        return authors.stream()
                .filter(Objects::nonNull)
                .filter(author -> !author.isBlank())
                .map(String::trim)
                .toList();
    }

    private Set<String> cleanCategories(Set<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return Set.of();
        }
        
        return categories.stream()
                .filter(Objects::nonNull)
                .filter(cat -> !cat.isBlank())
                .flatMap(cat -> {
                    // Split hierarchical categories (e.g., "Fiction / Fantasy / General")
                    if (cat.contains(" / ")) {
                        return Arrays.stream(cat.split(" / "))
                                .map(String::trim)
                                .filter(s -> !s.equalsIgnoreCase("General"));
                    }
                    return Stream.of(cat.trim());
                })
                .filter(cat -> !cat.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    String cleanDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        String cleaned = Jsoup.parse(description).text();
        cleaned = WHITESPACE_PATTERN.matcher(cleaned.trim()).replaceAll(" ");
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return null;
        }
        // Google uses ISO 639-1 codes, just ensure lowercase
        return language.toLowerCase().trim();
    }

    private String getExternalUrl(GoogleBooksApiResponse.Item.VolumeInfo volumeInfo) {
        // Prefer canonical volume link, then info link
        if (volumeInfo.getCanonicalVolumeLink() != null && !volumeInfo.getCanonicalVolumeLink().isBlank()) {
            return volumeInfo.getCanonicalVolumeLink();
        }
        if (volumeInfo.getInfoLink() != null && !volumeInfo.getInfoLink().isBlank()) {
            return volumeInfo.getInfoLink();
        }
        return null;
    }

    private String extractBestCoverImage(GoogleBooksApiResponse.Item.ImageLinks links) {
        if (links == null) {
            return null;
        }
        
        // Priority order: extraLarge > large > medium > small > thumbnail > smallThumbnail
        String imageUrl = Stream.of(
                        links.getExtraLarge(),
                        links.getLarge(),
                        links.getMedium(),
                        links.getSmall(),
                        links.getThumbnail(),
                        links.getSmallThumbnail())
                .filter(Objects::nonNull)
                .filter(url -> !url.isBlank())
                .findFirst()
                .orElse(null);
        
        if (imageUrl == null) {
            return null;
        }
        
        imageUrl = imageUrl.replace("http://", "https://");
        
        if (imageUrl.contains("zoom=")) {
            imageUrl = imageUrl.replaceAll("zoom=\\d+", "zoom=0");
        }
        
        imageUrl = imageUrl.replaceAll("&?edge=curl", "");
        
        return imageUrl;
    }

    private Map<String, String> extractISBNs(List<GoogleBooksApiResponse.Item.IndustryIdentifier> identifiers) {
        if (identifiers == null) return Map.of();

        return identifiers.stream()
                .filter(identifier -> "ISBN_13".equals(identifier.getType()) || "ISBN_10".equals(identifier.getType()))
                .collect(Collectors.toMap(
                        GoogleBooksApiResponse.Item.IndustryIdentifier::getType,
                        GoogleBooksApiResponse.Item.IndustryIdentifier::getIdentifier,
                        (existing, replacement) -> existing
                ));
    }

    private String getSearchTerm(Book book, FetchMetadataRequest request) {
        String searchTerm = Optional.ofNullable(request.getTitle())
                .filter(title -> !title.isEmpty())
                .orElseGet(() -> Optional.ofNullable(book.getPrimaryFile())
                        .map(pf -> pf.getFileName())
                        .filter(fileName -> !fileName.isEmpty())
                        .map(BookUtils::cleanFileName)
                        .orElse(null));

        if (searchTerm == null) {
            return null;
        }

        return searchTerm;
    }

    private String truncateToMaxWords(String input) {
        String[] words = WHITESPACE_PATTERN.split(input);
        StringBuilder truncated = new StringBuilder();

        for (String word : words) {
            if (truncated.length() + word.length() + 1 > MAX_SEARCH_TERM_LENGTH) {
                break;
            }
            if (!truncated.isEmpty()) {
                truncated.append(" ");
            }
            truncated.append(word);
        }

        return truncated.toString();
    }

    /**
     * Parse date string from Google Books API.
     * Handles various formats:
     * - "2021-05-15" (full date)
     * - "2021-05" (year-month)
     * - "2021" (year only)
     */
    private LocalDate parseDate(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        
        input = input.trim();
        
        try {
            // Try full date format first (YYYY-MM-DD)
            if (input.length() == 10 && input.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return LocalDate.parse(input, DATE_FORMATTER);
            }
            
            // Try year-month format (YYYY-MM)
            Matcher yearMonthMatcher = YEAR_MONTH_PATTERN.matcher(input);
            if (yearMonthMatcher.matches()) {
                int year = Integer.parseInt(yearMonthMatcher.group(1));
                int month = Integer.parseInt(yearMonthMatcher.group(2));
                // Validate month
                if (month >= 1 && month <= 12) {
                    return LocalDate.of(year, month, 1);
                }
            }
            
            // Try year only format (YYYY)
            Matcher yearMatcher = FOUR_DIGIT_YEAR_PATTERN.matcher(input);
            if (yearMatcher.matches()) {
                int year = Integer.parseInt(yearMatcher.group(1));
                // Basic year validation
                if (year >= 1000 && year <= 9999) {
                    return LocalDate.of(year, 1, 1);
                }
            }
            
            // Try parsing as ISO date as fallback
            return LocalDate.parse(input);
        } catch (DateTimeParseException | NumberFormatException e) {
            log.debug("Could not parse date '{}': {}", input, e.getMessage());
            return null;
        }
    }

    private String getApiUrl() {
        MetadataProviderSettings.Google googleSettings = appSettingService.getAppSettings()
                .getMetadataProviderSettings().getGoogle();

        String language = googleSettings.getLanguage();
        String apiKey = googleSettings.getApiKey();

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(GOOGLE_BOOKS_API_URL);

        if (language != null && !language.isEmpty()) {
            builder.queryParam("langRestrict", language);
        }

        if (apiKey != null && !apiKey.isBlank()) {
            builder.queryParam("key", apiKey);
        }

        return builder.build().toUri().toString();
    }

    private void waitForRateLimit() {
        long now = System.currentTimeMillis();
        long timeSinceLastRequest = now - lastRequestTime.get();
        if (timeSinceLastRequest < MIN_REQUEST_INTERVAL_MS) {
            try {
                Thread.sleep(MIN_REQUEST_INTERVAL_MS - timeSinceLastRequest);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastRequestTime.set(System.currentTimeMillis());
    }

    /**
     * Sort results by metadata completeness.
     * Items with more populated fields come first.
     */
    private List<BookMetadata> sortByCompleteness(List<BookMetadata> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        
        return results.stream()
                .sorted((a, b) -> Integer.compare(
                        countPopulatedFields(b),
                        countPopulatedFields(a)))
                .collect(Collectors.toList());
    }

    private int countPopulatedFields(BookMetadata metadata) {
        int count = 0;
        
        if (metadata.getTitle() != null && !metadata.getTitle().isBlank()) count++;
        if (metadata.getSubtitle() != null && !metadata.getSubtitle().isBlank()) count++;
        if (metadata.getAuthors() != null && !metadata.getAuthors().isEmpty()) count++;
        if (metadata.getPublisher() != null && !metadata.getPublisher().isBlank()) count++;
        if (metadata.getPublishedDate() != null) count++;
        if (metadata.getDescription() != null && !metadata.getDescription().isBlank()) count++;
        if (metadata.getIsbn13() != null && !metadata.getIsbn13().isBlank()) count++;
        if (metadata.getIsbn10() != null && !metadata.getIsbn10().isBlank()) count++;
        if (metadata.getPageCount() != null && metadata.getPageCount() > 0) count++;
        if (metadata.getLanguage() != null && !metadata.getLanguage().isBlank()) count++;
        if (metadata.getCategories() != null && !metadata.getCategories().isEmpty()) count++;
        if (metadata.getThumbnailUrl() != null && !metadata.getThumbnailUrl().isBlank()) count++;
        if (metadata.getSeriesName() != null && !metadata.getSeriesName().isBlank()) count++;
        if (metadata.getSeriesNumber() != null) count++;
        if (metadata.getGoogleId() != null && !metadata.getGoogleId().isBlank()) count++;
        
        return count;
    }
}



