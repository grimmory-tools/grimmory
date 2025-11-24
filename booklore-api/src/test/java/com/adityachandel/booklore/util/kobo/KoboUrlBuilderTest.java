package com.adityachandel.booklore.util.kobo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.*;

class KoboUrlBuilderTest {

    private KoboUrlBuilder koboUrlBuilder;
    private MockHttpServletRequest mockRequest;

    @BeforeEach
    void setUp() {
        // Mock the request attributes
        mockRequest = new MockHttpServletRequest();
        mockRequest.setScheme("http");
        mockRequest.setServerName("localhost");
        mockRequest.setServerPort(8080);
        mockRequest.setContextPath("");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockRequest));

        // Manually instantiate KoboUrlBuilder
        koboUrlBuilder = new KoboUrlBuilder();
        // Set the @Value field using ReflectionTestUtils
        ReflectionTestUtils.setField(koboUrlBuilder, "serverPort", 8080);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void testDownloadUrl() {
        String token = "testToken";
        Long bookId = 123L;

        String result = koboUrlBuilder.downloadUrl(token, bookId);

        assertNotNull(result);
        assertTrue(result.contains("/api/kobo/" + token + "/v1/books/" + bookId + "/download"),
                "URL should contain the expected path segments");
        assertTrue(result.startsWith("http://"), "URL should start with http://");
    }

    @Test
    void testImageUrlTemplate() {
        String token = "testToken";

        String result = koboUrlBuilder.imageUrlTemplate(token);

        assertNotNull(result);
        assertTrue(result.contains("/api/kobo/" + token + "/v1/books/"),
                "URL should contain the expected path segments");
        assertTrue(result.contains("{ImageId}"), "URL should contain ImageId placeholder");
        assertTrue(result.contains("{Width}"), "URL should contain Width placeholder");
        assertTrue(result.contains("{Height}"), "URL should contain Height placeholder");
        assertTrue(result.contains("image.jpg"), "URL should end with image.jpg");
    }

    @Test
    void testImageUrlQualityTemplate() {
        String token = "testToken";

        String result = koboUrlBuilder.imageUrlQualityTemplate(token);

        assertNotNull(result);
        assertTrue(result.contains("/api/kobo/" + token + "/v1/books/"),
                "URL should contain the expected path segments");
        assertTrue(result.contains("{ImageId}"), "URL should contain ImageId placeholder");
        assertTrue(result.contains("{Width}"), "URL should contain Width placeholder");
        assertTrue(result.contains("{Height}"), "URL should contain Height placeholder");
        assertTrue(result.contains("{Quality}"), "URL should contain Quality placeholder");
        assertTrue(result.contains("{IsGreyscale}"), "URL should contain IsGreyscale placeholder");
        assertTrue(result.contains("image.jpg"), "URL should end with image.jpg");
    }

    @Test
    void testDownloadUrlWithXForwardedPort() {
        // Set X-Forwarded-Port header
        mockRequest.addHeader("X-Forwarded-Port", "443");

        String token = "testToken";
        Long bookId = 123L;

        String result = koboUrlBuilder.downloadUrl(token, bookId);

        assertNotNull(result);
        assertTrue(result.contains("/api/kobo/" + token + "/v1/books/" + bookId + "/download"),
                "URL should contain the expected path segments");
    }

    @Test
    void testDownloadUrlWithInvalidXForwardedPort() {
        // Set invalid X-Forwarded-Port header
        mockRequest.addHeader("X-Forwarded-Port", "invalid");

        String token = "testToken";
        Long bookId = 123L;

        String result = koboUrlBuilder.downloadUrl(token, bookId);

        assertNotNull(result);
        assertTrue(result.contains("/api/kobo/" + token + "/v1/books/" + bookId + "/download"),
                "URL should contain the expected path segments even with invalid port header");
    }

    @Test
    void testUrlBuilderWithIpAddress() {
        // Test with IP address instead of localhost
        mockRequest.setServerName("192.168.1.100");
        mockRequest.addHeader("X-Forwarded-Port", "8443");

        String token = "testToken";
        Long bookId = 123L;

        String result = koboUrlBuilder.downloadUrl(token, bookId);

        assertNotNull(result);
        assertTrue(result.contains("/api/kobo/" + token + "/v1/books/" + bookId + "/download"),
                "URL should contain the expected path segments");
    }
}
