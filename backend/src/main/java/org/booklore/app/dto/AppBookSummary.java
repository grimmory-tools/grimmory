package org.booklore.app.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AppBookSummary {
    private Long id;
    private String title;
    private List<String> authors;
    private String thumbnailUrl;
    private String readStatus;
    private Integer personalRating;
    private String seriesName;
    private Float seriesNumber;
    private Long libraryId;
    private Instant addedOn;
    private Instant lastReadTime;
    private Float readProgress;
    private Long primaryFileId;
    private String primaryFileType;
    private String primaryFileName;
    private Instant coverUpdatedOn;
    private Instant audiobookCoverUpdatedOn;
    private Boolean isPhysical;

    // Metadata for filtering
    private String publisher;
    private List<String> categories;
    private List<String> tags;
    private List<String> moods;
    private String language;
    private String narrator;
    private String isbn13;
    private String isbn10;
    private LocalDate publishedDate;
    private Integer pageCount;
    private Integer ageRating;
    private String contentRating;
    private Float metadataMatchScore;
    private Long fileSizeKb;
    private Double amazonRating;
    private Integer amazonReviewCount;
    private Double goodreadsRating;
    private Integer goodreadsReviewCount;
    private Double hardcoverRating;
    private Integer hardcoverReviewCount;
    private Double ranobedbRating;
    private Double lubimyczytacRating;
    private Double audibleRating;
    private Integer audibleReviewCount;
    private Boolean allMetadataLocked;
}
