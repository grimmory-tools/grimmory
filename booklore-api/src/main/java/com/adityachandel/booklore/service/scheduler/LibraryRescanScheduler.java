package com.adityachandel.booklore.service.scheduler;

import com.adityachandel.booklore.model.dto.Library;
import com.adityachandel.booklore.service.library.LibraryService;
import lombok.AllArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@AllArgsConstructor
public class LibraryRescanScheduler {

    private final LibraryService libraryService;

    @Scheduled(cron = "0 0 0 * * *")  // At 00:00 every day
    public void rescanLibraries() {
        log.info("Starting scheduled library rescan at midnight");
        for (Library library : libraryService.getAllLibraries()) {
            try {
                libraryService.rescanLibrary(library.getId());
                log.info("Rescanned library '{}'", library.getName());
            } catch (Exception e) {
                log.error("Failed to rescan library '{}': {}", library.getName(), e.getMessage(), e);
            }
        }
        log.info("Completed scheduled library rescan");
    }
}