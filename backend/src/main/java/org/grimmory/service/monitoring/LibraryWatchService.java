package org.grimmory.service.monitoring;

import lombok.extern.slf4j.Slf4j;
import org.grimmory.model.dto.Library;
import org.grimmory.model.enums.BookFileExtension;
import org.grimmory.service.watcher.LibraryFileEventProcessor;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class LibraryWatchService implements SmartLifecycle {

    private static final int LIFECYCLE_PHASE = 20;

    private final LibraryFileEventProcessor eventProcessor;
    private WatchService watchService;
    private ExecutorService registrationExecutor;

    private final ConcurrentHashMap<Path, WatchEntry> watches = new ConcurrentHashMap<>();
    private final Map<Long, Boolean> libraryWatchStatus = new ConcurrentHashMap<>();
    private final Lock watchLock = new ReentrantLock();
    private volatile boolean running;

    private record WatchEntry(WatchKey key, long libraryId) {}

    public LibraryWatchService(LibraryFileEventProcessor eventProcessor) {
        this.eventProcessor = eventProcessor;
    }

    @Override
    public void start() {
        try {
            if (watchService == null) {
                this.watchService = FileSystems.getDefault().newWatchService();
            }
            if (registrationExecutor == null || registrationExecutor.isShutdown()) {
                this.registrationExecutor = Executors.newSingleThreadExecutor(
                        Thread.ofVirtual().name("watch-registrar").factory());
            }
            running = true;
            Thread.ofVirtual().name("watch-poll").start(this::pollLoop);
        } catch (IOException e) {
            log.error("Failed to start LibraryWatchService", e);
        }
    }

    @Override
    public void stop() {
        log.info("Shutting down LibraryWatchService...");
        running = false;
        registrationExecutor.shutdownNow();
        try {
            watchService.close();
        } catch (IOException e) {
            log.error("Failed to close WatchService", e);
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return LIFECYCLE_PHASE;
    }

    private void pollLoop() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key;
                try {
                    key = watchService.take();
                } catch (ClosedWatchServiceException e) {
                    log.warn("WatchService closed, stopping poll loop.");
                    break;
                }

                Path directory = (Path) key.watchable();

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        handleOverflow(directory);
                        continue;
                    }

                    Path fileName = (Path) event.context();
                    Path fullPath = directory.resolve(fileName);

                    boolean isDir = kind == StandardWatchEventKinds.ENTRY_CREATE
                            ? Files.isDirectory(fullPath)
                            : watches.containsKey(fullPath);

                    boolean isRelevantFile = !isDir && isRelevantBookFile(fullPath);
                    if (!isDir && !isRelevantFile) continue;

                    if (isDir && kind == StandardWatchEventKinds.ENTRY_CREATE) {
                        WatchEntry parentEntry = watches.get(directory);
                        if (parentEntry != null) {
                            registrationExecutor.submit(() -> registerRecursive(fullPath, parentEntry.libraryId()));
                        }
                    }

                    if (isDir && kind == StandardWatchEventKinds.ENTRY_DELETE) {
                        registrationExecutor.submit(() -> unregisterRecursive(fullPath));
                    }

                    WatchEntry entry = watches.get(directory);
                    if (entry != null) {
                        eventProcessor.processEvent(kind, entry.libraryId(), fullPath, isDir);
                    } else {
                        log.warn("No library ID found for watched directory: {}", directory);
                    }
                }

                boolean valid = key.reset();
                if (!valid) {
                    log.warn("WatchKey no longer valid: {}", directory);
                    registrationExecutor.submit(() -> unregisterRecursive(directory));
                }
            }
        } catch (InterruptedException e) {
            log.warn("Poll loop interrupted");
            Thread.currentThread().interrupt();
        }
    }

    private void handleOverflow(Path directory) {
        WatchEntry entry = watches.get(directory);
        if (entry != null) {
            log.warn("OVERFLOW for directory: {}. Queuing rescan for library {}.", directory, entry.libraryId());
            eventProcessor.processEvent(StandardWatchEventKinds.ENTRY_CREATE, entry.libraryId(), directory, true);
        } else {
            log.warn("OVERFLOW event for untracked directory: {}", directory);
        }
    }

    public void registerLibraries(List<Library> libraries) {
        libraries.forEach(lib -> libraryWatchStatus.put(lib.getId(), lib.isWatch()));
        libraries.stream().filter(Library::isWatch).forEach(this::registerLibrary);
        log.info("Registered {} libraries for monitoring", libraries.size());
    }

    public void registerLibrary(Library library) {
        libraryWatchStatus.put(library.getId(), library.isWatch());
        if (!library.isWatch()) return;

        int[] count = {0};
        library.getPaths().forEach(libraryPath -> {
            Path rootPath = Paths.get(libraryPath.getPath());
            if (Files.isDirectory(rootPath)) {
                try (Stream<Path> pathStream = Files.walk(rootPath)) {
                    pathStream.filter(Files::isDirectory).forEach(path -> {
                        if (registerPath(path, library.getId())) {
                            count[0]++;
                        }
                    });
                } catch (IOException e) {
                    log.error("Failed to register paths for library '{}': {}", library.getName(), e.getMessage(), e);
                }
            }
        });

        log.info("Registered {} folders for library '{}'", count[0], library.getName());
    }

    public void unregisterLibrary(Long libraryId) {
        Set<Path> pathsToRemove = watches.entrySet().stream()
                .filter(e -> e.getValue().libraryId() == libraryId)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        for (Path path : pathsToRemove) {
            unregisterPath(path);
        }

        libraryWatchStatus.put(libraryId, false);
        log.debug("Unregistered library {} from monitoring", libraryId);
    }

    public boolean registerPath(Path path, long libraryId) {
        if (!Files.exists(path)) {
            log.warn("Cannot register path that does not exist: {}", path);
            return false;
        }
        if (!Files.isDirectory(path)) {
            log.warn("Cannot register path that is not a directory: {}", path);
            return false;
        }
        watchLock.lock();
        try {
            if (!watches.containsKey(path)) {
                WatchKey key = path.register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_DELETE);
                watches.put(path, new WatchEntry(key, libraryId));
                return true;
            }
        } catch (IOException e) {
            log.error("Error registering path: {}", path, e);
        } finally {
            watchLock.unlock();
        }
        return false;
    }

    public void unregisterPath(Path path) {
        watchLock.lock();
        try {
            WatchEntry entry = watches.remove(path);
            if (entry != null) {
                entry.key().cancel();
                log.debug("Unregistered path: {}", path);
            }
        } finally {
            watchLock.unlock();
        }
    }

    private void registerRecursive(Path root, long libraryId) {
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isDirectory)
                    .forEach(p -> registerPath(p, libraryId));
        } catch (IOException e) {
            log.warn("Failed to register paths under: {}", root, e);
        }
    }

    private void unregisterRecursive(Path root) {
        Set<Path> toRemove = watches.keySet().stream()
                .filter(p -> p.startsWith(root))
                .collect(Collectors.toSet());

        for (Path path : toRemove) {
            unregisterPath(path);
        }
    }

    public void registerLibraryPaths(long libraryId, Path libraryRoot) {
        if (!Files.exists(libraryRoot) || !Files.isDirectory(libraryRoot)) {
            return;
        }
        try {
            log.debug("Registering library paths for libraryId {} at {}", libraryId, libraryRoot);
            registerPath(libraryRoot, libraryId);
            try (var stream = Files.walk(libraryRoot)) {
                stream.filter(Files::isDirectory)
                        .filter(path -> !path.equals(libraryRoot))
                        .forEach(path -> registerPath(path, libraryId));
            }
        } catch (Exception e) {
            log.error("Failed to register library paths for libraryId {} at {}", libraryId, libraryRoot, e);
        }
    }

    public boolean isRelevantBookFile(Path path) {
        return BookFileExtension.fromFileName(path.getFileName().toString()).isPresent();
    }

    public boolean isPathMonitored(Path path) {
        return watches.containsKey(path.toAbsolutePath().normalize());
    }

    public boolean isLibraryMonitored(long libraryId) {
        return libraryWatchStatus.getOrDefault(libraryId, false);
    }

    public Set<Path> getPathsForLibraries(Set<Long> libraryIds) {
        return watches.entrySet().stream()
                .filter(e -> libraryIds.contains(e.getValue().libraryId()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    public boolean waitForEventsDrained(Set<Long> libraryIds, long timeoutMs) {
        if (libraryIds == null || libraryIds.isEmpty()) {
            return true;
        }
        Set<Path> libraryPaths = getPathsForLibraries(libraryIds);
        return waitForEventsDrainedByPaths(libraryPaths, timeoutMs);
    }

    public boolean waitForEventsDrainedByPaths(Set<Path> paths, long timeoutMs) {
        if (paths == null || paths.isEmpty()) {
            return true;
        }

        final long pollIntervalMs = 50;
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (!eventProcessor.hasPendingEventsForPaths(paths)) {
                log.debug("Events drained for {} paths", paths.size());
                return true;
            }
            try {
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        log.warn("Timeout waiting for events to drain for {} paths", paths.size());
        return false;
    }
}
