package com.adityachandel.booklore.service;

import com.adityachandel.booklore.model.dto.BookloreTelemetry;
import com.adityachandel.booklore.model.dto.settings.AppSettings;
import com.adityachandel.booklore.model.dto.settings.MetadataProviderSettings;
import com.adityachandel.booklore.model.dto.settings.MetadataPublicReviewsSettings;
import com.adityachandel.booklore.model.entity.AppSettingEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.enums.ProvisioningMethod;
import com.adityachandel.booklore.model.enums.IconType;
import com.adityachandel.booklore.model.enums.LibraryScanMode;
import com.adityachandel.booklore.model.enums.MetadataProvider;
import com.adityachandel.booklore.repository.*;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class TelemetryService {

    private static class EnumMappings {
        static final Map<BookFileType, Integer> BOOK_FILE_TYPE = Map.of(
                BookFileType.PDF, 1,
                BookFileType.EPUB, 2,
                BookFileType.CBX, 3,
                BookFileType.FB2, 4
        );
        static final Map<IconType, Integer> ICON_TYPE = Map.of(
                IconType.PRIME_NG, 1,
                IconType.CUSTOM_SVG, 2
        );
        static final Map<LibraryScanMode, Integer> LIBRARY_SCAN_MODE = Map.of(
                LibraryScanMode.FILE_AS_BOOK, 1,
                LibraryScanMode.FOLDER_AS_BOOK, 2
        );
        static final Map<MetadataProvider, Integer> METADATA_PROVIDER = Map.of(
                MetadataProvider.Amazon, 1,
                MetadataProvider.Google, 2,
                MetadataProvider.GoodReads, 3,
                MetadataProvider.Hardcover, 4,
                MetadataProvider.Comicvine, 5,
                MetadataProvider.Douban, 6
        );
        static final Map<MetadataProvider, Integer> REVIEW_PROVIDER = Map.of(
                MetadataProvider.Amazon, 1,
                MetadataProvider.Google, 2,
                MetadataProvider.GoodReads, 3,
                MetadataProvider.Hardcover, 4,
                MetadataProvider.Comicvine, 5,
                MetadataProvider.Douban, 6
        );
    }

    private static final String INSTALLATION_ID_KEY = "installation_id";
    private final AppSettingsRepository appSettingsRepository;
    private final VersionService versionService;
    private final LibraryRepository libraryRepository;
    private final BookRepository bookRepository;
    private final BookMarkRepository bookMarkRepository;
    private final BookNoteRepository bookNoteRepository;
    private final BookAdditionalFileRepository bookAdditionalFileRepository;
    private final AuthorRepository authorRepository;
    private final ShelfRepository shelfRepository;
    private final MagicShelfRepository magicShelfRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final MoodRepository moodRepository;
    private final UserRepository userRepository;
    private final EmailProviderV2Repository emailProviderV2Repository;
    private final EmailRecipientV2Repository emailRecipientV2Repository;
    private final AppSettingService appSettingService;
    private final KoboUserSettingsRepository koboUserSettingsRepository;
    private final KoreaderUserRepository koreaderUserRepository;
    private final OpdsUserV2Repository opdsUserV2Repository;

    public BookloreTelemetry collectTelemetry() {
        long totalUsers = userRepository.count();
        long localUsers = userRepository.countByProvisioningMethod(ProvisioningMethod.LOCAL);
        long oidcUsers = userRepository.countByProvisioningMethod(ProvisioningMethod.OIDC);

        AppSettings settings = appSettingService.getAppSettings();

        BookloreTelemetry.BookStatistics bookStatistics = BookloreTelemetry.BookStatistics.builder()
                .totalBooks(bookRepository.count())
                .bookCountByType(getBookFileTypeCounts())
                .build();

        List<BookloreTelemetry.LibraryStatistics> libraryStatisticsList = libraryRepository.findAll().stream()
                .map(this::mapLibraryStatistics)
                .collect(Collectors.toList());

        int[] enabledMetadataProviders = getEnabledMetadataProvidersAsInt(settings.getMetadataProviderSettings());
        int[] enabledReviewMetadataProviders = getEnabledReviewMetadataProvidersAsInt(settings.getMetadataPublicReviewsSettings());

        return BookloreTelemetry.builder()
                .installationId(getInstallationId())
                .appVersion(versionService.appVersion)
                .totalLibraries((int) libraryRepository.count())
                .totalBooks(bookRepository.count())
                .totalAdditionalBookFiles(bookAdditionalFileRepository.count())
                .totalAuthors(authorRepository.count())
                .totalBookmarks(bookMarkRepository.count())
                .totalBookNotes(bookNoteRepository.count())
                .totalShelves((int) shelfRepository.count())
                .totalMagicShelves((int) magicShelfRepository.count())
                .totalCategories((int) categoryRepository.count())
                .totalTags((int) tagRepository.count())
                .totalMoods((int) moodRepository.count())
                .totalKoreaderUsers((int) koreaderUserRepository.count())
                .userStatistics(BookloreTelemetry.UserStatistics.builder()
                        .totalUsers((int) totalUsers)
                        .totalLocalUsers((int) localUsers)
                        .totalOidcUsers((int) oidcUsers)
                        .oidcEnabled(oidcUsers > 0)
                        .build())
                .metadataStatistics(BookloreTelemetry.MetadataStatistics.builder()
                        .enabledMetadataProviders(enabledMetadataProviders)
                        .enabledReviewMetadataProviders(enabledReviewMetadataProviders)
                        .saveMetadataToFile(settings.getMetadataPersistenceSettings().isSaveToOriginalFile())
                        .moveFileViaPattern(settings.getMetadataPersistenceSettings().isMoveFilesToLibraryPattern())
                        .autoBookSearchEnabled(settings.isAutoBookSearch())
                        .similarBookRecommendationsEnabled(settings.isSimilarBookRecommendation())
                        .metadataDownloadOnBookdropEnabled(settings.isMetadataDownloadOnBookdrop())
                        .build())
                .opdsStatistics(BookloreTelemetry.OpdsStatistics.builder()
                        .opdsEnabled(settings.isOpdsServerEnabled())
                        .totalOpdsUsers((int) opdsUserV2Repository.count())
                        .build())
                .emailStatistics(BookloreTelemetry.EmailStatistics.builder()
                        .totalEmailProviders((int) emailProviderV2Repository.count())
                        .totalEmailRecipients((int) emailRecipientV2Repository.count())
                        .build())
                .koboStatistics(BookloreTelemetry.KoboStatistics.builder()
                        .convertToKepubEnabled(settings.getKoboSettings().isConvertToKepub())
                        .totalKoboUsers((int) koboUserSettingsRepository.count())
                        .totalHardcoverSyncEnabled((int) koboUserSettingsRepository.countByHardcoverSyncEnabledTrue())
                        .totalAutoAddToShelf((int) koboUserSettingsRepository.countByAutoAddToShelfTrue())
                        .build())
                .bookStatistics(bookStatistics)
                .libraryStatisticsList(libraryStatisticsList)
                .build();
    }

    private Map<Integer, Long> getBookFileTypeCounts() {
        Map<Integer, Long> countByType = new HashMap<>();
        for (BookFileType type : BookFileType.values()) {
            Integer mapped = EnumMappings.BOOK_FILE_TYPE.get(type);
            if (mapped != null) {
                countByType.put(mapped, bookRepository.countByBookType(type));
            }
        }
        return countByType;
    }

    private BookloreTelemetry.LibraryStatistics mapLibraryStatistics(LibraryEntity lib) {
        return BookloreTelemetry.LibraryStatistics.builder()
                .libraryName(lib.getName())
                .totalLibraryPaths(lib.getLibraryPaths() != null ? lib.getLibraryPaths().size() : 0)
                .bookCount(bookRepository.countByLibraryId(lib.getId()))
                .watchEnabled(lib.isWatch())
                .iconType(lib.getIconType() != null ? EnumMappings.ICON_TYPE.getOrDefault(lib.getIconType(), -1) : -1)
                .scanMode(lib.getScanMode() != null ? EnumMappings.LIBRARY_SCAN_MODE.getOrDefault(lib.getScanMode(), -1) : -1)
                .build();
    }

    private int[] getEnabledMetadataProvidersAsInt(MetadataProviderSettings providers) {
        List<Integer> enabled = new ArrayList<>();
        if (providers.getAmazon() != null && providers.getAmazon().isEnabled())
            enabled.add(EnumMappings.METADATA_PROVIDER.get(MetadataProvider.Amazon));
        if (providers.getGoogle() != null && providers.getGoogle().isEnabled())
            enabled.add(EnumMappings.METADATA_PROVIDER.get(MetadataProvider.Google));
        if (providers.getGoodReads() != null && providers.getGoodReads().isEnabled())
            enabled.add(EnumMappings.METADATA_PROVIDER.get(MetadataProvider.GoodReads));
        if (providers.getHardcover() != null && providers.getHardcover().isEnabled())
            enabled.add(EnumMappings.METADATA_PROVIDER.get(MetadataProvider.Hardcover));
        if (providers.getComicvine() != null && providers.getComicvine().isEnabled())
            enabled.add(EnumMappings.METADATA_PROVIDER.get(MetadataProvider.Comicvine));
        if (providers.getDouban() != null && providers.getDouban().isEnabled())
            enabled.add(EnumMappings.METADATA_PROVIDER.get(MetadataProvider.Douban));
        return enabled.stream().mapToInt(i -> i).toArray();
    }

    private int[] getEnabledReviewMetadataProvidersAsInt(MetadataPublicReviewsSettings reviewSettings) {
        List<Integer> enabled = new ArrayList<>();
        if (reviewSettings.getProviders() != null) {
            reviewSettings.getProviders().stream()
                    .filter(MetadataPublicReviewsSettings.ReviewProviderConfig::isEnabled)
                    .forEach(cfg -> {
                        try {
                            MetadataProvider provider = MetadataProvider.valueOf(cfg.getProvider().name());
                            Integer mapped = EnumMappings.REVIEW_PROVIDER.get(provider);
                            if (mapped != null) enabled.add(mapped);
                        } catch (IllegalArgumentException ignored) {
                        }
                    });
        }
        return enabled.stream().mapToInt(i -> i).toArray();
    }

    private String getInstallationId() {
        AppSettingEntity setting = appSettingsRepository.findByName(INSTALLATION_ID_KEY);
        return setting != null ? setting.getVal() : "unknown";
    }
}
