package org.booklore.service.metadata.parser;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.request.FetchMetadataRequest;
import org.booklore.model.dto.settings.MetadataProviderSettings;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.MetadataProvider;
import org.booklore.service.appsettings.AppSettingService;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
@Service
public class AppleBooksParser implements BookParser {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResultItem(
            Optional<String> wrapperType,
            Optional<Long> trackId,
            Optional<Long> collectionId,
            Optional<String> trackName,
            Optional<String> collectionName,
            Optional<String> artistName,
            Optional<String> description,
            List<String> genres,
            String primaryGenreName,
            Optional<String> releaseDate,
            Optional<String> artworkUrl100,
            Double averageUserRating,
            Integer userRatingCount,
            Optional<String> trackViewUrl,
            Optional<String> collectionViewUrl,
            String language
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ResultsWrapper(
        List<ResultItem> results
    ) {}

    private static final String DEFAULT_COUNTRY = "US";
    private static final Pattern ARTWORK_SIZE_PATTERN = Pattern.compile("\\d+x\\d+(?:bb)?");
    private static final int THUMBNAIL_SIZE = 600;
    private static final String ITUNES_SEARCH_URL = "https://itunes.apple.com/search";
    private static final String ITUNES_LOOKUP_URL = "https://itunes.apple.com/lookup";
    private static final int DEFAULT_LIMIT = 10;
    private static final Set<String> VALID_WRAPPER_TYPES = Set.of("track", "collection", "audiobook");

    private final ObjectMapper objectMapper;
    private final AppSettingService appSettingService;
    private final HttpClient httpClient;

    private <T> T fetch(URI uri, Class<T> tClass) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .GET()
                .build();

        try {
            HttpResponse<InputStream> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofInputStream()
            );

            if (response.statusCode() < 200 || response.statusCode() > 399) {
                log.error("Request failed with status code: {}", response.statusCode());
                throw new RuntimeException("Failed to query Apple Books");
            }

            log.debug("Request success with code {}", response.statusCode());

            try (InputStream stream = response.body()) {
                return objectMapper.readValue(stream, tClass);
            }
        } catch (IOException e) {
            log.error("Error fetching metadata from iTunes API: {}", e.getMessage());
        } catch (InterruptedException e) {
            log.error("iTunes API request interrupted: {}", e.getMessage());
            Thread.currentThread().interrupt();
        }

        return null;
    }

    private String stripHTML(String value) {
        return Jsoup.parse(value).text();
    }

    private BookMetadata toMetadata(ResultItem result) {
        String collectionId = result.collectionId.map(id -> "collection:" + id).orElse(null);
        String trackId = result.trackId().map(tid -> "track:" + tid)
                .orElse(collectionId);

        var categories = new LinkedHashSet<String>();
        if (result.genres() != null) {
            categories.addAll(result.genres());
        }  else if (result.primaryGenreName() != null) {
            categories.add(result.primaryGenreName());
        }

        return BookMetadata.builder()
                .provider(MetadataProvider.AppleBooks)
                .applebooksId(trackId)
                .title(result.trackName.orElse(result.collectionName.orElse(null)))
                .authors(result.artistName.map(List::of).orElse(List.of()))
                .description(result.description.map(this::stripHTML).orElse(null))
                .publishedDate(result.releaseDate.map(r -> OffsetDateTime.parse(r).toLocalDate()).orElse(null))
                .categories(categories)
                .thumbnailUrl(result.artworkUrl100.map(u -> resizeArtworkUrl(u, THUMBNAIL_SIZE, THUMBNAIL_SIZE)).orElse(null))
                .applebooksRating(result.averageUserRating())
                .applebooksReviewCount(result.userRatingCount())
                .externalUrl(result.trackViewUrl.orElse(result.collectionViewUrl.orElse(null)))
                .language(result.language() != null ? result.language().toLowerCase(Locale.ROOT) : null)
                .build();
    }

    private List<BookMetadata> fetchBookMetadata(URI uri) {
        log.debug("Fetching books from URI: {}", uri);
        var response = fetch(uri, ResultsWrapper.class);

        if (response == null) {
            return List.of();
        }

        return response.results.stream()
                .filter(r -> r.wrapperType.map(VALID_WRAPPER_TYPES::contains).orElse(true))
                .map(this::toMetadata)
                .toList();
    }

    private List<BookMetadata> search(String term, String entity, int limit) {
        return fetchBookMetadata(
                getUri(
                    ITUNES_SEARCH_URL,
                    Map.of("term", term, "entity", entity, "limit", String.valueOf(limit)),
                    getCountry()
                )
        );
    }

    private List<BookMetadata> searchByIsbn(String isbn, String entity) {
        return fetchBookMetadata(
            getUri(
                    ITUNES_LOOKUP_URL,
                    Map.of("isbn", isbn, "entity", entity),
                    getCountry()
            )
        );
    }

    private URI getUri(String uri, Map<String, String> params, String country) {
        return UriComponentsBuilder.fromUriString(uri)
                .queryParams(MultiValueMap.fromSingleValue(params))
                .queryParam("country", country)
                .build()
                .toUri();
    }

    private String getCountry() {
        var appleBooks = appSettingService.getAppSettings()
                .getMetadataProviderSettings()
                .getAppleBooks();

        if (appleBooks == null || appleBooks.getCountry().isBlank()) {
            return DEFAULT_COUNTRY;
        }

        return appleBooks.getCountry().toUpperCase(Locale.ROOT);
    }

    private static String determineEntity(Book book) {
        if (book == null || book.getPrimaryFile() == null) {
            return "ebook";
        }

        if (book.getPrimaryFile().getBookType() == BookFileType.AUDIOBOOK) {
            return "audiobook";
        }

        return "ebook";
    }

    public static String resizeArtworkUrl(String url, int width, int height) {
        var matcher = ARTWORK_SIZE_PATTERN.matcher(url);
        if (matcher.find()) {
            return matcher.replaceFirst(width + "x" + height + "bb");
        }
        return url;
    }

    @Override
    public BookMetadata fetchTopMetadata(Book book, FetchMetadataRequest request) {
        var results = fetchMetadata(book, request);
        return results.isEmpty() ? null : results.getFirst();
    }

    @Override
    public List<BookMetadata> fetchMetadata(Book book, FetchMetadataRequest request) {
        String entity = determineEntity(book);

        if (request.getIsbn() != null && !request.getIsbn().isBlank()) {
            String cleanIsbn = ParserUtils.cleanIsbn(request.getIsbn());
            log.info("Searching with ISBN: {}", cleanIsbn);
            var results = searchByIsbn(cleanIsbn, entity);
            if (!results.isEmpty()) {
                return results;
            }
        }

        String title = request.getTitle() == null ? "" : request.getTitle();
        String author = request.getAuthor() == null ? "" : request.getAuthor();

        if (!title.isBlank()) {
            var term = !author.isBlank() ? title + " " + author : title;
            log.info("Searching with term: {}", term);
            return search(term, entity, DEFAULT_LIMIT);
        }

        return List.of();
    }
}
