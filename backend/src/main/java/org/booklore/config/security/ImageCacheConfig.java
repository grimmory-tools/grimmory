package org.booklore.config.security;

import org.booklore.config.security.filter.ImageCachingFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class ImageCacheConfig {

    @Bean
    public FilterRegistrationBean<ImageCachingFilter> imageCachingFilterRegistration() {
        FilterRegistrationBean<ImageCachingFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new ImageCachingFilter());
        registrationBean.addUrlPatterns(
                "/api/v1/media/book/*/cover",
                "/api/v1/media/book/*/thumbnail",
                "/api/v1/media/book/*/backup-cover"
        );
        registrationBean.setOrder(Ordered.LOWEST_PRECEDENCE);
        return registrationBean;
    }
}
