package com.adityachandel.booklore.model.dto;

import com.adityachandel.booklore.model.dto.settings.SidebarSortOption;
import com.adityachandel.booklore.model.enums.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
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
        private boolean canManipulateLibrary;
        private boolean canEmailBook;
        private boolean canDeleteBook;
    }

    @Data
    public static class UserSettings {
        public PerBookSetting perBookSetting;
        public PdfReaderSetting pdfReaderSetting;
        public NewPdfReaderSetting newPdfReaderSetting;
        public EpubReaderSetting epubReaderSetting;
        public CbxReaderSetting cbxReaderSetting;
        public SidebarSortOption sidebarLibrarySorting;
        public SidebarSortOption sidebarShelfSorting;
        public EntityViewPreferences entityViewPreferences;
        public String filterSortingMode;

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
            private String view;
            private Float coverSize;
            private Boolean seriesCollapsed;
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
            private String view;
            private Boolean seriesCollapse;
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
        }

        @Data
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        public static class NewPdfReaderSetting {
            private NewPdfPageSpread pageSpread;
            private NewPdfPageViewMode pageViewMode;
        }

        @Data
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        public static class PerBookSetting {
            private GlobalOrIndividual pdf;
            private GlobalOrIndividual epub;
            private GlobalOrIndividual cbx;

            public enum GlobalOrIndividual {
                Global, Individual
            }
        }
    }
}
