package org.booklore.interceptor;

import org.booklore.model.dto.settings.AppSettings;
import org.booklore.service.appsettings.AppSettingService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubpathInterceptorTest {
    @ParameterizedTest
    @ValueSource(strings = {"", "/grimmory"})
    void disabledDeviceApisRemainForbiddenInsideContextPath(String prefix) throws Exception {
        var settings = new AppSettings();
        settings.setOpdsServerEnabled(false);
        settings.setKomgaApiEnabled(false);
        var service = mock(AppSettingService.class);
        when(service.getAppSettings()).thenReturn(settings);

        var opdsResponse = new MockHttpServletResponse();
        assertThat(new OpdsEnabledInterceptor(service).preHandle(request(prefix, "/api/v1/opds"), opdsResponse, new Object()))
                .isFalse();
        assertThat(opdsResponse.getStatus()).isEqualTo(403);

        var komgaResponse = new MockHttpServletResponse();
        assertThat(new KomgaEnabledInterceptor(service).preHandle(request(prefix, "/komga/api/v1/series"), komgaResponse, new Object()))
                .isFalse();
        assertThat(komgaResponse.getStatus()).isEqualTo(403);

        settings.setOpdsServerEnabled(true);
        settings.setKomgaApiEnabled(true);
        assertThat(new OpdsEnabledInterceptor(service).preHandle(request(prefix, "/api/v1/opds"), new MockHttpServletResponse(), new Object()))
                .isTrue();
        assertThat(new KomgaEnabledInterceptor(service).preHandle(request(prefix, "/komga/api/v1/series"), new MockHttpServletResponse(), new Object()))
                .isTrue();
    }

    private MockHttpServletRequest request(String prefix, String path) {
        var request = new MockHttpServletRequest();
        request.setContextPath(prefix);
        request.setServletPath(path);
        request.setRequestURI(prefix + path);
        return request;
    }
}
