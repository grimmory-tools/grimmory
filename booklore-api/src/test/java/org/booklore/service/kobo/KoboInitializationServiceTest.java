package org.booklore.service.kobo;

import org.booklore.model.dto.kobo.KoboResources;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.settings.KoboSettings;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.util.kobo.KoboUrlBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
@DisplayName("KoboInitializationService Tests")
class KoboInitializationServiceTest {

    @Mock
    private AppSettingService appSettingService;

    @Mock
    private KoboServerProxy koboServerProxy;

    @Mock
    private KoboResourcesComponent koboResourcesComponent;

    @Mock
    private KoboUrlBuilder koboUrlBuilder;

    @InjectMocks
    private KoboInitializationService service;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    void mockWithForwardingToKoboStore(boolean enabled) {
        KoboSettings koboSettings = KoboSettings.builder()
                .forwardToKoboStore(enabled)
                .build();

        AppSettings appSettings = AppSettings.builder()
                        .koboSettings(koboSettings)
                        .build();

        when(appSettingService.getAppSettings()).thenReturn(appSettings);
    }

    @BeforeEach
    void setUp() {
        when(koboUrlBuilder.baseBuilder()).thenReturn(UriComponentsBuilder.fromUriString("http://localhost:8080"));
        when(koboUrlBuilder.withBaseUrl(eq("test-token"), any())).thenReturn("http://localhost:8080/with-base-url");
        when(koboUrlBuilder.imageUrlTemplate("test-token")).thenReturn("http://localhost:8080/kobo/test-token/image/{ImageId}/{Width}/{Height}/false/image.jpg");
        when(koboUrlBuilder.imageUrlQualityTemplate("test-token")).thenReturn("http://localhost:8080/kobo/test-token/image/{ImageId}/{Width}/{Height}/{Quality}/{IsGreyscale}/image.jpg");
        mockWithForwardingToKoboStore(true);
    }

    @Nested
    @DisplayName("When Kobo proxy returns Resources")
    class WithProxyResources {

        @Test
        @DisplayName("Should use Resources from Kobo proxy response")
        void initialize_usesProxyResources() throws Exception {
            ObjectNode resources = objectMapper.createObjectNode();
            resources.put("example", "https://storeapi.kobo.com/v1/library/sync");
            resources.put("image_host", "https://cdn.kobo.com/book-images/");

            ObjectNode body = objectMapper.createObjectNode();
            body.set("Resources", resources);

            when(koboServerProxy.proxyCurrentRequest(null, false))
                    .thenReturn(ResponseEntity.ok(body));

            ResponseEntity<KoboResources> response = service.initialize("test-token");

            assertEquals(200, response.getStatusCode().value());
            assertNotNull(response.getBody());
            JsonNode result = response.getBody().getResources();
            assertEquals("https://storeapi.kobo.com/v1/library/sync", result.get("example").asText());
        }

        @Test
        @DisplayName("Should override image_host with local base URL")
        void initialize_overridesImageHost() throws Exception {
            ObjectNode resources = objectMapper.createObjectNode();
            resources.put("image_host", "https://cdn.kobo.com/book-images/");

            ObjectNode body = objectMapper.createObjectNode();
            body.set("Resources", resources);

            when(koboServerProxy.proxyCurrentRequest(null, false))
                    .thenReturn(ResponseEntity.ok(body));

            ResponseEntity<KoboResources> response = service.initialize("test-token");

            JsonNode result = response.getBody().getResources();
            assertEquals("http://localhost:8080", result.get("image_host").asText());
        }

        @Test
        @DisplayName("Should override image_url_template with local URL")
        void initialize_overridesImageUrlTemplate() throws Exception {
            ObjectNode resources = objectMapper.createObjectNode();
            resources.put("image_url_template", "https://cdn.kobo.com/book-images/{ImageId}/{Width}/{Height}/false/image.jpg");

            ObjectNode body = objectMapper.createObjectNode();
            body.set("Resources", resources);

            when(koboServerProxy.proxyCurrentRequest(null, false))
                    .thenReturn(ResponseEntity.ok(body));

            ResponseEntity<KoboResources> response = service.initialize("test-token");

            JsonNode result = response.getBody().getResources();
            assertTrue(result.get("image_url_template").asText().contains("localhost:8080"));
        }
    }

    @Nested
    @DisplayName("When Kobo proxy fails or returns no Resources")
    class WithoutProxyResources {

        @Test
        @DisplayName("Should fall back to local resources when proxy returns null body")
        void initialize_fallsBackOnNullBody() throws Exception {
            when(koboServerProxy.proxyCurrentRequest(null, false))
                    .thenReturn(ResponseEntity.ok(null));

            ObjectNode fallbackResources = objectMapper.createObjectNode();
            fallbackResources.put("library_sync", "https://storeapi.kobo.com/v1/library/sync");
            when(koboResourcesComponent.getResources()).thenReturn(fallbackResources);

            ResponseEntity<KoboResources> response = service.initialize("test-token");

            assertEquals(200, response.getStatusCode().value());
            verify(koboResourcesComponent).getResources();
        }

        @Test
        @DisplayName("Should fall back to local resources when proxy is disabled")
        void initialize_fallsBackOnProxyDisabled() throws Exception {
            mockWithForwardingToKoboStore(false);

            ObjectNode resources = objectMapper.createObjectNode();
            resources.put("example", "https://storeapi.kobo.com/v1/library/sync");
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.set("Resources", resources);

            when(koboServerProxy.proxyCurrentRequest(null, false))
                    .thenReturn(ResponseEntity.ok(requestBody));

            ObjectNode fallbackResources = objectMapper.createObjectNode();
            fallbackResources.put("example", "http://127.0.0.1/v1/library/sync");
            when(koboResourcesComponent.getResources()).thenReturn(fallbackResources);

            ResponseEntity<KoboResources> response = service.initialize("test-token");

            var body = response.getBody();

            assertNotNull(body);
            assertEquals("http://127.0.0.1/v1/library/sync", body.getResources().get("example").asText());
        }

        @Test
        @DisplayName("Should fall back to local resources when proxy throws exception")
        void initialize_fallsBackOnProxyException() throws Exception {
            when(koboServerProxy.proxyCurrentRequest(null, false))
                    .thenThrow(new RuntimeException("Connection refused"));

            ObjectNode fallbackResources = objectMapper.createObjectNode();
            fallbackResources.put("library_sync", "https://storeapi.kobo.com/v1/library/sync");
            when(koboResourcesComponent.getResources()).thenReturn(fallbackResources);

            ResponseEntity<KoboResources> response = service.initialize("test-token");

            assertEquals(200, response.getStatusCode().value());
            verify(koboResourcesComponent).getResources();
        }

        @Test
        @DisplayName("Should fall back when proxy response has no Resources key")
        void initialize_fallsBackOnMissingResourcesKey() throws Exception {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("SomeOtherKey", "value");

            when(koboServerProxy.proxyCurrentRequest(null, false))
                    .thenReturn(ResponseEntity.ok(body));

            ObjectNode fallbackResources = objectMapper.createObjectNode();
            fallbackResources.put("example", "https://storeapi.kobo.com/v1/example");
            when(koboResourcesComponent.getResources()).thenReturn(fallbackResources);

            ResponseEntity<KoboResources> response = service.initialize("test-token");

            verify(koboResourcesComponent).getResources();
            assertEquals("https://storeapi.kobo.com/v1/example",
                    response.getBody().getResources().get("example").asText());
        }

        @Test
        @DisplayName("Should fall back when proxy returns null response")
        void initialize_fallsBackOnNullResponse() throws Exception {
            when(koboServerProxy.proxyCurrentRequest(null, false)).thenReturn(null);

            ObjectNode fallbackResources = objectMapper.createObjectNode();
            fallbackResources.put("library_sync", "https://storeapi.kobo.com/v1/library/sync");
            when(koboResourcesComponent.getResources()).thenReturn(fallbackResources);

            ResponseEntity<KoboResources> response = service.initialize("test-token");

            assertEquals(200, response.getStatusCode().value());
            verify(koboResourcesComponent).getResources();
        }
    }

    @Nested
    @DisplayName("Response Headers")
    class ResponseHeaders {

        @Test
        @DisplayName("Should include x-kobo-apitoken header")
        void initialize_includesApiTokenHeader() throws Exception {
            ObjectNode resources = objectMapper.createObjectNode();
            ObjectNode body = objectMapper.createObjectNode();
            body.set("Resources", resources);

            when(koboServerProxy.proxyCurrentRequest(null, false))
                    .thenReturn(ResponseEntity.ok(body));

            ResponseEntity<KoboResources> response = service.initialize("test-token");

            assertEquals("e30=", response.getHeaders().getFirst("x-kobo-apitoken"));
        }
    }
}
