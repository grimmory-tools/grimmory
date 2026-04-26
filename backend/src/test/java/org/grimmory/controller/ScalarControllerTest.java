package org.grimmory.controller;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ScalarControllerTest {

    private final ScalarController controller = new ScalarController();

    @Test
    void shouldForwardToStaticScalarPage() {
        assertThat(controller.scalar()).isEqualTo("forward:/scalar.html");
    }

    @Test
    void staticScalarPageShouldDisableTelemetryAndPointToApiDocs() throws IOException {
        String html = StreamUtils.copyToString(
                new ClassPathResource("static/scalar.html").getInputStream(),
                StandardCharsets.UTF_8
        );
        assertThat(html).contains("data-url=\"/api/openapi.json\"");
        assertThat(html).contains("\"telemetry\":false");
    }
}
