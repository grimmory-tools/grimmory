package org.booklore.service.watcher;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Prevents the library watcher from treating Grimmory's own file writes and moves as
 * external changes. Suppression is short lived so a later user edit is still observed.
 */
@Component
public class BookFileEventSuppression {

    private record Suppression(Instant expiresAt, boolean recursive) {}

    private final ConcurrentMap<Path, Suppression> suppressedPaths = new ConcurrentHashMap<>();
    private final AtomicInteger operations = new AtomicInteger();

    public void suppress(Path path, Duration duration, boolean recursive) {
        if (path == null || duration == null || duration.isNegative() || duration.isZero()) {
            return;
        }
        suppressedPaths.put(path.toAbsolutePath().normalize(), new Suppression(Instant.now().plus(duration), recursive));
        if ((operations.incrementAndGet() & 63) == 0) {
            Instant now = Instant.now();
            suppressedPaths.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        }
    }

    public boolean isSuppressed(Path path) {
        if (path == null) {
            return false;
        }
        Path normalized = path.toAbsolutePath().normalize();
        Instant now = Instant.now();
        Suppression exact = activeSuppression(normalized, now);
        if (exact != null) {
            return true;
        }

        for (Path parent = normalized.getParent(); parent != null; parent = parent.getParent()) {
            Suppression ancestor = activeSuppression(parent, now);
            if (ancestor != null && ancestor.recursive()) {
                return true;
            }
        }
        return false;
    }

    private Suppression activeSuppression(Path path, Instant now) {
        Suppression suppression = suppressedPaths.get(path);
        if (suppression != null && !suppression.expiresAt().isAfter(now)) {
            suppressedPaths.remove(path, suppression);
            return null;
        }
        return suppression;
    }
}
