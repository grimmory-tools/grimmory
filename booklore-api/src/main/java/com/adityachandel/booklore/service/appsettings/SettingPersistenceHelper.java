package com.adityachandel.booklore.service.appsettings;

import com.adityachandel.booklore.model.dto.request.MetadataRefreshOptions;
import com.adityachandel.booklore.model.dto.settings.*;
import com.adityachandel.booklore.model.entity.AppSettingEntity;
import com.adityachandel.booklore.model.enums.MetadataProvider;
import com.adityachandel.booklore.repository.AppSettingsRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
        return getJsonSettingInternal(settingsMap, key, defaultValue, persistDefault,
                json -> objectMapper.readValue(json, clazz));
    }

    public <T> T getJsonSetting(Map<String, String> settingsMap, AppSettingKey key, TypeReference<T> typeReference, T defaultValue, boolean persistDefault) {
        return getJsonSettingInternal(settingsMap, key, defaultValue, persistDefault,
                json -> objectMapper.readValue(json, typeReference));
    }

    private <T> T getJsonSettingInternal(Map<String, String> settingsMap, AppSettingKey key, T defaultValue, boolean persistDefault, JsonDeserializer<T> deserializer) {
        String json = settingsMap.get(key.toString());
        if (json != null && !json.isBlank()) {
            try {
                return deserializer.deserialize(json);
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

    @FunctionalInterface
    private interface JsonDeserializer<T> {
        T deserialize(String json) throws JsonProcessingException;
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
        MetadataRefreshOptions.FieldProvider amazonProvider = MetadataRefreshOptions.FieldProvider.builder()
                .p1(MetadataProvider.Amazon)
                .build();

        MetadataRefreshOptions.FieldProvider nullProvider = MetadataRefreshOptions.FieldProvider.builder()
                .build();

        MetadataRefreshOptions.FieldOptions fieldOptions = MetadataRefreshOptions.FieldOptions.builder()
                .title(amazonProvider)
                .subtitle(amazonProvider)
                .description(amazonProvider)
                .authors(amazonProvider)
                .publisher(amazonProvider)
                .publishedDate(amazonProvider)
                .seriesName(amazonProvider)
                .seriesNumber(amazonProvider)
                .seriesTotal(amazonProvider)
                .isbn13(amazonProvider)
                .isbn10(amazonProvider)
                .language(amazonProvider)
                .categories(amazonProvider)
                .cover(amazonProvider)
                .pageCount(amazonProvider)
                .asin(nullProvider)
                .goodreadsId(nullProvider)
                .comicvineId(nullProvider)
                .hardcoverId(nullProvider)
                .googleId(nullProvider)
                .amazonRating(nullProvider)
                .amazonReviewCount(nullProvider)
                .goodreadsRating(nullProvider)
                .goodreadsReviewCount(nullProvider)
                .hardcoverRating(nullProvider)
                .hardcoverReviewCount(nullProvider)
                .moods(nullProvider)
                .tags(nullProvider)
                .build();

        MetadataRefreshOptions.SkipFields skipFields = MetadataRefreshOptions.SkipFields.builder()
                .title(false)
                .subtitle(false)
                .description(false)
                .authors(false)
                .publisher(false)
                .publishedDate(false)
                .seriesName(false)
                .seriesNumber(false)
                .seriesTotal(false)
                .isbn13(false)
                .isbn10(false)
                .language(false)
                .categories(false)
                .cover(false)
                .pageCount(false)
                .asin(false)
                .goodreadsId(false)
                .comicvineId(false)
                .hardcoverId(false)
                .googleId(false)
                .amazonRating(false)
                .amazonReviewCount(false)
                .goodreadsRating(false)
                .goodreadsReviewCount(false)
                .hardcoverRating(false)
                .hardcoverReviewCount(false)
                .moods(false)
                .tags(false)
                .build();

        return MetadataRefreshOptions.builder()
                .libraryId(null)
                .refreshCovers(false)
                .mergeCategories(true)
                .reviewBeforeApply(false)
                .fieldOptions(fieldOptions)
                .skipFields(skipFields)
                .build();
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
                .moveFilesToLibraryPattern(false)
                .build();
    }

    public MetadataPublicReviewsSettings getDefaultMetadataPublicReviewsSettings() {
        return MetadataPublicReviewsSettings.builder()
                .downloadEnabled(true)
                .autoDownloadEnabled(false)
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