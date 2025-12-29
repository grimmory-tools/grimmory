package com.adityachandel.booklore.service.metadata.parser;

import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.request.FetchMetadataRequest;
import com.adityachandel.booklore.model.enums.MetadataProvider;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class LubimyCzytacParser implements BookParser {

    private static final String BASE_URL = "https://lubimyczytac.pl";
    private static final String SEARCH_URL = BASE_URL + "/szukaj/ksiazki";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    private static final int CONNECTION_TIMEOUT_MS = 10000;
    private static final int MAX_RESULTS = 10;
    private static final double RATING_SCALE_DIVISOR = 2.0; // Convert 10-point scale to 5-point scale
    private static final Pattern SERIES_NUMBER_PATTERN = Pattern.compile("\\(tom\\s+(\\d+)\\)");
    private static final Pattern BOOK_ID_PATTERN = Pattern.compile("/ksiazka/(\\d+)");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern WHITESPACE_HYPHEN_PATTERN = Pattern.compile("[\\s-]");

    private final AppSettingService appSettingService;

    @Override
    public List<BookMetadata> fetchMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        log.info("Fetching LubimyCzytac metadata for book: {}", book.getTitle());

        // Check if provider is enabled
        var settings = appSettingService.getAppSettings().getMetadataProviderSettings();
        if (settings == null || settings.getLubimyczytac() == null || !settings.getLubimyczytac().isEnabled()) {
            log.info("LubimyCzytac provider is disabled");
            return new ArrayList<>();
        }

        try {
            // Build search query
            String query = buildSearchQuery(fetchMetadataRequest);
            if (query.isEmpty()) {
                log.warn("Empty search query for book: {}", book.getTitle());
                return new ArrayList<>();
            }

            String author = fetchMetadataRequest.getAuthor();
            String searchIsbn = fetchMetadataRequest.getIsbn();
            boolean searchingByIsbn = searchIsbn != null && !searchIsbn.isEmpty();

            // Search for books
            List<String> bookUrls = searchBooks(query, author);
            if (bookUrls.isEmpty()) {
                log.info("No results found for query: {}", query);
                return new ArrayList<>();
            }

            // Parse details for each book
            List<BookMetadata> results = new ArrayList<>();
            for (String url : bookUrls) {
                BookMetadata metadata = parseBookDetails(url);
                if (metadata != null) {
                    // If searching by ISBN, validate that the book's ISBN matches
                    if (searchingByIsbn) {
                        if (isbnMatches(metadata, searchIsbn)) {
                            results.add(metadata);
                        } else {
                            log.debug("Skipping book {} - ISBN doesn't match search ISBN {}",
                                metadata.getTitle(), searchIsbn);
                        }
                    } else {
                        results.add(metadata);
                    }
                }

                // Limit results to avoid excessive scraping
                if (results.size() >= MAX_RESULTS) {
                    break;
                }
            }

            log.info("Found {} LubimyCzytac results for query: {}", results.size(), query);
            return results;

        } catch (Exception e) {
            log.error("Error fetching LubimyCzytac metadata", e);
            return new ArrayList<>();
        }
    }

    @Override
    public BookMetadata fetchTopMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        List<BookMetadata> results = fetchMetadata(book, fetchMetadataRequest);
        return results.isEmpty() ? null : results.get(0);
    }

    private String buildSearchQuery(FetchMetadataRequest request) {
        String isbn = request.getIsbn();
        if (isbn != null && !isbn.isEmpty()) {
            return isbn;
        }

        String title = request.getTitle();
        if (title != null && !title.isEmpty()) {
            return title.trim();
        }

        return "";
    }

    private String buildSearchUrl(String query, String author) {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = SEARCH_URL + "?phrase=" + encodedQuery;

        if (author != null && !author.isEmpty()) {
            String encodedAuthor = URLEncoder.encode(author, StandardCharsets.UTF_8);
            url += "&author=" + encodedAuthor;
        }

        return url;
    }

    private List<String> searchBooks(String query, String author) {
        List<String> bookUrls = new ArrayList<>();

        try {
            String searchUrl = buildSearchUrl(query, author);
            log.info("Searching LubimyCzytac: {}", searchUrl);

            Document doc = Jsoup.connect(searchUrl)
                    .userAgent(USER_AGENT)
                    .timeout(CONNECTION_TIMEOUT_MS)
                    .get();

            Elements results = doc.select(".authorAllBooks__single");
            log.info("Found {} search results", results.size());

            for (Element result : results) {
                Element titleLink = result.selectFirst(".authorAllBooks__singleTextTitle");
                if (titleLink != null) {
                    String href = titleLink.attr("href");
                    if (href != null && !href.isEmpty()) {
                        String fullUrl = href.startsWith("http") ? href : BASE_URL + href;
                        bookUrls.add(fullUrl);
                    }
                }
            }

        } catch (IOException e) {
            log.error("Error searching LubimyCzytac", e);
        }

        return bookUrls;
    }

    private BookMetadata parseBookDetails(String url) {
        try {
            log.info("Parsing book details from: {}", url);

            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(CONNECTION_TIMEOUT_MS)
                    .get();

            BookMetadata metadata = new BookMetadata();
            metadata.setProvider(MetadataProvider.Lubimyczytac);

            // Extract LubimyCzytac ID from URL (e.g., /ksiazka/123456/title -> 123456)
            String id = extractIdFromUrl(url);
            metadata.setLubimyczytacId(id);

            Element titleElement = doc.selectFirst("h1.book__title");
            if (titleElement != null) {
                metadata.setTitle(titleElement.text().trim());
            }

            Element coverElement = doc.selectFirst(".book-cover img");
            if (coverElement != null) {
                String coverUrl = coverElement.attr("src");
                if (coverUrl != null && !coverUrl.isEmpty()) {
                    metadata.setThumbnailUrl(coverUrl.startsWith("http") ? coverUrl : BASE_URL + coverUrl);
                }
            }

            Element publisherElement = doc.selectFirst("a[href*=/wydawnictwo/]");
            if (publisherElement != null) {
                metadata.setPublisher(publisherElement.text().trim());
            }

            Elements languageElements = doc.select("dt:contains(Język:) + dd");
            if (!languageElements.isEmpty()) {
                String language = languageElements.first().text().trim().toLowerCase();
                metadata.setLanguage(mapLanguage(language));
            }

            Element descElement = doc.selectFirst(".collapse-content");
            if (descElement != null) {
                String description = descElement.text();
                if (description != null && !description.isBlank()) {
                    metadata.setDescription(description);
                }
            }

            Element isbnMeta = doc.selectFirst("meta[property=books:isbn]");
            if (isbnMeta != null) {
                String isbn = isbnMeta.attr("content").trim();
                if (isbn.length() == 13) {
                    metadata.setIsbn13(isbn);
                } else if (isbn.length() == 10) {
                    metadata.setIsbn10(isbn);
                }
            }

            // Convert from 10-point to 5-point scale
            Element ratingMeta = doc.selectFirst("meta[property=books:rating:value]");
            if (ratingMeta != null) {
                try {
                    String ratingStr = ratingMeta.attr("content").trim();
                    if (!ratingStr.isEmpty()) {
                        double rating = Double.parseDouble(ratingStr);
                        metadata.setLubimyczytacRating(rating / RATING_SCALE_DIVISOR);
                    }
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse rating for book: {}", url, e);
                }
            }

            Set<String> tags = new HashSet<>();
            Elements tagElements = doc.select("a[href*=/ksiazki/t/]");
            for (Element tagElement : tagElements) {
                String tag = tagElement.text().trim();
                if (!tag.isEmpty()) {
                    tags.add(tag);
                }
            }
            if (!tags.isEmpty()) {
                metadata.setTags(tags);
            }

            // Series format: "Cykl: Series Name (tom 3)" or "Cykl: Series Name"
            Elements seriesElements = doc.select("span.d-none.d-sm-block.mt-1:contains(Cykl:)");
            if (!seriesElements.isEmpty()) {
                String seriesText = seriesElements.first().text().trim();
                parseSeriesInfo(seriesText, metadata);
            }

            // Extract authors, categories, pages, and publish date from JSON-LD structured data
            Elements jsonLdElements = doc.select("script[type=application/ld+json]");
            for (Element jsonLdElement : jsonLdElements) {
                try {
                    String jsonLd = jsonLdElement.html();
                    parseJsonLd(jsonLd, metadata);
                } catch (Exception e) {
                    log.warn("Failed to parse JSON-LD", e);
                }
            }

            return metadata;

        } catch (IOException e) {
            log.error("Error parsing book details from: {}", url, e);
            return null;
        }
    }

    private String extractIdFromUrl(String url) {
        // Extract ID from URL like https://lubimyczytac.pl/ksiazka/123456/title
        try {
            Matcher matcher = BOOK_ID_PATTERN.matcher(url);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            log.warn("Could not extract ID from URL: {}", url);
        }
        return null;
    }

    private boolean isbnMatches(BookMetadata metadata, String searchIsbn) {
        // Normalize ISBN by removing hyphens and spaces for comparison
        String normalizedSearch = WHITESPACE_HYPHEN_PATTERN.matcher(searchIsbn).replaceAll("");

        // Check ISBN-13
        if (metadata.getIsbn13() != null) {
            String normalized13 = WHITESPACE_HYPHEN_PATTERN.matcher(metadata.getIsbn13()).replaceAll("");
            if (normalized13.equals(normalizedSearch)) {
                return true;
            }
        }

        // Check ISBN-10
        if (metadata.getIsbn10() != null) {
            String normalized10 = WHITESPACE_HYPHEN_PATTERN.matcher(metadata.getIsbn10()).replaceAll("");
            if (normalized10.equals(normalizedSearch)) {
                return true;
            }
        }

        return false;
    }

    private String mapLanguage(String polishLanguage) {
        switch (polishLanguage) {
            case "polski":
                return "pl";
            case "angielski":
                return "en";
            case "niemiecki":
                return "de";
            case "francuski":
                return "fr";
            case "hiszpański":
                return "es";
            case "włoski":
                return "it";
            default:
                return polishLanguage;
        }
    }

    private void parseSeriesInfo(String seriesText, BookMetadata metadata) {
        // Format: "Cykl: Series Name (tom 3)" or "Cykl: Series Name"
        if (seriesText.startsWith("Cykl:")) {
            seriesText = seriesText.substring(5).trim();
        }

        // Extract series number if present
        var matcher = SERIES_NUMBER_PATTERN.matcher(seriesText);

        if (matcher.find()) {
            try {
                float seriesNumber = Float.parseFloat(matcher.group(1));
                metadata.setSeriesNumber(seriesNumber);

                // Remove the "(tom X)" part to get series name
                String seriesName = SERIES_NUMBER_PATTERN.matcher(seriesText).replaceAll("").trim();
                metadata.setSeriesName(seriesName);
            } catch (NumberFormatException e) {
                log.warn("Failed to parse series number from: {}", seriesText);
                metadata.setSeriesName(seriesText);
            }
        } else {
            metadata.setSeriesName(seriesText);
        }
    }

    private void parseJsonLd(String jsonLd, BookMetadata metadata) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(jsonLd);

            // Pages
            if (root.has("numberOfPages")) {
                try {
                    int pages = root.get("numberOfPages").asInt();
                    metadata.setPageCount(pages);
                } catch (Exception e) {
                    log.warn("Failed to parse numberOfPages from JSON-LD");
                }
            }

            // Publish date
            if (root.has("datePublished")) {
                try {
                    String dateStr = root.get("datePublished").asText();
                    LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_DATE);
                    metadata.setPublishedDate(date);
                } catch (Exception e) {
                    log.warn("Failed to parse datePublished from JSON-LD: {}", e.getMessage());
                }
            }

            // Author(s) - can be single Person or array of Person objects
            if (root.has("author")) {
                try {
                    Set<String> authors = new HashSet<>();
                    JsonNode authorNode = root.get("author");

                    if (authorNode.isArray()) {
                        // Multiple authors
                        for (JsonNode author : authorNode) {
                            if (author.has("name")) {
                                authors.add(author.get("name").asText());
                            }
                        }
                    } else if (authorNode.isObject() && authorNode.has("name")) {
                        // Single author
                        authors.add(authorNode.get("name").asText());
                    }

                    if (!authors.isEmpty()) {
                        metadata.setAuthors(authors);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse author from JSON-LD: {}", e.getMessage());
                }
            }

            // Genre/Category - extracted from genre URL
            if (root.has("genre")) {
                try {
                    String genreUrl = root.get("genre").asText();
                    // Extract category name from URL like "https://lubimyczytac.pl/ksiazki/k/69/poradniki"
                    // Get the last segment after the final slash
                    String[] urlParts = genreUrl.split("/");
                    if (urlParts.length > 0) {
                        String categoryName = urlParts[urlParts.length - 1];
                        if (!categoryName.isEmpty()) {
                            Set<String> categories = new HashSet<>();
                            categories.add(categoryName);
                            metadata.setCategories(categories);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse genre from JSON-LD: {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            log.warn("Failed to parse JSON-LD structure", e);
        }
    }
}
