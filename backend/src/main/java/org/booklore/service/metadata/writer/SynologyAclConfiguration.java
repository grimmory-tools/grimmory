package org.booklore.service.metadata.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.booklore.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SynologyAclConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "app.synology-acl", name = "enabled", havingValue = "true")
    SynologyAclSshCommands synologyAclSshCommands(AppProperties properties, ObjectMapper objectMapper) {
        return new SynologyAclSshCommands(properties, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.synology-acl", name = "enabled", havingValue = "true")
    NasSynologyPermissions synologyAclPermissions(SynologyAclSshCommands commands) {
        return new NasSynologyPermissions(commands);
    }
}
