package org.grimmory.service.user;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grimmory.model.dto.BookLoreUser;
import org.grimmory.model.dto.settings.UserSettingKey;
import org.grimmory.model.entity.BookLoreUserEntity;
import org.grimmory.model.entity.UserSettingEntity;
import org.grimmory.repository.UserRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.time.Duration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

@RequiredArgsConstructor
@Service
@Slf4j
public class DefaultSettingInitializer {

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final DefaultUserSettingsProvider settingsProvider;
    private static final Cache<Long, Boolean> initializedUsers = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofHours(24))
            .build();
    private static final ConcurrentHashMap<Long, Lock> userLocks = new ConcurrentHashMap<>();

    @Transactional
    public void ensureDefaultSettings(BookLoreUser bookLoreUser) {
        if (initializedUsers.getIfPresent(bookLoreUser.getId()) != null) {
            return;
        }
        Lock lock = userLocks.computeIfAbsent(bookLoreUser.getId(), _ -> new ReentrantLock());
        lock.lock();
        try {
            if (initializedUsers.getIfPresent(bookLoreUser.getId()) != null) return;
            BookLoreUserEntity user = userRepository.findByIdWithSettings(bookLoreUser.getId()).orElseThrow(() -> new UsernameNotFoundException("User not found"));
            for (UserSettingKey key : settingsProvider.getAllKeys()) {
                addSettingIfMissing(user, key, settingsProvider.getDefaultValue(key));
            }
            patchPerBookSetting(user);
            userRepository.save(user);
            initializedUsers.put(bookLoreUser.getId(), Boolean.TRUE);
        } finally {
            lock.unlock();
            userLocks.remove(bookLoreUser.getId());
        }
    }

    private void addSettingIfMissing(BookLoreUserEntity user, UserSettingKey key, Object value) {
        boolean exists = user.getSettings().stream().anyMatch(s -> s.getSettingKey().equals(key.getDbKey()));
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
