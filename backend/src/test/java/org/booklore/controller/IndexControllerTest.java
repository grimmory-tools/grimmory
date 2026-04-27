package org.booklore.controller;

import org.booklore.config.SpaContentProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IndexControllerTest {

    @Test
    void rewriteIndexHtml_replacesBaseHref() throws Exception {
        // Use reflection to test private method
        var mockServletContext = new org.springframework.mock.web.MockServletContext();
        mockServletContext.setContextPath("/grimmory");

        var provider = new SpaContentProvider(mockServletContext);

        var method = SpaContentProvider.class.getDeclaredMethod("rewriteIndexHtml", String.class);
        method.setAccessible(true);

        String original = "<base href=\"/\">";
        String result = (String) method.invoke(provider, original);

        assertThat(result).isEqualTo("<base href=\"/grimmory/\">");
    }

    @Test
    void rewriteManifest_updatesStartUrlAndScope() throws Exception {
        var mockServletContext = new org.springframework.mock.web.MockServletContext();
        mockServletContext.setContextPath("/grimmory");

        var provider = new SpaContentProvider(mockServletContext);

        var method = SpaContentProvider.class.getDeclaredMethod("rewriteManifest", String.class);
        method.setAccessible(true);

        String original = "{\"start_url\": \"./\", \"scope\": \"./\"}";
        String result = (String) method.invoke(provider, original);

        assertThat(result).contains("\"start_url\": \"/grimmory/\"");
        assertThat(result).contains("\"scope\": \"/grimmory/\"");
    }

    @Test
    void rewriteNgsw_prefixesAbsolutePaths() throws Exception {
        var mockServletContext = new org.springframework.mock.web.MockServletContext();
        mockServletContext.setContextPath("/grimmory");

        var provider = new SpaContentProvider(mockServletContext);

        var method = SpaContentProvider.class.getDeclaredMethod("rewriteNgsw", String.class);
        method.setAccessible(true);

        String original = "{\"index\": \"/index.html\", \"urls\": [\"/main.js\"]}";
        String result = (String) method.invoke(provider, original);

        assertThat(result).contains("\"/grimmory/index.html\"");
        assertThat(result).contains("\"/grimmory/main.js\"");
    }

    @Test
    void rootBasePath_doesNotRewrite() {
        var mockServletContext = new org.springframework.mock.web.MockServletContext();
        mockServletContext.setContextPath("");

        var provider = new SpaContentProvider(mockServletContext);

        // When base path is root, rewritten content should be null (serves original)
        assertThat(provider.isNeedsRewrite()).isFalse();
        assertThat(provider.getIndexHtml()).isNull();
    }
}
