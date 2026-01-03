package com.adityachandel.booklore.task.tasks;

import com.adityachandel.booklore.exception.APIException;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.Library;
import com.adityachandel.booklore.model.dto.request.TaskCreateRequest;
import com.adityachandel.booklore.model.dto.response.TaskCreateResponse;
import com.adityachandel.booklore.service.library.LibraryRescanHelper;
import com.adityachandel.booklore.service.library.LibraryService;
import com.adityachandel.booklore.task.TaskCancellationManager;
import com.adityachandel.booklore.task.options.LibraryRescanOptions;
import com.adityachandel.booklore.task.options.RescanLibraryContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.dao.InvalidDataAccessApiUsageException;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LibraryRescanTaskTest {

    @Mock
    private LibraryService libraryService;
    @Mock
    private LibraryRescanHelper libraryRescanHelper;
    @Mock
    private TaskCancellationManager cancellationManager;

    @InjectMocks
    private LibraryRescanTask libraryRescanTask;

    private BookLoreUser user;
    private TaskCreateRequest request;
    private LibraryRescanOptions options;

    @BeforeEach
    void setUp() {
        user = BookLoreUser.builder()
                .permissions(new BookLoreUser.UserPermissions())
                .build();
        request = mock(TaskCreateRequest.class);
        
        // Lenient stubs because not all tests use them
        lenient().when(request.getTaskId()).thenReturn("task-123");
        options = LibraryRescanOptions.builder().build();
        lenient().when(request.getOptions(LibraryRescanOptions.class)).thenReturn(options);
    }

    @Test
    void validatePermissions_shouldThrowException_whenUserCannotAccessTaskManager() {
        user.getPermissions().setCanAccessTaskManager(false);
        assertThrows(APIException.class, () -> libraryRescanTask.validatePermissions(user, request));
    }

    @Test
    void validatePermissions_shouldPass_whenUserCanAccessTaskManager() {
        user.getPermissions().setCanAccessTaskManager(true);
        assertDoesNotThrow(() -> libraryRescanTask.validatePermissions(user, request));
    }

    @Test
    void execute_shouldRescanAllLibraries() {
        Library lib1 = Library.builder().id(1L).name("Lib1").build();
        Library lib2 = Library.builder().id(2L).name("Lib2").build();
        when(libraryService.getAllLibraries()).thenReturn(List.of(lib1, lib2));
        when(cancellationManager.isTaskCancelled(anyString())).thenReturn(false);

        libraryRescanTask.execute(request);

        verify(libraryRescanHelper).handleRescanOptions(argThat(ctx -> ctx.getLibraryId().equals(1L)), eq("task-123"));
        verify(libraryRescanHelper).handleRescanOptions(argThat(ctx -> ctx.getLibraryId().equals(2L)), eq("task-123"));
    }

    @Test
    void execute_shouldStop_whenCancelled() {
        Library lib1 = Library.builder().id(1L).name("Lib1").build();
        Library lib2 = Library.builder().id(2L).name("Lib2").build();
        when(libraryService.getAllLibraries()).thenReturn(List.of(lib1, lib2));
        
        when(cancellationManager.isTaskCancelled("task-123")).thenReturn(true);

        libraryRescanTask.execute(request);

        verify(libraryRescanHelper, never()).handleRescanOptions(any(), any());
    }

    @Test
    void execute_shouldContinue_whenDataAccessExceptionOccurs() {
        Library lib1 = Library.builder().id(1L).name("Lib1").build();
        when(libraryService.getAllLibraries()).thenReturn(List.of(lib1));
        
        doThrow(new InvalidDataAccessApiUsageException("DB Error"))
                .when(libraryRescanHelper).handleRescanOptions(any(), any());

        assertDoesNotThrow(() -> libraryRescanTask.execute(request));
        
        verify(libraryRescanHelper).handleRescanOptions(any(), any());
    }
}
