package org.booklore.config.logging;

import org.booklore.config.logging.filter.RequestLoggingFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.GenericFilterBean;

@Configuration
public class RequestLoggingFilterConfig {

    @Bean
    public GenericFilterBean logFilter() {
        RequestLoggingFilter filter = new RequestLoggingFilter();

        filter.setIncludeQueryString(true);
        filter.setIncludePayload(true);
        filter.setMaxPayloadLength(10000);
        filter.setIncludeHeaders(true);

        return filter;
    }
}