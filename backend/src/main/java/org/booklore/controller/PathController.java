package org.booklore.controller;

import org.booklore.service.file.PathService;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Parameter;

import java.util.List;

@RestController
@RequestMapping("/api/v1/path")
@AllArgsConstructor
@Tag(name = "Library Paths", description = "Endpoints for retrieving folder paths within libraries")
public class PathController {

    private final PathService pathService;

    @Operation(summary = "Get folders at a path", description = "Retrieve a list of folders at a given path. Requires admin or library manipulation permission.")
    @ApiResponse(responseCode = "200", description = "Folders returned successfully")
    @GetMapping
    @PreAuthorize("@securityUtil.canManageLibrary() or @securityUtil.isAdmin()")
    public List<String> getFolders(@Parameter(description = "Path to list folders at") @RequestParam String path) {
        return pathService.getFoldersAtPath(path);
    }
}
