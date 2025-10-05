package com.adityachandel.booklore.model.dto.request;

import com.adityachandel.booklore.model.enums.MetadataProvider;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetadataRefreshOptions {
    private Long libraryId;
    private boolean refreshCovers;
    private boolean mergeCategories;
    private Boolean reviewBeforeApply;
    @NotNull(message = "Field options cannot be null")
    private FieldOptions fieldOptions;
    @NotNull(message = "Skip fields cannot be null")
    private SkipFields skipFields;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FieldOptions {
        private FieldProvider title;
        private FieldProvider subtitle;
        private FieldProvider description;
        private FieldProvider authors;
        private FieldProvider publisher;
        private FieldProvider publishedDate;
        private FieldProvider seriesName;
        private FieldProvider seriesNumber;
        private FieldProvider seriesTotal;
        private FieldProvider isbn13;
        private FieldProvider isbn10;
        private FieldProvider language;
        private FieldProvider categories;
        private FieldProvider cover;
        private FieldProvider pageCount;
        private FieldProvider asin;
        private FieldProvider goodreadsId;
        private FieldProvider comicvineId;
        private FieldProvider hardcoverId;
        private FieldProvider googleId;
        private FieldProvider amazonRating;
        private FieldProvider amazonReviewCount;
        private FieldProvider goodreadsRating;
        private FieldProvider goodreadsReviewCount;
        private FieldProvider hardcoverRating;
        private FieldProvider hardcoverReviewCount;
        private FieldProvider moods;
        private FieldProvider tags;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FieldProvider {
        private MetadataProvider p1;
        private MetadataProvider p2;
        private MetadataProvider p3;
        private MetadataProvider p4;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SkipFields {
        private boolean title;
        private boolean subtitle;
        private boolean description;
        private boolean authors;
        private boolean publisher;
        private boolean publishedDate;
        private boolean seriesName;
        private boolean seriesNumber;
        private boolean seriesTotal;
        private boolean isbn13;
        private boolean isbn10;
        private boolean language;
        private boolean categories;
        private boolean cover;
        private boolean pageCount;
        private boolean asin;
        private boolean goodreadsId;
        private boolean comicvineId;
        private boolean hardcoverId;
        private boolean googleId;
        private boolean amazonRating;
        private boolean amazonReviewCount;
        private boolean goodreadsRating;
        private boolean goodreadsReviewCount;
        private boolean hardcoverRating;
        private boolean hardcoverReviewCount;
        private boolean moods;
        private boolean tags;
    }
}
