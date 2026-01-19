package com.adityachandel.booklore.model.dto.komga;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KomgaSeriesMetadataDto {
    private String status;
    private Boolean statusLock;
    
    private String title;
    private Boolean titleLock;
    
    private String titleSort;
    private Boolean titleSortLock;
    
    private String summary;
    private Boolean summaryLock;
    
    private String readingDirection;
    private Boolean readingDirectionLock;
    
    private String publisher;
    private Boolean publisherLock;
    
    private Integer ageRating;
    private Boolean ageRatingLock;
    
    private String language;
    private Boolean languageLock;
    
    @Builder.Default
    private List<String> genres = new ArrayList<>();
    private Boolean genresLock;
    
    @Builder.Default
    private List<String> tags = new ArrayList<>();
    private Boolean tagsLock;
    
    private Integer totalBookCount;
    private Boolean totalBookCountLock;
    
    @Builder.Default
    private List<KomgaAlternateTitleDto> alternateTitles = new ArrayList<>();
    private Boolean alternateTitlesLock;
    
    @Builder.Default
    private List<KomgaWebLinkDto> links = new ArrayList<>();
    private Boolean linksLock;
    
    @Builder.Default
    private List<String> sharingLabels = new ArrayList<>();
    private Boolean sharingLabelsLock;
    
    private Instant created;
    private Instant lastModified;
}
