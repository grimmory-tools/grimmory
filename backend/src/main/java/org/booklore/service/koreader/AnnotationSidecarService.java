package org.booklore.service.koreader;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.config.AppProperties;
import org.booklore.model.entity.AnnotationEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.util.koreader.CfiConvertor;
import org.booklore.util.koreader.EpubCfiService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@AllArgsConstructor
@Service
public class AnnotationSidecarService {

    private static final DateTimeFormatter DATETIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final EpubCfiService epubCfiService;
    private final AppProperties appProperties;

    /**
     * Writes (or deletes) the per-user KOReader annotation sidecar for {@code book}.
     *
     * <p>File path: {@code <bookFullPath>.sdr/metadata.epub.<username>.lua}
     *
     * <p>No-ops silently when local-storage is not configured or the book path is unavailable.
     * All exceptions from CFI conversion or I/O are caught and logged so that a sidecar failure
     * never propagates back to the caller (and never rolls back a DB transaction).
     */
    public void writeSidecar(BookEntity book, BookLoreUserEntity user, List<AnnotationEntity> annotations) {
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
            if (annotations.isEmpty()) {
                if (Files.exists(sidecarPath)) {
                    Files.delete(sidecarPath);
                    log.info("Deleted annotation sidecar (no annotations remain): {}", sidecarPath);
                }
                return;
            }

            Files.createDirectories(sidecarPath.getParent());
            String lua = buildLua(bookPath, annotations);
            writeAtomic(sidecarPath, lua);
            log.info("Wrote annotation sidecar ({} annotations) to {}", annotations.size(), sidecarPath);
        } catch (IOException e) {
            log.error("Failed to write annotation sidecar for book {} user {}: {}",
                    book.getId(), user.getUsername(), e.getMessage());
        }
    }

    // Visible for testing
    static Path resolveSidecarPath(Path bookPath, String username) {
        Path sdrDir = Path.of(bookPath + ".sdr");
        return sdrDir.resolve("metadata.epub." + username + ".lua");
    }

    private String buildLua(Path bookPath, List<AnnotationEntity> annotations) {
        StringBuilder sb = new StringBuilder();
        sb.append("-- KOReader bookmark file\n");
        sb.append("-- version: 1\n");
        sb.append("{\n");
        sb.append("    [\"highlights\"] = {\n");

        int index = 1;
        for (AnnotationEntity ann : annotations) {
            CfiConvertor.XPointerResult xp;
            try {
                xp = epubCfiService.convertCfiToXPointer(bookPath, ann.getCfi());
            } catch (Exception e) {
                log.warn("Skipping annotation {} (CFI conversion failed): {}", ann.getId(), e.getMessage());
                continue;
            }

            String pos0 = xp.getPos0() != null ? xp.getPos0() : xp.getXpointer();
            String pos1 = xp.getPos1() != null ? xp.getPos1() : xp.getXpointer();
            String page = CfiConvertor.normalizeProgressXPointer(pos0);
            String drawer = styleToDrawer(ann.getStyle());
            String datetime = ann.getCreatedAt() != null
                    ? ann.getCreatedAt().format(DATETIME_FMT)
                    : "1970-01-01 00:00:00";
            String chapter = ann.getChapterTitle() != null ? ann.getChapterTitle() : "";
            String notes = ann.getText() != null ? ann.getText() : "";

            sb.append("        [").append(index++).append("] = {\n");
            sb.append("            [\"chapter\"] = ").append(luaString(chapter)).append(",\n");
            sb.append("            [\"datetime\"] = ").append(luaString(datetime)).append(",\n");
            sb.append("            [\"drawer\"] = ").append(luaString(drawer)).append(",\n");
            sb.append("            [\"notes\"] = ").append(luaString(notes)).append(",\n");
            sb.append("            [\"page\"] = ").append(luaString(page)).append(",\n");
            sb.append("            [\"pos0\"] = ").append(luaString(pos0)).append(",\n");
            sb.append("            [\"pos1\"] = ").append(luaString(pos1)).append(",\n");
            sb.append("        },\n");
        }

        sb.append("    },\n");
        sb.append("    [\"bookmarks\"] = {},\n");
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
