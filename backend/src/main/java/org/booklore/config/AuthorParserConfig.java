package org.booklore.config;

import org.booklore.model.enums.AuthorMetadataSource;
import org.booklore.service.metadata.parser.AudnexusAuthorParser;
import org.booklore.service.metadata.parser.AuthorParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class AuthorParserConfig {

    @Bean
    public Map<AuthorMetadataSource, AuthorParser> authorParserMap(AudnexusAuthorParser audnexusAuthorParser) {
        return Map.of(
                AuthorMetadataSource.AUDNEXUS, audnexusAuthorParser
        );
    }
}
