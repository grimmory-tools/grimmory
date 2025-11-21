package com.adityachandel.booklore.service.kobo;

import com.adityachandel.booklore.model.dto.kobo.KoboReadingState;
import com.adityachandel.booklore.model.entity.UserBookProgressEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Component
public class KoboReadingStateBuilder {

    public KoboReadingState.CurrentBookmark buildEmptyBookmark(OffsetDateTime timestamp) {
        return KoboReadingState.CurrentBookmark.builder()
                .lastModified(timestamp.toString())
                .build();
    }

    public KoboReadingState.CurrentBookmark buildBookmarkFromProgress(UserBookProgressEntity progress) {
        return buildBookmarkFromProgress(progress, null);
    }

    public KoboReadingState.CurrentBookmark buildBookmarkFromProgress(UserBookProgressEntity progress, OffsetDateTime defaultTime) {
        KoboReadingState.CurrentBookmark.Location location = Optional.ofNullable(progress.getKoboLocation())
                .map(loc -> KoboReadingState.CurrentBookmark.Location.builder()
                        .value(loc)
                        .type(progress.getKoboLocationType())
                        .source(progress.getKoboLocationSource())
                        .build())
                .orElse(null);

        String lastModified = Optional.ofNullable(progress.getKoboLastSyncTime())
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

        return KoboReadingState.builder()
                .entitlementId(entitlementId)
                .currentBookmark(bookmark)
                .created(lastModified)
                .lastModified(lastModified)
                .build();
    }

    private String formatTimestamp(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC).toString();
    }
}
