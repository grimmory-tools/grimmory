package com.adityachandel.booklore.model.entity;

import com.adityachandel.booklore.model.enums.AdditionalFileType;
import jakarta.persistence.*;
import lombok.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "book_additional_file")
public class BookAdditionalFileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "book_id", nullable = false)
    private BookEntity book;

    @Column(name = "file_name", length = 1000, nullable = false)
    private String fileName;

    @Column(name = "file_sub_path", length = 512, nullable = false)
    private String fileSubPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "additional_file_type", nullable = false)
    private AdditionalFileType additionalFileType;

    @Column(name = "file_size_kb")
    private Long fileSizeKb;

    @Column(name = "initial_hash", length = 128)
    private String initialHash;

    @Column(name = "current_hash", length = 128)
    private String currentHash;

    @Column(name = "alt_format_current_hash", insertable = false, updatable = false)
    private String altFormatCurrentHash;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "added_on")
    private Instant addedOn;

    public Path getFullFilePath() {
        if (book == null || book.getLibraryPath() == null || book.getLibraryPath().getPath() == null
                || fileSubPath == null || fileName == null) {
            throw new IllegalStateException("Cannot construct file path: missing book, library path, file subpath, or file name");
        }

        return Paths.get(book.getLibraryPath().getPath(), fileSubPath, fileName);
    }
}
