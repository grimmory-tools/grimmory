package org.booklore.service.koreader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.settings.KoreaderSyncSettings;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.ShelfEntity;
import org.booklore.model.enums.IconType;
import org.booklore.repository.ShelfRepository;
import org.booklore.repository.UserRepository;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.icon.BundledIconService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
@Slf4j
public class KoreaderShelfService {

    private final ShelfRepository shelfRepository;
    private final UserRepository userRepository;
    private final AppSettingService appSettingService;

    @Transactional
    @EventListener(ApplicationReadyEvent.class)
    @Order(1)
    public void reconcileOnStartup() {
        KoreaderSyncSettings settings = currentSettings();
        if (settings.isExternalServerEnabled()) {
            ensureShelvesForAllUsers(settings.effectiveShelfName());
        }
    }

    @Transactional
    @EventListener
    public void onSettingsChanged(KoreaderSyncSettingsChangedEvent event) {
        boolean wasOn = event.before().isExternalServerEnabled();
        boolean isOn = event.after().isExternalServerEnabled();
        String oldName = event.before().effectiveShelfName();
        String newName = event.after().effectiveShelfName();
        if (isOn && !wasOn) {
            ensureShelvesForAllUsers(newName);
        } else if (isOn && !oldName.equals(newName)) {
            renameShelves(oldName, newName);
            ensureShelvesForAllUsers(newName);
        }
    }

    @Transactional
    public void ensureShelfForUser(BookLoreUserEntity user) {
        KoreaderSyncSettings settings = currentSettings();
        if (settings.isExternalServerEnabled()) {
            ensureShelf(user, settings.effectiveShelfName());
        }
    }

    void ensureShelvesForAllUsers(String name) {
        int created = 0;
        for (BookLoreUserEntity user : userRepository.findAll()) {
            if (ensureShelf(user, name)) {
                created++;
            }
        }
        log.info("KOReader shelf '{}' present for every user ({} created)", name, created);
    }

    void renameShelves(String oldName, String newName) {
        for (ShelfEntity shelf : shelfRepository.findByName(oldName)) {
            if (!BundledIconService.KOREADER_ICON.equals(shelf.getIcon()) || shelf.getIconType() != IconType.CUSTOM_SVG) {
                continue;
            }
            Long userId = shelf.getUser().getId();
            if (shelfRepository.existsByUserIdAndName(userId, newName)) {
                log.warn("User {} already has a shelf named '{}'; leaving '{}' in place", userId, newName, oldName);
                continue;
            }
            shelf.setName(newName);
            shelfRepository.save(shelf);
        }
    }

    private boolean ensureShelf(BookLoreUserEntity user, String name) {
        if (shelfRepository.existsByUserIdAndName(user.getId(), name)) {
            return false;
        }
        shelfRepository.save(ShelfEntity.builder()
                .user(user)
                .name(name)
                .icon(BundledIconService.KOREADER_ICON)
                .iconType(IconType.CUSTOM_SVG)
                .build());
        return true;
    }

    private KoreaderSyncSettings currentSettings() {
        KoreaderSyncSettings settings = appSettingService.getAppSettings().getKoreaderSyncSettings();
        return settings == null ? new KoreaderSyncSettings() : settings;
    }
}
