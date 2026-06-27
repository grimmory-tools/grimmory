package org.booklore.service.customfont;

import org.booklore.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;

class CustomFontUploadLimitResolverTest {

    @Test
    void getMaxFileSizeMb_defaultsTo50Mb() {
        AppProperties appProperties = new AppProperties();
        MultipartProperties multipartProperties = multipartProperties(1024, 1024);
        CustomFontUploadLimitResolver resolver = new CustomFontUploadLimitResolver(appProperties, multipartProperties);

        assertThat(resolver.getMaxFileSizeMb()).isEqualTo(50);
    }

    @Test
    void getMaxFileSizeMb_usesConfiguredCustomFontLimit() {
        AppProperties appProperties = new AppProperties();
        appProperties.getCustomFont().setMaxFileSizeMb(60);
        MultipartProperties multipartProperties = multipartProperties(1024, 1024);
        CustomFontUploadLimitResolver resolver = new CustomFontUploadLimitResolver(appProperties, multipartProperties);

        assertThat(resolver.getMaxFileSizeMb()).isEqualTo(60);
    }

    @Test
    void getMaxFileSizeMb_capsAtMultipartLimit() {
        AppProperties appProperties = new AppProperties();
        appProperties.getCustomFont().setMaxFileSizeMb(200);
        MultipartProperties multipartProperties = multipartProperties(75, 90);
        CustomFontUploadLimitResolver resolver = new CustomFontUploadLimitResolver(appProperties, multipartProperties);

        assertThat(resolver.getMaxFileSizeMb()).isEqualTo(75);
    }

    private MultipartProperties multipartProperties(long maxFileSizeMb, long maxRequestSizeMb) {
        MultipartProperties multipartProperties = new MultipartProperties();
        multipartProperties.setMaxFileSize(DataSize.ofMegabytes(maxFileSizeMb));
        multipartProperties.setMaxRequestSize(DataSize.ofMegabytes(maxRequestSizeMb));
        return multipartProperties;
    }
}
