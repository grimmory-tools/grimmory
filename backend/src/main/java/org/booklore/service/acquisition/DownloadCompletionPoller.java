package org.booklore.service.acquisition;

import org.booklore.model.entity.AcquisitionClientEntity;
import org.booklore.model.entity.WantedBookEntity;
import org.booklore.model.enums.WantedBookStatus;
import org.booklore.model.websocket.AcquisitionNotification;
import org.booklore.model.websocket.Topic;
import org.booklore.repository.AcquisitionClientRepository;
import org.booklore.repository.WantedBookRepository;
import org.booklore.service.NotificationService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Polls SABnzbd every 60 seconds to detect completed downloads and
 * transitions DOWNLOADING → DOWNLOADED.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DownloadCompletionPoller {

    private final WantedBookRepository wantedBookRepository;
    private final AcquisitionClientRepository clientRepository;
    private final SabnzbdClient sabnzbdClient;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void poll() {
        List<WantedBookEntity> downloading =
                wantedBookRepository.findByStatus(WantedBookStatus.DOWNLOADING);

        if (downloading.isEmpty()) return;

        List<AcquisitionClientEntity> clients = clientRepository.findByEnabledTrue();
        if (clients.isEmpty()) return;

        AcquisitionClientEntity client = clients.get(0);

        for (WantedBookEntity book : downloading) {
            if (book.getDownloadId() == null) continue;
            try {
                checkBook(client, book);
            } catch (Exception e) {
                log.warn("Error polling download status for wanted book id={}: {}", book.getId(), e.getMessage());
            }
        }
    }

    private void checkBook(AcquisitionClientEntity client, WantedBookEntity book) {
        String nzoId = book.getDownloadId();

        if (sabnzbdClient.isInQueue(client, nzoId)) {
            log.debug("Book '{}' still in SABnzbd queue (nzo_id={})", book.getTitle(), nzoId);
            return;
        }

        String historyStatus = getHistoryStatus(client, nzoId);
        if ("Completed".equalsIgnoreCase(historyStatus)) {
            log.info("Book '{}' download completed (nzo_id={})", book.getTitle(), nzoId);
            book.setStatus(WantedBookStatus.DOWNLOADED);
            wantedBookRepository.save(book);
            notificationService.sendMessage(Topic.ACQUISITION_UPDATE,
                    AcquisitionNotification.of(book.getId(), book.getTitle(), WantedBookStatus.DOWNLOADED, "Download completed"));
        } else if ("Failed".equalsIgnoreCase(historyStatus) || "Deleted".equalsIgnoreCase(historyStatus)) {
            log.warn("Book '{}' download {} in SABnzbd (nzo_id={})", book.getTitle(), historyStatus, nzoId);
            book.setStatus(WantedBookStatus.FAILED);
            wantedBookRepository.save(book);
            notificationService.sendMessage(Topic.ACQUISITION_UPDATE,
                    AcquisitionNotification.of(book.getId(), book.getTitle(), WantedBookStatus.FAILED, "Download " + historyStatus.toLowerCase()));
        } else if (historyStatus == null) {
            log.debug("Book '{}' not in queue or history yet (nzo_id={})", book.getTitle(), nzoId);
        }
    }

    private String getHistoryStatus(AcquisitionClientEntity client, String nzoId) {
        try {
            URI uri = UriComponentsBuilder.fromUriString(client.getUrl().replaceAll("/+$", "") + "/api")
                    .queryParam("mode", "history")
                    .queryParam("output", "json")
                    .queryParam("limit", "50")
                    .queryParam("apikey", client.getApiKey())
                    .build()
                    .toUri();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode slots = objectMapper.readTree(response.body()).path("history").path("slots");

            if (slots.isArray()) {
                for (JsonNode slot : slots) {
                    if (nzoId.equals(slot.path("nzo_id").asText())) {
                        return slot.path("status").asText(null);
                    }
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("Failed to check SABnzbd history for nzo_id={}: {}", nzoId, e.getMessage());
            return null;
        }
    }
}
