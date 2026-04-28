package org.booklore.acquisition;

import org.booklore.BookloreApplication;
import org.booklore.model.entity.AcquisitionIndexerEntity;
import org.booklore.repository.AcquisitionIndexerRepository;
import org.booklore.service.task.TaskCronService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SpringBootTest(classes = {BookloreApplication.class})
@Transactional
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.path-config=build/tmp/test-config",
        "app.bookdrop-folder=build/tmp/test-bookdrop",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.task.scheduling.enabled=false",
        "app.features.oidc-enabled=false",
        "spring.jpa.properties.hibernate.connection.provider_disables_autocommit=false",
        "spring.jpa.properties.hibernate.enable_lazy_load_no_trans=false",
})
@Import(AcquisitionIndexerRepositoryTest.TestConfig.class)
class AcquisitionIndexerRepositoryTest {

    @TestConfiguration
    public static class TestConfig {
        @Bean("flyway")
        @Primary
        public Flyway flyway() {
            return mock(Flyway.class);
        }

        @Bean
        @Primary
        public TaskCronService taskCronService() {
            return mock(TaskCronService.class);
        }
    }

    @Autowired
    private AcquisitionIndexerRepository repository;

    @Test
    void save_and_findById() {
        AcquisitionIndexerEntity entity = AcquisitionIndexerEntity.builder()
                .name("NZBGeek")
                .url("https://api.nzbgeek.info")
                .apiKey("testkey123")
                .enabled(true)
                .priority(1)
                .createdAt(Instant.now())
                .build();

        AcquisitionIndexerEntity saved = repository.save(entity);
        Optional<AcquisitionIndexerEntity> found = repository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("NZBGeek");
        assertThat(found.get().getUrl()).isEqualTo("https://api.nzbgeek.info");
    }

    @Test
    void findByEnabledTrueOrderByPriorityAsc_returnsOnlyEnabled() {
        repository.save(indexer("A", true, 2));
        repository.save(indexer("B", false, 1));
        repository.save(indexer("C", true, 0));

        List<AcquisitionIndexerEntity> results = repository.findByEnabledTrueOrderByPriorityAsc();

        assertThat(results).hasSize(2);
        assertThat(results).extracting(AcquisitionIndexerEntity::getName).containsExactly("C", "A");
    }

    @Test
    void delete_removesEntity() {
        AcquisitionIndexerEntity saved = repository.save(indexer("ToDelete", true, 0));
        repository.deleteById(saved.getId());
        assertThat(repository.findById(saved.getId())).isEmpty();
    }

    private AcquisitionIndexerEntity indexer(String name, boolean enabled, int priority) {
        return AcquisitionIndexerEntity.builder()
                .name(name)
                .url("https://example.com")
                .apiKey("key")
                .enabled(enabled)
                .priority(priority)
                .createdAt(Instant.now())
                .build();
    }
}
