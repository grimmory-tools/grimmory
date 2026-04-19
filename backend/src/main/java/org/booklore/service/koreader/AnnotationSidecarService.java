package org.booklore.service.koreader;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.config.AppProperties;
import org.booklore.model.entity.AnnotationEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.BookNoteV2Entity;
import org.booklore.repository.AnnotationRepository;
import org.booklore.repository.BookNoteV2Repository;
import org.booklore.util.koreader.EpubCfiService;
import org.grimmory.epub4j.cfi.CfiConverter;
import org.grimmory.epub4j.cfi.XPointerResult;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@AllArgsConstructor
@Service
public class AnnotationSidecarService {

    private static final DateTimeFormatter DATETIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    static final int SCHEMA_VERSION = 1;

    private final EpubCfiService epubCfiService;
    private final AppProperties appProperties;
    private final AnnotationRepository annotationRepository;
    private final BookNoteV2Repository bookNoteV2Repository;

    /**
     * Writes (or deletes) the per-user KOReader annotation sidecar for {@code book}.
     *
     * <p>Fetches the current annotation and note state directly from the database so callers
     * do not need to pass entity lists. This means callers can invoke this after any mutation
     * (save or delete) and the sidecar will reflect the post-mutation state, provided the call
     * occurs within the same transaction (JPA flush mode AUTO ensures pending writes are visible
     * to subsequent queries in the same session).
     *
     * <p>File path: {@code <bookFullPath>.sdr/metadata.epub.<username>.lua}
     *
     * <p>No-ops silently when local-storage is not configured or the book path is unavailable.
     * All exceptions are caught and logged so a sidecar failure never propagates to the caller
     * or rolls back a {@code @Transactional}.
     */
    public void writeSidecar(BookEntity book, BookLoreUserEntity user) {
        if (!appProperties.isLocalStorage()) {
            return;
        }

        Path bookPath;
        try {
            bookPath = book.getFullFilePath();
        } catch (Exception e) {
            log.warn("Cannot write annotation sidecar: could not resolve file path for book {}", book.getId());
            return;
        }

        if (bookPath == null) {
            return;
        }

        Path sidecarPath = resolveSidecarPath(bookPath, user.getUsername());

        try {
            List<AnnotationEntity> annotations = annotationRepository
                    .findByBookIdAndUserIdOrderByCreatedAtDesc(book.getId(), user.getId());
            List<BookNoteV2Entity> notes = bookNoteV2Repository
                    .findByBookIdAndUserIdOrderByCreatedAtDesc(book.getId(), user.getId());

            if (annotations.isEmpty() && notes.isEmpty()) {
                if (Files.exists(sidecarPath)) {
                    Files.delete(sidecarPath);
                    log.info("Deleted annotation sidecar (no annotations remain): {}", sidecarPath);
                }
                return;
            }

            Files.createDirectories(sidecarPath.getParent());
            String lua = buildLua(bookPath, annotations, notes, user.getUsername(), user.getId(), book.getId());
            writeAtomic(sidecarPath, lua);
            log.info("Wrote annotation sidecar ({} annotations, {} notes) to {}",
                    annotations.size(), notes.size(), sidecarPath);
        } catch (Exception e) {
            // Swallow all exceptions so a sidecar failure never propagates to the caller
            // or rolls back a surrounding @Transactional.
            log.error("Failed to write annotation sidecar for book {} user {}: {}",
                    book.getId(), user.getUsername(), e.getMessage());
        }
    }

    /**
     * Converts annotations to a KOReader Lua string without writing to disk.
     * Used by the KOReader sync API endpoint to return annotations over HTTP.
     */
    public String buildAnnotationsLua(Path bookPath, List<AnnotationEntity> annotations,
                                      String username, long userId, long bookId) {
        return buildLua(bookPath, annotations, List.of(), username, userId, bookId);
    }

    // Visible for testing
    static Path resolveSidecarPath(Path bookPath, String username) {
        Path sdrDir = Path.of(bookPath + ".sdr");
        return sdrDir.resolve("metadata.epub." + username + ".lua");
    }

    private record HighlightEntry(AnnotationEntity ann, XPointerResult xp,
                                   String pos0, String pos1, String page,
                                   String drawer, String datetime) {}

    private record NoteEntry(BookNoteV2Entity note, XPointerResult xp,
                              String pos0, String page, String datetime) {}

    private String buildLua(Path bookPath, List<AnnotationEntity> annotations,
                            List<BookNoteV2Entity> bookNotes,
                            String username, long userId, long bookId) {
        String exportedAt = LocalDateTime.now().format(DATETIME_FMT);

        // Convert annotation CFIs; skip failures
        List<HighlightEntry> highlightEntries = new ArrayList<>();
        for (AnnotationEntity ann : annotations) {
            try {
                XPointerResult xp = epubCfiService.convertCfiToXPointer(bookPath, ann.getCfi());
                String pos0 = xp.getPos0() != null ? xp.getPos0() : xp.getXpointer();
                String pos1 = xp.getPos1() != null ? xp.getPos1() : xp.getXpointer();
                String page = CfiConverter.normalizeProgressXPointer(pos0);
                String drawer = styleToDrawer(ann.getStyle());
                String datetime = ann.getCreatedAt() != null
                        ? ann.getCreatedAt().format(DATETIME_FMT)
                        : "1970-01-01 00:00:00";
                highlightEntries.add(new HighlightEntry(ann, xp, pos0, pos1, page, drawer, datetime));
            } catch (Exception e) {
                log.warn("Skipping annotation {} (CFI conversion failed): {}", ann.getId(), e.getMessage());
            }
        }

        // Convert note CFIs; skip failures
        List<NoteEntry> noteEntries = new ArrayList<>();
        for (BookNoteV2Entity note : bookNotes) {
            try {
                XPointerResult xp = epubCfiService.convertCfiToXPointer(bookPath, note.getCfi());
                String pos0 = xp.getPos0() != null ? xp.getPos0() : xp.getXpointer();
                String page = CfiConverter.normalizeProgressXPointer(pos0);
                String datetime = note.getCreatedAt() != null
                        ? note.getCreatedAt().format(DATETIME_FMT)
                        : "1970-01-01 00:00:00";
                noteEntries.add(new NoteEntry(note, xp, pos0, page, datetime));
            } catch (Exception e) {
                log.warn("Skipping note {} (CFI conversion failed): {}", note.getId(), e.getMessage());
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("-- KOReader bookmark file\n");
        sb.append("-- version: 1\n");
        sb.append("-- booklore:schema_version=").append(SCHEMA_VERSION).append("\n");
        sb.append("{\n");

        // Grimmory identity block — ignored by KOReader, used during sidecar import
        sb.append("    [\"booklore_meta\"] = {\n");
        sb.append("        [\"username\"] = ").append(luaString(username)).append(",\n");
        sb.append("        [\"user_id\"] = ").append(userId).append(",\n");
        sb.append("        [\"book_id\"] = ").append(bookId).append(",\n");
        sb.append("        [\"exported_at\"] = ").append(luaString(exportedAt)).append(",\n");
        sb.append("        [\"schema_version\"] = ").append(SCHEMA_VERSION).append(",\n");
        sb.append("    },\n");

        // highlights section
        // - one entry per AnnotationEntity
        // - one entry per BookNoteV2Entity that has selected text
        sb.append("    [\"highlights\"] = {\n");
        int index = 1;
        for (HighlightEntry e : highlightEntries) {
            AnnotationEntity ann = e.ann();
            String notes = ann.getText() != null ? ann.getText() : "";
            String chapter = ann.getChapterTitle() != null ? ann.getChapterTitle() : "";

            sb.append("        [").append(index++).append("] = {\n");
            sb.append("            [\"chapter\"] = ").append(luaString(chapter)).append(",\n");
            sb.append("            [\"datetime\"] = ").append(luaString(e.datetime())).append(",\n");
            sb.append("            [\"drawer\"] = ").append(luaString(e.drawer())).append(",\n");
            sb.append("            [\"notes\"] = ").append(luaString(notes)).append(",\n");
            sb.append("            [\"page\"] = ").append(luaString(e.page())).append(",\n");
            sb.append("            [\"pos0\"] = ").append(luaString(e.pos0())).append(",\n");
            sb.append("            [\"pos1\"] = ").append(luaString(e.pos1())).append(",\n");
            sb.append("            [\"booklore\"] = {\n");
            sb.append("                [\"id\"] = ").append(ann.getId()).append(",\n");
            sb.append("                [\"color\"] = ").append(luaString(ann.getColor() != null ? ann.getColor() : "")).append(",\n");
            sb.append("                [\"style\"] = ").append(luaString(ann.getStyle() != null ? ann.getStyle() : "highlight")).append(",\n");
            sb.append("                [\"note\"] = ").append(luaString(ann.getNote() != null ? ann.getNote() : "")).append(",\n");
            sb.append("                [\"version\"] = ").append(ann.getVersion() != null ? ann.getVersion() : 0).append(",\n");
            sb.append("            },\n");
            sb.append("        },\n");
        }
        for (NoteEntry e : noteEntries) {
            BookNoteV2Entity note = e.note();
            String selectedText = note.getSelectedText();
            if (selectedText == null || selectedText.isBlank()) {
                continue; // no selected text — bookmark only, no highlight entry
            }
            String chapter = note.getChapterTitle() != null ? note.getChapterTitle() : "";
            String pos1 = e.xp().getPos1() != null ? e.xp().getPos1() : e.pos0();

            sb.append("        [").append(index++).append("] = {\n");
            sb.append("            [\"chapter\"] = ").append(luaString(chapter)).append(",\n");
            sb.append("            [\"datetime\"] = ").append(luaString(e.datetime())).append(",\n");
            sb.append("            [\"drawer\"] = \"lighten\",\n");
            sb.append("            [\"notes\"] = ").append(luaString(selectedText)).append(",\n");
            sb.append("            [\"page\"] = ").append(luaString(e.page())).append(",\n");
            sb.append("            [\"pos0\"] = ").append(luaString(e.pos0())).append(",\n");
            sb.append("            [\"pos1\"] = ").append(luaString(pos1)).append(",\n");
            sb.append("            [\"booklore\"] = {\n");
            sb.append("                [\"id\"] = ").append(note.getId()).append(",\n");
            sb.append("                [\"color\"] = ").append(luaString(note.getColor() != null ? note.getColor() : "")).append(",\n");
            sb.append("                [\"style\"] = \"highlight\",\n");
            sb.append("                [\"note\"] = ").append(luaString(note.getNoteContent() != null ? note.getNoteContent() : "")).append(",\n");
            sb.append("                [\"version\"] = ").append(note.getVersion() != null ? note.getVersion() : 0).append(",\n");
            sb.append("            },\n");
            sb.append("        },\n");
        }
        sb.append("    },\n");

        // bookmarks section
        // - one entry per AnnotationEntity with a typed note
        // - one entry per BookNoteV2Entity (note content is always present)
        sb.append("    [\"bookmarks\"] = {\n");
        int bmIndex = 1;
        for (HighlightEntry e : highlightEntries) {
            AnnotationEntity ann = e.ann();
            if (ann.getNote() == null || ann.getNote().isBlank()) {
                continue;
            }
            sb.append("        [").append(bmIndex++).append("] = {\n");
            sb.append("            [\"datetime\"] = ").append(luaString(e.datetime())).append(",\n");
            sb.append("            [\"notes\"] = ").append(luaString(ann.getNote())).append(",\n");
            sb.append("            [\"page\"] = ").append(luaString(e.page())).append(",\n");
            sb.append("            [\"pos0\"] = ").append(luaString(e.pos0())).append(",\n");
            sb.append("        },\n");
        }
        for (NoteEntry e : noteEntries) {
            BookNoteV2Entity note = e.note();
            String noteContent = note.getNoteContent() != null ? note.getNoteContent() : "";
            sb.append("        [").append(bmIndex++).append("] = {\n");
            sb.append("            [\"datetime\"] = ").append(luaString(e.datetime())).append(",\n");
            sb.append("            [\"notes\"] = ").append(luaString(noteContent)).append(",\n");
            sb.append("            [\"page\"] = ").append(luaString(e.page())).append(",\n");
            sb.append("            [\"pos0\"] = ").append(luaString(e.pos0())).append(",\n");
            sb.append("        },\n");
        }
        sb.append("    },\n");

        sb.append("}\n");
        return sb.toString();
    }

    private static String styleToDrawer(String style) {
        if (style == null) return "lighten";
        return switch (style) {
            case "underline" -> "underscore";
            case "strikethrough" -> "strikeout";
            case "squiggly" -> "underscore";
            default -> "lighten"; // "highlight" and unknown
        };
    }

    private static String luaString(String value) {
        // Escape backslashes and double quotes, wrap in double quotes
        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }

    private static void writeAtomic(Path target, String content) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, content);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
