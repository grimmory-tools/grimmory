package org.booklore.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Shared configuration for the Hardcover GraphQL API {@link RestClient}.
 * <p>
 * Centralises the base URL, connect / read timeouts and request factory so that
 * every service that talks to Hardcover reuses the same, pre-configured client.
 */
@Configuration
public class HardcoverClientConfig {

    static final String HARDCOVER_API_URL = "https://api.hardcover.app/v1/graphql";
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    @Bean
    public RestClient hardcoverRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);

        return RestClient.builder()
                .baseUrl(HARDCOVER_API_URL)
                .requestFactory(factory)
                .build();
    }
}
