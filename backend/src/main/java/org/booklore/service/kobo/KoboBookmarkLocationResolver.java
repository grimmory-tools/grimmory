package org.booklore.service.kobo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.kobo.KoboSpanPositionMap;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.UserBookFileProgressEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.util.koreader.EpubCfiService;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

import static org.booklore.service.kobo.KoboEpubUtils.clampUnit;
import static org.booklore.service.kobo.KoboEpubUtils.normalizeHref;

@Slf4j
@Service
@RequiredArgsConstructor
public class KoboBookmarkLocationResolver {

    private final KoboSpanMapService koboSpanMapService;
    private final EpubCfiService epubCfiService;

    public Optional<ResolvedBookmarkLocation> resolve(UserBookProgressEntity progress,
                                                      UserBookFileProgressEntity fileProgress) {
        return resolve(progress, fileProgress, null);
    }

    public Optional<ResolvedBookmarkLocation> resolve(UserBookProgressEntity progress,
                                                      UserBookFileProgressEntity fileProgress,
                                                      Map<Long, KoboSpanPositionMap> preloadedMaps) {
        BookFileEntity bookFile = resolveBookFile(progress, fileProgress);
        if (!isKepubExportEnabled(bookFile)) {
            return Optional.empty();
        }
        Optional<KoboSpanPositionMap> spanMap = lookupSpanMap(bookFile, preloadedMaps);
        if (spanMap.isEmpty() || spanMap.get().chapters().isEmpty()) {
            return Optional.empty();
        }

        Optional<EpubCfiService.CfiLocation> cfiLocation = resolveCfiLocation(bookFile, progress, fileProgress);
        String href = cfiLocation
                .map(EpubCfiService.CfiLocation::href)
                .filter(value -> !value.isBlank())
                .orElseGet(() -> resolveHref(progress, fileProgress));
        Float chapterProgressPercent = Optional.ofNullable(fileProgress)
                .map(UserBookFileProgressEntity::getContentSourceProgressPercent)
                .orElseGet(() -> cfiLocation
                        .map(EpubCfiService.CfiLocation::contentSourceProgressPercent)
                        .orElse(null));
        Float globalProgressPercent = resolveGlobalProgressPercent(progress, fileProgress);

        Optional<KoboSpanPositionMap.Chapter> chapter = resolveChapter(spanMap.get(), href, globalProgressPercent);
        if (chapter.isEmpty()) {
            return Optional.empty();
        }

        Float resolvedChapterProgressPercent = resolveChapterProgressPercent(chapter.get(), chapterProgressPercent,
                globalProgressPercent);
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

    private Optional<EpubCfiService.CfiLocation> resolveCfiLocation(BookFileEntity bookFile,
                                                                    UserBookProgressEntity progress,
                                                                    UserBookFileProgressEntity fileProgress) {
        String cfi = resolveCfi(progress, fileProgress);
        if (cfi == null || cfi.isBlank() || bookFile == null) {
            return Optional.empty();
        }

        try {
            Path epubPath = bookFile.getFullFilePath();
            if (epubPath == null) {
                return Optional.empty();
            }
            return epubCfiService.resolveCfiLocation(epubPath, cfi);
        } catch (Exception e) {
            log.debug("Failed to derive chapter position from CFI for bookFile {}: {}", bookFile.getId(), e.getMessage());
            return Optional.empty();
        }
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

    private Optional<KoboSpanPositionMap> lookupSpanMap(BookFileEntity bookFile,
                                                       Map<Long, KoboSpanPositionMap> preloadedMaps) {
        if (preloadedMaps != null && bookFile.getId() != null) {
            KoboSpanPositionMap map = preloadedMaps.get(bookFile.getId());
            if (map != null) {
                return Optional.of(map);
            }
        }
        return koboSpanMapService.getValidMap(bookFile);
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

    private String resolveCfi(UserBookProgressEntity progress,
                              UserBookFileProgressEntity fileProgress) {
        return Optional.ofNullable(fileProgress)
                .map(UserBookFileProgressEntity::getPositionData)
                .filter(value -> value != null && value.startsWith("epubcfi("))
                .orElseGet(() -> Optional.ofNullable(progress)
                        .map(UserBookProgressEntity::getEpubProgress)
                        .filter(value -> value != null && value.startsWith("epubcfi("))
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
                .map(KoboBookmarkLocationResolver::clampPercent)
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
                .max(Comparator.<KoboSpanPositionMap.Chapter>comparingInt(chapter -> {
                    String normalizedChapter = chapter.normalizedHref();
                    return normalizedChapter.equals(normalizedHref) ? 1 : 0;
                }).thenComparingInt(chapter -> chapter.normalizedHref().length()));
    }

    private Optional<KoboSpanPositionMap.Chapter> findChapterByGlobalProgress(KoboSpanPositionMap spanMap,
                                                                              Float globalProgressPercent) {
        float globalProgress = clampUnit(globalProgressPercent / 100f);
        return spanMap.chapters().stream()
                .min(Comparator.comparingDouble(chapter -> distanceToChapter(globalProgress, chapter)));
    }

    private Float resolveChapterProgressPercent(KoboSpanPositionMap.Chapter chapter,
                                                Float chapterProgressPercent,
                                                Float globalProgressPercent) {
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

    private double distanceToChapter(float globalProgress, KoboSpanPositionMap.Chapter chapter) {
        if (globalProgress < chapter.globalStartProgress()) {
            return chapter.globalStartProgress() - globalProgress;
        }
        if (globalProgress > chapter.globalEndProgress()) {
            return globalProgress - chapter.globalEndProgress();
        }
        return 0d;
    }

    private static Float clampPercent(Float value) {
        if (value == null) {
            return null;
        }
        return Math.max(0f, Math.min(value, 100f));
    }

    public record ResolvedBookmarkLocation(String value,
                                           String type,
                                           String source,
                                           Float contentSourceProgressPercent) {
    }
}
