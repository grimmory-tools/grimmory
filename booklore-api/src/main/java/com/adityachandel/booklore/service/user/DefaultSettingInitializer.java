package com.adityachandel.booklore.service.user;

import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.settings.UserSettingKey;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.model.entity.UserSettingEntity;
import com.adityachandel.booklore.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
@Service
@Slf4j
public class DefaultSettingInitializer {

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final DefaultUserSettingsProvider settingsProvider;
    private static final Set<Long> initializedUsers = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Transactional
    public void ensureDefaultSettings(BookLoreUser bookLoreUser) {
        if (initializedUsers.contains(bookLoreUser.getId())) {
            return;
        }
        synchronized (("user-init-" + bookLoreUser.getId()).intern()) {
            if (initializedUsers.contains(bookLoreUser.getId())) return;
            BookLoreUserEntity user = userRepository.findById(bookLoreUser.getId()).orElseThrow(() -> new UsernameNotFoundException("User not found"));
            for (UserSettingKey key : settingsProvider.getAllKeys()) {
                addSettingIfMissing(user, key, settingsProvider.getDefaultValue(key));
            }
            patchPerBookSetting(user);
            userRepository.save(user);
            initializedUsers.add(bookLoreUser.getId());
        }
    }

    private void addSettingIfMissing(BookLoreUserEntity user, UserSettingKey key, Object value) {
        boolean exists = user.getSettings().stream()
                .anyMatch(s -> s.getSettingKey().equals(key.getDbKey()));
        if (!exists) {
            try {
                String serializedValue = key.isJson()
                        ? objectMapper.writeValueAsString(value)
                        : value.toString();

                user.getSettings().add(UserSettingEntity.builder()
                        .user(user)
                        .settingKey(key.getDbKey())
                        .settingValue(serializedValue)
                        .build());

                log.info("Added default {} for user {}", key, user.getUsername());
            } catch (Exception e) {
                log.error("Failed to add default {} for user {}", key, user.getUsername(), e);
            }
        }
    }

    private void patchPerBookSetting(BookLoreUserEntity user) {
        user.getSettings()
                .stream()
                .filter(s -> s.getSettingKey().equals(UserSettingKey.PER_BOOK_SETTING.getDbKey()))
                .findFirst()
                .ifPresent(setting -> {
                    try {
                        var current = objectMapper.readValue(setting.getSettingValue(), BookLoreUser.UserSettings.PerBookSetting.class);
                        if (current.getCbx() == null) {
                            current.setCbx(BookLoreUser.UserSettings.PerBookSetting.GlobalOrIndividual.Individual);
                            setting.setSettingValue(objectMapper.writeValueAsString(current));
                        }
                    } catch (Exception e) {
                        log.error("Failed to patch PER_BOOK_SETTING for user {}", user.getUsername(), e);
                    }
                });
    }
}

