package com.adityachandel.booklore.service.metadata.parser;

import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.response.GoogleBooksApiResponse;
import com.adityachandel.booklore.model.dto.request.FetchMetadataRequest;
import com.adityachandel.booklore.model.enums.MetadataProvider;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.util.BookUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleParser implements BookParser {

    private static final Pattern FOUR_DIGIT_YEAR_PATTERN = Pattern.compile("\\d{4}");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern SPECIAL_CHARACTERS_PATTERN = Pattern.compile("[.,\\-\\[\\]{}()!@#$%^&*_=+|~`<>?/\";:]");
    private static final long MIN_REQUEST_INTERVAL_MS = 1500;
    private static final int MAX_SEARCH_TERM_LENGTH = 60;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final ObjectMapper objectMapper;
    private final AppSettingService appSettingService;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private static final String GOOGLE_BOOKS_API_URL = "https://www.googleapis.com/books/v1/volumes";
    private final AtomicLong lastRequestTime = new AtomicLong(0);

    @Override
    public BookMetadata fetchTopMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        List<BookMetadata> fetchedBookMetadata = fetchMetadata(book, fetchMetadataRequest);
        return fetchedBookMetadata.isEmpty() ? null : fetchedBookMetadata.getFirst();
    }

    @Override
    public List<BookMetadata> fetchMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        if (fetchMetadataRequest.getIsbn() != null && !fetchMetadataRequest.getIsbn().isBlank()) {
            return getMetadataListByIsbn(ParserUtils.cleanIsbn(fetchMetadataRequest.getIsbn()));
        }
        String searchTerm = getSearchTerm(book, fetchMetadataRequest);
        return searchTerm != null ? getMetadataListByTerm(searchTerm) : List.of();
    }

    private List<BookMetadata> getMetadataListByIsbn(String isbn) {
        return fetchFromApi("isbn:" + isbn.replace("-", ""));
    }

    public List<BookMetadata> getMetadataListByTerm(String term) {
        return fetchFromApi(term);
    }

    private List<BookMetadata> fetchFromApi(String query) {
        try {
            waitForRateLimit();

            URI uri = UriComponentsBuilder.fromUriString(getApiUrl())
                    .queryParam("q", query)
                    .build()
                    .toUri();

            log.info("Google Books API URL: {}", uri);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return parseGoogleBooksApiResponse(response.body());
            }

            log.error("Failed to fetch metadata from Google Books API. Status: {}, Response: {}",
                    response.statusCode(), response.body());
            return List.of();
        } catch (IOException | InterruptedException e) {
            log.error("Error occurred while fetching metadata from Google Books API", e);
            return List.of();
        }
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

        return BookMetadata.builder()
                .provider(MetadataProvider.Google)
                .googleId(item.getId())
                .title(volumeInfo.getTitle())
                .subtitle(volumeInfo.getSubtitle())
                .publisher(volumeInfo.getPublisher())
                .publishedDate(parseDate(volumeInfo.getPublishedDate()))
                .description(volumeInfo.getDescription())
                .authors(Optional.ofNullable(volumeInfo.getAuthors()).orElse(Set.of()))
                .categories(Optional.ofNullable(volumeInfo.getCategories()).orElse(Set.of()))
                .isbn13(isbns.get("ISBN_13"))
                .isbn10(isbns.get("ISBN_10"))
                .pageCount(volumeInfo.getPageCount())
                .thumbnailUrl(extractBestCoverImage(volumeInfo.getImageLinks()))
                .language(volumeInfo.getLanguage())
                .build();
    }

    private String extractBestCoverImage(GoogleBooksApiResponse.Item.ImageLinks links) {
        if (links == null) {
            return null;
        }
        return Stream.of(
                        links.getExtraLarge(),
                        links.getLarge(),
                        links.getMedium(),
                        links.getSmall(),
                        links.getThumbnail(),
                        links.getSmallThumbnail())
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
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
                .orElseGet(() -> Optional.ofNullable(book.getFileName())
                        .filter(fileName -> !fileName.isEmpty())
                        .map(BookUtils::cleanFileName)
                        .orElse(null));

        if (searchTerm == null) {
            return null;
        }

        searchTerm = SPECIAL_CHARACTERS_PATTERN.matcher(searchTerm).replaceAll("").trim();
        searchTerm = "intitle:" + truncateToMaxWords(searchTerm);

        if (request.getAuthor() != null && !request.getAuthor().isEmpty()) {
            searchTerm += " inauthor:" + request.getAuthor();
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

    private LocalDate parseDate(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        try {
            if (FOUR_DIGIT_YEAR_PATTERN.matcher(input).matches()) {
                return LocalDate.of(Integer.parseInt(input), 1, 1);
            }
            return LocalDate.parse(input, DATE_FORMATTER);
        } catch (Exception e) {
            return null;
        }
    }

    private String getApiUrl() {
        String language = appSettingService.getAppSettings().getMetadataProviderSettings().getGoogle().getLanguage();

        if (language == null || language.isEmpty()) {
            return GOOGLE_BOOKS_API_URL;
        }

        return UriComponentsBuilder.fromUriString(GOOGLE_BOOKS_API_URL)
            .queryParam("langRestrict", language)
            .build()
            .toUri()
            .toString();
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
}