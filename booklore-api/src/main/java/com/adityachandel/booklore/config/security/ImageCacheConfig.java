package com.adityachandel.booklore.config.security;

import com.adityachandel.booklore.config.security.filter.ImageCachingFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ImageCacheConfig {

    @Bean
    public FilterRegistrationBean<ImageCachingFilter> imageCachingFilterRegistration(ImageCachingFilter filter) {
        FilterRegistrationBean<ImageCachingFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(filter);
        registrationBean.addUrlPatterns(
                "/api/v1/media/book/*/cover",
                "/api/v1/media/book/*/thumbnail",
                "/api/v1/media/book/*/backup-cover"
        );
        registrationBean.setOrder(1);
        return registrationBean;
    }
}
