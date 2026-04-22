package org.booklore.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.booklore.model.dto.response.AuditLogDto;
import org.booklore.model.enums.AuditAction;
import org.booklore.service.audit.AuditService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/audit-logs")
@Tag(name = "Audit Logs", description = "Endpoints for viewing audit logs")
public class AuditLogController {

    private final AuditService auditService;

    @Operation(summary = "Get audit logs", description = "Retrieve paginated audit logs with optional filters. Requires admin.")
    @ApiResponse(responseCode = "200", description = "Audit logs returned successfully")
    @GetMapping
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<Page<AuditLogDto>> getAuditLogs(
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "25") int size,
            @Parameter(description = "Filter by action") @RequestParam(required = false) AuditAction action,
            @Parameter(description = "Filter by user ID") @RequestParam(required = false) Long userId,
            @Parameter(description = "Filter by username") @RequestParam(required = false) String username,
            @Parameter(description = "Filter from date") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "Filter to date") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        Page<AuditLogDto> logs;
        if (action == null && userId == null && username == null && from == null && to == null) {
            logs = auditService.getAuditLogs(PageRequest.of(page, size));
        } else {
            logs = auditService.getAuditLogs(action, userId, username, from, to, PageRequest.of(page, size));
        }
        return ResponseEntity.ok(logs);
    }

    @Operation(summary = "Get distinct usernames", description = "Retrieve distinct usernames from audit logs for filtering.")
    @GetMapping("/usernames")
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<List<String>> getDistinctUsernames() {
        return ResponseEntity.ok(auditService.getDistinctUsernames());
    }
}
