package org.grimmory.service.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeoIpService {

    private static final String GEO_API_URL = "http://ip-api.com/json/%s?fields=countryCode";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(2);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String resolveCountryCode(String ip) {
        if (ip == null || ip.isBlank() || isPrivateOrLocal(ip)) {
            return null;
        }
        String result = cache.get(ip);
        if (result == null) {
            result = fetchCountryCode(ip);
            cache.put(ip, result);
        }
        return result.isEmpty() ? null : result;
    }

    private String fetchCountryCode(String ip) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(String.format(GEO_API_URL, ip)))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode node = objectMapper.readTree(response.body());
                if (node.has("countryCode") && !node.get("countryCode").asText().isBlank()) {
                    return node.get("countryCode").asText();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("Interrupted while resolving country code for IP: {}", ip);
        } catch (Exception e) {
            log.debug("Failed to resolve country code for IP: {}", ip);
        }
        return "";
    }

    private boolean isPrivateOrLocal(String ip) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            return addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress();
        } catch (Exception e) {
            return true;
        }
    }
}
