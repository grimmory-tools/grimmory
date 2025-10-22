package com.adityachandel.booklore.service.monitoring;

import com.adityachandel.booklore.model.dto.Library;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;

@Slf4j
@Service
@AllArgsConstructor
public class MonitoringRegistrationService {

    private final MonitoringService monitoringService;

    public boolean isPathMonitored(Path path) {
        return monitoringService.isPathMonitored(path);
    }

    public void unregisterSpecificPath(Path path) {
        monitoringService.unregisterPath(path);
    }

    public void registerSpecificPath(Path path, Long libraryId) {
        monitoringService.registerPath(path, libraryId);
    }

    public void registerLibrary(Library library) {
        monitoringService.registerLibrary(library);
    }

    public void unregisterLibrary(Long libraryId) {
        monitoringService.unregisterLibrary(libraryId);
    }

    public void registerLibraryPaths(Long libraryId, Path libraryRoot) {
        if (!Files.exists(libraryRoot) || !Files.isDirectory(libraryRoot)) {
            return;
        }
        try {
            monitoringService.registerPath(libraryRoot, libraryId);
            try (var stream = Files.walk(libraryRoot)) {
                stream.filter(Files::isDirectory)
                        .filter(path -> !path.equals(libraryRoot))
                        .forEach(path -> monitoringService.registerPath(path, libraryId));
            }
        } catch (Exception e) {
            log.error("Failed to register library paths for libraryId {} at {}", libraryId, libraryRoot, e);
        }
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
}