package org.booklore.service.metadata.parser;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.AudiobookMetadata;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.request.FetchMetadataRequest;
import org.booklore.model.enums.MetadataProvider;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.util.BookUtils;
import org.booklore.util.LanguageNormalizer;
import org.jsoup.Jsoup;
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
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class AudibleParser implements BookParser, DetailedMetadataProvider {
    private static final String DEFAULT_TLD = "com";

    private static final Map<String, String> BASE_URIS = Map.of(
            "com", "https://api.audible.com/",
            "co.uk", "https://api.audible.co.uk/",
            "de", "https://api.audible.de/",
            "fr", "https://api.audible.fr/",
            "it", "https://api.audible.it/",
            "es", "https://api.audible.es/",
            "ca", "https://api.audible.ca/",
            "com.au", "https://api.audible.com.au/",
            "co.jp", "https://api.audible.co.jp",
            "in", "https://api.audible.in/"
    );

    private static final String PATH_SEARCH = "/1.0/catalog/products";
    private static final String PATH_ASIN = "/1.0/catalog/products/{asin}";
    private static final String IMAGE_SIZE = "1000";

    private static final Set<String> RESPONSE_GROUPS = Set.of(
            "rating",
            "category_ladders",
            "contributors",
            "media",
            "product_desc",
            "product_attrs",
            "product_extended_attrs",
            "series"
    );

    private static final Pattern NON_ALPHANUMERIC_PATTERN = Pattern.compile("[^\\p{L}\\p{M}0-9]+");
    private static final Pattern ASIN_PATTERN = Pattern.compile("([A-Z0-9]{10})");

    private final AppSettingService appSettingService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AudibleSeries (
            String asin,
            String title,
            String sequence
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AudibleContributor (
            Optional<String> asin,
            String name
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AudibleRatingDistribution (
            @JsonProperty("average_rating")
            Double averageRating
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AudibleRating (
            @JsonProperty("num_reviews")
            int reviewCount,
            @JsonProperty("overall_distribution")
            AudibleRatingDistribution overallDistribution
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AudibleCategory(
            String name
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AudibleCategoryLadder(
            String root,
            List<AudibleCategory> ladder
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AudibleProduct (
            String asin,
            String title,
            Optional<String> subtitle,
            List<AudibleContributor> authors,
            List<AudibleContributor> narrators,
            @JsonProperty("category_ladders")
            Optional<List<AudibleCategoryLadder>> categoryLadders,
            AudibleRating rating,
            @JsonProperty("product_images")
            Map<String, String> productImages,
            @JsonProperty("merchandising_summary")
            String merchandisingSummary,
            String language,
            @JsonProperty("format_type")
            String formatType,
            @JsonProperty("release_date")
            LocalDate releaseDate,
            @JsonProperty("publisher_name")
            String publisherName,
            @JsonProperty("runtime_length_min")
            int runtimeLengthMinutes,
            Optional<List<AudibleSeries>> series
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AudibleProductContainer (
            AudibleProduct product
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AudibleProductList (
            List<AudibleProduct> products
    ) {}

    private String getBaseURI() {
        var settings = appSettingService.getAppSettings().getMetadataProviderSettings();
        String tld = DEFAULT_TLD;
        if (settings != null && settings.getAudible() != null && settings.getAudible().getDomain() != null) {
            tld = settings.getAudible().getDomain();
        }
        return BASE_URIS.getOrDefault(tld, BASE_URIS.get(DEFAULT_TLD));
    }

    private URI getURI(
            String path,
            Map<String, String> pathParameters,
            Map<String, String> queryParameters
    ) {
        return UriComponentsBuilder.fromUriString(getBaseURI())
                .path(path)
                .queryParams(MultiValueMap.fromSingleValue(queryParameters))
                .build(pathParameters);
    }

    private <T> T sendRequest(HttpRequest request, Class<T> tClass) throws InterruptedException {
        log.debug("Making request: {}", request.uri().toString());

        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() < 200 || response.statusCode() > 399) {
                log.error("Audible request failed with status code: {}", response.statusCode());
                throw new RuntimeException("Failed to query Audible");
            }

            log.debug("Request success with code {}", response.statusCode());

            try (InputStream stream = response.body()) {
                return objectMapper.readValue(stream, tClass);
            }
        } catch (IOException e) {
            log.error("Audible request failed", e);
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    private AudibleProduct lookup(String asin) {
        if (asin == null || asin.isBlank()) {
            return null;
        }

        URI uri = getURI(
                PATH_ASIN,
                Map.of("asin", asin),
                Map.of(
                        "response_groups", String.join(",", RESPONSE_GROUPS),
                        "image_sizes", IMAGE_SIZE
                )
        );

        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .build();

        try {
            return sendRequest(request, AudibleProductContainer.class).product;
        } catch (Exception e) {
            return null;
        }
    }

    private List<AudibleProduct> search(String query, int limit) {
        URI uri = getURI(
                PATH_SEARCH,
                Map.of(),
                Map.of(
                        "keywords", query,
                        "num_results", String.valueOf(limit),
                        "products_sort_by", "Relevance",
                        "response_groups", String.join(",", RESPONSE_GROUPS),
                        "image_sizes", IMAGE_SIZE
                )
        );

        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .build();

        try {
            return sendRequest(request, AudibleProductList.class).products;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String cleanSearchTerm(String text) {
        return Arrays.stream(text.split(" "))
                .map(word -> NON_ALPHANUMERIC_PATTERN.matcher(word).replaceAll(" ").trim())
                .filter(word -> !word.isEmpty())
                .collect(Collectors.joining(" "));
    }

    private String getSearchTerm(Book book, FetchMetadataRequest request) {
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

        return searchTerm.toString();
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

    @Override
    public BookMetadata fetchTopMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        AudibleProduct existingProduct = lookup(getExistingAsin(book));
        if (existingProduct != null) {
            return toMetadata(existingProduct);
        }

        AudibleProduct lookupProduct = lookup(fetchMetadataRequest.getAsin());
        if (lookupProduct != null) {
            return toMetadata(lookupProduct);
        }

        String searchTerm = getSearchTerm(book, fetchMetadataRequest);
        if (searchTerm == null) {
            return null;
        }

        return search(searchTerm, 1)
                .stream()
                .map(this::toMetadata)
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<BookMetadata> fetchMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        String searchTerm = getSearchTerm(book, fetchMetadataRequest);

        if (searchTerm == null) {
            return List.of();
        }

        return search(searchTerm, 10)
                .stream()
                .map(this::toMetadata)
                .toList();
    }

    @Override
    public BookMetadata fetchDetailedMetadata(String audibleId) {
        log.info("Audible: Fetching metadata for: {}", audibleId);

        try {
            return this.toMetadata(this.lookup(audibleId));
        } catch (Exception e) {
            log.error("Failed to fetch Audible metadata for ID {}: {}", audibleId, e.getMessage());
            return null;
        }
    }

    private String stripHTML(String value) {
        if (value == null) {
            return null;
        }

        return Jsoup.parse(value).text();
    }

    private Float valueOfOrNull(String value) {
        try {
            return Float.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BookMetadata toMetadata(AudibleProduct product) {
        AudiobookMetadata audiobookMetadata = AudiobookMetadata.builder()
                .durationSeconds(product.runtimeLengthMinutes * 60L)
                .build();

        Optional<AudibleSeries> series = product.series.flatMap(s -> s.stream().findFirst());
        boolean abridged = !("unabridged".equalsIgnoreCase(product.formatType));

        String description = stripHTML(product.merchandisingSummary);

        List<String> authors = product.authors
                .stream()
                .map(AudibleContributor::name)
                .toList();

        String narrator = product.narrators
                .stream()
                .map(AudibleContributor::name)
                .findFirst()
                .orElse(null);

        Set<String> categories = product.categoryLadders
                .flatMap(l -> l.stream().findFirst())
                .map(
                        l -> l.ladder
                                .stream()
                                .map(AudibleCategory::name)
                                .collect(Collectors.toSet())
                )
                .orElse(Set.of());

        return BookMetadata.builder()
                .provider(MetadataProvider.Audible)
                .asin(product.asin)
                .audibleId(product.asin)
                .title(product.title)
                .subtitle(product.subtitle.orElse(null))
                .authors(authors)
                .narrator(narrator)
                .categories(categories)
                .description(description)
                .seriesName(series.map(s -> s.title).orElse(null))
                .seriesNumber(series.map(AudibleSeries::sequence).map(this::valueOfOrNull).orElse(null))
                .publisher(product.publisherName)
                .publishedDate(product.releaseDate)
                .language(LanguageNormalizer.normalize(product.language))
                .thumbnailUrl(product.productImages.get(IMAGE_SIZE))
                .audibleRating(product.rating.overallDistribution().averageRating)
                .audibleReviewCount(product.rating.reviewCount)
                .abridged(abridged)
                .audiobookMetadata(audiobookMetadata)
                .build();
    }
}
