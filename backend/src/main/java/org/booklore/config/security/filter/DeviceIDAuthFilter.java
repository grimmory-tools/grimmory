package org.booklore.config.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.booklore.model.dto.kobo.KoboHeaders;
import org.booklore.model.entity.KoboUserSettingsEntity;
import org.booklore.repository.KoboUserSettingsRepository;
import org.springframework.boot.web.servlet.FilterRegistration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@FilterRegistration(enabled = false)
public class DeviceIDAuthFilter extends OncePerRequestFilter {

    private final KoboUserSettingsRepository koboUserSettingsRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String deviceId = request.getHeader(KoboHeaders.X_KOBO_DEVICEID);

        if (deviceId == null || deviceId.isBlank()) {
            log.warn("Reading services request missing {} header", KoboHeaders.X_KOBO_DEVICEID);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Device ID missing");
            return;
        }

        if (!isDeviceIdAllowed(deviceId)) {
            log.warn("Reading services request with unrecognized device ID");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid device ID");
            return;
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "kobo-device", null, List.of(new SimpleGrantedAuthority("ROLE_DEVICE"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private boolean isDeviceIdAllowed(String deviceId) {
        List<KoboUserSettingsEntity> settingsWithDeviceIds = koboUserSettingsRepository.findByAllowedDeviceIdsIsNotNull();
        return settingsWithDeviceIds.stream()
                .flatMap(settings -> Arrays.stream(settings.getAllowedDeviceIds().split(",")))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .anyMatch(id -> id.equals(deviceId));
    }
}
