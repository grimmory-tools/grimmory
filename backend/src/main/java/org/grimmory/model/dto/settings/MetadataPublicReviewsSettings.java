package org.booklore.model.dto.settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.booklore.model.enums.MetadataProvider;

import java.util.Set;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MetadataPublicReviewsSettings {

    private boolean downloadEnabled;
    private boolean autoDownloadEnabled;
    private Set<ReviewProviderConfig> providers;

    @Builder
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ReviewProviderConfig {
        private MetadataProvider provider;
        private boolean enabled;
        private int maxReviews;
    }
}
