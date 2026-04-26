package org.grimmory.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.DynamicUpdate;

import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamicUpdate
@Table(name = "comic_metadata")
public class ComicMetadataEntity {

    @Id
    @Column(name = "book_id")
    private Long bookId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", insertable = false, updatable = false)
    @JsonIgnore
    private BookMetadataEntity bookMetadata;

    @Column(name = "issue_number")
    private String issueNumber;

    @Column(name = "volume_name")
    private String volumeName;

    @Column(name = "volume_number")
    private Integer volumeNumber;

    @Column(name = "story_arc")
    private String storyArc;

    @Column(name = "story_arc_number")
    private Integer storyArcNumber;

    @Column(name = "alternate_series")
    private String alternateSeries;

    @Column(name = "alternate_issue")
    private String alternateIssue;

    @Column(name = "imprint")
    private String imprint;

    @Column(name = "format", length = 50)
    private String format;

    @Column(name = "black_and_white")
    @Builder.Default
    private Boolean blackAndWhite = Boolean.FALSE;

    @Column(name = "manga")
    @Builder.Default
    private Boolean manga = Boolean.FALSE;

    @Column(name = "reading_direction", length = 10)
    @Builder.Default
    private String readingDirection = "ltr";

    @Column(name = "web_link")
    private String webLink;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // Many-to-many relationships
    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "comic_metadata_character_mapping",
            joinColumns = @JoinColumn(name = "book_id"),
            inverseJoinColumns = @JoinColumn(name = "character_id"))
    @BatchSize(size = 20)
    @Builder.Default
    private Set<ComicCharacterEntity> characters = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "comic_metadata_team_mapping",
            joinColumns = @JoinColumn(name = "book_id"),
            inverseJoinColumns = @JoinColumn(name = "team_id"))
    @BatchSize(size = 20)
    @Builder.Default
    private Set<ComicTeamEntity> teams = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "comic_metadata_location_mapping",
            joinColumns = @JoinColumn(name = "book_id"),
            inverseJoinColumns = @JoinColumn(name = "location_id"))
    @BatchSize(size = 20)
    @Builder.Default
    private Set<ComicLocationEntity> locations = new HashSet<>();

    @OneToMany(mappedBy = "comicMetadata", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    @BatchSize(size = 20)
    @Builder.Default
    private Set<ComicCreatorMappingEntity> creatorMappings = new HashSet<>();

    // Locked fields
    @Column(name = "issue_number_locked")
    @Builder.Default
    private Boolean issueNumberLocked = Boolean.FALSE;

    @Column(name = "volume_name_locked")
    @Builder.Default
    private Boolean volumeNameLocked = Boolean.FALSE;

    @Column(name = "volume_number_locked")
    @Builder.Default
    private Boolean volumeNumberLocked = Boolean.FALSE;

    @Column(name = "story_arc_locked")
    @Builder.Default
    private Boolean storyArcLocked = Boolean.FALSE;

    @Column(name = "story_arc_number_locked")
    @Builder.Default
    private Boolean storyArcNumberLocked = Boolean.FALSE;

    @Column(name = "alternate_series_locked")
    @Builder.Default
    private Boolean alternateSeriesLocked = Boolean.FALSE;

    @Column(name = "alternate_issue_locked")
    @Builder.Default
    private Boolean alternateIssueLocked = Boolean.FALSE;

    @Column(name = "imprint_locked")
    @Builder.Default
    private Boolean imprintLocked = Boolean.FALSE;

    @Column(name = "format_locked")
    @Builder.Default
    private Boolean formatLocked = Boolean.FALSE;

    @Column(name = "black_and_white_locked")
    @Builder.Default
    private Boolean blackAndWhiteLocked = Boolean.FALSE;

    @Column(name = "manga_locked")
    @Builder.Default
    private Boolean mangaLocked = Boolean.FALSE;

    @Column(name = "reading_direction_locked")
    @Builder.Default
    private Boolean readingDirectionLocked = Boolean.FALSE;

    @Column(name = "web_link_locked")
    @Builder.Default
    private Boolean webLinkLocked = Boolean.FALSE;

    @Column(name = "notes_locked")
    @Builder.Default
    private Boolean notesLocked = Boolean.FALSE;

    @Column(name = "creators_locked")
    @Builder.Default
    private Boolean creatorsLocked = Boolean.FALSE;

    @Column(name = "pencillers_locked")
    @Builder.Default
    private Boolean pencillersLocked = Boolean.FALSE;

    @Column(name = "inkers_locked")
    @Builder.Default
    private Boolean inkersLocked = Boolean.FALSE;

    @Column(name = "colorists_locked")
    @Builder.Default
    private Boolean coloristsLocked = Boolean.FALSE;

    @Column(name = "letterers_locked")
    @Builder.Default
    private Boolean letterersLocked = Boolean.FALSE;

    @Column(name = "cover_artists_locked")
    @Builder.Default
    private Boolean coverArtistsLocked = Boolean.FALSE;

    @Column(name = "editors_locked")
    @Builder.Default
    private Boolean editorsLocked = Boolean.FALSE;

    @Column(name = "characters_locked")
    @Builder.Default
    private Boolean charactersLocked = Boolean.FALSE;

    @Column(name = "teams_locked")
    @Builder.Default
    private Boolean teamsLocked = Boolean.FALSE;

    @Column(name = "locations_locked")
    @Builder.Default
    private Boolean locationsLocked = Boolean.FALSE;

    public void applyLockToAllFields(boolean lock) {
        this.issueNumberLocked = lock;
        this.volumeNameLocked = lock;
        this.volumeNumberLocked = lock;
        this.storyArcLocked = lock;
        this.storyArcNumberLocked = lock;
        this.alternateSeriesLocked = lock;
        this.alternateIssueLocked = lock;
        this.imprintLocked = lock;
        this.formatLocked = lock;
        this.blackAndWhiteLocked = lock;
        this.mangaLocked = lock;
        this.readingDirectionLocked = lock;
        this.webLinkLocked = lock;
        this.notesLocked = lock;
        this.creatorsLocked = lock;
        this.pencillersLocked = lock;
        this.inkersLocked = lock;
        this.coloristsLocked = lock;
        this.letterersLocked = lock;
        this.coverArtistsLocked = lock;
        this.editorsLocked = lock;
        this.charactersLocked = lock;
        this.teamsLocked = lock;
        this.locationsLocked = lock;
    }

    public boolean areAllFieldsLocked() {
        return Boolean.TRUE.equals(this.issueNumberLocked)
                && Boolean.TRUE.equals(this.volumeNameLocked)
                && Boolean.TRUE.equals(this.volumeNumberLocked)
                && Boolean.TRUE.equals(this.storyArcLocked)
                && Boolean.TRUE.equals(this.storyArcNumberLocked)
                && Boolean.TRUE.equals(this.alternateSeriesLocked)
                && Boolean.TRUE.equals(this.alternateIssueLocked)
                && Boolean.TRUE.equals(this.imprintLocked)
                && Boolean.TRUE.equals(this.formatLocked)
                && Boolean.TRUE.equals(this.blackAndWhiteLocked)
                && Boolean.TRUE.equals(this.mangaLocked)
                && Boolean.TRUE.equals(this.readingDirectionLocked)
                && Boolean.TRUE.equals(this.webLinkLocked)
                && Boolean.TRUE.equals(this.notesLocked)
                && Boolean.TRUE.equals(this.creatorsLocked)
                && Boolean.TRUE.equals(this.pencillersLocked)
                && Boolean.TRUE.equals(this.inkersLocked)
                && Boolean.TRUE.equals(this.coloristsLocked)
                && Boolean.TRUE.equals(this.letterersLocked)
                && Boolean.TRUE.equals(this.coverArtistsLocked)
                && Boolean.TRUE.equals(this.editorsLocked)
                && Boolean.TRUE.equals(this.charactersLocked)
                && Boolean.TRUE.equals(this.teamsLocked)
                && Boolean.TRUE.equals(this.locationsLocked);
    }
}
