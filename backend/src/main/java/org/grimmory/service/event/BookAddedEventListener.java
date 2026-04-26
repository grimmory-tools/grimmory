package org.grimmory.service.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.Book;
import org.booklore.service.kobo.KoboAutoShelfService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Handles book creation events after the triggering transaction commits.
 * Broadcasts notifications and adds books to Kobo shelves automatically.
 * Execution is asynchronous with security context propagation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookAddedEventListener {

    private final BookEventBroadcaster bookEventBroadcaster;
    private final KoboAutoShelfService koboAutoShelfService;

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(BookAddedEvent event) {
        Book book = event.book();
        try {
            log.debug("Processing book added event for book ID {}", book.getId());
            bookEventBroadcaster.broadcastBookAddEvent(book);
            koboAutoShelfService.autoAddBookToKoboShelves(book.getId());
            log.debug("Book {} notifications and Kobo shelf updates completed", book.getId());
        } catch (Exception e) {
            log.error("Failed to process book added event for book ID {}: {}", book.getId(), e.getMessage(), e);
        }
    }
}
