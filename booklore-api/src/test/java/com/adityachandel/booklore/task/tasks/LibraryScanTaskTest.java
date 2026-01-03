package com.adityachandel.booklore.task.tasks;

import com.adityachandel.booklore.exception.APIException;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.Library;
import com.adityachandel.booklore.model.dto.request.TaskCreateRequest;
import com.adityachandel.booklore.model.dto.response.TaskCreateResponse;
import com.adityachandel.booklore.model.enums.TaskType;
import com.adityachandel.booklore.service.library.LibraryService;
import com.adityachandel.booklore.task.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LibraryScanTaskTest {

    @Mock
    private LibraryService libraryService;

    @InjectMocks
    private LibraryScanTask libraryScanTask;

    private BookLoreUser user;
    private TaskCreateRequest request;

    @BeforeEach
    void setUp() {
        user = BookLoreUser.builder()
                .permissions(new BookLoreUser.UserPermissions())
                .build();
        request = new TaskCreateRequest();
    }

    @Test
    void validatePermissions_shouldThrowException_whenUserCannotAccessTaskManager() {
        user.getPermissions().setCanAccessTaskManager(false);
        assertThrows(APIException.class, () -> libraryScanTask.validatePermissions(user, request));
    }

    @Test
    void validatePermissions_shouldPass_whenUserCanAccessTaskManager() {
        user.getPermissions().setCanAccessTaskManager(true);
        assertDoesNotThrow(() -> libraryScanTask.validatePermissions(user, request));
    }

    @Test
    void execute_shouldScanAllLibraries() {
        Library lib1 = Library.builder().id(1L).name("Lib1").build();
        Library lib2 = Library.builder().id(2L).name("Lib2").build();
        when(libraryService.getAllLibraries()).thenReturn(List.of(lib1, lib2));

        TaskCreateResponse response = libraryScanTask.execute(request);

        assertEquals(TaskType.SYNC_LIBRARY_FILES, response.getTaskType());
        assertEquals(TaskStatus.COMPLETED, response.getStatus());
        verify(libraryService).rescanLibrary(1L);
        verify(libraryService).rescanLibrary(2L);
    }

    @Test
    void execute_shouldContinue_whenOneLibraryScanFails() {
        Library lib1 = Library.builder().id(1L).name("Lib1").build();
        Library lib2 = Library.builder().id(2L).name("Lib2").build();
        when(libraryService.getAllLibraries()).thenReturn(List.of(lib1, lib2));
        
        doThrow(new RuntimeException("Scan failed")).when(libraryService).rescanLibrary(1L);

        TaskCreateResponse response = libraryScanTask.execute(request);

        assertEquals(TaskStatus.COMPLETED, response.getStatus());
        verify(libraryService).rescanLibrary(1L);
        verify(libraryService).rescanLibrary(2L);
    }

    @Test
    void execute_shouldReturnFailed_whenFatalErrorOccurs() {
        when(libraryService.getAllLibraries()).thenThrow(new RuntimeException("Fatal error"));

        TaskCreateResponse response = libraryScanTask.execute(request);

        assertEquals(TaskStatus.FAILED, response.getStatus());
    }
}
