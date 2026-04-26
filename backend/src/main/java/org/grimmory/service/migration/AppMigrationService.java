package org.grimmory.service.migration;

import org.grimmory.model.entity.AppMigrationEntity;
import org.grimmory.repository.AppMigrationRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@AllArgsConstructor
@Service
public class AppMigrationService {

    private AppMigrationRepository migrationRepository;


    @Transactional
    public void executeMigration(Migration migration) {
        if (migrationRepository.existsById(migration.getKey())) {
            log.debug("Migration '{}' already executed, skipping", migration.getKey());
            return;
        }
        try {
            migration.execute();
            AppMigrationEntity entity = new AppMigrationEntity(migration.getKey(), LocalDateTime.now(), migration.getDescription());
            migrationRepository.save(entity);

            log.info("Migration '{}' completed successfully", migration.getKey());
        } catch (Exception e) {
            log.error("Migration '{}' failed", migration.getKey(), e);
            throw e;
        }
    }
}
