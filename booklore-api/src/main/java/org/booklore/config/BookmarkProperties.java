package org.booklore.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bookmarks")
@Data
public class BookmarkProperties {
    private int defaultPriority = 3;
    private int minPriority = 1;
    private int maxPriority = 5;
    private int maxNotesLength = 2000;
    private int maxTitleLength = 255;
    private int maxCfiLength = 500;
}