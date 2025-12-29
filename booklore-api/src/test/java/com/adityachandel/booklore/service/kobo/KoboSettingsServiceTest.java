package com.adityachandel.booklore.service.kobo;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.KoboSyncSettings;
import com.adityachandel.booklore.model.dto.Shelf;
import com.adityachandel.booklore.model.dto.request.ShelfCreateRequest;
import com.adityachandel.booklore.model.entity.KoboUserSettingsEntity;
import com.adityachandel.booklore.model.entity.ShelfEntity;
import com.adityachandel.booklore.model.enums.ShelfType;
import com.adityachandel.booklore.repository.KoboUserSettingsRepository;
import com.adityachandel.booklore.service.ShelfService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KoboSettingsServiceTest {

    @Mock
    private KoboUserSettingsRepository repository;
    @Mock
    private AuthenticationService authenticationService;
    @Mock
    private ShelfService shelfService;

    @InjectMocks
    private KoboSettingsService service;

    private BookLoreUser user;
    private KoboUserSettingsEntity settingsEntity;

    @BeforeEach
    void setUp() {
        user = BookLoreUser.builder().id(1L).build();
        settingsEntity = KoboUserSettingsEntity.builder()
                .id(10L)
                .userId(1L)
                .token("token")
                .syncEnabled(true)
                .autoAddToShelf(true)
                .progressMarkAsReadingThreshold(0.5f)
                .progressMarkAsFinishedThreshold(0.9f)
                .build();
    }

    @Test
    void getCurrentUserSettings_existingSettings() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(repository.findByUserId(1L)).thenReturn(Optional.of(settingsEntity));

        KoboSyncSettings dto = service.getCurrentUserSettings();

        assertEquals(settingsEntity.getId(), dto.getId());
        assertEquals(settingsEntity.getUserId().toString(), dto.getUserId());
        assertEquals(settingsEntity.getToken(), dto.getToken());
        assertTrue(dto.isSyncEnabled());
        assertTrue(dto.isAutoAddToShelf());
        assertEquals(settingsEntity.getProgressMarkAsReadingThreshold(), dto.getProgressMarkAsReadingThreshold());
        assertEquals(settingsEntity.getProgressMarkAsFinishedThreshold(), dto.getProgressMarkAsFinishedThreshold());
    }

    @Test
    void getCurrentUserSettings_noSettings_createsDefault() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(repository.findByUserId(1L)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(shelfService.getShelf(eq(1L), eq(ShelfType.KOBO.getName()))).thenReturn(Optional.empty());
        doReturn(Shelf.builder().id(100L).build()).when(shelfService).createShelf(any(ShelfCreateRequest.class));

        KoboSyncSettings dto = service.getCurrentUserSettings();

        assertEquals(user.getId().toString(), dto.getUserId());
        assertNotNull(dto.getToken());
        assertFalse(dto.isSyncEnabled());
    }

    @Test
    void createOrUpdateToken_existingSettings() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(repository.findByUserId(1L)).thenReturn(Optional.of(settingsEntity));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(shelfService.getShelf(eq(1L), eq(ShelfType.KOBO.getName()))).thenReturn(Optional.of(ShelfEntity.builder().id(100L).build()));

        KoboSyncSettings dto = service.createOrUpdateToken();

        assertEquals(settingsEntity.getUserId().toString(), dto.getUserId());
        assertNotNull(dto.getToken());
        assertNotEquals("token", dto.getToken());
    }

    @Test
    void createOrUpdateToken_noSettings_createsNew() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(repository.findByUserId(1L)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(shelfService.getShelf(eq(1L), eq(ShelfType.KOBO.getName()))).thenReturn(Optional.empty());
        doReturn(Shelf.builder().id(100L).build()).when(shelfService).createShelf(any(ShelfCreateRequest.class));

        KoboSyncSettings dto = service.createOrUpdateToken();

        assertEquals(user.getId().toString(), dto.getUserId());
        assertNotNull(dto.getToken());
        assertFalse(dto.isSyncEnabled());
    }

    @Test
    void updateSettings_disableSync_deletesShelf() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(repository.findByUserId(1L)).thenReturn(Optional.of(settingsEntity));
        Shelf shelf = Shelf.builder().id(100L).build();
        when(shelfService.getUserKoboShelf()).thenReturn(shelf);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        KoboSyncSettings update = new KoboSyncSettings();
        update.setSyncEnabled(false);
        update.setAutoAddToShelf(false);

        KoboSyncSettings dto = service.updateSettings(update);

        verify(shelfService).deleteShelf(100L);
        assertFalse(dto.isSyncEnabled());
        assertFalse(dto.isAutoAddToShelf());
    }

    @Test
    void updateSettings_enableSync_createsShelf() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        settingsEntity.setSyncEnabled(false);
        when(repository.findByUserId(1L)).thenReturn(Optional.of(settingsEntity));
        when(shelfService.getUserKoboShelf()).thenReturn(null);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(shelfService.getShelf(eq(1L), eq(ShelfType.KOBO.getName()))).thenReturn(Optional.empty());
        doReturn(Shelf.builder().id(100L).build()).when(shelfService).createShelf(any(ShelfCreateRequest.class));

        KoboSyncSettings update = new KoboSyncSettings();
        update.setSyncEnabled(true);
        update.setAutoAddToShelf(true);

        KoboSyncSettings dto = service.updateSettings(update);

        verify(shelfService).createShelf(any(ShelfCreateRequest.class));
        assertTrue(dto.isSyncEnabled());
        assertTrue(dto.isAutoAddToShelf());
    }

    @Test
    void updateSettings_updatesThresholds() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(repository.findByUserId(1L)).thenReturn(Optional.of(settingsEntity));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        KoboSyncSettings update = new KoboSyncSettings();
        update.setSyncEnabled(true);
        update.setAutoAddToShelf(true);
        update.setProgressMarkAsReadingThreshold(0.7f);
        update.setProgressMarkAsFinishedThreshold(0.95f);

        KoboSyncSettings dto = service.updateSettings(update);

        assertEquals((Float)0.7f, dto.getProgressMarkAsReadingThreshold());
        assertEquals((Float)0.95f, dto.getProgressMarkAsFinishedThreshold());
    }

    @Test
    void updateSettings_nullThresholds_shouldNotChangeExisting() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(repository.findByUserId(1L)).thenReturn(Optional.of(settingsEntity));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Float originalReading = settingsEntity.getProgressMarkAsReadingThreshold();
        Float originalFinished = settingsEntity.getProgressMarkAsFinishedThreshold();

        KoboSyncSettings update = new KoboSyncSettings();
        update.setSyncEnabled(true);
        update.setAutoAddToShelf(true);
        update.setProgressMarkAsReadingThreshold(null);
        update.setProgressMarkAsFinishedThreshold(null);

        KoboSyncSettings dto = service.updateSettings(update);

        assertEquals(originalReading, dto.getProgressMarkAsReadingThreshold());
        assertEquals(originalFinished, dto.getProgressMarkAsFinishedThreshold());
    }

    @Test
    void getCurrentUserSettings_settingsWithNullToken_shouldReturnDtoWithNullToken() {
        settingsEntity.setToken(null);
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(repository.findByUserId(1L)).thenReturn(Optional.of(settingsEntity));

        KoboSyncSettings dto = service.getCurrentUserSettings();

        assertNull(dto.getToken());
    }

    @Test
    void getCurrentUserSettings_noAuthenticatedUser_shouldThrowException() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(null);
        assertThrows(NullPointerException.class, () -> service.getCurrentUserSettings());
    }

    @Test
    void updateSettings_getUserKoboShelfReturnsNull_shouldNotThrow() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(repository.findByUserId(1L)).thenReturn(Optional.of(settingsEntity));
        when(shelfService.getUserKoboShelf()).thenReturn(null);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        KoboSyncSettings update = new KoboSyncSettings();
        update.setSyncEnabled(false);
        update.setAutoAddToShelf(false);

        assertDoesNotThrow(() -> service.updateSettings(update));
    }

    @Test
    void ensureKoboShelfExists_doesNotCreateIfExists() throws Exception {
        when(shelfService.getShelf(eq(1L), eq(ShelfType.KOBO.getName()))).thenReturn(Optional.of(ShelfEntity.builder().id(100L).build()));

        var method = service.getClass().getDeclaredMethod("ensureKoboShelfExists", Long.class);
        method.setAccessible(true);
        assertDoesNotThrow(() -> method.invoke(service, 1L));
        verify(shelfService, never()).createShelf(any());
    }

    @Test
    void ensureKoboShelfExists_createsIfMissing() throws Exception {
        when(shelfService.getShelf(eq(1L), eq(ShelfType.KOBO.getName()))).thenReturn(Optional.empty());
        doReturn(Shelf.builder().id(100L).build()).when(shelfService).createShelf(any(ShelfCreateRequest.class));

        var method = service.getClass().getDeclaredMethod("ensureKoboShelfExists", Long.class);
        method.setAccessible(true);
        assertDoesNotThrow(() -> method.invoke(service, 1L));
        verify(shelfService).createShelf(any(ShelfCreateRequest.class));
    }

    @Test
    void ensureKoboShelfExists_idempotentIfCalledTwice() throws Exception {
        when(shelfService.getShelf(eq(1L), eq(ShelfType.KOBO.getName())))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(ShelfEntity.builder().id(100L).build()));
        doReturn(Shelf.builder().id(100L).build()).when(shelfService).createShelf(any(ShelfCreateRequest.class));

        var method = service.getClass().getDeclaredMethod("ensureKoboShelfExists", Long.class);
        method.setAccessible(true);

        method.invoke(service, 1L);
        method.invoke(service, 1L);

        verify(shelfService, times(1)).createShelf(any(ShelfCreateRequest.class));
    }

    @Test
    void createOrUpdateToken_multipleCalls_generateDifferentTokens() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(repository.findByUserId(1L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(settingsEntity));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(shelfService.getShelf(eq(1L), eq(ShelfType.KOBO.getName()))).thenReturn(Optional.empty());
        doReturn(Shelf.builder().id(100L).build()).when(shelfService).createShelf(any(ShelfCreateRequest.class));

        KoboSyncSettings dto1 = service.createOrUpdateToken();
        // Simulate a new call with an existing entity
        KoboSyncSettings dto2 = service.createOrUpdateToken();

        assertNotEquals(dto1.getToken(), dto2.getToken());
    }
}
