package org.booklore.model.dto.settings;

import org.booklore.model.dto.request.MetadataRefreshOptions;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AppSettings {
    private MetadataRefreshOptions defaultMetadataRefreshOptions;
    private List<MetadataRefreshOptions> libraryMetadataRefreshOptions;
    private boolean autoBookSearch;
    private boolean similarBookRecommendation;
    private boolean opdsServerEnabled;
    private boolean komgaApiEnabled;
    private boolean komgaGroupUnknown;
    private String uploadPattern;
    private Integer pdfCacheSizeInMb;
    private Integer maxFileUploadSizeInMb;
    private boolean remoteAuthEnabled;
    private boolean metadataDownloadOnBookdrop;
    private boolean oidcEnabled;
    private OidcProviderDetails oidcProviderDetails;
    private OidcAutoProvisionDetails oidcAutoProvisionDetails;
    private MetadataProviderSettings metadataProviderSettings;
    private MetadataMatchWeights metadataMatchWeights;
    private MetadataPersistenceSettings metadataPersistenceSettings;
    private MetadataPublicReviewsSettings metadataPublicReviewsSettings;
    private KoboSettings koboSettings;
    private CoverCroppingSettings coverCroppingSettings;
    private MetadataProviderSpecificFields metadataProviderSpecificFields;
    private Integer oidcSessionDurationHours;
    private String oidcGroupSyncMode;
    private boolean oidcForceOnlyMode;
    private String diskType;
}