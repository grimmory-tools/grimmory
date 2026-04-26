package org.grimmory.util.kobo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.*;

class KoboUrlBuilderTest {

    private KoboUrlBuilder koboUrlBuilder;
    private MockHttpServletRequest mockRequest;

    @BeforeEach
    void setUp() {
        mockRequest = new MockHttpServletRequest();
        mockRequest.setScheme("http");
        mockRequest.setServerName("localhost");
        mockRequest.setServerPort(6060);
        mockRequest.setContextPath("");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockRequest));

        koboUrlBuilder = new KoboUrlBuilder();
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void testDownloadUrl() {
        String expected = "http://localhost:6060/api/kobo/testToken/v1/books/123/download";

        String actual = koboUrlBuilder.downloadUrl("testToken", 123L);

        assertEquals(expected, actual);
    }

    @Test
    void testImageUrlTemplate() {
        String expected = "http://localhost:6060/api/kobo/testToken/v1/books/{ImageId}/thumbnail/{Width}/{Height}/false/image.jpg";

        String actual = koboUrlBuilder.imageUrlTemplate("testToken");

        assertEquals(expected, actual);
    }

    @Test
    void testImageUrlQualityTemplate() {
        String expected = "http://localhost:6060/api/kobo/testToken/v1/books/{ImageId}/thumbnail/{Width}/{Height}/{Quality}/{IsGreyscale}/image.jpg";

        String actual = koboUrlBuilder.imageUrlQualityTemplate("testToken");

        assertEquals(expected, actual);
    }

    @Test
    void testUrlBuilderWithIpAddress() {
        mockRequest.setServerName("192.168.1.100");
        mockRequest.setServerPort(6060);

        String expected = "http://192.168.1.100:6060/api/kobo/testToken";

        String actual = koboUrlBuilder.withBaseUrl("testToken");

        assertEquals(expected, actual);
    }

    @Test
    void testUrlBuilderWithDomainName() {
        mockRequest.setScheme("https");
        mockRequest.setServerName("books.example.com");
        mockRequest.setServerPort(443);
        mockRequest.setLocalPort(443);

        String expected = "https://books.example.com/api/kobo/testToken";

        String actual = koboUrlBuilder.withBaseUrl("testToken");

        assertEquals(expected, actual);
    }

    @Test
    void testHostHeaderWithoutPort_restoresLocalPort() {
        // Kobo devices send "Host: 192.168.1.100" without port.
        // Tomcat resolves missing port to 80 (HTTP default), which Spring strips.
        // The fix should restore the actual listening port from request.getLocalPort().
        mockRequest.setServerName("192.168.1.100");
        mockRequest.setServerPort(80);
        mockRequest.setLocalPort(6060);

        String expected = "http://192.168.1.100:6060/api/kobo/testToken";

        String actual = koboUrlBuilder.withBaseUrl("testToken");

        assertEquals(expected, actual);
    }

    @Test
    void testHostHeaderWithoutPort_imageUrls() {
        mockRequest.setServerName("192.168.1.100");
        mockRequest.setServerPort(80);
        mockRequest.setLocalPort(6060);

        String expected = "http://192.168.1.100:6060/api/kobo/testToken";

        String actual = koboUrlBuilder.withBaseUrl("testToken");

        assertEquals(expected, actual);
    }

    @Test
    void testReverseProxy_xForwardedProto_doesNotOverridePort() {
        mockRequest.setScheme("https");
        mockRequest.setServerName("books.example.com");
        mockRequest.setServerPort(443);
        mockRequest.setLocalPort(6060);
        mockRequest.addHeader("X-Forwarded-Proto", "https");

        String expected = "https://books.example.com/api/kobo/testToken";

        String actual = koboUrlBuilder.withBaseUrl("testToken");

        assertEquals(expected, actual);
    }

    @Test
    void testReverseProxy_xForwardedHost_doesNotOverridePort() {
        mockRequest.setScheme("https");
        mockRequest.setServerName("books.example.com");
        mockRequest.setServerPort(443);
        mockRequest.setLocalPort(6060);
        mockRequest.addHeader("X-Forwarded-Host", "books.example.com");

        String expected = "https://books.example.com/api/kobo/testToken";

        String actual = koboUrlBuilder.withBaseUrl("testToken");

        assertEquals(expected, actual);
    }

    @Test
    void testReverseProxy_xForwardedPort_doesNotOverridePort() {
        mockRequest.setScheme("https");
        mockRequest.setServerName("books.example.com");
        mockRequest.setServerPort(443);
        mockRequest.setLocalPort(6060);
        mockRequest.addHeader("X-Forwarded-Port", "443");

        String expected = "https://books.example.com/api/kobo/testToken";

        String actual = koboUrlBuilder.withBaseUrl("testToken");

        assertEquals(expected, actual);
    }

    @Test
    void testReverseProxy_forwardedHeader_doesNotOverridePort() {
        mockRequest.setScheme("https");
        mockRequest.setServerName("books.example.com");
        mockRequest.setServerPort(443);
        mockRequest.setLocalPort(6060);
        mockRequest.addHeader("Forwarded", "proto=https;host=books.example.com");

        String expected = "https://books.example.com/api/kobo/testToken";

        String actual = koboUrlBuilder.withBaseUrl("testToken");

        assertEquals(expected, actual);
    }

    @Test
    void testReverseProxy_nonStandardPort_preserved() {
        mockRequest.setScheme("https");
        mockRequest.setServerName("books.example.com");
        mockRequest.setServerPort(8443);
        mockRequest.setLocalPort(6060);
        mockRequest.addHeader("X-Forwarded-Port", "8443");

        String expected = "https://books.example.com:8443/api/kobo/testToken";

        String actual = koboUrlBuilder.withBaseUrl("testToken");

        assertEquals(expected, actual);
    }

    @Test
    void testServerOnPort80_noPortAdded() {
        mockRequest.setServerName("192.168.1.100");
        mockRequest.setServerPort(80);
        mockRequest.setLocalPort(80);

        String expected = "http://192.168.1.100/api/kobo/testToken";

        String actual = koboUrlBuilder.withBaseUrl("testToken");

        assertEquals(expected, actual);
    }

    @Test
    void testServerOnPort443_noPortAdded() {
        mockRequest.setScheme("https");
        mockRequest.setServerName("192.168.1.100");
        mockRequest.setServerPort(443);
        mockRequest.setLocalPort(443);


        String expected = "https://192.168.1.100/api/kobo/testToken";

        String actual = koboUrlBuilder.withBaseUrl("testToken");

        assertEquals(expected, actual);
    }
}
