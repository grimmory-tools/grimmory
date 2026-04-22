package org.booklore.util.kobo;

import jakarta.servlet.http.HttpServletRequest;
import org.booklore.model.dto.BookloreSyncToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.ObjectMapper;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BookloreSyncTokenGeneratorTest {

    private ObjectMapper objectMapper;
    private BookloreSyncTokenGenerator generator;

    @BeforeEach
    void setUp() {
        objectMapper = mock(ObjectMapper.class);
        generator = new BookloreSyncTokenGenerator(objectMapper);
    }

    @Test
    void testToBase64_success() throws Exception {
        BookloreSyncToken token = BookloreSyncToken.builder()
                .ongoingSyncPointId("ongoing123")
                .lastSuccessfulSyncPointId("last456")
                .rawKoboSyncToken("raw789")
                .build();

        String json = "{\"ongoingSyncPointId\":\"ongoing123\",\"lastSuccessfulSyncPointId\":\"last456\",\"rawKoboSyncToken\":\"raw789\"}";
        when(objectMapper.writeValueAsString(token)).thenReturn(json);

        String result = generator.toBase64(token);

        assertTrue(result.startsWith("BOOKLORE."));
        String base64Part = result.substring("BOOKLORE.".length());
        String decodedJson = new String(Base64.getDecoder().decode(base64Part));
        assertEquals(json, decodedJson);
    }

    @Test
    void testToBase64_exception() throws Exception {
        BookloreSyncToken token = BookloreSyncToken.builder().build();

        when(objectMapper.writeValueAsString(any(BookloreSyncToken.class)))
                .thenThrow(new RuntimeException("Serialization failed"));

        String result = generator.toBase64(token);

        assertEquals("BOOKLORE.", result);
    }

    @Test
    void testFromBase64_booklorePrefix() throws Exception {
        BookloreSyncToken token = BookloreSyncToken.builder()
                .ongoingSyncPointId("test123")
                .lastSuccessfulSyncPointId("test456")
                .build();

        String json = "{\"ongoingSyncPointId\":\"test123\",\"lastSuccessfulSyncPointId\":\"test456\"}";
        String base64 = Base64.getEncoder().encodeToString(json.getBytes());
        String tokenB64 = "BOOKLORE." + base64;

        when(objectMapper.readValue(json.getBytes(), BookloreSyncToken.class)).thenReturn(token);

        BookloreSyncToken result = generator.fromBase64(tokenB64);

        assertNotNull(result);
        assertEquals("test123", result.getOngoingSyncPointId());
        assertEquals("test456", result.getLastSuccessfulSyncPointId());
    }

    @Test
    void testFromBase64_withDot() {
        String rawToken = "some.raw.token";

        BookloreSyncToken result = generator.fromBase64(rawToken);

        assertNotNull(result);
        assertEquals(rawToken, result.getRawKoboSyncToken());
        assertNull(result.getOngoingSyncPointId());
        assertNull(result.getLastSuccessfulSyncPointId());
    }

    @Test
    void testFromBase64_invalidBase64() {
        String invalidToken = "BOOKLORE.invalidbase64";

        BookloreSyncToken result = generator.fromBase64(invalidToken);

        assertNotNull(result);
        assertNull(result.getOngoingSyncPointId());
        assertNull(result.getLastSuccessfulSyncPointId());
        assertNull(result.getRawKoboSyncToken());
    }

    @Test
    void testFromBase64_deserializationException() throws Exception {
        String json = "{\"invalid\":\"json\"}";
        String base64 = Base64.getEncoder().encodeToString(json.getBytes());
        String tokenB64 = "BOOKLORE." + base64;

        when(objectMapper.readValue(any(byte[].class), eq(BookloreSyncToken.class)))
                .thenThrow(new RuntimeException("Deserialization failed"));

        BookloreSyncToken result = generator.fromBase64(tokenB64);

        assertNotNull(result);
        assertNull(result.getOngoingSyncPointId());
        assertNull(result.getLastSuccessfulSyncPointId());
        assertNull(result.getRawKoboSyncToken());
    }

    @Test
    void testFromBase64_emptyString() {
        BookloreSyncToken result = generator.fromBase64("");

        assertNotNull(result);
        assertNull(result.getOngoingSyncPointId());
        assertNull(result.getLastSuccessfulSyncPointId());
        assertNull(result.getRawKoboSyncToken());
    }

    @Test
    void testFromBase64_null() {
        BookloreSyncToken result = generator.fromBase64(null);

        assertNotNull(result);
        assertNull(result.getOngoingSyncPointId());
        assertNull(result.getLastSuccessfulSyncPointId());
        assertNull(result.getRawKoboSyncToken());
    }

    @Test
    void testFromRequestHeaders_withHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Kobo-Synctoken", "BOOKLORE.test");

        BookloreSyncToken expectedToken = new BookloreSyncToken();
        when(objectMapper.readValue(any(byte[].class), eq(BookloreSyncToken.class))).thenReturn(expectedToken);

        BookloreSyncToken result = generator.fromRequestHeaders(request);

        assertNotNull(result);
        assertEquals(expectedToken, result);
    }

    @Test
    void testFromRequestHeaders_noHeader() {
        HttpServletRequest request = new MockHttpServletRequest();

        BookloreSyncToken result = generator.fromRequestHeaders(request);

        assertNull(result);
    }

    @Test
    void testFromRequestHeaders_nullRequest() {
        BookloreSyncToken result = generator.fromRequestHeaders(null);

        assertNull(result);
    }
}
