package com.adityachandel.booklore.service.appsettings;

import com.adityachandel.booklore.model.dto.request.MetadataRefreshOptions;
import com.adityachandel.booklore.model.dto.settings.*;
import com.adityachandel.booklore.model.entity.AppSettingEntity;
import com.adityachandel.booklore.model.enums.MetadataProvider;
import com.adityachandel.booklore.repository.AppSettingsRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SettingPersistenceHelper {

    public final AppSettingsRepository appSettingsRepository;
    private final ObjectMapper objectMapper;

    public String getOrCreateSetting(AppSettingKey key, String defaultValue) {
        var setting = appSettingsRepository.findByName(key.toString());
        if (setting != null) return setting.getVal();

        saveDefaultSetting(key, defaultValue);
        return defaultValue;
    }

    public void saveDefaultSetting(AppSettingKey key, String value) {
        AppSettingEntity setting = new AppSettingEntity();
        setting.setName(key.toString());
        setting.setVal(value);
        appSettingsRepository.save(setting);
    }

    public <T> T getJsonSetting(Map<String, String> settingsMap, AppSettingKey key, Class<T> clazz, T defaultValue, boolean persistDefault) {
        String json = settingsMap.get(key.toString());
        if (json != null && !json.isBlank()) {
            try {
                return objectMapper.readValue(json, clazz);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to parse " + key, e);
            }
        }
        if (defaultValue != null && persistDefault) {
            try {
                saveDefaultSetting(key, objectMapper.writeValueAsString(defaultValue));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to persist default for " + key, e);
            }
        }
        return defaultValue;
    }

    public String serializeSettingValue(AppSettingKey key, Object val) throws JsonProcessingException {
        return key.isJson() ? objectMapper.writeValueAsString(val) : val.toString();
    }

    public MetadataProviderSettings getDefaultMetadataProviderSettings() {
        MetadataProviderSettings defaultMetadataProviderSettings = new MetadataProviderSettings();

        MetadataProviderSettings.Amazon defaultAmazon = new MetadataProviderSettings.Amazon();
        defaultAmazon.setEnabled(true);
        defaultAmazon.setCookie(null);
        defaultAmazon.setDomain("com");

        MetadataProviderSettings.Google defaultGoogle = new MetadataProviderSettings.Google();
        defaultGoogle.setEnabled(true);

        MetadataProviderSettings.Goodreads defaultGoodreads = new MetadataProviderSettings.Goodreads();
        defaultGoodreads.setEnabled(true);

        MetadataProviderSettings.Hardcover defaultHardcover = new MetadataProviderSettings.Hardcover();
        defaultHardcover.setEnabled(false);
        defaultHardcover.setApiKey(null);

        MetadataProviderSettings.Comicvine defaultComicvine = new MetadataProviderSettings.Comicvine();
        defaultComicvine.setEnabled(false);
        defaultComicvine.setApiKey(null);

        MetadataProviderSettings.Douban defaultDouban = new MetadataProviderSettings.Douban();
        defaultDouban.setEnabled(false);

        defaultMetadataProviderSettings.setAmazon(defaultAmazon);
        defaultMetadataProviderSettings.setGoogle(defaultGoogle);
        defaultMetadataProviderSettings.setGoodReads(defaultGoodreads);
        defaultMetadataProviderSettings.setHardcover(defaultHardcover);
        defaultMetadataProviderSettings.setComicvine(defaultComicvine);
        defaultMetadataProviderSettings.setDouban(defaultDouban);

        return defaultMetadataProviderSettings;
    }

    MetadataRefreshOptions getDefaultMetadataRefreshOptions() {
        MetadataRefreshOptions.FieldProvider titleProviders =
                new MetadataRefreshOptions.FieldProvider(null, MetadataProvider.Google, MetadataProvider.Amazon, MetadataProvider.GoodReads);
        MetadataRefreshOptions.FieldProvider subtitleProviders =
                new MetadataRefreshOptions.FieldProvider(null, MetadataProvider.Google, MetadataProvider.Amazon, MetadataProvider.GoodReads);
        MetadataRefreshOptions.FieldProvider descriptionProviders =
                new MetadataRefreshOptions.FieldProvider(null, MetadataProvider.Google, MetadataProvider.Amazon, MetadataProvider.GoodReads);
        MetadataRefreshOptions.FieldProvider authorsProviders =
                new MetadataRefreshOptions.FieldProvider(null, MetadataProvider.Google, MetadataProvider.Amazon, MetadataProvider.GoodReads);
        MetadataRefreshOptions.FieldProvider publisherProviders =
                new MetadataRefreshOptions.FieldProvider(null, MetadataProvider.Google, MetadataProvider.Amazon, MetadataProvider.GoodReads);
        MetadataRefreshOptions.FieldProvider publishedDateProviders =
                new MetadataRefreshOptions.FieldProvider(null, MetadataProvider.Google, MetadataProvider.Amazon, MetadataProvider.GoodReads);
        MetadataRefreshOptions.FieldProvider seriesNameProviders =
                new MetadataRefreshOptions.FieldProvider(null, MetadataProvider.Google, MetadataProvider.Amazon, MetadataProvider.GoodReads);
        MetadataRefreshOptions.FieldProvider seriesNumberProviders =
                new MetadataRefreshOptions.FieldProvider(null, MetadataProvider.Google, MetadataProvider.Amazon, MetadataProvider.GoodReads);
        MetadataRefreshOptions.FieldProvider seriesTotalProviders =
                new MetadataRefreshOptions.FieldProvider(null, MetadataProvider.Google, MetadataProvider.Amazon, MetadataProvider.GoodReads);
        MetadataRefreshOptions.FieldProvider isbn13Providers =
                new MetadataRefreshOptions.FieldProvider(null, MetadataProvider.Google, MetadataProvider.Amazon, MetadataProvider.GoodReads);
        MetadataRefreshOptions.FieldProvider isbn10Providers =
                new MetadataRefreshOptions.FieldProvider(null, MetadataProvider.Google, MetadataProvider.Amazon, MetadataProvider.GoodReads);
        MetadataRefreshOptions.FieldProvider languageProviders =
                new MetadataRefreshOptions.FieldProvider(null, MetadataProvider.Google, MetadataProvider.Amazon, MetadataProvider.GoodReads);
        MetadataRefreshOptions.FieldProvider categoriesProviders =
                new MetadataRefreshOptions.FieldProvider(null, MetadataProvider.Google, MetadataProvider.Amazon, MetadataProvider.GoodReads);
        MetadataRefreshOptions.FieldProvider coverProviders =
                new MetadataRefreshOptions.FieldProvider(null, MetadataProvider.Google, MetadataProvider.Amazon, MetadataProvider.GoodReads);

        MetadataRefreshOptions.FieldOptions fieldOptions = new MetadataRefreshOptions.FieldOptions(
                titleProviders,
                subtitleProviders,
                descriptionProviders,
                authorsProviders,
                publisherProviders,
                publishedDateProviders,
                seriesNameProviders,
                seriesNumberProviders,
                seriesTotalProviders,
                isbn13Providers,
                isbn10Providers,
                languageProviders,
                categoriesProviders,
                coverProviders
        );

        return new MetadataRefreshOptions(
                MetadataProvider.GoodReads,
                MetadataProvider.Amazon,
                MetadataProvider.Google,
                null,
                false,
                true,
                false,
                fieldOptions
        );
    }

    public MetadataMatchWeights getDefaultMetadataMatchWeights() {
        return MetadataMatchWeights.builder()
                .title(10)
                .subtitle(1)
                .description(10)
                .authors(10)
                .publisher(5)
                .publishedDate(3)
                .seriesName(2)
                .seriesNumber(2)
                .seriesTotal(1)
                .isbn13(3)
                .isbn10(5)
                .language(2)
                .pageCount(1)
                .categories(10)
                .amazonRating(3)
                .amazonReviewCount(2)
                .goodreadsRating(4)
                .goodreadsReviewCount(2)
                .hardcoverRating(2)
                .hardcoverReviewCount(1)
                .doubanRating(3)
                .doubanReviewCount(2)
                .coverImage(5)
                .build();
    }

    public MetadataPersistenceSettings getDefaultMetadataPersistenceSettings() {
        return MetadataPersistenceSettings.builder()
                .saveToOriginalFile(false)
                .convertCbrCb7ToCbz(false)
                .backupMetadata(false)
                .backupCover(false)
                .build();
    }

    public MetadataPublicReviewsSettings getDefaultMetadataPublicReviewsSettings() {
        return MetadataPublicReviewsSettings.builder()
                .downloadEnabled(true)
                .providers(Set.of(
                        MetadataPublicReviewsSettings.ReviewProviderConfig.builder()
                                .provider(MetadataProvider.Amazon)
                                .enabled(true)
                                .maxReviews(5)
                                .build(),
                        MetadataPublicReviewsSettings.ReviewProviderConfig.builder()
                                .provider(MetadataProvider.GoodReads)
                                .enabled(false)
                                .maxReviews(5)
                                .build(),
                        MetadataPublicReviewsSettings.ReviewProviderConfig.builder()
                                .provider(MetadataProvider.Douban)
                                .enabled(false)
                                .maxReviews(5)
                                .build()
                ))
                .build();
    }

    public KoboSettings getDefaultKoboSettings() {
        return KoboSettings.builder()
                .convertToKepub(false)
                .conversionLimitInMb(100)
                .build();
    }
}