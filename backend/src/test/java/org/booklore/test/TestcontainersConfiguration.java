package org.booklore.test;

import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.mariadb.MariaDBContainer;

import java.util.Locale;
import java.util.Map;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final String DEFAULT_DATABASE = "mariadb";
    private static final String DEFAULT_MARIADB_VERSION = "11.4";

    private static final JdbcDatabaseContainer<?> DATABASE = newDatabase();

    private static JdbcDatabaseContainer<?> newDatabase() {
        String database = envOrDefault("TEST_DATABASE", DEFAULT_DATABASE).toLowerCase(Locale.ROOT);
        return switch (database) {
            case "mariadb" -> new MariaDBContainer("mariadb:" + envOrDefault("TEST_DATABASE_VERSION", DEFAULT_MARIADB_VERSION))
                    .withUrlParam("connectionTimeZone", "UTC")
                    .withUrlParam("forceConnectionTimeZoneToSession", "true")
                    .withTmpFs(Map.of("/var/lib/mysql", "rw"))
                    .withCommand(
                            "--innodb-flush-log-at-trx-commit=0",
                            "--innodb-doublewrite=0",
                            "--innodb-buffer-pool-size=256M");
            default -> throw new IllegalStateException("Unsupported TEST_DATABASE: " + database);
        };
    }

    private static String envOrDefault(String name, String fallback) {
        String configured = System.getenv(name);
        return configured == null || configured.isBlank() ? fallback : configured.trim();
    }

    static {
        DATABASE.start();
    }

    @Bean
    JdbcConnectionDetails jdbcConnectionDetails() {
        return new JdbcConnectionDetails() {
            @Override
            public String getUsername() {
                return DATABASE.getUsername();
            }

            @Override
            public String getPassword() {
                return DATABASE.getPassword();
            }

            @Override
            public String getJdbcUrl() {
                return DATABASE.getJdbcUrl();
            }
        };
    }
}
