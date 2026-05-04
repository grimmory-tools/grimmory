package org.booklore.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "app.cache")
public class CacheProperties {
    /**
     * Default time-to-live for cache entries.
     */
    private Duration ttl = Duration.ofHours(24);

    /**
     * Maximum number of entries per cache.
     */
    private int maximumSize = 1000;
}
