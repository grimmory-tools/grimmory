package org.booklore.controller;

import org.booklore.model.dto.MetadataBatchProgressNotification;
import org.booklore.model.dto.response.MetadataTaskDetailsResponse;
import org.booklore.service.metadata.MetadataTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/metadata/tasks")
@RequiredArgsConstructor
@Tag(name = "Metadata Tasks", description = "Endpoints for managing metadata batch tasks and proposals")
public class MetadataTaskController {

    private final MetadataTaskService metadataTaskService;

    @Operation(summary = "Get metadata task with proposals", description = "Retrieve a metadata task and its proposals by task ID. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "200", description = "Task details returned successfully")
    @GetMapping("/{taskId}")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    public ResponseEntity<MetadataTaskDetailsResponse> getTaskWithProposals(
            @Parameter(description = "Task ID") @PathVariable String taskId) {
        return metadataTaskService.getTaskWithProposals(taskId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get active metadata tasks", description = "Retrieve all active metadata batch tasks. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "200", description = "Active tasks returned successfully")
    @GetMapping("/active")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    public ResponseEntity<List<MetadataBatchProgressNotification>> getActiveTasks() {
        return ResponseEntity.ok(metadataTaskService.getActiveTasks());
    }

    @Operation(summary = "Delete a metadata task", description = "Delete a metadata task and its proposals by task ID. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "204", description = "Task deleted successfully")
    @DeleteMapping("/{taskId}")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    public ResponseEntity<Void> deleteTask(
            @Parameter(description = "Task ID") @PathVariable String taskId) {
        boolean deleted = metadataTaskService.deleteTaskAndProposals(taskId);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @Operation(summary = "Update proposal status", description = "Update the status of a proposal for a metadata task. Requires metadata edit permission or admin.")
    @ApiResponse(responseCode = "200", description = "Proposal status updated successfully")
    @PostMapping("/{taskId}/proposals/{proposalId}/status")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    public ResponseEntity<Void> updateProposalStatus(
            @Parameter(description = "Task ID") @PathVariable String taskId,
            @Parameter(description = "Proposal ID") @PathVariable Long proposalId,
            @Parameter(description = "New status") @RequestParam String status) {
        boolean updated = metadataTaskService.updateProposalStatus(taskId, proposalId, status);
        return updated ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }
}
