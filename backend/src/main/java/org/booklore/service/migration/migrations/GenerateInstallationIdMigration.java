package org.booklore.service.migration.migrations;

import org.booklore.service.InstallationService;
import org.booklore.service.migration.Migration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GenerateInstallationIdMigration implements Migration {

    private final InstallationService installationService;

    @Override
    public String getKey() {
        return "generateInstallationId";
    }

    @Override
    public String getDescription() {
        return "Generate unique installation ID using timestamp and UUID";
    }

    @Override
    public void execute() {
        log.info("Executing migration: {}", getKey());
        installationService.getOrCreateInstallation();
        log.info("Completed migration: {}", getKey());
    }
}

