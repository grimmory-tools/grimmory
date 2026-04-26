package org.grimmory.service.appsettings;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.request.MetadataRefreshOptions;
import org.booklore.model.dto.settings.*;
import org.booklore.model.entity.AppSettingEntity;
import org.booklore.model.enums.MetadataProvider;
import org.booklore.model.enums.MetadataReplaceMode;
import org.booklore.repository.AppSettingsRepository;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
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
            } catch (JacksonException e) {
                log.error("Failed to parse JSON for setting key '{}'. Using default value. Error: {}", key, e.getMessage());
                return defaultValue;
            }
        }
        if (defaultValue != null && persistDefault) {
            try {
                saveDefaultSetting(key, objectMapper.writeValueAsString(defaultValue));
            } catch (JacksonException e) {
                log.error("Failed to persist default value for setting key '{}'. Error: {}", key, e.getMessage());
            }
        }
        return defaultValue;
    }

    @FunctionalInterface
    private interface JsonDeserializer<T> {
        T deserialize(String json) throws JacksonException;
    }

    public String serializeSettingValue(AppSettingKey key, Object val) throws JacksonException {
        if (val == null) {
            return null;
        }
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

        MetadataProviderSettings.Ranobedb defaultRanobedb = new MetadataProviderSettings.Ranobedb();
        defaultRanobedb.setEnabled(false);

        defaultMetadataProviderSettings.setAmazon(defaultAmazon);
        defaultMetadataProviderSettings.setGoogle(defaultGoogle);
        defaultMetadataProviderSettings.setGoodReads(defaultGoodreads);
        defaultMetadataProviderSettings.setHardcover(defaultHardcover);
        defaultMetadataProviderSettings.setComicvine(defaultComicvine);
        defaultMetadataProviderSettings.setRanobedb(defaultRanobedb);
        defaultMetadataProviderSettings.setDouban(defaultDouban);

        return defaultMetadataProviderSettings;
    }

    MetadataRefreshOptions getDefaultMetadataRefreshOptions() {
        MetadataRefreshOptions.FieldProvider goodreadsGoogleProvider = MetadataRefreshOptions.FieldProvider.builder()
                .p1(MetadataProvider.GoodReads)
                .p2(MetadataProvider.Google)
                .build();

        MetadataRefreshOptions.FieldProvider nullProvider = MetadataRefreshOptions.FieldProvider.builder()
                .build();

        MetadataRefreshOptions.FieldOptions fieldOptions = MetadataRefreshOptions.FieldOptions.builder()
                .title(goodreadsGoogleProvider)
                .subtitle(goodreadsGoogleProvider)
                .description(goodreadsGoogleProvider)
                .authors(goodreadsGoogleProvider)
                .publisher(goodreadsGoogleProvider)
                .publishedDate(goodreadsGoogleProvider)
                .seriesName(goodreadsGoogleProvider)
                .seriesNumber(goodreadsGoogleProvider)
                .seriesTotal(goodreadsGoogleProvider)
                .isbn13(goodreadsGoogleProvider)
                .isbn10(goodreadsGoogleProvider)
                .language(goodreadsGoogleProvider)
                .categories(goodreadsGoogleProvider)
                .cover(goodreadsGoogleProvider)
                .pageCount(goodreadsGoogleProvider)
                .asin(nullProvider)
                .goodreadsId(nullProvider)
                .comicvineId(nullProvider)
                .hardcoverId(nullProvider)
                .hardcoverBookId(nullProvider)
                .googleId(nullProvider)
                .lubimyczytacId(nullProvider)
                .amazonRating(nullProvider)
                .amazonReviewCount(nullProvider)
                .goodreadsRating(nullProvider)
                .goodreadsReviewCount(nullProvider)
                .hardcoverRating(nullProvider)
                .hardcoverReviewCount(nullProvider)
                .lubimyczytacRating(nullProvider)
                .ranobedbId(nullProvider)
                .ranobedbRating(nullProvider)
                .audibleId(nullProvider)
                .audibleRating(nullProvider)
                .audibleReviewCount(nullProvider)
                .moods(nullProvider)
                .tags(nullProvider)
                .build();

        MetadataRefreshOptions.EnabledFields enabledFields = MetadataRefreshOptions.EnabledFields.builder()
                .title(true)
                .subtitle(true)
                .description(true)
                .authors(true)
                .publisher(true)
                .publishedDate(true)
                .seriesName(true)
                .seriesNumber(true)
                .seriesTotal(true)
                .isbn13(true)
                .isbn10(true)
                .language(true)
                .categories(true)
                .cover(true)
                .pageCount(true)
                .asin(true)
                .goodreadsId(true)
                .comicvineId(true)
                .hardcoverId(true)
                .hardcoverBookId(true)
                .googleId(true)
                .lubimyczytacId(true)
                .amazonRating(true)
                .amazonReviewCount(true)
                .goodreadsRating(true)
                .goodreadsReviewCount(true)
                .hardcoverRating(true)
                .hardcoverReviewCount(true)
                .lubimyczytacRating(true)
                .ranobedbId(false)
                .ranobedbRating(false)
                .audibleId(true)
                .audibleRating(true)
                .audibleReviewCount(true)
                .moods(true)
                .tags(true)
                .build();

        return MetadataRefreshOptions.builder()
                .libraryId(null)
                .refreshCovers(false)
                .mergeCategories(true)
                .reviewBeforeApply(false)
                .replaceMode(MetadataReplaceMode.REPLACE_MISSING)
                .fieldOptions(fieldOptions)
                .enabledFields(enabledFields)
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
                .ranobedbRating(2)
                .lubimyczytacRating(2)
                .audibleRating(0)
                .audibleReviewCount(0)
                .coverImage(5)
                .build();
    }

    public MetadataPersistenceSettings getDefaultMetadataPersistenceSettings() {
        MetadataPersistenceSettings.FormatSettings epubSettings = MetadataPersistenceSettings.FormatSettings.builder()
                .enabled(false)
                .maxFileSizeInMb(250)
                .build();

        MetadataPersistenceSettings.FormatSettings pdfSettings = MetadataPersistenceSettings.FormatSettings.builder()
                .enabled(false)
                .maxFileSizeInMb(250)
                .build();

        MetadataPersistenceSettings.FormatSettings cbxSettings = MetadataPersistenceSettings.FormatSettings.builder()
                .enabled(false)
                .maxFileSizeInMb(250)
                .build();

        MetadataPersistenceSettings.FormatSettings audiobookSettings = MetadataPersistenceSettings.FormatSettings.builder()
                .enabled(false)
                .maxFileSizeInMb(250)
                .build();

        MetadataPersistenceSettings.SaveToOriginalFile saveToOriginalFile = MetadataPersistenceSettings.SaveToOriginalFile.builder()
                .epub(epubSettings)
                .pdf(pdfSettings)
                .cbx(cbxSettings)
                .audiobook(audiobookSettings)
                .build();

        return MetadataPersistenceSettings.builder()
                .saveToOriginalFile(saveToOriginalFile)
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
                .convertToKepub(true)
                .conversionLimitInMb(100)
                .convertCbxToEpub(false)
                .conversionLimitInMbForCbx(100)
                .conversionImageCompressionPercentage(85)
                .forceEnableHyphenation(false)
                .forwardToKoboStore(true)
                .build();
    }

    public CoverCroppingSettings getDefaultCoverCroppingSettings() {
        return CoverCroppingSettings.builder()
                .verticalCroppingEnabled(false)
                .horizontalCroppingEnabled(false)
                .aspectRatioThreshold(2.5)
                .smartCroppingEnabled(false)
                .build();
    }

    public MetadataProviderSpecificFields getDefaultMetadataProviderSpecificFields() {
        MetadataProviderSpecificFields fields = new MetadataProviderSpecificFields();
        fields.setAsin(true);
        fields.setAmazonRating(true);
        fields.setAmazonReviewCount(true);
        fields.setGoogleId(true);
        fields.setGoodreadsId(true);
        fields.setGoodreadsRating(true);
        fields.setGoodreadsReviewCount(true);
        fields.setHardcoverId(true);
        fields.setHardcoverBookId(true);
        fields.setHardcoverRating(true);
        fields.setHardcoverReviewCount(true);
        fields.setComicvineId(true);
        fields.setLubimyczytacId(true);
        fields.setLubimyczytacRating(true);
        fields.setRanobedbRating(true);
        fields.setAudibleId(true);
        fields.setAudibleRating(true);
        fields.setAudibleReviewCount(true);
        return fields;
    }
}
