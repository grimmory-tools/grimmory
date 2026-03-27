package org.booklore.service.kobo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import org.booklore.model.dto.settings.KoboSettings;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.UserBookFileProgressEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.service.appsettings.AppSettingService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KoboBookmarkLocationResolver {

    private final AppSettingService appSettingService;
    private final KepubConversionService kepubConversionService;

    public Optional<ResolvedBookmarkLocation> resolve(UserBookProgressEntity progress,
                                                      UserBookFileProgressEntity fileProgress) {
        BookFileEntity bookFile = resolveBookFile(progress, fileProgress);
        if (!isKepubExportEnabled(bookFile)) {
            return Optional.empty();
        }

        File epubFile = getEpubFile(bookFile);
        if (epubFile == null || !epubFile.isFile()) {
            return Optional.empty();
        }

        String href = resolveHref(progress, fileProgress);
        if (href == null || href.isBlank()) {
            return Optional.empty();
        }

        Float contentSourceProgressPercent = Optional.ofNullable(fileProgress)
                .map(UserBookFileProgressEntity::getContentSourceProgressPercent)
                .orElse(null);

        return resolveKoboSpan(epubFile, href, contentSourceProgressPercent);
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

    private File getEpubFile(BookFileEntity bookFile) {
        try {
            Path fullPath = bookFile.getFullFilePath();
            return fullPath != null ? fullPath.toFile() : null;
        } catch (Exception e) {
            log.debug("Unable to resolve EPUB path for file {}", bookFile.getId(), e);
            return null;
        }
    }

    private String resolveHref(UserBookProgressEntity progress,
                               UserBookFileProgressEntity fileProgress) {
        return Optional.ofNullable(fileProgress)
                .map(UserBookFileProgressEntity::getPositionHref)
                .filter(value -> !value.isBlank())
                .orElseGet(() -> Optional.ofNullable(progress.getEpubProgressHref())
                        .filter(value -> !value.isBlank())
                        .orElse(null));
    }

    private Optional<ResolvedBookmarkLocation> resolveKoboSpan(File epubFile,
                                                               String href,
                                                               Float contentSourceProgressPercent) {
        Path tempDir = null;
        try {
            KoboSettings koboSettings = appSettingService.getAppSettings().getKoboSettings();
            tempDir = Files.createTempDirectory("kobo-bookmark");
            File kepubFile = kepubConversionService.convertEpubToKepub(
                    epubFile,
                    tempDir.toFile(),
                    koboSettings != null && koboSettings.isForceEnableHyphenation());

            Optional<ZipResource> resourceOpt = readZipResource(kepubFile, href);
            if (resourceOpt.isEmpty()) {
                return Optional.empty();
            }

            ZipResource resource = resourceOpt.get();
            String html = resource.content();
            if (html == null || html.isBlank()) {
                return Optional.empty();
            }

            Float resolvedProgress = clampPercent(
                    contentSourceProgressPercent != null ? contentSourceProgressPercent : 0f);
            float targetProgress = clampUnit(resolvedProgress / 100f);
            Document document = Jsoup.parse(html, "", Parser.htmlParser().setTrackPosition(true));
            List<KoboSpanMarker> markers = document.select("span.koboSpan[id]").stream()
                    .map(span -> new KoboSpanMarker(
                            span.id(),
                            clampUnit(span.sourceRange().endPos() / (float) Math.max(html.length(), 1))))
                    .filter(marker -> !marker.id().isBlank())
                    .toList();

            if (markers.isEmpty()) {
                return Optional.empty();
            }

            KoboSpanMarker marker = contentSourceProgressPercent == null
                    ? markers.getFirst()
                    : markers.stream()
                            .min(Comparator.comparingDouble(item -> Math.abs(item.progression() - targetProgress)))
                            .orElse(null);
            if (marker == null) {
                return Optional.empty();
            }

            return Optional.of(new ResolvedBookmarkLocation(
                    marker.id(),
                    "KoboSpan",
                    resource.entryName(),
                    resolvedProgress));
        } catch (Exception e) {
            log.debug("Unable to resolve KoboSpan bookmark for href {}", href, e);
            return Optional.empty();
        } finally {
            if (tempDir != null) {
                try {
                    FileSystemUtils.deleteRecursively(tempDir);
                } catch (IOException e) {
                    log.debug("Unable to delete temporary KEPUB directory {}", tempDir, e);
                }
            }
        }
    }

    private Optional<ZipResource> readZipResource(File archiveFile, String href) throws IOException {
        String normalizedHref = normalizeHref(href);
        try (ZipFile zipFile = new ZipFile(archiveFile)) {
            FileHeader fileHeader = zipFile.getFileHeaders().stream()
                    .filter(header -> !header.isDirectory())
                    .filter(header -> {
                        String normalizedEntry = normalizeHref(header.getFileName());
                        return normalizedEntry.equals(normalizedHref)
                                || normalizedEntry.endsWith("/" + normalizedHref);
                    })
                    .min(Comparator.comparingInt(header -> {
                        String normalizedEntry = normalizeHref(header.getFileName());
                        return normalizedEntry.equals(normalizedHref) ? 0 : normalizedEntry.length();
                    }))
                    .orElse(null);

            if (fileHeader == null) {
                return Optional.empty();
            }

            try (var inputStream = zipFile.getInputStream(fileHeader)) {
                String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                return Optional.of(new ZipResource(fileHeader.getFileName(), content));
            }
        }
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

    private Float clampPercent(Float value) {
        return Math.max(0f, Math.min(value, 100f));
    }

    public record ResolvedBookmarkLocation(String value,
                                           String type,
                                           String source,
                                           Float contentSourceProgressPercent) {
    }

    private record ZipResource(String entryName, String content) {
    }

    private record KoboSpanMarker(String id, float progression) {
    }
}
