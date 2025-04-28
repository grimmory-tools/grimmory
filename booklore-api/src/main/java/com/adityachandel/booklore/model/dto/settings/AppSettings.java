package com.adityachandel.booklore.model.dto.settings;

import com.adityachandel.booklore.model.dto.request.MetadataRefreshOptions;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AppSettings {
    private String coverResolution;
    private MetadataRefreshOptions metadataRefreshOptions;
    private boolean autoBookSearch;
    private boolean similarBookRecommendation;
    private boolean opdsServerEnabled;
    private String uploadPattern;
}