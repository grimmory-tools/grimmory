package org.booklore.model.websocket;

import lombok.Getter;

import java.time.Instant;


@Getter
public class LogNotification {

    private final Instant timestamp = Instant.now();
    private final String message;
    private final Severity severity;

    public LogNotification(String message, Severity severity) {
        this.message = message;
        this.severity = severity;
    }

    public static LogNotification createLogNotification(String message, Severity severity) {
        return new LogNotification(message, severity);
    }

    public static LogNotification info(String message) {
        return new LogNotification(message, Severity.INFO);
    }

    public static LogNotification warn(String message) {
        return new LogNotification(message, Severity.WARN);
    }

    public static LogNotification error(String message) {
        return new LogNotification(message, Severity.ERROR);
    }
}
