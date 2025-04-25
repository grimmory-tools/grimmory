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
    public static final String AUTO_BOOK_SEARCH = "auto_book_search";
    public static final String COVER_IMAGE_RESOLUTION = "cover_image_resolution";
    public static final String SIMILAR_BOOK_RECOMMENDATION = "similar_book_recommendation";
    public static final String UPLOAD_FILE_PATTERN = "upload_file_pattern";

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

        if (QUICK_BOOK_MATCH.equals(name)) {
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
                throw new RuntimeException("Failed to parse setting: " + QUICK_BOOK_MATCH, e);
            }
        }

        builder.coverResolution(getOrCreateSetting(COVER_IMAGE_RESOLUTION, "250x350"));
        builder.autoBookSearch(Boolean.parseBoolean(getOrCreateSetting(AUTO_BOOK_SEARCH, "true")));
        builder.uploadPattern(getOrCreateSetting(UPLOAD_FILE_PATTERN, ""));
        builder.similarBookRecommendation(Boolean.parseBoolean(getOrCreateSetting(SIMILAR_BOOK_RECOMMENDATION, "true")));
        return builder.build();
    }

    private String getOrCreateSetting(String name, String defaultValue) {
        AppSettingEntity existing = appSettingsRepository.findByName(name);
        if (existing != null) {
            return existing.getVal();
        }
        AppSettingEntity newSetting = new AppSettingEntity();
        newSetting.setName(name);
        newSetting.setVal(defaultValue);
        appSettingsRepository.save(newSetting);
        return defaultValue;
    }
}