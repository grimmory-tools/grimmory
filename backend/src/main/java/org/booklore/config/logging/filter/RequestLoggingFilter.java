package org.booklore.config.logging.filter;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

import java.util.Set;
import java.util.function.Predicate;

public class RequestLoggingFilter extends CommonsRequestLoggingFilter {
    private Predicate<String> pathPredicate = null;

    public void setPathPredicate(@Nullable Predicate<String> pathPredicate) {
        this.pathPredicate = pathPredicate;
    }

    @Override
    protected boolean shouldLog(HttpServletRequest request) {
        if (pathPredicate != null && !pathPredicate.test(request.getRequestURI())) {
            return false;
        }

        return super.shouldLog(request);
    }
}
