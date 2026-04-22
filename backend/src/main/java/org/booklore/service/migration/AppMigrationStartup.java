package org.booklore.service.migration;

import org.booklore.service.migration.migrations.*;
import lombok.AllArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class AppMigrationStartup {

    private final AppMigrationService appMigrationService;
    private final GenerateInstallationIdMigration generateInstallationIdMigration;
    private final MigrateInstallationIdToJsonMigration migrateInstallationIdToJsonMigration;
    private final PopulateMissingFileSizesMigration populateMissingFileSizesMigration;
    private final PopulateMetadataScoresMigration populateMetadataScoresMigration;
    private final PopulateFileHashesMigration populateFileHashesMigration;
    private final PopulateCoversAndResizeThumbnailsMigration populateCoversAndResizeThumbnailsMigration;
    private final PopulateSearchTextMigration populateSearchTextMigration;
    private final MoveIconsToDataFolderMigration moveIconsToDataFolderMigration;
    private final GenerateCoverHashMigration generateCoverHashMigration;
    private final MigrateProgressToFileProgressMigration migrateProgressToFileProgressMigration;

    @EventListener(ApplicationReadyEvent.class)
    public void runMigrationsOnce() {
        appMigrationService.executeMigration(generateInstallationIdMigration);
        appMigrationService.executeMigration(migrateInstallationIdToJsonMigration);
        appMigrationService.executeMigration(populateMissingFileSizesMigration);
        appMigrationService.executeMigration(populateMetadataScoresMigration);
        appMigrationService.executeMigration(populateFileHashesMigration);
        appMigrationService.executeMigration(populateCoversAndResizeThumbnailsMigration);
        appMigrationService.executeMigration(populateSearchTextMigration);
        appMigrationService.executeMigration(moveIconsToDataFolderMigration);
        appMigrationService.executeMigration(generateCoverHashMigration);
        appMigrationService.executeMigration(migrateProgressToFileProgressMigration);
    }
}
