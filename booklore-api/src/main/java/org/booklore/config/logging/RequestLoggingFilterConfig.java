package org.booklore.config.logging;

import org.booklore.config.logging.filter.RequestLoggingFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.GenericFilterBean;

import java.util.Set;

@Configuration
public class RequestLoggingFilterConfig {
    private static final Set<String> EXCLUDED_PATHS = Set.of(
            "/api/v1/healthcheck",
            "/ws"
    );

    private static final Set<String> EXCLUDED_HEADERS = Set.of(
            "authorization",
            "cookie",
            "set-cookie",
            "x-api-key",
            "x-forwarded-access-token"
    );

    @Bean
    public GenericFilterBean logFilter() {
        RequestLoggingFilter filter = new RequestLoggingFilter();

        filter.setIncludeQueryString(true);
        filter.setIncludePayload(true);
        filter.setMaxPayloadLength(10000);
        filter.setIncludeHeaders(true);

        filter.setHeaderPredicate((header) -> !EXCLUDED_HEADERS.contains(header.toLowerCase()));
        filter.setPathPredicate((path) -> !EXCLUDED_PATHS.contains(path));

        return filter;
    }
}