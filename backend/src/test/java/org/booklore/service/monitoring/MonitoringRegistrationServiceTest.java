package org.booklore.service.monitoring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MonitoringRegistrationServiceTest {

    @Mock
    LibraryWatchService libraryWatchService;

    @InjectMocks
    MonitoringRegistrationService registrationService;

    @TempDir
    Path tmp;

    Path root;
    Path sub1;
    Path sub2;

    @BeforeEach
    void setupFs() throws IOException {
        root = tmp.resolve("libroot");
        sub1 = root.resolve("a");
        sub2 = root.resolve("a").resolve("b");
        Files.createDirectories(sub2);
    }

    @Test
    void isPathMonitored_delegatesToLibraryWatchService() {
        when(libraryWatchService.isPathMonitored(root)).thenReturn(true);
        assertTrue(registrationService.isPathMonitored(root));
        verify(libraryWatchService).isPathMonitored(root);
    }

    @Test
    void unregisterSpecificPath_delegates() {
        registrationService.unregisterSpecificPath(root);
        verify(libraryWatchService).unregisterPath(root);
    }

    @Test
    void registerSpecificPath_delegates() {
        registrationService.registerSpecificPath(root, 123L);
        verify(libraryWatchService).registerPath(root, 123L);
    }

    @Test
    void unregisterLibrary_delegates() {
        registrationService.unregisterLibrary(99L);
        verify(libraryWatchService).unregisterLibrary(99L);
    }

    @Test
    void registerLibraryPaths_noopWhenMissingOrNotDirectory() throws IOException {
        Path missing = tmp.resolve("does-not-exist");
        registrationService.registerLibraryPaths(7L, missing);
        verify(libraryWatchService).registerLibraryPaths(7L, missing);

        Path file = tmp.resolve("afile.txt");
        Files.writeString(file, "x");
        registrationService.registerLibraryPaths(7L, file);
        verify(libraryWatchService).registerLibraryPaths(7L, file);
    }

    @Test
    void registerLibraryPaths_delegates() {
        registrationService.registerLibraryPaths(42L, root);
        verify(libraryWatchService).registerLibraryPaths(42L, root);
    }
}
