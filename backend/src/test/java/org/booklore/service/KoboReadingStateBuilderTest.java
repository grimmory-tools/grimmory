package org.booklore.service;

import org.booklore.model.dto.KoboSyncSettings;
import org.booklore.model.dto.kobo.KoboReadingState;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.enums.KoboReadStatus;
import org.booklore.model.enums.ReadStatus;
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
import static org.mockito.Mockito.when;

@DisplayName("KoboReadingStateBuilder Tests")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KoboReadingStateBuilderTest {

    @Mock
    private KoboSettingsService koboSettingsService;

    private KoboReadingStateBuilder builder;

    @BeforeEach
    void setUp() {
        KoboSyncSettings settings = new KoboSyncSettings();
        settings.setTwoWayProgressSync(true);
        when(koboSettingsService.getCurrentUserSettings()).thenReturn(settings);
        builder = new KoboReadingStateBuilder(koboSettingsService);
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
            progress.setKoboLocation("epubcfi(/6/4[chap01ref]!/4/2/1:3)");
            progress.setKoboLocationType("EpubCfi");
            progress.setKoboLocationSource("Kobo");
            progress.setKoboProgressReceivedTime(Instant.parse("2025-11-26T10:00:00Z"));

            KoboReadingState.CurrentBookmark bookmark = builder.buildBookmarkFromProgress(progress);

            assertNotNull(bookmark);
            assertEquals(76, bookmark.getProgressPercent()); // Rounded
            assertNotNull(bookmark.getLocation());
            assertEquals("epubcfi(/6/4[chap01ref]!/4/2/1:3)", bookmark.getLocation().getValue());
            assertEquals("EpubCfi", bookmark.getLocation().getType());
            assertEquals("Kobo", bookmark.getLocation().getSource());
        }

        @Test
        @DisplayName("Should build bookmark without location when null")
        void buildBookmarkFromProgress_NoLocation() {
            UserBookProgressEntity progress = new UserBookProgressEntity();
            progress.setKoboProgressPercent(50f);
            progress.setKoboLocation(null);
            progress.setKoboProgressReceivedTime(Instant.now());

            KoboReadingState.CurrentBookmark bookmark = builder.buildBookmarkFromProgress(progress);

            assertNotNull(bookmark);
            assertEquals(50, bookmark.getProgressPercent());
            assertNull(bookmark.getLocation());
        }

        @Test
        @DisplayName("Should use default time when progress received time is null")
        void buildBookmarkFromProgress_UseDefaultTime() {
            UserBookProgressEntity progress = new UserBookProgressEntity();
            progress.setKoboProgressPercent(25f);
            progress.setKoboProgressReceivedTime(null);

            OffsetDateTime defaultTime = OffsetDateTime.parse("2025-11-26T12:00:00Z");
            KoboReadingState.CurrentBookmark bookmark = builder.buildBookmarkFromProgress(progress, defaultTime);

            assertNotNull(bookmark);
            assertEquals(defaultTime.toString(), bookmark.getLastModified());
        }

        @Test
        @DisplayName("Should round progress percentage correctly")
        void buildBookmarkFromProgress_RoundProgress() {
            UserBookProgressEntity progress = new UserBookProgressEntity();
            progress.setKoboProgressPercent(33.7f);
            progress.setKoboProgressReceivedTime(Instant.now());

            KoboReadingState.CurrentBookmark bookmark = builder.buildBookmarkFromProgress(progress);

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
            progress.setKoboLocation("epubcfi(/6/4!)");
            progress.setKoboLocationType("EpubCfi");
            progress.setKoboLocationSource("Kobo");
            progress.setKoboProgressReceivedTime(Instant.parse("2025-11-26T10:00:00Z"));
            progress.setReadStatus(ReadStatus.READING);

            KoboReadingState state = builder.buildReadingStateFromProgress("100", progress);

            assertNotNull(state);
            assertEquals("100", state.getEntitlementId());
            assertNotNull(state.getCurrentBookmark());
            assertNotNull(state.getStatusInfo());
            assertEquals(KoboReadStatus.READING, state.getStatusInfo().getStatus());
        }
    }
}
