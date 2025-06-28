package com.adityachandel.booklore.model.dto;

import com.adityachandel.booklore.model.enums.MetadataProvider;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookMetadata {
    private Long bookId;
    private String title;
    private String subtitle;
    private String publisher;
    private LocalDate publishedDate;
    private String description;
    private String seriesName;
    private Float seriesNumber;
    private Integer seriesTotal;
    private String isbn13;
    private String isbn10;
    private Integer pageCount;
    private String language;
    private Double rating;
    private String asin;
    private Double amazonRating;
    private Integer amazonReviewCount;
    private String goodreadsId;
    private Double goodreadsRating;
    private Integer goodreadsReviewCount;
    private String hardcoverId;
    private Double hardcoverRating;
    private Integer hardcoverReviewCount;
    private Double personalRating;
    private String googleId;
    private Instant coverUpdatedOn;
    private Set<String> authors;
    private Set<String> categories;
    private MetadataProvider provider;
    private String thumbnailUrl;

    private Boolean titleLocked;
    private Boolean subtitleLocked;
    private Boolean publisherLocked;
    private Boolean publishedDateLocked;
    private Boolean descriptionLocked;
    private Boolean seriesNameLocked;
    private Boolean seriesNumberLocked;
    private Boolean seriesTotalLocked;
    private Boolean isbn13Locked;
    private Boolean isbn10Locked;
    private Boolean asinLocked;
    private Boolean goodreadsIdLocked;
    private Boolean hardcoverIdLocked;
    private Boolean googleIdLocked;
    private Boolean pageCountLocked;
    private Boolean languageLocked;
    private Boolean personalRatingLocked;
    private Boolean amazonRatingLocked;
    private Boolean amazonReviewCountLocked;
    private Boolean goodreadsRatingLocked;
    private Boolean goodreadsReviewCountLocked;
    private Boolean hardcoverRatingLocked;
    private Boolean hardcoverReviewCountLocked;
    private Boolean coverLocked;
    private Boolean authorsLocked;
    private Boolean categoriesLocked;
}