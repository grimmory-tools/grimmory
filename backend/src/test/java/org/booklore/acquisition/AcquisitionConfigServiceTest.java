package org.booklore.acquisition;

import org.booklore.exception.APIException;
import org.booklore.model.dto.acquisition.ClientDTO;
import org.booklore.model.dto.acquisition.IndexerDTO;
import org.booklore.model.entity.AcquisitionClientEntity;
import org.booklore.model.entity.AcquisitionIndexerEntity;
import org.booklore.model.enums.AcquisitionClientType;
import org.booklore.repository.AcquisitionClientRepository;
import org.booklore.repository.AcquisitionIndexerRepository;
import org.booklore.service.acquisition.AcquisitionConfigService;
import org.booklore.service.acquisition.UrlValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AcquisitionConfigServiceTest {

    @Mock
    private AcquisitionIndexerRepository indexerRepository;

    @Mock
    private AcquisitionClientRepository clientRepository;

    @Mock
    private UrlValidator urlValidator;

    @InjectMocks
    private AcquisitionConfigService service;

    private AcquisitionIndexerEntity indexerEntity;
    private AcquisitionClientEntity clientEntity;

    @BeforeEach
    void setUp() {
        indexerEntity = AcquisitionIndexerEntity.builder()
                .id(1L)
                .name("NZBGeek")
                .url("https://api.nzbgeek.info")
                .apiKey("key123")
                .enabled(true)
                .priority(0)
                .createdAt(Instant.now())
                .build();

        clientEntity = AcquisitionClientEntity.builder()
                .id(1L)
                .name("SABnzbd")
                .type(AcquisitionClientType.SABNZBD)
                .url("http://localhost:8080")
                .apiKey("sabkey")
                .category("books")
                .enabled(true)
                .createdAt(Instant.now())
                .build();
    }

    // ─── Indexer CRUD ─────────────────────────────────────────────────────────

    @Test
    void getIndexers_returnsMappedList() {
        when(indexerRepository.findAll()).thenReturn(List.of(indexerEntity));

        List<IndexerDTO> result = service.getIndexers();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("NZBGeek");
    }

    @Test
    void createIndexer_savesAndReturnsDTO() {
        IndexerDTO dto = new IndexerDTO(null, "NZBGeek", "https://api.nzbgeek.info", "key123", true, 0);
        when(indexerRepository.save(any())).thenReturn(indexerEntity);

        IndexerDTO result = service.createIndexer(dto);

        assertThat(result.name()).isEqualTo("NZBGeek");
        verify(indexerRepository).save(any(AcquisitionIndexerEntity.class));
    }

    @Test
    void updateIndexer_notFound_throws404() {
        when(indexerRepository.findById(99L)).thenReturn(Optional.empty());
        IndexerDTO dto = new IndexerDTO(99L, "X", "url", "key", true, 0);

        assertThatThrownBy(() -> service.updateIndexer(99L, dto))
                .isInstanceOf(APIException.class)
                .satisfies(e -> assertThat(((APIException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updateIndexer_found_updatesAndReturns() {
        IndexerDTO dto = new IndexerDTO(1L, "Updated", "https://new.url", "newkey", false, 5);
        when(indexerRepository.findById(1L)).thenReturn(Optional.of(indexerEntity));
        when(indexerRepository.save(any())).thenReturn(indexerEntity);

        IndexerDTO result = service.updateIndexer(1L, dto);

        assertThat(result).isNotNull();
        verify(indexerRepository).save(indexerEntity);
    }

    @Test
    void deleteIndexer_notFound_throws404() {
        when(indexerRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.deleteIndexer(99L))
                .isInstanceOf(APIException.class)
                .satisfies(e -> assertThat(((APIException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void deleteIndexer_found_deletes() {
        when(indexerRepository.existsById(1L)).thenReturn(true);

        service.deleteIndexer(1L);

        verify(indexerRepository).deleteById(1L);
    }

    // ─── Client CRUD ──────────────────────────────────────────────────────────

    @Test
    void getClients_returnsMappedList() {
        when(clientRepository.findAll()).thenReturn(List.of(clientEntity));

        List<ClientDTO> result = service.getClients();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("SABnzbd");
    }

    @Test
    void createClient_savesAndReturnsDTO() {
        ClientDTO dto = new ClientDTO(null, "SABnzbd", "SABNZBD", "http://localhost:8080", "sabkey", "books", true);
        when(clientRepository.save(any())).thenReturn(clientEntity);

        ClientDTO result = service.createClient(dto);

        assertThat(result.name()).isEqualTo("SABnzbd");
        verify(clientRepository).save(any(AcquisitionClientEntity.class));
    }

    @Test
    void updateClient_notFound_throws404() {
        when(clientRepository.findById(99L)).thenReturn(Optional.empty());
        ClientDTO dto = new ClientDTO(99L, "X", "SABNZBD", "url", "key", "books", true);

        assertThatThrownBy(() -> service.updateClient(99L, dto))
                .isInstanceOf(APIException.class)
                .satisfies(e -> assertThat(((APIException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void deleteClient_notFound_throws404() {
        when(clientRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.deleteClient(99L))
                .isInstanceOf(APIException.class)
                .satisfies(e -> assertThat(((APIException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void deleteClient_found_deletes() {
        when(clientRepository.existsById(1L)).thenReturn(true);

        service.deleteClient(1L);

        verify(clientRepository).deleteById(1L);
    }
}
