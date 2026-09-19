package org.booklore.service.appsettings;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.request.MetadataRefreshOptions;
import org.booklore.model.enums.MetadataProvider;
import org.booklore.model.enums.MetadataReplaceMode;
import org.booklore.repository.AppSettingsRepository;
import org.springframework.transaction.annotation.Transactional;
import org.booklore.config.AppProperties;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.settings.*;
import org.booklore.model.entity.AppSettingEntity;
import org.booklore.model.enums.AuditAction;
import org.booklore.model.enums.PermissionType;
import org.booklore.service.audit.AuditService;
import org.booklore.util.UserPermissionUtils;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.type.TypeFactory;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@DependsOnDatabaseInitialization
public class AppSettingService {
    private static final String DEFAULT_MOBILE_REDIRECT_URI = "grimmory://oauth2-callback";
    private static final String WILDCARD_REDIRECT_URI = "*";

    private final AppProperties appProperties;
    private final AppSettingsRepository appSettingsRepository;
    private final ObjectMapper objectMapper;
    private final AuthenticationService authenticationService;
    private final AuditService auditService;

    private final Cache<AppSettingKey, Optional<String>> cachedSettings = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(Duration.ofHours(24))
            .build();

    public AppSettingService(AppProperties appProperties, AppSettingsRepository appSettingsRepository, ObjectMapper objectMapper, @Lazy AuthenticationService authenticationService, @Lazy AuditService auditService) {
        this.appProperties = appProperties;
        this.appSettingsRepository = appSettingsRepository;
        this.objectMapper = objectMapper;
        this.authenticationService = authenticationService;
        this.auditService = auditService;
    }

    @Transactional
    public void updateSetting(AppSettingKey key, Object val) throws JacksonException {
        BookLoreUser user = authenticationService.getAuthenticatedUser();

        validatePermission(key, user);

        if (key == AppSettingKey.OIDC_REDIRECT_URIS) {
            val = validateAndNormalizeOidcRedirectUris(val);
        }

        if (key == AppSettingKey.OIDC_FORCE_ONLY_MODE) {
            validateOidcForceOnlyMode(val);
        }

        var setting = appSettingsRepository.findByName(key.toString());

        if (setting == null) {
            setting = new AppSettingEntity();
            setting.setName(key.toString());
        }

        if (val == null) {
            setting.setVal(null);
        } else if (key.isJson()) {
            setting.setVal(objectMapper.writeValueAsString(val));
        } else {
            setting.setVal(val.toString());
        }

        appSettingsRepository.save(setting);

        cachedSettings.put(key, Optional.ofNullable(setting.getVal()));

        AuditAction action = switch (key) {
            case AppSettingKey k when k == AppSettingKey.OIDC_FORCE_ONLY_MODE -> AuditAction.OIDC_FORCE_ONLY_MODE_CHANGED;
            case AppSettingKey k when k.name().startsWith("OIDC_") -> AuditAction.OIDC_CONFIG_CHANGED;
            default -> AuditAction.SETTINGS_UPDATED;
        };
        auditService.log(action, "Updated setting: " + key);
    }

    private void validateOidcForceOnlyMode(Object val) {
        boolean enabling = Boolean.parseBoolean(String.valueOf(val));
        if (!enabling) return;

        AppSettings current = getAppSettings();
        if (!current.isOidcEnabled()) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("Cannot enable OIDC-only mode: OIDC must be enabled first");
        }
        OidcProviderDetails details = current.getOidcProviderDetails();
        if (details == null || details.getIssuerUri() == null || details.getIssuerUri().isBlank()
                || details.getClientId() == null || details.getClientId().isBlank()) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("Cannot enable OIDC-only mode: OIDC must be configured with issuer URI and client ID");
        }
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

    private List<String> validateAndNormalizeOidcRedirectUris(Object val) {
        if (val == null) {
            return List.of();
        }
        if (!(val instanceof List<?> rawValues)) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("OIDC redirect URIs must be an array");
        }

        List<String> redirectUris = rawValues.stream()
                .map(value -> {
                    if (value == null) {
                        return null;
                    }
                    if (!(value instanceof String stringValue)) {
                        throw ApiError.GENERIC_BAD_REQUEST.createException("OIDC redirect URIs must be an array of strings");
                    }
                    return stringValue.trim();
                })
                .toList();

        if (redirectUris.contains(WILDCARD_REDIRECT_URI) && redirectUris.size() > 1) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("Wildcard redirect URI must be the only value");
        }

        Set<String> uniqueUris = new LinkedHashSet<>();
        for (String redirectUri : redirectUris) {
            if (redirectUri == null || redirectUri.isBlank()) {
                throw ApiError.GENERIC_BAD_REQUEST.createException("Redirect URI cannot be blank");
            }
            if (!WILDCARD_REDIRECT_URI.equals(redirectUri)) {
                validateMobileRedirectUriShape(redirectUri);
            }
            if (!uniqueUris.add(redirectUri)) {
                throw ApiError.GENERIC_BAD_REQUEST.createException("Duplicate redirect URI: " + redirectUri);
            }
        }

        return List.copyOf(uniqueUris);
    }

    private void validateMobileRedirectUriShape(String redirectUri) {
        try {
            URI uri = URI.create(redirectUri);
            String scheme = uri.getScheme();
            if (scheme == null || scheme.isBlank()) {
                throw ApiError.GENERIC_BAD_REQUEST.createException("Redirect URI must include a scheme");
            }
            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                throw ApiError.GENERIC_BAD_REQUEST.createException("Redirect URI must use a custom mobile scheme");
            }
            if (uri.getFragment() != null) {
                throw ApiError.GENERIC_BAD_REQUEST.createException("Redirect URI must not contain a fragment");
            }
        } catch (IllegalArgumentException _) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("Redirect URI is not a valid URI: " + redirectUri);
        }
    }

    private Map<AppSettingKey, Optional<String>> getSettingsMap() {
        var keys = Arrays.stream(AppSettingKey.values())
                .filter(key -> cachedSettings.getIfPresent(key) == null)
                .collect(Collectors.toSet());

        if (!keys.isEmpty()) {
            log.debug("Settings Key Cache miss: {} keys missing from cache", keys.size());

            Map<String, String> loadedSettings = appSettingsRepository.findAll()
                    .stream()
                    .filter(e -> e.getVal() != null)
                    .collect(
                            Collectors.toMap(
                                    AppSettingEntity::getName,
                                    AppSettingEntity::getVal
                            )
                    );

            cachedSettings.putAll(
                    keys.stream().collect(
                            Collectors.toMap(
                                    key -> key,
                                    key -> Optional.ofNullable(loadedSettings.get(key.getDbKey()))
                            )
                    )
            );
        }

        return cachedSettings.asMap();
    }

    private boolean isOIDCForceDisabled() {
        return (
                appProperties.getOidc() != null &&
                appProperties.getOidc().getForceDisable() != null &&
                appProperties.getOidc().getForceDisable()
        );
    }

    private boolean hasAccess(BookLoreUser.UserPermissions permissions, AppSettingKey key) {
        if (key.isPublic()) {
            return true;
        }

        if (permissions == null) {
            return false;
        }

        return key.getRequiredPermissions().stream()
                .anyMatch((p) -> UserPermissionUtils.hasPermission(permissions, p));
    }

    private <T> List<T> getJsonListSetting(BookLoreUser.UserPermissions permissions, Map<AppSettingKey, Optional<String>> settingsMap, AppSettingKey key, Class<T> classType, List<T> defaultValue) {
        var javaType = TypeFactory.createDefaultInstance().constructParametricType(List.class, classType);
        return getJsonSetting(permissions, settingsMap, key, javaType, defaultValue);
    }

    private <T> T getJsonSetting(BookLoreUser.UserPermissions permissions, Map<AppSettingKey, Optional<String>> settingsMap, AppSettingKey key, Class<T> classType, T defaultValue) {
        var javaType = TypeFactory.createDefaultInstance().constructType(classType);
        return getJsonSetting(permissions, settingsMap, key, javaType, defaultValue);
    }

    private <T> T getJsonSetting(BookLoreUser.UserPermissions permissions, Map<AppSettingKey, Optional<String>> settingsMap, AppSettingKey key, JavaType javaType, T defaultValue) {
        return getValue(permissions, settingsMap, key, defaultValue, (json) -> objectMapper.readValue(json, javaType));
    }

    private String getValue(BookLoreUser.UserPermissions permissions, Map<AppSettingKey, Optional<String>> settingsMap, AppSettingKey key, String defaultValue) {
        return getValue(permissions, settingsMap, key, defaultValue, String::valueOf);
    }

    private boolean getBooleanValue(BookLoreUser.UserPermissions permissions, Map<AppSettingKey, Optional<String>> settingsMap, AppSettingKey key, boolean defaultValue) {
        var value = getValue(permissions, settingsMap, key, defaultValue, Boolean::parseBoolean);

        return value == null ? defaultValue : value;
    }

    private <T> T getValue(BookLoreUser.UserPermissions permissions, Map<AppSettingKey, Optional<String>> settingsMap, AppSettingKey key, T defaultValue, Function<String, T> mapper) {
        if (!hasAccess(permissions, key)) {
            return null;
        }

        Optional<String> value = settingsMap.getOrDefault(key, Optional.empty());

        try {
            return value.map(mapper).orElse(defaultValue);
        } catch (Exception e) {
            log.error("Failed to parse value for setting key '{}'. Using default value. Error: {}", key, e.getMessage());
            return defaultValue;
        }
    }

    public PublicAppSetting getPublicSettings() {
        Map<AppSettingKey, Optional<String>> settingsMap = getSettingsMap();
        PublicAppSetting.PublicAppSettingBuilder builder = PublicAppSetting.builder();

        builder.remoteAuthEnabled(appProperties.getRemoteAuth().isEnabled());
        OidcProviderDetails details = getJsonSetting(null, settingsMap, AppSettingKey.OIDC_PROVIDER_DETAILS, OidcProviderDetails.class, null);

        boolean oidcEnabled = getBooleanValue(null, settingsMap, AppSettingKey.OIDC_ENABLED, false);
        boolean oidcForceOnlyMode = getBooleanValue(null, settingsMap, AppSettingKey.OIDC_FORCE_ONLY_MODE, false);

        if (isOIDCForceDisabled()) {
            oidcEnabled = false;
            oidcForceOnlyMode = false;
        }
        builder.oidcEnabled(oidcEnabled);
        builder.oidcForceOnlyMode(oidcForceOnlyMode);

        builder.oidcProviderDetails(details);

        return builder.build();
    }

    public AppSettings getAppSettings() {
        var permissions = new BookLoreUser.UserPermissions();
        permissions.setAdmin(true);
        return getAppSettings(permissions);
    }

    public AppSettings getAppSettings(BookLoreUser user) {
        return getAppSettings(user.getPermissions());
    }

    public AppSettings getAppSettings(BookLoreUser.UserPermissions permissions) {
        Map<AppSettingKey, Optional<String>> settingsMap = getSettingsMap();

        AppSettings.AppSettingsBuilder builder = AppSettings.builder();
        builder.remoteAuthEnabled(appProperties.getRemoteAuth().isEnabled());

        builder.defaultMetadataRefreshOptions(getJsonSetting(permissions, settingsMap, AppSettingKey.QUICK_BOOK_MATCH, MetadataRefreshOptions.class, getDefaultMetadataRefreshOptions()));
        builder.libraryMetadataRefreshOptions(getJsonListSetting(permissions, settingsMap, AppSettingKey.LIBRARY_METADATA_REFRESH_OPTIONS, MetadataRefreshOptions.class, List.of()));
        builder.oidcProviderDetails(getJsonSetting(permissions, settingsMap, AppSettingKey.OIDC_PROVIDER_DETAILS, OidcProviderDetails.class, null));
        builder.oidcRedirectUris(getJsonListSetting(permissions, settingsMap, AppSettingKey.OIDC_REDIRECT_URIS, String.class, List.of(DEFAULT_MOBILE_REDIRECT_URI)));
        builder.oidcAutoProvisionDetails(getJsonSetting(permissions, settingsMap, AppSettingKey.OIDC_AUTO_PROVISION_DETAILS, OidcAutoProvisionDetails.class, new OidcAutoProvisionDetails()));
        builder.metadataProviderSettings(getJsonSetting(permissions, settingsMap, AppSettingKey.METADATA_PROVIDER_SETTINGS, MetadataProviderSettings.class, getDefaultMetadataProviderSettings()));
        builder.metadataMatchWeights(getJsonSetting(permissions, settingsMap, AppSettingKey.METADATA_MATCH_WEIGHTS, MetadataMatchWeights.class, getDefaultMetadataMatchWeights()));
        builder.metadataPersistenceSettings(getJsonSetting(permissions, settingsMap, AppSettingKey.METADATA_PERSISTENCE_SETTINGS, MetadataPersistenceSettings.class, getDefaultMetadataPersistenceSettings()));
        builder.metadataPublicReviewsSettings(getJsonSetting(permissions, settingsMap, AppSettingKey.METADATA_PUBLIC_REVIEWS_SETTINGS, MetadataPublicReviewsSettings.class, getDefaultMetadataPublicReviewsSettings()));
        builder.koboSettings(getJsonSetting(permissions, settingsMap, AppSettingKey.KOBO_SETTINGS, KoboSettings.class, getDefaultKoboSettings()));
        builder.coverCroppingSettings(getJsonSetting(permissions, settingsMap, AppSettingKey.COVER_CROPPING_SETTINGS, CoverCroppingSettings.class, getDefaultCoverCroppingSettings()));
        builder.metadataProviderSpecificFields(getJsonSetting(permissions, settingsMap, AppSettingKey.METADATA_PROVIDER_SPECIFIC_FIELDS, MetadataProviderSpecificFields.class, getDefaultMetadataProviderSpecificFields()));

        builder.autoBookSearch(getBooleanValue(permissions, settingsMap, AppSettingKey.AUTO_BOOK_SEARCH, false));
        builder.uploadPattern(getValue(permissions, settingsMap, AppSettingKey.UPLOAD_FILE_PATTERN, "{authors}/<{series}/><{seriesIndex} - >{title}/{title}< - {authors}>< ({year})>"));
        builder.similarBookRecommendation(getBooleanValue(permissions, settingsMap, AppSettingKey.SIMILAR_BOOK_RECOMMENDATION, true));
        builder.opdsServerEnabled(getBooleanValue(permissions, settingsMap, AppSettingKey.OPDS_SERVER_ENABLED, false));
        builder.komgaApiEnabled(getBooleanValue(permissions, settingsMap, AppSettingKey.KOMGA_API_ENABLED, false));
        builder.komgaGroupUnknown(getBooleanValue(permissions, settingsMap, AppSettingKey.KOMGA_GROUP_UNKNOWN, true));
        builder.pdfCacheSizeInMb(getValue(permissions, settingsMap, AppSettingKey.PDF_CACHE_SIZE_IN_MB, 5120, Integer::parseInt));
        builder.maxFileUploadSizeInMb(getValue(permissions, settingsMap, AppSettingKey.MAX_FILE_UPLOAD_SIZE_IN_MB, 100, Integer::parseInt));
        builder.metadataDownloadOnBookdrop(getBooleanValue(permissions, settingsMap, AppSettingKey.METADATA_DOWNLOAD_ON_BOOKDROP, true));
        builder.oidcProviderClientSecret(getValue(permissions, settingsMap, AppSettingKey.OIDC_PROVIDER_CLIENT_SECRET, ""));
        builder.oidcSessionDurationHours(getValue(permissions, settingsMap, AppSettingKey.OIDC_SESSION_DURATION_HOURS, null, Integer::parseInt));

        boolean oidcEnabled = getBooleanValue(permissions, settingsMap, AppSettingKey.OIDC_ENABLED, false);
        boolean oidcForceOnlyMode = getBooleanValue(permissions, settingsMap, AppSettingKey.OIDC_FORCE_ONLY_MODE, false);

        if (isOIDCForceDisabled()) {
            oidcEnabled = false;
            oidcForceOnlyMode = false;
        }

        builder.oidcEnabled(oidcEnabled);
        builder.oidcForceOnlyMode(oidcForceOnlyMode);

        builder.oidcGroupSyncMode(getValue(permissions, settingsMap, AppSettingKey.OIDC_GROUP_SYNC_MODE, "DISABLED"));

        builder.diskType(appProperties.getDiskType());

        return builder.build();
    }

    public String getSettingValue(String key) {
        var setting = appSettingsRepository.findByName(key);
        return setting != null ? setting.getVal() : null;
    }

    private MetadataProviderSettings getDefaultMetadataProviderSettings() {
        MetadataProviderSettings defaultMetadataProviderSettings = new MetadataProviderSettings();

        MetadataProviderSettings.OpenLibrary defaultOpenLibrary = new MetadataProviderSettings.OpenLibrary();
        defaultOpenLibrary.setEnabled(false);

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

        MetadataProviderSettings.AppleBooks defaultAppleBooks = new MetadataProviderSettings.AppleBooks();
        defaultAppleBooks.setEnabled(false);
        defaultAppleBooks.setCountry("US");

        defaultMetadataProviderSettings.setOpenLibrary(defaultOpenLibrary);
        defaultMetadataProviderSettings.setAmazon(defaultAmazon);
        defaultMetadataProviderSettings.setGoogle(defaultGoogle);
        defaultMetadataProviderSettings.setGoodReads(defaultGoodreads);
        defaultMetadataProviderSettings.setHardcover(defaultHardcover);
        defaultMetadataProviderSettings.setComicvine(defaultComicvine);
        defaultMetadataProviderSettings.setRanobedb(defaultRanobedb);
        defaultMetadataProviderSettings.setDouban(defaultDouban);
        defaultMetadataProviderSettings.setAppleBooks(defaultAppleBooks);

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
                .openlibraryId(nullProvider)
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
                .applebooksId(nullProvider)
                .applebooksRating(nullProvider)
                .applebooksReviewCount(nullProvider)
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
                .openlibraryId(true)
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
                .applebooksId(true)
                .applebooksRating(true)
                .applebooksReviewCount(true)
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

    private MetadataMatchWeights getDefaultMetadataMatchWeights() {
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
                .applebooksRating(0)
                .applebooksReviewCount(0)
                .coverImage(5)
                .build();
    }

    private MetadataPersistenceSettings getDefaultMetadataPersistenceSettings() {
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

    private MetadataPublicReviewsSettings getDefaultMetadataPublicReviewsSettings() {
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

    private KoboSettings getDefaultKoboSettings() {
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

    private CoverCroppingSettings getDefaultCoverCroppingSettings() {
        return CoverCroppingSettings.builder()
                .verticalCroppingEnabled(false)
                .horizontalCroppingEnabled(false)
                .aspectRatioThreshold(2.5)
                .smartCroppingEnabled(false)
                .build();
    }

    private MetadataProviderSpecificFields getDefaultMetadataProviderSpecificFields() {
        MetadataProviderSpecificFields fields = new MetadataProviderSpecificFields();
        fields.setOpenlibraryId(true);
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
        fields.setApplebooksId(true);
        fields.setApplebooksRating(true);
        fields.setApplebooksReviewCount(true);
        return fields;
    }
}
