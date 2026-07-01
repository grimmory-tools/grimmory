package org.booklore.service.metadata.parser.hardcover;

import lombok.extern.slf4j.Slf4j;
import org.booklore.service.appsettings.AppSettingService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class HardcoverBookSearchService {

    public static final int DEFAULT_PER_PAGE = 10;
    private static final long INITIAL_DELAY_MS = 1000;
    private static final long MAX_DELAY_MS = 15000;

    private final RestClient restClient;
    private final AppSettingService appSettingService;
    private final AtomicLong requestDelayMs = new AtomicLong(INITIAL_DELAY_MS);
    private final AtomicLong lastRequestTime = new AtomicLong(0);
    private final AtomicLong successCount = new AtomicLong(0);

    public HardcoverBookSearchService(AppSettingService appSettingService) {
        this.appSettingService = appSettingService;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.hardcover.app/v1/graphql")
                .build();
    }

    public List<GraphQLResponse.BookWithEditions> searchBookByIsbn(List<String> isbn) {
        return searchBookById(isbn, List.of(0));
    }

    public List<GraphQLResponse.BookWithEditions> searchBookByHcid(List<Integer> hcid) {
        return searchBookById(List.of("0"), hcid);
    }

    public List<GraphQLResponse.BookWithEditions> searchBookById(List<String> isbn, List<Integer> hcid) {
        String apiToken = getApiToken();
        if (apiToken == null) {
            return Collections.emptyList();
        }

        GraphQLRequest body = new GraphQLRequest();
        body.setQuery("""
                query BookSearchByIsbn($isbn: [String!]!, $hcid: [Int!]!) {
                    books(
                        where: {editions: {_or: [{isbn_13: {_in: $isbn}}, {isbn_10: {_in: $isbn}}, {book_id: {_in: $hcid}} ]}}
                    ) {
                        id
                        slug
                        title
                        subtitle
                        description
                        cached_contributors
                        featured_book_series {
                          series {
                            name
                            books_count
                            primary_books_count
                          }
                          position
                        }
                        rating
                        ratings_count
                        reviews_count
                        pages
                        release_date
                        release_year
                        image {
                          url
                        }
                        cached_tags
                        editions(where: {_or: [{isbn_13: {_in: $isbn}}, {isbn_10: {_in: $isbn}}, {book_id: {_in: $hcid}}]}, order_by: [{score: desc}]) {
                          id
                          title
                          subtitle
                          cached_contributors
                          pages
                          release_date
                          release_year
                          image {
                            url
                          }
                          publisher {
                            name
                          }
                          isbn_10
                          isbn_13
                          language {
                            code2
                          }
                          reading_format_id
                          score
                        }
                      }
                    }""");
        body.setVariables(Map.of("isbn", isbn, "hcid", hcid));

        GraphQLResponse response = executeRequest(body, GraphQLResponse.class, apiToken);
        if (response == null || response.getData() == null ||
                response.getData().getBooks() == null ||
                response.getData().getBooks().isEmpty()) {
            return Collections.emptyList();
        }

        return response.getData().getBooks();
    }

    public List<GraphQLResponse.Hit> searchBooks(String title) {
        return searchBooks(title, DEFAULT_PER_PAGE);
    }

    public List<GraphQLResponse.Hit> searchBooks(String title, int limit) {
        return searchBooks(title, "alternative_titles: " + title, "10,5,3", "title, alternative_titles, description", limit);
    }

    public List<GraphQLResponse.Hit> searchBooks(String title, String author) {
        return searchBooks(title, author, DEFAULT_PER_PAGE);
    }

    public List<GraphQLResponse.Hit> searchBooks(String title, String author, int limit) {
        return searchBooks(title + " "  + author, "", "5,3,10,5", "title, alternative_titles, author_names, description", limit);
    }

    public List<GraphQLResponse.Hit> searchBooks(String query, String filterBy, String weights, String fields, int perPage) {
        String apiToken = getApiToken();
        if (apiToken == null) {
            return Collections.emptyList();
        }

        int sanitizedPerPage = Math.clamp(perPage, 1, 100);

        GraphQLRequest body = new GraphQLRequest();
        body.setQuery("""
                query BookSearch($q: String!, $limit: Int, $filterBy: String, $weights: String, $fields: String) {
                    search(query: $q,
                            filter_by: $filterBy,
                            weights: $weights,
                            fields: $fields,
                            sort: "users_count:desc",
                            typos: "2",
                            query_type: "Book",
                            per_page: $limit,
                            page: 1)
                {results }}""");
        body.setVariables(Map.of("q", query, "filterBy", filterBy, "weights", weights, "fields", fields, "limit", sanitizedPerPage));

        GraphQLResponse response = executeRequest(body, GraphQLResponse.class, apiToken);
        if (response == null || response.getData() == null ||
                response.getData().getSearch() == null ||
                response.getData().getSearch().getResults() == null) {
            return Collections.emptyList();
        }

        List<GraphQLResponse.Hit> hits = response.getData().getSearch().getResults().getHits();
        return hits != null ? hits : Collections.emptyList();
    }

    private <T> T executeRequest(GraphQLRequest body, Class<T> responseType, String apiToken) {
        enforceRateLimit();

        try {
            T response = restClient.post()
                    .uri("")
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                    .body(body)
                    .retrieve()
                    .body(responseType);

            successCount.incrementAndGet();
            if (successCount.get() % 5 == 0) {
                reduceDelay();
            }
            return response;

        } catch (RestClientResponseException e) {
            successCount.set(0);
            if (e.getStatusCode().value() == 429 || e.getResponseBodyAsString().contains("Throttled")) {
                increaseDelay();
                log.warn("Hardcover API throttled, increased delay to {}ms", requestDelayMs.get());
            } else {
                log.error("Hardcover API error: {}", e.getMessage());
            }
            return null;
        } catch (Exception e) {
            log.error("Hardcover API request failed: {}", e.getMessage());
            return null;
        } finally {
            lastRequestTime.set(System.currentTimeMillis());
        }
    }

    private void enforceRateLimit() {
        long now = System.currentTimeMillis();
        long last = lastRequestTime.get();
        long delay = requestDelayMs.get();
        long elapsed = now - last;

        if (elapsed < delay) {
            try {
                Thread.sleep(delay - elapsed);
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void increaseDelay() {
        requestDelayMs.updateAndGet(current -> Math.min(current * 2, MAX_DELAY_MS));
    }

    private void reduceDelay() {
        requestDelayMs.updateAndGet(current -> Math.max(current - 100, INITIAL_DELAY_MS));
    }

    private String getApiToken() {
        String apiToken = appSettingService.getAppSettings().getMetadataProviderSettings().getHardcover().getApiKey();
        if (apiToken == null || apiToken.isEmpty()) {
            log.warn("Hardcover API token not set");
            return null;
        }
        return apiToken;
    }
}
