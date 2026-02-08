package org.booklore.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.booklore.model.enums.ComicCreatorRole;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "comic_metadata_creator_mapping")
public class ComicCreatorMappingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private ComicMetadataEntity comicMetadata;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private ComicCreatorEntity creator;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private ComicCreatorRole role;
}
