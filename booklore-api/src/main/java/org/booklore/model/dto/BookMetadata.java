package org.booklore.model.dto;

import org.booklore.model.enums.MetadataProvider;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@Builder(toBuilder = true)
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
    private String narrator;
    private Boolean abridged;

    private AudiobookMetadata audiobookMetadata;
    private ComicMetadata comicMetadata;

    private String asin;
    private Double amazonRating;
    private Integer amazonReviewCount;
    private String goodreadsId;
    private String comicvineId;
    private Double goodreadsRating;
    private Integer goodreadsReviewCount;
    private String hardcoverId;
    private String hardcoverBookId;
    private Double hardcoverRating;
    private Integer hardcoverReviewCount;
    private String doubanId;
    private Double doubanRating;
    private Integer doubanReviewCount;
    private Double lubimyczytacRating;
    private String googleId;
    private String lubimyczytacId;
    private String ranobedbId;
    private Double ranobedbRating;
    private String audibleId;
    private Double audibleRating;
    private Integer audibleReviewCount;
    private String externalUrl;
    private Instant coverUpdatedOn;
    private Instant audiobookCoverUpdatedOn;
    private Set<String> authors;
    @Singular
    private Set<String> categories;
    private Set<String> moods;
    private Set<String> tags;
    private MetadataProvider provider;
    private String thumbnailUrl;
    private List<BookReview> bookReviews;
    private Double rating;

    private Boolean allMetadataLocked;

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
    private Boolean comicvineIdLocked;
    private Boolean hardcoverIdLocked;
    private Boolean hardcoverBookIdLocked;
    private Boolean doubanIdLocked;
    private Boolean googleIdLocked;
    private Boolean pageCountLocked;
    private Boolean languageLocked;
    private Boolean amazonRatingLocked;
    private Boolean amazonReviewCountLocked;
    private Boolean goodreadsRatingLocked;
    private Boolean goodreadsReviewCountLocked;
    private Boolean hardcoverRatingLocked;
    private Boolean hardcoverReviewCountLocked;
    private Boolean doubanRatingLocked;
    private Boolean doubanReviewCountLocked;
    private Boolean lubimyczytacIdLocked;
    private Boolean lubimyczytacRatingLocked;
    private Boolean ranobedbIdLocked;
    private Boolean ranobedbRatingLocked;
    private Boolean audibleIdLocked;
    private Boolean audibleRatingLocked;
    private Boolean audibleReviewCountLocked;
    private Boolean externalUrlLocked;
    private Boolean coverLocked;
    private Boolean audiobookCoverLocked;
    private Boolean authorsLocked;
    private Boolean categoriesLocked;
    private Boolean moodsLocked;
    private Boolean tagsLocked;
    private Boolean reviewsLocked;
    private Boolean narratorLocked;
    private Boolean abridgedLocked;

    private Integer ageRating;
    private String contentRating;
    private Boolean ageRatingLocked;
    private Boolean contentRatingLocked;
}
