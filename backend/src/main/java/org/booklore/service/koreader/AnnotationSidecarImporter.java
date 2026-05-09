package org.booklore.service.koreader;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.config.AppProperties;
import org.booklore.model.entity.AnnotationEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.repository.AnnotationRepository;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserRepository;
import org.booklore.util.koreader.EpubCfiService;
import org.booklore.util.koreader.SidecarAnnotationParser;
import org.booklore.util.koreader.SidecarAnnotationParser.ParsedHighlight;
import org.booklore.util.koreader.SidecarAnnotationParser.ParsedSidecar;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Walks all books in the library, finds per-user annotation sidecar files, and
 * upserts any annotations that are not already in the database.
 *
 * <p>Import is idempotent: annotations are skipped if the same CFI already exists
 * for the same user + book.  When a {@code booklore.id} is present, the lookup
 * is done by that ID first, which is faster and avoids CFI-conversion cost.
 *
 * <p>Only runs when local storage is configured; silently no-ops otherwise.
 */
@Slf4j
@AllArgsConstructor
@Service
public class AnnotationSidecarImporter {

    private static final String SIDECAR_GLOB = "metadata.epub.*.lua";

    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final AnnotationRepository annotationRepository;
    private final EpubCfiService epubCfiService;
    private final AppProperties appProperties;

    public record ImportResult(int booksScanned, int sidecarFiles, int imported, int skipped, int failed) {
        @Override
        public String toString() {
            return String.format(
                    "books=%d sidecarFiles=%d imported=%d skipped=%d failed=%d",
                    booksScanned, sidecarFiles, imported, skipped, failed);
        }
    }

    /**
     * Scans all books in the library for KOReader annotation sidecar files and imports
     * any annotations not already present in the database.
     *
     * @return a summary of how many books were scanned and how many annotations were
     *         imported, skipped (duplicates or unknown user), or failed
     */
    public ImportResult importAll() {
        if (!appProperties.isLocalStorage()) {
            log.info("Sidecar import skipped: local storage not configured");
            return new ImportResult(0, 0, 0, 0, 0);
        }

        int booksScanned = 0, sidecarFiles = 0, imported = 0, skipped = 0, failed = 0;

        List<BookEntity> books = bookRepository.findAll();
        for (BookEntity book : books) {
            booksScanned++;
            Path bookPath;
            try {
                bookPath = book.getFullFilePath();
            } catch (Exception e) {
                log.warn("Cannot resolve path for book {}, skipping", book.getId());
                continue;
            }
            if (bookPath == null) {
                continue;
            }

            Path sdrDir = Path.of(bookPath + ".sdr");
            if (!Files.isDirectory(sdrDir)) {
                continue;
            }

            List<Path> sidecarPaths = findSidecarFiles(sdrDir);
            for (Path sidecarPath : sidecarPaths) {
                sidecarFiles++;
                try {
                    String lua = Files.readString(sidecarPath);
                    ParsedSidecar parsed = SidecarAnnotationParser.parse(lua);
                    if (parsed == null) {
                        log.warn("Could not parse sidecar: {}", sidecarPath);
                        failed++;
                        continue;
                    }

                    BookLoreUserEntity user = resolveUser(parsed, sidecarPath);
                    if (user == null) {
                        log.warn("User not found for sidecar {}, skipping", sidecarPath);
                        skipped += parsed.highlights().size();
                        continue;
                    }

                    for (ParsedHighlight highlight : parsed.highlights()) {
                        try {
                            if (importHighlight(highlight, book, bookPath, user)) {
                                imported++;
                            } else {
                                skipped++;
                            }
                        } catch (Exception e) {
                            log.warn("Failed to import highlight from {}: {}", sidecarPath, e.getMessage());
                            failed++;
                        }
                    }
                } catch (IOException e) {
                    log.error("Failed to read sidecar {}: {}", sidecarPath, e.getMessage());
                    failed++;
                }
            }
        }

        log.info("Sidecar import complete: {}", new ImportResult(booksScanned, sidecarFiles, imported, skipped, failed));
        return new ImportResult(booksScanned, sidecarFiles, imported, skipped, failed);
    }

    private List<Path> findSidecarFiles(Path sdrDir) {
        try (Stream<Path> stream = Files.list(sdrDir)) {
            return stream
                    .filter(p -> p.getFileName().toString().startsWith("metadata.epub.")
                            && p.getFileName().toString().endsWith(".lua"))
                    .toList();
        } catch (IOException e) {
            log.warn("Cannot list sidecar directory {}: {}", sdrDir, e.getMessage());
            return List.of();
        }
    }

    private BookLoreUserEntity resolveUser(ParsedSidecar parsed, Path sidecarPath) {
        // Prefer username lookup (stable across DB recreations)
        if (parsed.username() != null) {
            Optional<BookLoreUserEntity> byUsername = userRepository.findByUsername(parsed.username());
            if (byUsername.isPresent()) {
                return byUsername.get();
            }
        }
        // Fall back to the user_id embedded in the booklore_meta block
        if (parsed.userId() != null) {
            Optional<BookLoreUserEntity> byId = userRepository.findById(parsed.userId());
            if (byId.isPresent()) {
                return byId.get();
            }
        }
        // Last resort: extract username from filename (metadata.epub.<username>.lua)
        String filename = sidecarPath.getFileName().toString();
        String extracted = extractUsernameFromFilename(filename);
        if (extracted != null) {
            return userRepository.findByUsername(extracted).orElse(null);
        }
        return null;
    }

    /** Returns true if imported, false if skipped. */
    private boolean importHighlight(ParsedHighlight h, BookEntity book, Path bookPath,
                                    BookLoreUserEntity user) throws Exception {
        // Fast path: stable Grimmory ID present — avoids CFI conversion cost
        if (h.bookloreId() != null &&
                annotationRepository.findByIdAndUserId(h.bookloreId(), user.getId()).isPresent()) {
            log.debug("Annotation {} already exists, skipping", h.bookloreId());
            return false;
        }

        String cfi = epubCfiService.convertXPointerRangeToCfi(bookPath, h.pos0(), h.pos1());

        // Slow path: check by CFI (covers sidecars without a booklore block)
        if (annotationRepository.existsByCfiAndBookIdAndUserId(cfi, book.getId(), user.getId())) {
            log.debug("Annotation already exists (by CFI), skipping: {}", cfi);
            return false;
        }

        // Prefer booklore.style (lossless); fall back to reverse-mapping drawer; default to "highlight"
        String style = h.style() != null ? h.style() : drawerToStyle(h.drawer());

        AnnotationEntity annotation = AnnotationEntity.builder()
                .cfi(cfi)
                .text(h.text() != null ? h.text() : "")
                .color(h.color() != null ? h.color() : "#FFFF00")
                .style(style)
                .note(h.note())
                .chapterTitle(h.chapter())
                .book(book)
                .user(user)
                .build();

        annotationRepository.save(annotation);
        log.debug("Imported annotation for book {} user {}: {}", book.getId(), user.getId(), cfi);
        return true;
    }

    /**
     * Extracts the username from a sidecar filename of the form
     * {@code metadata.epub.<username>.lua}.
     *
     * @param filename the sidecar filename (basename only, no directory path)
     * @return the extracted username, or {@code null} if the filename does not match
     *         the expected pattern or the username segment is empty
     */
    static String extractUsernameFromFilename(String filename) {
        // metadata.epub.<username>.lua
        if (!filename.startsWith("metadata.epub.") || !filename.endsWith(".lua")) {
            return null;
        }
        String middle = filename.substring("metadata.epub.".length(), filename.length() - ".lua".length());
        return middle.isEmpty() ? null : middle;
    }

    private static String drawerToStyle(String drawer) {
        if (drawer == null) return "highlight";
        return switch (drawer) {
            case "underscore" -> "underline";
            case "strikeout" -> "strikethrough";
            default -> "highlight";
        };
    }
}
