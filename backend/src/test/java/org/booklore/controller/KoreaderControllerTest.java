package org.booklore.controller;

import org.booklore.model.dto.progress.KoreaderProgress;
import org.booklore.service.koreader.KoreaderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KoreaderControllerTest {

    @Mock
    private KoreaderService koreaderService;

    @InjectMocks
    private KoreaderController controller;

    @BeforeEach
    void setUp() {
        try (AutoCloseable mocks = MockitoAnnotations.openMocks(this)) {
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void authorizeUser_returnsServiceResponse() {
        Map<String, String> expected = Map.of("username", "test");
        when(koreaderService.authorizeUser()).thenReturn(ResponseEntity.ok(expected));
        ResponseEntity<Map<String, String>> resp = controller.authorizeUser();
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(expected, resp.getBody());
    }

    @Test
    void createUser_returnsForbiddenAndLogs() {
        Map<String, Object> userData = Map.of("username", "test");
        ResponseEntity<?> resp = controller.createUser(userData);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        assertTrue(((Map<?, ?>) resp.getBody()).get("error").toString().contains("disabled"));
    }

    @Test
    void getProgress_returnsProgress() {
        KoreaderProgress progress = KoreaderProgress.builder()
                .document("doc")
                .progress("progress")
                .percentage(0.5F)
                .device("dev")
                .device_id("id")
                .build();
        when(koreaderService.getProgress("hash")).thenReturn(progress);
        ResponseEntity<KoreaderProgress> resp = controller.getProgress("hash");
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, resp.getHeaders().getContentType());
        assertEquals(progress, resp.getBody());
    }

    @Test
    void updateProgress_returnsOk() {
        KoreaderProgress progress = KoreaderProgress.builder()
                .document("doc")
                .progress("progress")
                .percentage(0.5F)
                .device("dev")
                .device_id("id")
                .build();
        doNothing().when(koreaderService).saveProgress("doc", progress);
        ResponseEntity<?> resp = controller.updateProgress(progress);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("progress updated", ((Map<?, ?>) resp.getBody()).get("status"));
    }
}
