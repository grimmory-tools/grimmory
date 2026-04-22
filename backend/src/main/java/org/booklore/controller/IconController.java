package org.booklore.controller;

import org.booklore.model.dto.request.SvgIconBatchRequest;
import org.booklore.model.dto.request.SvgIconCreateRequest;
import org.booklore.model.dto.response.SvgIconBatchResponse;
import org.booklore.service.IconService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Icons", description = "Endpoints for managing SVG icons")
@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/icons")
public class IconController {

    private final IconService iconService;

    @Operation(summary = "Save an SVG icon", description = "Saves an SVG icon to the system.")
    @ApiResponse(responseCode = "200", description = "SVG icon saved successfully")
    @PostMapping
    @PreAuthorize("@securityUtil.canManageIcons() or @securityUtil.isAdmin()")
    public ResponseEntity<?> saveSvgIcon(@Valid @RequestBody SvgIconCreateRequest svgIconCreateRequest) {
        iconService.saveSvgIcon(svgIconCreateRequest);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Save multiple SVG icons", description = "Saves multiple SVG icons to the system in batch.")
    @ApiResponse(responseCode = "200", description = "Batch save completed with detailed results")
    @PostMapping("/batch")
    @PreAuthorize("@securityUtil.canManageIcons() or @securityUtil.isAdmin()")
    public ResponseEntity<SvgIconBatchResponse> saveBatchSvgIcons(@Valid @RequestBody SvgIconBatchRequest request) {
        SvgIconBatchResponse response = iconService.saveBatchSvgIcons(request.getIcons());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get SVG icon content", description = "Retrieve the SVG content of an icon by its name.")
    @ApiResponse(responseCode = "200", description = "SVG icon content retrieved successfully")
    @GetMapping("/{svgName}/content")
    public ResponseEntity<String> getSvgIconContent(@Parameter(description = "SVG icon name") @PathVariable String svgName) {
        String svgContent = iconService.getSvgIcon(svgName);
        return ResponseEntity.ok()
                .header("Content-Type", "image/svg+xml")
                .body(svgContent);
    }

    @Operation(summary = "Get paginated icon names", description = "Retrieve a paginated list of icon names (default 50 per page).")
    @ApiResponse(responseCode = "200", description = "Icon names retrieved successfully")
    @GetMapping
    public ResponseEntity<Page<String>> getIconNames(
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "50") int size) {
        Page<String> response = iconService.getIconNames(page, size);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Delete an SVG icon", description = "Deletes an SVG icon by its name.")
    @ApiResponse(responseCode = "200", description = "SVG icon deleted successfully")
    @DeleteMapping("/{svgName}")
    @PreAuthorize("@securityUtil.canManageIcons() or @securityUtil.isAdmin()")
    public ResponseEntity<?> deleteSvgIcon(@Parameter(description = "SVG icon name") @PathVariable String svgName) {
        iconService.deleteSvgIcon(svgName);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Get all icon contents", description = "Retrieve all SVG icons as a map of icon names to their content.")
    @ApiResponse(responseCode = "200", description = "All icon contents retrieved successfully")
    @GetMapping("/all/content")
    public ResponseEntity<Map<String, String>> getAllIconsContent() {
        Map<String, String> iconsMap = iconService.getAllIconsContent();
        return ResponseEntity.ok(iconsMap);
    }
}
