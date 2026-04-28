package org.booklore.acquisition;

import org.booklore.model.entity.AcquisitionClientEntity;
import org.booklore.model.enums.AcquisitionClientType;
import org.booklore.service.acquisition.SabnzbdClient;
import tools.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class SabnzbdClientTest {

    private MockWebServer server;
    private SabnzbdClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new SabnzbdClient(new ObjectMapper());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private AcquisitionClientEntity sabClient() {
        return AcquisitionClientEntity.builder()
                .id(1L)
                .name("SABnzbd")
                .type(AcquisitionClientType.SABNZBD)
                .url("http://localhost:" + server.getPort())
                .apiKey("testkey123")
                .category("books")
                .enabled(true)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void sendNzb_success_returnsNzoId() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"status\": true, \"nzo_ids\": [\"SABnzbd_nzo_abc123\"]}"));

        String jobId = client.sendNzb(sabClient(),
                "https://indexer.com/nzb/dune.nzb", "Dune");

        assertThat(jobId).isEqualTo("SABnzbd_nzo_abc123");
    }

    @Test
    void sendNzb_sabReturnsErrorStatus_throwsRuntimeException() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"status\": false, \"error\": \"Invalid API key\"}"));

        assertThatThrownBy(() ->
                client.sendNzb(sabClient(), "https://indexer.com/nzb/dune.nzb", "Dune"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid API key");
    }

    @Test
    void sendNzb_http500_throwsRuntimeException() {
        server.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));

        assertThatThrownBy(() ->
                client.sendNzb(sabClient(), "https://indexer.com/nzb/dune.nzb", "Dune"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void sendNzb_connectionRefused_throwsRuntimeException() {
        AcquisitionClientEntity deadClient = AcquisitionClientEntity.builder()
                .id(99L)
                .name("Dead")
                .type(AcquisitionClientType.SABNZBD)
                .url("http://localhost:1")
                .apiKey("key")
                .category("books")
                .enabled(true)
                .createdAt(Instant.now())
                .build();

        assertThatThrownBy(() ->
                client.sendNzb(deadClient, "https://indexer.com/nzb/dune.nzb", "Dune"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void isInQueue_jobPresent_returnsTrue() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "queue": {
                            "slots": [
                              {"nzo_id": "SABnzbd_nzo_abc123", "filename": "Dune.nzb"}
                            ]
                          }
                        }
                        """));

        boolean inQueue = client.isInQueue(sabClient(), "SABnzbd_nzo_abc123");

        assertThat(inQueue).isTrue();
    }

    @Test
    void isInQueue_jobAbsent_returnsFalse() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "queue": {
                            "slots": []
                          }
                        }
                        """));

        boolean inQueue = client.isInQueue(sabClient(), "SABnzbd_nzo_missing");

        assertThat(inQueue).isFalse();
    }
}
