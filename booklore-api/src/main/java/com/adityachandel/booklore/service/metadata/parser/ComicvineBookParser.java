package com.adityachandel.booklore.service.metadata.parser;

import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.request.FetchMetadataRequest;
import com.adityachandel.booklore.model.dto.response.comicvineapi.Comic;
import com.adityachandel.booklore.model.dto.response.comicvineapi.ComicvineApiResponse;
import com.adityachandel.booklore.model.dto.response.comicvineapi.ComicvineIssueResponse;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComicvineBookParser implements BookParser {

    private static final String COMICVINE_URL = "https://comicvine.gamespot.com/api/";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("\\d+");
    private static final Pattern SERIES_ISSUE_PATTERN = Pattern.compile("^(.+?)\\s+#?(\\d+(?:\\.\\d+)?)(?:\\s|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIGITAL_PATTERN = Pattern.compile("\\(digital\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PARENTHETICAL_PATTERN = Pattern.compile("\\([^)]*\\)");
    private static final Pattern BRACKETED_PATTERN = Pattern.compile("\\[[^\\]]*\\]");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern SPECIAL_ISSUE_PATTERN = Pattern.compile("(annual|special|one-?shot)\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\(?(\\d{4})\\)?");
    private static final long MIN_REQUEST_INTERVAL_MS = 1000;

    private final ObjectMapper objectMapper;
    private final AppSettingService appSettingService;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private final AtomicBoolean rateLimited = new AtomicBoolean(false);
    private final AtomicLong rateLimitResetTime = new AtomicLong(0);
    private final AtomicLong lastRequestTime = new AtomicLong(0);
    private final Map<String, CachedVolumes> volumeCache = new java.util.concurrent.ConcurrentHashMap<>();

    private static class CachedVolumes {
        final List<Comic> volumes;
        final long timestamp;

        CachedVolumes(List<Comic> volumes) {
            this.volumes = volumes;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > 300_000;
        }
    }

    @Override
    public List<BookMetadata> fetchMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        String searchTerm = getSearchTerm(book, fetchMetadataRequest);
        if (searchTerm == null) {
            log.warn("No valid search term provided for metadata fetch.");
            return Collections.emptyList();
        }
        return getMetadataListByTerm(searchTerm);
    }

    @Override
    public BookMetadata fetchTopMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        List<BookMetadata> metadataList = fetchMetadata(book, fetchMetadataRequest);
        return metadataList.isEmpty() ? null : metadataList.getFirst();
    }

    public List<BookMetadata> getMetadataListByTerm(String term) {
        SeriesAndIssue seriesAndIssue = extractSeriesAndIssue(term);

        // Try precise search if issue number present
        if (seriesAndIssue.issue() != null) {
            log.info("Attempting structured search for Series: '{}', Issue: '{}', Year: '{}', Type: '{}'", 
                    seriesAndIssue.series(), seriesAndIssue.issue(), seriesAndIssue.year(), seriesAndIssue.issueType());
            List<BookMetadata> preciseResults = searchVolumesAndIssues(seriesAndIssue.series(), seriesAndIssue.issue(), seriesAndIssue.year());
            if (!preciseResults.isEmpty()) {
                return preciseResults;
            }
            log.info("Structured search yielded no results, falling back to general search.");
        }

        // Fallback to general search
        List<BookMetadata> results = searchGeneral(term);
        if (!results.isEmpty()) {
            return results;
        }

        // If general search failed and we had a structured match, try searching with the cleaned/modified term
        // This helps when the issue number #1 was incorrectly identified or confuses the search engine
        // e.g. "The Witcher #1 - Volume 1: House of Glass" -> "The Witcher - Volume 1: House of Glass"
        if (seriesAndIssue.issue() != null && seriesAndIssue.remainder() != null && !seriesAndIssue.remainder().isBlank()) {
            String modifiedTerm = seriesAndIssue.series() + " " + seriesAndIssue.remainder();
            if (seriesAndIssue.year() != null) {
                modifiedTerm += " " + seriesAndIssue.year();
            }
            log.info("General search failed, trying modified term: '{}'", modifiedTerm);
            return searchGeneral(modifiedTerm);
        }

        return Collections.emptyList();
    }

    private List<BookMetadata> searchVolumesAndIssues(String seriesName, String issueNumber, Integer extractedYear) {
        String normalizedIssue = normalizeIssueNumber(issueNumber);
        if (seriesName.endsWith(" " + issueNumber) || seriesName.endsWith(" " + normalizedIssue)) {
            // Strip issue number from series name if it leaked in
            seriesName = seriesName.replaceAll("\\s+" + Pattern.quote(issueNumber) + "$", "")
                                   .replaceAll("\\s+" + Pattern.quote(normalizedIssue) + "$", "")
                                   .trim();
            log.warn("Issue number found in series name, corrected to: '{}'", seriesName);
        }

        final String finalSeriesName = seriesName;
        log.debug("searchVolumesAndIssues: seriesName='{}', issueNumber='{}', year='{}'", finalSeriesName, issueNumber, extractedYear);
        List<Comic> volumes = searchVolumes(finalSeriesName);
        if (volumes.isEmpty()) {
            log.debug("No volumes found for series '{}'", finalSeriesName);
            return Collections.emptyList();
        }

        // Sort volumes by relevance
        volumes.sort((v1, v2) -> {
            int score1 = 0;
            int score2 = 0;

            if (extractedYear != null) {
                if (matchesYear(v1, extractedYear)) score1 += 100;
                if (matchesYear(v2, extractedYear)) score2 += 100;
            }

            if (v1.getName() != null && v1.getName().equalsIgnoreCase(finalSeriesName)) score1 += 50;
            if (v2.getName() != null && v2.getName().equalsIgnoreCase(finalSeriesName)) score2 += 50;

            try {
                int requestedIssue = (int) Math.floor(Double.parseDouble(normalizedIssue));
                if (v1.getCountOfIssues() != null && v1.getCountOfIssues() >= requestedIssue) score1 += 20;
                if (v2.getCountOfIssues() != null && v2.getCountOfIssues() >= requestedIssue) score2 += 20;
            } catch (NumberFormatException ignored) {}

            Set<String> majorPublishers = Set.of("Marvel", "DC Comics", "Image", "Dark Horse Comics", "IDW Publishing");
            if (v1.getPublisher() != null && majorPublishers.stream().anyMatch(p -> v1.getPublisher().getName().contains(p))) score1 += 10;
            if (v2.getPublisher() != null && majorPublishers.stream().anyMatch(p -> v2.getPublisher().getName().contains(p))) score2 += 10;

            return Integer.compare(score2, score1);
        });

        List<BookMetadata> results = new ArrayList<>();
        // Limit to top 3 volumes to minimize API calls
        int limit = Math.min(volumes.size(), 3);

        for (int i = 0; i < limit; i++) {
            Comic volume = volumes.get(i);
            log.debug("Checking volume: id='{}', name='{}', start_year='{}'", volume.getId(), volume.getName(), volume.getStartYear());
            
            List<BookMetadata> issues = searchIssuesInVolume(volume.getId(), issueNumber);
            if (!issues.isEmpty()) {
                results.addAll(issues);
                
                // If we found a result in a high-score volume (e.g. year matched), stop.
                if (extractedYear != null && matchesYear(volume, extractedYear)) {
                    log.info("Found match in year-aligned volume '{}' ({}) . Stopping further volume searches.", volume.getName(), volume.getStartYear());
                    break;
                }
            }
        }
        return results;
    }

    private boolean matchesYear(Comic volume, int targetYear) {
        if (volume.getStartYear() == null) return false;
        try {
            int volYear = Integer.parseInt(volume.getStartYear());
            return Math.abs(volYear - targetYear) <= 1;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private List<Comic> searchVolumes(String seriesName) {
        CachedVolumes cached = volumeCache.get(seriesName.toLowerCase());
        if (cached != null && !cached.isExpired()) {
            log.debug("Using cached volume search for '{}'", seriesName);
            return cached.volumes;
        }

        String apiToken = getApiToken();
        if (apiToken == null) return Collections.emptyList();

        URI uri = UriComponentsBuilder.fromUriString(COMICVINE_URL)
                .path("/search/")
                .queryParam("api_key", apiToken)
                .queryParam("format", "json")
                .queryParam("resources", "volume")
                .queryParam("query", seriesName)
                .queryParam("limit", 10)
                .queryParam("field_list", "id,name,publisher,start_year,count_of_issues")
                .build()
                .toUri();

        ComicvineApiResponse response = sendRequest(uri, ComicvineApiResponse.class);
        List<Comic> volumes = response != null && response.getResults() != null ? response.getResults() : Collections.emptyList();

        if (!volumes.isEmpty()) {
            volumeCache.put(seriesName.toLowerCase(), new CachedVolumes(volumes));
        } else if (seriesName.contains(" - ")) {
            String alternativeName = seriesName.replace(" - ", ": ");
            log.debug("No results for '{}', trying alternative name '{}'", seriesName, alternativeName);
            return searchVolumes(alternativeName);
        }

        return volumes;
    }

    private List<BookMetadata> searchIssuesInVolume(int volumeId, String issueNumber) {
        String apiToken = getApiToken();
        if (apiToken == null) return Collections.emptyList();

        String normalizedIssue = normalizeIssueNumber(issueNumber);
        log.debug("searchIssuesInVolume: volumeId='{}', original='{}', normalized='{}'", 
                  volumeId, issueNumber, normalizedIssue);

        String fieldsList = String.join(",", "api_detail_url", "cover_date", "description", "id", "image", "issue_number", "name", "volume", "site_detail_url");

        URI uri = UriComponentsBuilder.fromUriString(COMICVINE_URL)
                .path("/issues/")
                .queryParam("api_key", apiToken)
                .queryParam("format", "json")
                .queryParam("filter", "volume:" + volumeId + ",issue_number:" + normalizedIssue)
                .queryParam("field_list", fieldsList)
                .queryParam("limit", 5)
                .build()
                .toUri();

        ComicvineApiResponse response = sendRequest(uri, ComicvineApiResponse.class);
        if (response != null && response.getResults() != null && !response.getResults().isEmpty()) {
            Comic firstIssue = response.getResults().getFirst();
            String returnedIssue = normalizeIssueNumber(firstIssue.getIssueNumber());
            
            if (!normalizedIssue.equals(returnedIssue)) {
                log.warn("Issue number mismatch! Requested '{}', got '{}' from volume {}", 
                         normalizedIssue, returnedIssue, volumeId);
                return Collections.emptyList();
            }

            BookMetadata detailed = fetchIssueDetails(firstIssue.getId());

            return Collections.singletonList(Objects.requireNonNullElseGet(detailed, () -> convertToBookMetadata(firstIssue)));
        }
        return Collections.emptyList();
    }

    private BookMetadata fetchIssueDetails(int issueId) {
        String apiToken = getApiToken();
        if (apiToken == null) return null;

        String fieldsList = String.join(",", "api_detail_url", "cover_date", "description", "id", "image", "issue_number", "name", "person_credits", "volume", "site_detail_url");

        URI uri = UriComponentsBuilder.fromUriString(COMICVINE_URL)
                .path("/issue/4000-" + issueId + "/")
                .queryParam("api_key", apiToken)
                .queryParam("format", "json")
                .queryParam("field_list", fieldsList)
                .build()
                .toUri();

        ComicvineIssueResponse response = sendRequest(uri, ComicvineIssueResponse.class);
        if (response != null && response.getResults() != null) {
            return convertToBookMetadata(response.getResults());
        }
        return null;
    }

    private List<BookMetadata> searchGeneral(String term) {
        String apiToken = getApiToken();
        if (apiToken == null) return Collections.emptyList();

        String fieldsList = String.join(",", "api_detail_url", "cover_date", "description", "id", "image", "issue_number", "name", "publisher", "volume", "site_detail_url", "resource_type", "start_year", "count_of_issues");
        String resources = "volume,issue";

        URI uri = UriComponentsBuilder.fromUriString(COMICVINE_URL)
                .path("/search/")
                .queryParam("api_key", apiToken)
                .queryParam("format", "json")
                .queryParam("resources", resources)
                .queryParam("query", term)
                .queryParam("limit", 10)
                .queryParam("field_list", fieldsList)
                .build()
                .toUri();

        ComicvineApiResponse response = sendRequest(uri, ComicvineApiResponse.class);
        if (response != null && response.getResults() != null) {
            return response.getResults().stream()
                    .map(this::convertToBookMetadata)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private <T> T sendRequest(URI uri, Class<T> responseType) {
        return sendRequestWithRetry(uri, responseType, 2);
    }

    private <T> T sendRequestWithRetry(URI uri, Class<T> responseType, int retriesLeft) {
        if (rateLimited.get()) {
            long currentTime = System.currentTimeMillis();
            if (currentTime < rateLimitResetTime.get()) {
                log.warn("ComicVine API is currently rate limited. Skipping request. Rate limit resets at: {}",
                        Instant.ofEpochMilli(rateLimitResetTime.get()));
                return null;
            } else {
                rateLimited.compareAndSet(true, false);
            }
        }

        long now = System.currentTimeMillis();
        long timeSinceLastRequest = now - lastRequestTime.get();
        if (timeSinceLastRequest < MIN_REQUEST_INTERVAL_MS) {
            try {
                Thread.sleep(MIN_REQUEST_INTERVAL_MS - timeSinceLastRequest);
            } catch (InterruptedException ignored) {}
        }
        lastRequestTime.set(System.currentTimeMillis());

        try {
            log.debug("Sending request to ComicVine: {}", uri);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("User-Agent", "BookLore/1.0 (Book and Comic Metadata Fetcher; +https://github.com/booklore-app/booklore)")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.debug("Received response from ComicVine: status='{}', body_length='{}'", 
                    response.statusCode(), response.body() != null ? response.body().length() : 0);

            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), responseType);
            } else if (response.statusCode() == 420 || response.statusCode() == 429) {
                handleRateLimit(response);
                return null;
            } else if (response.statusCode() >= 500 && retriesLeft > 0) {
                log.warn("ComicVine API returned status {}. Retrying... ({} retries left)", 
                         response.statusCode(), retriesLeft);
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {}
                return sendRequestWithRetry(uri, responseType, retriesLeft - 1);
            } else {
                log.error("Comicvine API returned status code {}. Body: {}", response.statusCode(), response.body());
            }
        } catch (IOException e) {
            if (retriesLeft > 0) {
                log.warn("IOException during ComicVine request. Retrying... ({} retries left)", retriesLeft, e);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {}
                return sendRequestWithRetry(uri, responseType, retriesLeft - 1);
            } else {
                log.error("Error fetching data from Comicvine API after retries", e);
            }
        } catch (InterruptedException e) {
            log.error("Request interrupted", e);
            Thread.currentThread().interrupt();
        }
        return null;
    }

    private void handleRateLimit(HttpResponse<String> response) {
        log.error("ComicVine API rate limit exceeded (Error {}). Setting rate limit flag.", response.statusCode());

        long resetDelayMs = 3600000;
        List<String> retryAfterHeaders = response.headers().allValues("Retry-After");
        if (!retryAfterHeaders.isEmpty()) {
            try {
                String retryAfter = retryAfterHeaders.getFirst();
                if (DIGIT_PATTERN.matcher(retryAfter).matches()) {
                    resetDelayMs = Long.parseLong(retryAfter) * 1000;
                } else {
                    Instant instant = Instant.parse(retryAfter);
                    resetDelayMs = instant.toEpochMilli() - System.currentTimeMillis();
                    if (resetDelayMs <= 0) {
                        resetDelayMs = 3600000;
                    }
                }
            } catch (Exception e) {
                log.warn("Could not parse Retry-After header '{}', using default 1 hour delay", retryAfterHeaders.getFirst());
            }
        }

        if (rateLimited.compareAndSet(false, true)) {
            rateLimitResetTime.set(System.currentTimeMillis() + resetDelayMs);
            log.info("Rate limit will reset at: {}", Instant.ofEpochMilli(rateLimitResetTime.get()));
        }
    }

    private BookMetadata convertToBookMetadata(Comic comic) {
        // Check if this is a Volume
        if ("volume".equalsIgnoreCase(comic.getResourceType())) {
            return BookMetadata.builder()
                    .provider(MetadataProvider.Comicvine)
                    .comicvineId(String.valueOf(comic.getId()))
                    .title(comic.getName()) // Use volume name as title
                    .seriesName(comic.getName())
                    .seriesTotal(comic.getCountOfIssues())
                    .publishedDate(safeParseDate(comic.getStartYear() + "-01-01")) // Rough estimate if only year
                    .description(comic.getDescription())
                    .publisher(comic.getPublisher() != null ? comic.getPublisher().getName() : null)
                    .thumbnailUrl(comic.getImage() != null ? comic.getImage().getMediumUrl() : null)
                    .externalUrl(comic.getSiteDetailUrl())
                    .build();
        }

        Set<String> authors = extractAuthors(comic.getPersonCredits());
        String formattedTitle = formatTitle(comic.getVolume() != null ? comic.getVolume().getName() : null, comic.getIssueNumber(), comic.getName());

        BookMetadata metadata = BookMetadata.builder()
                .provider(MetadataProvider.Comicvine)
                .comicvineId(String.valueOf(comic.getId()))
                .title(formattedTitle)
                .authors(authors)
                .thumbnailUrl(comic.getImage() != null ? comic.getImage().getMediumUrl() : null)
                .description(comic.getDescription())
                .seriesName(comic.getVolume() != null ? comic.getVolume().getName() : null)
                .seriesNumber(safeParseFloat(comic.getIssueNumber()))
                .publishedDate(safeParseDate(comic.getCoverDate()))
                .externalUrl(comic.getSiteDetailUrl())
                .build();
        
        if (metadata.getSeriesName() == null || metadata.getSeriesNumber() == null) {
            log.warn("Incomplete metadata for issue {}: missing series name or number", metadata.getComicvineId());
        }
        
        if (metadata.getAuthors().isEmpty()) {
            log.debug("No authors found for issue {} ({})", metadata.getComicvineId(), metadata.getTitle());
        }
        
        return metadata;
    }

    private BookMetadata convertToBookMetadata(ComicvineIssueResponse.IssueResults issue) {
        // Extract ID from api_detail_url: "https://comicvine.gamespot.com/api/issue/4000-12345/"
        String comicvineId = null;
        if (issue.getApiDetailUrl() != null) {
            Matcher matcher = Pattern.compile("/(\\d+)/?$").matcher(issue.getApiDetailUrl());
            if (matcher.find()) {
                comicvineId = matcher.group(1);
            }
        }

        Set<String> authors = extractAuthors(issue.getPersonCredits());
        String formattedTitle = formatTitle(issue.getVolume() != null ? issue.getVolume().getName() : null, issue.getIssueNumber(), issue.getName());

        BookMetadata metadata = BookMetadata.builder()
                .provider(MetadataProvider.Comicvine)
                .comicvineId(comicvineId)
                .title(formattedTitle)
                .authors(authors)
                .thumbnailUrl(issue.getImage() != null ? issue.getImage().getMediumUrl() : null)
                .description(issue.getDescription())
                .seriesName(issue.getVolume() != null ? issue.getVolume().getName() : null)
                .seriesNumber(safeParseFloat(issue.getIssueNumber()))
                .publishedDate(safeParseDate(issue.getCoverDate()))
                .externalUrl(issue.getSiteDetailUrl())
                .build();
        
        if (metadata.getSeriesName() == null || metadata.getSeriesNumber() == null) {
            log.warn("Incomplete metadata for issue {}: missing series name or number", comicvineId);
        }
        
        if (metadata.getAuthors().isEmpty()) {
            log.debug("No authors found for issue {} ({})", comicvineId, metadata.getTitle());
        }
        
        return metadata;
    }
    
    private String formatTitle(String seriesName, String issueNumber, String issueName) {
        if (seriesName == null) return issueName;
        
        String normalizedIssue = normalizeIssueNumber(issueNumber);
        String title = seriesName + " #" + (normalizedIssue != null ? normalizedIssue : "");
        
        // Append issue title if it exists and isn't just "Issue #X" or identical to series
        if (issueName != null && !issueName.isBlank()) {
            String lowerName = issueName.toLowerCase();
            boolean isGeneric = lowerName.matches("issue\\s*#?\\d+") || lowerName.equals(seriesName.toLowerCase());
            if (!isGeneric) {
                title += " - " + issueName;
            }
        }
        return title;
    }
    
    private Set<String> extractAuthors(List<Comic.PersonCredit> personCredits) {
        if (personCredits == null || personCredits.isEmpty()) {
            return Collections.emptySet();
        }
        
        // Primary writer roles
        Set<String> writerRoles = Set.of("writer", "script", "story", "plotter", "plot");
        
        // Collect writers
        Set<String> authors = personCredits.stream()
                .filter(pc -> {
                    if (pc.getRole() == null) return false;
                    String role = pc.getRole().toLowerCase();
                    return writerRoles.stream().anyMatch(role::contains);
                })
                .map(Comic.PersonCredit::getName)
                .filter(name -> name != null && !name.isEmpty())
                .collect(Collectors.toSet());
        
        // Fallback: If no writers found, check for "creator" role
        if (authors.isEmpty()) {
            // DEBUG: Log all roles if we missed them
            List<String> allRoles = personCredits.stream()
                    .map(pc -> pc.getName() + " (" + pc.getRole() + ")")
                    .toList();
            log.debug("No writers found. Available roles: {}", allRoles);

            authors = personCredits.stream()
                    .filter(pc -> pc.getRole() != null && pc.getRole().toLowerCase().contains("creator"))
                    .map(Comic.PersonCredit::getName)
                    .filter(name -> name != null && !name.isEmpty())
                    .collect(Collectors.toSet());
        }
        
        return authors;
    }

    private String getSearchTerm(Book book, FetchMetadataRequest request) {
        if (request.getTitle() != null && !request.getTitle().isEmpty()) {
            return request.getTitle();
        } else if (book.getFileName() != null && !book.getFileName().isEmpty()) {
            // Use filename but preserve parentheses for year extraction
            String name = book.getFileName();
            // Remove extension only
            int dotIndex = name.lastIndexOf('.');
            if (dotIndex > 0) {
                name = name.substring(0, dotIndex);
            }
            return name.trim();
        }
        return null;
    }

    private SeriesAndIssue extractSeriesAndIssue(String term) {
        // 1. Extract Year
        Integer year = null;
        Matcher yearMatcher = YEAR_PATTERN.matcher(term);
        String yearString = null;
        if (yearMatcher.find()) {
            try {
                int y = Integer.parseInt(yearMatcher.group(1));
                if (y > 1900 && y <= LocalDate.now().getYear() + 1) {
                    year = y;
                    yearString = yearMatcher.group(0);
                }
            } catch (NumberFormatException ignored) {}
        }

        // 2. Remove ONLY the found year and noise from the term
        String cleaned = term;
        if (yearString != null) {
            cleaned = cleaned.replace(yearString, "");
        }
        cleaned = DIGITAL_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = PARENTHETICAL_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = BRACKETED_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = cleaned.trim();
        cleaned = WHITESPACE_PATTERN.matcher(cleaned).replaceAll(" ");

        log.debug("Cleaned filename: '{}'", cleaned);

        // 3. Check for special issue keywords BEFORE extracting issue number
        String lowerCleaned = cleaned.toLowerCase();
        if (lowerCleaned.contains("annual") || 
            lowerCleaned.contains("special") || 
            lowerCleaned.contains("one-shot") ||
            lowerCleaned.contains("one shot")) {
            
            // Try to extract number from "Annual 2" or "Special 3"
            Matcher specialMatcher = SPECIAL_ISSUE_PATTERN.matcher(cleaned);
            if (specialMatcher.find()) {
                String type = specialMatcher.group(1);
                String num = specialMatcher.group(2);
                // Return series name before the special keyword
                String series = cleaned.substring(0, specialMatcher.start()).trim();
                return new SeriesAndIssue(series, num, year, type, null);
            } else {
                // "Batman Annual" without number - search for all annuals
                return new SeriesAndIssue(cleaned, null, year, "annual", null);
            }
        }

        // 4. Standard issue extraction with IMPROVED PATTERN
        Matcher matcher = SERIES_ISSUE_PATTERN.matcher(cleaned);
        if (matcher.find()) {
            String series = matcher.group(1).trim();
            String issueNum = matcher.group(2);
            
            if (series.endsWith("#")) {
                series = series.substring(0, series.length() - 1).trim();
            }

            String remainder = cleaned.substring(matcher.end()).trim();

            log.debug("Extracted - Series: '{}', Issue: '{}', Remainder: '{}'", series, issueNum, remainder);
            return new SeriesAndIssue(series, issueNum, year, null, remainder);
        }
        
        log.debug("No issue number found in: '{}'", cleaned);
        return new SeriesAndIssue(cleaned, null, year, null, null);
    }
    
    private String normalizeIssueNumber(String issueNumber) {
        if (issueNumber == null || issueNumber.isEmpty()) {
            return issueNumber;
        }
        
        issueNumber = issueNumber.trim();
        
        if (issueNumber.contains("/")) {
            try {
                String[] parts = issueNumber.split("/");
                if (parts.length == 2) {
                    double numerator = Double.parseDouble(parts[0]);
                    double denominator = Double.parseDouble(parts[1]);
                    return String.valueOf(numerator / denominator);
                }
            } catch (NumberFormatException e) {
                log.warn("Could not parse fractional issue number '{}'", issueNumber);
                return issueNumber;
            }
        }
        
        try {
            if (issueNumber.contains(".")) {
                double d = Double.parseDouble(issueNumber);
                if (d == Math.floor(d)) {
                    return String.valueOf((int) d);
                } else {
                    return new java.text.DecimalFormat("0.#####").format(d);
                }
            } else {
                return String.valueOf(Integer.parseInt(issueNumber));
            }
        } catch (NumberFormatException e) {
            log.debug("Non-numeric issue number '{}', using as-is", issueNumber);
            return issueNumber;
        }
    }

    private static LocalDate safeParseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return null;
        try {
            return LocalDate.parse(dateStr, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            log.warn("Invalid date '{}'", dateStr);
            return null;
        }
    }

    private static Float safeParseFloat(String numStr) {
        if (numStr == null || numStr.isEmpty()) return null;
        try {
            return Float.valueOf(numStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String getApiToken() {
        String apiToken = appSettingService.getAppSettings().getMetadataProviderSettings().getComicvine().getApiKey();
        if (apiToken == null || apiToken.isEmpty()) {
            log.warn("Comicvine API token not set");
            return null;
        }
        return apiToken;
    }

    private record SeriesAndIssue(String series, String issue, Integer year, String issueType, String remainder) {}
}