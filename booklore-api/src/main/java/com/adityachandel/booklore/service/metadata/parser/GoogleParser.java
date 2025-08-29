package com.adityachandel.booklore.service.metadata.parser;

import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.response.GoogleBooksApiResponse;
import com.adityachandel.booklore.model.dto.request.FetchMetadataRequest;
import com.adityachandel.booklore.model.enums.MetadataProvider;
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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleParser implements BookParser {

    private final ObjectMapper objectMapper;
    private static final String GOOGLE_BOOKS_API_URL = "https://www.googleapis.com/books/v1/volumes";

    @Override
    public BookMetadata fetchTopMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        List<BookMetadata> fetchedBookMetadata = fetchMetadata(book, fetchMetadataRequest);
        return fetchedBookMetadata.isEmpty() ? null : fetchedBookMetadata.getFirst();
    }

    @Override
    public List<BookMetadata> fetchMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        // 1. If ISBN exists, prioritize it
        if (fetchMetadataRequest.getIsbn() != null && !fetchMetadataRequest.getIsbn().isBlank()) {
            return getMetadataListByIsbn(fetchMetadataRequest.getIsbn());
        }

        // 2. Otherwise fallback to existing title/author search
        String searchTerm = getSearchTerm(book, fetchMetadataRequest);
        return searchTerm != null ? getMetadataListByTerm(searchTerm) : List.of();
    }

    private List<BookMetadata> getMetadataListByIsbn(String isbn) {
        try {
            URI uri = UriComponentsBuilder.fromUriString(GOOGLE_BOOKS_API_URL)
                    .queryParam("q", "isbn:" + isbn.replace("-", ""))
                    .build()
                    .toUri();

            log.info("Google Books API URL (ISBN): {}", uri);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return parseGoogleBooksApiResponse(response.body());
            } else {
                log.error("Failed to fetch metadata from Google Books API with ISBN. Status: {}, Response: {}",
                        response.statusCode(), response.body());
                return List.of();
            }
        } catch (IOException | InterruptedException e) {
            log.error("Error occurred while fetching metadata from Google Books API with ISBN", e);
            return List.of();
        }
    }

    public List<BookMetadata> getMetadataListByTerm(String term) {
        try {
            URI uri = UriComponentsBuilder.fromUriString(GOOGLE_BOOKS_API_URL)
                    .queryParam("q", term)
                    .build()
                    .toUri();

            log.info("Google Books API URLx: {}", uri);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return parseGoogleBooksApiResponse(response.body());
            } else {
                log.error("Failed to fetch metadata from Google Books API. Status: {}, Response: {}", response.statusCode(), response.body());
                return List.of();
            }
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

        String highResCover = Optional.ofNullable(volumeInfo.getImageLinks())
                .map(links -> {
                    if (links.getExtraLarge() != null) return links.getExtraLarge();
                    if (links.getLarge() != null) return links.getLarge();
                    if (links.getMedium() != null) return links.getMedium();
                    if (links.getSmall() != null) return links.getSmall();
                    if (links.getThumbnail() != null) return links.getThumbnail();
                    return links.getSmallThumbnail();
                })
                .orElse(null);

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
                .thumbnailUrl(highResCover)
                .language(volumeInfo.getLanguage())
                .build();
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

        if (searchTerm != null) {
            searchTerm = searchTerm.replaceAll("[.,\\-\\[\\]{}()!@#$%^&*_=+|~`<>?/\";:]", "").trim();
            searchTerm = truncateToMaxLength(searchTerm, 60);
        }

        if (searchTerm != null && request.getAuthor() != null && !request.getAuthor().isEmpty()) {
            searchTerm += " " + request.getAuthor();
        }

        return searchTerm;
    }

    private String truncateToMaxLength(String input, int maxLength) {
        String[] words = input.split("\\s+");
        StringBuilder truncated = new StringBuilder();

        for (String word : words) {
            if (truncated.length() + word.length() + 1 > maxLength) break;
            if (!truncated.isEmpty()) truncated.append(" ");
            truncated.append(word);
        }

        return truncated.toString();
    }

    public LocalDate parseDate(String input) {
        try {
            if (input.matches("\\d{4}")) {
                return LocalDate.of(Integer.parseInt(input), 1, 1);
            }
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            return LocalDate.parse(input, formatter);
        } catch (Exception e) {
            return null;
        }
    }
}