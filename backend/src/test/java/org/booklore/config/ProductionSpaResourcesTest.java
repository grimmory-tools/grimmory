package org.booklore.config;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.mock.web.MockServletContext;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/** Run after build:prod; processResources packages that build just as bootJar does. */
@EnabledIfEnvironmentVariable(named = "RUN_PRODUCTION_SPA_TESTS", matches = "true")
class ProductionSpaResourcesTest {
    @ParameterizedTest
    @ValueSource(strings = {"", "/grimmory", "/apps/books"})
    void everyServiceWorkerHashMatchesTheServedProductionBytes(String prefix) throws Exception {
        var context = new MockServletContext();
        context.setContextPath(prefix);
        var loader = new DefaultResourceLoader();
        var provider = new SpaContentProvider(context, loader);
        assertThat(provider.getIndexHtmlResource().getContentAsString(StandardCharsets.UTF_8))
                .contains("<base href=\"" + prefix + "/\">");
        var manifest = new ObjectMapper().readTree(provider.getNgswResource().getContentAsByteArray());
        assertThat(manifest.path("index").asText()).isEqualTo(prefix + "/index.html");
        assertThat(manifest.path("hashTable").size()).isGreaterThan(10);
        for (var entry : manifest.path("hashTable").properties()) {
            assertThat(entry.getKey()).startsWith(prefix + "/");
            String path = entry.getKey().substring(prefix.length());
            var resource = path.equals("/index.html") ? provider.getIndexHtmlResource()
                    : loader.getResource("classpath:/static" + path);
            assertThat(resource.exists()).as(entry.getKey()).isTrue();
            assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1")
                    .digest(resource.getContentAsByteArray())))
                    .as(entry.getKey()).isEqualTo(entry.getValue().asText());
        }
        for (var group : manifest.path("assetGroups")) {
            for (var url : group.path("urls")) {
                assertThat(url.asText()).startsWith(prefix + "/");
                assertThat(manifest.path("hashTable").has(url.asText())).isTrue();
            }
        }
    }
}
