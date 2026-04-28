package org.booklore.service.metadata.parser;

import org.booklore.model.dto.BookMetadata;
import org.booklore.model.enums.MetadataProvider;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenLibraryParser {

    private final ObjectMapper objectMapper;

    private static final String SEARCH_URL = "https://openlibrary.org/search.json";
    private static final String COVER_URL = "https://covers.openlibrary.org/b/id/%s-M.jpg";
    private static final int LIMIT = 20;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public List<BookMetadata> searchByTerm(String term) {
        return searchByTerm(term, 0);
    }

    public List<BookMetadata> searchByTerm(String term, int page) {
        log.info("Open Library: searching for '{}', page={}", term, page);
        try {
            URI uri = UriComponentsBuilder.fromUriString(SEARCH_URL)
                    .queryParam("q", term)
                    .queryParam("limit", LIMIT)
                    .queryParam("offset", page * LIMIT)
                    .queryParam("fields", "key,title,author_name,first_publish_year,isbn,cover_i,subject,number_of_pages_median,language")
                    .build()
                    .toUri();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return parseResponse(response.body());
            } else {
                log.error("Open Library returned status {}", response.statusCode());
                return List.of();
            }
        } catch (Exception e) {
            log.error("Error searching Open Library: {}", e.getMessage());
            return List.of();
        }
    }

    public List<BookMetadata> searchByIsbn(String isbn) {
        return searchByTerm("isbn:" + isbn);
    }

    private List<BookMetadata> parseResponse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode docs = root.path("docs");
            List<BookMetadata> results = new ArrayList<>();

            for (JsonNode doc : docs) {
                BookMetadata meta = docToMetadata(doc);
                if (meta != null) {
                    results.add(meta);
                }
            }

            log.info("Open Library: {} results", results.size());
            return results;
        } catch (Exception e) {
            log.error("Failed to parse Open Library response: {}", e.getMessage());
            return List.of();
        }
    }

    private BookMetadata docToMetadata(JsonNode doc) {
        try {
            String title = doc.path("title").asText(null);
            if (title == null || title.isBlank()) return null;

            List<String> authors = new ArrayList<>();
            JsonNode authorNode = doc.path("author_name");
            if (authorNode.isArray()) {
                for (JsonNode a : authorNode) authors.add(a.asText());
            }

            String isbn13 = null;
            String isbn10 = null;
            JsonNode isbnNode = doc.path("isbn");
            if (isbnNode.isArray()) {
                for (JsonNode isbn : isbnNode) {
                    String val = isbn.asText();
                    if (isbn13 == null && val.length() == 13) isbn13 = val;
                    if (isbn10 == null && val.length() == 10) isbn10 = val;
                    if (isbn13 != null && isbn10 != null) break;
                }
            }

            String thumbnail = null;
            JsonNode coverId = doc.path("cover_i");
            if (!coverId.isMissingNode() && !coverId.isNull()) {
                thumbnail = String.format(COVER_URL, coverId.asLong());
            }

            LocalDate publishedDate = null;
            JsonNode yearNode = doc.path("first_publish_year");
            if (!yearNode.isMissingNode() && !yearNode.isNull()) {
                publishedDate = LocalDate.of(yearNode.asInt(), 1, 1);
            }

            Integer pageCount = null;
            JsonNode pages = doc.path("number_of_pages_median");
            if (!pages.isMissingNode() && !pages.isNull()) {
                pageCount = pages.asInt();
            }

            Set<String> categories = new LinkedHashSet<>();
            JsonNode subjects = doc.path("subject");
            if (subjects.isArray()) {
                int count = 0;
                for (JsonNode s : subjects) {
                    if (count++ >= 5) break;
                    categories.add(s.asText());
                }
            }

            String language = null;
            JsonNode langNode = doc.path("language");
            if (langNode.isArray() && langNode.size() > 0) {
                language = langNode.get(0).asText();
            }

            return BookMetadata.builder()
                    .provider(MetadataProvider.OpenLibrary)
                    .title(title)
                    .authors(authors)
                    .isbn13(isbn13)
                    .isbn10(isbn10)
                    .thumbnailUrl(thumbnail)
                    .publishedDate(publishedDate)
                    .pageCount(pageCount)
                    .categories(categories)
                    .language(language)
                    .build();

        } catch (Exception e) {
            log.warn("Failed to parse Open Library doc: {}", e.getMessage());
            return null;
        }
    }
}
