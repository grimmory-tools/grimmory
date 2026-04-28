package org.booklore.crons;

import org.booklore.model.dto.acquisition.AcquisitionResult;
import org.booklore.model.entity.WantedBookEntity;
import org.booklore.model.enums.WantedBookStatus;
import org.booklore.model.websocket.AcquisitionNotification;
import org.booklore.model.websocket.Topic;
import org.booklore.repository.WantedBookRepository;
import org.booklore.service.NotificationService;
import org.booklore.service.acquisition.AcquisitionService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@AllArgsConstructor
public class BookAcquisitionScheduler {

    private final AcquisitionService acquisitionService;
    private final WantedBookRepository wantedBookRepository;
    private final NotificationService notificationService;

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(cron = "0 0 3 * * ?")
    public void runDaily() {
        runAcquisition();
    }

    public void triggerNow() {
        if (running.get()) {
            log.info("Book acquisition already running — skipping manual trigger");
            return;
        }
        runAcquisition();
    }

    private void runAcquisition() {
        if (!running.compareAndSet(false, true)) {
            log.info("Book acquisition already running — skipping concurrent execution");
            return;
        }
        try {
            log.info("Starting book acquisition job");

            List<WantedBookEntity> candidates = wantedBookRepository
                    .findByStatusIn(List.of(WantedBookStatus.WANTED, WantedBookStatus.NOT_FOUND));

            log.info("Processing {} wanted books", candidates.size());

            for (WantedBookEntity book : candidates) {
                if (book.getRetryCount() >= 5) {
                    log.warn("Book id={} '{}' has reached max retries, marking FAILED_PERMANENT",
                            book.getId(), book.getTitle());
                    book.setStatus(WantedBookStatus.FAILED_PERMANENT);
                    wantedBookRepository.save(book);
                    notificationService.sendMessage(Topic.ACQUISITION_UPDATE,
                            AcquisitionNotification.of(book.getId(), book.getTitle(), WantedBookStatus.FAILED_PERMANENT, "Max retries reached"));
                    continue;
                }
                try {
                    AcquisitionResult result = acquisitionService.searchAndDispatch(book);

                    WantedBookStatus newStatus = result.found()
                            ? WantedBookStatus.DOWNLOADING
                            : WantedBookStatus.NOT_FOUND;

                    String message = result.found()
                            ? "Dispatched to SABnzbd: " + result.nzbTitle()
                            : "No confident match found";

                    notificationService.sendMessage(
                            Topic.ACQUISITION_UPDATE,
                            AcquisitionNotification.of(book.getId(), book.getTitle(), newStatus, message)
                    );
                } catch (Exception e) {
                    log.error("Failed to process wanted book id={}: {}", book.getId(), e.getMessage(), e);
                }
            }

            log.info("Book acquisition job complete");
        } finally {
            running.set(false);
        }
    }
}
