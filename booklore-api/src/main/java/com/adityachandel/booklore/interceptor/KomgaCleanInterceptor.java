package com.adityachandel.booklore.interceptor;

import com.adityachandel.booklore.context.KomgaCleanContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptor to handle the "clean" query parameter for Komga API endpoints.
 * When the "clean" parameter is present (with or without a value) or set to "true",
 * it enables clean mode which filters out "Lock" fields, null values, and empty arrays
 * from the JSON response. Supports both ?clean and ?clean=true syntax.
 */
@Component
public class KomgaCleanInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestURI = request.getRequestURI();
        
        // Only apply to Komga API endpoints
        if (requestURI != null && requestURI.startsWith("/komga/api")) {
            String cleanParam = request.getParameter("clean");
            // Enable clean mode if parameter is present (even without value) or explicitly set to "true"
            // Supports both ?clean and ?clean=true
            boolean cleanMode = cleanParam != null && (cleanParam.isEmpty() || "true".equalsIgnoreCase(cleanParam));
            KomgaCleanContext.setCleanMode(cleanMode);
        }
        
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // Clean up ThreadLocal to prevent memory leaks
        KomgaCleanContext.clear();
    }
}
