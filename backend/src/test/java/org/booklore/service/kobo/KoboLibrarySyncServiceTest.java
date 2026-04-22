package org.booklore.service.kobo;

import org.booklore.model.dto.KoboSyncSettings;
import org.booklore.model.dto.kobo.ChangedReadingState;
import org.booklore.model.dto.kobo.KoboReadingState;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.enums.ReadStatus;
import org.booklore.repository.KoboDeletedBookProgressRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.booklore.util.kobo.BookloreSyncTokenGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("KoboLibrarySyncService Tests")
class KoboLibrarySyncServiceTest {

    @Mock
    private BookloreSyncTokenGenerator tokenGenerator;
    @Mock
    private KoboLibrarySnapshotService koboLibrarySnapshotService;
    @Mock
    private KoboEntitlementService entitlementService;
    @Mock
    private KoboDeletedBookProgressRepository koboDeletedBookProgressRepository;
    @Mock
    private UserBookProgressRepository userBookProgressRepository;
    @Mock
    private KoboServerProxy koboServerProxy;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private KoboSettingsService koboSettingsService;

    @InjectMocks
    private KoboLibrarySyncService service;

    private KoboSyncSettings testSettings;

    @BeforeEach
    void setUp() {
        testSettings = new KoboSyncSettings();
        testSettings.setTwoWayProgressSync(false);
        when(koboSettingsService.getCurrentUserSettings()).thenReturn(testSettings);
    }

    @Nested
    @DisplayName("Sync Filtering - Two-Way Toggle Gating")
    class TwoWaySyncFiltering {

        @Test
        @DisplayName("Should filter out web-reader-only entries when two-way sync is OFF")
        void filterWebReaderOnlyEntries_whenToggleOff() {
            testSettings.setTwoWayProgressSync(false);

            UserBookProgressEntity webReaderOnly = createProgress(1L);
            webReaderOnly.setEpubProgress("epubcfi(/6/4)");
            webReaderOnly.setEpubProgressPercent(50f);
            webReaderOnly.setLastReadTime(Instant.now());
            webReaderOnly.setKoboProgressPercent(null);
            webReaderOnly.setKoboProgressReceivedTime(null);
            webReaderOnly.setReadStatusModifiedTime(null);

            assertFalse(needsStatusSync(webReaderOnly));
            assertFalse(needsKoboProgressSync(webReaderOnly));
        }

        @Test
        @DisplayName("Should include Kobo progress entries regardless of toggle state")
        void includeKoboProgressEntries_alwaysIncluded() {
            testSettings.setTwoWayProgressSync(false);

            UserBookProgressEntity koboProgress = createProgress(1L);
            koboProgress.setKoboProgressPercent(75f);
            koboProgress.setKoboProgressReceivedTime(Instant.now());
            koboProgress.setKoboProgressSentTime(null);

            assertTrue(needsKoboProgressSync(koboProgress));
        }

        @Test
        @DisplayName("Should include status sync entries regardless of toggle state")
        void includeStatusEntries_alwaysIncluded() {
            testSettings.setTwoWayProgressSync(false);

            UserBookProgressEntity statusProgress = createProgress(1L);
            statusProgress.setReadStatus(ReadStatus.READ);
            statusProgress.setReadStatusModifiedTime(Instant.now());
            statusProgress.setKoboStatusSentTime(null);

            assertTrue(needsStatusSync(statusProgress));
        }

        @Test
        @DisplayName("Should not filter web-reader entries when two-way sync is ON")
        void includeWebReaderEntries_whenToggleOn() {
            testSettings.setTwoWayProgressSync(true);

            UserBookProgressEntity webReaderProgress = createProgress(1L);
            webReaderProgress.setEpubProgress("epubcfi(/6/4)");
            webReaderProgress.setEpubProgressPercent(50f);
            webReaderProgress.setLastReadTime(Instant.now());

            assertNotNull(webReaderProgress.getEpubProgressPercent());
            assertNotNull(webReaderProgress.getLastReadTime());
        }
    }

    @Nested
    @DisplayName("Progress Sync Detection")
    class ProgressSyncDetection {

        @Test
        @DisplayName("Should detect unsynced Kobo progress when never sent")
        void needsKoboProgressSync_neverSent() {
            UserBookProgressEntity progress = createProgress(1L);
            progress.setKoboProgressReceivedTime(Instant.now());
            progress.setKoboProgressSentTime(null);

            assertTrue(needsKoboProgressSync(progress));
        }

        @Test
        @DisplayName("Should detect unsynced Kobo progress when received after sent")
        void needsKoboProgressSync_receivedAfterSent() {
            UserBookProgressEntity progress = createProgress(1L);
            progress.setKoboProgressReceivedTime(Instant.now());
            progress.setKoboProgressSentTime(Instant.now().minusSeconds(60));

            assertTrue(needsKoboProgressSync(progress));
        }

        @Test
        @DisplayName("Should not detect sync needed when sent after received")
        void needsKoboProgressSync_alreadySynced() {
            UserBookProgressEntity progress = createProgress(1L);
            progress.setKoboProgressReceivedTime(Instant.now().minusSeconds(60));
            progress.setKoboProgressSentTime(Instant.now());

            assertFalse(needsKoboProgressSync(progress));
        }

        @Test
        @DisplayName("Should not detect sync needed when no progress received")
        void needsKoboProgressSync_noProgressReceived() {
            UserBookProgressEntity progress = createProgress(1L);
            progress.setKoboProgressReceivedTime(null);

            assertFalse(needsKoboProgressSync(progress));
        }
    }

    @Nested
    @DisplayName("Status Sync Detection")
    class StatusSyncDetection {

        @Test
        @DisplayName("Should detect unsynced status when never sent")
        void needsStatusSync_neverSent() {
            UserBookProgressEntity progress = createProgress(1L);
            progress.setReadStatusModifiedTime(Instant.now());
            progress.setKoboStatusSentTime(null);

            assertTrue(needsStatusSync(progress));
        }

        @Test
        @DisplayName("Should detect unsynced status when modified after sent")
        void needsStatusSync_modifiedAfterSent() {
            UserBookProgressEntity progress = createProgress(1L);
            progress.setReadStatusModifiedTime(Instant.now());
            progress.setKoboStatusSentTime(Instant.now().minusSeconds(60));

            assertTrue(needsStatusSync(progress));
        }

        @Test
        @DisplayName("Should not detect status sync when already sent")
        void needsStatusSync_alreadySynced() {
            UserBookProgressEntity progress = createProgress(1L);
            progress.setReadStatusModifiedTime(Instant.now().minusSeconds(60));
            progress.setKoboStatusSentTime(Instant.now());

            assertFalse(needsStatusSync(progress));
        }

        @Test
        @DisplayName("Should not detect status sync when no modification time")
        void needsStatusSync_noModificationTime() {
            UserBookProgressEntity progress = createProgress(1L);
            progress.setReadStatusModifiedTime(null);

            assertFalse(needsStatusSync(progress));
        }
    }

    @Nested
    @DisplayName("Web Reader Progress Sync (Two-Way)")
    class WebReaderProgressSync {

        @Test
        @DisplayName("Should detect web reader progress needing sync when toggle ON and lastReadTime after sent")
        void needsProgressSync_webReaderNewer() {
            testSettings.setTwoWayProgressSync(true);

            UserBookProgressEntity progress = createProgress(1L);
            progress.setEpubProgress("epubcfi(/6/4)");
            progress.setEpubProgressPercent(65f);
            progress.setLastReadTime(Instant.now());
            progress.setKoboProgressSentTime(Instant.now().minusSeconds(60));
            progress.setKoboProgressReceivedTime(Instant.now().minusSeconds(120));

            assertTrue(needsProgressSync(progress));
        }

        @Test
        @DisplayName("Should not sync web reader progress when toggle OFF")
        void needsProgressSync_toggleOff() {
            testSettings.setTwoWayProgressSync(false);

            UserBookProgressEntity progress = createProgress(1L);
            progress.setEpubProgress("epubcfi(/6/4)");
            progress.setEpubProgressPercent(65f);
            progress.setLastReadTime(Instant.now());
            progress.setKoboProgressSentTime(Instant.now().minusSeconds(60));
            progress.setKoboProgressReceivedTime(null);

            assertFalse(needsProgressSync(progress));
        }

        @Test
        @DisplayName("Should not bounce Kobo progress back immediately")
        void needsProgressSync_preventBounce() {
            testSettings.setTwoWayProgressSync(true);

            UserBookProgressEntity progress = createProgress(1L);
            progress.setEpubProgress("epubcfi(/6/4)");
            progress.setEpubProgressPercent(65f);
            progress.setLastReadTime(Instant.now().minusSeconds(120));
            progress.setKoboProgressSentTime(Instant.now().minusSeconds(60));
            progress.setKoboProgressReceivedTime(Instant.now());

            assertFalse(needsProgressSyncWebReader(progress));
        }
    }

    private UserBookProgressEntity createProgress(Long bookId) {
        BookEntity book = new BookEntity();
        book.setId(bookId);
        UserBookProgressEntity progress = new UserBookProgressEntity();
        progress.setBook(book);
        return progress;
    }

    private boolean needsStatusSync(UserBookProgressEntity progress) {
        Instant modifiedTime = progress.getReadStatusModifiedTime();
        if (modifiedTime == null) return false;
        Instant sentTime = progress.getKoboStatusSentTime();
        return sentTime == null || modifiedTime.isAfter(sentTime);
    }

    private boolean needsKoboProgressSync(UserBookProgressEntity progress) {
        Instant sentTime = progress.getKoboProgressSentTime();
        Instant receivedTime = progress.getKoboProgressReceivedTime();
        return receivedTime != null && (sentTime == null || receivedTime.isAfter(sentTime));
    }

    private boolean needsProgressSync(UserBookProgressEntity progress) {
        if (needsKoboProgressSync(progress)) return true;

        if (testSettings.isTwoWayProgressSync()
                && progress.getEpubProgress() != null && progress.getEpubProgressPercent() != null) {
            Instant sentTime = progress.getKoboProgressSentTime();
            Instant lastReadTime = progress.getLastReadTime();
            if (lastReadTime != null && (sentTime == null || lastReadTime.isAfter(sentTime))) {
                return true;
            }
        }
        return false;
    }

    private boolean needsProgressSyncWebReader(UserBookProgressEntity progress) {
        if (!testSettings.isTwoWayProgressSync()) return false;
        if (progress.getEpubProgress() == null || progress.getEpubProgressPercent() == null) return false;

        Instant lastReadTime = progress.getLastReadTime();
        Instant sentTime = progress.getKoboProgressSentTime();
        Instant receivedTime = progress.getKoboProgressReceivedTime();

        if (lastReadTime == null) return false;
        if (sentTime != null && !lastReadTime.isAfter(sentTime)) return false;
        return receivedTime == null || lastReadTime.isAfter(receivedTime);
    }
}
