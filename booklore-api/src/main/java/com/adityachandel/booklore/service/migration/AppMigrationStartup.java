package com.adityachandel.booklore.service.migration;

import lombok.AllArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class AppMigrationStartup {

    private final AppMigrationService appMigrationService;

    @EventListener(ApplicationReadyEvent.class)
    public void runMigrationsOnce() {
        appMigrationService.populateMissingFileSizesOnce();
        appMigrationService.populateMetadataScoresOnce();
        appMigrationService.populateFileHashesOnce();
        appMigrationService.populateCoversAndResizeThumbnails();
    }
}
