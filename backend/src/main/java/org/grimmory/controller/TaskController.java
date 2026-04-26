package org.grimmory.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.booklore.model.dto.TaskInfo;
import org.booklore.model.dto.request.TaskCreateRequest;
import org.booklore.model.dto.request.TaskCronConfigRequest;
import org.booklore.model.dto.response.CronConfig;
import org.booklore.model.dto.response.TaskCancelResponse;
import org.booklore.model.dto.response.TaskCreateResponse;
import org.booklore.model.dto.response.TasksHistoryResponse;
import org.booklore.model.enums.TaskType;
import org.booklore.service.task.TaskCronService;
import org.booklore.service.task.TaskHistoryService;
import org.booklore.service.task.TaskService;
import org.booklore.task.TaskStatus;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/tasks")
@Tag(name = "Tasks", description = "Endpoints for listing, starting, canceling, and scheduling background tasks")
public class TaskController {

    private final TaskService service;
    private final TaskHistoryService taskHistoryService;
    private final TaskCronService taskCronService;

    @Operation(
            summary = "List available tasks",
            description = "Retrieve all task types available to the current user.",
            operationId = "taskGetAvailableTasks"
    )
    @GetMapping
    @PreAuthorize("@securityUtil.canAccessTaskManager() or @securityUtil.isAdmin()")
    public ResponseEntity<List<TaskInfo>> getAvailableTasks() {
        List<TaskInfo> taskInfos = service.getAvailableTasks();
        return ResponseEntity.ok(taskInfos);
    }

    @Operation(
            summary = "Start task",
            description = "Start a task immediately with the provided task request payload.",
            operationId = "taskStartTask"
    )
    @PostMapping("/start")
    @PreAuthorize("@securityUtil.canAccessTaskManager() or @securityUtil.isAdmin()")
    public ResponseEntity<TaskCreateResponse> startTask(@RequestBody TaskCreateRequest request) {
        TaskCreateResponse response = service.runAsUser(request);
        if (response.getStatus() == TaskStatus.ACCEPTED) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Cancel task",
            description = "Cancel an in-progress task by task ID.",
            operationId = "taskCancelTask"
    )
    @DeleteMapping("/{taskId}/cancel")
    @PreAuthorize("@securityUtil.canAccessTaskManager() or @securityUtil.isAdmin()")
    public ResponseEntity<TaskCancelResponse> cancelTask(@PathVariable String taskId) {
        TaskCancelResponse response = service.cancelTask(taskId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get latest tasks by type",
            description = "Retrieve the latest task execution entry for each task type.",
            operationId = "taskGetLatestTasksForEachType"
    )
    @GetMapping("/last")
    @PreAuthorize("@securityUtil.canAccessTaskManager() or @securityUtil.isAdmin()")
    public ResponseEntity<TasksHistoryResponse> getLatestTasksForEachType() {
        TasksHistoryResponse response = taskHistoryService.getLatestTasksForEachType();
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Update task cron configuration",
            description = "Patch cron schedule configuration for a task type and reschedule execution.",
            operationId = "taskPatchCronConfig"
    )
    @PatchMapping("/{taskType}/cron")
    @PreAuthorize("@securityUtil.canAccessTaskManager() or @securityUtil.isAdmin()")
    public ResponseEntity<CronConfig> patchCronConfig(@PathVariable TaskType taskType, @RequestBody TaskCronConfigRequest request) {
        CronConfig response = taskCronService.patchCronConfig(taskType, request);
        service.rescheduleTask(taskType);
        return ResponseEntity.ok(response);
    }
}
