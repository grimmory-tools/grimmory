package org.grimmory.config.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Enables SharedArrayBuffer by setting Cross-Origin isolation headers.
 * Required by pdfium WASM (Emscripten-compiled with thread support) used by EmbedPDF.
 * Without these headers the WASM module stalls on instantiation.
 */
@Component
@Order(1)
public class SharedArrayBufferHeaderFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        response.setHeader("Cross-Origin-Embedder-Policy", "credentialless");
        filterChain.doFilter(request, response);
    }
}
