package com.adityachandel.booklore.service.appsettings;

import com.adityachandel.booklore.config.AppProperties;
import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.request.MetadataRefreshOptions;
import com.adityachandel.booklore.model.dto.settings.*;
import com.adityachandel.booklore.model.entity.AppSettingEntity;
import com.adityachandel.booklore.model.enums.PermissionType;
import com.adityachandel.booklore.util.UserPermissionUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.transaction.Transactional;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Service
public class AppSettingService {

    private final AppProperties appProperties;
    private final SettingPersistenceHelper settingPersistenceHelper;
    private final AuthenticationService authenticationService;

    private volatile AppSettings appSettings;
    private final ReentrantLock lock = new ReentrantLock();

    public AppSettingService(AppProperties appProperties, SettingPersistenceHelper settingPersistenceHelper, @Lazy AuthenticationService authenticationService) {
        this.appProperties = appProperties;
        this.settingPersistenceHelper = settingPersistenceHelper;
        this.authenticationService = authenticationService;
    }

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
        BookLoreUser user = authenticationService.getAuthenticatedUser();

        validatePermission(key, user);

        var setting = settingPersistenceHelper.appSettingsRepository.findByName(key.toString());
        if (setting == null) {
            setting = new AppSettingEntity();
            setting.setName(key.toString());
        }
        setting.setVal(settingPersistenceHelper.serializeSettingValue(key, val));
        settingPersistenceHelper.appSettingsRepository.save(setting);
        refreshCache();
    }

    private void validatePermission(AppSettingKey key, BookLoreUser user) {
        List<PermissionType> requiredPermissions = key.getRequiredPermissions();
        if (requiredPermissions.isEmpty()) {
            return;
        }

        boolean hasPermission = requiredPermissions.stream().anyMatch(permission ->
                UserPermissionUtils.hasPermission(user.getPermissions(), permission)
        );

        if (!hasPermission) {
            throw new AccessDeniedException("User does not have permission to update " + key.getDbKey());
        }
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
        return settingPersistenceHelper.appSettingsRepository.findAll().stream()
                .filter(entity -> entity.getName() != null && entity.getVal() != null)
                .collect(Collectors.toMap(AppSettingEntity::getName, AppSettingEntity::getVal));
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
        builder.metadataProviderSpecificFields(
            settingPersistenceHelper.getJsonSetting(
                settingsMap,
                AppSettingKey.METADATA_PROVIDER_SPECIFIC_FIELDS,
                MetadataProviderSpecificFields.class,
                settingPersistenceHelper.getDefaultMetadataProviderSpecificFields(),
                true
            )
        );
        builder.autoBookSearch(Boolean.parseBoolean(settingPersistenceHelper.getOrCreateSetting(AppSettingKey.AUTO_BOOK_SEARCH, "true")));
        builder.uploadPattern(settingPersistenceHelper.getOrCreateSetting(AppSettingKey.UPLOAD_FILE_PATTERN, "{authors}/<{series}/><{seriesIndex}. >{title}< - {authors}>< ({year})>"));
        builder.similarBookRecommendation(Boolean.parseBoolean(settingPersistenceHelper.getOrCreateSetting(AppSettingKey.SIMILAR_BOOK_RECOMMENDATION, "true")));
        builder.opdsServerEnabled(Boolean.parseBoolean(settingPersistenceHelper.getOrCreateSetting(AppSettingKey.OPDS_SERVER_ENABLED, "false")));
        builder.komgaApiEnabled(Boolean.parseBoolean(settingPersistenceHelper.getOrCreateSetting(AppSettingKey.KOMGA_API_ENABLED, "false")));
        builder.komgaGroupUnknown(Boolean.parseBoolean(settingPersistenceHelper.getOrCreateSetting(AppSettingKey.KOMGA_GROUP_UNKNOWN, "true")));
        builder.telemetryEnabled(Boolean.parseBoolean(settingPersistenceHelper.getOrCreateSetting(AppSettingKey.TELEMETRY_ENABLED, "true")));
        builder.pdfCacheSizeInMb(Integer.parseInt(settingPersistenceHelper.getOrCreateSetting(AppSettingKey.PDF_CACHE_SIZE_IN_MB, "5120")));
        builder.maxFileUploadSizeInMb(Integer.parseInt(settingPersistenceHelper.getOrCreateSetting(AppSettingKey.MAX_FILE_UPLOAD_SIZE_IN_MB, "100")));
        builder.metadataDownloadOnBookdrop(Boolean.parseBoolean(settingPersistenceHelper.getOrCreateSetting(AppSettingKey.METADATA_DOWNLOAD_ON_BOOKDROP, "true")));

        boolean settingEnabled = Boolean.parseBoolean(settingPersistenceHelper.getOrCreateSetting(AppSettingKey.OIDC_ENABLED, "false"));
        Boolean forceDisable = appProperties.getForceDisableOidc();
        boolean finalEnabled = settingEnabled && (forceDisable == null || !forceDisable);
        builder.oidcEnabled(finalEnabled);

        return builder.build();
    }

    public String getSettingValue(String key) {
        var setting = settingPersistenceHelper.appSettingsRepository.findByName(key);
        return setting != null ? setting.getVal() : null;
    }

    @Transactional
    public void saveSetting(String key, String value) {
        var setting = settingPersistenceHelper.appSettingsRepository.findByName(key);
        if (setting == null) {
            setting = new AppSettingEntity();
            setting.setName(key);
        }
        setting.setVal(value);
        settingPersistenceHelper.appSettingsRepository.save(setting);
    }
}
