package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.quartz.JobSchedulerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final JobSchedulerService jobSchedulerService;

    @DeleteMapping("/{taskId}")
    public ResponseEntity<?> cancelTask(@PathVariable String taskId) {
        log.info("Received request to cancel task: {}", taskId);
        boolean cancelled = jobSchedulerService.cancelJob(taskId);
        if (cancelled) {
            return ResponseEntity.ok(Map.of("message", "Task cancellation scheduled"));
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to cancel task or task not found"));
        }
    }
}

