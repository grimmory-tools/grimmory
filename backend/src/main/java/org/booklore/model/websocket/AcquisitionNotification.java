package org.booklore.model.websocket;

import org.booklore.model.enums.WantedBookStatus;
import lombok.Getter;

import java.time.Instant;

@Getter
public class AcquisitionNotification {

    private final Instant timestamp = Instant.now();
    private final Long wantedBookId;
    private final String title;
    private final WantedBookStatus status;
    private final String message;
    private final Instant lastCheckedAt;

    private AcquisitionNotification(Long wantedBookId, String title, WantedBookStatus status, String message, Instant lastCheckedAt) {
        this.wantedBookId = wantedBookId;
        this.title = title;
        this.status = status;
        this.message = message;
        this.lastCheckedAt = lastCheckedAt;
    }

    public static AcquisitionNotification of(Long wantedBookId, String title, WantedBookStatus status, String message) {
        return new AcquisitionNotification(wantedBookId, title, status, message, Instant.now());
    }
}
