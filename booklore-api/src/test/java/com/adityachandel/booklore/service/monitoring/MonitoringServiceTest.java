package com.adityachandel.booklore.service.monitoring;

import com.adityachandel.booklore.model.dto.Library;
import com.adityachandel.booklore.model.dto.LibraryPath;
import com.adityachandel.booklore.service.watcher.LibraryFileEventProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MonitoringServiceTest {

    @TempDir
    Path tmp;

    MonitoringService service;
    LibraryFileEventProcessor processor;
    MonitoringTask monitoringTask;
    WatchService watchService;

    @BeforeEach
    void setup() throws Exception {
        processor = mock(LibraryFileEventProcessor.class);
        monitoringTask = mock(MonitoringTask.class);
        watchService = FileSystems.getDefault().newWatchService();
        service = Mockito.spy(new MonitoringService(processor, watchService, monitoringTask));
    }

    @AfterEach
    void teardown() {
        try {
            service.stopMonitoring();
        } catch (Exception ignored) {}
        try { watchService.close(); } catch (Exception ignored) {}
    }

    @Test
    void registerLibrary_registersAllDirectoriesUnderLibraryPath() throws Exception {
        Path root = tmp.resolve("libroot");
        Path a = root.resolve("a");
        Path b = a.resolve("b");
        Files.createDirectories(b);

        Library lib = mock(Library.class);
        LibraryPath lp = mock(LibraryPath.class);
        when(lp.getPath()).thenReturn(root.toString());
        when(lib.getPaths()).thenReturn(List.of(lp));
        when(lib.getId()).thenReturn(7L);
        when(lib.getName()).thenReturn("my-lib");
        when(lib.isWatch()).thenReturn(true);

        doReturn(true).when(service).registerPath(any(Path.class), eq(7L));

        service.registerLibrary(lib);

        Files.walk(root).filter(Files::isDirectory).forEach(path ->
                verify(service).registerPath(eq(path), eq(7L))
        );
    }

    @Test
    void unregisterLibrary_removesRegisteredPathsAndUpdatesMaps() throws Exception {
        Path root = tmp.resolve("libroot2");
        Files.createDirectories(root);

        Field pathToLibraryField = MonitoringService.class.getDeclaredField("pathToLibraryIdMap");
        pathToLibraryField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Path,Long> map = (Map<Path,Long>) pathToLibraryField.get(service);
        map.put(root, 99L);

        Field monitoredPathsField = MonitoringService.class.getDeclaredField("monitoredPaths");
        monitoredPathsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<Path> monitored = (Set<Path>) monitoredPathsField.get(service);
        monitored.add(root);

        Field registeredKeysField = MonitoringService.class.getDeclaredField("registeredWatchKeys");
        registeredKeysField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Path,WatchKey> keys = (Map<Path,WatchKey>) registeredKeysField.get(service);
        WatchKey mockKey = mock(WatchKey.class);
        keys.put(root, mockKey);

        service.unregisterLibrary(99L);

        assertFalse(monitored.contains(root), "monitoredPaths should no longer contain root");
        assertFalse(map.containsKey(root), "pathToLibraryIdMap should no longer contain root");
        assertFalse(keys.containsKey(root), "registeredWatchKeys should no longer contain root");
        verify(mockKey).cancel();
    }

    @Test
    void handleFileChangeEvent_createDirectory_registersNestedPaths() throws Exception {
        Path watched = tmp.resolve("watched");
        Files.createDirectories(watched);
        Path newDir = watched.resolve("newdir");
        Files.createDirectories(newDir);

        Field pathToLibraryField = MonitoringService.class.getDeclaredField("pathToLibraryIdMap");
        pathToLibraryField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Path,Long> map = (Map<Path,Long>) pathToLibraryField.get(service);
        map.put(watched, 5L);

        doReturn(true).when(service).registerPath(any(Path.class), eq(5L));

        FileChangeEvent ev = mock(FileChangeEvent.class);
        when(ev.getFilePath()).thenReturn(newDir);
        doReturn(StandardWatchEventKinds.ENTRY_CREATE).when(ev).getEventKind();
        when(ev.getWatchedFolder()).thenReturn(watched);

        service.handleFileChangeEvent(ev);

        Files.walk(newDir).filter(Files::isDirectory).forEach(p -> verify(service).registerPath(eq(p), eq(5L)));
    }

    @Test
    void backgroundProcessor_processesQueuedEvents_and_callsProcessor() throws Exception {
        Path watched = tmp.resolve("wf");
        Files.createDirectories(watched);
        Path file = watched.resolve("book.pdf");
        Files.writeString(file, "x");

        Field pathToLibraryField = MonitoringService.class.getDeclaredField("pathToLibraryIdMap");
        pathToLibraryField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Path,Long> map = (Map<Path,Long>) pathToLibraryField.get(service);
        map.put(watched, 123L);

        java.lang.reflect.Method startMethod = MonitoringService.class.getDeclaredMethod("startProcessingThread");
        startMethod.setAccessible(true);
        startMethod.invoke(service);

        FileChangeEvent ev = mock(FileChangeEvent.class);
        when(ev.getFilePath()).thenReturn(file);
        doReturn(StandardWatchEventKinds.ENTRY_CREATE).when(ev).getEventKind();
        when(ev.getWatchedFolder()).thenReturn(watched);

        service.handleFileChangeEvent(ev);

        verify(processor, timeout(2_000)).processFile(eq(StandardWatchEventKinds.ENTRY_CREATE), eq(123L), eq(watched.toString()), eq(file.toString()));
    }

    @Test
    void handleWatchKeyInvalidation_removesInvalidPath_and_cancelsKey() throws Exception {
        Path invalid = tmp.resolve("inv");
        Files.createDirectories(invalid);

        Field monitoredPathsField = MonitoringService.class.getDeclaredField("monitoredPaths");
        monitoredPathsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<Path> monitored = (Set<Path>) monitoredPathsField.get(service);
        monitored.add(invalid);

        Field registeredKeysField = MonitoringService.class.getDeclaredField("registeredWatchKeys");
        registeredKeysField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Path,WatchKey> keys = (Map<Path,WatchKey>) registeredKeysField.get(service);
        WatchKey wk = mock(WatchKey.class);
        keys.put(invalid, wk);

        WatchKeyInvalidatedEvent ev = mock(WatchKeyInvalidatedEvent.class);
        when(ev.getInvalidPath()).thenReturn(invalid);

        service.handleWatchKeyInvalidation(ev);

        assertFalse(monitored.contains(invalid));
        assertFalse(keys.containsKey(invalid));
        verify(wk).cancel();
    }

    @Test
    void isRelevantBookFile_detectsBookExtensions() {
        Path pdf = Paths.get("somebook.pdf");
        Path txt = Paths.get("notes.txt");

        assertTrue(service.isRelevantBookFile(pdf));
        assertFalse(service.isRelevantBookFile(txt));
    }

    @Test
    void handleFileChangeEvent_ignoresIrrelevantNonBookFile() throws Exception {
        Path watched = tmp.resolve("watched-ignore");
        Files.createDirectories(watched);
        Path file = watched.resolve("notes.txt");
        Files.writeString(file, "notes");

        Field pathToLibraryField = MonitoringService.class.getDeclaredField("pathToLibraryIdMap");
        pathToLibraryField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Path,Long> map = (Map<Path,Long>) pathToLibraryField.get(service);
        map.put(watched, 11L);

        java.lang.reflect.Method startMethod = MonitoringService.class.getDeclaredMethod("startProcessingThread");
        startMethod.setAccessible(true);
        startMethod.invoke(service);

        FileChangeEvent ev = mock(FileChangeEvent.class);
        when(ev.getFilePath()).thenReturn(file);
        doReturn(StandardWatchEventKinds.ENTRY_CREATE).when(ev).getEventKind();
        when(ev.getWatchedFolder()).thenReturn(watched);

        service.handleFileChangeEvent(ev);

        verify(processor, timeout(500).times(0)).processFile(any(), anyLong(), anyString(), anyString());
    }

    @Test
    void handleFileChangeEvent_deleteDirectory_unregistersSubPaths() throws Exception {
        Path watched = tmp.resolve("watched-del");
        Path a = watched.resolve("a");
        Path b = a.resolve("b");
        Files.createDirectories(b);

        Field monitoredPathsField = MonitoringService.class.getDeclaredField("monitoredPaths");
        monitoredPathsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<Path> monitored = (Set<Path>) monitoredPathsField.get(service);
        monitored.add(watched);
        monitored.add(a);
        monitored.add(b);

        FileChangeEvent ev = mock(FileChangeEvent.class);
        when(ev.getFilePath()).thenReturn(a);
        doReturn(StandardWatchEventKinds.ENTRY_DELETE).when(ev).getEventKind();
        when(ev.getWatchedFolder()).thenReturn(watched);

        service.handleFileChangeEvent(ev);

        assertFalse(monitored.contains(a));
        assertFalse(monitored.contains(b));
        assertTrue(monitored.contains(watched));
    }

    @Test
    void isPathMonitored_handlesNonNormalizedPaths() throws Exception {
        Path root = tmp.resolve("libroot-norm");
        Path sub = root.resolve("subdir");
        Files.createDirectories(sub);

        Field monitoredPathsField = MonitoringService.class.getDeclaredField("monitoredPaths");
        monitoredPathsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<Path> monitored = (Set<Path>) monitoredPathsField.get(service);
        monitored.add(sub.toAbsolutePath().normalize());

        Path nonNormalized = root.resolve("subdir/../subdir/.");
        assertTrue(service.isPathMonitored(nonNormalized));
    }

    @Test
    void registerPath_successful_updatesInternalMapsAndSets() throws Exception {
        Path dir = tmp.resolve("regdir");
        Files.createDirectories(dir);

        boolean registered = service.registerPath(dir, 55L);
        assertTrue(registered);

        Field pathToLibraryField = MonitoringService.class.getDeclaredField("pathToLibraryIdMap");
        pathToLibraryField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Path,Long> map = (Map<Path,Long>) pathToLibraryField.get(service);
        assertEquals(55L, map.get(dir));

        Field monitoredPathsField = MonitoringService.class.getDeclaredField("monitoredPaths");
        monitoredPathsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<Path> monitored = (Set<Path>) monitoredPathsField.get(service);
        assertTrue(monitored.contains(dir));

        Field keysField = MonitoringService.class.getDeclaredField("registeredWatchKeys");
        keysField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Path, WatchKey> keys = (Map<Path, WatchKey>) keysField.get(service);
        assertTrue(keys.containsKey(dir));
    }
}
