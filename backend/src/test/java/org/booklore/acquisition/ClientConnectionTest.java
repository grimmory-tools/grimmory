package org.booklore.acquisition;

import org.booklore.model.dto.acquisition.ConnectionTestResult;
import org.booklore.model.entity.AcquisitionClientEntity;
import org.booklore.model.enums.AcquisitionClientType;
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
class ClientConnectionTest {

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
    void testClientConnection_success_whenVersionResponseReturned() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"version\": \"4.2.0\"}"));

        AcquisitionClientEntity client = AcquisitionClientEntity.builder()
                .id(1L)
                .name("SABnzbd")
                .type(AcquisitionClientType.SABNZBD)
                .url("http://localhost:" + server.getPort())
                .apiKey("sabkey")
                .category("books")
                .enabled(true)
                .createdAt(Instant.now())
                .build();

        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));

        ConnectionTestResult result = service.testClientConnection(1L);

        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("successfully");
    }

    @Test
    void testClientConnection_failure_whenNoVersionField() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"error\": \"Wrong API key\"}"));

        AcquisitionClientEntity client = AcquisitionClientEntity.builder()
                .id(2L)
                .name("SABnzbd")
                .type(AcquisitionClientType.SABNZBD)
                .url("http://localhost:" + server.getPort())
                .apiKey("badkey")
                .category("books")
                .enabled(true)
                .createdAt(Instant.now())
                .build();

        when(clientRepository.findById(2L)).thenReturn(Optional.of(client));

        ConnectionTestResult result = service.testClientConnection(2L);

        assertThat(result.success()).isFalse();
    }

    @Test
    void testClientConnection_failure_whenConnectionRefused() {
        AcquisitionClientEntity client = AcquisitionClientEntity.builder()
                .id(3L)
                .name("DeadClient")
                .type(AcquisitionClientType.SABNZBD)
                .url("http://localhost:1")
                .apiKey("key")
                .category("books")
                .enabled(true)
                .createdAt(Instant.now())
                .build();

        when(clientRepository.findById(3L)).thenReturn(Optional.of(client));

        ConnectionTestResult result = service.testClientConnection(3L);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isNotBlank();
    }
}
