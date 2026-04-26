package org.grimmory.config.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import org.grimmory.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiDocsDisabledIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(OpenApiConfig.class, TestConfiguration.class);

    @Test
    void shouldNotRegisterOpenApiBeanWhenDocsAreDisabled() {
        contextRunner
                .withPropertyValues("app.api-docs.enabled=false")
                .run(context -> assertThat(context.getBeansOfType(OpenAPI.class)).isEmpty());
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
