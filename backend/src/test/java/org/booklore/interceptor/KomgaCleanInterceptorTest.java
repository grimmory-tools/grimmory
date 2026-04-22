package org.booklore.interceptor;

import org.booklore.context.KomgaCleanContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class KomgaCleanInterceptorTest {

    private KomgaCleanInterceptor interceptor;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        interceptor = new KomgaCleanInterceptor();
    }

    @AfterEach
    void cleanup() {
        KomgaCleanContext.clear();
    }

    @Test
    void shouldEnableCleanModeWithParameterWithoutValue() throws Exception {
        // Given: Request with ?clean (no value)
        when(request.getRequestURI()).thenReturn("/komga/api/v1/series");
        when(request.getParameter("clean")).thenReturn("");  // Empty string means parameter present without value

        // When: Interceptor processes request
        interceptor.preHandle(request, response, new Object());

        // Then: Clean mode should be enabled
        assertThat(KomgaCleanContext.isCleanMode()).isTrue();
    }

    @Test
    void shouldEnableCleanModeWithParameterSetToTrue() throws Exception {
        // Given: Request with ?clean=true
        when(request.getRequestURI()).thenReturn("/komga/api/v1/series");
        when(request.getParameter("clean")).thenReturn("true");

        // When: Interceptor processes request
        interceptor.preHandle(request, response, new Object());

        // Then: Clean mode should be enabled
        assertThat(KomgaCleanContext.isCleanMode()).isTrue();
    }

    @Test
    void shouldEnableCleanModeWithParameterSetToTrueCaseInsensitive() throws Exception {
        // Given: Request with ?clean=TRUE
        when(request.getRequestURI()).thenReturn("/komga/api/v1/books/123");
        when(request.getParameter("clean")).thenReturn("TRUE");

        // When: Interceptor processes request
        interceptor.preHandle(request, response, new Object());

        // Then: Clean mode should be enabled
        assertThat(KomgaCleanContext.isCleanMode()).isTrue();
    }

    @Test
    void shouldNotEnableCleanModeWhenParameterAbsent() throws Exception {
        // Given: Request without clean parameter
        when(request.getRequestURI()).thenReturn("/komga/api/v1/series");
        when(request.getParameter("clean")).thenReturn(null);

        // When: Interceptor processes request
        interceptor.preHandle(request, response, new Object());

        // Then: Clean mode should not be enabled
        assertThat(KomgaCleanContext.isCleanMode()).isFalse();
    }

    @Test
    void shouldNotEnableCleanModeWhenParameterSetToFalse() throws Exception {
        // Given: Request with ?clean=false
        when(request.getRequestURI()).thenReturn("/komga/api/v1/series");
        when(request.getParameter("clean")).thenReturn("false");

        // When: Interceptor processes request
        interceptor.preHandle(request, response, new Object());

        // Then: Clean mode should not be enabled
        assertThat(KomgaCleanContext.isCleanMode()).isFalse();
    }

    @Test
    void shouldNotApplyToNonKomgaEndpoints() throws Exception {
        // Given: Request to non-Komga endpoint with clean parameter
        when(request.getRequestURI()).thenReturn("/api/v1/books");
        when(request.getParameter("clean")).thenReturn("true");

        // When: Interceptor processes request
        interceptor.preHandle(request, response, new Object());

        // Then: Clean mode should not be enabled
        assertThat(KomgaCleanContext.isCleanMode()).isFalse();
    }

    @Test
    void shouldClearContextAfterCompletion() throws Exception {
        // Given: Clean mode is enabled
        KomgaCleanContext.setCleanMode(true);
        assertThat(KomgaCleanContext.isCleanMode()).isTrue();

        // When: After completion is called
        interceptor.afterCompletion(request, response, new Object(), null);

        // Then: Context should be cleared
        assertThat(KomgaCleanContext.isCleanMode()).isFalse();
    }
}
