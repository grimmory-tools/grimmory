package org.booklore.service.metadata.parser;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.request.FetchMetadataRequest;
import org.booklore.service.SleepService;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenLibraryParser implements BookParser {
    private static final int REQUEST_RATE_LIMIT = 5;
    private static final int RATE_LIMIT_PER = 1000;

    private static final String COVERS_BASE_URI = "https://covers.openlibrary.org/";
    private static final String COVER_PATH = "/b/id/{cover_id}-{size}.jpg";

    private static final String BASE_URI = "https://openlibrary.org/";
    private static final String SEARCH_PATH = "/search.json";
    private static final String WORKS_PATH = "/works/{work_id}.json";
    private static final String SERIES_PATH = "/series/{series_id}.json";
    private static final String EDITIONS_PATH = "/books/{edition_id}.json";
    private static final String AUTHORS_PATH = "/authors/{author_id}.json";

    private static final String SEARCH_FIELDS = "key,author_key,editions,editions.key";
    private static final String USER_AGENT = "Grimmory (developers@grimmory.org)";

    // Public because it has to be for Jackson to use it.
    public static class OpenLibraryTypedValueDeserializer extends StdDeserializer<OpenLibraryTypedValue> {
        public OpenLibraryTypedValueDeserializer() {
            super(OpenLibraryTypedValue.class);
        }

        @Override
        public OpenLibraryTypedValue deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
            JsonNode node = p.readValueAsTree();

            JsonNode typeNode = node.get("type");
            JsonNode valueNode = node.get("value");

            try {
                if (typeNode != null && !typeNode.isNull() && valueNode != null && !valueNode.isNull()) {
                    return new OpenLibraryTypedValue(
                            typeNode.stringValue(),
                            valueNode.stringValue()
                    );
                }
            } catch (DatabindException e) {
                // Do nothing
            }

            try {
                String value = node.stringValue();
                return new OpenLibraryTypedValue("/type/text", value);
            } catch (DatabindException e) {
                // Do nothing
            }

            return null;
        }
    }

    @JsonDeserialize(using = OpenLibraryTypedValueDeserializer.class)
    record OpenLibraryTypedValue(
            String type,
            String value
    ) {}

    record OpenLibraryReference(
            String key
    ) {}

    record OpenLibrarySearchDocumentEditions(
            int numFound,
            List<OpenLibraryReference> docs
    ) {}

    record OpenLibrarySearchDocument(
        String key,
        @JsonProperty("author_key")
        List<String> authorKey,
        OpenLibrarySearchDocumentEditions editions
    ) {}

    record OpenLibrarySearchResult(
            int numFound,
            List<OpenLibrarySearchDocument> docs
    ) {}

    record OpenLibraryAuthor(
            String key,
            OpenLibraryReference type,
            String name
    ) {}

    record OpenLibraryEdition(
            String key,
            OpenLibraryReference type,
            String title,
            Optional<String> subtitle,
            Optional<OpenLibraryTypedValue> description,
            List<OpenLibraryReference> languages,
            List<OpenLibraryReference> authors,
            List<OpenLibraryReference> works,
            Optional<Map<String, List<String>>> identifiers,
            Optional<List<String>> publishers,
            @JsonProperty("publish_date")
            Optional<String> publishDate,
            @JsonProperty("edition_name")
            String editionName,
            @JsonProperty("number_of_pages")
            Optional<Integer> pageCount,
            @JsonProperty("isbn_10")
            Optional<List<String>> isbn10,
            @JsonProperty("isbn_13")
            Optional<List<String>> isbn13,
            Optional<List<Integer>> covers
    ) {
        String getIdentifier(String name) {
            return this.identifiers.flatMap(
                    i -> i.getOrDefault(name, List.of())
                            .stream().findFirst()
            ).orElse(null);
        }
    }

    record OpenLibrarySeries(
            OpenLibraryReference type,
            String name,
            Optional<OpenLibraryTypedValue> description
    ) {}

    record OpenLibraryWorkAuthor(
            OpenLibraryReference type,
            OpenLibraryReference author
    ) {}

    record OpenLibraryWorkSeries(
            String position,
            OpenLibraryReference series
    ) {}

    record OpenLibraryWork (
            String key,
            OpenLibraryReference type,
            String title,
            Optional<String> subtitle,
            Optional<OpenLibraryTypedValue> description,
            List<OpenLibraryWorkAuthor> authors,
            Optional<List<OpenLibraryWorkSeries>> series,
            List<String> subjects,
            Optional<List<Integer>> covers,
            OpenLibraryTypedValue created,
            @JsonProperty("last_modified")
            OpenLibraryTypedValue lastModified
    ) {}

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final SleepService sleepService;

    private long rateLimitResetTime = 0;
    private long rateLimitCounter = 0;

    private URI getURI(String path, Map<String, String> pathParameters, Map<String, String> queryParameters) {
        return UriComponentsBuilder.fromUriString(BASE_URI)
                .path(path)
                .queryParams(MultiValueMap.fromSingleValue(queryParameters))
                .build(pathParameters);
    }

    private synchronized void applyRateLimit() throws InterruptedException {
        long now = System.currentTimeMillis();

        if (rateLimitResetTime < now) {
            rateLimitCounter = 0;
            rateLimitResetTime = now + RATE_LIMIT_PER;
        }

        if (rateLimitCounter >= REQUEST_RATE_LIMIT) {
            // Slow down.
            try {
                sleepService.sleep(Math.max(0, rateLimitResetTime - now));
            } finally {
                rateLimitResetTime = System.currentTimeMillis() + 1000;
                rateLimitCounter = 0;
            }
        } else {
            rateLimitCounter++;
        }
    }

    private <T> T sendRequest(HttpRequest request, Class<T> tClass) throws InterruptedException {
        request = HttpRequest.newBuilder(request, (n, v) -> !n.equalsIgnoreCase("User-Agent"))
                .header("User-Agent", USER_AGENT)
                .build();

        log.debug("Making request: {}", request.uri().toString());

        applyRateLimit();

        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() < 200 || response.statusCode() > 399) {
                log.error("OpenLibrary request failed with status code: {}", response.statusCode());
                throw new RuntimeException("Failed to query OpenLibrary");
            }

            log.debug("Request success with code {}", response.statusCode());

            try (InputStream stream = response.body()) {
                return objectMapper.readValue(stream, tClass);
            }
        } catch (IOException e) {
            log.error("OpenLibrary request failed", e);
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    private String escapeSolrValue(String value) {
        return value.replaceAll("[-+&|!(){}\\[\\]^\"~*?:\\\\/]", "\\\\$0");
    }

    private String getSolrQuery(FetchMetadataRequest fetchMetadataRequest) {
        List<String> predicates = new ArrayList<>();

        if (fetchMetadataRequest.getIsbn() != null && !fetchMetadataRequest.getIsbn().isBlank()) {
            predicates.add("isbn:" + escapeSolrValue(fetchMetadataRequest.getIsbn()));
        }

        if (fetchMetadataRequest.getTitle() != null && !fetchMetadataRequest.getTitle().isBlank()) {
            predicates.add("title:" + escapeSolrValue(fetchMetadataRequest.getTitle()));
        }

        if (fetchMetadataRequest.getAuthor() != null && !fetchMetadataRequest.getAuthor().isBlank()) {
            predicates.add("author_name:" + escapeSolrValue(fetchMetadataRequest.getAuthor()));
        }

        return String.join(" AND ", predicates);
    }

    private OpenLibrarySearchResult search(FetchMetadataRequest fetchMetadataRequest, int limit) throws InterruptedException {
        String solrQuery = getSolrQuery(fetchMetadataRequest);

        if (solrQuery.isEmpty()) {
            // Empty query means no results.
            log.info("No search query available");
            return new OpenLibrarySearchResult(0, List.of());
        }

        URI uri = getURI(
                SEARCH_PATH,
                Map.of(),
                Map.of(
                        "q", solrQuery,
                        "fields", SEARCH_FIELDS,
                        "limit", String.valueOf(limit)
                )
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .GET()
                .build();

        return sendRequest(request, OpenLibrarySearchResult.class);
    }

    private String getId(String type, String reference) {
        String[] parts = reference.split("/");

        if (parts.length != 3 || !parts[1].equalsIgnoreCase(type)) {
            throw new RuntimeException("Wrong key type");
        }

        return parts[2];
    }

    private OpenLibraryAuthor getAuthor(String reference) throws InterruptedException {
        String id = getId("authors", reference);

        URI uri = getURI(AUTHORS_PATH, Map.of("author_id", id), Map.of());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .GET()
                .build();

        return sendRequest(request, OpenLibraryAuthor.class);
    }

    private OpenLibraryEdition getEdition(String reference) throws InterruptedException {
        String id = getId("books", reference);

        URI uri = getURI(EDITIONS_PATH, Map.of("edition_id", id), Map.of());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .GET()
                .build();

        return sendRequest(request, OpenLibraryEdition.class);
    }

    private OpenLibrarySeries getSeries(String reference) throws InterruptedException {
        String id = getId("series", reference);

        URI uri = getURI(SERIES_PATH, Map.of("series_id", id), Map.of());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .GET()
                .build();

        return sendRequest(request, OpenLibrarySeries.class);
    }

    private OpenLibraryWork getWork(String reference) throws InterruptedException {
        String id = getId("works", reference);

        URI uri = getURI(WORKS_PATH, Map.of("work_id", id), Map.of());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .GET()
                .build();

        return sendRequest(request, OpenLibraryWork.class);
    }

    private Optional<URI> getCoverUri(
            Integer coverId,
            String size
    ) {
        if (coverId == null || size == null) {
            return Optional.empty();
        }

        return Optional.of(UriComponentsBuilder.fromUriString(COVERS_BASE_URI)
                .path(COVER_PATH)
                .build(Map.of("cover_id", coverId, "size", size)));
    }

    private Optional<URI> getCoverUri(
            OpenLibraryWork work,
            OpenLibraryEdition edition,
            String size
    ) {
        if (edition != null) {
            Integer editionCoverId = edition.covers.flatMap(c -> c.stream().findFirst()).orElse(null);
            if (editionCoverId != null) {
                return getCoverUri(
                        editionCoverId,
                        size
                );
            }
        }

        Integer workCoverId = work.covers.flatMap(c -> c.stream().findFirst()).orElse(null);
        if (workCoverId != null) {
            return getCoverUri(
                    workCoverId,
                    size
            );
        }

        return Optional.empty();
    }

    private BookMetadata toMetadata(
            OpenLibraryWork work,
            OpenLibrarySeries series,
            OpenLibraryEdition edition,
            List<OpenLibraryAuthor> authors
    ) {
        var builder = BookMetadata.builder()
                .title(work.title)
                .subtitle(work.subtitle.orElse(null))
                .description(work.description.map(OpenLibraryTypedValue::value).orElse(null))
                .authors(authors.stream().map(a -> a.name).toList())
                .thumbnailUrl(getCoverUri(work, edition, "M").map(URI::toString).orElse(null));

        if (series != null) {
            builder.seriesName(series.name);
        }

        if (!work.series.map(List::isEmpty).orElse(true)) {
            String[] position = work.series.get().getFirst().position.split("-");
            if (position.length == 2) {
                try {
                    Float seriesNumber = Float.valueOf(position[0]);
                    Integer seriesTotal = Integer.valueOf(position[1]);

                    builder
                            .seriesNumber(seriesNumber)
                            .seriesTotal(seriesTotal);
                } catch (NumberFormatException e) {
                    // Do nothing
                }
            }
        }

        if (edition != null) {
            builder
                    .openlibraryId(edition.key)
                    .publisher(edition.publishers.flatMap(p -> p.stream().findFirst()).orElse(null))
                    .isbn10(edition.isbn10.flatMap(i -> i.stream().findFirst()).orElse(null))
                    .isbn13(edition.isbn13.flatMap(i -> i.stream().findFirst()).orElse(null))
                    .pageCount(edition.pageCount.orElse(null))
                    .goodreadsId(edition.getIdentifier("goodreads"))
                    .asin(edition.getIdentifier("amazon"));
        } else {
            builder.openlibraryId(work.key);
        }

        return builder.build();
    }

    @Override
    public List<BookMetadata> fetchMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        return fetchMetadataStream(book, fetchMetadataRequest).collectList().block();
    }

    public Flux<BookMetadata> fetchMetadataStream(Book book, FetchMetadataRequest fetchMetadataRequest) {
        return fetchMetadataStream(book, fetchMetadataRequest, 10);
    }

    public Flux<BookMetadata> fetchMetadataStream(Book book, FetchMetadataRequest fetchMetadataRequest, int limit) {
        return Flux.create(sink -> {
            try {
                OpenLibrarySearchResult searchResponse = search(fetchMetadataRequest, limit);

                log.debug("Found {} total results, using {}", searchResponse.numFound, searchResponse.docs.size());

                Map<String, OpenLibraryAuthor> seenAuthors = new HashMap<>();

                for (OpenLibrarySearchDocument document : searchResponse.docs) {
                    if (sink.isCancelled()) {
                        return;
                    }

                    log.debug("OpenLibrary: Fetching metadata for Work: {}", document.key);

                    try {
                        OpenLibraryWork work = this.getWork(document.key);

                        OpenLibrarySeries series = null;

                        if (!work.series.map(List::isEmpty).orElse(true)) {
                            series = this.getSeries(work.series.get().getFirst().series.key);
                        }

                        OpenLibraryEdition edition = null;

                        if (!document.editions.docs.isEmpty()) {
                            edition = this.getEdition(document.editions.docs.getFirst().key);
                        }

                        List<OpenLibraryAuthor> authors = new ArrayList<>();
                        for (String authorKey : document.authorKey) {
                            if (seenAuthors.containsKey(authorKey)) {
                                authors.add(seenAuthors.get(authorKey));
                            } else {
                                OpenLibraryAuthor author = this.getAuthor("/authors/" + authorKey);
                                seenAuthors.put(authorKey, author);
                                authors.add(author);
                            }
                        }

                        BookMetadata metadata = this.toMetadata(
                                work,
                                series,
                                edition,
                                authors
                        );
                        if (metadata != null) {
                            sink.next(metadata);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw e;
                    } catch (Exception e) {
                        log.error("Error fetching metadata for Work: {}", document.key, e);
                    }
                }

                sink.complete();
            } catch (Exception e) {
                sink.error(e);
            }
        });
    }

    @Override
    public BookMetadata fetchTopMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        return fetchMetadataStream(book, fetchMetadataRequest, 1).blockFirst();
    }
}
