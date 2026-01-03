package com.adityachandel.booklore.task.tasks;

import com.adityachandel.booklore.exception.APIException;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.request.TaskCreateRequest;
import com.adityachandel.booklore.model.dto.response.TaskCreateResponse;
import com.adityachandel.booklore.model.enums.TaskType;
import com.adityachandel.booklore.task.TaskStatus;
import com.adityachandel.booklore.util.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClearCbxCacheTaskTest {

    @Mock
    private FileService fileService;

    @InjectMocks
    private ClearCbxCacheTask clearCbxCacheTask;

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
        assertThrows(APIException.class, () -> clearCbxCacheTask.validatePermissions(user, request));
    }

    @Test
    void execute_shouldClearCache() {
        String cachePathStr = "/tmp/cbx-cache";
        when(fileService.getCbxCachePath()).thenReturn(cachePathStr);
        doNothing().when(fileService).clearCacheDirectory(cachePathStr);

        TaskCreateResponse response = clearCbxCacheTask.execute(request);

        assertEquals(TaskType.CLEAR_CBX_CACHE, response.getTaskType());
        assertEquals(TaskStatus.COMPLETED, response.getStatus());
        
        verify(fileService).getCbxCachePath();
        verify(fileService).clearCacheDirectory(cachePathStr);
    }

    @Test
    void execute_shouldHandleException_whenClearCacheFails() {
        String cachePathStr = "/tmp/cbx-cache";
        when(fileService.getCbxCachePath()).thenReturn(cachePathStr);
        doThrow(new RuntimeException("Delete failed")).when(fileService).clearCacheDirectory(cachePathStr);

        assertThrows(RuntimeException.class, () -> clearCbxCacheTask.execute(request));
    }
}
