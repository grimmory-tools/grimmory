package org.booklore.service.koreader;

import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.settings.KoreaderSyncSettings;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.ShelfEntity;
import org.booklore.model.enums.IconType;
import org.booklore.service.icon.BundledIconService;
import org.booklore.repository.ShelfRepository;
import org.booklore.repository.UserRepository;
import org.booklore.service.appsettings.AppSettingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KoreaderShelfServiceTest {

    @Mock private ShelfRepository shelfRepository;
    @Mock private UserRepository userRepository;
    @Mock private AppSettingService appSettingService;

    @InjectMocks private KoreaderShelfService service;

    private static KoreaderSyncSettings settings(boolean enabled, String shelfName) {
        return KoreaderSyncSettings.builder().externalServerEnabled(enabled).externalServerUrl("https://sync.example").shelfName(shelfName).build();
    }

    private static BookLoreUserEntity user(long id) {
        BookLoreUserEntity user = new BookLoreUserEntity();
        user.setId(id);
        return user;
    }

    private static ShelfEntity shelf(BookLoreUserEntity user, String name) {
        return ShelfEntity.builder().id(user.getId() * 100).user(user).name(name)
                .icon(BundledIconService.KOREADER_ICON).iconType(IconType.CUSTOM_SVG).build();
    }

    private void currentSettings(KoreaderSyncSettings s) {
        when(appSettingService.getAppSettings()).thenReturn(AppSettings.builder().koreaderSyncSettings(s).build());
    }

    @Test
    void defaultShelfNameIsKoreaderAndBlankFallsBack() {
        assertEquals("KOReader", new KoreaderSyncSettings().effectiveShelfName());
        assertEquals("KOReader", settings(true, "  ").effectiveShelfName());
        assertEquals("Devices", settings(true, " Devices ").effectiveShelfName());
    }

    @Test
    void newUserGetsTheShelfWhileBookBridgeSyncIsOn() {
        currentSettings(settings(true, null));
        when(shelfRepository.existsByUserIdAndName(7L, "KOReader")).thenReturn(false);

        service.ensureShelfForUser(user(7L));

        ArgumentCaptor<ShelfEntity> saved = ArgumentCaptor.forClass(ShelfEntity.class);
        verify(shelfRepository).save(saved.capture());
        assertEquals("KOReader", saved.getValue().getName());
        assertEquals("koreader-icon", saved.getValue().getIcon());
        assertEquals(IconType.CUSTOM_SVG, saved.getValue().getIconType());
        assertFalse(saved.getValue().isPublic());
        assertEquals(7L, saved.getValue().getUser().getId());
    }

    @Test
    void newUserGetsNothingWhileBookBridgeSyncIsOff() {
        currentSettings(settings(false, null));

        service.ensureShelfForUser(user(7L));

        verify(shelfRepository, never()).save(any());
    }

    @Test
    void existingShelfIsLeftAlone() {
        currentSettings(settings(true, "KOReader"));
        when(shelfRepository.existsByUserIdAndName(7L, "KOReader")).thenReturn(true);

        service.ensureShelfForUser(user(7L));

        verify(shelfRepository, never()).save(any());
    }

    @Test
    void turningTheFeatureOnCreatesTheShelfForEveryUserWithoutOne() {
        when(userRepository.findAll()).thenReturn(List.of(user(1L), user(2L)));
        when(shelfRepository.existsByUserIdAndName(1L, "KOReader")).thenReturn(true);
        when(shelfRepository.existsByUserIdAndName(2L, "KOReader")).thenReturn(false);

        service.onSettingsChanged(new KoreaderSyncSettingsChangedEvent(settings(false, null), settings(true, null)));

        ArgumentCaptor<ShelfEntity> saved = ArgumentCaptor.forClass(ShelfEntity.class);
        verify(shelfRepository, times(1)).save(saved.capture());
        assertEquals(2L, saved.getValue().getUser().getId());
    }

    @Test
    void turningTheFeatureOffKeepsEveryShelf() {
        service.onSettingsChanged(new KoreaderSyncSettingsChangedEvent(settings(true, null), settings(false, null)));

        verifyNoInteractions(shelfRepository);
    }

    @Test
    void aShelfTheReaderMadeWithTheSameNameIsNeverRenamed() {
        ShelfEntity personal = ShelfEntity.builder().id(300L).user(user(3L)).name("KOReader").icon("book").iconType(IconType.LUCIDE).build();
        when(shelfRepository.findByName("KOReader")).thenReturn(List.of(personal));

        service.onSettingsChanged(new KoreaderSyncSettingsChangedEvent(settings(true, "KOReader"), settings(true, "Devices")));

        verify(shelfRepository, never()).save(personal);
        assertEquals("KOReader", personal.getName());
    }

    @Test
    void renamingMovesExistingShelvesAndSkipsUsersWhoAlreadyHaveTheNewName() {
        ShelfEntity movable = shelf(user(1L), "KOReader");
        ShelfEntity blocked = shelf(user(2L), "KOReader");
        when(shelfRepository.findByName("KOReader")).thenReturn(List.of(movable, blocked));
        when(shelfRepository.existsByUserIdAndName(1L, "Devices")).thenReturn(false, true);
        when(shelfRepository.existsByUserIdAndName(2L, "Devices")).thenReturn(true);
        when(userRepository.findAll()).thenReturn(List.of(user(1L), user(2L)));

        service.onSettingsChanged(new KoreaderSyncSettingsChangedEvent(settings(true, "KOReader"), settings(true, "Devices")));

        assertEquals("Devices", movable.getName());
        assertEquals("KOReader", blocked.getName());
        verify(shelfRepository, times(1)).save(movable);
        verify(shelfRepository, never()).deleteAll(anyList());
    }

    @Test
    void unchangedSettingsDoNothing() {
        service.onSettingsChanged(new KoreaderSyncSettingsChangedEvent(settings(true, "KOReader"), settings(true, "KOReader")));
        service.onSettingsChanged(new KoreaderSyncSettingsChangedEvent(settings(false, "A"), settings(false, "B")));

        verifyNoInteractions(shelfRepository, userRepository);
    }

    @Test
    void startupReconcileCreatesMissingShelvesOnlyWhenOn() {
        currentSettings(settings(true, "KOReader"));
        when(userRepository.findAll()).thenReturn(List.of(user(1L)));
        when(shelfRepository.existsByUserIdAndName(1L, "KOReader")).thenReturn(false);

        service.reconcileOnStartup();

        verify(shelfRepository).save(any(ShelfEntity.class));
    }

    @Test
    void startupReconcileIsANoOpWhenOff() {
        currentSettings(settings(false, "KOReader"));

        service.reconcileOnStartup();

        verifyNoInteractions(shelfRepository, userRepository);
    }
}
