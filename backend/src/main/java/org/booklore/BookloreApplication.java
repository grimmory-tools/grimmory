package org.booklore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import org.booklore.config.AppProperties;
import org.booklore.config.BookmarkProperties;
import org.booklore.config.CacheProperties;

@EnableScheduling
@EnableConfigurationProperties({AppProperties.class, BookmarkProperties.class, CacheProperties.class})
@SpringBootApplication
public class BookloreApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookloreApplication.class, args);
    }
}
