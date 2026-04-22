package org.booklore.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ComicMetadata {
    private String issueNumber;
    private String volumeName;
    private Integer volumeNumber;
    private String storyArc;
    private Integer storyArcNumber;
    private String alternateSeries;
    private String alternateIssue;

    // Creators
    private Set<String> pencillers;
    private Set<String> inkers;
    private Set<String> colorists;
    private Set<String> letterers;
    private Set<String> coverArtists;
    private Set<String> editors;

    private String imprint;
    private String format;
    private Boolean blackAndWhite;
    private Boolean manga;
    private String readingDirection;

    // Characters, teams, locations
    private Set<String> characters;
    private Set<String> teams;
    private Set<String> locations;

    private String webLink;
    private String notes;

    // Locked fields
    private Boolean issueNumberLocked;
    private Boolean volumeNameLocked;
    private Boolean volumeNumberLocked;
    private Boolean storyArcLocked;
    private Boolean storyArcNumberLocked;
    private Boolean alternateSeriesLocked;
    private Boolean alternateIssueLocked;
    private Boolean imprintLocked;
    private Boolean formatLocked;
    private Boolean blackAndWhiteLocked;
    private Boolean mangaLocked;
    private Boolean readingDirectionLocked;
    private Boolean webLinkLocked;
    private Boolean notesLocked;
    private Boolean creatorsLocked;
    private Boolean pencillersLocked;
    private Boolean inkersLocked;
    private Boolean coloristsLocked;
    private Boolean letterersLocked;
    private Boolean coverArtistsLocked;
    private Boolean editorsLocked;
    private Boolean charactersLocked;
    private Boolean teamsLocked;
    private Boolean locationsLocked;
}
