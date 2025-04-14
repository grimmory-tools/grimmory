package com.adityachandel.booklore.service;

import com.adityachandel.booklore.model.dto.request.MetadataRefreshOptions;
import com.adityachandel.booklore.model.dto.settings.AppSettings;
import com.adityachandel.booklore.model.entity.AppSettingEntity;
import com.adityachandel.booklore.repository.AppSettingsRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AppSettingService {

    private final AppSettingsRepository appSettingsRepository;
    private final ObjectMapper objectMapper;

    public static final String QUICK_BOOK_MATCH = "quick_book_match";
    public static final String COVER_IMAGE_RESOLUTION = "cover_image_resolution";

    private volatile AppSettings appSettings;
    private final ReentrantLock lock = new ReentrantLock();

    public AppSettings getAppSettings() {
        if (appSettings == null) {
            lock.lock();
            try {
                if (appSettings == null) {
                    appSettings = buildAppSettings();
                }
            } finally {
                lock.unlock();
            }
        }
        return appSettings;
    }

    @Transactional
    public void updateSetting(String name, Object val) throws JsonProcessingException {
        AppSettingEntity setting = appSettingsRepository.findByName(name);
        if (setting == null) {
            throw new IllegalArgumentException("Setting not found for name: " + name);
        }
        if (name.equals(QUICK_BOOK_MATCH)) {
            setting.setVal(objectMapper.writeValueAsString(val));
        } else {
            setting.setVal(val.toString());
        }
        appSettingsRepository.save(setting);
        refreshCache();
    }

    private void refreshCache() {
        lock.lock();
        try {
            this.appSettings = buildAppSettings();
        } finally {
            lock.unlock();
        }
    }

    private AppSettings buildAppSettings() {
        List<AppSettingEntity> settings = appSettingsRepository.findAll();
        Map<String, String> settingsMap = settings.stream().collect(Collectors.toMap(AppSettingEntity::getName, AppSettingEntity::getVal));

        AppSettings.AppSettingsBuilder builder = AppSettings.builder();

        if (settingsMap.containsKey(QUICK_BOOK_MATCH)) {
            try {
                MetadataRefreshOptions options = objectMapper.readValue(settingsMap.get(QUICK_BOOK_MATCH), MetadataRefreshOptions.class);
                builder.metadataRefreshOptions(options);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to parse setting 'quick_book_match'", e);
            }
        }

        if (settingsMap.containsKey(COVER_IMAGE_RESOLUTION)) {
            builder.coverSettings(AppSettings.CoverSettings.builder()
                    .resolution(settingsMap.get(COVER_IMAGE_RESOLUTION))
                    .build());
        }

        return builder.build();
    }
}