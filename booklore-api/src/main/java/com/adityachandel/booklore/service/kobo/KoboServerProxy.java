package com.adityachandel.booklore.service.kobo;

import com.adityachandel.booklore.model.dto.BookloreSyncToken;
import com.adityachandel.booklore.model.dto.kobo.KoboHeaders;
import com.adityachandel.booklore.util.RequestUtils;
import com.adityachandel.booklore.util.kobo.BookloreSyncTokenGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class KoboServerProxy {

    private static final Pattern KOBO_API_PREFIX_PATTERN = Pattern.compile("^/api/kobo/[^/]+");
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMinutes(1)).build();
    private final ObjectMapper objectMapper;
    private final BookloreSyncTokenGenerator bookloreSyncTokenGenerator;

    private static final Set<String> HEADERS_OUT_INCLUDE = Set.of(
            HttpHeaders.AUTHORIZATION.toLowerCase(),
            HttpHeaders.USER_AGENT,
            HttpHeaders.ACCEPT,
            HttpHeaders.ACCEPT_LANGUAGE
    );

    private static final Set<String> HEADERS_OUT_EXCLUDE = Set.of(
            KoboHeaders.X_KOBO_SYNCTOKEN
    );

    private boolean isKoboHeader(String headerName) {
        return headerName.toLowerCase().startsWith("x-kobo-");
    }

    public ResponseEntity<JsonNode> proxyCurrentRequest(Object body, boolean includeSyncToken) {
        HttpServletRequest request = RequestUtils.getCurrentRequest();
        String path = KOBO_API_PREFIX_PATTERN.matcher(request.getRequestURI()).replaceFirst("");

        BookloreSyncToken syncToken = null;
        if (includeSyncToken) {
            syncToken = bookloreSyncTokenGenerator.fromRequestHeaders(request);
            if (syncToken == null || syncToken.getRawKoboSyncToken() == null || syncToken.getRawKoboSyncToken().isBlank()) {
                //throw new IllegalStateException("Request must include sync token, but none found");
            }
        }

        return executeProxyRequest(request, body, path, includeSyncToken, syncToken);
    }

    public ResponseEntity<Resource> proxyExternalUrl(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_JPEG);

            return new ResponseEntity<>(new ByteArrayResource(response.body()), headers, HttpStatus.valueOf(response.statusCode()));
        } catch (Exception e) {
            log.error("Failed to proxy external Kobo CDN URL", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch image", e);
        }
    }

    private ResponseEntity<JsonNode> executeProxyRequest(HttpServletRequest request, Object body, String path, boolean includeSyncToken, BookloreSyncToken syncToken) {
        try {
            String koboBaseUrl = "https://storeapi.kobo.com";

            String queryString = request.getQueryString();
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(koboBaseUrl)
                    .path(path);

            if (queryString != null && !queryString.isBlank()) {
                uriBuilder.query(queryString);
            }

            URI uri = uriBuilder.build(true).toUri();
            log.info("Kobo proxy URL: {}", uri);

            String bodyString = body != null ? objectMapper.writeValueAsString(body) : "{}";
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofMinutes(1))
                    .method(request.getMethod(), HttpRequest.BodyPublishers.ofString(bodyString))
                    .header(HttpHeaders.CONTENT_TYPE, "application/json");

            Collections.list(request.getHeaderNames()).forEach(headerName -> {
                if (!HEADERS_OUT_EXCLUDE.contains(headerName.toLowerCase()) &&
                        (HEADERS_OUT_INCLUDE.contains(headerName) || isKoboHeader(headerName))) {
                    Collections.list(request.getHeaders(headerName))
                            .forEach(value -> builder.header(headerName, value));
                }
            });

            if (includeSyncToken && syncToken != null && syncToken.getRawKoboSyncToken() != null && !syncToken.getRawKoboSyncToken().isBlank()) {
                builder.header(KoboHeaders.X_KOBO_SYNCTOKEN, syncToken.getRawKoboSyncToken());
            }

            HttpRequest httpRequest = builder.build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            JsonNode responseBody = response.body() != null && !response.body().isBlank()
                    ? objectMapper.readTree(response.body())
                    : null;

            HttpHeaders responseHeaders = new HttpHeaders();
            response.headers().map().forEach((key, values) -> {
                if (isKoboHeader(key)) {
                    responseHeaders.put(key, values);
                }
            });

            if (responseHeaders.containsKey(KoboHeaders.X_KOBO_SYNCTOKEN) && includeSyncToken && syncToken != null) {
                String koboToken = responseHeaders.getFirst(KoboHeaders.X_KOBO_SYNCTOKEN);
                if (koboToken != null) {
                    BookloreSyncToken updated = BookloreSyncToken.builder()
                            .ongoingSyncPointId(syncToken.getOngoingSyncPointId())
                            .lastSuccessfulSyncPointId(syncToken.getLastSuccessfulSyncPointId())
                            .rawKoboSyncToken(koboToken)
                            .build();
                    responseHeaders.set(KoboHeaders.X_KOBO_SYNCTOKEN, bookloreSyncTokenGenerator.toBase64(updated));
                }
            }

            log.info("Kobo proxy response status: {}", response.statusCode());

            return new ResponseEntity<>(responseBody, responseHeaders, HttpStatus.valueOf(response.statusCode()));

        } catch (Exception e) {
            log.error("Failed to proxy request to Kobo", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to proxy request to Kobo", e);
        }
    }
}