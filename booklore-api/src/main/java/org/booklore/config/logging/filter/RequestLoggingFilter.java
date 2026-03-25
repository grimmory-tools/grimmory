package org.booklore.config.logging.filter;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

import java.util.Set;

public class RequestLoggingFilter extends CommonsRequestLoggingFilter {
    Set<String> excludedPaths = Set.of(
            "/api/v1/healthcheck",
            "/ws"
    );

    @Override
    protected boolean shouldLog(HttpServletRequest request) {
        if (excludedPaths.contains(request.getRequestURI())) {
            return false;
        }

        return super.shouldLog(request);
    }
}
