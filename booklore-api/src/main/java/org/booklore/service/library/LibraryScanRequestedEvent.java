package org.booklore.service.library;

/**
 * Event published when a library scan is requested.
 * The actual scan will be performed after the current transaction commits.
 *
 * @param libraryId the ID of the library to scan
 */
public record LibraryScanRequestedEvent(long libraryId, boolean fullRescan) {
}
