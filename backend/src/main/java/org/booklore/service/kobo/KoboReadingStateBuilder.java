package org.booklore.service.kobo;

import lombok.RequiredArgsConstructor;
import org.booklore.model.dto.kobo.KoboReadingState;
import org.booklore.model.entity.UserBookFileProgressEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.enums.KoboReadStatus;
import org.booklore.model.enums.ReadStatus;
import org.springframework.stereotype.Component;

import org.booklore.model.dto.kobo.KoboSpanPositionMap;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class KoboReadingStateBuilder {

    private final KoboSettingsService koboSettingsService;
    private final KoboBookmarkLocationResolver bookmarkLocationResolver;

    public KoboReadingState.CurrentBookmark buildEmptyBookmark(OffsetDateTime timestamp) {
        return KoboReadingState.CurrentBookmark.builder()
                .lastModified(timestamp.toString())
                .build();
    }

    public KoboReadingState.CurrentBookmark buildBookmarkFromProgress(UserBookProgressEntity progress,
                                                                      UserBookFileProgressEntity fileProgress) {
        return buildBookmarkFromProgress(progress, fileProgress, null, null);
    }

    public KoboReadingState.CurrentBookmark buildBookmarkFromProgress(UserBookProgressEntity progress,
                                                                      UserBookFileProgressEntity fileProgress,
                                                                      OffsetDateTime defaultTime) {
        return buildBookmarkFromProgress(progress, fileProgress, defaultTime, null);
    }

    public KoboReadingState.CurrentBookmark buildBookmarkFromProgress(UserBookProgressEntity progress,
                                                                      UserBookFileProgressEntity fileProgress,
                                                                      OffsetDateTime defaultTime,
                                                                      Map<Long, KoboSpanPositionMap> preloadedMaps) {
        if (isWebReaderNewer(progress)) {
            return buildBookmarkFromWebReaderProgress(progress, fileProgress, defaultTime, preloadedMaps);
        }
        return buildBookmarkFromKoboProgress(progress, defaultTime);
    }

    private boolean isWebReaderNewer(UserBookProgressEntity progress) {
        if (!koboSettingsService.getCurrentUserSettings().isTwoWayProgressSync()) {
            return false;
        }
        if (progress.getEpubProgress() == null || progress.getEpubProgressPercent() == null) {
            return false;
        }
        if (progress.getLastReadTime() == null) {
            return false;
        }
        if (progress.getKoboProgressReceivedTime() == null) {
            return true;
        }
        return !progress.getKoboProgressReceivedTime().isAfter(progress.getLastReadTime());
    }

    private KoboReadingState.CurrentBookmark buildBookmarkFromWebReaderProgress(UserBookProgressEntity progress,
                                                                                UserBookFileProgressEntity fileProgress,
                                                                                OffsetDateTime defaultTime,
                                                                                Map<Long, KoboSpanPositionMap> preloadedMaps) {
        String lastModified = Optional.ofNullable(progress.getLastReadTime())
                .map(this::formatTimestamp)
                .or(() -> Optional.ofNullable(defaultTime).map(OffsetDateTime::toString))
                .orElse(null);

        Optional<KoboBookmarkLocationResolver.ResolvedBookmarkLocation> resolvedLocation =
                bookmarkLocationResolver.resolve(progress, fileProgress, preloadedMaps);

        KoboReadingState.CurrentBookmark.Location location = resolvedLocation
                .map(resolved -> KoboReadingState.CurrentBookmark.Location.builder()
                        .value(resolved.value())
                        .type(resolved.type())
                        .source(resolved.source())
                        .build())
                .orElse(null);

        return KoboReadingState.CurrentBookmark.builder()
                .progressPercent(Math.round(progress.getEpubProgressPercent()))
                .contentSourceProgressPercent(resolvedLocation
                        .map(KoboBookmarkLocationResolver.ResolvedBookmarkLocation::contentSourceProgressPercent)
                        .map(Math::round)
                        .orElseGet(() -> Optional.ofNullable(fileProgress)
                                .map(UserBookFileProgressEntity::getContentSourceProgressPercent)
                                .map(Math::round)
                                .orElse(null)))
                .location(location)
                .lastModified(lastModified)
                .build();
    }

    private KoboReadingState.CurrentBookmark buildBookmarkFromKoboProgress(UserBookProgressEntity progress,
                                                                           OffsetDateTime defaultTime) {
        KoboReadingState.CurrentBookmark.Location location = Optional.ofNullable(progress.getKoboLocation())
                .map(koboLocation -> KoboReadingState.CurrentBookmark.Location.builder()
                        .value(koboLocation)
                        .type(progress.getKoboLocationType())
                        .source(progress.getKoboLocationSource())
                        .build())
                .orElse(null);

        String lastModified = Optional.ofNullable(progress.getKoboProgressReceivedTime())
                .map(this::formatTimestamp)
                .or(() -> Optional.ofNullable(defaultTime).map(OffsetDateTime::toString))
                .orElse(null);

        return KoboReadingState.CurrentBookmark.builder()
                .progressPercent(Optional.ofNullable(progress.getKoboProgressPercent())
                        .map(Math::round)
                        .orElse(null))
                .location(location)
                .lastModified(lastModified)
                .build();
    }

    public KoboReadingState buildReadingStateFromProgress(String entitlementId,
                                                          UserBookProgressEntity progress,
                                                          UserBookFileProgressEntity fileProgress) {
        KoboReadingState.CurrentBookmark bookmark = buildBookmarkFromProgress(progress, fileProgress);
        String lastModified = bookmark.getLastModified();
        KoboReadingState.StatusInfo statusInfo = buildStatusInfoFromProgress(progress, lastModified);

        return KoboReadingState.builder()
                .entitlementId(entitlementId)
                .currentBookmark(bookmark)
                .statusInfo(statusInfo)
                .created(lastModified)
                .lastModified(lastModified)
                .build();
    }

    public KoboReadingState.StatusInfo buildStatusInfoFromProgress(UserBookProgressEntity progress, String lastModified) {
        KoboReadStatus koboStatus = mapReadStatusToKoboStatus(progress.getReadStatus());
        int timesStartedReading = koboStatus == KoboReadStatus.READY_TO_READ ? 0 : 1;
        
        KoboReadingState.StatusInfo.StatusInfoBuilder builder = KoboReadingState.StatusInfo.builder()
                .lastModified(lastModified)
                .status(koboStatus)
                .timesStartedReading(timesStartedReading);
        
        if (koboStatus == KoboReadStatus.FINISHED && progress.getDateFinished() != null) {
            builder.lastTimeFinished(formatTimestamp(progress.getDateFinished()));
        }
        
        return builder.build();
    }
    
    public KoboReadStatus mapReadStatusToKoboStatus(ReadStatus readStatus) {
        if (readStatus == null) {
            return KoboReadStatus.READY_TO_READ;
        }
        
        return switch (readStatus) {
            case READ -> KoboReadStatus.FINISHED;
            case PARTIALLY_READ, READING, RE_READING, PAUSED -> KoboReadStatus.READING;
            case UNREAD, WONT_READ, ABANDONED, UNSET -> KoboReadStatus.READY_TO_READ;
        };
    }

    private String formatTimestamp(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC).toString();
    }
}
