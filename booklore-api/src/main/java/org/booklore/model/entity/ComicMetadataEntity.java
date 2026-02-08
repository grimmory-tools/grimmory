package org.booklore.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
    @Fetch(FetchMode.SUBSELECT)
    @Builder.Default
    private Set<ComicCharacterEntity> characters = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "comic_metadata_team_mapping",
            joinColumns = @JoinColumn(name = "book_id"),
            inverseJoinColumns = @JoinColumn(name = "team_id"))
    @Fetch(FetchMode.SUBSELECT)
    @Builder.Default
    private Set<ComicTeamEntity> teams = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "comic_metadata_location_mapping",
            joinColumns = @JoinColumn(name = "book_id"),
            inverseJoinColumns = @JoinColumn(name = "location_id"))
    @Fetch(FetchMode.SUBSELECT)
    @Builder.Default
    private Set<ComicLocationEntity> locations = new HashSet<>();

    @OneToMany(mappedBy = "comicMetadata", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Fetch(FetchMode.SUBSELECT)
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

    @Column(name = "creators_locked")
    @Builder.Default
    private Boolean creatorsLocked = Boolean.FALSE;

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
        this.creatorsLocked = lock;
        this.charactersLocked = lock;
        this.teamsLocked = lock;
        this.locationsLocked = lock;
    }

    public boolean areAllFieldsLocked() {
        return Boolean.TRUE.equals(this.issueNumberLocked)
                && Boolean.TRUE.equals(this.volumeNameLocked)
                && Boolean.TRUE.equals(this.volumeNumberLocked)
                && Boolean.TRUE.equals(this.storyArcLocked)
                && Boolean.TRUE.equals(this.creatorsLocked)
                && Boolean.TRUE.equals(this.charactersLocked)
                && Boolean.TRUE.equals(this.teamsLocked)
                && Boolean.TRUE.equals(this.locationsLocked);
    }
}
