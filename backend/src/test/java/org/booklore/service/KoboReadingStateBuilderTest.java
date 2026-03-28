package org.booklore.service;

import org.booklore.model.dto.KoboSyncSettings;
import org.booklore.model.dto.kobo.KoboReadingState;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.UserBookFileProgressEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.enums.KoboReadStatus;
import org.booklore.model.enums.ReadStatus;
import org.booklore.service.kobo.KoboBookmarkLocationResolver;
import org.booklore.service.kobo.KoboReadingStateBuilder;
import org.booklore.service.kobo.KoboSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("KoboReadingStateBuilder Tests")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KoboReadingStateBuilderTest {

    @Mock
    private KoboSettingsService koboSettingsService;

    @Mock
    private KoboBookmarkLocationResolver bookmarkLocationResolver;

    private KoboReadingStateBuilder builder;

    @BeforeEach
    void setUp() {
        KoboSyncSettings settings = new KoboSyncSettings();
        settings.setTwoWayProgressSync(true);
        when(koboSettingsService.getCurrentUserSettings()).thenReturn(settings);
        when(bookmarkLocationResolver.resolve(any(), any(), any())).thenReturn(java.util.Optional.empty());
        builder = new KoboReadingStateBuilder(koboSettingsService, bookmarkLocationResolver);
    }

    @Nested
    @DisplayName("Status Mapping - ReadStatus to KoboReadStatus")
    class StatusMapping {

        @Test
        @DisplayName("Should map null ReadStatus to READY_TO_READ")
        void mapNullStatus() {
            assertEquals(KoboReadStatus.READY_TO_READ, builder.mapReadStatusToKoboStatus(null));
        }

        @ParameterizedTest
        @MethodSource("finishedStatusProvider")
        @DisplayName("Should map completion statuses to FINISHED")
        void mapToFinished(ReadStatus input) {
            assertEquals(KoboReadStatus.FINISHED, builder.mapReadStatusToKoboStatus(input));
        }

        static Stream<Arguments> finishedStatusProvider() {
            return Stream.of(
                Arguments.of(ReadStatus.READ)
            );
        }

        @ParameterizedTest
        @MethodSource("readingStatusProvider")
        @DisplayName("Should map in-progress statuses to READING")
        void mapToReading(ReadStatus input) {
            assertEquals(KoboReadStatus.READING, builder.mapReadStatusToKoboStatus(input));
        }

        static Stream<Arguments> readingStatusProvider() {
            return Stream.of(
                Arguments.of(ReadStatus.PARTIALLY_READ),
                Arguments.of(ReadStatus.READING),
                Arguments.of(ReadStatus.RE_READING),
                Arguments.of(ReadStatus.PAUSED)
            );
        }

        @ParameterizedTest
        @MethodSource("readyToReadStatusProvider")
        @DisplayName("Should map non-started statuses to READY_TO_READ")
        void mapToReadyToRead(ReadStatus input) {
            assertEquals(KoboReadStatus.READY_TO_READ, builder.mapReadStatusToKoboStatus(input));
        }

        static Stream<Arguments> readyToReadStatusProvider() {
            return Stream.of(
                Arguments.of(ReadStatus.UNREAD),
                Arguments.of(ReadStatus.WONT_READ),
                Arguments.of(ReadStatus.ABANDONED)
            );
        }
    }

    @Nested
    @DisplayName("StatusInfo Building")
    class StatusInfoBuilding {

        @Test
        @DisplayName("Should build StatusInfo with FINISHED status and finishedDate")
        void buildStatusInfo_WithFinishedDate() {
            UserBookProgressEntity progress = new UserBookProgressEntity();
            progress.setReadStatus(ReadStatus.READ);
            progress.setDateFinished(Instant.parse("2025-11-15T10:30:00Z"));

            KoboReadingState.StatusInfo statusInfo = builder.buildStatusInfoFromProgress(
                progress, "2025-11-26T12:00:00Z");

            assertEquals(KoboReadStatus.FINISHED, statusInfo.getStatus());
            assertNotNull(statusInfo.getLastTimeFinished());
            assertEquals(1, statusInfo.getTimesStartedReading());
        }

        @Test
        @DisplayName("Should build StatusInfo with READING status")
        void buildStatusInfo_Reading() {
            UserBookProgressEntity progress = new UserBookProgressEntity();
            progress.setReadStatus(ReadStatus.READING);

            KoboReadingState.StatusInfo statusInfo = builder.buildStatusInfoFromProgress(
                progress, "2025-11-26T12:00:00Z");

            assertEquals(KoboReadStatus.READING, statusInfo.getStatus());
            assertNull(statusInfo.getLastTimeFinished());
            assertEquals(1, statusInfo.getTimesStartedReading());
        }

        @Test
        @DisplayName("Should build StatusInfo with READY_TO_READ and zero times started")
        void buildStatusInfo_ReadyToRead() {
            UserBookProgressEntity progress = new UserBookProgressEntity();
            progress.setReadStatus(ReadStatus.UNREAD);

            KoboReadingState.StatusInfo statusInfo = builder.buildStatusInfoFromProgress(
                progress, "2025-11-26T12:00:00Z");

            assertEquals(KoboReadStatus.READY_TO_READ, statusInfo.getStatus());
            assertNull(statusInfo.getLastTimeFinished());
            assertEquals(0, statusInfo.getTimesStartedReading());
        }

        @Test
        @DisplayName("Should handle FINISHED without dateFinished")
        void buildStatusInfo_FinishedWithoutDate() {
            UserBookProgressEntity progress = new UserBookProgressEntity();
            progress.setReadStatus(ReadStatus.READ);
            progress.setDateFinished(null);

            KoboReadingState.StatusInfo statusInfo = builder.buildStatusInfoFromProgress(
                progress, "2025-11-26T12:00:00Z");

            assertEquals(KoboReadStatus.FINISHED, statusInfo.getStatus());
            assertNull(statusInfo.getLastTimeFinished());
            assertEquals(1, statusInfo.getTimesStartedReading());
        }
    }

    @Nested
    @DisplayName("Bookmark Building")
    class BookmarkBuilding {

        @Test
        @DisplayName("Should build empty bookmark with timestamp")
        void buildEmptyBookmark() {
            OffsetDateTime timestamp = OffsetDateTime.now(ZoneOffset.UTC);
            KoboReadingState.CurrentBookmark bookmark = builder.buildEmptyBookmark(timestamp);

            assertNotNull(bookmark);
            assertEquals(timestamp.toString(), bookmark.getLastModified());
            assertNull(bookmark.getProgressPercent());
            assertNull(bookmark.getLocation());
        }

        @Test
        @DisplayName("Should build bookmark from progress with location")
        void buildBookmarkFromProgress_WithLocation() {
            UserBookProgressEntity progress = new UserBookProgressEntity();
            progress.setKoboProgressPercent(75.5f);
            progress.setKoboLocation("kobo.12.18");
            progress.setKoboLocationType("KoboSpan");
            progress.setKoboLocationSource("OEBPS/chapter3.xhtml");
            progress.setKoboProgressReceivedTime(Instant.parse("2025-11-26T10:00:00Z"));

            KoboReadingState.CurrentBookmark bookmark = builder.buildBookmarkFromProgress(progress, (UserBookFileProgressEntity) null);

            assertNotNull(bookmark);
            assertEquals(76, bookmark.getProgressPercent()); // Rounded
            assertNotNull(bookmark.getLocation());
            assertEquals("kobo.12.18", bookmark.getLocation().getValue());
            assertEquals("KoboSpan", bookmark.getLocation().getType());
            assertEquals("OEBPS/chapter3.xhtml", bookmark.getLocation().getSource());
        }

        @Test
        @DisplayName("Should build bookmark without location when null")
        void buildBookmarkFromProgress_NoLocation() {
            UserBookProgressEntity progress = new UserBookProgressEntity();
            progress.setKoboProgressPercent(50f);
            progress.setKoboLocation(null);
            progress.setKoboProgressReceivedTime(Instant.now());

            KoboReadingState.CurrentBookmark bookmark = builder.buildBookmarkFromProgress(progress, (UserBookFileProgressEntity) null);

            assertNotNull(bookmark);
            assertEquals(50, bookmark.getProgressPercent());
            assertNull(bookmark.getLocation());
        }

        @Test
        @DisplayName("Should not carry file content progress into Kobo bookmark")
        void buildBookmarkFromProgress_KoboBookmarkDoesNotReuseFileContentProgress() {
            UserBookProgressEntity progress = new UserBookProgressEntity();
            progress.setKoboProgressPercent(44f);
            progress.setKoboProgressReceivedTime(Instant.parse("2025-11-26T10:00:00Z"));

            UserBookFileProgressEntity fileProgress = new UserBookFileProgressEntity();
            fileProgress.setContentSourceProgressPercent(18.6f);

            KoboReadingState.CurrentBookmark bookmark = builder.buildBookmarkFromProgress(progress, fileProgress);

            assertNotNull(bookmark);
            assertEquals(44, bookmark.getProgressPercent());
            assertNull(bookmark.getContentSourceProgressPercent());
        }

        @Test
        @DisplayName("Should build web reader bookmark without a fallback location when no KoboSpan resolves")
        void buildBookmarkFromProgress_WebReaderLocation() {
            UserBookProgressEntity progress = new UserBookProgressEntity();
            progress.setEpubProgress("epubcfi(/6/4!/4/2/6/1:1)");
            progress.setEpubProgressHref("OPS/chapter1.xhtml");
            progress.setEpubProgressPercent(54.6f);
            progress.setLastReadTime(Instant.parse("2025-11-26T10:00:00Z"));

            UserBookFileProgressEntity fileProgress = new UserBookFileProgressEntity();
            fileProgress.setPositionData("epubcfi(/6/8!/4/2/6/1:15)");
            fileProgress.setPositionHref("OPS/chapter3.xhtml");
            fileProgress.setContentSourceProgressPercent(18.6f);

            KoboReadingState.CurrentBookmark bookmark = builder.buildBookmarkFromProgress(progress, fileProgress);

            assertNotNull(bookmark);
            assertEquals(55, bookmark.getProgressPercent());
            assertEquals(19, bookmark.getContentSourceProgressPercent());
            assertNull(bookmark.getLocation());
        }

        @Test
        @DisplayName("Should build web reader bookmark with resolved KoboSpan location")
        void buildBookmarkFromProgress_WebReaderResolvedKoboSpanLocation() {
            UserBookProgressEntity progress = new UserBookProgressEntity();
            progress.setEpubProgress("epubcfi(/6/4!/4/2/6/1:1)");
            progress.setEpubProgressHref("OPS/chapter3.xhtml");
            progress.setEpubProgressPercent(54.6f);
            progress.setLastReadTime(Instant.parse("2025-11-26T10:00:00Z"));

            UserBookFileProgressEntity fileProgress = new UserBookFileProgressEntity();
            fileProgress.setPositionData("epubcfi(/6/8!/4/2/6/1:15)");
            fileProgress.setPositionHref("OPS/chapter3.xhtml");

            when(bookmarkLocationResolver.resolve(progress, fileProgress, null))
                    .thenReturn(java.util.Optional.of(
                            new KoboBookmarkLocationResolver.ResolvedBookmarkLocation(
                                    "kobo.12.18",
                                    "KoboSpan",
                                    "OEBPS/OPS/chapter3.xhtml",
                                    18.6f)));

            KoboReadingState.CurrentBookmark bookmark = builder.buildBookmarkFromProgress(progress, fileProgress);

            assertNotNull(bookmark);
            assertEquals(55, bookmark.getProgressPercent());
            assertEquals(19, bookmark.getContentSourceProgressPercent());
            assertNotNull(bookmark.getLocation());
            assertEquals("kobo.12.18", bookmark.getLocation().getValue());
            assertEquals("KoboSpan", bookmark.getLocation().getType());
            assertEquals("OEBPS/OPS/chapter3.xhtml", bookmark.getLocation().getSource());
        }

        @Test
        @DisplayName("Should prefer web reader bookmark when Kobo bookmark timestamp is older")
        void buildBookmarkFromProgress_PrefersWebReaderWhenKoboTimestampIsOlder() {
            UserBookProgressEntity progress = new UserBookProgressEntity();
            progress.setEpubProgress("epubcfi(/6/4!/4/2/6/1:1)");
            progress.setEpubProgressHref("OPS/chapter3.xhtml");
            progress.setEpubProgressPercent(54.6f);
            progress.setLastReadTime(Instant.parse("2025-11-26T10:00:00Z"));
            progress.setKoboProgressPercent(12f);
            progress.setKoboLocation("kobo.1.1");
            progress.setKoboLocationType("KoboSpan");
            progress.setKoboLocationSource("OPS/chapter1.xhtml");
            progress.setKoboProgressReceivedTime(Instant.parse("2025-11-26T09:00:00Z"));

            UserBookFileProgressEntity fileProgress = new UserBookFileProgressEntity();
            fileProgress.setPositionData("epubcfi(/6/8!/4/2/6/1:15)");
            fileProgress.setPositionHref("OPS/chapter3.xhtml");

            KoboReadingState.CurrentBookmark bookmark = builder.buildBookmarkFromProgress(progress, fileProgress);

            assertNotNull(bookmark);
            assertEquals(55, bookmark.getProgressPercent());
            assertNull(bookmark.getLocation());
        }

        @Test
        @DisplayName("Should prefer web reader bookmark when newer progress has href and percent but no CFI")
        void buildBookmarkFromProgress_UsesWebReaderWithoutCfi() {
            UserBookProgressEntity progress = new UserBookProgressEntity();
            progress.setEpubProgressHref("OPS/chapter3.xhtml");
            progress.setEpubProgressPercent(54.6f);
            progress.setLastReadTime(Instant.parse("2025-11-26T10:00:00Z"));
            progress.setKoboProgressPercent(12f);
            progress.setKoboLocation("kobo.1.1");
            progress.setKoboLocationType("KoboSpan");
            progress.setKoboLocationSource("OPS/chapter1.xhtml");
            progress.setKoboProgressReceivedTime(Instant.parse("2025-11-26T09:00:00Z"));

            UserBookFileProgressEntity fileProgress = new UserBookFileProgressEntity();
            fileProgress.setPositionHref("OPS/chapter3.xhtml");
            fileProgress.setContentSourceProgressPercent(18.6f);

            KoboReadingState.CurrentBookmark bookmark = builder.buildBookmarkFromProgress(progress, fileProgress);

            assertNotNull(bookmark);
            assertEquals(55, bookmark.getProgressPercent());
            assertEquals(19, bookmark.getContentSourceProgressPercent());
            assertNull(bookmark.getLocation());
        }

        @Test
        @DisplayName("Should use default time when progress received time is null")
        void buildBookmarkFromProgress_UseDefaultTime() {
            UserBookProgressEntity progress = new UserBookProgressEntity();
            progress.setKoboProgressPercent(25f);
            progress.setKoboProgressReceivedTime(null);

            OffsetDateTime defaultTime = OffsetDateTime.parse("2025-11-26T12:00:00Z");
            KoboReadingState.CurrentBookmark bookmark = builder.buildBookmarkFromProgress(progress, null, defaultTime);

            assertNotNull(bookmark);
            assertEquals(defaultTime.toString(), bookmark.getLastModified());
        }

        @Test
        @DisplayName("Should round progress percentage correctly")
        void buildBookmarkFromProgress_RoundProgress() {
            UserBookProgressEntity progress = new UserBookProgressEntity();
            progress.setKoboProgressPercent(33.7f);
            progress.setKoboProgressReceivedTime(Instant.now());

            KoboReadingState.CurrentBookmark bookmark = builder.buildBookmarkFromProgress(progress, (UserBookFileProgressEntity) null);

            assertEquals(34, bookmark.getProgressPercent()); // Rounded up
        }
    }

    @Nested
    @DisplayName("Full ReadingState Building")
    class FullReadingStateBuilding {

        @Test
        @DisplayName("Should build complete reading state from progress")
        void buildReadingStateFromProgress() {
            BookEntity book = new BookEntity();
            book.setId(100L);

            UserBookProgressEntity progress = new UserBookProgressEntity();
            progress.setBook(book);
            progress.setKoboProgressPercent(75f);
            progress.setKoboLocation("kobo.1.1");
            progress.setKoboLocationType("KoboSpan");
            progress.setKoboLocationSource("OEBPS/CoverImage.xhtml");
            progress.setKoboProgressReceivedTime(Instant.parse("2025-11-26T10:00:00Z"));
            progress.setReadStatus(ReadStatus.READING);

            KoboReadingState state = builder.buildReadingStateFromProgress("100", progress, null);

            assertNotNull(state);
            assertEquals("100", state.getEntitlementId());
            assertNotNull(state.getCurrentBookmark());
            assertNotNull(state.getStatusInfo());
            assertEquals(KoboReadStatus.READING, state.getStatusInfo().getStatus());
        }
    }
}
