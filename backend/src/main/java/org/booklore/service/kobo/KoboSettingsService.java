package org.booklore.service.kobo;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.HardcoverSyncSettings;
import org.booklore.model.dto.KoboSyncSettings;
import org.booklore.model.dto.Shelf;
import org.booklore.model.dto.request.ShelfCreateRequest;
import org.booklore.model.entity.KoboUserSettingsEntity;
import org.booklore.model.entity.ShelfEntity;
import org.booklore.model.enums.IconType;
import org.booklore.model.enums.ShelfType;
import org.booklore.repository.KoboUserSettingsRepository;
import org.booklore.service.ShelfService;
import org.booklore.service.hardcover.HardcoverSyncSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KoboSettingsService {

    private final KoboUserSettingsRepository repository;
    private final AuthenticationService authenticationService;
    private final ShelfService shelfService;
    private final HardcoverSyncSettingsService hardcoverSyncSettingsService;

    @Transactional(readOnly = true)
    public KoboSyncSettings getCurrentUserSettings() {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        KoboUserSettingsEntity entity = repository.findByUserId(user.getId())
                .orElseGet(() -> initDefaultSettings(user.getId()));
        return mapToDto(entity);
    }

    @Transactional
    public KoboSyncSettings createOrUpdateToken() {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        String newToken = generateToken();

        KoboUserSettingsEntity entity = repository.findByUserId(user.getId())
                .map(existing -> {
                    existing.setToken(newToken);
                    return existing;
                })
                .orElseGet(() -> KoboUserSettingsEntity.builder()
                        .userId(user.getId())
                        .token(newToken)
                        .syncEnabled(false)
                        .build());

        ensureKoboShelfExists(user.getId());
        repository.save(entity);

        return mapToDto(entity);
    }

    @Transactional
    public KoboSyncSettings updateSettings(KoboSyncSettings settings) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        KoboUserSettingsEntity entity = repository.findByUserId(user.getId()).orElseGet(() -> initDefaultSettings(user.getId()));

        if (settings.isSyncEnabled() != entity.isSyncEnabled()) {
            Shelf userKoboShelf = shelfService.getUserKoboShelf();
            if (!settings.isSyncEnabled()) {
                if (userKoboShelf != null) {
                    shelfService.deleteShelf(userKoboShelf.getId());
                }
            } else {
                ensureKoboShelfExists(user.getId());
            }
            entity.setSyncEnabled(settings.isSyncEnabled());
        }

        if (settings.getProgressMarkAsReadingThreshold() != null) {
            entity.setProgressMarkAsReadingThreshold(settings.getProgressMarkAsReadingThreshold());
        }
        if (settings.getProgressMarkAsFinishedThreshold() != null) {
            entity.setProgressMarkAsFinishedThreshold(settings.getProgressMarkAsFinishedThreshold());
        }

        entity.setAutoAddToShelf(settings.isAutoAddToShelf());
        entity.setTwoWayProgressSync(settings.isTwoWayProgressSync());

        repository.save(entity);
        return mapToDto(entity, hardcoverSyncSettingsService.getSettingsForUserId(user.getId()));
    }

    private KoboUserSettingsEntity initDefaultSettings(Long userId) {
        ensureKoboShelfExists(userId);
        KoboUserSettingsEntity entity = KoboUserSettingsEntity.builder()
                .userId(userId)
                .syncEnabled(false)
                .token(generateToken())
                .build();
        return repository.save(entity);
    }

    private void ensureKoboShelfExists(Long userId) {
        Optional<ShelfEntity> shelf = shelfService.getShelf(userId, ShelfType.KOBO.getName());
        if (shelf.isEmpty()) {
            shelfService.createShelf(
                    ShelfCreateRequest.builder()
                            .name(ShelfType.KOBO.getName())
                            .icon(ShelfType.KOBO.getIcon())
                            .iconType(IconType.PRIME_NG)
                            .build()
            );
        }
    }

    private String generateToken() {
        return UUID.randomUUID().toString();
    }

    private KoboSyncSettings mapToDto(KoboUserSettingsEntity entity) {
        HardcoverSyncSettings hardcoverSettings = hardcoverSyncSettingsService.getSettingsForUserId(entity.getUserId());
        return mapToDto(entity, hardcoverSettings);
    }

    private KoboSyncSettings mapToDto(KoboUserSettingsEntity entity, HardcoverSyncSettings hardcoverSettings) {
        KoboSyncSettings dto = new KoboSyncSettings();
        dto.setId(entity.getId());
        dto.setUserId(entity.getUserId().toString());
        dto.setToken(entity.getToken());
        dto.setSyncEnabled(entity.isSyncEnabled());
        dto.setProgressMarkAsReadingThreshold(entity.getProgressMarkAsReadingThreshold());
        dto.setProgressMarkAsFinishedThreshold(entity.getProgressMarkAsFinishedThreshold());
        dto.setAutoAddToShelf(entity.isAutoAddToShelf());
        dto.setTwoWayProgressSync(entity.isTwoWayProgressSync());
        if (hardcoverSettings != null) {
            dto.setHardcoverApiKey(hardcoverSettings.getHardcoverApiKey());
            dto.setHardcoverSyncEnabled(hardcoverSettings.isHardcoverSyncEnabled());
        } else {
            dto.setHardcoverSyncEnabled(false);
        }
        return dto;
    }

    /**
     * Get Kobo settings for a specific user by ID.
     */
    @Transactional(readOnly = true)
    public KoboSyncSettings getSettingsByUserId(Long userId) {
        return repository.findByUserId(userId)
                .map(this::mapToDto)
                .orElse(null);
    }

}
