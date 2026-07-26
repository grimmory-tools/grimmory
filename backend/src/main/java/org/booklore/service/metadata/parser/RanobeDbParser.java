package org.booklore.service.metadata.parser;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.request.FetchMetadataRequest;
import org.booklore.model.dto.response.ranobedbapi.RanobedbBookResponse;
import org.booklore.model.dto.response.ranobedbapi.RanobedbSearchResponse;
import org.booklore.model.enums.MetadataProvider;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.util.BookUtils;
import org.booklore.util.LanguageNormalizer;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.DateTimeException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class RanobeDbParser implements BookParser {
    private static final String RANOBEDB_URL = "https://ranobedb.org/api/v0/";
    private static final String RANOBEDB_IMAGE_URL = "https://images.ranobedb.org/";

    private final ObjectMapper objectMapper;
    private final AppSettingService appSettingService;
    private final HttpClient httpClient;
    private final Cache<String, Integer> authorIds = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofHours(1))
            .build();

    // Rate limiter
    private static final int MAX_REQUESTS_PER_WINDOW = 60;
    private static final long RATE_LIMIT_WINDOW_MS = 60000; // 60 seconds in milliseconds
    private static final long TOKEN_GENERATION_INTERVAL = RATE_LIMIT_WINDOW_MS / MAX_REQUESTS_PER_WINDOW;
    private final Object waitLock = new Object();
    private long lastTokenGenerationTime = 0;
    private long tokenCount = MAX_REQUESTS_PER_WINDOW;

    private record SearchTerms(String title, Integer authorId) {}

    @Override
    public List<BookMetadata> fetchMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {

        SearchTerms searchTerm = getSearchTerm(book, fetchMetadataRequest);
        if (searchTerm == null) {
            log.warn("No valid search term provided for metadata fetch.");
            return Collections.emptyList();
        }
        return getMetadataListByTerm(searchTerm, false);
    }

    @Override
    public BookMetadata fetchTopMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        SearchTerms searchTerm = getSearchTerm(book, fetchMetadataRequest);
        if (searchTerm == null) {
            log.warn("No valid search term provided for metadata fetch.");
            return null;
        }
        List<BookMetadata> metadataList = getMetadataListByTerm(searchTerm, true);
        return metadataList.isEmpty() ? null : metadataList.getFirst();
    }

    private void waitForRateLimit() throws InterruptedException {
        while (true) {
            synchronized (waitLock) {
                long currentTime = System.currentTimeMillis();
                long timeSinceLastRequest = currentTime - lastTokenGenerationTime;

                // Refill tokens based on time elapsed
                if (timeSinceLastRequest > TOKEN_GENERATION_INTERVAL) {
                    long tokensToAdd = timeSinceLastRequest / TOKEN_GENERATION_INTERVAL;

                    tokenCount = Math.min(tokenCount + tokensToAdd, MAX_REQUESTS_PER_WINDOW);

                    // Bring us to when the last token was generated - which is likely
                    // in the past because of rounding.
                    lastTokenGenerationTime += tokensToAdd * TOKEN_GENERATION_INTERVAL;
                }

                // Try to consume a token
                if (tokenCount > 0) {
                    tokenCount--;
                    return; // Successfully acquired a token
                }
            }

            // No tokens available, wait before retrying
            try {
                Thread.sleep(TOKEN_GENERATION_INTERVAL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Rate limiter interrupted", e);
                throw e;
            }
        }
    }

    private List<BookMetadata> getMetadataListByTerm(SearchTerms term, boolean fetchTop) {
        log.info("Ranobedb: Fetching metadata for term: '{}'", term);

        List<BookMetadata> metadataList = getMetadataList(term, fetchTop);

        if (metadataList != null && metadataList.isEmpty() && term.authorId() != null) {
            log.info("RanobeDB: Failed to find results with author, trying again with only the title.");
            metadataList = getMetadataList(new SearchTerms(term.title(), null), fetchTop);
        }

        if (metadataList != null) return metadataList;
        return Collections.emptyList();
    }

    private List<BookMetadata> getMetadataList(SearchTerms term, boolean fetchTop) {
        try {
            // Apply rate limiting before making the API request
            waitForRateLimit();

            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(RANOBEDB_URL)
                    .path("/books")
                    .queryParam("q", term.title())
                    .queryParam("limit", 20)
                    .queryParam("rf", "digital")
                    .queryParam("rfl", "or");

            if (term.authorId() != null) {
                builder.queryParam("staff", term.authorId())
                        .queryParam("sl", "and");
            }

            URI uri = builder.build().toUri();

          HttpRequest request = HttpRequest.newBuilder()
                  .uri(uri)
                  .header("User-Agent", "Grimmory/1.0 (Book and Comic Metadata Fetcher; +https://github.com/grimmory-tools/grimmory)")
                  .GET()
                  .build();

          HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

          if (response.statusCode() == 200) {
              List<BookMetadata> metadataList = parseRanobeDbApiResponse(response.body(), fetchTop);
              log.info("Ranobedb: Found {} results for term: '{}'", metadataList.size(), term);
              return metadataList;
          } else {
              log.error("Ranobedb Search API returned status code {}", response.statusCode());
          }
      } catch (IOException | InterruptedException e) {
          log.error("Error fetching metadata from Ranobedb Search API", e);
      }
      return null;
    }

    private SearchTerms getSearchTerm(Book book, FetchMetadataRequest request) {
        String title = null;
        if (request.getTitle() != null && !request.getTitle().isEmpty()) {
            title = request.getTitle();
        } else if (book.getPrimaryFile() != null && book.getPrimaryFile().getFileName() != null && !book.getPrimaryFile().getFileName().isEmpty()) {
            title = BookUtils.cleanFileName(book.getPrimaryFile().getFileName());
        }
        if (title == null) {
            return null;
        }
        Integer authorId = getAuthorID(request.getAuthor());
        return new SearchTerms(title, authorId);
    }

    private Integer getAuthorID(String author) {
        if (author == null || author.isEmpty()) {
            return null;
        }

        Integer cachedAuthorId = authorIds.getIfPresent(author);
        if (cachedAuthorId != null) {
            return cachedAuthorId;
        }

        try {
            // Apply rate limiting before making the API request
            waitForRateLimit();

            URI uri = UriComponentsBuilder.fromUriString(RANOBEDB_URL)
                    .path("/staff")
                    .queryParam("q", author)
                    .queryParam("limit", 1)
                    .build()
                    .toUri();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("User-Agent", "BookLore/1.0 (Book and Comic Metadata Fetcher; +https://github.com/booklore-app/booklore)")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                // parse response and return first staff id
                var root = objectMapper.readTree(response.body());
                var staffArray = root.path("staff");
                if (staffArray.isArray() && !staffArray.isEmpty()) {
                    var staffNode = staffArray.get(0).path("id");
                    if (staffNode.isInt()) {
                        authorIds.put(author, staffNode.asInt());
                        return staffNode.asInt();
                    }
                }
            } else {
                log.error("Ranobedb Staff API returned status code {}", response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            log.error("Error fetching staff from Ranobedb API", e);
        }
        return null;
    }

    private List<BookMetadata> parseRanobeDbApiResponse(String responseBody, boolean fetchTop) throws IOException {
        RanobedbSearchResponse searchResponse = objectMapper.readValue(responseBody, RanobedbSearchResponse.class);
        if (searchResponse.getBooks() == null) {
            return Collections.emptyList();
        }

        if (fetchTop && !searchResponse.getBooks().isEmpty()) {
            BookMetadata topMetadata = searchResultToBookMetadata(searchResponse.getBooks().getFirst().getId());
            return topMetadata != null ? List.of(topMetadata) : Collections.emptyList();
        } else {
            return searchResponse.getBooks().stream()
                    .map(book -> searchResultToBookMetadata(book.getId()))
                    .filter(Objects::nonNull)
                    .toList();
        }
    }

    private boolean isPreferringRomaji() {
        var appSettings = appSettingService.getAppSettings();

        if (appSettings == null || appSettings.getMetadataProviderSettings() == null) {
            return false;
        }

        var ranobedbSettings = appSettings.getMetadataProviderSettings().getRanobedb();

        if (ranobedbSettings == null) {
            return false;
        }

        return ranobedbSettings.isPreferRomaji();
    }

    private String getPreferredValue(String romaji, String normal) {
        if (isPreferringRomaji() && romaji != null && !romaji.isBlank()) {
            return romaji;
        }

        if (normal != null && !normal.isBlank()) {
            return normal;
        }

        return romaji;
    }

    private BookMetadata searchResultToBookMetadata(int bookId) {

        try {
            // Apply rate limiting before making the API request
            waitForRateLimit();
            
            log.info("Ranobedb: Fetching metadata for book id: '{}'", bookId);
            URI uri = UriComponentsBuilder.fromUriString(RANOBEDB_URL)
                    .pathSegment("book", String.valueOf(bookId))
                    .build()
                    .toUri();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("User-Agent", "Grimmory/1.0 (Book and Comic Metadata Fetcher; +https://github.com/grimmory-tools/grimmory)")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                RanobedbBookResponse responseObj = objectMapper.readValue(response.body(), RanobedbBookResponse.class);
                RanobedbBookResponse.Book book = responseObj.getBook();
                if (book == null) {
                    return null;
                }

                RanobedbBookResponse.Release release = book.getReleases().stream()
                        .filter(Objects::nonNull)
                        .max(Comparator.comparing(r -> "en".equalsIgnoreCase(r.getLang())))
                        .orElse(null);

                String bookLang = release != null ? release.getLang() : book.getLang();

                RanobedbBookResponse.Publisher publisher = book.getPublishers().stream()
                        .filter(p -> bookLang != null && bookLang.equalsIgnoreCase(p.getLang()))
                        .filter(p -> RanobedbBookResponse.PublisherType.PUBLISHER.equals(p.getPublisherType()))
                        .findFirst()
                        .orElse(null);

                String publisherName = null;
                if (publisher != null) {
                    publisherName = getPreferredValue(publisher.getRomaji(), publisher.getName());
                }

                List<RanobedbBookResponse.SeriesBook> seriesBooks = book.getSeries() != null ? book.getSeries().getBooks() : List.of();
                int seriesIndex = IntStream.range(0, seriesBooks.size())
                        .filter(i -> seriesBooks.get(i).getId().equals(book.getId()))
                        .findFirst()
                        .orElse(-1);

                List<String> authors = book.getEditions().stream()
                        .flatMap(edition -> edition.getStaff().stream())
                        .filter(staff -> RanobedbBookResponse.RoleType.AUTHOR.equals(staff.getRoleType()))
                        .map(staff -> getPreferredValue(staff.getRomaji(), staff.getName()))
                        .toList();

                HashSet<String> genres = book.getSeries() != null ? book.getSeries().getTags().stream()
                        .filter(tag -> RanobedbBookResponse.TagType.GENRE.equals(tag.getTtype()))
                        .map(RanobedbBookResponse.Tag::getName)
                        .map(genre -> Pattern.compile("\\b(.)(.*?)\\b").matcher(genre).replaceAll(m -> m.group(1).toUpperCase() + m.group(2).toLowerCase()))
                        .collect(Collectors.toCollection(HashSet::new)) : new HashSet<>();

                String title;
                if (release != null) {
                    title = getPreferredValue(release.getRomaji(), release.getTitle());
                } else {
                    title = getPreferredValue(book.getRomaji(), book.getTitle());
                }

                String subtitle = null;
                if (book.getSeries() != null && book.getSeries().getTitle() != null && title.startsWith(book.getSeries().getTitle())) {
                    String remainingTitle = title.substring(book.getSeries().getTitle().length()).trim();
                    String[] titleParts = remainingTitle.split(":", 2);
                    if (titleParts.length == 2) {
                        title = title.substring(0, title.indexOf(titleParts[1]) - 1).trim();
                        subtitle = titleParts[1].trim();
                    }
                }

                String description;
                if (release != null && release.getDescription() != null && !release.getDescription().isBlank()) {
                    description = release.getDescription();
                } else {
                    description = "ja".equalsIgnoreCase(bookLang) ? book.getDescriptionJa() : book.getDescription();
                }

                return BookMetadata.builder()
                    .provider(MetadataProvider.Ranobedb)
                    .ranobedbId(String.valueOf(book.getId()))
                    .ranobedbRating(book.getRating() != null ? book.getRating().getScore() / 2.0 : null)
                    .title(title) 
                    .subtitle(subtitle)
                    .authors(authors)
                    .categories(genres)
                    .publisher(publisherName)
                    .thumbnailUrl(book.getImage() != null ? RANOBEDB_IMAGE_URL + book.getImage().getFilename() : null)
                    .description(description)
                    .language(LanguageNormalizer.normalize(bookLang))
                    .seriesName(book.getSeries() != null ? book.getSeries().getTitle() : null)
                    .seriesNumber(seriesIndex != -1 ? seriesIndex + 1.0f : null)
                    .seriesTotal(seriesBooks.isEmpty() ? null : seriesBooks.size())
                    .publishedDate(release != null ? parseDate(release.getReleaseDate()) : parseDate(book.getCReleaseDate()))
                    .isbn13(release != null ? release.getIsbn13() : null)
                    .build();
            } else {
                log.info("Ranobedb Get Book API returned status code {}", response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            log.error("Error fetching metadata from Ranobedb Search API", e);
        }
        
        return null;
    }

    private LocalDate parseDate(Long dateInt) {
        if (dateInt == null || dateInt == 0) {
            return null;
        }

        if (dateInt < 9999) {
            dateInt = (dateInt * 10000) + 101;
        } else if (dateInt < 999999) {
            dateInt = (dateInt * 100) + 1;
        }

        // Parse date from integer of the format (YYYYMMDD)
        int year = (int) (dateInt / 10000);
        int month = (int) ((dateInt / 100) % 100);
        int day = (int) (dateInt % 100);

        try {
            return LocalDate.of(year, month, day);
        } catch (DateTimeException ignored) {
            log.debug("Could not parse date: {}", dateInt);
            return null;
        }
    }
}
