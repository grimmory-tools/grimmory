package org.booklore.service.kobo;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.kobo.KoboEventsContainer;
import org.booklore.model.dto.request.ReadingSessionRequest;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookMetadataRepository;
import org.booklore.repository.BookRepository;
import org.booklore.repository.ReadingSessionRepository;
import org.booklore.service.ReadingSessionService;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class KoboEventsService {
    private static final String MONETIZATION_TYPE_PAID = "Paid";
    private static final String MONETIZATION_TYPE_SIDELOADED = "Sideloaded";
    private static final String EVENT_TYPE_OPEN_CONTENT = "OpenContent";
    private static final String EVENT_TYPE_LEAVE_CONTENT = "LeaveContent";
    private static final Duration OPEN_EVENT_TTL = Duration.ofDays(7);
    private static final int OPEN_EVENT_CACHE_MAX = 1000;

    private final AuthenticationService authenticationService;
    private final ReadingSessionService readingSessionService;
    private final BookRepository bookRepository;
    private final BookMetadataRepository bookMetadataRepository;
    private final ReadingSessionRepository readingSessionRepository;

    private record OpenEventKey(Long userId, String serial, Long bookId) {}
    private record CachedOpenEvent(Float progress, Instant cachedAt) {}

    private final Map<OpenEventKey, CachedOpenEvent> openEventCache = new ConcurrentHashMap<>();

    public void handleEvents(KoboEventsContainer eventsContainer) {
        if (eventsContainer.getEvents() == null) {
            return;
        }
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        if (user == null || user.getId() == null) {
            log.warn("Received Kobo analytics events without an authenticated user; ignoring");
            return;
        }
        String serial = StringUtils.defaultString(eventsContainer.getSerialNumber());

        evictStaleOpenEvents();

        for (KoboEventsContainer.Event event : eventsContainer.getEvents()) {
            if (event == null || event.getEventType() == null) {
                continue;
            }
            try {
                switch (event.getEventType()) {
                    case EVENT_TYPE_OPEN_CONTENT -> handleOpenContent(user.getId(), serial, event);
                    case EVENT_TYPE_LEAVE_CONTENT -> handleLeaveContent(user.getId(), serial, event);
                    default -> log.trace("Unhandled Kobo event type: {}", event.getEventType());
                }
            } catch (Exception e) {
                log.warn("Failed to process Kobo {} event {}: {}", event.getEventType(), event.getId(), e.getMessage(), e);
            }
        }
    }

    private void handleOpenContent(Long userId, String serial, KoboEventsContainer.Event event) {
        Long bookId = resolveBookId(event);
        if (bookId == null) {
            return;
        }
        Float progress = event.getAttributes() == null ? null : event.getAttributes().getProgress();
        CachedOpenEvent previous = openEventCache.put(new OpenEventKey(userId, serial, bookId), new CachedOpenEvent(progress, Instant.now()));
        if (previous != null) {
            log.debug("Replacing cached Kobo OpenContent event for book {} (previous had no matching LeaveContent)", bookId);
        }
    }

    private void handleLeaveContent(Long userId, String serial, KoboEventsContainer.Event event) {
        Long bookId = resolveBookId(event);
        if (bookId == null) {
            return;
        }

        CachedOpenEvent startEvent = openEventCache.remove(new OpenEventKey(userId, serial, bookId));

        KoboEventsContainer.Metrics metrics = event.getMetrics();
        Integer secondsRead = metrics == null ? null : metrics.getSecondsRead();
        Integer pagesTurned = metrics == null ? null : metrics.getPagesTurned();
        if (pagesTurned == null || pagesTurned <= 0 || secondsRead == null || secondsRead <= 0) {
            log.debug("Ignoring Kobo LeaveContent for book {} without reading activity", bookId);
            return;
        }

        Instant endTime = event.getTimestamp();
        if (endTime == null) {
            log.warn("Ignoring Kobo LeaveContent for book {}: missing timestamp", bookId);
            return;
        }
        Instant startTime = endTime.minusSeconds(secondsRead);

        if (readingSessionRepository.existsByUser_IdAndBook_IdAndStartTimeAndEndTime(userId, bookId, startTime, endTime)) {
            log.debug("Ignoring duplicate Kobo reading session for book {} from {} to {}", bookId, startTime, endTime);
            return;
        }

        BookFileType bookType = bookRepository.findByIdWithBookFiles(bookId)
                .map(BookEntity::getPrimaryBookFile)
                .map(f -> f.getBookType())
                .orElse(BookFileType.EPUB);

        Float startProgress = startEvent == null ? null : startEvent.progress();
        Float endProgress = event.getAttributes() == null ? null : event.getAttributes().getProgress();
        Float progressDelta = (startProgress != null && endProgress != null) ? endProgress - startProgress : null;

        ReadingSessionRequest request = ReadingSessionRequest.builder()
                .bookId(bookId)
                .bookType(bookType)
                .startTime(startTime)
                .endTime(endTime)
                .durationSeconds(secondsRead)
                .startProgress(startProgress)
                .endProgress(endProgress)
                .progressDelta(progressDelta)
                .build();

        readingSessionService.recordSession(request);
        log.info("Stored Kobo reading session for book {} from {} to {} ({} pages turned, progress {} -> {})",
                bookId, startTime, endTime, pagesTurned, startProgress, endProgress);
    }

    private Long resolveBookId(KoboEventsContainer.Event event) {
        KoboEventsContainer.Attributes attributes = event.getAttributes();
        if (attributes == null || attributes.getMonetization() == null) {
            log.debug("Kobo event {} has no attributes/monetization; ignoring", event.getId());
            return null;
        }
        switch (attributes.getMonetization()) {
            case MONETIZATION_TYPE_PAID -> {
                // Grimmory uses the numeric book id as Kobo entitlement id; ignore non-numeric volume ids
                String volumeId = attributes.getVolumeid();
                if (!StringUtils.isNumeric(volumeId)) {
                    return null;
                }
                Long id = Long.valueOf(volumeId);
                if (!bookRepository.existsById(id)) {
                    log.warn("Kobo event references unknown book id {}", id);
                    return null;
                }
                return id;
            }
            case MONETIZATION_TYPE_SIDELOADED -> {
                String title = attributes.getTitle();
                if (StringUtils.isBlank(title)) {
                    return null;
                }
                List<BookMetadataEntity> metadata = bookMetadataRepository.findByTitleIgnoreCase(title.trim());
                if (metadata.isEmpty()) {
                    log.info("Kobo event for sideloaded book \"{}\" did not match any book in Grimmory", title);
                    return null;
                }
                if (metadata.size() > 1) {
                    log.info("Kobo sideloaded title \"{}\" matches {} books; using book {}", title, metadata.size(), metadata.getFirst().getBookId());
                }
                return metadata.getFirst().getBookId();
            }
            default -> {
                log.debug("Unknown Kobo monetization type {} on event {}", attributes.getMonetization(), event.getId());
                return null;
            }
        }
    }

    private void evictStaleOpenEvents() {
        Instant cutoff = Instant.now().minus(OPEN_EVENT_TTL);
        openEventCache.entrySet().removeIf(e -> e.getValue().cachedAt().isBefore(cutoff));
        if (openEventCache.size() > OPEN_EVENT_CACHE_MAX) {
            log.warn("Kobo open-event cache exceeded {} entries; clearing it", OPEN_EVENT_CACHE_MAX);
            openEventCache.clear();
        }
    }
}
