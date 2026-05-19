package org.booklore.service.metadata.parser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.request.FetchMetadataRequest;
import org.booklore.model.dto.response.ItunesApiResponse;
import org.booklore.model.dto.settings.MetadataProviderSettings;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.MetadataProvider;
import org.booklore.service.appsettings.AppSettingService;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
@Service
public class ItunesParser implements BookParser, DetailedMetadataProvider {

    private static final Pattern NON_ALPHANUMERIC_PATTERN = Pattern.compile("[^a-z0-9\\s]");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");
    private static final Pattern ARTWORK_SIZE_PATTERN = Pattern.compile("\\d+x\\d+(?:bb)?");

    private final ObjectMapper objectMapper;
    private final AppSettingService appSettingService;
    private final ItunesHttpClient itunesHttpClient;
    
    private static final String ITUNES_SEARCH_URL = "https://itunes.apple.com/search";
    private static final String ITUNES_LOOKUP_URL = "https://itunes.apple.com/lookup";
    private static final int DEFAULT_LIMIT = 20;
    private static final Set<String> VALID_WRAPPER_TYPES = Set.of("track", "collection", "audiobook");

    @Override
    public BookMetadata fetchTopMetadata(Book book, FetchMetadataRequest request) {
        var results = fetchMetadata(book, request);
        return results.isEmpty() ? null : results.getFirst();
    }

    @Override
    public List<BookMetadata> fetchMetadata(Book book, FetchMetadataRequest request) {
        var itunesSettings = appSettingService.getAppSettings().getMetadataProviderSettings().getItunes();
        if (itunesSettings == null || !itunesSettings.isEnabled()) return List.of();

        var entity = determineEntity(book);
        var country = getCountry();

        if (request.getIsbn() != null && !request.getIsbn().isBlank()) {
            var cleanIsbn = ParserUtils.cleanIsbn(request.getIsbn());
            log.info("iTunes: Searching with ISBN: {}", cleanIsbn);
            var results = fetchFromApi(ITUNES_LOOKUP_URL, Map.of("isbn", cleanIsbn, "entity", entity), country);
            if (!results.isEmpty()) return results;
        }

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            var term = request.getAuthor() != null && !request.getAuthor().isBlank() ? request.getTitle() + " " + request.getAuthor() : request.getTitle();
            log.info("iTunes: Searching with term: {}", term);
            var results = fetchFromApi(ITUNES_SEARCH_URL, Map.of("term", term, "entity", entity, "limit", String.valueOf(DEFAULT_LIMIT)), country);
            return rankAndFilterResults(results, request);
        }

        return List.of();
    }

    @Override
    public BookMetadata fetchDetailedMetadata(String providerItemId) {
        log.info("iTunes: Fetching detailed metadata for providerItemId: {}", providerItemId);
        String cleanId = providerItemId;
        if (providerItemId != null && providerItemId.contains(":")) {
            cleanId = providerItemId.substring(providerItemId.indexOf(':') + 1);
        }
        var results = fetchFromApi(ITUNES_LOOKUP_URL, Map.of("id", cleanId), getCountry());
        return results.isEmpty() ? null : results.getFirst();
    }

    private String getCountry() {
        return Optional.ofNullable(appSettingService.getAppSettings().getMetadataProviderSettings().getItunes())
                .map(MetadataProviderSettings.Itunes::getCountry)
                .filter(Predicate.not(String::isBlank))
                .map(c -> c.toUpperCase(Locale.ROOT))
                .orElse("US");
    }

    private static String determineEntity(Book book) {
        return book != null && book.getPrimaryFile() != null && book.getPrimaryFile().getBookType() == BookFileType.AUDIOBOOK ? "audiobook" : "ebook";
    }

    public static String resizeArtworkUrl(String url, int width, int height) {
        if (url == null) return null;
        var matcher = ARTWORK_SIZE_PATTERN.matcher(url);
        if (matcher.find()) {
            return matcher.replaceFirst(width + "x" + height + "bb");
        }
        return url;
    }

    private List<BookMetadata> fetchFromApi(String baseUrl, Map<String, String> params, String country) {
        try {
            var responseBody = itunesHttpClient.executeGet(baseUrl, params, country);
            return parseItunesApiResponse(responseBody);
        } catch (IOException e) {
            log.error("Error fetching metadata from iTunes API: {}", e.getMessage());
        } catch (InterruptedException e) {
            log.error("iTunes API request interrupted: {}", e.getMessage());
            Thread.currentThread().interrupt();
        }
        return List.of();
    }

    private List<BookMetadata> parseItunesApiResponse(String responseBody) throws IOException {
        try {
            var apiResponse = objectMapper.readValue(responseBody, ItunesApiResponse.class);
            if (apiResponse == null || apiResponse.results() == null) return List.of();

            return apiResponse.results().stream()
                    .filter(r -> (r.wrapperType() != null && VALID_WRAPPER_TYPES.contains(r.wrapperType())) || 
                                 (r.wrapperType() == null && (r.trackId() != null || r.collectionId() != null)))
                    .map(ItunesParser::convertToBookMetadata)
                    .toList();
        } catch (Exception e) {
            log.error("Failed to parse iTunes API response. Response preview: {}", 
                    responseBody != null ? responseBody.substring(0, Math.min(responseBody.length(), 500)) : "null", e);
            if (e instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Failed to parse response body", e);
        }
    }

    private static BookMetadata convertToBookMetadata(ItunesApiResponse.Result result) {
        var description = result.description() != null ? Jsoup.clean(result.description(), Safelist.basic()) : null;
        var thumbnailUrl = resizeArtworkUrl(result.artworkUrl100(), 600, 600);
        
        LocalDate publishedDate = null;
        if (result.releaseDate() != null) {
            try {
                publishedDate = OffsetDateTime.parse(result.releaseDate()).toLocalDate();
            } catch (DateTimeParseException ignored) {}
        }

        var trackId = Optional.ofNullable(result.trackId())
                .map(tid -> "track:" + tid)
                .or(() -> Optional.ofNullable(result.collectionId()).map(cid -> "collection:" + cid))
                .orElse(null);
        var title = result.trackName() != null ? result.trackName() : result.collectionName();
        var externalUrl = result.trackViewUrl() != null ? result.trackViewUrl() : result.collectionViewUrl();

        var categories = new LinkedHashSet<String>();
        if (result.genres() != null) categories.addAll(result.genres());
        else if (result.primaryGenreName() != null) categories.add(result.primaryGenreName());

        return BookMetadata.builder()
                .provider(MetadataProvider.Itunes)
                .itunesId(trackId)
                .title(title)
                .authors(result.artistName() != null ? List.of(result.artistName()) : List.of())
                .description(description)
                .publishedDate(publishedDate)
                .categories(categories)
                .thumbnailUrl(thumbnailUrl)
                .itunesRating(result.averageUserRating())
                .itunesReviewCount(result.userRatingCount())
                .externalUrl(externalUrl)
                .language(result.language() != null ? result.language().toLowerCase(Locale.ROOT) : null)
                .build();
    }

    private static List<BookMetadata> rankAndFilterResults(List<BookMetadata> results, FetchMetadataRequest request) {
        if (request.getTitle() == null || request.getTitle().isBlank() || results.isEmpty()) {
            return results;
        }

        List<String> queryNumbers = extractAllNumbers(request.getTitle());

        // First, compute scores for all results
        List<ScoredResult> scoredResults = results.stream()
                .map(metadata -> {
                    double score = computeRelevanceScore(metadata, request, queryNumbers);
                    return new ScoredResult(metadata, score);
                })
                .toList();

        // Check if there is any result with a strong title match (overlap + bonus >= 1.0)
        boolean hasAnyTitleMatch = scoredResults.stream()
                .anyMatch(sr -> sr.score >= 1.0);

        return scoredResults.stream()
                .filter(sr -> {
                    if (sr.score < 0.0) {
                        return false; // Filter out volume/number mismatches
                    }
                    return !hasAnyTitleMatch || !(sr.score < 1.0); // Filter out zero title overlap if we have actual title matches
                })
                .sorted(Comparator.comparingDouble((ScoredResult sr) -> sr.score).reversed())
                .map(sr -> sr.metadata)
                .toList();
    }

    private static final Map<String, String> WORD_TO_DIGIT = Map.ofEntries(
        Map.entry("one", "1"), Map.entry("first", "1"), Map.entry("vol1", "1"), Map.entry("vol.1", "1"),
        Map.entry("two", "2"), Map.entry("second", "2"), Map.entry("vol2", "2"), Map.entry("vol.2", "2"),
        Map.entry("three", "3"), Map.entry("third", "3"), Map.entry("vol3", "3"), Map.entry("vol.3", "3"),
        Map.entry("four", "4"), Map.entry("fourth", "4"), Map.entry("vol4", "4"), Map.entry("vol.4", "4"),
        Map.entry("five", "5"), Map.entry("fifth", "5"), Map.entry("vol5", "5"), Map.entry("vol.5", "5"),
        Map.entry("six", "6"), Map.entry("sixth", "6"), Map.entry("vol6", "6"), Map.entry("vol.6", "6"),
        Map.entry("seven", "7"), Map.entry("seventh", "7"), Map.entry("vol7", "7"), Map.entry("vol.7", "7"),
        Map.entry("eight", "8"), Map.entry("eighth", "8"), Map.entry("vol8", "8"), Map.entry("vol.8", "8"),
        Map.entry("nine", "9"), Map.entry("ninth", "9"), Map.entry("vol9", "9"), Map.entry("vol.9", "9"),
        Map.entry("ten", "10"), Map.entry("tenth", "10"), Map.entry("vol10", "10"), Map.entry("vol.10", "10")
    );

    private static List<String> extractAllNumbers(String text) {
        if (text == null) return List.of();
        List<String> numbers = new ArrayList<>();
        String normalized = NON_ALPHANUMERIC_PATTERN.matcher(text.toLowerCase()).replaceAll(" ");
        String[] tokens = WHITESPACE_PATTERN.split(normalized);
        for (String token : tokens) {
            if (NUMBER_PATTERN.matcher(token).matches()) {
                numbers.add(token);
            } else if (WORD_TO_DIGIT.containsKey(token)) {
                numbers.add(WORD_TO_DIGIT.get(token));
            }
        }
        return numbers;
    }

    private static double computeRelevanceScore(BookMetadata metadata, FetchMetadataRequest request, List<String> queryNumbers) {
        String resultTitle = metadata.getTitle();
        if (resultTitle == null || resultTitle.isBlank()) return 0.0;

        // Normalize both strings to lowercase and strip all punctuation/special characters
        String qClean = request.getTitle() != null ? NON_ALPHANUMERIC_PATTERN.matcher(request.getTitle().toLowerCase()).replaceAll(" ") : "";
        String rClean = NON_ALPHANUMERIC_PATTERN.matcher(resultTitle.toLowerCase()).replaceAll(" ");

        List<String> resultNumbers = extractAllNumbers(resultTitle);
        if (!queryNumbers.isEmpty()) {
            boolean hasAllNumbers = new HashSet<>(resultNumbers).containsAll(queryNumbers);
            if (!hasAllNumbers) {
                boolean hasDifferentNumbers = resultNumbers.stream().anyMatch(n -> !queryNumbers.contains(n));
                if (hasDifferentNumbers) {
                    return -1.0;
                }
            }
        }

        double overlap = 0.0;
        if (!qClean.isBlank()) {
            String[] qTokens = WHITESPACE_PATTERN.split(qClean);
            List<String> filteredQTokens = Arrays.stream(qTokens)
                    .filter(token -> NUMBER_PATTERN.matcher(token).matches() || token.length() > 2)
                    .toList();

            if (!filteredQTokens.isEmpty()) {
                long matchedTokens = filteredQTokens.stream()
                        .filter(rClean::contains)
                        .count();
                overlap = (double) matchedTokens / filteredQTokens.size();
            }
        }

        double bonus = 0.0;
        if (!qClean.isBlank()) {
            // Check substring match on normalized clean strings (collapsing multiple spaces)
            String qCollapsed = WHITESPACE_PATTERN.matcher(qClean).replaceAll(" ").trim();
            String rCollapsed = WHITESPACE_PATTERN.matcher(rClean).replaceAll(" ").trim();
            if (!qCollapsed.isEmpty() && !rCollapsed.isEmpty() && (rCollapsed.contains(qCollapsed) || qCollapsed.contains(rCollapsed))) {
                bonus = 1.0;
            }
        }

        double authorBonus = 0.0;
        if (request.getAuthor() != null && !request.getAuthor().isBlank() && metadata.getAuthors() != null) {
            String qAuthorLower = WHITESPACE_PATTERN.matcher(NON_ALPHANUMERIC_PATTERN.matcher(request.getAuthor().toLowerCase()).replaceAll(" ")).replaceAll(" ").trim();
            boolean authorMatched = metadata.getAuthors().stream()
                    .filter(java.util.Objects::nonNull)
                    .map(a -> WHITESPACE_PATTERN.matcher(NON_ALPHANUMERIC_PATTERN.matcher(a.toLowerCase()).replaceAll(" ")).replaceAll(" ").trim())
                    .anyMatch(a -> a.contains(qAuthorLower) || qAuthorLower.contains(a));
            if (authorMatched) {
                authorBonus = 0.5;
            }
        }

        return overlap + bonus + authorBonus;
    }

    private record ScoredResult(BookMetadata metadata, double score) {
    }
}
