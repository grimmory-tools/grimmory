package com.adityachandel.booklore.service;

import com.adityachandel.booklore.model.dto.request.MetadataRefreshOptions;
import com.adityachandel.booklore.model.dto.settings.AppSettings;
import com.adityachandel.booklore.model.dto.settings.OidcAutoProvisionDetails;
import com.adityachandel.booklore.model.dto.settings.OidcProviderDetails;
import com.adityachandel.booklore.model.entity.AppSettingEntity;
import com.adityachandel.booklore.repository.AppSettingsRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
    public static final String OPDS_SERVER_ENABLED = "opds_server_enabled";
    public static final String OIDC_ENABLED = "oidc_enabled";
    public static final String OIDC_PROVIDER_DETAILS = "oidc_provider_details";
    public static final String OIDC_AUTO_PROVISION_DETAILS = "oidc_auto_provision_details";

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
            setting = new AppSettingEntity();
            setting.setName(name);
        }

        if (QUICK_BOOK_MATCH.equals(name) || OIDC_PROVIDER_DETAILS.equals(name) || OIDC_AUTO_PROVISION_DETAILS.equals(name)) {
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
            appSettings = buildAppSettings();
        } finally {
            lock.unlock();
        }
    }

    private AppSettings buildAppSettings() {
        Map<String, String> settingsMap = appSettingsRepository.findAll().stream().collect(Collectors.toMap(AppSettingEntity::getName, AppSettingEntity::getVal));

        AppSettings.AppSettingsBuilder builder = AppSettings.builder();

        if (settingsMap.containsKey(QUICK_BOOK_MATCH)) {
            try {
                builder.metadataRefreshOptions(objectMapper.readValue(settingsMap.get(QUICK_BOOK_MATCH), MetadataRefreshOptions.class));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to parse " + QUICK_BOOK_MATCH, e);
            }
        }

        String oidcProviderDetailsJson = settingsMap.get(OIDC_PROVIDER_DETAILS);
        if (oidcProviderDetailsJson != null && !oidcProviderDetailsJson.isBlank()) {
            try {
                builder.oidcProviderDetails(objectMapper.readValue(oidcProviderDetailsJson, OidcProviderDetails.class));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to parse " + OIDC_PROVIDER_DETAILS, e);
            }
        } else {
            builder.oidcProviderDetails(null);
        }

        String oidcAutoProvisionDetailsJson = settingsMap.get(OIDC_AUTO_PROVISION_DETAILS);
        if (oidcAutoProvisionDetailsJson != null && !oidcAutoProvisionDetailsJson.isBlank()) {
            try {
                builder.oidcAutoProvisionDetails(objectMapper.readValue(oidcAutoProvisionDetailsJson, OidcAutoProvisionDetails.class));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to parse " + OIDC_AUTO_PROVISION_DETAILS, e);
            }
        } else {
            builder.oidcAutoProvisionDetails(null);
        }

        builder.coverResolution(getOrCreateSetting(COVER_IMAGE_RESOLUTION, "250x350"));
        builder.autoBookSearch(Boolean.parseBoolean(getOrCreateSetting(AUTO_BOOK_SEARCH, "true")));
        builder.uploadPattern(getOrCreateSetting(UPLOAD_FILE_PATTERN, ""));
        builder.similarBookRecommendation(Boolean.parseBoolean(getOrCreateSetting(SIMILAR_BOOK_RECOMMENDATION, "true")));
        builder.opdsServerEnabled(Boolean.parseBoolean(getOrCreateSetting(OPDS_SERVER_ENABLED, "false")));
        builder.oidcEnabled(Boolean.parseBoolean(getOrCreateSetting(OIDC_ENABLED, "false")));

        return builder.build();
    }

    private String getOrCreateSetting(String name, String defaultValue) {
        AppSettingEntity setting = appSettingsRepository.findByName(name);
        if (setting != null) {
            return setting.getVal();
        }
        saveDefaultSetting(name, defaultValue);
        return defaultValue;
    }

    private void saveDefaultSetting(String name, String value) {
        AppSettingEntity setting = new AppSettingEntity();
        setting.setName(name);
        setting.setVal(value);
        appSettingsRepository.save(setting);
    }
}