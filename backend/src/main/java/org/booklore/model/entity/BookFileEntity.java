package org.booklore.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.booklore.convertor.AudioFileChapterListConverter;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.DocumentParseStatus;
import org.booklore.util.ArchiveUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "book_file")
public class BookFileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private BookEntity book;

    @Column(name = "file_name", length = 1000, nullable = false)
    private String fileName;

    @Column(name = "file_sub_path", length = 512, nullable = false)
    private String fileSubPath;

    @Column(name = "source_archive", length = 1000)
    private String sourceArchive;

    @Column(name = "source_archive_entry", length = 1000)
    private String sourceArchiveEntry;

    @Column(name = "is_book", nullable = false)
    private boolean isBookFormat;

    @Column(name = "is_folder_based", nullable = false)
    @Builder.Default
    private boolean folderBased = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "book_type", nullable = false)
    private BookFileType bookType;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_parse_status")
    private DocumentParseStatus documentParseStatus;

    @Column(name = "is_fixed_layout", nullable = false)
    @Builder.Default
    private boolean isFixedLayout = false;

    @Column(name = "archive_type")
    @Enumerated(EnumType.STRING)
    private ArchiveUtils.ArchiveType archiveType;

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

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(name = "bitrate")
    private Integer bitrate;

    @Column(name = "sample_rate")
    private Integer sampleRate;

    @Column(name = "channels")
    private Integer channels;

    @Column(name = "codec", length = 50)
    private String codec;

    @Column(name = "chapter_count")
    private Integer chapterCount;

    @Convert(converter = AudioFileChapterListConverter.class)
    @Column(name = "chapters_json", columnDefinition = "TEXT")
    private List<AudioFileChapter> chapters;

    public boolean isBook() {
        return isBookFormat;
    }

    public boolean isFixedLayout() {
        return isFixedLayout;
    }

    public Path getFullFilePath() {
        if (book == null || book.getLibraryPath() == null || book.getLibraryPath().getPath() == null
                || fileSubPath == null || fileName == null) {
            throw new IllegalStateException("Cannot construct file path: missing book, library path, file subpath, or file name");
        }

        return Paths.get(book.getLibraryPath().getPath(), fileSubPath, fileName);
    }

    public boolean isArchivedSource() {
        return sourceArchive != null && !sourceArchive.isBlank()
                && sourceArchiveEntry != null && !sourceArchiveEntry.isBlank();
    }

    /**
     * For folder-based audiobooks, the stored file name is the folder itself,
     * so the full path covers both the folder and the regular-file case.
     */
    public Path getAudiobookPath() {
        return getFullFilePath();
    }

    /**
     * For folder-based audiobooks, returns the first audio file in the folder (for metadata extraction).
     * For regular files, returns the file itself.
     */
    public Path getFirstAudioFile() {
        if (!folderBased) {
            return getFullFilePath();
        }
        Path folderPath = getFullFilePath();
        try (var files = Files.list(folderPath)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return name.endsWith(".mp3") || name.endsWith(".m4a") || name.endsWith(".m4b") || name.endsWith(".opus");
                    })
                    .sorted()
                    .findFirst()
                    .orElse(folderPath);
        } catch (IOException _) {
            return folderPath;
        }
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AudioFileChapter {
        private Integer index;
        private String title;
        private Long startTimeMs;
        private Long endTimeMs;
        private Long durationMs;
    }
}
