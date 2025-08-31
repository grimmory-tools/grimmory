package com.adityachandel.booklore.interceptor;

import com.adityachandel.booklore.service.appsettings.AppSettingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class OpdsEnabledInterceptor implements HandlerInterceptor {

    private final AppSettingService appSettingService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/v1/opds") || uri.startsWith("/api/v2/opds")) {
            if (!appSettingService.getAppSettings().isOpdsServerEnabled()) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "OPDS Server is disabled.");
                return false;
            }
        }
        return true;
    }
}
