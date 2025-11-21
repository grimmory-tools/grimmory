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
    public void setSyncEnabled(boolean enabled) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        KoboUserSettingsEntity entity = repository.findByUserId(user.getId()).orElseThrow(() -> new IllegalStateException("Kobo settings not found for user"));
        Shelf userKoboShelf = shelfService.getUserKoboShelf();
        if (!enabled) {
            if (userKoboShelf != null) {
                shelfService.deleteShelf(userKoboShelf.getId());
            }
        } else {
            ensureKoboShelfExists(user.getId());
        }
        entity.setSyncEnabled(enabled);
        repository.save(entity);
    }

    @Transactional
    public KoboSyncSettings updateProgressThresholds(Float readingThreshold, Float finishedThreshold) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        KoboUserSettingsEntity entity = repository.findByUserId(user.getId())
                .orElseGet(() -> initDefaultSettings(user.getId()));
        
        if (readingThreshold != null) {
            entity.setProgressMarkAsReadingThreshold(readingThreshold);
        }
        if (finishedThreshold != null) {
            entity.setProgressMarkAsFinishedThreshold(finishedThreshold);
        }
        
        repository.save(entity);
        return mapToDto(entity);
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
                            .build()
            );
        }
    }

    private String generateToken() {
        return UUID.randomUUID().toString();
    }

    private KoboSyncSettings mapToDto(KoboUserSettingsEntity entity) {
        KoboSyncSettings dto = new KoboSyncSettings();
        dto.setId(entity.getId());
        dto.setUserId(entity.getUserId().toString());
        dto.setToken(entity.getToken());
        dto.setSyncEnabled(entity.isSyncEnabled());
        dto.setProgressMarkAsReadingThreshold(entity.getProgressMarkAsReadingThreshold());
        dto.setProgressMarkAsFinishedThreshold(entity.getProgressMarkAsFinishedThreshold());
        return dto;
    }
}
