package org.grimmory.app.dto;

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
    private String primaryFileType;
    private Instant coverUpdatedOn;
    private Instant audiobookCoverUpdatedOn;
    private Boolean isPhysical;

    // Metadata for filtering
    private LocalDate publishedDate;
    private Integer pageCount;
    private Integer ageRating;
    private String contentRating;
    private Float metadataMatchScore;
    private Long fileSizeKb;
}
