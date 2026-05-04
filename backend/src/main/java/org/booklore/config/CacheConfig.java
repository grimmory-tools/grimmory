package org.booklore.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableCaching
@RequiredArgsConstructor
public class CacheConfig {

    private final CacheProperties cacheProperties;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                "publicSettings",
                "appSettings",
                "library-by-id",
                "libraries-by-user",
                "shelves-by-user",
                "shelf-by-id",
                "authors-by-user",
                "author-by-name",
                "author-by-id",
                "authors-by-book",
                "recommendations",
                "versionInfo",
                "changelog"
        );
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(cacheProperties.getTtl())
                .maximumSize(cacheProperties.getMaximumSize()));
        return cacheManager;
    }
}
