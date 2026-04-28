package org.booklore.service.acquisition;

import org.booklore.model.entity.AcquisitionClientEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
@Slf4j
@AllArgsConstructor
public class SabnzbdClient {

    private final ObjectMapper objectMapper;

    public String sendNzb(AcquisitionClientEntity client, String nzbUrl, String bookTitle) {
        try {
            URI uri = UriComponentsBuilder.fromUriString(client.getUrl() + "/api")
                    .queryParam("mode", "addurl")
                    .queryParam("name", nzbUrl)
                    .queryParam("nzbname", bookTitle)
                    .queryParam("cat", client.getCategory() != null ? client.getCategory() : "books")
                    .queryParam("apikey", client.getApiKey())
                    .queryParam("output", "json")
                    .build()
                    .toUri();

            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("SABnzbd returned HTTP " + response.statusCode());
            }

            JsonNode json = objectMapper.readTree(response.body());

            boolean status = json.path("status").asBoolean(false);
            if (!status) {
                String error = json.path("error").asText("unknown error");
                throw new RuntimeException("SABnzbd rejected NZB: " + error);
            }

            JsonNode nzoIds = json.path("nzo_ids");
            if (nzoIds.isArray() && nzoIds.size() > 0) {
                return nzoIds.get(0).asText();
            }
            throw new RuntimeException("SABnzbd returned no job ID");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to send NZB to SABnzbd: " + e.getMessage(), e);
        }
    }

    public boolean isInQueue(AcquisitionClientEntity client, String nzoId) {
        try {
            URI uri = UriComponentsBuilder.fromUriString(client.getUrl() + "/api")
                    .queryParam("mode", "queue")
                    .queryParam("output", "json")
                    .queryParam("apikey", client.getApiKey())
                    .build()
                    .toUri();

            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode queue = objectMapper.readTree(response.body()).path("queue").path("slots");
            if (queue.isArray()) {
                for (JsonNode slot : queue) {
                    if (nzoId.equals(slot.path("nzo_id").asText())) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            log.warn("Failed to check SABnzbd queue for job {}: {}", nzoId, e.getMessage());
            return false;
        }
    }
}
