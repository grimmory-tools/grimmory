package org.booklore.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.booklore.convertor.BookRecommendationIdsListConverter;
import org.booklore.model.dto.BookRecommendationLite;
import org.booklore.model.enums.BookFileType;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
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

    @Column(name = "metadata_match_score")
    private Float metadataMatchScore;

    @OneToOne(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private BookMetadataEntity metadata;

    @Column(name = "metadata_updated_at")
    private Instant metadataUpdatedAt;

    @Column(name = "metadata_for_write_updated_at")
    private Instant metadataForWriteUpdatedAt;

    @ManyToOne
    @JoinColumn(name = "library_id", nullable = false)
    private LibraryEntity library;

    @ManyToOne
    @JoinColumn(name = "library_path_id")
    private LibraryPathEntity libraryPath;

    @Column(name = "is_physical")
    @Builder.Default
    private Boolean isPhysical = Boolean.FALSE;

    @Column(name = "added_on")
    private Instant addedOn;

    @Column(name = "book_cover_hash", length = 20)
    private String bookCoverHash;

    @Column(name = "audiobook_cover_hash", length = 20)
    private String audiobookCoverHash;

    @Column(name = "deleted")
    @Builder.Default
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
    @OrderBy("id ASC")
    @Builder.Default
    private List<BookFileEntity> bookFiles = new ArrayList<>();

    @OneToMany(mappedBy = "book", fetch = FetchType.LAZY)
    private List<UserBookProgressEntity> userBookProgress;

    public Path getFullFilePath() {
        BookFileEntity primaryBookFile = getPrimaryBookFile();
        if (primaryBookFile == null || libraryPath == null || libraryPath.getPath() == null || primaryBookFile.getFileSubPath() == null || primaryBookFile.getFileName() == null) {
            return null;
        }

        return Paths.get(libraryPath.getPath(), primaryBookFile.getFileSubPath(), primaryBookFile.getFileName());
    }

    public BookFileEntity getPrimaryBookFile() {
        if (bookFiles == null) {
            bookFiles = new ArrayList<>();
        }
        if (bookFiles.isEmpty()) {
            return null;
        }
        if (library != null && library.getFormatPriority() != null && !library.getFormatPriority().isEmpty()) {
            for (BookFileType format : library.getFormatPriority()) {
                var match = bookFiles.stream()
                        .filter(bf -> bf.isBookFormat() && bf.getBookType() == format)
                        .findFirst();
                if (match.isPresent()) {
                    return match.get();
                }
            }
        }
        return bookFiles.getFirst();
    }

    public boolean hasFiles() {
        return bookFiles != null && !bookFiles.isEmpty();
    }
}
