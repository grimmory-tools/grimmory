package org.booklore.service.metadata.parser;

import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.request.FetchMetadataRequest;
import org.booklore.model.enums.MetadataProvider;
import org.booklore.util.BookUtils;
import org.booklore.util.LanguageNormalizer;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Metadata provider for bol.com (Dutch book retailer).
 *
 * Scraping mirrors the calibre BOL_NL plugin (Pr. BarnArt): bol.com product
 * pages embed a schema.org Book JSON-LD block that carries title, author,
 * publisher, ISBN/gtin, rating, description, cover, language and genre.
 * Search uses bol.com's public search endpoint and grabs product links.
 */
@Slf4j
@Service
public class BolBookParser implements BookParser {

    private static final String SEARCH_URL = "https://www.bol.com/nl/nl/s/";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";
    private static final int DETAIL_FETCH_COUNT = 3;
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern NON_ISBN_PATTERN = Pattern.compile("[^0-9Xx]");

    private final ObjectMapper objectMapper;

    public BolBookParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public BookMetadata fetchTopMetadata(Book book, FetchMetadataRequest request) {
        List<BookMetadata> results = fetchMetadata(book, request);
        return results == null || results.isEmpty() ? null : results.getFirst();
    }

    @Override
    public List<BookMetadata> fetchMetadata(Book book, FetchMetadataRequest request) {
        String query = buildQuery(request, book);
        if (query == null) {
            return List.of();
        }

        List<SearchResult> results = search(query);
        if (results.isEmpty()) {
            log.info("Bol.com: no search results for query '{}'", query);
            return List.of();
        }

        List<BookMetadata> metadataList = new ArrayList<>();
        for (SearchResult result : results) {
            if (metadataList.size() >= DETAIL_FETCH_COUNT) {
                break;
            }
            try {
                BookMetadata metadata = fetchProductMetadata(result.url());
                if (metadata != null) {
                    if (metadata.getThumbnailUrl() == null && result.coverUrl() != null) {
                        metadata.setThumbnailUrl(result.coverUrl());
                    }
                    metadataList.add(metadata);
                }
            } catch (Exception e) {
                log.warn("Bol.com: failed to fetch product metadata for {}", result.url(), e);
            }
        }
        return metadataList;
    }

    private String buildQuery(FetchMetadataRequest request, Book book) {
        String isbn = request.getIsbn();
        if (isbn != null && !isbn.isBlank()) {
            return ParserUtils.cleanIsbn(isbn);
        }

        String title = request.getTitle();
        String author = request.getAuthor();
        StringBuilder query = new StringBuilder(256);
        if (title != null && !title.isBlank()) {
            query.append(title.trim());
        } else if (book.getPrimaryFile() != null && book.getPrimaryFile().getFileName() != null) {
            query.append(BookUtils.cleanFileName(book.getPrimaryFile().getFileName()));
        }
        if (author != null && !author.isBlank()) {
            if (!query.isEmpty()) {
                query.append(' ');
            }
            // bol.com ranks better with just the first author
            query.append(author.trim().split("\\s+\\(", 2)[0].split(",")[0].trim());
        }
        return query.isEmpty() ? null : query.toString();
    }

    private List<SearchResult> search(String query) {
        try {
            Document doc = fetchDocument(SEARCH_URL + "?searchtext=" + encode(query) + "&searchContext=media_all");

            // Product links look like /nl/nl/p/<slug>/<numeric-id>/
            Elements links = doc.select("a[href^=/nl/nl/p/]");
            Map<String, SearchResult> seen = new LinkedHashMap<>();
            for (Element link : links) {
                String href = link.attr("href");
                if (!href.matches("/nl/nl/p/.+?/\\d+/")) {
                    continue;
                }
                String title = link.text().trim();
                if (title.isEmpty()) {
                    Element img = link.selectFirst("img");
                    if (img != null) {
                        title = img.attr("alt").trim();
                    }
                }
                if (title.isEmpty() || title.length() < 2) {
                    continue;
                }
                String url = "https://www.bol.com" + href;
                String cover = null;
                Element img = link.selectFirst("img[src]");
                if (img != null) {
                    cover = img.attr("src");
                }
                seen.putIfAbsent(href, new SearchResult(url, title, cover));
            }
            log.info("Bol.com: found {} search results for '{}'", seen.size(), query);
            return new ArrayList<>(seen.values());
        } catch (IOException e) {
            log.error("Bol.com: search request failed for '{}'", query, e);
            return List.of();
        }
    }

    private BookMetadata fetchProductMetadata(String url) throws IOException {
        Document doc = fetchDocument(url);
        BookMetadata metadata = parseProductPage(doc.html());
        if (metadata != null && metadata.getExternalUrl() == null) {
            metadata.setExternalUrl(url);
        }
        return metadata;
    }

    /** Parses the schema.org Book JSON-LD block out of a bol.com product page. */
    public BookMetadata parseProductPage(String html) {
        Document doc = Jsoup.parse(html);

        // The schema.org Book JSON-LD block carries all the metadata we need.
        for (Element script : doc.select("script[type=application/ld+json]")) {
            JsonNode node;
            try {
                node = objectMapper.readTree(script.data());
            } catch (Exception e) {
                continue; // not JSON or not ours
            }
            if (node == null || !containsType(node, "Book")) {
                continue;
            }
            BookMetadata metadata = buildMetadata(node);
            if (metadata != null && metadata.getTitle() != null && !metadata.getTitle().isBlank()) {
                return metadata;
            }
        }
        log.warn("Bol.com: no Book JSON-LD found on page");
        return null;
    }

    private BookMetadata buildMetadata(JsonNode node) {
        BookMetadata.BookMetadataBuilder builder = BookMetadata.builder()
                .provider(MetadataProvider.Bol)
                .title(cleanText(node.path("name").asText(null)))
                .description(cleanDescription(node.path("description").asText(null)))
                .language(LanguageNormalizer.normalize(node.path("inLanguage").asText(null)))
                .externalUrl(node.path("url").asText(null))
                .authors(extractNames(node.get("author")))
                .publisher(extractName(node.get("publisher")))
                .categories(extractGenre(node.get("genre")))
                .thumbnailUrl(extractImageUrl(node.get("image")));

        JsonNode rating = node.get("aggregateRating");
        if (rating != null && rating.isObject()) {
            JsonNode ratingValue = rating.get("ratingValue");
            if (ratingValue != null && ratingValue.isNumber()) {
                builder.rating(ratingValue.asDouble());
            }
        }

        // ISBN: prefer the top-level gtin13, fall back to the first work example.
        String gtin = node.path("gtin13").asText(null);
        String isbn13 = null, isbn10 = null;
        LocalDate publishedDate = parseDate(node.path("datePublished").asText(null));
        if (isNonBlank(gtin)) {
            isbn13 = gtin;
        }

        JsonNode workExample = findFirstBook(node.get("workExample"));
        if (workExample != null) {
            if (isbn13 == null) {
                String isbn = workExample.path("isbn13").asText(null);
                if (isNonBlank(isbn)) {
                    isbn13 = isbn;
                } else {
                    isbn = workExample.path("isbn").asText(null);
                    if (isNonBlank(isbn)) {
                        String clean = ParserUtils.cleanIsbn(isbn);
                        if (clean.length() == 10) {
                            isbn10 = clean;
                        } else {
                            isbn13 = clean;
                        }
                    }
                }
            }
            if (publishedDate == null) {
                publishedDate = parseDate(workExample.path("datePublished").asText(null));
            }
            Integer pages = parseInt(workExample.path("numberOfPages").asText(null));
            if (pages != null) {
                builder.pageCount(pages);
            }
        }
        return builder
                .publishedDate(publishedDate)
                .isbn13(isbn13)
                .isbn10(isbn10)
                .build();
    }

    private static JsonNode findFirstBook(JsonNode workExample) {
        if (workExample == null || !workExample.isArray()) {
            return null;
        }
        for (JsonNode item : workExample) {
            if (containsType(item, "Book")) {
                return item;
            }
        }
        return workExample.get(0);
    }

    private static boolean containsType(JsonNode node, String type) {
        JsonNode typeNode = node != null ? node.get("@type") : null;
        if (typeNode == null) {
            return false;
        }
        if (typeNode.isArray()) {
            for (JsonNode t : typeNode) {
                if (type.equalsIgnoreCase(t.asText())) {
                    return true;
                }
            }
            return false;
        }
        return type.equalsIgnoreCase(typeNode.asText());
    }

    private List<String> extractNames(JsonNode node) {
        if (node == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                addName(names, item);
            }
        } else {
            addName(names, node);
        }
        return names;
    }

    private void addName(List<String> names, JsonNode item) {
        String name = item.path("name").asText(null);
        if (isNonBlank(name)) {
            names.add(name.trim());
        }
    }

    private String extractName(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isArray() && !node.isEmpty()) {
            node = node.get(0);
        }
        String name = node.path("name").asText(null);
        return isNonBlank(name) ? name.trim() : null;
    }

    private Set<String> extractGenre(JsonNode node) {
        if (node == null) {
            return Set.of();
        }
        Set<String> genres = new LinkedHashSet<>();
        if (node.isArray()) {
            for (JsonNode g : node) {
                addGenre(genres, g);
            }
        } else {
            addGenre(genres, node);
        }
        return genres;
    }

    private void addGenre(Set<String> genres, JsonNode g) {
        String genre = g.asText(null);
        if (isNonBlank(genre)) {
            genres.add(genre.trim());
        }
    }

    private String extractImageUrl(JsonNode image) {
        if (image == null) {
            return null;
        }
        if (image.isTextual()) {
            return image.asText();
        }
        String url = image.path("url").asText(null);
        return isNonBlank(url) ? url : null;
    }

    private String cleanDescription(String description) {
        if (!isNonBlank(description)) {
            return null;
        }
        String text = Jsoup.parse(description).text();
        String cleaned = WHITESPACE_PATTERN.matcher(text.trim()).replaceAll(" ");
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String cleanText(String text) {
        if (!isNonBlank(text)) {
            return null;
        }
        String cleaned = WHITESPACE_PATTERN.matcher(text.trim()).replaceAll(" ");
        return cleaned.isEmpty() ? null : cleaned;
    }

    private Integer parseInt(String value) {
        if (!isNonBlank(value)) {
            return null;
        }
        try {
            return Integer.parseInt(NON_ISBN_PATTERN.matcher(value).replaceAll(""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate parseDate(String value) {
        if (!isNonBlank(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private boolean isNonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String encode(String value) {
        return value.replace(" ", "+");
    }

    private Document fetchDocument(String url) throws IOException {
        Connection connection = Jsoup.connect(url)
                .header("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("accept-language", "nl-NL,nl;q=0.9,en;q=0.8")
                .header("user-agent", USER_AGENT)
                .timeout(15000)
                .method(Connection.Method.GET)
                .ignoreContentType(true)
                .maxBodySize(0)
                .followRedirects(true);
        return connection.get();
    }

    private record SearchResult(String url, String title, String coverUrl) {
    }
}