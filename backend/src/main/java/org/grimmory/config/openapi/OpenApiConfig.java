package org.grimmory.config.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import lombok.RequiredArgsConstructor;
import org.grimmory.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.api-docs.enabled", havingValue = "true", matchIfMissing = false)
public class OpenApiConfig {

    private final AppProperties appProperties;

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Grimmory API")
                        .description("REST API documentation for managing libraries, readers, metadata, and device integrations in Grimmory.")
                        .version(appProperties.getVersion())
                        .contact(new Contact()
                                .name("Grimmory")
                                .url("https://github.com/grimmory-tools/grimmory"))
                        .license(new License()
                                .name("AGPL-3.0")
                                .url("https://www.gnu.org/licenses/agpl-3.0.html")));
    }
}
