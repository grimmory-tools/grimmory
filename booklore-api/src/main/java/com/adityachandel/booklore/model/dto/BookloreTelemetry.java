package com.adityachandel.booklore.model.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Builder
@Setter
@Getter
public class BookloreTelemetry {
    private int telemetryVersion = 1;
    private String installationId;
    private String appVersion;

    private int totalLibraries;
    private long totalBooks;
    private long totalAdditionalBookFiles;
    private long totalAuthors;
    private long totalBookNotes;
    private long totalBookmarks;
    private int totalShelves;
    private int totalMagicShelves;
    private int totalCategories;
    private int totalTags;
    private int totalMoods;
    private int totalKoreaderUsers;

    private UserStatistics userStatistics;
    private MetadataStatistics metadataStatistics;
    private OpdsStatistics opdsStatistics;
    private KoboStatistics koboStatistics;
    private EmailStatistics emailStatistics;
    private BookStatistics bookStatistics;
    private List<LibraryStatistics> libraryStatisticsList;

    @Builder
    @Getter
    public static class UserStatistics {
        private int totalUsers;
        private int totalLocalUsers;
        private int totalOidcUsers;
        private boolean oidcEnabled;
    }

    @Builder
    @Getter
    public static class MetadataStatistics {
        private int[] enabledMetadataProviders;
        private int[] enabledReviewMetadataProviders;
        private boolean saveMetadataToFile;
        private boolean moveFileViaPattern;
        private boolean autoBookSearchEnabled;
        private boolean similarBookRecommendationsEnabled;
        private boolean metadataDownloadOnBookdropEnabled;
    }

    @Builder
    @Getter
    public static class OpdsStatistics {
        private boolean opdsEnabled;
        private int totalOpdsUsers;
    }

    @Builder
    @Getter
    public static class KoboStatistics {
        private int totalKoboUsers;
        private int totalHardcoverSyncEnabled;
        private int totalAutoAddToShelf;
        private boolean convertToKepubEnabled;
    }

    @Builder
    @Getter
    public static class EmailStatistics {
        private int totalEmailProviders;
        private int totalEmailRecipients;
    }

    @Builder
    @Getter
    public static class BookStatistics {
        private long totalBooks;
        private Map<Integer, Long> bookCountByType;
    }

    @Builder
    @Getter
    public static class LibraryStatistics {
        private String libraryName;
        private long bookCount;
        private int totalLibraryPaths;
        private boolean watchEnabled;
        private int iconType;
        private int scanMode;
    }
}
