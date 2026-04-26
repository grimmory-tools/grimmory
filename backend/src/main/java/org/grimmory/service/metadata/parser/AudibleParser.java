package org.grimmory.service.metadata.parser;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grimmory.model.dto.AudiobookMetadata;
import org.grimmory.model.dto.Book;
import org.grimmory.model.dto.BookMetadata;
import org.grimmory.model.dto.request.FetchMetadataRequest;
import org.grimmory.model.enums.MetadataProvider;
import org.grimmory.service.appsettings.AppSettingService;
import org.grimmory.util.BookUtils;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class AudibleParser implements BookParser, DetailedMetadataProvider {

    private static final int COUNT_DETAILED_METADATA_TO_GET = 3;
    private static final long MIN_REQUEST_INTERVAL_MS = 1500;
    private static final String DEFAULT_DOMAIN = "com";

    private static final Pattern NON_ALPHANUMERIC_PATTERN = Pattern.compile("[^\\p{L}\\p{M}0-9]");
    private static final Pattern ASIN_PATTERN = Pattern.compile("([A-Z0-9]{10})");
    private static final Pattern ASIN_PATH_PATTERN = Pattern.compile("/pd/[^/]+/([A-Z0-9]{10})");
    private static final Pattern SERIES_BOOK_PATTERN = Pattern.compile(",?\\s*Book\\s*(\\d+(?:\\.\\d+)?)$");

    private static final Map<String, LocaleInfo> DOMAIN_LOCALE_MAP = Map.ofEntries(
            Map.entry("com", new LocaleInfo("en-US,en;q=0.9", Locale.US)),
            Map.entry("co.uk", new LocaleInfo("en-GB,en;q=0.9", Locale.UK)),
            Map.entry("de", new LocaleInfo("en-GB,en;q=0.9,de;q=0.8", Locale.GERMANY)),
            Map.entry("fr", new LocaleInfo("en-GB,en;q=0.9,fr;q=0.8", Locale.FRANCE)),
            Map.entry("it", new LocaleInfo("en-GB,en;q=0.9,it;q=0.8", Locale.ITALY)),
            Map.entry("es", new LocaleInfo("en-GB,en;q=0.9,es;q=0.8", new Locale.Builder().setLanguage("es").setRegion("ES").build())),
            Map.entry("ca", new LocaleInfo("en-US,en;q=0.9", Locale.CANADA)),
            Map.entry("com.au", new LocaleInfo("en-GB,en;q=0.9", new Locale.Builder().setLanguage("en").setRegion("AU").build())),
            Map.entry("co.jp", new LocaleInfo("en-GB,en;q=0.9,ja;q=0.8", Locale.JAPAN)),
            Map.entry("in", new LocaleInfo("en-GB,en;q=0.9", new Locale.Builder().setLanguage("en").setRegion("IN").build()))
    );

    private static final LocaleInfo DEFAULT_LOCALE_INFO = new LocaleInfo("en-US,en;q=0.9", Locale.US);

    private final AppSettingService appSettingService;
    private final ObjectMapper objectMapper;
    private final AtomicLong lastRequestTime = new AtomicLong(0);

    private record LocaleInfo(String acceptLanguage, Locale locale) {}

    @Override
    public BookMetadata fetchTopMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        String audibleId = getTopAudibleId(book, fetchMetadataRequest);
        if (audibleId == null || audibleId.isEmpty()) {
            return null;
        }
        return getBookMetadata(audibleId);
    }

    @Override
    public List<BookMetadata> fetchMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        List<String> audibleIds = getAudibleIds(book, fetchMetadataRequest);
        if (audibleIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<BookMetadata> results = new ArrayList<>();
        for (int i = 0; i < audibleIds.size() && results.size() < COUNT_DETAILED_METADATA_TO_GET; i++) {
            try {
                if (i > 0) {
                    Thread.sleep(ThreadLocalRandom.current().nextLong(500, 1501));
                }
                BookMetadata metadata = getBookMetadata(audibleIds.get(i));
                if (metadata != null) {
                    results.add(metadata);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error fetching metadata for Audible ID: {}", audibleIds.get(i), e);
            }
        }
        return results;
    }

    @Override
    public BookMetadata fetchDetailedMetadata(String audibleId) {
        return getBookMetadata(audibleId);
    }

    private String getExistingAsin(Book book) {
        if (book == null) {
            return null;
        }

        BookMetadata metadata = book.getMetadata();
        if (metadata == null) {
            return null;
        }
        String asin = metadata.getAsin();
        if (asin == null) {
            return null;
        }

        Matcher matcher = AudibleParser.ASIN_PATTERN.matcher(asin);
        if (!matcher.find()) {
            return null;
        }

        return matcher.group(1);
    }

    private String getTopAudibleId(Book book, FetchMetadataRequest request) {
        String storedBookId = getExistingAsin(book);
        if (storedBookId != null) {
            return storedBookId;
        }

        List<String> bookIds = getAudibleIds(book, request);
        if (!bookIds.isEmpty() && !bookIds.getFirst().isEmpty()) {
            return bookIds.getFirst();
        }

        return null;
    }

    private List<String> getAudibleIds(Book book, FetchMetadataRequest request) {
        String queryUrl = buildQueryUrl(request, book);
        if (queryUrl == null) {
            log.error("Query URL is null, cannot proceed with Audible search.");
            return Collections.emptyList();
        }

        List<String> bookIds = new ArrayList<>();
        try {
            enforceRateLimit();
            Document doc = fetchDocument(queryUrl);

            Elements allLinks = doc.select("a[href*='/pd/']");
            for (Element link : allLinks) {
                String href = link.attr("href");
                Matcher matcher = ASIN_PATH_PATTERN.matcher(href);
                if (matcher.find()) {
                    String asin = matcher.group(1);
                    if (asin != null && !asin.isEmpty() && !bookIds.contains(asin)) {
                        bookIds.add(asin);
                    }
                }
            }

        } catch (Exception e) {
            log.error("Failed to get Audible IDs: {}", e.getMessage(), e);
        }

        log.info("Audible: Found {} book ids", bookIds.size());
        return bookIds;
    }

    private BookMetadata getBookMetadata(String audibleId) {
        log.info("Audible: Fetching metadata for: {}", audibleId);

        String domain = getDomain();
        String url = "https://www.audible." + domain + "/pd/" + audibleId;

        try {
            enforceRateLimit();
            Document doc = fetchDocument(url);
            return buildBookMetadataFromJsonLd(doc, audibleId);
        } catch (Exception e) {
            log.error("Failed to fetch Audible metadata for ID {}: {}", audibleId, e.getMessage());
            return null;
        }
    }

    private BookMetadata buildBookMetadataFromJsonLd(Document doc, String audibleId) {
        JsonNode audiobookNode = null;
        JsonNode productNode = null;
        JsonNode breadcrumbNode = null;

        Elements jsonLdScripts = doc.select("script[type='application/ld+json']");
        for (Element script : jsonLdScripts) {
            try {
                String jsonContent = script.html();
                JsonNode rootNode = objectMapper.readTree(jsonContent);

                if (rootNode.isArray()) {
                    for (JsonNode node : rootNode) {
                        String type = node.path("@type").asText("");
                        if ("Audiobook".equals(type)) {
                            audiobookNode = node;
                        } else if ("Product".equals(type)) {
                            productNode = node;
                        } else if ("BreadcrumbList".equals(type)) {
                            breadcrumbNode = node;
                        }
                    }
                } else {
                    String type = rootNode.path("@type").asText("");
                    if ("Audiobook".equals(type)) {
                        audiobookNode = rootNode;
                    } else if ("Product".equals(type)) {
                        productNode = rootNode;
                    } else if ("BreadcrumbList".equals(type)) {
                        breadcrumbNode = rootNode;
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to parse JSON-LD block: {}", e.getMessage());
            }
        }

        if (audiobookNode == null && productNode == null) {
            log.warn("No JSON-LD structured data found for Audible ID: {}", audibleId);
            return null;
        }

        String title = null;
        String subtitle = null;
        String fullName = getJsonString(audiobookNode, "name");
        if (fullName == null) {
            fullName = getJsonString(productNode, "name");
        }

        if (fullName != null) {
            Matcher seriesMatcher = SERIES_BOOK_PATTERN.matcher(fullName);
            if (seriesMatcher.find()) {
                fullName = seriesMatcher.replaceFirst("").trim();
            }

            int colonIdx = fullName.indexOf(':');
            if (colonIdx > 0 && colonIdx < fullName.length() - 1) {
                title = fullName.substring(0, colonIdx).trim();
                subtitle = fullName.substring(colonIdx + 1).trim();
            } else {
                title = fullName;
            }
        }

        List<String> authors = extractPersonNames(audiobookNode, "author");
        String narrator = extractFirstPersonName(audiobookNode, "readBy");
        String publisher = getJsonString(audiobookNode, "publisher");
        String description = getJsonString(audiobookNode, "description");
        String language = getJsonString(audiobookNode, "inLanguage");
        String imageUrl = getJsonString(audiobookNode, "image");
        if (imageUrl == null) {
            imageUrl = getJsonString(productNode, "image");
        }

        LocalDate publishedDate = null;
        String dateStr = getJsonString(audiobookNode, "datePublished");
        if (dateStr != null) {
            try {
                publishedDate = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException e) {
                log.debug("Failed to parse date: {}", dateStr);
            }
        }

        Long durationSeconds = null;
        String durationStr = getJsonString(audiobookNode, "duration");
        if (durationStr != null) {
            durationSeconds = parseIsoDuration(durationStr);
        }

        Boolean abridged = null;
        String abridgedStr = getJsonString(audiobookNode, "abridged");
        if (abridgedStr != null) {
            abridged = "true".equalsIgnoreCase(abridgedStr);
        }

        Double rating = null;
        Integer reviewCount = null;
        JsonNode ratingNode = audiobookNode != null ? audiobookNode.path("aggregateRating") : null;
        if (ratingNode == null || ratingNode.isMissingNode()) {
            ratingNode = productNode != null ? productNode.path("aggregateRating") : null;
        }
        if (ratingNode != null && !ratingNode.isMissingNode()) {
            String ratingValue = ratingNode.path("ratingValue").asText(null);
            String ratingCountStr = ratingNode.path("ratingCount").asText(null);
            if (ratingValue != null) {
                try {
                    rating = Double.parseDouble(ratingValue);
                } catch (NumberFormatException e) {
                    log.debug("Failed to parse rating: {}", ratingValue);
                }
            }
            if (ratingCountStr != null) {
                try {
                    reviewCount = Integer.parseInt(ratingCountStr);
                } catch (NumberFormatException e) {
                    log.debug("Failed to parse review count: {}", ratingCountStr);
                }
            }
        }

        Set<String> categories = new HashSet<>();
        if (breadcrumbNode != null) {
            JsonNode itemListElement = breadcrumbNode.path("itemListElement");
            if (itemListElement.isArray()) {
                for (JsonNode item : itemListElement) {
                    String categoryName = item.path("item").path("name").asText(null);
                    if (categoryName != null && !"Home".equalsIgnoreCase(categoryName)) {
                        categories.add(categoryName);
                    }
                }
            }
        }

        String seriesName = null;
        Float seriesNumber = null;
        Elements seriesLinks = doc.select("a[href*='/series/']");
        for (Element link : seriesLinks) {
            String seriesText = link.text().trim();
            if (!seriesText.isEmpty()) {
                Matcher bookMatcher = SERIES_BOOK_PATTERN.matcher(seriesText);
                if (bookMatcher.find()) {
                    seriesName = bookMatcher.replaceFirst("").trim();
                    try {
                        seriesNumber = Float.parseFloat(bookMatcher.group(1));
                    } catch (NumberFormatException e) {
                        log.debug("Failed to parse series number from: {}", seriesText);
                    }
                } else {
                    if (seriesName == null) {
                        seriesName = seriesText;
                    }
                }
            }
        }

        AudiobookMetadata audiobookMetadata = AudiobookMetadata.builder()
                .durationSeconds(durationSeconds)
                .build();

        return BookMetadata.builder()
                .provider(MetadataProvider.Audible)
                .asin(audibleId)
                .audibleId(audibleId)
                .title(title)
                .subtitle(subtitle)
                .authors(authors)
                .categories(categories)
                .description(description)
                .seriesName(seriesName)
                .seriesNumber(seriesNumber)
                .publisher(publisher)
                .publishedDate(publishedDate)
                .language(language)
                .thumbnailUrl(imageUrl)
                .audibleRating(rating)
                .audibleReviewCount(reviewCount)
                .narrator(narrator)
                .abridged(abridged)
                .audiobookMetadata(audiobookMetadata)
                .build();
    }

    private String getJsonString(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode fieldNode = node.path(field);
        if (fieldNode.isMissingNode() || fieldNode.isNull()) return null;
        return fieldNode.asText(null);
    }

    private List<String> extractPersonNames(JsonNode node, String field) {
        List<String> names = new ArrayList<>();
        if (node == null) return names;

        JsonNode fieldNode = node.path(field);
        if (fieldNode.isMissingNode()) return names;

        if (fieldNode.isArray()) {
            for (JsonNode person : fieldNode) {
                String name = person.path("name").asText(null);
                if (name != null && !name.isEmpty()) {
                    names.add(name);
                }
            }
        } else if (fieldNode.isObject()) {
            String name = fieldNode.path("name").asText(null);
            if (name != null && !name.isEmpty()) {
                names.add(name);
            }
        }

        return names;
    }

    private String extractFirstPersonName(JsonNode node, String field) {
        if (node == null) return null;

        JsonNode fieldNode = node.path(field);
        if (fieldNode.isMissingNode()) return null;

        if (fieldNode.isArray() && fieldNode.size() > 0) {
            return fieldNode.get(0).path("name").asText(null);
        } else if (fieldNode.isObject()) {
            return fieldNode.path("name").asText(null);
        }

        return null;
    }

    private Long parseIsoDuration(String duration) {
        if (duration == null || duration.isEmpty()) return null;

        try {
            Duration d = Duration.parse(duration);
            return d.getSeconds();
        } catch (DateTimeParseException e) {
            log.debug("Failed to parse ISO duration: {}", duration);
        }

        return null;
    }

    private String buildQueryUrl(FetchMetadataRequest request, Book book) {
        String domain = getDomain();
        StringBuilder searchTerm = new StringBuilder(256);

        String title = request.getTitle();
        if (title != null && !title.isEmpty()) {
            searchTerm.append(cleanSearchTerm(title));
        } else if (book.getPrimaryFile() != null && book.getPrimaryFile().getFileName() != null) {
            String filename = BookUtils.cleanAndTruncateSearchTerm(BookUtils.cleanFileName(book.getPrimaryFile().getFileName()));
            if (!filename.isEmpty()) {
                searchTerm.append(cleanSearchTerm(filename));
            }
        }

        String author = request.getAuthor();
        if (author != null && !author.isEmpty()) {
            if (!searchTerm.isEmpty()) {
                searchTerm.append(" ");
            }
            searchTerm.append(cleanSearchTerm(author));
        }

        if (searchTerm.isEmpty()) {
            return null;
        }

        String encodedSearchTerm = searchTerm.toString().replace(" ", "+");
        String url = "https://www.audible." + domain + "/search?keywords=" + encodedSearchTerm;
        log.info("Audible Query URL: {}", url);
        return url;
    }

    private String cleanSearchTerm(String text) {
        return Arrays.stream(text.split(" "))
                .map(word -> NON_ALPHANUMERIC_PATTERN.matcher(word).replaceAll("").trim())
                .filter(word -> !word.isEmpty())
                .collect(Collectors.joining(" "));
    }

    private Document fetchDocument(String url) {
        try {
            String domain = getDomain();
            LocaleInfo localeInfo = DOMAIN_LOCALE_MAP.getOrDefault(domain, DEFAULT_LOCALE_INFO);

            Connection connection = Jsoup.connect(url)
                    .header("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .header("accept-language", localeInfo.acceptLanguage)
                    .header("cache-control", "no-cache")
                    .header("pragma", "no-cache")
                    .header("sec-ch-ua", "\"Google Chrome\";v=\"137\", \"Chromium\";v=\"137\", \"Not_A Brand\";v=\"24\"")
                    .header("sec-ch-ua-mobile", "?0")
                    .header("sec-ch-ua-platform", "\"macOS\"")
                    .header("sec-fetch-dest", "document")
                    .header("sec-fetch-mode", "navigate")
                    .header("sec-fetch-site", "none")
                    .header("sec-fetch-user", "?1")
                    .header("upgrade-insecure-requests", "1")
                    .header("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36")
                    .timeout(30000)
                    .method(Connection.Method.GET);

            Connection.Response response = connection.execute();
            return response.parse();
        } catch (HttpStatusException e) {
            log.error("HTTP error fetching Audible URL. Status={}, URL=[{}]", e.getStatusCode(), url);
            throw new RuntimeException(e);
        } catch (IOException e) {
            log.error("Error fetching Audible URL: {}", url, e);
            throw new RuntimeException(e);
        }
    }

    private void enforceRateLimit() {
        long now = System.currentTimeMillis();
        long lastRequest = lastRequestTime.get();
        long elapsed = now - lastRequest;

        if (elapsed < MIN_REQUEST_INTERVAL_MS) {
            try {
                Thread.sleep(MIN_REQUEST_INTERVAL_MS - elapsed);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        lastRequestTime.set(System.currentTimeMillis());
    }

    private String getDomain() {
        var settings = appSettingService.getAppSettings().getMetadataProviderSettings();
        if (settings != null && settings.getAudible() != null && settings.getAudible().getDomain() != null) {
            return settings.getAudible().getDomain();
        }
        return DEFAULT_DOMAIN;
    }
}
