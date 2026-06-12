package org.booklore.opf;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.booklore.model.dto.settings.LibraryFile;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Component
public class AdjacentOpfCoverLocator {

    private static final List<String> FALLBACK_NAMES = List.of(
            "cover.jpg",
            "cover.jpeg",
            "cover.png",
            "folder.jpg",
            "folder.png"
    );

    public Optional<Path> find(Path opfPath, LibraryFile libraryFile) {
        if (opfPath == null || libraryFile == null || libraryFile.getFullPath() == null) {
            return Optional.empty();
        }

        Path folder = opfPath.toAbsolutePath().normalize().getParent();
        if (folder == null || !Files.isDirectory(folder)) {
            return Optional.empty();
        }

        Optional<Path> manifestCover = findManifestCover(opfPath, folder);
        if (manifestCover.isPresent()) {
            return manifestCover;
        }

        return findFallbackCover(folder, libraryFile);
    }

    private Optional<Path> findManifestCover(Path opfPath, Path folder) {
        try {
            String xml = Files.readString(opfPath, StandardCharsets.UTF_8);
            Element root = OpfXmlParser.parse(xml).getDocumentElement();
            if (root == null) {
                return Optional.empty();
            }

            List<ManifestItem> items = readManifestItems(root);
            Optional<String> coverId = readCoverId(root);
            if (coverId.isPresent()) {
                Optional<Path> byMeta = items.stream()
                        .filter(item -> coverId.get().equals(item.id()))
                        .map(ManifestItem::href)
                        .map(href -> resolveSafe(folder, href))
                        .flatMap(Optional::stream)
                        .findFirst();
                if (byMeta.isPresent()) {
                    return byMeta;
                }
            }

            Optional<Path> byCoverProperty = items.stream()
                    .filter(ManifestItem::hasCoverImageProperty)
                    .map(ManifestItem::href)
                    .map(href -> resolveSafe(folder, href))
                    .flatMap(Optional::stream)
                    .findFirst();
            if (byCoverProperty.isPresent()) {
                return byCoverProperty;
            }

            return items.stream()
                    .filter(ManifestItem::hasCoverHint)
                    .map(ManifestItem::href)
                    .map(href -> resolveSafe(folder, href))
                    .flatMap(Optional::stream)
                    .findFirst();
        } catch (Exception e) {
            log.warn("Failed to inspect OPF cover manifest {}: {}", opfPath, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> readCoverId(Element root) {
        var nodes = root.getElementsByTagNameNS("*", "meta");
        for (int i = 0; i < nodes.getLength(); i++) {
            if (!(nodes.item(i) instanceof Element element)) {
                continue;
            }
            if ("cover".equalsIgnoreCase(element.getAttribute("name"))) {
                String content = normalize(element.getAttribute("content"));
                if (StringUtils.isNotBlank(content)) {
                    return Optional.of(content);
                }
            }
        }
        return Optional.empty();
    }

    private List<ManifestItem> readManifestItems(Element root) {
        List<ManifestItem> items = new ArrayList<>();
        var nodes = root.getElementsByTagNameNS("*", "item");
        for (int i = 0; i < nodes.getLength(); i++) {
            if (!(nodes.item(i) instanceof Element element)) {
                continue;
            }
            String href = normalize(element.getAttribute("href"));
            if (StringUtils.isBlank(href)) {
                continue;
            }
            items.add(new ManifestItem(
                    normalize(element.getAttribute("id")),
                    href,
                    normalize(element.getAttribute("media-type")),
                    normalize(element.getAttribute("properties"))
            ));
        }
        return items;
    }

    private Optional<Path> findFallbackCover(Path folder, LibraryFile libraryFile) {
        for (String fileName : fallbackNames(libraryFile)) {
            Optional<Path> candidate = resolveSafe(folder, fileName);
            if (candidate.isPresent()) {
                return candidate;
            }
        }
        return Optional.empty();
    }

    private List<String> fallbackNames(LibraryFile libraryFile) {
        List<String> names = new ArrayList<>(FALLBACK_NAMES);
        if (!libraryFile.isFolderBased() && libraryFile.getFullPath().getFileName() != null) {
            String stem = stripExtension(libraryFile.getFullPath().getFileName().toString());
            names.add(stem + ".jpg");
            names.add(stem + ".png");
        }
        return names;
    }

    private Optional<Path> resolveSafe(Path folder, String href) {
        String decoded = URLDecoder.decode(href, StandardCharsets.UTF_8);
        Path candidate = folder.resolve(decoded).toAbsolutePath().normalize();
        if (!candidate.startsWith(folder) || !Files.isRegularFile(candidate)) {
            return Optional.empty();
        }
        return isSupportedImage(candidate) ? Optional.of(candidate) : Optional.empty();
    }

    private boolean isSupportedImage(Path candidate) {
        String fileName = candidate.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".png");
    }

    private String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private record ManifestItem(String id, String href, String mediaType, String properties) {
        boolean hasCoverImageProperty() {
            if (!mediaType.toLowerCase(Locale.ROOT).startsWith("image/")) {
                return false;
            }
            for (String property : properties.toLowerCase(Locale.ROOT).split("\\s+")) {
                if ("cover-image".equals(property)) {
                    return true;
                }
            }
            return false;
        }

        boolean hasCoverHint() {
            String lowerId = id.toLowerCase(Locale.ROOT);
            String lowerHref = href.toLowerCase(Locale.ROOT);
            return mediaType.toLowerCase(Locale.ROOT).startsWith("image/")
                    && (lowerId.contains("cover") || lowerHref.contains("cover"));
        }
    }
}
