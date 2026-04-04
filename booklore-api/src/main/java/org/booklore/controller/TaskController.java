package org.booklore.controller;

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
public class TaskController {

    private final TaskService service;
    private final TaskHistoryService taskHistoryService;
    private final TaskCronService taskCronService;

    @GetMapping
    @PreAuthorize("@securityUtil.canAccessTaskManager() or @securityUtil.isAdmin()")
    public ResponseEntity<List<TaskInfo>> getAvailableTasks() {
        List<TaskInfo> taskInfos = service.getAvailableTasks();
        return ResponseEntity.ok(taskInfos);
    }

    @PostMapping("/start")
    @PreAuthorize("@securityUtil.canAccessTaskManager() or @securityUtil.isAdmin()")
    public ResponseEntity<TaskCreateResponse> startTask(@RequestBody TaskCreateRequest request) {
        TaskCreateResponse response = service.runAsUser(request);
        if (response.getStatus() == TaskStatus.ACCEPTED) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{taskId}/cancel")
    @PreAuthorize("@securityUtil.canAccessTaskManager() or @securityUtil.isAdmin()")
    public ResponseEntity<TaskCancelResponse> cancelTask(@PathVariable String taskId) {
        TaskCancelResponse response = service.cancelTask(taskId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/last")
    @PreAuthorize("@securityUtil.canAccessTaskManager() or @securityUtil.isAdmin()")
    public ResponseEntity<TasksHistoryResponse> getLatestTasksForEachType() {
        TasksHistoryResponse response = taskHistoryService.getLatestTasksForEachType();
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{taskType}/cron")
    @PreAuthorize("@securityUtil.canAccessTaskManager() or @securityUtil.isAdmin()")
    public ResponseEntity<CronConfig> patchCronConfig(@PathVariable TaskType taskType, @RequestBody TaskCronConfigRequest request) {
        CronConfig response = taskCronService.patchCronConfig(taskType, request);
        service.rescheduleTask(taskType);
        return ResponseEntity.ok(response);
    }
}
