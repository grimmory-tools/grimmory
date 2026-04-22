package org.booklore.service.kobo;

import lombok.RequiredArgsConstructor;
import org.booklore.model.dto.kobo.KoboReadingState;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.enums.KoboReadStatus;
import org.booklore.model.enums.ReadStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class KoboReadingStateBuilder {

    private final KoboSettingsService koboSettingsService;

    public KoboReadingState.CurrentBookmark buildEmptyBookmark(OffsetDateTime timestamp) {
        return KoboReadingState.CurrentBookmark.builder()
                .lastModified(timestamp.toString())
                .build();
    }

    public KoboReadingState.CurrentBookmark buildBookmarkFromProgress(UserBookProgressEntity progress) {
        return buildBookmarkFromProgress(progress, null);
    }

    public KoboReadingState.CurrentBookmark buildBookmarkFromProgress(UserBookProgressEntity progress, OffsetDateTime defaultTime) {
        if (isWebReaderNewer(progress)) {
            return buildBookmarkFromWebReaderProgress(progress, defaultTime);
        }
        return buildBookmarkFromKoboProgress(progress, defaultTime);
    }

    private boolean isWebReaderNewer(UserBookProgressEntity progress) {
        return koboSettingsService.getCurrentUserSettings().isTwoWayProgressSync()
                && progress.getEpubProgress() != null && progress.getEpubProgressPercent() != null;
    }

    private KoboReadingState.CurrentBookmark buildBookmarkFromWebReaderProgress(UserBookProgressEntity progress, OffsetDateTime defaultTime) {
        String lastModified = Optional.ofNullable(progress.getLastReadTime())
                .map(this::formatTimestamp)
                .or(() -> Optional.ofNullable(defaultTime).map(OffsetDateTime::toString))
                .orElse(null);

        return KoboReadingState.CurrentBookmark.builder()
                .progressPercent(Math.round(progress.getEpubProgressPercent()))
                .lastModified(lastModified)
                .build();
    }

    private KoboReadingState.CurrentBookmark buildBookmarkFromKoboProgress(UserBookProgressEntity progress, OffsetDateTime defaultTime) {
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

    public KoboReadingState buildReadingStateFromProgress(String entitlementId, UserBookProgressEntity progress) {
        KoboReadingState.CurrentBookmark bookmark = buildBookmarkFromProgress(progress);
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
