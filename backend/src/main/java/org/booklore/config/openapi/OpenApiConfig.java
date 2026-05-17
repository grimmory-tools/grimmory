package org.booklore.config.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import lombok.RequiredArgsConstructor;
import org.booklore.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.api-docs.enabled", havingValue = "true", matchIfMissing = false)
public class OpenApiConfig {

    static final String API_DESCRIPTION = """
            > [!warning]
            > This documentation is auto-generated and public, but the API is unstable; to join the conversation around changes to the API, visit our [GitHub](https://github.com/grimmory-tools/grimmory) or [Discord](https://discord.gg/9YJ7HB4n8T).

            REST API documentation for managing libraries, readers, metadata, and device integrations in Grimmory.
            """;

    private final AppProperties appProperties;

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Grimmory API")
                        .description(API_DESCRIPTION)
                        .version(appProperties.getVersion())
                        .contact(new Contact()
                                .name("Grimmory")
                                .url("https://github.com/grimmory-tools/grimmory"))
                        .license(new License()
                                .name("AGPL-3.0")
                                .url("https://www.gnu.org/licenses/agpl-3.0.html")));
    }
}
