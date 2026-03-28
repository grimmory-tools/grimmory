package org.booklore.service.kobo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.kobo.KoboSpanPositionMap;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.UserBookFileProgressEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.enums.BookFileType;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KoboBookmarkLocationResolver {

    private final KoboSpanMapService koboSpanMapService;

    public Optional<ResolvedBookmarkLocation> resolve(UserBookProgressEntity progress,
                                                      UserBookFileProgressEntity fileProgress) {
        BookFileEntity bookFile = resolveBookFile(progress, fileProgress);
        if (!isKepubExportEnabled(bookFile)) {
            return Optional.empty();
        }
        Optional<KoboSpanPositionMap> spanMap = koboSpanMapService.getValidMap(bookFile);
        if (spanMap.isEmpty() || spanMap.get().chapters().isEmpty()) {
            return Optional.empty();
        }

        String href = resolveHref(progress, fileProgress);
        Float chapterProgressPercent = Optional.ofNullable(fileProgress)
                .map(UserBookFileProgressEntity::getContentSourceProgressPercent)
                .orElse(null);
        Float globalProgressPercent = resolveGlobalProgressPercent(progress, fileProgress);

        Optional<KoboSpanPositionMap.Chapter> chapter = resolveChapter(spanMap.get(), href, globalProgressPercent);
        if (chapter.isEmpty()) {
            return Optional.empty();
        }

        Float resolvedChapterProgressPercent = resolveChapterProgressPercent(chapter.get(), chapterProgressPercent,
                globalProgressPercent, href);
        KoboSpanPositionMap.Span span = resolveSpanMarker(chapter.get(), resolvedChapterProgressPercent);
        if (span == null) {
            return Optional.empty();
        }

        return Optional.of(new ResolvedBookmarkLocation(
                span.id(),
                "KoboSpan",
                chapter.get().sourceHref(),
                resolvedChapterProgressPercent));
    }

    private BookFileEntity resolveBookFile(UserBookProgressEntity progress,
                                           UserBookFileProgressEntity fileProgress) {
        if (fileProgress != null && fileProgress.getBookFile() != null) {
            return fileProgress.getBookFile();
        }
        if (progress == null || progress.getBook() == null) {
            return null;
        }
        return progress.getBook().getPrimaryBookFile();
    }

    private boolean isKepubExportEnabled(BookFileEntity bookFile) {
        if (bookFile == null) {
            return false;
        }
        return bookFile.getBookType() == BookFileType.EPUB && !bookFile.isFixedLayout();
    }

    private String resolveHref(UserBookProgressEntity progress,
                               UserBookFileProgressEntity fileProgress) {
        return Optional.ofNullable(fileProgress)
                .map(UserBookFileProgressEntity::getPositionHref)
                .filter(value -> !value.isBlank())
                .orElseGet(() -> Optional.ofNullable(progress)
                        .map(UserBookProgressEntity::getEpubProgressHref)
                        .filter(value -> !value.isBlank())
                        .orElse(null));
    }

    private Float resolveGlobalProgressPercent(UserBookProgressEntity progress,
                                               UserBookFileProgressEntity fileProgress) {
        Float fileProgressPercent = Optional.ofNullable(fileProgress)
                .map(UserBookFileProgressEntity::getProgressPercent)
                .orElse(null);
        if (fileProgressPercent != null) {
            return clampPercent(fileProgressPercent);
        }
        return Optional.ofNullable(progress)
                .map(UserBookProgressEntity::getEpubProgressPercent)
                .map(this::clampPercent)
                .orElse(null);
    }

    private Optional<KoboSpanPositionMap.Chapter> resolveChapter(KoboSpanPositionMap spanMap,
                                                                 String href,
                                                                 Float globalProgressPercent) {
        Optional<KoboSpanPositionMap.Chapter> byHref = findChapterByHref(spanMap, href);
        if (byHref.isPresent()) {
            return byHref;
        }
        if (globalProgressPercent == null) {
            return Optional.empty();
        }
        return findChapterByGlobalProgress(spanMap, globalProgressPercent);
    }

    private Optional<KoboSpanPositionMap.Chapter> findChapterByHref(KoboSpanPositionMap spanMap, String href) {
        if (href == null || href.isBlank()) {
            return Optional.empty();
        }
        String normalizedHref = normalizeHref(href);
        return spanMap.chapters().stream()
                .filter(chapter -> {
                    String normalizedChapter = chapter.normalizedHref();
                    return normalizedChapter.equals(normalizedHref)
                            || normalizedChapter.endsWith("/" + normalizedHref);
                })
                .min(Comparator.comparingInt(chapter -> {
                    String normalizedChapter = chapter.normalizedHref();
                    return normalizedChapter.equals(normalizedHref) ? 0 : normalizedChapter.length();
                }));
    }

    private Optional<KoboSpanPositionMap.Chapter> findChapterByGlobalProgress(KoboSpanPositionMap spanMap,
                                                                              Float globalProgressPercent) {
        float globalProgress = clampUnit(globalProgressPercent / 100f);
        return spanMap.chapters().stream()
                .min(Comparator.comparingDouble(chapter -> distanceToChapter(globalProgress, chapter)));
    }

    private Float resolveChapterProgressPercent(KoboSpanPositionMap.Chapter chapter,
                                                Float chapterProgressPercent,
                                                Float globalProgressPercent,
                                                String href) {
        if (chapterProgressPercent != null) {
            return clampPercent(chapterProgressPercent);
        }
        if (globalProgressPercent != null) {
            float chapterStart = chapter.globalStartProgress();
            float chapterEnd = chapter.globalEndProgress();
            float chapterWidth = chapterEnd - chapterStart;
            if (chapterWidth <= 0f) {
                return 0f;
            }
            float chapterProgress = (clampUnit(globalProgressPercent / 100f) - chapterStart) / chapterWidth;
            return clampPercent(clampUnit(chapterProgress) * 100f);
        }
        if (href != null && !href.isBlank()) {
            return 0f;
        }
        return null;
    }

    private KoboSpanPositionMap.Span resolveSpanMarker(KoboSpanPositionMap.Chapter chapter, Float chapterProgressPercent) {
        if (chapter.spans().isEmpty()) {
            return null;
        }
        if (chapterProgressPercent == null) {
            return chapter.spans().getFirst();
        }

        float targetProgress = clampUnit(chapterProgressPercent / 100f);
        return chapter.spans().stream()
                .min(Comparator.comparingDouble(item -> Math.abs(item.progression() - targetProgress)))
                .orElse(null);
    }

    private String normalizeHref(String href) {
        return URLDecoder.decode(href, StandardCharsets.UTF_8)
                .replace('\\', '/')
                .replaceFirst("#.*$", "")
                .replaceFirst("^/+", "");
    }

    private float clampUnit(float value) {
        return Math.max(0f, Math.min(value, 1f));
    }

    private double distanceToChapter(float globalProgress, KoboSpanPositionMap.Chapter chapter) {
        if (globalProgress < chapter.globalStartProgress()) {
            return chapter.globalStartProgress() - globalProgress;
        }
        if (globalProgress > chapter.globalEndProgress()) {
            return globalProgress - chapter.globalEndProgress();
        }
        return 0d;
    }

    private Float clampPercent(Float value) {
        return Math.max(0f, Math.min(value, 100f));
    }

    public record ResolvedBookmarkLocation(String value,
                                           String type,
                                           String source,
                                           Float contentSourceProgressPercent) {
    }
}
