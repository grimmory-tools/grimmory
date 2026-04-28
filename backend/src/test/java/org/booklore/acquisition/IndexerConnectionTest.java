package org.booklore.acquisition;

import org.booklore.model.dto.acquisition.ConnectionTestResult;
import org.booklore.model.entity.AcquisitionIndexerEntity;
import org.booklore.repository.AcquisitionClientRepository;
import org.booklore.repository.AcquisitionIndexerRepository;
import org.booklore.service.acquisition.AcquisitionConfigService;
import org.booklore.service.acquisition.UrlValidator;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IndexerConnectionTest {

    private MockWebServer server;

    @Mock
    private AcquisitionIndexerRepository indexerRepository;

    @Mock
    private AcquisitionClientRepository clientRepository;

    @Mock
    private UrlValidator urlValidator;

    @InjectMocks
    private AcquisitionConfigService service;

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stopServer() throws IOException {
        server.shutdown();
    }

    @Test
    void testIndexerConnection_success_whenCapsResponseReturned() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("<?xml version=\"1.0\" encoding=\"UTF-8\"?><caps><server title=\"Test\"/></caps>"));

        AcquisitionIndexerEntity indexer = AcquisitionIndexerEntity.builder()
                .id(1L)
                .name("TestIndexer")
                .url("http://localhost:" + server.getPort())
                .apiKey("testkey")
                .enabled(true)
                .priority(0)
                .createdAt(Instant.now())
                .build();

        when(indexerRepository.findById(1L)).thenReturn(Optional.of(indexer));

        ConnectionTestResult result = service.testIndexerConnection(1L);

        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("successfully");
    }

    @Test
    void testIndexerConnection_failure_whenServerReturns500() {
        server.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));

        AcquisitionIndexerEntity indexer = AcquisitionIndexerEntity.builder()
                .id(2L)
                .name("BadIndexer")
                .url("http://localhost:" + server.getPort())
                .apiKey("key")
                .enabled(true)
                .priority(0)
                .createdAt(Instant.now())
                .build();

        when(indexerRepository.findById(2L)).thenReturn(Optional.of(indexer));

        ConnectionTestResult result = service.testIndexerConnection(2L);

        assertThat(result.success()).isFalse();
    }

    @Test
    void testIndexerConnection_failure_whenConnectionRefused() {
        AcquisitionIndexerEntity indexer = AcquisitionIndexerEntity.builder()
                .id(3L)
                .name("DeadIndexer")
                .url("http://localhost:1")
                .apiKey("key")
                .enabled(true)
                .priority(0)
                .createdAt(Instant.now())
                .build();

        when(indexerRepository.findById(3L)).thenReturn(Optional.of(indexer));

        ConnectionTestResult result = service.testIndexerConnection(3L);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isNotBlank();
    }
}
