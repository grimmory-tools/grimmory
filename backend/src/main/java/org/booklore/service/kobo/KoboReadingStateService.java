package org.booklore.service.kobo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.mapper.KoboReadingStateMapper;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.KoboSyncSettings;
import org.booklore.model.dto.kobo.KoboReadingState;
import org.booklore.model.dto.response.kobo.KoboReadingStateResponse;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.KoboReadingStateEntity;
import org.booklore.model.entity.UserBookFileProgressEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.enums.ReadStatus;
import org.booklore.repository.*;
import org.booklore.service.hardcover.HardcoverSyncService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KoboReadingStateService {

    private static final int STATUS_SYNC_BUFFER_SECONDS = 10;
    private static final DateTimeFormatter KOBO_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSS'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter LOCAL_TIMESTAMP_FORMAT = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .optionalEnd()
            .toFormatter();

    private final KoboReadingStateRepository repository;
    private final KoboReadingStateMapper mapper;
    private final UserBookProgressRepository progressRepository;
    private final UserBookFileProgressRepository fileProgressRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final AuthenticationService authenticationService;
    private final KoboSettingsService koboSettingsService;
    private final KoboReadingStateBuilder readingStateBuilder;
    private final HardcoverSyncService hardcoverSyncService;

    @Transactional
    public KoboReadingStateResponse saveReadingState(List<KoboReadingState> readingStates) {
        normalizePutTimestamps(readingStates);
        List<KoboReadingState> koboReadingStates = saveAll(readingStates);

        List<KoboReadingStateResponse.UpdateResult> updateResults = koboReadingStates.stream()
                .map(state -> KoboReadingStateResponse.UpdateResult.builder()
                        .entitlementId(state.getEntitlementId())
                        .currentBookmarkResult(KoboReadingStateResponse.Result.success())
                        .statisticsResult(KoboReadingStateResponse.Result.success())
                        .statusInfoResult(KoboReadingStateResponse.Result.success())
                        .build())
                .collect(Collectors.toList());

        return KoboReadingStateResponse.builder()
                .requestResult("Success")
                .updateResults(updateResults)
                .build();
    }

    private List<KoboReadingState> saveAll(List<KoboReadingState> dtos) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();

        return dtos.stream()
                .map(dto -> {
                    String entitlementId = mapper.cleanString(dto.getEntitlementId());
                    Optional<KoboReadingStateEntity> existingOpt =
                            repository.findByEntitlementIdAndUserId(entitlementId, userId);
                    log.debug("Kobo reading state lookup: entitlementId={}, foundExisting={}",
                            entitlementId, existingOpt.isPresent());
                    KoboReadingStateEntity entity = existingOpt
                            .map(existing -> mergeReadingState(existing, dto))
                            .orElseGet(() -> {
                                KoboReadingStateEntity newEntity = mapper.toEntity(dto);
                                newEntity.setUserId(userId);
                                if (entitlementId != null && !entitlementId.isBlank()) {
                                    newEntity.setEntitlementId(entitlementId);
                                }
                                String created = normalizeTimestampValue(dto.getCreated());
                                if (isBlank(created)) {
                                    created = KOBO_TIMESTAMP_FORMAT.format(Instant.now());
                                }
                                String lastModified = normalizeTimestampValue(dto.getLastModified());
                                if (isBlank(lastModified)) {
                                    lastModified = created;
                                }
                                dto.setLastModified(lastModified);
                                dto.setCreated(created);
                                newEntity.setLastModifiedString(mapper.cleanString(lastModified));
                                newEntity.setPriorityTimestamp(mapper.cleanString(computePriorityTimestamp(dto)));
                                newEntity.setCreated(mapper.cleanString(created));
                                return newEntity;
                            });

                    KoboReadingStateEntity savedEntity = repository.save(entity);
                    KoboReadingState savedState = mapper.toDto(savedEntity);

                    syncKoboProgressToUserBookProgress(savedState, userId);

                    return savedEntity;
                })
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteReadingState(Long bookId) {
        Long userId = authenticationService.getAuthenticatedUser().getId();
        repository.findByEntitlementIdAndUserId(String.valueOf(bookId), userId).ifPresent(repository::delete);
    }

    public List<KoboReadingState> getReadingState(String entitlementId) {
        Long userId = authenticationService.getAuthenticatedUser().getId();
        Optional<KoboReadingState> readingState = repository.findByEntitlementIdAndUserId(entitlementId, userId)
                .map(mapper::toDto)
                .or(() -> repository
                        .findFirstByEntitlementIdAndUserIdIsNullOrderByPriorityTimestampDescLastModifiedStringDescIdDesc(
                                entitlementId)
                        .map(mapper::toDto))
                .or(() -> constructReadingStateFromProgress(entitlementId));

        readingState.ifPresent(state -> overlayWebReaderBookmark(state, entitlementId, userId));

        return readingState
                .map(state -> {
                    normalizeResponseTimestamps(state);
                    return List.of(state);
                })
                .orElse(List.of());
    }

    private void overlayWebReaderBookmark(KoboReadingState state, String entitlementId, Long userId) {
        if (!koboSettingsService.getCurrentUserSettings().isTwoWayProgressSync()) {
            return;
        }
        try {
            Long bookId = Long.parseLong(entitlementId);
            progressRepository.findByUserIdAndBookId(userId, bookId)
                    .filter(progress -> progress.getEpubProgress() != null && progress.getEpubProgressPercent() != null)
                    .ifPresent(progress -> {
                        KoboReadingState.CurrentBookmark existing = state.getCurrentBookmark();
                        KoboReadingState.CurrentBookmark webReaderBookmark = readingStateBuilder.buildBookmarkFromProgress(progress);

                        if (existing != null) {
                            existing.setProgressPercent(webReaderBookmark.getProgressPercent());
                            existing.setLastModified(webReaderBookmark.getLastModified());
                        } else {
                            state.setCurrentBookmark(webReaderBookmark);
                        }
                    });
        } catch (NumberFormatException e) {
            // Not a valid book ID, skip overlay
        }
    }

    private Optional<KoboReadingState> constructReadingStateFromProgress(String entitlementId) {
        try {
            Long bookId = Long.parseLong(entitlementId);
            BookLoreUser user = authenticationService.getAuthenticatedUser();

            boolean twoWaySync = koboSettingsService.getCurrentUserSettings().isTwoWayProgressSync();
            return progressRepository.findByUserIdAndBookId(user.getId(), bookId)
                    .filter(progress -> progress.getKoboProgressPercent() != null || progress.getKoboLocation() != null || (twoWaySync && progress.getEpubProgressPercent() != null))
                    .map(progress -> readingStateBuilder.buildReadingStateFromProgress(entitlementId, progress));
        } catch (NumberFormatException e) {
            log.warn("Invalid entitlement ID format when constructing reading state: {}", entitlementId);
            return Optional.empty();
        }
    }

    private void syncKoboProgressToUserBookProgress(KoboReadingState readingState, Long userId) {
        try {
            Long bookId = Long.parseLong(readingState.getEntitlementId());

            Optional<BookEntity> bookOpt = bookRepository.findById(bookId);
            if (bookOpt.isEmpty()) {
                log.warn("Book not found for entitlement ID: {}", readingState.getEntitlementId());
                return;
            }

            BookEntity book = bookOpt.get();
            Optional<BookLoreUserEntity> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                log.warn("User not found: {}", userId);
                return;
            }

            UserBookProgressEntity progress = progressRepository.findByUserIdAndBookId(userId, bookId)
                    .orElseGet(() -> {
                        UserBookProgressEntity newProgress = new UserBookProgressEntity();
                        newProgress.setUser(userOpt.get());
                        newProgress.setBook(book);
                        return newProgress;
                    });

            Float prevousKoboProgressPercent = progress.getKoboProgressPercent();
            ReadStatus previousReadStatus = progress.getReadStatus();

            KoboReadingState.CurrentBookmark bookmark = readingState.getCurrentBookmark();
            if (bookmark != null) {
                if (bookmark.getProgressPercent() != null) {
                    progress.setKoboProgressPercent(bookmark.getProgressPercent().floatValue());
                }

                KoboReadingState.CurrentBookmark.Location location = bookmark.getLocation();
                if (location != null) {
                    progress.setKoboLocation(location.getValue());
                    progress.setKoboLocationType(location.getType());
                    progress.setKoboLocationSource(location.getSource());
                }
            }

            Instant now = Instant.now();
            progress.setKoboProgressReceivedTime(now);

            boolean koboApplied = crossPopulateEpubFieldsFromKobo(progress, bookmark, book, userId, now);
            if (koboApplied) {
                progress.setLastReadTime(now);
            }

            if (progress.getKoboProgressPercent() != null) {
                updateReadStatusFromKoboProgress(progress, now);
            }

            progressRepository.save(progress);

            // Sync progress to Hardcover asynchronously (if enabled for this user)
            // But only if the progress percentage has changed from last time, or the read status has changed
            if (progress.getKoboProgressPercent() != null
                    && (!progress.getKoboProgressPercent().equals(prevousKoboProgressPercent)
                    || progress.getReadStatus() != previousReadStatus)) {
                hardcoverSyncService.syncProgressToHardcover(book.getId(), progress.getKoboProgressPercent(), userId);
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid entitlement ID format: {}", readingState.getEntitlementId());
        }
    }

    private boolean crossPopulateEpubFieldsFromKobo(UserBookProgressEntity progress,
                                                      KoboReadingState.CurrentBookmark bookmark,
                                                      BookEntity book, Long userId, Instant now) {
        if (bookmark == null || bookmark.getProgressPercent() == null) {
            return false;
        }

        if (!koboSettingsService.getCurrentUserSettings().isTwoWayProgressSync()) {
            return false;
        }

        UserBookFileProgressEntity fileProgress = book.getPrimaryBookFile() != null
                ? fileProgressRepository.findByUserIdAndBookFileId(userId, book.getPrimaryBookFile().getId()).orElse(null)
                : null;

        Instant bookmarkTime = parseTimestamp(bookmark.getLastModified());
        boolean webReaderIsNewer = fileProgress != null
                && fileProgress.getLastReadTime() != null
                && bookmarkTime != null
                && fileProgress.getLastReadTime().isAfter(bookmarkTime);

        if (webReaderIsNewer) {
            return false;
        }

        progress.setEpubProgressPercent(bookmark.getProgressPercent().floatValue());
        KoboReadingState.CurrentBookmark.Location location = bookmark.getLocation();
        if (location != null && location.getSource() != null) {
            progress.setEpubProgressHref(location.getSource());
        }
        progress.setEpubProgress(null);

        if (fileProgress != null) {
            fileProgress.setProgressPercent(bookmark.getProgressPercent().floatValue());
            if (location != null && location.getSource() != null) {
                fileProgress.setPositionHref(location.getSource());
            }
            fileProgress.setPositionData(null);
            fileProgress.setLastReadTime(now);
            fileProgressRepository.save(fileProgress);
        }

        return true;
    }

    private void normalizePutTimestamps(List<KoboReadingState> readingStates) {
        if (readingStates == null || readingStates.isEmpty()) {
            return;
        }
        String requestTimestamp = KOBO_TIMESTAMP_FORMAT.format(Instant.now());
        readingStates.forEach(state -> {
            if (isBlank(state.getPriorityTimestamp())) {
                state.setPriorityTimestamp(requestTimestamp);
            }
            if (isBlank(state.getLastModified())) {
                state.setLastModified(requestTimestamp);
            }
            if (state.getStatusInfo() != null) {
                if (isBlank(state.getStatusInfo().getLastModified())) {
                    state.getStatusInfo().setLastModified(requestTimestamp);
                }
            }
            if (state.getStatistics() != null) {
                if (isBlank(state.getStatistics().getLastModified())) {
                    state.getStatistics().setLastModified(requestTimestamp);
                }
            }
            if (state.getCurrentBookmark() != null) {
                if (isBlank(state.getCurrentBookmark().getLastModified())) {
                    state.getCurrentBookmark().setLastModified(requestTimestamp);
                }
            }
        });
    }

    private void normalizeResponseTimestamps(KoboReadingState state) {
        if (state == null) {
            return;
        }
        state.setCreated(normalizeTimestampValue(state.getCreated()));
        state.setLastModified(normalizeTimestampValue(state.getLastModified()));
        state.setPriorityTimestamp(normalizeTimestampValue(state.getPriorityTimestamp()));
        if (state.getStatusInfo() != null) {
            state.getStatusInfo().setLastModified(normalizeTimestampValue(state.getStatusInfo().getLastModified()));
        }
        if (state.getStatistics() != null) {
            state.getStatistics().setLastModified(normalizeTimestampValue(state.getStatistics().getLastModified()));
        }
        if (state.getCurrentBookmark() != null) {
            state.getCurrentBookmark().setLastModified(normalizeTimestampValue(state.getCurrentBookmark().getLastModified()));
        }
    }

    private String normalizeTimestampValue(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed)) {
            return value;
        }
        try {
            Instant instant = Instant.parse(trimmed).truncatedTo(ChronoUnit.SECONDS);
            return KOBO_TIMESTAMP_FORMAT.format(instant);
        } catch (Exception ignored) {
        }
        try {
            OffsetDateTime offsetDateTime = OffsetDateTime.parse(trimmed);
            return KOBO_TIMESTAMP_FORMAT.format(offsetDateTime.toInstant().truncatedTo(ChronoUnit.SECONDS));
        } catch (Exception ignored) {
        }
        try {
            LocalDateTime localDateTime = LocalDateTime.parse(trimmed, LOCAL_TIMESTAMP_FORMAT);
            return KOBO_TIMESTAMP_FORMAT.format(localDateTime.toInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
        } catch (Exception ignored) {
        }
        return value;
    }

    private KoboReadingStateEntity mergeReadingState(KoboReadingStateEntity existing, KoboReadingState incoming) {
        KoboReadingState existingState = mapper.toDto(existing);
        if (existingState == null) {
            existingState = KoboReadingState.builder().entitlementId(existing.getEntitlementId()).build();
        }

        KoboReadingState merged = mergeReadingState(existingState, incoming);

        existing.setCurrentBookmarkJson(mapper.toJson(merged.getCurrentBookmark()));
        existing.setStatisticsJson(mapper.toJson(merged.getStatistics()));
        existing.setStatusInfoJson(mapper.toJson(merged.getStatusInfo()));
        existing.setLastModifiedString(mapper.cleanString(merged.getLastModified()));
        existing.setPriorityTimestamp(mapper.cleanString(merged.getPriorityTimestamp()));
        return existing;
    }

    private KoboReadingState mergeReadingState(KoboReadingState existing, KoboReadingState incoming) {
        KoboReadingState.StatusInfo statusInfo = existing.getStatusInfo();
        KoboReadingState.Statistics statistics = existing.getStatistics();
        KoboReadingState.CurrentBookmark currentBookmark = existing.getCurrentBookmark();
        String lastModified = existing.getLastModified();

        if (incoming.getStatusInfo() != null && isIncomingNewer(incoming.getStatusInfo().getLastModified(),
                statusInfo != null ? statusInfo.getLastModified() : null)) {
            statusInfo = incoming.getStatusInfo();
        }
        if (incoming.getStatistics() != null && isIncomingNewer(incoming.getStatistics().getLastModified(),
                statistics != null ? statistics.getLastModified() : null)) {
            statistics = incoming.getStatistics();
        }
        if (incoming.getCurrentBookmark() != null && isIncomingNewer(incoming.getCurrentBookmark().getLastModified(),
                currentBookmark != null ? currentBookmark.getLastModified() : null)) {
            currentBookmark = incoming.getCurrentBookmark();
        }
        if (isIncomingNewer(incoming.getLastModified(), lastModified)) {
            lastModified = incoming.getLastModified();
        }

        KoboReadingState merged = KoboReadingState.builder()
                .entitlementId(existing.getEntitlementId())
                .created(firstNonBlank(existing.getCreated(), incoming.getCreated()))
                .lastModified(lastModified)
                .statusInfo(statusInfo)
                .statistics(statistics)
                .currentBookmark(currentBookmark)
                .build();
        merged.setPriorityTimestamp(computePriorityTimestamp(merged));
        return merged;
    }

    private boolean isIncomingNewer(String incoming, String existing) {
        if (isBlank(incoming)) {
            return false;
        }
        if (isBlank(existing)) {
            return true;
        }
        Instant incomingInstant = parseTimestamp(incoming);
        Instant existingInstant = parseTimestamp(existing);
        if (incomingInstant == null || existingInstant == null) {
            return false;
        }
        return incomingInstant.isAfter(existingInstant);
    }

    private String computePriorityTimestamp(KoboReadingState state) {
        Instant maxInstant = maxInstant(
                state.getLastModified(),
                state.getStatusInfo() != null ? state.getStatusInfo().getLastModified() : null,
                state.getStatistics() != null ? state.getStatistics().getLastModified() : null,
                state.getCurrentBookmark() != null ? state.getCurrentBookmark().getLastModified() : null
        );
        if (maxInstant == null) {
            String fallback = firstNonBlank(state.getPriorityTimestamp(), state.getLastModified());
            return normalizeTimestampValue(fallback);
        }
        return KOBO_TIMESTAMP_FORMAT.format(maxInstant);
    }

    private Instant maxInstant(String... candidates) {
        Instant max = null;
        for (String candidate : candidates) {
            Instant parsed = parseTimestamp(candidate);
            if (parsed == null) {
                continue;
            }
            if (max == null || parsed.isAfter(max)) {
                max = parsed;
            }
        }
        return max;
    }

    private Instant parseTimestamp(String value) {
        if (isBlank(value)) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return Instant.parse(trimmed);
        } catch (Exception ignored) {
        }
        try {
            return OffsetDateTime.parse(trimmed).toInstant();
        } catch (Exception ignored) {
        }
        try {
            LocalDateTime localDateTime = LocalDateTime.parse(trimmed, LOCAL_TIMESTAMP_FORMAT);
            return localDateTime.toInstant(ZoneOffset.UTC);
        } catch (Exception ignored) {
        }
        return null;
    }

    private String firstNonBlank(String primary, String fallback) {
        return isBlank(primary) ? fallback : primary;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty() || "null".equalsIgnoreCase(value.trim());
    }

    private void updateReadStatusFromKoboProgress(UserBookProgressEntity userProgress, Instant now) {
        if (shouldPreserveCurrentStatus(userProgress, now)) {
            return;
        }

        double koboProgressPercent = userProgress.getKoboProgressPercent();

        ReadStatus derivedStatus = deriveStatusFromProgress(koboProgressPercent);
        userProgress.setReadStatus(derivedStatus);

        if (derivedStatus == ReadStatus.READ && userProgress.getDateFinished() == null) {
            userProgress.setDateFinished(Instant.now());
        }
    }

    private boolean shouldPreserveCurrentStatus(UserBookProgressEntity progress, Instant now) {
        Instant statusModifiedTime = progress.getReadStatusModifiedTime();
        if (statusModifiedTime == null) {
            return false;
        }

        Instant statusSentTime = progress.getKoboStatusSentTime();

        boolean hasPendingStatusUpdate = statusSentTime == null || statusModifiedTime.isAfter(statusSentTime);
        if (hasPendingStatusUpdate) {
            return true;
        }

        return now.isBefore(statusSentTime.plusSeconds(STATUS_SYNC_BUFFER_SECONDS));
    }

    private ReadStatus deriveStatusFromProgress(double progressPercent) {
        KoboSyncSettings settings = koboSettingsService.getCurrentUserSettings();

        float finishedThreshold = settings.getProgressMarkAsFinishedThreshold() != null
                ? settings.getProgressMarkAsFinishedThreshold() : 99f;
        float readingThreshold = settings.getProgressMarkAsReadingThreshold() != null
                ? settings.getProgressMarkAsReadingThreshold() : 1f;

        if (progressPercent >= finishedThreshold) return ReadStatus.READ;
        if (progressPercent >= readingThreshold) return ReadStatus.READING;
        return ReadStatus.UNREAD;
    }
}
