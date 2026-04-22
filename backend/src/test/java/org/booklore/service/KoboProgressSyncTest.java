package org.booklore.service;

import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.enums.ReadStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Kobo Progress Sync Behavior")
class KoboProgressSyncTest {

    @Nested
    @DisplayName("When Kobo receives progress from a device")
    class WhenKoboReceivesProgress {

        @Test
        @DisplayName("New progress should be marked for sync to other devices")
        void newProgress_shouldBeMarkedForSync() {
            UserBookProgressEntity progress = createProgressForBook(1L);
            progress.setKoboProgressPercent(50f);
            progress.setKoboProgressReceivedTime(Instant.now());
            progress.setKoboProgressSentTime(null);

            assertTrue(hasUnsyncedProgress(progress));
        }

        @Test
        @DisplayName("Already synced progress should not sync again")
        void alreadySyncedProgress_shouldNotSyncAgain() {
            UserBookProgressEntity progress = createProgressForBook(1L);
            Instant receivedTime = Instant.now().minusSeconds(60);
            Instant sentTime = Instant.now().minusSeconds(30);
            
            progress.setKoboProgressReceivedTime(receivedTime);
            progress.setKoboProgressSentTime(sentTime);

            assertFalse(hasUnsyncedProgress(progress));
        }

        @Test
        @DisplayName("Updated progress after sync should be marked for re-sync")
        void updatedProgressAfterSync_shouldBeMarkedForReSync() {
            UserBookProgressEntity progress = createProgressForBook(1L);
            Instant oldSentTime = Instant.now().minusSeconds(60);
            Instant newReceivedTime = Instant.now().minusSeconds(30);
            
            progress.setKoboProgressSentTime(oldSentTime);
            progress.setKoboProgressReceivedTime(newReceivedTime);

            assertTrue(hasUnsyncedProgress(progress));
        }
    }

    @Nested
    @DisplayName("When user changes read status in BookLore")
    class WhenUserChangesStatus {

        @Test
        @DisplayName("New status change should be marked for sync")
        void newStatusChange_shouldBeMarkedForSync() {
            UserBookProgressEntity progress = createProgressForBook(1L);
            progress.setReadStatus(ReadStatus.READ);
            progress.setReadStatusModifiedTime(Instant.now());
            progress.setKoboStatusSentTime(null);

            assertTrue(hasUnsyncedStatus(progress));
        }

        @Test
        @DisplayName("Already synced status should not sync again")
        void alreadySyncedStatus_shouldNotSyncAgain() {
            UserBookProgressEntity progress = createProgressForBook(1L);
            Instant modifiedTime = Instant.now().minusSeconds(60);
            Instant sentTime = Instant.now().minusSeconds(30);
            
            progress.setReadStatusModifiedTime(modifiedTime);
            progress.setKoboStatusSentTime(sentTime);

            assertFalse(hasUnsyncedStatus(progress));
        }
    }

    @Nested
    @DisplayName("Kobo-to-Kobo sync scenarios")
    class KoboToKoboSync {

        @Test
        @DisplayName("Progress from Device A should sync to Device B")
        void progressFromDeviceA_shouldSyncToDeviceB() {
            UserBookProgressEntity progress = createProgressForBook(1L);
            
            // Device A sends progress
            progress.setKoboProgressPercent(50f);
            progress.setKoboLocation("epubcfi(/6/10)");
            progress.setKoboProgressReceivedTime(Instant.now());
            
            // Should need sync (to Device B)
            assertTrue(hasUnsyncedProgress(progress));
            
            // After sync completes
            progress.setKoboProgressSentTime(Instant.now());
            
            // Should no longer need sync
            assertFalse(hasUnsyncedProgress(progress));
        }

        @Test
        @DisplayName("Both status and progress changes should sync together")
        void statusAndProgressChanges_shouldSyncTogether() {
            UserBookProgressEntity progress = createProgressForBook(1L);
            
            // User marks as read in BookLore
            progress.setReadStatus(ReadStatus.READ);
            progress.setReadStatusModifiedTime(Instant.now().minusSeconds(30));
            
            // Device also sends progress
            progress.setKoboProgressPercent(100f);
            progress.setKoboProgressReceivedTime(Instant.now().minusSeconds(20));

            // Both should need sync
            assertTrue(hasUnsyncedStatus(progress));
            assertTrue(hasUnsyncedProgress(progress));
        }
    }

    private UserBookProgressEntity createProgressForBook(Long bookId) {
        BookEntity book = new BookEntity();
        book.setId(bookId);
        
        UserBookProgressEntity progress = new UserBookProgressEntity();
        progress.setBook(book);
        return progress;
    }

    private boolean hasUnsyncedProgress(UserBookProgressEntity progress) {
        Instant receivedTime = progress.getKoboProgressReceivedTime();
        if (receivedTime == null) {
            return false;
        }
        Instant sentTime = progress.getKoboProgressSentTime();
        return sentTime == null || receivedTime.isAfter(sentTime);
    }

    private boolean hasUnsyncedStatus(UserBookProgressEntity progress) {
        Instant modifiedTime = progress.getReadStatusModifiedTime();
        if (modifiedTime == null) {
            return false;
        }
        Instant sentTime = progress.getKoboStatusSentTime();
        return sentTime == null || modifiedTime.isAfter(sentTime);
    }
}
