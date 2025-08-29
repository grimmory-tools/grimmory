package com.adityachandel.booklore.config;

import com.adityachandel.booklore.model.enums.MetadataProvider;
import com.adityachandel.booklore.service.metadata.parser.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class BookParserConfig {

    @Bean
    public Map<MetadataProvider, BookParser> parserMap(GoogleParser googleParser, AmazonBookParser amazonBookParser,
                                                       GoodReadsParser goodReadsParser, HardcoverParser hardcoverParser, ComicvineBookParser comicvineBookParser, DoubanBookParser doubanBookParser) {
        return Map.of(
                MetadataProvider.Amazon, amazonBookParser,
                MetadataProvider.GoodReads, goodReadsParser,
                MetadataProvider.Google, googleParser,
                MetadataProvider.Hardcover, hardcoverParser,
                MetadataProvider.Comicvine, comicvineBookParser,
                MetadataProvider.Douban, doubanBookParser
        );
    }
}
