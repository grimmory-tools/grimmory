package org.booklore.service.kobo;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.BookloreSyncToken;
import org.booklore.model.dto.kobo.KoboHeaders;
import org.booklore.util.RequestUtils;
import org.booklore.util.kobo.BookloreSyncTokenGenerator;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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
    private final String KOBO_BOOK_IMAGE_CDN_URL = "https://cdn.kobo.com/book-images/{ImageId}/{Width}/{Height}/{IsGreyscale}/image.jpg";
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMinutes(1)).build();
    private final ObjectMapper objectMapper;
    private final BookloreSyncTokenGenerator bookloreSyncTokenGenerator;

    private static final Set<String> HEADERS_OUT_INCLUDE = Set.of(
            HttpHeaders.AUTHORIZATION.toLowerCase(),
            HttpHeaders.AUTHORIZATION,
            HttpHeaders.USER_AGENT,
            HttpHeaders.ACCEPT_LANGUAGE
    );

    private static final Set<String> HEADERS_OUT_EXCLUDE = Set.of(
            KoboHeaders.X_KOBO_SYNCTOKEN
    );

    private boolean isKoboHeader(String headerName) {
        return headerName.toLowerCase().startsWith("x-kobo-");
    }

    public ResponseEntity<JsonNode> proxyCurrentRequest() {
        return proxyCurrentRequest(null, false);
    }

    public ResponseEntity<JsonNode> proxyCurrentRequest(Object body, boolean includeSyncToken) {
        HttpServletRequest request = RequestUtils.getCurrentRequest();
        String path = KOBO_API_PREFIX_PATTERN.matcher(request.getServletPath()).replaceFirst("");

        BookloreSyncToken syncToken = null;
        if (includeSyncToken) {
            syncToken = bookloreSyncTokenGenerator.fromRequestHeaders(request);
            if (syncToken == null || syncToken.getRawKoboSyncToken() == null || syncToken.getRawKoboSyncToken().isBlank()) {
                //throw new IllegalStateException("Request must include sync token, but none found");
            }
        }

        return executeProxyRequest(request, body, path, includeSyncToken, syncToken);
    }

    public URI getKoboCDNCoverUri(String imageId, int width, int height, boolean isGreyscale) {
        return UriComponentsBuilder.fromUriString(KOBO_BOOK_IMAGE_CDN_URL)
                .encode()
                .buildAndExpand(imageId, width, height, isGreyscale)
                .toUri();
    }

    private ResponseEntity<JsonNode> executeProxyRequest(HttpServletRequest request, Object body, String path, boolean includeSyncToken, BookloreSyncToken syncToken) {
        try {
            String koboBaseUrl = "https://storeapi.kobo.com";

            String queryString = request.getQueryString();
            String uriString = koboBaseUrl + path;
            if (queryString != null && !queryString.isBlank()) {
                uriString += "?" + queryString;
            }

            URI uri = URI.create(uriString);
            log.debug("Kobo proxy URL: {}", uri);

            String bodyString = body != null ? objectMapper.writeValueAsString(body) : "{}";
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofMinutes(1))
                    .method(request.getMethod(), HttpRequest.BodyPublishers.ofString(bodyString))
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .header(HttpHeaders.ACCEPT, "application/json");

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
                    responseHeaders.addAll(key, values);
                }
            });

            if (responseHeaders.getFirst(KoboHeaders.X_KOBO_SYNCTOKEN) != null && includeSyncToken && syncToken != null) {
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

            log.debug("Kobo proxy response status: {}", response.statusCode());

            return new ResponseEntity<>(responseBody, responseHeaders, HttpStatus.valueOf(response.statusCode()));

        } catch (Exception e) {
            log.error("Failed to proxy request to Kobo", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to proxy request to Kobo", e);
        }
    }
}
