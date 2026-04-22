package org.booklore.service.migration.migrations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.entity.AppSettingEntity;
import org.booklore.repository.AppSettingsRepository;
import org.booklore.service.migration.Migration;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class MigrateInstallationIdToJsonMigration implements Migration {

    private static final String INSTALLATION_ID_KEY = "installation_id";

    private final AppSettingsRepository appSettingsRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String getKey() {
        return "migrateInstallationIdToJson";
    }

    @Override
    public String getDescription() {
        return "Migrate existing installation_id from plain string to JSON format with date";
    }

    @Override
    public void execute() {
        log.info("Executing migration: {}", getKey());

        AppSettingEntity setting = appSettingsRepository.findByName(INSTALLATION_ID_KEY);

        if (setting != null) {
            String value = setting.getVal();
            try {
                objectMapper.readTree(value);
                log.info("Installation ID is already in JSON format, skipping migration");
            } catch (Exception e) {
                Instant now = Instant.now();
                String json = String.format("{\"id\":\"%s\",\"date\":\"%s\"}", value, now);
                setting.setVal(json);
                appSettingsRepository.save(setting);
                log.info("Migrated installation ID to JSON format with current date");
            }
        }

        log.info("Completed migration: {}", getKey());
    }
}

