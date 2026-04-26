package org.grimmory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import org.booklore.config.AppProperties;
import org.booklore.config.BookmarkProperties;

@EnableScheduling
@EnableConfigurationProperties({AppProperties.class, BookmarkProperties.class})
@SpringBootApplication
public class BookloreApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookloreApplication.class, args);
    }
}
