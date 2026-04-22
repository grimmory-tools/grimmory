package org.booklore.service.metadata.parser;

import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.AuthorSearchResult;
import org.booklore.model.dto.response.audnexus.AudnexusAuthorResponse;
import org.booklore.model.enums.AuthorMetadataSource;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class AudnexusAuthorParser implements AuthorParser {

    private static final String BASE_URL = "https://api.audnex.us";
    private static final long MIN_REQUEST_INTERVAL_MS = 150;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AtomicLong lastRequestTime = new AtomicLong(0);

    public AudnexusAuthorParser(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<AuthorSearchResult> searchAuthors(String name, String region) {
        try {
            waitForRateLimit();

            URI uri = UriComponentsBuilder.fromUriString(BASE_URL + "/authors")
                    .queryParam("name", name)
                    .queryParam("region", region)
                    .build()
                    .toUri();

            log.info("Audnexus author search URL: {}", uri);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                AudnexusAuthorResponse[] authors = objectMapper.readValue(response.body(), AudnexusAuthorResponse[].class);
                LinkedHashMap<String, AudnexusAuthorResponse> uniqueByAsin = new LinkedHashMap<>();
                for (AudnexusAuthorResponse author : authors) {
                    uniqueByAsin.putIfAbsent(author.getAsin(), author);
                }
                List<AuthorSearchResult> results = new ArrayList<>();
                int limit = 10;
                for (AudnexusAuthorResponse author : uniqueByAsin.values()) {
                    if (results.size() >= limit) break;
                    AuthorSearchResult enriched = getAuthorByAsin(author.getAsin(), region);
                    results.add(enriched != null ? enriched : toSearchResult(author));
                }
                return results;
            }

            log.warn("Audnexus author search returned status {}", response.statusCode());
            return Collections.emptyList();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Audnexus author search interrupted", e);
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Audnexus author search failed for name: {}", name, e);
            return Collections.emptyList();
        }
    }

    @Override
    public AuthorSearchResult quickSearch(String name, String region) {
        try {
            waitForRateLimit();

            URI uri = UriComponentsBuilder.fromUriString(BASE_URL + "/authors")
                    .queryParam("name", name)
                    .queryParam("region", region)
                    .build()
                    .toUri();

            log.info("Audnexus author quick search URL: {}", uri);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                AudnexusAuthorResponse[] authors = objectMapper.readValue(response.body(), AudnexusAuthorResponse[].class);
                if (authors.length == 0) return null;
                return getAuthorByAsin(authors[0].getAsin(), region);
            }

            log.warn("Audnexus author quick search returned status {}", response.statusCode());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.error("Audnexus author quick search failed for name: {}", name, e);
            return null;
        }
    }

    @Override
    public AuthorSearchResult getAuthorByAsin(String asin, String region) {
        try {
            waitForRateLimit();

            URI uri = UriComponentsBuilder.fromUriString(BASE_URL + "/authors/" + asin)
                    .queryParam("region", region)
                    .build()
                    .toUri();

            log.info("Audnexus author detail URL: {}", uri);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                AudnexusAuthorResponse author = objectMapper.readValue(response.body(), AudnexusAuthorResponse.class);
                return toSearchResult(author);
            }

            log.warn("Audnexus author detail returned status {}", response.statusCode());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Audnexus author detail interrupted", e);
            return null;
        } catch (Exception e) {
            log.error("Audnexus author detail failed for ASIN: {}", asin, e);
            return null;
        }
    }

    private AuthorSearchResult toSearchResult(AudnexusAuthorResponse response) {
        return AuthorSearchResult.builder()
                .source(AuthorMetadataSource.AUDNEXUS)
                .asin(response.getAsin())
                .name(response.getName())
                .description(response.getDescription())
                .imageUrl(response.getImageUrl())
                .build();
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
