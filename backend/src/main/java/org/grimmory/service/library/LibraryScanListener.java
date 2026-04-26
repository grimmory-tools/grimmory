package org.grimmory.service.library;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import org.grimmory.task.options.RescanLibraryContext;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles library scan requests after the triggering transaction commits.
 * Scans are executed asynchronously with deduplication to prevent concurrent scans.
 * Security context is automatically propagated via DelegatingSecurityContextExecutor.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LibraryScanListener {

    private final LibraryProcessingService libraryProcessingService;
    private final Set<Long> scanningLibraries = ConcurrentHashMap.newKeySet();

    public boolean isScanning(long libraryId) {
        return scanningLibraries.contains(libraryId);
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(LibraryScanRequestedEvent event) {
        long libraryId = event.libraryId();
        
        if (!scanningLibraries.add(libraryId)) {
            log.warn("Library {} is already being scanned, skipping duplicate request", libraryId);
            return;
        }
        
        try {
            log.info("Starting background scan for library ID {} (full rescan: {})", libraryId, event.fullRescan());
            if (event.fullRescan()) {
                libraryProcessingService.rescanLibrary(RescanLibraryContext.builder().libraryId(libraryId).build());
            } else {
                libraryProcessingService.processLibrary(libraryId);
            }
            log.info("Completed background scan for library ID {}", libraryId);
        } catch (InvalidDataAccessApiUsageException e) {
            log.debug("InvalidDataAccessApiUsageException during library scan - Library id: {}", libraryId, e);
        } catch (IOException e) {
            log.error("IO error during library scan for library ID {}: {}", libraryId, e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error during library scan for library ID {}: {}", libraryId, e.getMessage(), e);
        } finally {
            scanningLibraries.remove(libraryId);
        }
    }
}
