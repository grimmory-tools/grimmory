package org.booklore.config;

import org.flywaydb.core.api.exception.FlywayValidateException;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {

    @Bean
    FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            try {
                flyway.migrate();
            } catch (FlywayValidateException e) {
                flyway.repair();
                flyway.migrate();
            }
        };
    }
}
