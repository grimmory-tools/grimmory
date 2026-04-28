package org.booklore.service.acquisition;

import org.booklore.exception.ApiError;
import org.booklore.model.dto.acquisition.ClientDTO;
import org.booklore.model.dto.acquisition.ConnectionTestResult;
import org.booklore.model.dto.acquisition.IndexerDTO;
import org.booklore.model.entity.AcquisitionClientEntity;
import org.booklore.model.entity.AcquisitionIndexerEntity;
import org.booklore.model.enums.AcquisitionClientType;
import org.booklore.repository.AcquisitionClientRepository;
import org.booklore.repository.AcquisitionIndexerRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class AcquisitionConfigService {

    private final AcquisitionIndexerRepository indexerRepository;
    private final AcquisitionClientRepository clientRepository;
    private final UrlValidator urlValidator;

    public List<IndexerDTO> getIndexers() {
        return indexerRepository.findAll().stream()
                .map(this::toIndexerDTO)
                .toList();
    }

    public IndexerDTO createIndexer(IndexerDTO dto) {
        AcquisitionIndexerEntity entity = AcquisitionIndexerEntity.builder()
                .name(dto.name())
                .url(dto.url())
                .apiKey(dto.apiKey())
                .enabled(dto.enabled())
                .priority(dto.priority())
                .createdAt(Instant.now())
                .build();
        return toIndexerDTO(indexerRepository.save(entity));
    }

    public IndexerDTO updateIndexer(Long id, IndexerDTO dto) {
        AcquisitionIndexerEntity entity = indexerRepository.findById(id)
                .orElseThrow(() -> ApiError.INDEXER_NOT_FOUND.createException(id));
        entity.setName(dto.name());
        entity.setUrl(dto.url());
        if (dto.apiKey() != null && !dto.apiKey().startsWith("****")) {
            entity.setApiKey(dto.apiKey());
        }
        entity.setEnabled(dto.enabled());
        entity.setPriority(dto.priority());
        return toIndexerDTO(indexerRepository.save(entity));
    }

    public void deleteIndexer(Long id) {
        if (!indexerRepository.existsById(id)) {
            throw ApiError.INDEXER_NOT_FOUND.createException(id);
        }
        indexerRepository.deleteById(id);
    }

    public ConnectionTestResult testIndexerConnection(Long id) {
        AcquisitionIndexerEntity indexer = indexerRepository.findById(id)
                .orElseThrow(() -> ApiError.INDEXER_NOT_FOUND.createException(id));
        try {
            urlValidator.validateOutboundUrl(indexer.getUrl());
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            String url = indexer.getUrl() + "/api?t=caps&apikey=" + indexer.getApiKey();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body().contains("caps")) {
                return new ConnectionTestResult(true, "Indexer connected successfully");
            }
            return new ConnectionTestResult(false, "Unexpected response: HTTP " + response.statusCode());
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("Indexer connection test failed for id={}: {}", id, msg);
            return new ConnectionTestResult(false, msg);
        }
    }

    public List<ClientDTO> getClients() {
        return clientRepository.findAll().stream()
                .map(this::toClientDTO)
                .toList();
    }

    public ClientDTO createClient(ClientDTO dto) {
        AcquisitionClientEntity entity = AcquisitionClientEntity.builder()
                .name(dto.name())
                .type(AcquisitionClientType.valueOf(dto.type()))
                .url(dto.url())
                .apiKey(dto.apiKey())
                .category(dto.category())
                .enabled(dto.enabled())
                .createdAt(Instant.now())
                .build();
        return toClientDTO(clientRepository.save(entity));
    }

    public ClientDTO updateClient(Long id, ClientDTO dto) {
        AcquisitionClientEntity entity = clientRepository.findById(id)
                .orElseThrow(() -> ApiError.CLIENT_NOT_FOUND.createException(id));
        entity.setName(dto.name());
        entity.setType(AcquisitionClientType.valueOf(dto.type()));
        entity.setUrl(dto.url());
        if (dto.apiKey() != null && !dto.apiKey().startsWith("****")) {
            entity.setApiKey(dto.apiKey());
        }
        entity.setCategory(dto.category());
        entity.setEnabled(dto.enabled());
        return toClientDTO(clientRepository.save(entity));
    }

    public void deleteClient(Long id) {
        if (!clientRepository.existsById(id)) {
            throw ApiError.CLIENT_NOT_FOUND.createException(id);
        }
        clientRepository.deleteById(id);
    }

    public ConnectionTestResult testClientConnection(Long id) {
        AcquisitionClientEntity client = clientRepository.findById(id)
                .orElseThrow(() -> ApiError.CLIENT_NOT_FOUND.createException(id));
        try {
            urlValidator.validateOutboundUrl(client.getUrl());
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            String url = client.getUrl() + "/api?mode=version&apikey=" + client.getApiKey() + "&output=json";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body().contains("version")) {
                return new ConnectionTestResult(true, "SABnzbd connected successfully");
            }
            return new ConnectionTestResult(false, "Unexpected response: HTTP " + response.statusCode());
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("Client connection test failed for id={}: {}", id, msg);
            return new ConnectionTestResult(false, msg);
        }
    }

    private IndexerDTO toIndexerDTO(AcquisitionIndexerEntity e) {
        return new IndexerDTO(e.getId(), e.getName(), e.getUrl(), maskApiKey(e.getApiKey()), e.isEnabled(), e.getPriority());
    }

    private ClientDTO toClientDTO(AcquisitionClientEntity e) {
        return new ClientDTO(e.getId(), e.getName(), e.getType().name(), e.getUrl(), maskApiKey(e.getApiKey()), e.getCategory(), e.isEnabled());
    }

    private String maskApiKey(String key) {
        if (key == null || key.length() <= 4) return "****";
        return "****" + key.substring(key.length() - 4);
    }
}
