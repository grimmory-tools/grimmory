package org.booklore.model.dto.kobo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
@Builder
public class KoboBookMetadata {
    private String crossRevisionId;
    private String revisionId;
    private Publisher publisher;
    private String publicationDate;

    @Builder.Default
    private String language = "en";

    private String isbn;
    @Builder.Default
    private String genre = "00000000-0000-0000-0000-000000000001";
    private String slug;
    private String coverImageId;

    @JsonProperty("IsSocialEnabled")
    @Builder.Default
    private boolean socialEnabled = true;

    private String workId;

    @Builder.Default
    private List<Object> externalIds = new ArrayList<>();

    @JsonProperty("IsPreOrder")
    @Builder.Default
    private boolean preOrder = false;

    @Builder.Default
    private List<ContributorRole> contributorRoles = new ArrayList<>();

    @JsonProperty("IsInternetArchive")
    @Builder.Default
    private boolean internetArchive = false;

    private String entitlementId;
    private String title;
    private String description;

    @Builder.Default
    private List<String> categories = List.of("00000000-0000-0000-0000-000000000001");

    @Builder.Default
    private List<DownloadUrl> downloadUrls = new ArrayList<>();

    @Builder.Default
    private List<String> contributors = new ArrayList<>();

    private Series series;

    @Builder.Default
    private CurrentDisplayPrice currentDisplayPrice = CurrentDisplayPrice.builder()
            .totalAmount(0)
            .currencyCode("USD")
            .build();

    @Builder.Default
    private CurrentLoveDisplayPrice currentLoveDisplayPrice = CurrentLoveDisplayPrice.builder()
            .totalAmount(0)
            .build();

    @JsonProperty("IsEligibleForKoboLove")
    @Builder.Default
    private boolean eligibleForKoboLove = false;

    @Builder.Default
    private Map<String, String> phoneticPronunciations = Map.of();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Publisher {
        private String name;
        private String imprint;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ContributorRole {
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DownloadUrl {
        @Builder.Default
        private String drmType = "None";
        private String format;
        private String url;
        private long size;
        @Builder.Default
        private String platform = "Generic";
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Series {
        private String id;
        private String name;
        private String number;
        private double numberFloat;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CurrentDisplayPrice {
        private double totalAmount;
        private String currencyCode;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CurrentLoveDisplayPrice {
        private double totalAmount;
    }
}