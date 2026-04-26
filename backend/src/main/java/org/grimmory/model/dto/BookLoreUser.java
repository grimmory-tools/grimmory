package org.grimmory.model.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.grimmory.model.dto.settings.SidebarSortOption;
import org.grimmory.model.enums.*;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BookLoreUser {
    private Long id;
    private String username;
    private boolean isDefaultPassword;
    private String name;
    private String email;
    private ProvisioningMethod provisioningMethod;
    private List<Library> assignedLibraries;
    private UserPermissions permissions;
    private UserSettings userSettings;

    @Data
    public static class UserPermissions {
        private boolean isAdmin;
        private boolean canUpload;
        private boolean canDownload;
        private boolean canEditMetadata;
        private boolean canManageLibrary;
        private boolean canSyncKoReader;
        private boolean canSyncKobo;
        private boolean canEmailBook;
        private boolean canDeleteBook;
        private boolean canAccessOpds;
        private boolean canManageMetadataConfig;
        private boolean canAccessBookdrop;
        private boolean canAccessLibraryStats;
        private boolean canAccessUserStats;
        private boolean canAccessTaskManager;
        private boolean canManageGlobalPreferences;
        private boolean canManageIcons;
        private boolean canManageFonts;
        private boolean isDemoUser;
        private boolean canBulkAutoFetchMetadata;
        private boolean canBulkCustomFetchMetadata;
        private boolean canBulkEditMetadata;
        private boolean canBulkRegenerateCover;
        private boolean canMoveOrganizeFiles;
        private boolean canBulkLockUnlockMetadata;
        private boolean canBulkResetBookloreReadProgress;
        private boolean canBulkResetKoReaderReadProgress;
        private boolean canBulkResetBookReadStatus;
    }

    @Data
    public static class UserSettings {
        public PerBookSetting perBookSetting;
        public PdfReaderSetting pdfReaderSetting;
        public NewPdfReaderSetting newPdfReaderSetting;
        public EpubReaderSetting epubReaderSetting;
        public EbookReaderSetting ebookReaderSetting;
        public CbxReaderSetting cbxReaderSetting;
        public SidebarSortOption sidebarLibrarySorting;
        public SidebarSortOption sidebarShelfSorting;
        public SidebarSortOption sidebarMagicShelfSorting;
        public EntityViewPreferences entityViewPreferences;
        public List<TableColumnPreference> tableColumnPreference;
        public String filterMode;
        public String filterSortingMode;
        public String metadataCenterViewMode;
        public boolean koReaderEnabled;
        public boolean enableSeriesView;
        public boolean autoSaveMetadata;
        public List<String> visibleFilters;
        public List<String> visibleSortFields;
        public DashboardConfig dashboardConfig;

        @Data
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class TableColumnPreference {
            private String field;
            private Boolean visible;
            private Integer order;
        }

        @Data
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        public static class EntityViewPreferences {
            private GlobalPreferences global;
            private List<OverridePreference> overrides;
        }

        @Data
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        public static class GlobalPreferences {
            private String sortKey;
            private String sortDir;
            private List<SortCriterion> sortCriteria;
            private String view;
            private Float coverSize;
            @JsonAlias("seriesCollapse")
            private Boolean seriesCollapsed;
            private Boolean overlayBookType;
        }

        @Data
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        public static class OverridePreference {
            private String entityType;
            private Long entityId;
            private OverrideDetails preferences;
        }

        @Data
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        public static class OverrideDetails {
            private String sortKey;
            private String sortDir;
            private List<SortCriterion> sortCriteria;
            private String view;
            @JsonAlias("seriesCollapse")
            private Boolean seriesCollapsed;
            private Boolean overlayBookType;
            private Float coverSize;
        }

        @Data
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        public static class SortCriterion {
            private String field;
            private String direction;
        }

        @Data
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        public static class EpubReaderSetting {
            private String theme;
            private String font;
            private Integer fontSize;
            private Float letterSpacing;
            private Float lineHeight;
            private String flow;
            private String spread;
        }

        @Data
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        public static class EbookReaderSetting {
            private String fontFamily;
            private Integer fontSize;
            private Float gap;
            private Boolean hyphenate;
            private Boolean isDark;
            private Boolean justify;
            private Float lineHeight;
            private Integer maxBlockSize;
            private Integer maxColumnCount;
            private Integer maxInlineSize;
            private String theme;
            private String flow;
        }

        @Data
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        public static class PdfReaderSetting {
            private String pageSpread;
            private String pageZoom;
        }

        @Data
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        public static class CbxReaderSetting {
            private CbxPageSpread pageSpread;
            private CbxPageViewMode pageViewMode;
            private CbxPageFitMode fitMode;
            private CbxPageScrollMode scrollMode;
            private CbxBackgroundColor backgroundColor;
            /** Max width (percent of reader) for infinite / long-strip modes; null means 100. */
            private Integer stripMaxWidthPercent;
        }

        @Data
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        public static class NewPdfReaderSetting {
            private NewPdfPageSpread pageSpread;
            private NewPdfPageViewMode pageViewMode;
            private NewPdfBackgroundColor backgroundColor;
            private NewPdfPageFitMode fitMode;
            private NewPdfPageScrollMode scrollMode;
        }

        @Data
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        public static class PerBookSetting {
            private GlobalOrIndividual pdf;
            private GlobalOrIndividual epub;
            private GlobalOrIndividual cbx;
            private GlobalOrIndividual newPdf;

            public enum GlobalOrIndividual {
                Global, Individual
            }
        }

        @Data
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        public static class DashboardConfig {
            private List<ScrollerConfig> scrollers;
        }

        @Data
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        public static class ScrollerConfig {
            private String id;
            private String type;
            private String title;
            private boolean enabled;
            private int order;
            private Integer maxItems;
            private Long magicShelfId;
            private String sortField;
            private String sortDirection;
        }
    }
}
