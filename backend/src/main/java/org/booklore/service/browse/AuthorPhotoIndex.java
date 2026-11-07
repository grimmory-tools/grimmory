package org.booklore.service.browse;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.util.FileService;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthorPhotoIndex {

    private static final String KEY = "ids";

    private final FileService fileService;

    private final Cache<String, Set<Long>> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(30))
            .build();

    public Set<Long> authorIdsWithPhoto() {
        return cache.get(KEY, key -> scan());
    }

    void clearCache() {
        cache.invalidateAll();
    }

    private Set<Long> scan() {
        Path root = Paths.get(fileService.getAuthorImagesRoot());
        if (!Files.isDirectory(root)) {
            return Set.of();
        }
        Set<Long> ids = new HashSet<>();
        try (Stream<Path> dirs = Files.list(root)) {
            dirs.forEach(dir -> {
                long id;
                try {
                    id = Long.parseLong(dir.getFileName().toString());
                } catch (NumberFormatException e) {
                    return;
                }
                if (Files.exists(Paths.get(fileService.getAuthorThumbnailFile(id)))) {
                    ids.add(id);
                }
            });
        } catch (IOException e) {
            log.warn("Failed to scan author images root {}", root, e);
            return Set.of();
        }
        return ids;
    }
}
