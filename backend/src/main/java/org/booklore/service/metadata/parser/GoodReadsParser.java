package org.booklore.service.metadata.parser;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.BookReview;
import org.booklore.model.dto.request.FetchMetadataRequest;
import org.booklore.model.enums.MetadataProvider;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.util.BookUtils;
import org.booklore.util.LanguageNormalizer;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import tools.jackson.core.type.TypeReference;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

@Slf4j
@Service
@AllArgsConstructor
public class GoodReadsParser implements BookParser, DetailedMetadataProvider {
    private static final TypeReference<List<GoodreadsAutocompleteEntry>> AUTOCOMPLETE_RESPONSE_TYPE = new TypeReference<>() {};

    private static final String GRAPHQL_ENDPOINT = "https://kxbwmqov6jgg3daaamb744ycu4.appsync-api.us-east-1.amazonaws.com/graphql";
    private static final String API_KEY = "da2-xpgsdydkbregjhpr6ejzqdhuwy";
    private static final String GRAPHQL_QUERY = "query getBookPageData($legacyBookId:Int!){getBookByLegacyId(legacyId:$legacyBookId){title description imageUrl primaryContributorEdge{node{name}}secondaryContributorEdges{node{name}}bookSeries{userPosition series{title}}bookGenres{genre{name}}details{numPages publicationTime publisher isbn isbn13 language{name}}work{stats{averageRating ratingsCount}}}}";

    private static final String BASE_AUTOCOMPLETE_URL = "https://www.goodreads.com/book/auto_complete?format=json&q=";
    private static final String BASE_ISBN_URL = "https://www.goodreads.com/book/isbn/";
    private static final int COUNT_DETAILED_METADATA_TO_GET = 3;

    private final HttpClient httpClient;
    private final AppSettingService appSettingService;
    private final ObjectMapper objectMapper;

    private record TitleInfo(String title, String subtitle) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GoodreadsAutocompleteEntry(String bookId) {
    }

    @Override
    public BookMetadata fetchTopMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        String existingGoodreadsId = getExistingGoodreadsId(book);
        if (existingGoodreadsId != null) {
            log.info("GoodReads: Using existing Goodreads ID: {}", existingGoodreadsId);
            try {
                BookMetadata metadata = fetchAndParseBook(existingGoodreadsId);
                if (metadata != null) {
                    return metadata;
                }
                log.warn("GoodReads: Failed to parse details for existing ID: {}, falling back to search",
                        existingGoodreadsId);
            } catch (Exception e) {
                log.warn("GoodReads: Error fetching existing ID {}: {}, falling back to search", existingGoodreadsId,
                        e.getMessage());
            }
        }

        return fetchMetadataStream(book, fetchMetadataRequest).blockFirst();
    }

    private String getExistingGoodreadsId(Book book) {
        if (book == null || book.getMetadata() == null) {
            return null;
        }
        String goodreadsId = book.getMetadata().getGoodreadsId();
        if (goodreadsId == null || goodreadsId.isBlank()) {
            return null;
        }
        String numericId = goodreadsId.split("-")[0].split("\\.")[0];
        try {
            Long.parseLong(numericId);
            return goodreadsId;
        } catch (NumberFormatException e) {
            log.debug("GoodReads: Invalid Goodreads ID format: {}", goodreadsId);
            return null;
        }
    }

    @Override
    public List<BookMetadata> fetchMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        return fetchMetadataStream(book, fetchMetadataRequest).collectList().block();
    }

    @Override
    public Flux<BookMetadata> fetchMetadataStream(Book book, FetchMetadataRequest fetchMetadataRequest) {
        return Flux.create(sink -> {
            try {
                String isbn = ParserUtils.cleanIsbn(fetchMetadataRequest.getIsbn());
                if (isbn != null && !isbn.isBlank()) {
                    try {
                        String legacyId = resolveIsbn(isbn);
                        BookMetadata metadata = fetchAndParseBook(legacyId);
                        if (metadata != null) {
                            sink.next(metadata);
                        }
                    } catch (Exception e) {
                        log.warn("GoodReads: ISBN lookup failed: {}, falling back to title search", e.getMessage());
                    }
                }

                List<String> searchResultIds = fetchSearchResults(book, fetchMetadataRequest).stream()
                        .limit(COUNT_DETAILED_METADATA_TO_GET)
                        .toList();

                for (String goodreadsId : searchResultIds) {
                    if (sink.isCancelled())
                        return;
                    log.info("GoodReads: Fetching metadata for: Goodreads ID {}", goodreadsId);
                    try {
                        BookMetadata metadata = fetchAndParseBook(goodreadsId);
                        if (metadata != null) {
                            sink.next(metadata);
                        }
                        Thread.sleep(ThreadLocalRandom.current().nextLong(500, 1501));
                    } catch (InterruptedException e) {
                        throw e;
                    } catch (Exception e) {
                        log.error("Error fetching metadata for book: {}", goodreadsId, e);
                    }
                }

                sink.complete();
            } catch (Exception e) {
                sink.error(e);
            }
        });
    }

    private BookMetadata fetchAndParseBook(String goodreadsId) {
        JsonNode bookNode = fetchBookFromGraphql(goodreadsId);
        if (bookNode == null)
            return null;
        return parseBookDetails(bookNode, goodreadsId);
    }

    private String resolveIsbn(String isbn) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_ISBN_URL + isbn))
                .GET()
                .build();
        HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        URI finalUri = response.uri();
        String path = finalUri.getPath();
        String id = path.substring(path.lastIndexOf('/') + 1);
        log.info("GoodReads: ISBN {} resolved to legacyId {}", isbn, id);
        return id;
    }

    private JsonNode fetchBookFromGraphql(String goodreadsId) {
        String numericPart = goodreadsId.split("-")[0].split("\\.")[0];
        try {
            int legacyId = Integer.parseInt(numericPart);

            ObjectNode payload = objectMapper.createObjectNode()
                    .put("operationName", "getBookPageData")
                    .set("variables", objectMapper.createObjectNode().put("legacyBookId", legacyId))
                    .put("query", GRAPHQL_QUERY);
            String requestBody = payload.toString();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GRAPHQL_ENDPOINT))
                    .header("x-api-key", API_KEY)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() > 399) {
                String errorBody = response.body();
                log.error("GraphQL request failed with status: {}, body: {}", response.statusCode(),
                        errorBody != null ? errorBody.substring(0, Math.min(errorBody.length(), 500)) : "null");
                return null;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode errors = root.get("errors");
            if (errors != null && errors.isArray() && errors.size() > 0) {
                log.error("GraphQL returned errors: {}", errors);
                return null;
            }

            return root.path("data").path("getBookByLegacyId");
        } catch (NumberFormatException e) {
            log.error("Invalid Goodreads ID format: {}", goodreadsId);
            return null;
        } catch (Exception e) {
            log.error("GraphQL request failed for ID: {}", goodreadsId, e);
            return null;
        }
    }

    private BookMetadata parseBookDetails(JsonNode bookNode, String goodreadsId) {
        if (bookNode == null || bookNode.isMissingNode())
            return null;

        BookMetadata.BookMetadataBuilder builder = BookMetadata.builder()
                .goodreadsId(goodreadsId)
                .provider(MetadataProvider.GoodReads);

        try {
            extractContributorDetails(bookNode, builder);
            extractSeriesDetails(bookNode, builder);
            extractBookDetails(bookNode, builder);
            extractWorkDetails(bookNode, builder);

            appSettingService.getAppSettings()
                    .getMetadataPublicReviewsSettings()
                    .getProviders()
                    .stream()
                    .filter(cfg -> cfg.getProvider() == MetadataProvider.GoodReads && cfg.isEnabled())
                    .findFirst()
                    .ifPresent(cfg -> extractReviews(bookNode, builder, cfg.getMaxReviews()));

        } catch (Exception e) {
            log.error("Error parsing book details for providerBookId: {}", goodreadsId, e);
            return null;
        }

        return builder.build();
    }

    private void extractContributorDetails(JsonNode bookNode, BookMetadata.BookMetadataBuilder builder) {
        List<String> authors = new ArrayList<>();

        JsonNode primaryEdge = bookNode.get("primaryContributorEdge");
        if (primaryEdge != null && primaryEdge.isObject()) {
            JsonNode node = primaryEdge.get("node");
            if (node != null) {
                String name = node.path("name").asText(null);
                if (name != null) {
                    authors.add(name);
                }
            }
        }

        JsonNode secondaryEdges = bookNode.get("secondaryContributorEdges");
        if (secondaryEdges != null && secondaryEdges.isArray()) {
            for (int i = 0; i < secondaryEdges.size(); i++) {
                JsonNode node = secondaryEdges.get(i).path("node");
                String name = node.path("name").asText(null);
                if (name != null) {
                    authors.add(name);
                }
            }
        }

        if (!authors.isEmpty()) {
            builder.authors(authors);
        }
    }

    private void extractReviews(JsonNode bookNode, BookMetadata.BookMetadataBuilder builder, int maxReviews) {
        List<BookReview> reviews = new ArrayList<>();

        JsonNode work = bookNode.get("work");
        if (work == null || !work.isObject())
            return;

        JsonNode reviewsJson = work.get("reviews");
        if (reviewsJson == null || !reviewsJson.isObject()) {
            reviewsJson = work.get("reviewStats");
            if (reviewsJson == null)
                return;
        }

        JsonNode edges = reviewsJson.get("edges");
        if (edges == null || !edges.isArray())
            return;

        int count = 0;
        for (int i = 0; i < edges.size() && count < maxReviews; i++) {
            JsonNode reviewNode = edges.get(i).path("node");
            if (reviewNode == null || reviewNode.isMissingNode())
                continue;

            try {
                String rawBody = reviewNode.path("text").asText(null);
                String plainBody = rawBody != null ? Jsoup.parse(rawBody).text() : null;
                if (plainBody == null || plainBody.trim().isEmpty())
                    continue;

                JsonNode creator = reviewNode.get("creator");
                String reviewerName = creator != null ? creator.path("name").asText(null) : null;
                Integer followersCount = null;
                Integer textReviewsCount = null;
                if (creator != null) {
                    JsonNode fn = creator.path("followersCount");
                    if (fn.canConvertToInt())
                        followersCount = fn.asInt();
                    JsonNode trn = creator.path("textReviewsCount");
                    if (trn.canConvertToInt())
                        textReviewsCount = trn.asInt();
                }

                JsonNode updatedAtNode = reviewNode.path("updatedAt");
                BookReview review = BookReview.builder()
                        .metadataProvider(MetadataProvider.GoodReads)
                        .date(updatedAtNode.isIntegralNumber() ? Instant.ofEpochMilli(updatedAtNode.asLong()) : null)
                        .body(plainBody.trim())
                        .rating(Float.valueOf(reviewNode.path("rating").asText("0")))
                        .spoiler(reviewNode.path("spoilerStatus").asBoolean(false))
                        .reviewerName(reviewerName != null ? reviewerName.trim() : null)
                        .followersCount(followersCount)
                        .textReviewsCount(textReviewsCount)
                        .build();
                reviews.add(review);
                count++;
            } catch (Exception e) {
                log.error("Error parsing review at index {}: {}", i, e.getMessage());
            }
        }

        builder.bookReviews(reviews);
    }

    private void extractSeriesDetails(JsonNode bookNode, BookMetadata.BookMetadataBuilder builder) {
        JsonNode bookSeries = bookNode.get("bookSeries");
        if (bookSeries != null && bookSeries.isArray() && bookSeries.size() > 0) {
            JsonNode first = bookSeries.get(0);
            JsonNode series = first.path("series");
            String seriesName = series.path("title").asText(null);
            if (seriesName != null) {
                builder.seriesName(seriesName);
            }
            builder.seriesNumber(parseNumber(first.path("userPosition").asText(null), Float::parseFloat));
        }
    }

    private void extractBookDetails(JsonNode bookNode, BookMetadata.BookMetadataBuilder builder) {
        TitleInfo titleInfo = parseTitleInfo(bookNode.path("title").asText(null));
        builder.title(titleInfo.title())
                .subtitle(titleInfo.subtitle())
                .description(normalizeNull(bookNode.path("description").asText(null)))
                .thumbnailUrl(normalizeNull(bookNode.path("imageUrl").asText(null)))
                .categories(extractGenres(bookNode));

        JsonNode detailsJson = bookNode.get("details");
        if (detailsJson != null && detailsJson.isObject()) {
            builder.pageCount(parseNumber(detailsJson.path("numPages").asText(null), Integer::parseInt))
                    .publishedDate(convertToLocalDate(detailsJson.path("publicationTime")))
                    .publisher(normalizeNull(detailsJson.path("publisher").asText(null)))
                    .isbn10(normalizeNull(detailsJson.path("isbn").asText(null)))
                    .isbn13(normalizeNull(detailsJson.path("isbn13").asText(null)));

            JsonNode languageJson = detailsJson.get("language");
            if (languageJson != null && languageJson.isObject()) {
                builder.language(LanguageNormalizer.normalize(normalizeNull(languageJson.path("name").asText(null))));
            }
        }
    }

    private void extractWorkDetails(JsonNode bookNode, BookMetadata.BookMetadataBuilder builder) {
        JsonNode work = bookNode.get("work");
        if (work == null || !work.isObject())
            return;

        JsonNode statsJson = work.get("stats");
        if (statsJson != null && statsJson.isObject()) {
            builder.goodreadsRating(parseNumber(statsJson.path("averageRating").asText(null), Double::parseDouble))
                    .goodreadsReviewCount(parseNumber(statsJson.path("ratingsCount").asText(null), Integer::parseInt));
        }
    }

    private Set<String> extractGenres(JsonNode bookNode) {
        try {
            Set<String> genres = new HashSet<>();
            JsonNode bookGenresArray = bookNode.get("bookGenres");
            if (bookGenresArray != null && bookGenresArray.isArray()) {
                for (int i = 0; i < bookGenresArray.size(); i++) {
                    JsonNode genreJson = bookGenresArray.get(i).path("genre");
                    genres.add(genreJson.path("name").asText());
                }
            }
            return genres;
        } catch (Exception e) {
            log.error("Error extracting genres", e);
            return null;
        }
    }

    private TitleInfo parseTitleInfo(String fullTitle) {
        if (fullTitle == null || "null".equals(fullTitle)) {
            return new TitleInfo(null, null);
        }
        String[] parts = fullTitle.split(":", 2);
        String title = parts[0].trim();
        String subtitle = parts.length > 1 ? parts[1].trim() : null;
        return new TitleInfo(title.isEmpty() ? null : title, subtitle);
    }

    private <T extends Number> T parseNumber(String value, Function<String, T> parser) {
        if (value == null || value.isEmpty() || "null".equals(value)) {
            return null;
        }
        try {
            return parser.apply(value);
        } catch (NumberFormatException e) {
            log.warn("Error parsing number: {}", value);
            return null;
        }
    }

    private String normalizeNull(String s) {
        return "null".equals(s) || (s != null && s.isEmpty()) ? null : s;
    }

    private LocalDate convertToLocalDate(JsonNode publicationTimeNode) {
        if (publicationTimeNode == null || publicationTimeNode.isMissingNode() || publicationTimeNode.isNull()) {
            return null;
        }
        try {
            long millis;
            if (publicationTimeNode.isNumber()) {
                millis = publicationTimeNode.asLong();
            } else {
                String text = publicationTimeNode.asText(null);
                if (text == null || text.isBlank() || "null".equals(text))
                    return null;
                millis = Long.parseLong(text);
            }
            return Instant.ofEpochMilli(millis)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
        } catch (Exception e) {
            log.error("Invalid publication time: {}, Error: {}", publicationTimeNode, e.getMessage());
            return null;
        }
    }

    public String generateSearchUrl(String searchTerm) {
        String encodedSearchTerm = URLEncoder.encode(searchTerm, StandardCharsets.UTF_8);
        String url = BASE_AUTOCOMPLETE_URL + encodedSearchTerm;
        log.info("Goodreads Query URL: {}", url);
        return url;
    }

    public List<String> fetchSearchResults(Book book, FetchMetadataRequest request) throws InterruptedException {
        String searchTerm = getSearchTerm(book, request);

        if (searchTerm == null || searchTerm.isEmpty()) {
            log.info("GoodReads: No metadata previews found (no ISBN, title, or filename).");
            return Collections.emptyList();
        }

        try {
            String searchUrl = generateSearchUrl(searchTerm);

            List<GoodreadsAutocompleteEntry> records = fetchJson(
                    searchUrl,
                    AUTOCOMPLETE_RESPONSE_TYPE
            );

            Thread.sleep(Duration.ofSeconds(1));
            return records.stream()
                    .filter(Objects::nonNull)
                    .map(r -> r.bookId)
                    .filter(Objects::nonNull)
                    .filter(id -> !id.isBlank())
                    .toList();

        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching metadata previews: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private String getSearchTerm(Book book, FetchMetadataRequest request) {
        if (request.getTitle() != null && !request.getTitle().isEmpty()) {
            // We used to include the author name, but with the new autocomplete
            // endpoint it leads to less reliable results.
            return request.getTitle();
        }
        return (book.getPrimaryFile() != null && book.getPrimaryFile().getFileName() != null && !book.getPrimaryFile().getFileName().isEmpty()
                ? BookUtils.cleanFileName(book.getPrimaryFile().getFileName())
                : null);
    }

    @Override
    public BookMetadata fetchDetailedMetadata(String goodreadsId) {
        log.info("GoodReads: Fetching detailed metadata for ID: {}", goodreadsId);
        try {
            return fetchAndParseBook(goodreadsId);
        } catch (Exception e) {
            log.error("Error fetching detailed metadata for GoodReads ID: {}", goodreadsId, e);
            return null;
        }
    }

    private <T> T fetchJson(String url, TypeReference<T> typeReference) throws InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() > 399) {
                log.error("GoodReads request failed with status code: {}", response.statusCode());
                throw new RuntimeException("Failed to query GoodReads");
            }

            return objectMapper.readValue(response.body(), typeReference);
        } catch (InterruptedException e) {
            throw e;
        } catch (IOException e) {
            log.error("GoodReads request failed", e);
            throw new RuntimeException(e);
        }
    }
}
