package com.adityachandel.booklore.model.entity;

import com.adityachandel.booklore.convertor.BookRecommendationIdsListConverter;
import com.adityachandel.booklore.model.dto.BookRecommendationLite;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.enums.ReadStatus;
import jakarta.persistence.*;
import lombok.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "book")
public class BookEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", length = 1000)
    private String fileName;

    @Column(name = "file_sub_path")
    private String fileSubPath;

    @Column(name = "book_type")
    private BookFileType bookType;

    @Column(name = "file_size_kb")
    private Long fileSizeKb;

    @Column(name = "metadata_match_score")
    private Float metadataMatchScore;

    @OneToOne(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private BookMetadataEntity metadata;

    @ManyToOne
    @JoinColumn(name = "library_id", nullable = false)
    private LibraryEntity library;

    @ManyToOne
    @JoinColumn(name = "library_path_id", nullable = false)
    private LibraryPathEntity libraryPath;

    @Column(name = "added_on")
    private Instant addedOn;

    @Column(name = "initial_hash", length = 128, updatable = false)
    private String initialHash;

    @Column(name = "current_hash", length = 128)
    private String currentHash;

    @Column(name = "deleted")
    private Boolean deleted = Boolean.FALSE;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @ManyToMany
    @JoinTable(
            name = "book_shelf_mapping",
            joinColumns = @JoinColumn(name = "book_id"),
            inverseJoinColumns = @JoinColumn(name = "shelf_id")
    )
    private Set<ShelfEntity> shelves;

    @Convert(converter = BookRecommendationIdsListConverter.class)
    @Column(name = "similar_books_json", columnDefinition = "TEXT")
    private Set<BookRecommendationLite> similarBooksJson;

    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<BookAdditionalFileEntity> additionalFiles;

    public Path getFullFilePath() {
        if (libraryPath == null || libraryPath.getPath() == null || fileSubPath == null || fileName == null) {
            throw new IllegalStateException("Cannot construct file path: missing library path, file subpath, or file name");
        }

        return Paths.get(libraryPath.getPath(), fileSubPath, fileName);
    }
}
