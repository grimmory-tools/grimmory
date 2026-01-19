package com.adityachandel.booklore.interceptor;

import com.adityachandel.booklore.service.appsettings.AppSettingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class KomgaEnabledInterceptor implements HandlerInterceptor {

    private final AppSettingService appSettingService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        
        if (uri.startsWith("/komga/api/")) {
            boolean komgaEnabled = appSettingService.getAppSettings().isKomgaApiEnabled();
            if (!komgaEnabled) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Komga API is disabled.");
                return false;
            }
        }
        return true;
    }
}