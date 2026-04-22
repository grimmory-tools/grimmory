package org.booklore.service.hardcover;

import lombok.RequiredArgsConstructor;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.HardcoverSyncSettings;
import org.booklore.model.dto.settings.UserSettingKey;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.KoboUserSettingsEntity;
import org.booklore.model.entity.UserSettingEntity;
import org.booklore.repository.KoboUserSettingsRepository;
import org.booklore.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class HardcoverSyncSettingsService {

    private final UserRepository userRepository;
    private final AuthenticationService authenticationService;
    private final KoboUserSettingsRepository koboUserSettingsRepository;

    @Transactional
    public HardcoverSyncSettings getCurrentUserSettings() {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        return getSettingsForUserId(user.getId());
    }

    @Transactional
    public HardcoverSyncSettings getSettingsForUserId(Long userId) {
        BookLoreUserEntity user = userRepository.findByIdWithSettings(userId)
                .orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(userId));
        return readSettings(user, userId);
    }

    @Transactional
    public HardcoverSyncSettings updateCurrentUserSettings(HardcoverSyncSettings settings) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        return updateSettingsForUserId(user.getId(), settings);
    }

    @Transactional
    public HardcoverSyncSettings updateSettingsForUserId(Long userId, HardcoverSyncSettings settings) {
        if (settings == null) {
            throw ApiError.INVALID_INPUT.createException("Hardcover settings cannot be null.");
        }

        BookLoreUserEntity user = userRepository.findByIdWithSettings(userId)
                .orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(userId));

        String apiKey = settings.getHardcoverApiKey();
        if (apiKey == null) {
            apiKey = "";
        } else {
            apiKey = apiKey.trim();
        }
        upsertSetting(user, UserSettingKey.HARDCOVER_API_KEY, apiKey);
        upsertSetting(user, UserSettingKey.HARDCOVER_SYNC_ENABLED, Boolean.toString(settings.isHardcoverSyncEnabled()));

        userRepository.save(user);

        HardcoverSyncSettings updated = new HardcoverSyncSettings();
        updated.setHardcoverApiKey(apiKey);
        updated.setHardcoverSyncEnabled(settings.isHardcoverSyncEnabled());
        return updated;
    }

    private HardcoverSyncSettings readSettings(BookLoreUserEntity user, Long userId) {
        UserSettingEntity apiKeySetting = findSetting(user, UserSettingKey.HARDCOVER_API_KEY).orElse(null);
        UserSettingEntity syncEnabledSetting = findSetting(user, UserSettingKey.HARDCOVER_SYNC_ENABLED).orElse(null);

        String apiKey = apiKeySetting != null ? apiKeySetting.getSettingValue() : null;
        boolean syncEnabled = syncEnabledSetting != null && Boolean.parseBoolean(syncEnabledSetting.getSettingValue());

        boolean shouldSave = false;
        if (apiKeySetting == null || syncEnabledSetting == null) {
            KoboUserSettingsEntity legacySettings = koboUserSettingsRepository.findByUserId(userId).orElse(null);
            if (legacySettings != null) {
                if (apiKeySetting == null && legacySettings.getHardcoverApiKey() != null && !legacySettings.getHardcoverApiKey().isBlank()) {
                    apiKey = legacySettings.getHardcoverApiKey();
                    upsertSetting(user, UserSettingKey.HARDCOVER_API_KEY, apiKey);
                    shouldSave = true;
                }
                if (syncEnabledSetting == null && legacySettings.isHardcoverSyncEnabled()) {
                    syncEnabled = legacySettings.isHardcoverSyncEnabled();
                    upsertSetting(user, UserSettingKey.HARDCOVER_SYNC_ENABLED, Boolean.toString(syncEnabled));
                    shouldSave = true;
                }
            }
        }
        if (shouldSave) {
            userRepository.save(user);
        }

        HardcoverSyncSettings settings = new HardcoverSyncSettings();
        settings.setHardcoverApiKey(apiKey);
        settings.setHardcoverSyncEnabled(syncEnabled);
        return settings;
    }

    private Optional<UserSettingEntity> findSetting(BookLoreUserEntity user, UserSettingKey key) {
        return user.getSettings().stream()
                .filter(setting -> key.getDbKey().equals(setting.getSettingKey()))
                .findFirst();
    }

    private void upsertSetting(BookLoreUserEntity user, UserSettingKey key, String value) {
        UserSettingEntity setting = findSetting(user, key)
                .orElseGet(() -> {
                    UserSettingEntity newSetting = new UserSettingEntity();
                    newSetting.setUser(user);
                    newSetting.setSettingKey(key.getDbKey());
                    user.getSettings().add(newSetting);
                    return newSetting;
                });
        setting.setSettingValue(value);
    }
}
