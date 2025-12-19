package com.adityachandel.booklore.service.appsettings;

import com.adityachandel.booklore.config.AppProperties;
import com.adityachandel.booklore.model.dto.request.MetadataRefreshOptions;
import com.adityachandel.booklore.model.dto.settings.*;
import com.adityachandel.booklore.model.entity.AppSettingEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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

    private final AppProperties appProperties;
    private final SettingPersistenceHelper settingPersistenceHelper;

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
    public void updateSetting(AppSettingKey key, Object val) throws JsonProcessingException {
        var setting = settingPersistenceHelper.appSettingsRepository.findByName(key.toString());
        if (setting == null) {
            setting = new AppSettingEntity();
            setting.setName(key.toString());
        }
        setting.setVal(settingPersistenceHelper.serializeSettingValue(key, val));
        settingPersistenceHelper.appSettingsRepository.save(setting);
        refreshCache();
    }

    public PublicAppSetting getPublicSettings() {
        return buildPublicSetting();
    }

    private void refreshCache() {
        lock.lock();
        try {
            appSettings = buildAppSettings();
        } finally {
            lock.unlock();
        }
    }

    private Map<String, String> getSettingsMap() {
        return settingPersistenceHelper.appSettingsRepository.findAll().stream().collect(Collectors.toMap(AppSettingEntity::getName, AppSettingEntity::getVal));
    }

    private PublicAppSetting buildPublicSetting() {
        Map<String, String> settingsMap = getSettingsMap();
        PublicAppSetting.PublicAppSettingBuilder builder = PublicAppSetting.builder();

        builder.oidcEnabled(Boolean.parseBoolean(settingPersistenceHelper.getOrCreateSetting(AppSettingKey.OIDC_ENABLED, "false")));
        builder.remoteAuthEnabled(appProperties.getRemoteAuth().isEnabled());
        builder.oidcProviderDetails(settingPersistenceHelper.getJsonSetting(settingsMap, AppSettingKey.OIDC_PROVIDER_DETAILS, OidcProviderDetails.class, null, false));

        return builder.build();
    }

    private AppSettings buildAppSettings() {
        Map<String, String> settingsMap = getSettingsMap();

        AppSettings.AppSettingsBuilder builder = AppSettings.builder();
        builder.remoteAuthEnabled(appProperties.getRemoteAuth().isEnabled());

        builder.defaultMetadataRefreshOptions(settingPersistenceHelper.getJsonSetting(settingsMap, AppSettingKey.QUICK_BOOK_MATCH, MetadataRefreshOptions.class, settingPersistenceHelper.getDefaultMetadataRefreshOptions(), true));
        builder.libraryMetadataRefreshOptions(settingPersistenceHelper.getJsonSetting(settingsMap, AppSettingKey.LIBRARY_METADATA_REFRESH_OPTIONS, new TypeReference<>() {
        }, List.of(), true));
        builder.oidcProviderDetails(settingPersistenceHelper.getJsonSetting(settingsMap, AppSettingKey.OIDC_PROVIDER_DETAILS, OidcProviderDetails.class, null, false));
        builder.oidcAutoProvisionDetails(settingPersistenceHelper.getJsonSetting(settingsMap, AppSettingKey.OIDC_AUTO_PROVISION_DETAILS, OidcAutoProvisionDetails.class, null, false));
        builder.metadataProviderSettings(settingPersistenceHelper.getJsonSetting(settingsMap, AppSettingKey.METADATA_PROVIDER_SETTINGS, MetadataProviderSettings.class, settingPersistenceHelper.getDefaultMetadataProviderSettings(), true));
        builder.metadataMatchWeights(settingPersistenceHelper.getJsonSetting(settingsMap, AppSettingKey.METADATA_MATCH_WEIGHTS, MetadataMatchWeights.class, settingPersistenceHelper.getDefaultMetadataMatchWeights(), true));
        builder.metadataPersistenceSettings(settingPersistenceHelper.getJsonSetting(settingsMap, AppSettingKey.METADATA_PERSISTENCE_SETTINGS, MetadataPersistenceSettings.class, settingPersistenceHelper.getDefaultMetadataPersistenceSettings(), true));
        builder.metadataPublicReviewsSettings(settingPersistenceHelper.getJsonSetting(settingsMap, AppSettingKey.METADATA_PUBLIC_REVIEWS_SETTINGS, MetadataPublicReviewsSettings.class, settingPersistenceHelper.getDefaultMetadataPublicReviewsSettings(), true));
        builder.koboSettings(settingPersistenceHelper.getJsonSetting(settingsMap, AppSettingKey.KOBO_SETTINGS, KoboSettings.class, settingPersistenceHelper.getDefaultKoboSettings(), true));
        builder.coverCroppingSettings(settingPersistenceHelper.getJsonSetting(settingsMap, AppSettingKey.COVER_CROPPING_SETTINGS, CoverCroppingSettings.class, settingPersistenceHelper.getDefaultCoverCroppingSettings(), true));

        builder.autoBookSearch(Boolean.parseBoolean(settingPersistenceHelper.getOrCreateSetting(AppSettingKey.AUTO_BOOK_SEARCH, "true")));
        builder.uploadPattern(settingPersistenceHelper.getOrCreateSetting(AppSettingKey.UPLOAD_FILE_PATTERN, "{authors}/<{series}/><{seriesIndex}. >{title}< - {authors}>< ({year})>"));
        builder.similarBookRecommendation(Boolean.parseBoolean(settingPersistenceHelper.getOrCreateSetting(AppSettingKey.SIMILAR_BOOK_RECOMMENDATION, "true")));
        builder.opdsServerEnabled(Boolean.parseBoolean(settingPersistenceHelper.getOrCreateSetting(AppSettingKey.OPDS_SERVER_ENABLED, "false")));
        builder.cbxCacheSizeInMb(Integer.parseInt(settingPersistenceHelper.getOrCreateSetting(AppSettingKey.CBX_CACHE_SIZE_IN_MB, "5120")));
        builder.pdfCacheSizeInMb(Integer.parseInt(settingPersistenceHelper.getOrCreateSetting(AppSettingKey.PDF_CACHE_SIZE_IN_MB, "5120")));
        builder.maxFileUploadSizeInMb(Integer.parseInt(settingPersistenceHelper.getOrCreateSetting(AppSettingKey.MAX_FILE_UPLOAD_SIZE_IN_MB, "100")));
        builder.bookDeletionEnabled(Boolean.parseBoolean(settingPersistenceHelper.getOrCreateSetting(AppSettingKey.BOOK_DELETION_ENABLED, "false")));
        builder.metadataDownloadOnBookdrop(Boolean.parseBoolean(settingPersistenceHelper.getOrCreateSetting(AppSettingKey.METADATA_DOWNLOAD_ON_BOOKDROP, "true")));

        boolean settingEnabled = Boolean.parseBoolean(settingPersistenceHelper.getOrCreateSetting(AppSettingKey.OIDC_ENABLED, "false"));
        Boolean forceDisable = appProperties.getForceDisableOidc();
        boolean finalEnabled = settingEnabled && (forceDisable == null || !forceDisable);
        builder.oidcEnabled(finalEnabled);

        return builder.build();
    }
}
