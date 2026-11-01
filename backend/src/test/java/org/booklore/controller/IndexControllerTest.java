package org.booklore.controller;

import jakarta.servlet.ServletContext;
import org.booklore.config.SpaContentProvider;
import org.booklore.config.WebMvcConfig;
import org.booklore.interceptor.KomgaCleanInterceptor;
import org.booklore.interceptor.KomgaEnabledInterceptor;
import org.booklore.interceptor.OpdsEnabledInterceptor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class IndexControllerTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "/grimmory", "/library.v1/books"})
    void servesConsistentSpaAndServiceWorkerResources(String prefix) throws Exception {
        var servletContext = new MockServletContext();
        servletContext.setContextPath(prefix);
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(servletContext);
            context.register(TestConfig.class);
            context.refresh();
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();

            byte[] index = mvc.perform(get(prefix + "/").contextPath(prefix))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("text/html"))
                    .andExpect(header().string("Cache-Control", "no-store, no-transform"))
                    .andReturn().getResponse().getContentAsByteArray();
            assertThat(new String(index, StandardCharsets.UTF_8)).contains("<base href=\"" + prefix + "/\">");
            for (String route : new String[]{"/index.html", "/library/42/books"}) {
                mvc.perform(get(prefix + route).contextPath(prefix).accept("text/html"))
                        .andExpect(status().isOk())
                        .andExpect(content().bytes(index));
            }
            // Route parameters may contain dots; they are not asset extensions.
            mvc.perform(get(prefix + "/series/Dr.%20Who").contextPath(prefix).accept("text/html"))
                    .andExpect(status().isOk()).andExpect(content().bytes(index));
            mvc.perform(get(prefix + "/manifest.webmanifest").contextPath(prefix))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("application/manifest+json"))
                    .andExpect(content().json("{\"start_url\":\"./\",\"scope\":\"./\"}"));

            String json = mvc.perform(get(prefix + "/ngsw.json").contextPath(prefix))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store, no-transform"))
                    .andReturn().getResponse().getContentAsString();
            var manifest = new ObjectMapper().readTree(json);
            assertThat(manifest.path("index").asText()).isEqualTo(prefix + "/index.html");
            assertThat(manifest.path("assetGroups").get(0).path("urls").get(0).asText())
                    .isEqualTo(prefix + "/index.html");
            assertThat(manifest.path("hashTable").path(prefix + "/manifest.webmanifest").asText())
                    .isEqualTo("unchanged-manifest-hash");
            if (!prefix.isEmpty()) {
                assertThat(manifest.path("hashTable").path(prefix + "/index.html").asText())
                        .isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(index)));
                assertThat(manifest.path("hashTable").has("/index.html")).isFalse();
            }
            // Angular tests navigation URLs after removing the registration scope,
            // so these paths have no deployment prefix, even for subpath deployments.
            for (String route : new String[]{"/dashboard", "/library/42/books"}) {
                assertThat(matchesNavigation(manifest, route)).as(route).isTrue();
            }
            for (String excluded : new String[]{"/api/v1/books", "/komga/api/v1/series", "/assets/embedpdf-frame.html"}) {
                assertThat(matchesNavigation(manifest, excluded)).as(excluded).isFalse();
            }
            assertThat(Pattern.matches(manifest.path("assetGroups").get(0).path("patterns").get(0).asText(),
                    prefix + "/assets/example.png")).isTrue();
            assertThat(manifest.path("assetGroups").get(0).path("patterns").get(1).asText())
                    .isEqualTo("https://cdn.example.com/.*");
            assertThat(Pattern.matches(manifest.path("dataGroups").get(0).path("patterns").get(0).asText(),
                    prefix + "/api/public/example")).isTrue();
            if (!prefix.isEmpty()) {
                String dataPattern = manifest.path("dataGroups").get(0).path("patterns").get(0).asText();
                assertThat(Pattern.matches(dataPattern, "/api/public/example")).isFalse();
                assertThat(Pattern.matches(dataPattern, prefix.replace('.', 'X') + "/api/public/example"))
                        .isEqualTo(!prefix.contains("."));
            }
        }
    }

    private boolean matchesNavigation(JsonNode manifest, String scopeRelativePath) {
        boolean included = false;
        for (JsonNode navigation : manifest.path("navigationUrls")) {
            if (Pattern.compile(navigation.path("regex").asText()).matcher(scopeRelativePath).find()) {
                if (!navigation.path("positive").asBoolean()) {
                    return false;
                }
                included = true;
            }
        }
        return included;
    }

    @Configuration
    @EnableWebMvc
    static class TestConfig {
        @Bean
        SpaContentProvider spaContentProvider(ServletContext servletContext) {
            return new SpaContentProvider(servletContext, new DefaultResourceLoader() {
                @Override
                public Resource getResource(String location) {
                    return super.getResource(location.replace("/static/", "/spa-test/"));
                }
            });
        }

        @Bean
        IndexController indexController(SpaContentProvider provider) {
            return new IndexController(provider);
        }

        @Bean
        WebMvcConfig webMvcConfig(SpaContentProvider provider) {
            return new WebMvcConfig(mock(OpdsEnabledInterceptor.class), mock(KomgaEnabledInterceptor.class),
                    mock(KomgaCleanInterceptor.class), provider);
        }
    }
}
