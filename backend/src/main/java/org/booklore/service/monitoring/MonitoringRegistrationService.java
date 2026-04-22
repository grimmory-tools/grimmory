package org.booklore.service.monitoring;

import org.booklore.model.dto.Library;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@AllArgsConstructor
public class MonitoringRegistrationService {

    private final LibraryWatchService libraryWatchService;

    public boolean isPathMonitored(Path path) {
        return libraryWatchService.isPathMonitored(path);
    }

    public boolean isLibraryMonitored(Long libraryId) {
        return libraryWatchService.isLibraryMonitored(libraryId);
    }

    public void unregisterSpecificPath(Path path) {
        if (!Files.exists(path)) {
            log.debug("Path does not exist, attempting to unregister anyway: {}", path);
        }
        libraryWatchService.unregisterPath(path);
    }

    public void registerSpecificPath(Path path, Long libraryId) {
        if (!Files.exists(path)) {
            log.warn("Cannot register path that does not exist: {}", path);
            return;
        }
        if (!Files.isDirectory(path)) {
            log.warn("Cannot register path that is not a directory: {}", path);
            return;
        }
        libraryWatchService.registerPath(path, libraryId);
    }

    public void registerLibrary(Library library) {
        libraryWatchService.registerLibrary(library);
    }

    public void unregisterLibrary(Long libraryId) {
        libraryWatchService.unregisterLibrary(libraryId);
    }

    public void registerLibraryPaths(Long libraryId, Path libraryRoot) {
        libraryWatchService.registerLibraryPaths(libraryId, libraryRoot);
    }

    public void registerLibraries(Map<Long, Path> libraries) {
        if (libraries == null || libraries.isEmpty()) {
            return;
        }
        libraries.forEach(this::registerLibraryPaths);
    }

    public void unregisterLibraries(Collection<Long> libraryIds) {
        if (libraryIds == null || libraryIds.isEmpty()) {
            return;
        }
        libraryIds.forEach(this::unregisterLibrary);
    }

    public Set<Path> getPathsForLibraries(Collection<Long> libraryIds) {
        if (libraryIds == null || libraryIds.isEmpty()) {
            return Set.of();
        }
        return libraryWatchService.getPathsForLibraries(new HashSet<>(libraryIds));
    }

    public boolean waitForEventsDrainedByPaths(Set<Path> paths, long timeoutMs) {
        return libraryWatchService.waitForEventsDrainedByPaths(paths, timeoutMs);
    }

    public boolean waitForEventsDrained(Collection<Long> libraryIds, long timeoutMs) {
        if (libraryIds == null || libraryIds.isEmpty()) {
            return true;
        }
        return libraryWatchService.waitForEventsDrained(new HashSet<>(libraryIds), timeoutMs);
    }
}
