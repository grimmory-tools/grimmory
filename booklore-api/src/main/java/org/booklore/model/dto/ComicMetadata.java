package org.booklore.model.dto;

import lombok.*;

import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
    private Boolean creatorsLocked;
    private Boolean charactersLocked;
    private Boolean teamsLocked;
    private Boolean locationsLocked;
}
