package org.booklore.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockServletContext;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpaContentProviderTest {
    @ParameterizedTest
    @ValueSource(strings = {"<base href=\"/\">", "<BASE target='_self' HREF = '/'>", "<base href='/' />"})
    void rewritesBaseRegardlessOfAttributeFormatting(String base) throws Exception {
        var provider = provider("<html><head>" + base + "</head></html>");
        assertThat(provider.getIndexHtmlResource().getContentAsString(StandardCharsets.UTF_8))
                .contains("/apps/books/").doesNotContain("href=\"/\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"<html></html>", "<base data-href='/'>", "<base href='/old/'>", "<base href='/'><base href='/'>"})
    void rejectsUnexpectedBuildOutputRatherThanSilentlyServingWrongBase(String html) {
        assertThatThrownBy(() -> provider(html)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("base href");
    }

    private SpaContentProvider provider(String html) {
        var context = new MockServletContext();
        context.setContextPath("/apps/books");
        return new SpaContentProvider(context, new DefaultResourceLoader() {
            @Override
            public Resource getResource(String location) {
                return location.endsWith("index.html")
                        ? new ByteArrayResource(html.getBytes(StandardCharsets.UTF_8))
                        : super.getResource("classpath:/missing-spa-test-resource");
            }
        });
    }
}
