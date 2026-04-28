package org.booklore.acquisition;

import org.booklore.service.acquisition.UrlValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlValidatorTest {

    private final UrlValidator validator = new UrlValidator();

    // ─── Valid URLs ───────────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "http://indexer.example.com",
            "https://indexer.example.com/api",
            "http://192.168.1.201",
            "http://192.168.1.201:9117",
            "http://10.0.0.5:8080",
            "http://172.16.0.1",
            "http://localhost:6060",
            "http://sabnzbd.local:8080/sabnzbd",
    })
    void validUrls_doNotThrow(String url) {
        assertThatCode(() -> validator.validateOutboundUrl(url)).doesNotThrowAnyException();
    }

    // ─── LAN/private IPs are explicitly allowed (self-hosted use case) ────────

    @Test
    void privateIp_192168_isAllowed() {
        assertThatCode(() -> validator.validateOutboundUrl("http://192.168.1.100:9117"))
                .doesNotThrowAnyException();
    }

    @Test
    void privateIp_10x_isAllowed() {
        assertThatCode(() -> validator.validateOutboundUrl("http://10.0.0.1:8080"))
                .doesNotThrowAnyException();
    }

    @Test
    void privateIp_172x_isAllowed() {
        assertThatCode(() -> validator.validateOutboundUrl("http://172.16.0.1:9117"))
                .doesNotThrowAnyException();
    }

    // ─── Invalid schemes ──────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "ftp://indexer.example.com",
            "file:///etc/passwd",
            "javascript:alert(1)",
    })
    void nonHttpScheme_throws(String url) {
        assertThatThrownBy(() -> validator.validateOutboundUrl(url))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http");
    }

    // ─── Malformed URLs ───────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "not-a-url",
            "http://",
    })
    void malformedUrl_throws(String url) {
        assertThatThrownBy(() -> validator.validateOutboundUrl(url))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
