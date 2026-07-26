/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.booklore.util.epub;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.service.ArchiveService;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.MediaType;
import org.grimmory.epub4j.domain.MediaTypes;
import org.grimmory.epub4j.domain.Resource;
import org.grimmory.epub4j.domain.Resources;
import org.grimmory.epub4j.domain.Spine;
import org.grimmory.epub4j.epub.EpubReader;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CoverDetectorService {
    /** Minimum image size in bytes to consider as a potential cover (10KB). */
    private static final int MIN_COVER_SIZE = 10 * 1024;

    private static final Pattern IMG_SRC_PATTERN =
            Pattern.compile("<img[^>]+src\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern SVG_IMAGE_PATTERN =
            Pattern.compile(
                    "<image[^>]+(?:href|xlink:href)\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    private final ArchiveService archiveService;

    private Resource detectCoverImageFallback(Path path) {
        // Last resort: scan container for cover-like images
        try {
            for (var entryName : archiveService.getEntryNames(path)) {
                String lower = entryName.toLowerCase();
                if (lower.contains("cover") && (lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                        lower.endsWith(".png") || lower.endsWith(".webp"))) {
                    return new Resource(
                            archiveService.getEntryBytes(path, entryName),
                            collapsePath(entryName)
                    );
                }
            }
        } catch (Exception e) {
            log.debug("Container cover search failed for {}: {}", path.getFileName().toString(), e.getMessage());
        }

        return null;
    }

    private Resource detectCoverImageResource(Book book) {
        if (book.getCoverImage() != null) {
            return book.getCoverImage();
        }

        var resources = book.getResources().getAll();

        Resource byId = findCoverById(resources);
        if (byId != null) {
            log.debug("Cover detected by resource id: {}", byId.getHref());
            return byId;
        }

        Resource byName = findCoverByName(resources);
        if (byName != null) {
            log.debug("Cover detected by filename: {}", byName.getHref());
            return byName;
        }

        Resource fromSpine = findCoverFromFirstSpineItem(book);
        if (fromSpine != null) {
            log.debug("Cover detected from first spine item: {}", fromSpine.getHref());
            return fromSpine;
        }

        Resource largest = findLargestImage(resources);
        if (largest != null) {
            log.debug("Cover detected as largest image: {}", largest.getHref());
            return largest;
        }

        Resource firstImage = findFirstManifestImage(resources);
        if (firstImage != null) {
            log.debug("Cover detected as first manifest image: {}", firstImage.getHref());
            return firstImage;
        }

        return null;
    }

    public String detectCoverImagePath(Path path) {
        try {
            Book book = new EpubReader().readEpubLazy(path, "UTF-8");

            Resource opfResource = book.getOpfResource();
            String opfPath = opfResource != null ? opfResource.getHref() : "";
            String rootPath = opfPath.contains("/") ? opfPath.substring(0, opfPath.lastIndexOf('/') + 1) : "";

            var resource = detectCoverImageResource(book);

            if (resource == null) {
                // fallback is absolute
                rootPath = "";
                resource = detectCoverImageFallback(path);
            }

            return resource == null ? null : rootPath + resource.getHref();
        } catch (IOException e) {
            log.debug("Failed to read epub for cover detection: {}", e.getMessage());
        }

        return null;
    }

    public byte[] detectCoverImage(Path path) {
        try {
            Book book = new EpubReader().readEpubLazy(path, "UTF-8");

            var resource = detectCoverImageResource(book);

            if (resource == null) {
                resource = detectCoverImageFallback(path);
            }

            return resource == null ? null : resource.getData();
        } catch (IOException e) {
            log.debug("Failed to read epub for cover detection: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Find an image resource whose id contains "cover" (case-insensitive). Matches id patterns like
     * "cover-image", "cover", "coverimg" that OPF generators commonly use.
     */
    private Resource findCoverById(Collection<Resource> resources) {
        for (Resource resource : resources) {
            if (!isImageResource(resource)) continue;
            String id = resource.getId();
            if (id != null && id.toLowerCase().contains("cover")) {
                return resource;
            }
        }
        return null;
    }

    /**
     * Returns the first image resource found in the manifest. Last-resort fallback when all other
     * strategies fail.
     */
    private Resource findFirstManifestImage(Collection<Resource> resources) {
        for (Resource resource : resources) {
            if (isImageResource(resource) && resource.getSize() >= MIN_COVER_SIZE) {
                return resource;
            }
        }
        return null;
    }

    /**
     * Find an image resource whose filename contains "cover" (case-insensitive). Prioritizes exact
     * matches like "cover.jpg" over partial matches like "discover.png".
     */
    private Resource findCoverByName(Collection<Resource> resources) {
        Resource exactMatch = null;
        Resource partialMatch = null;

        for (Resource resource : resources) {
            if (!isImageResource(resource)) continue;

            String href = resource.getHref();
            if (href == null) continue;

            String filename = href;
            int lastSlash = filename.lastIndexOf('/');
            if (lastSlash >= 0) filename = filename.substring(lastSlash + 1);
            String filenameLower = filename.toLowerCase();

            int dotPos = filenameLower.lastIndexOf('.');
            String nameWithoutExt = dotPos > 0 ? filenameLower.substring(0, dotPos) : filenameLower;
            if ("cover".equals(nameWithoutExt)) {
                exactMatch = resource;
                break; // Can't do better than an exact match
            }

            if (partialMatch == null && filenameLower.contains("cover")) {
                partialMatch = resource;
            }
        }

        return exactMatch != null ? exactMatch : partialMatch;
    }

    /**
     * Look at the first spine item's XHTML content and find the first referenced image. This works
     * for EPUBs where the first page is a cover page containing an img tag.
     */
    private Resource findCoverFromFirstSpineItem(Book book) {
        Spine spine = book.getSpine();
        if (spine.isEmpty()) return null;

        Resource firstResource = spine.getResource(0);
        if (firstResource == null || firstResource.getMediaType() != MediaTypes.XHTML) {
            return null;
        }

        try {
            byte[] data = firstResource.getData();
            if (data == null || data.length == 0) return null;

            String content = new String(data, StandardCharsets.UTF_8);
            String firstHref = firstResource.getHref();
            String basePath = "";
            if (firstHref != null) {
                int lastSlash = firstHref.lastIndexOf('/');
                if (lastSlash >= 0) {
                    basePath = firstHref.substring(0, lastSlash + 1);
                }
            }

            Matcher imgMatcher = IMG_SRC_PATTERN.matcher(content);
            if (imgMatcher.find()) {
                Resource resolved = resolveImageRef(book.getResources(), basePath, imgMatcher.group(1));
                if (resolved != null) return resolved;
            }

            Matcher svgMatcher = SVG_IMAGE_PATTERN.matcher(content);
            if (svgMatcher.find()) {
                Resource resolved = resolveImageRef(book.getResources(), basePath, svgMatcher.group(1));
                if (resolved != null) return resolved;
            }
        } catch (IOException e) {
            log.debug("Failed to read first spine item for cover detection: {}", e.getMessage());
        }

        return null;
    }

    /** Resolve an image reference relative to a base path and look it up in resources. */
    private Resource resolveImageRef(Resources resources, String basePath, String imgSrc) {
        if (imgSrc == null || imgSrc.isBlank()) return null;

        int hashPos = imgSrc.indexOf('#');
        if (hashPos >= 0) imgSrc = imgSrc.substring(0, hashPos);

        int queryPos = imgSrc.indexOf('?');
        if (queryPos >= 0) imgSrc = imgSrc.substring(0, queryPos);

        Resource resource = resources.getByHref(imgSrc);
        if (resource != null) return resource;

        String resolved = basePath + imgSrc;
        resource = resources.getByHref(resolved);
        if (resource != null) return resource;

        if (resolved.contains("..") || resolved.contains("./") || resolved.startsWith("/")) {
            resolved = collapsePath(resolved);
            resource = resources.getByHref(resolved);
            return resource;
        }

        return null;
    }

    /**
     * Find the largest image resource by data size. Only considers images above the minimum size
     * threshold.
     */
    private Resource findLargestImage(Collection<Resource> resources) {
        Resource largest = null;
        long largestSize = MIN_COVER_SIZE; // reject images below this as unlikely covers

        for (Resource resource : resources) {
            if (!isImageResource(resource)) continue;

            long size = resource.getSize();
            if (size > largestSize) {
                largestSize = size;
                largest = resource;
            }
        }

        return largest;
    }

    private boolean isImageResource(Resource resource) {
        MediaType mt = resource.getMediaType();
        return mt == MediaTypes.JPG
                || mt == MediaTypes.PNG
                || mt == MediaTypes.GIF
                || mt == MediaTypes.SVG
                || mt == MediaTypes.WEBP;
    }

    /** Collapse ".." and "." segments in a path. */
    private String collapsePath(String path) {
        String[] parts = path.split("/");
        Deque<String> stack = new ArrayDeque<>();
        for (String part : parts) {
            if ("..".equals(part)) {
                if (!stack.isEmpty()) stack.removeLast();
            } else if (!".".equals(part) && !part.isEmpty()) {
                stack.addLast(part);
            }
        }
        return String.join("/", stack);
    }
}
