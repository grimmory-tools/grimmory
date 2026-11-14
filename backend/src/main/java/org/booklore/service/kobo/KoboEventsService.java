package org.booklore.service.kobo;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.Library;
import org.booklore.model.dto.kobo.KoboEventsContainer;
import org.booklore.model.dto.request.ReadingSessionRequest;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookMetadataRepository;
import org.booklore.repository.BookRepository;
import org.booklore.repository.ReadingSessionRepository;
import org.booklore.service.ReadingSessionService;
import org.booklore.service.restriction.ContentRestrictionService;
import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Turns the analytics events a Kobo device posts to {@code /v1/analytics/event} into Grimmory reading sessions.
 * <p>
 * An {@code OpenContent} event is cached in memory until the matching {@code LeaveContent} event arrives; the pair is
 * then stored as one reading session. All other event types are ignored.
 */
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
    private final ContentRestrictionService contentRestrictionService;

    private record OpenEventKey(Long userId, String serial, Long bookId) {}
    /** Wraps the start progress, which may be {@code null}; Caffeine does not accept {@code null} values. */
    private record CachedOpenEvent(Float progress) {}

    private final Cache<OpenEventKey, CachedOpenEvent> openEventCache = Caffeine.newBuilder()
            .maximumSize(OPEN_EVENT_CACHE_MAX)
            .expireAfterWrite(OPEN_EVENT_TTL)
            .build();

    /**
     * Processes a batch of Kobo analytics events for the authenticated user.
     * <p>
     * Each event is handled independently: an event that cannot be processed is logged and skipped so the rest of the
     * batch is still recorded.
     *
     * @param eventsContainer the analytics payload posted by the Kobo device
     */
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

        for (KoboEventsContainer.Event event : eventsContainer.getEvents()) {
            if (event == null || event.getEventType() == null) {
                continue;
            }
            try {
                switch (event.getEventType()) {
                    case EVENT_TYPE_OPEN_CONTENT -> handleOpenContent(user, serial, event);
                    case EVENT_TYPE_LEAVE_CONTENT -> handleLeaveContent(user, serial, event);
                    default -> log.trace("Unhandled Kobo event type: {}", event.getEventType());
                }
            } catch (Exception e) {
                log.warn("Failed to process Kobo {} event {}: {}", event.getEventType(), event.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Remembers the start progress of an {@code OpenContent} event until its {@code LeaveContent} event arrives.
     * A previously cached open event for the same user, device and book is replaced.
     */
    private void handleOpenContent(BookLoreUser user, String serial, KoboEventsContainer.Event event) {
        Long bookId = resolveBookId(user, event);
        if (bookId == null) {
            return;
        }
        Float progress = event.getAttributes() == null ? null : event.getAttributes().getProgress();
        OpenEventKey key = new OpenEventKey(user.getId(), serial, bookId);
        CachedOpenEvent previous = openEventCache.asMap().put(key, new CachedOpenEvent(progress));
        if (previous != null) {
            log.debug("Replacing cached Kobo OpenContent event for book {} (previous had no matching LeaveContent)", bookId);
        }
    }

    /**
     * Stores a reading session for a {@code LeaveContent} event.
     * <p>
     * Events without reading activity, without a timestamp, or that duplicate an already stored session are ignored.
     * Start progress and progress delta are only set when the matching {@code OpenContent} event is still cached.
     */
    private void handleLeaveContent(BookLoreUser user, String serial, KoboEventsContainer.Event event) {
        Long bookId = resolveBookId(user, event);
        if (bookId == null) {
            return;
        }
        Long userId = user.getId();

        CachedOpenEvent startEvent = openEventCache.asMap().remove(new OpenEventKey(userId, serial, bookId));

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

    /**
     * Resolves the Grimmory book an event refers to.
     * <p>
     * Paid (Grimmory-synced) books carry the Grimmory book id as {@code volumeid}; sideloaded books are matched by title,
     * case-insensitively. Only books the user may access are considered, and an ambiguous title match is ignored.
     *
     * @return the book id, or {@code null} if the event cannot be attributed to exactly one accessible book
     */
    private Long resolveBookId(BookLoreUser user, KoboEventsContainer.Event event) {
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
                BookEntity book = bookRepository.findByIdWithMetadata(id).orElse(null);
                if (book == null || !hasAccess(user, book)) {
                    log.warn("Kobo event references unknown or inaccessible book id {}", id);
                    return null;
                }
                return id;
            }
            case MONETIZATION_TYPE_SIDELOADED -> {
                String title = attributes.getTitle();
                if (StringUtils.isBlank(title)) {
                    return null;
                }
                List<Long> candidates = bookMetadataRepository.findByTitleIgnoreCase(title.trim()).stream()
                        .map(BookMetadataEntity::getBookId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .filter(id -> bookRepository.findByIdWithMetadata(id).filter(book -> hasAccess(user, book)).isPresent())
                        .toList();
                if (candidates.isEmpty()) {
                    log.info("Kobo event for sideloaded book \"{}\" did not match any accessible book in Grimmory", title);
                    return null;
                }
                if (candidates.size() > 1) {
                    log.info("Kobo sideloaded title \"{}\" matches {} books; ignoring event", title, candidates.size());
                    return null;
                }
                return candidates.getFirst();
            }
            default -> {
                log.debug("Unknown Kobo monetization type {} on event {}", attributes.getMonetization(), event.getId());
                return null;
            }
        }
    }

    /**
     * Applies the same access rules as {@code BookAccessAspect}: admins may access every book, other users only books
     * in their assigned libraries that are not hidden by their content restrictions.
     */
    private boolean hasAccess(BookLoreUser user, BookEntity book) {
        if (user.getPermissions() != null && user.getPermissions().isAdmin()) {
            return true;
        }
        if (user.getAssignedLibraries() == null || book.getLibrary() == null) {
            return false;
        }
        Long libraryId = book.getLibrary().getId();
        boolean hasLibraryAccess = user.getAssignedLibraries().stream()
                .map(Library::getId)
                .anyMatch(libraryId::equals);
        return hasLibraryAccess && !contentRestrictionService.applyRestrictions(List.of(book), user.getId()).isEmpty();
    }
}
