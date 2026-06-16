package org.booklore.config;

import org.booklore.model.enums.MetadataProvider;
import org.booklore.service.metadata.parser.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class BookParserConfig {

    @Bean
    public Map<MetadataProvider, BookParser> parserMap(
            GoogleParser googleParser,
            AmazonBookParser amazonBookParser,
            GoodReadsParser goodReadsParser,
            HardcoverParser hardcoverParser,
            ComicvineBookParser comicvineBookParser,
            DoubanBookParser doubanBookParser,
            RanobeDbParser ranobedbParser,
            LubimyCzytacParser lubimyczytacParser,
            AudibleParser audibleParser,
            AppleBooksParser appleBooksParser,
            OpenLibraryParser openLibraryParser
    ) {
        return Map.ofEntries(
                Map.entry(MetadataProvider.OpenLibrary, openLibraryParser),
                Map.entry(MetadataProvider.Amazon, amazonBookParser),
                Map.entry(MetadataProvider.GoodReads, goodReadsParser),
                Map.entry(MetadataProvider.Google, googleParser),
                Map.entry(MetadataProvider.Hardcover, hardcoverParser),
                Map.entry(MetadataProvider.Comicvine, comicvineBookParser),
                Map.entry(MetadataProvider.Douban, doubanBookParser),
                Map.entry(MetadataProvider.Lubimyczytac, lubimyczytacParser),
                Map.entry(MetadataProvider.Ranobedb, ranobedbParser),
                Map.entry(MetadataProvider.Audible, audibleParser),
                Map.entry(MetadataProvider.AppleBooks, appleBooksParser)
        );
    }
}
