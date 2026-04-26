package org.grimmory.config.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import org.booklore.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(OpenApiConfig.class, TestConfiguration.class);

    @Test
    void shouldPublishConfiguredApiMetadata() {
        contextRunner
                .withPropertyValues("app.api-docs.enabled=true")
                .run(context -> {
                    assertThat(context.getBeansOfType(OpenAPI.class)).hasSize(1);

                    OpenAPI openAPI = context.getBean(OpenAPI.class);
                    assertThat(openAPI.getInfo().getTitle()).isEqualTo("Grimmory API");
                    assertThat(openAPI.getInfo().getDescription())
                            .isEqualTo("REST API documentation for managing libraries, readers, metadata, and device integrations in Grimmory.");
                    assertThat(openAPI.getInfo().getVersion()).isEqualTo("9.9.9-test");
                    assertThat(openAPI.getInfo().getContact().getName()).isEqualTo("Grimmory");
                    assertThat(openAPI.getInfo().getContact().getUrl())
                            .isEqualTo("https://github.com/grimmory-tools/grimmory");
                    assertThat(openAPI.getInfo().getLicense().getName()).isEqualTo("AGPL-3.0");
                    assertThat(openAPI.getInfo().getLicense().getUrl())
                            .isEqualTo("https://www.gnu.org/licenses/agpl-3.0.html");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfiguration {

        @Bean
        AppProperties appProperties() {
            AppProperties appProperties = new AppProperties();
            appProperties.setVersion("9.9.9-test");
            return appProperties;
        }
    }
}
