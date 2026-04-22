package org.booklore.service;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.mapper.KoboReadingStateMapper;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.KoboSyncSettings;
import org.booklore.model.dto.kobo.KoboReadingState;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.KoboReadingStateEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.enums.ReadStatus;
import org.booklore.repository.BookRepository;
import org.booklore.repository.KoboReadingStateRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.booklore.repository.UserRepository;
import org.booklore.service.hardcover.HardcoverSyncService;
import org.booklore.service.kobo.KoboReadingStateBuilder;
import org.booklore.service.kobo.KoboReadingStateService;
import org.booklore.service.kobo.KoboSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Kobo Status Sync Protection Tests")
class KoboStatusSyncProtectionTest {

    @Mock
    private KoboReadingStateRepository repository;
    @Mock
    private KoboReadingStateMapper mapper;
    @Mock
    private UserBookProgressRepository progressRepository;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuthenticationService authenticationService;
    @Mock
    private KoboSettingsService koboSettingsService;
    @Mock
    private KoboReadingStateBuilder readingStateBuilder;

    @Mock
    private HardcoverSyncService hardcoverSyncService;

    @InjectMocks
    private KoboReadingStateService service;

    private BookLoreUser testUser;
    private BookEntity testBook;
    private BookLoreUserEntity testUserEntity;
    private KoboSyncSettings testSettings;

    @BeforeEach
    void setUp() {
        testUser = BookLoreUser.builder().id(1L).username("testuser").isDefaultPassword(false).build();
        testUserEntity = new BookLoreUserEntity();
        testUserEntity.setId(1L);
        testBook = new BookEntity();
        testBook.setId(100L);

        testSettings = new KoboSyncSettings();
        testSettings.setProgressMarkAsReadingThreshold(1f);
        testSettings.setProgressMarkAsFinishedThreshold(99f);

        when(authenticationService.getAuthenticatedUser()).thenReturn(testUser);
        when(koboSettingsService.getCurrentUserSettings()).thenReturn(testSettings);
        when(bookRepository.findById(100L)).thenReturn(Optional.of(testBook));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUserEntity));
        lenient().when(repository
                .findFirstByEntitlementIdAndUserIdIsNullOrderByPriorityTimestampDescLastModifiedStringDescIdDesc(
                        anyString()))
                .thenReturn(Optional.empty());
        lenient().when(mapper.toJson(any())).thenCallRealMethod();
        lenient().when(mapper.cleanString(any())).thenCallRealMethod();
    }

    private void setupMocksForSave(String entitlementId, KoboReadingState readingState) {
        KoboReadingStateEntity entity = new KoboReadingStateEntity();
        when(mapper.toEntity(any())).thenReturn(entity);
        when(mapper.toDto(any(KoboReadingStateEntity.class))).thenReturn(readingState);
        when(repository.findByEntitlementIdAndUserId(entitlementId, 1L)).thenReturn(Optional.empty());
        when(repository.save(any())).thenReturn(entity);
    }

    @Nested
    @DisplayName("Pending Sync Protection - User changed status in BookLore, waiting to sync to Kobo")
    class PendingSyncProtection {

        @Test
        @DisplayName("User marks book READ in BookLore -> Kobo sends 50% progress before sync completes -> READ status preserved until synced")
        void preserveManualReadStatus_WhenKoboSendsProgress() {
            UserBookProgressEntity existingProgress = new UserBookProgressEntity();
            existingProgress.setUser(testUserEntity);
            existingProgress.setBook(testBook);
            existingProgress.setReadStatus(ReadStatus.READ);
            existingProgress.setReadStatusModifiedTime(Instant.now().minusSeconds(60));
            existingProgress.setKoboStatusSentTime(null); // Not yet sent to Kobo

            KoboReadingState readingState = KoboReadingState.builder()
                    .entitlementId("100")
                    .currentBookmark(KoboReadingState.CurrentBookmark.builder()
                            .progressPercent(50) // Would normally set to READING
                            .build())
                    .build();

            setupMocksForSave("100", readingState);
            when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.of(existingProgress));
            
            ArgumentCaptor<UserBookProgressEntity> captor = ArgumentCaptor.forClass(UserBookProgressEntity.class);
            when(progressRepository.save(captor.capture())).thenReturn(existingProgress);

            service.saveReadingState(List.of(readingState));

            UserBookProgressEntity saved = captor.getValue();
            assertEquals(ReadStatus.READ, saved.getReadStatus(), 
                "READ status must be preserved until it has been synced to Kobo");
        }

        @Test
        @DisplayName("User marks book UNREAD in BookLore -> Kobo sends 100% progress before sync -> UNREAD preserved until synced")
        void preserveManualUnreadStatus_WhenKoboReports100Percent() {
            UserBookProgressEntity existingProgress = new UserBookProgressEntity();
            existingProgress.setUser(testUserEntity);
            existingProgress.setBook(testBook);
            existingProgress.setReadStatus(ReadStatus.UNREAD);
            existingProgress.setReadStatusModifiedTime(Instant.now().minusSeconds(30));
            existingProgress.setKoboStatusSentTime(null);

            KoboReadingState readingState = KoboReadingState.builder()
                    .entitlementId("100")
                    .currentBookmark(KoboReadingState.CurrentBookmark.builder()
                            .progressPercent(100)
                            .build())
                    .build();

            setupMocksForSave("100", readingState);
            when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.of(existingProgress));
            
            ArgumentCaptor<UserBookProgressEntity> captor = ArgumentCaptor.forClass(UserBookProgressEntity.class);
            when(progressRepository.save(captor.capture())).thenReturn(existingProgress);

            service.saveReadingState(List.of(readingState));

            UserBookProgressEntity saved = captor.getValue();
            assertEquals(ReadStatus.UNREAD, saved.getReadStatus(),
                "UNREAD status must be preserved until it has been synced to Kobo");
        }

        @Test
        @DisplayName("User changes status AFTER last sync -> new status preserved until next sync completes")
        void preserveStatus_WhenModifiedAfterSent() {
            Instant sentTime = Instant.now().minusSeconds(120);
            Instant modifiedTime = Instant.now().minusSeconds(60); // Modified AFTER sent

            UserBookProgressEntity existingProgress = new UserBookProgressEntity();
            existingProgress.setUser(testUserEntity);
            existingProgress.setBook(testBook);
            existingProgress.setReadStatus(ReadStatus.ABANDONED);
            existingProgress.setReadStatusModifiedTime(modifiedTime);
            existingProgress.setKoboStatusSentTime(sentTime);

            KoboReadingState readingState = KoboReadingState.builder()
                    .entitlementId("100")
                    .currentBookmark(KoboReadingState.CurrentBookmark.builder()
                            .progressPercent(75)
                            .build())
                    .build();

            setupMocksForSave("100", readingState);
            when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.of(existingProgress));
            
            ArgumentCaptor<UserBookProgressEntity> captor = ArgumentCaptor.forClass(UserBookProgressEntity.class);
            when(progressRepository.save(captor.capture())).thenReturn(existingProgress);

            service.saveReadingState(List.of(readingState));

            assertEquals(ReadStatus.ABANDONED, captor.getValue().getReadStatus(),
                "Status modified after last sync should be preserved");
        }
    }

    @Nested
    @DisplayName("Buffer Window - Kobo sends multiple requests during single sync cycle")
    class BufferWindowProtection {

        @Test
        @DisplayName("Status synced 5s ago -> Kobo sends progress -> preserve status (handles rapid Kobo requests)")
        void preserveStatus_WithinBufferWindow() {
            Instant sentTime = Instant.now().minusSeconds(5);
            Instant modifiedTime = sentTime.minusSeconds(10);

            UserBookProgressEntity existingProgress = new UserBookProgressEntity();
            existingProgress.setUser(testUserEntity);
            existingProgress.setBook(testBook);
            existingProgress.setReadStatus(ReadStatus.READ);
            existingProgress.setReadStatusModifiedTime(modifiedTime);
            existingProgress.setKoboStatusSentTime(sentTime);

            KoboReadingState readingState = KoboReadingState.builder()
                    .entitlementId("100")
                    .currentBookmark(KoboReadingState.CurrentBookmark.builder()
                            .progressPercent(50)
                            .build())
                    .build();

            setupMocksForSave("100", readingState);
            when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.of(existingProgress));
            
            ArgumentCaptor<UserBookProgressEntity> captor = ArgumentCaptor.forClass(UserBookProgressEntity.class);
            when(progressRepository.save(captor.capture())).thenReturn(existingProgress);

            service.saveReadingState(List.of(readingState));

            assertEquals(ReadStatus.READ, captor.getValue().getReadStatus(),
                "Status should be preserved within 10-second buffer after sync");
        }

        @Test
        @DisplayName("Status synced 15s ago -> buffer expired -> Kobo progress can now update status (e.g., re-reading)")
        void allowStatusUpdate_AfterBufferExpires() {
            Instant sentTime = Instant.now().minusSeconds(15);
            Instant modifiedTime = sentTime.minusSeconds(10);

            UserBookProgressEntity existingProgress = new UserBookProgressEntity();
            existingProgress.setUser(testUserEntity);
            existingProgress.setBook(testBook);
            existingProgress.setReadStatus(ReadStatus.READ);
            existingProgress.setReadStatusModifiedTime(modifiedTime);
            existingProgress.setKoboStatusSentTime(sentTime);

            KoboReadingState readingState = KoboReadingState.builder()
                    .entitlementId("100")
                    .currentBookmark(KoboReadingState.CurrentBookmark.builder()
                            .progressPercent(50)
                            .build())
                    .build();

            setupMocksForSave("100", readingState);
            when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.of(existingProgress));
            
            ArgumentCaptor<UserBookProgressEntity> captor = ArgumentCaptor.forClass(UserBookProgressEntity.class);
            when(progressRepository.save(captor.capture())).thenReturn(existingProgress);

            service.saveReadingState(List.of(readingState));

            assertEquals(ReadStatus.READING, captor.getValue().getReadStatus(),
                "Status should update after buffer window expires");
        }

        @Test
        @DisplayName("Status synced 11s ago -> just past buffer -> allow status update")
        void handleBufferBoundary_AtExactly10Seconds() {
            Instant sentTime = Instant.now().minusSeconds(11);
            Instant modifiedTime = sentTime.minusSeconds(5);

            UserBookProgressEntity existingProgress = new UserBookProgressEntity();
            existingProgress.setUser(testUserEntity);
            existingProgress.setBook(testBook);
            existingProgress.setReadStatus(ReadStatus.WONT_READ);
            existingProgress.setReadStatusModifiedTime(modifiedTime);
            existingProgress.setKoboStatusSentTime(sentTime);

            KoboReadingState readingState = KoboReadingState.builder()
                    .entitlementId("100")
                    .currentBookmark(KoboReadingState.CurrentBookmark.builder()
                            .progressPercent(100)
                            .build())
                    .build();

            setupMocksForSave("100", readingState);
            when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.of(existingProgress));
            
            ArgumentCaptor<UserBookProgressEntity> captor = ArgumentCaptor.forClass(UserBookProgressEntity.class);
            when(progressRepository.save(captor.capture())).thenReturn(existingProgress);

            service.saveReadingState(List.of(readingState));

            assertEquals(ReadStatus.READ, captor.getValue().getReadStatus(),
                "Status should update when past 10-second boundary");
        }
    }

    @Nested
    @DisplayName("Normal Updates - Status set purely from Kobo progress (no manual status)")
    class NormalStatusUpdates {

        @Test
        @DisplayName("No manual status set -> Kobo progress determines status")
        void setStatusFromProgress_WhenNoManualStatus() {
            UserBookProgressEntity existingProgress = new UserBookProgressEntity();
            existingProgress.setUser(testUserEntity);
            existingProgress.setBook(testBook);
            existingProgress.setReadStatus(null);
            existingProgress.setReadStatusModifiedTime(null);
            existingProgress.setKoboStatusSentTime(null);

            KoboReadingState readingState = KoboReadingState.builder()
                    .entitlementId("100")
                    .currentBookmark(KoboReadingState.CurrentBookmark.builder()
                            .progressPercent(50)
                            .build())
                    .build();

            setupMocksForSave("100", readingState);
            when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.of(existingProgress));
            
            ArgumentCaptor<UserBookProgressEntity> captor = ArgumentCaptor.forClass(UserBookProgressEntity.class);
            when(progressRepository.save(captor.capture())).thenReturn(existingProgress);

            service.saveReadingState(List.of(readingState));

            assertEquals(ReadStatus.READING, captor.getValue().getReadStatus());
        }

        @Test
        @DisplayName("First sync from Kobo -> creates progress with status from progress%")
        void setStatusForNewProgress() {
            KoboReadingState readingState = KoboReadingState.builder()
                    .entitlementId("100")
                    .currentBookmark(KoboReadingState.CurrentBookmark.builder()
                            .progressPercent(100)
                            .build())
                    .build();

            setupMocksForSave("100", readingState);
            when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.empty());
            
            ArgumentCaptor<UserBookProgressEntity> captor = ArgumentCaptor.forClass(UserBookProgressEntity.class);
            when(progressRepository.save(captor.capture())).thenReturn(new UserBookProgressEntity());

            service.saveReadingState(List.of(readingState));

            UserBookProgressEntity saved = captor.getValue();
            assertEquals(ReadStatus.READ, saved.getReadStatus());
            assertNotNull(saved.getDateFinished());
        }

        @Test
        @DisplayName("Progress below threshold -> UNREAD (e.g., just opened book)")
        void setUnread_ForLowProgress() {
            testSettings.setProgressMarkAsReadingThreshold(5f);

            KoboReadingState readingState = KoboReadingState.builder()
                    .entitlementId("100")
                    .currentBookmark(KoboReadingState.CurrentBookmark.builder()
                            .progressPercent(1)
                            .build())
                    .build();

            setupMocksForSave("100", readingState);
            when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.empty());
            
            ArgumentCaptor<UserBookProgressEntity> captor = ArgumentCaptor.forClass(UserBookProgressEntity.class);
            when(progressRepository.save(captor.capture())).thenReturn(new UserBookProgressEntity());

            service.saveReadingState(List.of(readingState));

            assertEquals(ReadStatus.UNREAD, captor.getValue().getReadStatus());
        }
    }

    @Nested
    @DisplayName("Re-reading - User re-reads after status was synced")
    class ReReadingScenario {

        @Test
        @DisplayName("Book marked READ, synced 5min ago, now progress at 50% -> becomes READING")
        void allowReReading_AfterSyncAndBuffer() {
            Instant sentTime = Instant.now().minusSeconds(300); // 5 minutes ago
            Instant modifiedTime = sentTime.minusSeconds(60);

            UserBookProgressEntity existingProgress = new UserBookProgressEntity();
            existingProgress.setUser(testUserEntity);
            existingProgress.setBook(testBook);
            existingProgress.setReadStatus(ReadStatus.READ);
            existingProgress.setReadStatusModifiedTime(modifiedTime);
            existingProgress.setKoboStatusSentTime(sentTime);

            KoboReadingState readingState = KoboReadingState.builder()
                    .entitlementId("100")
                    .currentBookmark(KoboReadingState.CurrentBookmark.builder()
                            .progressPercent(10) // Starting to re-read
                            .build())
                    .build();

            setupMocksForSave("100", readingState);
            when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.of(existingProgress));
            
            ArgumentCaptor<UserBookProgressEntity> captor = ArgumentCaptor.forClass(UserBookProgressEntity.class);
            when(progressRepository.save(captor.capture())).thenReturn(existingProgress);

            service.saveReadingState(List.of(readingState));

            assertEquals(ReadStatus.READING, captor.getValue().getReadStatus(),
                "Re-reading should update status after sync completed and buffer expired");
        }
    }

    @Nested
    @DisplayName("Rapid Requests - Kobo sends multiple syncs within seconds")
    class MultipleRapidRequests {

        @Test
        @DisplayName("Two requests 2s after sync -> both preserve READ status")
        void handleMultipleRapidRequests() {
            Instant sentTime = Instant.now().minusSeconds(2);
            Instant modifiedTime = sentTime.minusSeconds(5);

            UserBookProgressEntity existingProgress = new UserBookProgressEntity();
            existingProgress.setUser(testUserEntity);
            existingProgress.setBook(testBook);
            existingProgress.setReadStatus(ReadStatus.READ);
            existingProgress.setReadStatusModifiedTime(modifiedTime);
            existingProgress.setKoboStatusSentTime(sentTime);
            existingProgress.setKoboProgressPercent(50f);

            // First request
            KoboReadingState firstRequest = KoboReadingState.builder()
                    .entitlementId("100")
                    .currentBookmark(KoboReadingState.CurrentBookmark.builder()
                            .progressPercent(52)
                            .build())
                    .build();

            setupMocksForSave("100", firstRequest);
            when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.of(existingProgress));
            
            ArgumentCaptor<UserBookProgressEntity> captor = ArgumentCaptor.forClass(UserBookProgressEntity.class);
            when(progressRepository.save(captor.capture())).thenReturn(existingProgress);

            service.saveReadingState(List.of(firstRequest));
            assertEquals(ReadStatus.READ, captor.getValue().getReadStatus(), 
                "First rapid request should preserve status");

            // Second request immediately after
            KoboReadingState secondRequest = KoboReadingState.builder()
                    .entitlementId("100")
                    .currentBookmark(KoboReadingState.CurrentBookmark.builder()
                            .progressPercent(55)
                            .build())
                    .build();

            setupMocksForSave("100", secondRequest);
            service.saveReadingState(List.of(secondRequest));
            assertEquals(ReadStatus.READ, captor.getValue().getReadStatus(),
                "Second rapid request should also preserve status");
        }
    }
}
