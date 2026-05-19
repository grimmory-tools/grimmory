package org.booklore.service.metadata.parser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
@Component
public class ItunesHttpClient {

    private final HttpClient httpClient;

    private final Semaphore rateLimitGate = new Semaphore(1, true);
    private long lastRequestTime = 0;
    private static final long MIN_REQUEST_INTERVAL_MS = 3000;

    /**
     * Executes a GET request to the iTunes API with automatic rate-limiting, retries for 429, and error logging.
     */
    public String executeGet(String baseUrl, Map<String, String> params, String country) throws IOException, InterruptedException {
        var uriBuilder = UriComponentsBuilder.fromUriString(baseUrl);
        uriBuilder.queryParam("country", country);
        params.forEach(uriBuilder::queryParam);

        int retries = 0;
        HttpResponse<String> response = null;
        while (retries <= 2) {
            acquireRateLimitSlot();
            var request = HttpRequest.newBuilder()
                    .uri(uriBuilder.build().toUri())
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return response.body();
            } else if (response.statusCode() == 429) {
                retries++;
                // Note: Retry-After can also be an HTTP-date (RFC 7231). We assume seconds as used by iTunes.
                long backoffMs = response.headers().firstValue("Retry-After")
                        .map(s -> {
                            try {
                                return Long.parseLong(s) * 1000;
                              } catch (NumberFormatException e) {
                                  return 0L;
                              }
                          })
                          .orElse(0L);

                if (backoffMs <= 0) {
                    backoffMs = (long) (5000 * Math.pow(2, retries - 1) + ThreadLocalRandom.current().nextLong(0, 1000));
                }

                log.warn("iTunes API returned 429 Too Many Requests. Retrying after {} ms... (Attempt {}/2)", backoffMs, retries);
                Thread.sleep(backoffMs);
            } else {
                break;
            }
        }

        if (response.statusCode() != 200) {
            log.error("iTunes API request failed. Status: {}", response.statusCode());
            throw new IOException("iTunes API request failed with status: " + response.statusCode());
        }
        throw new IOException("iTunes API request failed with unknown error");
    }

    private void acquireRateLimitSlot() throws InterruptedException {
        if (!rateLimitGate.tryAcquire(15, TimeUnit.SECONDS)) {
            throw new InterruptedException("iTunes rate limit gate timeout");
        }
        try {
            long sinceLast = System.currentTimeMillis() - lastRequestTime;
            if (sinceLast < MIN_REQUEST_INTERVAL_MS) {
                Thread.sleep(MIN_REQUEST_INTERVAL_MS - sinceLast);
            }
            lastRequestTime = System.currentTimeMillis();
        } finally {
            rateLimitGate.release();
        }
    }
}
