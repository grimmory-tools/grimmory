package org.grimmory.service.metadata.parser;

import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.BookReview;
import org.booklore.model.dto.request.FetchMetadataRequest;
import org.booklore.model.enums.MetadataProvider;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.util.BookUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.similarity.FuzzyScore;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@AllArgsConstructor
public class GoodReadsParser implements BookParser, DetailedMetadataProvider {

    private static final String BASE_SEARCH_URL = "https://www.goodreads.com/search?q=";
    private static final String BASE_BOOK_URL = "https://www.goodreads.com/book/show/";
    private static final String BASE_ISBN_URL = "https://www.goodreads.com/book/isbn/";
    private static final int COUNT_DETAILED_METADATA_TO_GET = 3;
    private static final int COUNT_DETAILED_METADATA_TO_GET_RETRY = 2;
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern BOOK_SHOW_ID_PATTERN = Pattern.compile("/book/show/(\\d+)");

    private final AppSettingService appSettingService;

    private record TitleInfo(String title, String subtitle) {}

    @Override
    public BookMetadata fetchTopMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        String existingGoodreadsId = getExistingGoodreadsId(book);
        if (existingGoodreadsId != null) {
            log.info("GoodReads: Using existing Goodreads ID: {}", existingGoodreadsId);
            try {
                Document document = fetchDoc(BASE_BOOK_URL + existingGoodreadsId);
                BookMetadata metadata = parseBookDetails(document, existingGoodreadsId);
                if (metadata != null) {
                    return metadata;
                }
                log.warn("GoodReads: Failed to parse details for existing ID: {}, falling back to search", existingGoodreadsId);
            } catch (Exception e) {
                log.warn("GoodReads: Error fetching existing ID {}: {}, falling back to search", existingGoodreadsId, e.getMessage());
            }
        }

        String isbn = ParserUtils.cleanIsbn(fetchMetadataRequest.getIsbn());
        if (isbn != null && !isbn.isBlank()) {
            log.info("GoodReads: Trying ISBN lookup: {}", isbn);
            try {
                Document doc = fetchDoc(BASE_ISBN_URL + isbn);
                String goodreadsId = extractGoodreadsIdFromOgUrl(doc);
                if (goodreadsId != null) {
                    BookMetadata metadata = parseBookDetails(doc, goodreadsId);
                    if (metadata != null) {
                        return metadata;
                    }
                }
                log.info("GoodReads: ISBN lookup returned no results, falling back to title search");
            } catch (Exception e) {
                log.warn("GoodReads: ISBN lookup failed: {}, falling back to title search", e.getMessage());
            }
        }

        Optional<BookMetadata> preview = fetchMetadataPreviews(book, fetchMetadataRequest).stream().findFirst();
        if (preview.isEmpty()) {
            return null;
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

    private String extractGoodreadsIdFromOgUrl(Document doc) {
        String ogUrl = Optional.ofNullable(doc.selectFirst("meta[property=og:url]"))
                .map(e -> e.attr("content"))
                .orElse(null);
        if (ogUrl == null || ogUrl.isBlank()) {
            return null;
        }
        try {
            String path = new URI(ogUrl).getPath();
            if (path == null || path.isBlank()) {
                return null;
            }
            String id = path.substring(path.lastIndexOf('/') + 1);
            return id.isBlank() ? null : id;
        } catch (URISyntaxException e) {
            log.warn("GoodReads: Could not parse og:url '{}': {}", ogUrl, e.getMessage());
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
                        log.info("Goodreads Query URL (ISBN): {}{}", BASE_ISBN_URL, isbn);
                        Document doc = fetchDoc(BASE_ISBN_URL + isbn);
                        String goodreadsId = extractGoodreadsIdFromOgUrl(doc);
                        if (goodreadsId != null) {
                            BookMetadata metadata = parseBookDetails(doc, goodreadsId);
                            if (metadata != null) {
                                sink.next(metadata);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("GoodReads: ISBN lookup failed: {}, falling back to title search", e.getMessage());
                    }
                }

                List<BookMetadata> previews = fetchMetadataPreviews(book, fetchMetadataRequest).stream()
                        .limit(COUNT_DETAILED_METADATA_TO_GET)
                        .toList();

                for (BookMetadata preview : previews) {
                    if (sink.isCancelled()) return;
                    log.info("GoodReads: Fetching metadata for: {}", preview.getTitle());
                    try {
                        Document document = fetchDoc(BASE_BOOK_URL + preview.getGoodreadsId());
                        BookMetadata detailedMetadata = parseBookDetails(document, preview.getGoodreadsId());
                        if (detailedMetadata != null) {
                            sink.next(detailedMetadata);
                        }
                        Thread.sleep(ThreadLocalRandom.current().nextLong(500, 1501));
                    } catch (Exception e) {
                        log.error("Error fetching metadata for book: {}", preview.getGoodreadsId(), e);
                    }
                }

                if (fetchMetadataRequest.getTitle() != null && !fetchMetadataRequest.getTitle().isBlank()
                        && fetchMetadataRequest.getAuthor() != null && !fetchMetadataRequest.getAuthor().isBlank()
                        && previews.isEmpty()) {

                    log.info("GoodReads: No hits for Title + Author search, retrying with Title only: {}", fetchMetadataRequest.getTitle());
                    FetchMetadataRequest titleOnlyRequest = FetchMetadataRequest.builder()
                            .title(fetchMetadataRequest.getTitle())
                            .build();

                    List<BookMetadata> titleOnlyPreviews = fetchMetadataPreviews(book, titleOnlyRequest).stream()
                            .limit(COUNT_DETAILED_METADATA_TO_GET_RETRY)
                            .toList();

                    for (BookMetadata preview : titleOnlyPreviews) {
                        if (sink.isCancelled()) return;
                        log.info("GoodReads: Fetching metadata (Title only hit) for: {}", preview.getTitle());
                        try {
                            Document document = fetchDoc(BASE_BOOK_URL + preview.getGoodreadsId());
                            BookMetadata detailedMetadata = parseBookDetails(document, preview.getGoodreadsId());
                            if (detailedMetadata != null) {
                                sink.next(detailedMetadata);
                            }
                            Thread.sleep(ThreadLocalRandom.current().nextLong(500, 1501));
                        } catch (Exception e) {
                            log.error("Error fetching metadata for book (Title only retry): {}", preview.getGoodreadsId(), e);
                        }
                    }
                }

                sink.complete();
            } catch (Exception e) {
                sink.error(e);
            }
        });
    }

    private BookMetadata parseBookDetails(Document document, String goodreadsId) {
        BookMetadata.BookMetadataBuilder builder = BookMetadata.builder()
                .goodreadsId(goodreadsId)
                .provider(MetadataProvider.GoodReads);

        try {
            JSONObject apolloStateJson = getJson(document)
                    .getJSONObject("props")
                    .getJSONObject("pageProps")
                    .getJSONObject("apolloState");

            LinkedHashSet<String> keySet = getJsonKeys(apolloStateJson);

            extractContributorDetails(apolloStateJson, keySet, builder);
            extractSeriesDetails(apolloStateJson, keySet, builder);
            extractBookDetails(apolloStateJson, keySet, builder);
            extractWorkDetails(apolloStateJson, keySet, builder);

            appSettingService.getAppSettings()
                    .getMetadataPublicReviewsSettings()
                    .getProviders()
                    .stream()
                    .filter(cfg -> cfg.getProvider() == MetadataProvider.GoodReads && cfg.isEnabled())
                    .findFirst()
                    .ifPresent(cfg -> extractReviews(apolloStateJson, keySet, builder, cfg.getMaxReviews()));

        } catch (Exception e) {
            log.error("Error parsing book details for providerBookId: {}", goodreadsId, e);
            return null;
        }

        return builder.build();
    }

    private void extractContributorDetails(JSONObject apolloStateJson, LinkedHashSet<String> keySet, BookMetadata.BookMetadataBuilder builder) {
        String contributorKey = findKeyByPrefix(keySet, "Contributor:kca");
        String contributorName = getJsonStringField(apolloStateJson, contributorKey, "name");
        if (contributorName != null) {
            builder.authors(List.of(contributorName));
        }
    }

    private void extractReviews(JSONObject apolloStateJson, LinkedHashSet<String> keySet, BookMetadata.BookMetadataBuilder builder, int maxReviews) {
        List<String> allReviewKeys = findKeysByPrefixAll(keySet, "Review:kca");
        List<BookReview> reviews = new ArrayList<>();

        int count = 0;
        int index = 0;

        while (count < maxReviews && index < allReviewKeys.size()) {
            String reviewKey = allReviewKeys.get(index);
            index++;
            try {
                JSONObject reviewJson = apolloStateJson.getJSONObject(reviewKey);
                String creatorRef = reviewJson.getJSONObject("creator").getString("__ref");
                JSONObject userJson = apolloStateJson.optJSONObject(creatorRef);

                String reviewerName = null;
                Integer followersCount = null;
                Integer textReviewsCount = null;
                if (userJson != null) {
                    reviewerName = userJson.optString("name", null);
                    followersCount = userJson.has("followersCount") ? userJson.optInt("followersCount") : null;
                    textReviewsCount = userJson.has("textReviewsCount") ? userJson.optInt("textReviewsCount") : null;
                }

                String rawBody = reviewJson.optString("text", null);
                String plainBody = rawBody != null ? Jsoup.parse(rawBody).text() : null;

                if (plainBody == null || plainBody.trim().isEmpty()) {
                    continue;
                }

                BookReview review = BookReview.builder()
                        .metadataProvider(MetadataProvider.GoodReads)
                        .date(parseEpochMillis(String.valueOf(reviewJson.getLong("updatedAt"))))
                        .body(plainBody.trim())
                        .rating(Float.valueOf(reviewJson.optString("rating", null)))
                        .spoiler(reviewJson.optBoolean("spoilerStatus", false))
                        .reviewerName(reviewerName != null ? reviewerName.trim() : null)
                        .followersCount(followersCount)
                        .textReviewsCount(textReviewsCount)
                        .build();
                reviews.add(review);
                count++;
            } catch (Exception e) {
                log.error("Error fetching review: {}, Error: {}", reviewKey, e.getMessage());
            }
        }

        builder.bookReviews(reviews);
    }

    private Instant parseEpochMillis(String millisString) {
        try {
            long millis = Long.parseLong(millisString);
            return Instant.ofEpochMilli(millis);
        } catch (NumberFormatException e) {
            log.warn("Invalid epoch millis: {}", millisString, e);
            return null;
        }
    }

    private List<String> findKeysByPrefixAll(LinkedHashSet<String> keySet, String prefix) {
        List<String> matchingKeys = new ArrayList<>();
        for (String key : keySet) {
            if (key.startsWith(prefix)) {
                matchingKeys.add(key);
            }
        }
        return matchingKeys;
    }

    private void extractSeriesDetails(JSONObject apolloStateJson, LinkedHashSet<String> keySet, BookMetadata.BookMetadataBuilder builder) {
        String seriesKey = findKeyByPrefix(keySet, "Series:kca");
        String seriesName = getJsonStringField(apolloStateJson, seriesKey, "title");
        if (seriesName != null) {
            builder.seriesName(seriesName);
        }
    }

    private void extractBookDetails(JSONObject apolloStateJson, LinkedHashSet<String> keySet, BookMetadata.BookMetadataBuilder builder) {
        JSONObject bookJson = getValidBookJson(apolloStateJson, keySet);
        if (bookJson == null) {
            return;
        }

        TitleInfo titleInfo = parseTitleInfo(bookJson.optString("title"));
        builder.title(titleInfo.title())
                .subtitle(titleInfo.subtitle())
                .description(normalizeNull(bookJson.optString("description")))
                .thumbnailUrl(normalizeNull(bookJson.optString("imageUrl")))
                .categories(extractGenres(bookJson));

        JSONObject detailsJson = bookJson.optJSONObject("details");
        if (detailsJson != null) {
            builder.pageCount(parseNumber(detailsJson.optString("numPages"), Integer::parseInt))
                    .publishedDate(convertToLocalDate(detailsJson.optString("publicationTime")))
                    .publisher(normalizeNull(detailsJson.optString("publisher")))
                    .isbn10(normalizeNull(detailsJson.optString("isbn")))
                    .isbn13(normalizeNull(detailsJson.optString("isbn13")));

            JSONObject languageJson = detailsJson.optJSONObject("language");
            if (languageJson != null) {
                builder.language(normalizeNull(languageJson.optString("name")));
            }
        }

        JSONArray bookSeriesJson = bookJson.optJSONArray("bookSeries");
        if (bookSeriesJson != null && bookSeriesJson.length() > 0) {
            JSONObject firstElement = bookSeriesJson.optJSONObject(0);
            if (firstElement != null) {
                builder.seriesNumber(parseNumber(firstElement.optString("userPosition"), Float::parseFloat));
            }
        }
    }

    private void extractWorkDetails(JSONObject apolloStateJson, LinkedHashSet<String> keySet, BookMetadata.BookMetadataBuilder builder) {
        String workKey = findKeyByPrefix(keySet, "Work:kca:");
        if (workKey == null) {
            return;
        }
        JSONObject workJson = apolloStateJson.optJSONObject(workKey);
        if (workJson == null) {
            return;
        }
        JSONObject statsJson = workJson.optJSONObject("stats");
        if (statsJson != null) {
            builder.goodreadsRating(parseNumber(statsJson.optString("averageRating"), Double::parseDouble))
                    .goodreadsReviewCount(parseNumber(statsJson.optString("ratingsCount"), Integer::parseInt));
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

    @SuppressWarnings("unchecked")
    private LinkedHashSet<String> getJsonKeys(JSONObject apolloStateJson) {
        LinkedHashSet<String> keySet = new LinkedHashSet<>();
        Iterator<String> keys = apolloStateJson.keys();
        while (keys.hasNext()) {
            keySet.add(keys.next());
        }
        return keySet;
    }

    private JSONObject getValidBookJson(JSONObject apolloStateJson, LinkedHashSet<String> keySet) {
        try {
            for (String key : keySet) {
                if (key.contains("Book:kca:")) {
                    JSONObject bookJson = apolloStateJson.getJSONObject(key);
                    String title = bookJson.optString("title");
                    if (title != null && !title.isEmpty()) {
                        return bookJson;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error finding valid book JSON: {}", e.getMessage());
        }
        return null;
    }

    private String findKeyByPrefix(LinkedHashSet<String> keySet, String prefix) {
        return keySet.stream()
                .filter(key -> key.contains(prefix))
                .findFirst()
                .orElse(null);
    }

    private String getJsonStringField(JSONObject apolloStateJson, String key, String fieldName) {
        if (key == null) {
            return null;
        }
        try {
            return apolloStateJson.getJSONObject(key).getString(fieldName);
        } catch (Exception e) {
            log.warn("Error fetching {} from {}: {}", fieldName, key, e.getMessage());
            return null;
        }
    }

    private Set<String> extractGenres(JSONObject bookJson) {
        try {
            Set<String> genres = new HashSet<>();
            JSONArray bookGenresJsonArray = bookJson.getJSONArray("bookGenres");
            for (int i = 0; i < bookGenresJsonArray.length(); i++) {
                JSONObject genreJson = bookGenresJsonArray.getJSONObject(i).getJSONObject("genre");
                genres.add(genreJson.getString("name"));
            }
            return genres;
        } catch (Exception e) {
            log.error("Error extracting genres from book: {}, Error: {}", bookJson, e.getMessage());
        }
        return null;
    }

    private LocalDate convertToLocalDate(String timestamp) {
        if (timestamp == null || timestamp.isBlank() || "null".equals(timestamp)) {
            return null;
        }
        try {
            long millis = Long.parseLong(timestamp);
            return Instant.ofEpochMilli(millis)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
        } catch (Exception e) {
            log.error("Invalid publication time: {}, Error: {}", timestamp, e.getMessage());
            return null;
        }
    }

    public JSONObject getJson(Element document) {
        try {
            Element scriptElement = document.getElementById("__NEXT_DATA__");

            if (scriptElement != null) {
                String jsonString = scriptElement.html();
                return new JSONObject(jsonString);
            } else {
                log.warn("No JSON script element found!");
            }
        } catch (Exception e) {
            log.error("No JSON script element found!", e);
        }
        return null;
    }

    public String generateSearchUrl(String searchTerm) {
        String encodedSearchTerm = URLEncoder.encode(searchTerm, StandardCharsets.UTF_8);
        String url = BASE_SEARCH_URL + encodedSearchTerm;
        log.info("Goodreads Query URL: {}", url);
        return url;
    }

    public List<BookMetadata> fetchMetadataPreviews(Book book, FetchMetadataRequest request) {
        String searchTerm = getSearchTerm(book, request);

        if (searchTerm == null || searchTerm.isEmpty()) {
            log.info("GoodReads: No metadata previews found (no ISBN, title, or filename).");
            return Collections.emptyList();
        }

        try {
            String searchUrl = generateSearchUrl(searchTerm);
            Document doc = fetchDoc(searchUrl);
            Element tableList = doc.select("table.tableList").first();

            if (tableList == null) {
                log.warn("GoodReads: No results table found for search term: {}", searchTerm);
                return Collections.emptyList();
            }

            Elements previewBooks = tableList.select("tr[itemtype=http://schema.org/Book]");

            List<BookMetadata> metadataPreviews = new ArrayList<>();
            FuzzyScore fuzzyScore = new FuzzyScore(Locale.ENGLISH);
            String queryAuthor = request.getAuthor();

            for (Element previewBook : previewBooks) {
                List<String> authors = extractAuthorsPreview(previewBook);

                if (queryAuthor != null && !queryAuthor.isBlank()) {
                    List<String> queryAuthorTokens = List.of(WHITESPACE_PATTERN.split(queryAuthor.toLowerCase()));
                    boolean matches = authors.stream()
                            .flatMap(a -> Arrays.stream(WHITESPACE_PATTERN.split(a.toLowerCase())))
                            .anyMatch(actual -> {
                                for (String query : queryAuthorTokens) {
                                    int score = fuzzyScore.fuzzyScore(actual, query);
                                    int maxScore = Math.max(fuzzyScore.fuzzyScore(query, query),
                                            fuzzyScore.fuzzyScore(actual, actual));
                                    double similarity = maxScore > 0 ? (double) score / maxScore : 0;
                                    if (similarity >= 0.5) return true;
                                }
                                return false;
                            });

                    if (!matches) {
                        continue;
                    }
                }

                BookMetadata previewMetadata = BookMetadata.builder()
                        .goodreadsId(String.valueOf(extractGoodReadsIdPreview(previewBook)))
                        .title(extractTitlePreview(previewBook))
                        .authors(authors)
                        .provider(MetadataProvider.GoodReads)
                        .thumbnailUrl(extractThumbnailPreview(previewBook))
                        .build();
                metadataPreviews.add(previewMetadata);
            }

            Thread.sleep(Duration.ofSeconds(1));
            return metadataPreviews;

        } catch (Exception e) {
            log.error("Error fetching metadata previews: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private String getSearchTerm(Book book, FetchMetadataRequest request) {
        if (request.getTitle() != null && !request.getTitle().isEmpty()) {
            if (request.getAuthor() != null && !request.getAuthor().isEmpty()) {
                return request.getTitle() + " " + request.getAuthor();
            }
            return request.getTitle();
        }
        return (book.getPrimaryFile() != null && book.getPrimaryFile().getFileName() != null && !book.getPrimaryFile().getFileName().isEmpty()
                ? BookUtils.cleanFileName(book.getPrimaryFile().getFileName())
                : null);
    }

    private Integer extractGoodReadsIdPreview(Element book) {
        try {
            Element bookTitle = book.select("a.bookTitle").first();
            if (bookTitle == null) {
                return null;
            }
            String href = bookTitle.attr("href");
            Matcher matcher = BOOK_SHOW_ID_PATTERN.matcher(href);
            if (matcher.find()) {
                return Integer.valueOf(matcher.group(1));
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private List<String> extractAuthorsPreview(Element book) {
        List<String> authors = new ArrayList<>();
        try {
            Elements authorsElement = book.select("a.authorName");
            for (Element authorElement : authorsElement) {
                authors.add(authorElement.text());
            }
        } catch (Exception e) {
            log.warn("Error extracting author: {}", e.getMessage());
            return authors;
        }
        return authors;
    }

    private String extractTitlePreview(Element book) {
        try {
            Element link = book.select("a[title]").first();
            return link != null ? link.attr("title") : null;
        } catch (Exception e) {
            log.warn("Error extracting title: {}", e.getMessage());
            return null;
        }
    }

    private String extractThumbnailPreview(Element book) {
        try {
            Element img = book.selectFirst("img");
            if (img != null) {
                String src = img.attr("src");
                if (!src.isBlank()) {
                    return src;
                }
            }
        } catch (Exception e) {
            log.warn("Error extracting thumbnail: {}", e.getMessage());
        }
        return null;
    }

    @Override
    public BookMetadata fetchDetailedMetadata(String goodreadsId) {
        log.info("GoodReads: Fetching detailed metadata for ID: {}", goodreadsId);
        try {
            Document document = fetchDoc(BASE_BOOK_URL + goodreadsId);
            return parseBookDetails(document, goodreadsId);
        } catch (Exception e) {
            log.error("Error fetching detailed metadata for GoodReads ID: {}", goodreadsId, e);
            return null;
        }
    }

    private Document fetchDoc(String url) {
        try {
            Connection.Response response = Jsoup.connect(url)
                    .header("accept", "text/html, application/json")
                    .header("accept-language", "en-US,en;q=0.9")
                    .header("content-type", "application/json")
                    .header("device-memory", "8")
                    .header("downlink", "10")
                    .header("dpr", "2")
                    .header("ect", "4g")
                    .header("origin", "https://www.amazon.com")
                    .header("priority", "u=1, i")
                    .header("rtt", "50")
                    .header("sec-ch-device-memory", "8")
                    .header("sec-ch-dpr", "2")
                    .header("sec-ch-ua", "\"Google Chrome\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"")
                    .header("sec-ch-ua-mobile", "?0")
                    .header("sec-ch-ua-platform", "\"macOS\"")
                    .header("sec-ch-viewport-width", "1170")
                    .header("sec-fetch-dest", "empty")
                    .header("sec-fetch-mode", "cors")
                    .header("sec-fetch-site", "same-origin")
                    .header("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                    .header("viewport-width", "1170")
                    .header("x-amz-amabot-click-attributes", "disable")
                    .header("x-requested-with", "XMLHttpRequest")
                    .method(Connection.Method.GET)
                    .execute();
            return response.parse();
        } catch (IOException e) {
            log.error("Error parsing url: {}", url, e);
            throw new RuntimeException(e);
        }
    }
}