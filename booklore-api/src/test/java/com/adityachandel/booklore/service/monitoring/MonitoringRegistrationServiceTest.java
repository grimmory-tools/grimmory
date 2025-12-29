package com.adityachandel.booklore.service.monitoring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MonitoringRegistrationServiceTest {

    @Mock
    MonitoringService monitoringService;

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
    void isPathMonitored_delegatesToMonitoringService() {
        when(monitoringService.isPathMonitored(root)).thenReturn(true);
        assertTrue(registrationService.isPathMonitored(root));
        verify(monitoringService).isPathMonitored(root);
    }

    @Test
    void unregisterSpecificPath_delegates() {
        registrationService.unregisterSpecificPath(root);
        verify(monitoringService).unregisterPath(root);
    }

    @Test
    void registerSpecificPath_delegates() {
        registrationService.registerSpecificPath(root, 123L);
        verify(monitoringService).registerPath(root, 123L);
    }

    @Test
    void unregisterLibrary_delegates() {
        registrationService.unregisterLibrary(99L);
        verify(monitoringService).unregisterLibrary(99L);
    }

    @Test
    void registerLibraryPaths_noopWhenMissingOrNotDirectory() throws IOException {
        Path missing = tmp.resolve("does-not-exist");
        registrationService.registerLibraryPaths(7L, missing);
        verifyNoInteractions(monitoringService);

        Path file = tmp.resolve("afile.txt");
        Files.writeString(file, "x");
        registrationService.registerLibraryPaths(7L, file);
        verifyNoInteractions(monitoringService);
    }

    @Test
    void registerLibraryPaths_registersRootAndAllSubdirs() {
        registrationService.registerLibraryPaths(42L, root);

        verify(monitoringService).registerPath(root, 42L);

        // subdirs a and a/b should be registered as well
        verify(monitoringService).registerPath(sub1, 42L);
        verify(monitoringService).registerPath(sub2, 42L);

        ArgumentCaptor<Path> capt = ArgumentCaptor.forClass(Path.class);
        verify(monitoringService, atLeast(3)).registerPath(capt.capture(), eq(42L));
        List<Path> registered = capt.getAllValues();
        assertTrue(registered.contains(root));
        assertTrue(registered.contains(sub1));
        assertTrue(registered.contains(sub2));
    }

    @Test
    void registerLibraryPaths_handlesMonitoringServiceExceptionGracefully() {
        doThrow(new RuntimeException("boom")).when(monitoringService).registerPath(eq(root), eq(55L));
        assertDoesNotThrow(() -> registrationService.registerLibraryPaths(55L, root));
        verify(monitoringService).registerPath(root, 55L);
    }

    @Test
    void registerLibraryPaths_partialFailureStops() {
        doAnswer(invocation -> {
            Path p = invocation.getArgument(0);
            Long id = invocation.getArgument(1);
            if (id != null && id.equals(42L) && p.getFileName() != null && "a".equals(p.getFileName().toString())) {
                throw new RuntimeException("fail-sub1");
            }
            return null;
        }).when(monitoringService).registerPath(any(Path.class), anyLong());

        registrationService.registerLibraryPaths(42L, root);

        verify(monitoringService).registerPath(root, 42L);
        verify(monitoringService).registerPath(argThat(p -> p.getFileName() != null && "a".equals(p.getFileName().toString())), eq(42L));
        verify(monitoringService, never()).registerPath(argThat(p -> p.getFileName() != null && "b".equals(p.getFileName().toString())), eq(42L));
    }

    @Test
    void registerLibraryPaths_onlyRegistersDirectories() throws IOException {
        Path fileInRoot = root.resolve("file.txt");
        Files.createDirectories(root);
        Files.writeString(fileInRoot, "content");

        registrationService.registerLibraryPaths(100L, root);

        verify(monitoringService).registerPath(root, 100L);
        verify(monitoringService).registerPath(sub1, 100L);
        verify(monitoringService).registerPath(sub2, 100L);

        verify(monitoringService, never()).registerPath(eq(fileInRoot), anyLong());
    }
}
