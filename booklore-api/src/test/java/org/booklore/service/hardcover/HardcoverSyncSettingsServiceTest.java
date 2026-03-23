package org.booklore.service.hardcover;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.HardcoverSyncSettings;
import org.booklore.model.dto.settings.UserSettingKey;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.KoboUserSettingsEntity;
import org.booklore.model.entity.UserSettingEntity;
import org.booklore.repository.KoboUserSettingsRepository;
import org.booklore.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HardcoverSyncSettingsServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthenticationService authenticationService;

    @Mock
    private KoboUserSettingsRepository koboUserSettingsRepository;

    private HardcoverSyncSettingsService service;

    @BeforeEach
    void setUp() {
        service = new HardcoverSyncSettingsService(userRepository, authenticationService, koboUserSettingsRepository);
    }

    @Test
    @DisplayName("Should fallback to legacy Kobo settings and persist user settings")
    void getSettingsForUserId_whenLegacyPresent_shouldPersistUserSettings() {
        BookLoreUserEntity user = new BookLoreUserEntity();
        user.setId(1L);
        user.setSettings(new HashSet<>());

        KoboUserSettingsEntity legacy = KoboUserSettingsEntity.builder()
                .userId(1L)
                .hardcoverApiKey("legacy-key")
                .hardcoverSyncEnabled(true)
                .build();

        when(userRepository.findByIdWithSettings(1L)).thenReturn(Optional.of(user));
        when(koboUserSettingsRepository.findByUserId(1L)).thenReturn(Optional.of(legacy));

        HardcoverSyncSettings settings = service.getSettingsForUserId(1L);

        assertEquals("legacy-key", settings.getHardcoverApiKey());
        assertTrue(settings.isHardcoverSyncEnabled());
        assertTrue(user.getSettings().stream()
                .anyMatch(setting -> UserSettingKey.HARDCOVER_API_KEY.getDbKey().equals(setting.getSettingKey())));
        assertTrue(user.getSettings().stream()
                .anyMatch(setting -> UserSettingKey.HARDCOVER_SYNC_ENABLED.getDbKey().equals(setting.getSettingKey())));
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("Should trim API key when updating settings")
    void updateSettingsForUserId_shouldTrimApiKey() {
        BookLoreUserEntity user = new BookLoreUserEntity();
        user.setId(1L);
        user.setSettings(new HashSet<>());
        user.getSettings().add(UserSettingEntity.builder()
                .user(user)
                .settingKey(UserSettingKey.HARDCOVER_API_KEY.getDbKey())
                .settingValue("old")
                .build());

        when(userRepository.findByIdWithSettings(1L)).thenReturn(Optional.of(user));

        HardcoverSyncSettings update = new HardcoverSyncSettings();
        update.setHardcoverApiKey("  new-key  ");
        update.setHardcoverSyncEnabled(false);

        HardcoverSyncSettings result = service.updateSettingsForUserId(1L, update);

        assertEquals("new-key", result.getHardcoverApiKey());
        assertTrue(user.getSettings().stream()
                .anyMatch(setting -> UserSettingKey.HARDCOVER_API_KEY.getDbKey().equals(setting.getSettingKey())
                        && "new-key".equals(setting.getSettingValue())));
        verify(userRepository).save(user);
    }
}
