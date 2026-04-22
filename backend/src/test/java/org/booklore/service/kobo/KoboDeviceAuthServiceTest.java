package org.booklore.service.kobo;

import org.booklore.model.dto.kobo.KoboAuthentication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("KoboDeviceAuthService Tests")
class KoboDeviceAuthServiceTest {

    private KoboDeviceAuthService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        service = new KoboDeviceAuthService(objectMapper);
    }

    @Test
    @DisplayName("Should authenticate device with valid UserKey")
    void authenticateDevice_validUserKey() {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("UserKey", "test-user-key-123");

        ResponseEntity<KoboAuthentication> response = service.authenticateDevice(body);

        assertEquals(200, response.getStatusCode().value());
        KoboAuthentication auth = response.getBody();
        assertNotNull(auth);
        assertEquals("test-user-key-123", auth.getUserKey());
        assertNotNull(auth.getAccessToken());
        assertNotNull(auth.getRefreshToken());
        assertNotNull(auth.getTrackingId());
        assertEquals(24, auth.getAccessToken().length());
        assertEquals(24, auth.getRefreshToken().length());
    }

    @Test
    @DisplayName("Should generate unique tokens per request")
    void authenticateDevice_uniqueTokens() {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("UserKey", "user1");

        KoboAuthentication first = service.authenticateDevice(body).getBody();
        KoboAuthentication second = service.authenticateDevice(body).getBody();

        assertNotEquals(first.getAccessToken(), second.getAccessToken());
        assertNotEquals(first.getRefreshToken(), second.getRefreshToken());
        assertNotEquals(first.getTrackingId(), second.getTrackingId());
    }

    @Test
    @DisplayName("Should throw when request body is null")
    void authenticateDevice_nullBody() {
        assertThrows(IllegalArgumentException.class, () -> service.authenticateDevice(null));
    }

    @Test
    @DisplayName("Should throw when UserKey is missing")
    void authenticateDevice_missingUserKey() {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("SomeOtherField", "value");

        assertThrows(IllegalArgumentException.class, () -> service.authenticateDevice(body));
    }

    @Test
    @DisplayName("Should set correct Content-Type header")
    void authenticateDevice_contentTypeHeader() {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("UserKey", "user1");

        ResponseEntity<KoboAuthentication> response = service.authenticateDevice(body);

        assertTrue(response.getHeaders().getFirst("Content-Type").contains("application/json"));
    }

    @Test
    @DisplayName("Should set Content-Length header")
    void authenticateDevice_contentLengthHeader() {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("UserKey", "user1");

        ResponseEntity<KoboAuthentication> response = service.authenticateDevice(body);

        String contentLength = response.getHeaders().getFirst("Content-Length");
        assertNotNull(contentLength);
        assertTrue(Integer.parseInt(contentLength) > 0);
    }

    @Test
    @DisplayName("Should serialize KoboAuthentication to JSON bytes")
    void toJsonBytes_validAuth() {
        KoboAuthentication auth = KoboAuthentication.builder()
                .accessToken("abc123")
                .refreshToken("def456")
                .trackingId("track-1")
                .userKey("user-key")
                .build();

        byte[] bytes = service.toJsonBytes(auth);

        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
        String json = new String(bytes);
        assertTrue(json.contains("abc123"));
        assertTrue(json.contains("user-key"));
    }

    @Test
    @DisplayName("Should preserve UserKey value exactly as received")
    void authenticateDevice_preservesUserKey() {
        String userKey = "special-chars_123.ABC@domain";
        ObjectNode body = objectMapper.createObjectNode();
        body.put("UserKey", userKey);

        KoboAuthentication auth = service.authenticateDevice(body).getBody();

        assertEquals(userKey, auth.getUserKey());
    }
}
