package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.BookdropFile;
import com.adityachandel.booklore.model.dto.BookdropFileNotification;
import com.adityachandel.booklore.model.dto.request.BookdropBulkEditRequest;
import com.adityachandel.booklore.model.dto.request.BookdropFinalizeRequest;
import com.adityachandel.booklore.model.dto.request.BookdropPatternExtractRequest;
import com.adityachandel.booklore.model.dto.request.BookdropSelectionRequest;
import com.adityachandel.booklore.model.dto.response.BookdropBulkEditResult;
import com.adityachandel.booklore.model.dto.response.BookdropFinalizeResult;
import com.adityachandel.booklore.model.dto.response.BookdropPatternExtractResult;
import com.adityachandel.booklore.service.bookdrop.BookDropService;
import com.adityachandel.booklore.service.bookdrop.BookdropBulkEditService;
import com.adityachandel.booklore.service.bookdrop.BookdropMonitoringService;
import com.adityachandel.booklore.service.bookdrop.FilenamePatternExtractor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Bookdrop", description = "Endpoints for managing bookdrop files and imports")
@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/bookdrop")
public class BookdropFileController {

    private final BookDropService bookDropService;
    private final BookdropMonitoringService monitoringService;
    private final FilenamePatternExtractor filenamePatternExtractor;
    private final BookdropBulkEditService bookdropBulkEditService;

    @Operation(summary = "Get bookdrop notification summary", description = "Retrieve a summary of bookdrop file notifications.")
    @ApiResponse(responseCode = "200", description = "Notification summary returned successfully")
    @GetMapping("/notification")
    @PreAuthorize("@securityUtil.canAccessBookdrop() or @securityUtil.isAdmin()")
    public BookdropFileNotification getSummary() {
        return bookDropService.getFileNotificationSummary();
    }

    @Operation(summary = "Get bookdrop files by status", description = "Retrieve a paginated list of bookdrop files filtered by status.")
    @ApiResponse(responseCode = "200", description = "Bookdrop files returned successfully")
    @GetMapping("/files")
    @PreAuthorize("@securityUtil.canAccessBookdrop() or @securityUtil.isAdmin()")
    public Page<BookdropFile> getFilesByStatus(
            @Parameter(description = "Status to filter files by") @RequestParam(required = false) String status,
            Pageable pageable) {
        return bookDropService.getFilesByStatus(status, pageable);
    }

    @Operation(summary = "Discard selected bookdrop files", description = "Discard selected bookdrop files based on selection criteria.")
    @ApiResponse(responseCode = "200", description = "Files discarded successfully")
    @PostMapping("/files/discard")
    @PreAuthorize("@securityUtil.canAccessBookdrop() or @securityUtil.isAdmin()")
    public ResponseEntity<Void> discardSelectedFiles(
            @Parameter(description = "Selection request for files to discard") @RequestBody BookdropSelectionRequest request) {
        bookDropService.discardSelectedFiles(request.isSelectAll(), request.getExcludedIds(), request.getSelectedIds());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Finalize bookdrop import", description = "Finalize the import of selected bookdrop files.")
    @ApiResponse(responseCode = "200", description = "Import finalized successfully")
    @PostMapping("/imports/finalize")
    @PreAuthorize("@securityUtil.canAccessBookdrop() or @securityUtil.isAdmin()")
    public ResponseEntity<BookdropFinalizeResult> finalizeImport(
            @Parameter(description = "Finalize import request") @RequestBody BookdropFinalizeRequest request) {
        BookdropFinalizeResult result = bookDropService.finalizeImport(request);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Rescan bookdrop folder", description = "Trigger a rescan of the bookdrop folder for new files.")
    @ApiResponse(responseCode = "200", description = "Bookdrop folder rescanned successfully")
    @PostMapping("/rescan")
    @PreAuthorize("@securityUtil.canAccessBookdrop() or @securityUtil.isAdmin()")
    public ResponseEntity<Void> rescanBookdrop() {
        monitoringService.rescanBookdropFolder();
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Extract metadata from filenames using pattern", description = "Parse filenames of selected files using a pattern to extract metadata fields.")
    @ApiResponse(responseCode = "200", description = "Pattern extraction completed")
    @PostMapping("/files/extract-pattern")
    @PreAuthorize("@securityUtil.canAccessBookdrop() or @securityUtil.isAdmin()")
    public ResponseEntity<BookdropPatternExtractResult> extractFromPattern(
            @Parameter(description = "Pattern extraction request") @Valid @RequestBody BookdropPatternExtractRequest request) {
        BookdropPatternExtractResult result = filenamePatternExtractor.bulkExtract(request);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Bulk edit metadata for selected files", description = "Apply metadata changes to multiple selected files at once.")
    @ApiResponse(responseCode = "200", description = "Bulk edit completed")
    @PostMapping("/files/bulk-edit")
    @PreAuthorize("@securityUtil.canAccessBookdrop() or @securityUtil.isAdmin()")
    public ResponseEntity<BookdropBulkEditResult> bulkEditMetadata(
            @Parameter(description = "Bulk edit request") @Valid @RequestBody BookdropBulkEditRequest request) {
        BookdropBulkEditResult result = bookdropBulkEditService.bulkEdit(request);
        return ResponseEntity.ok(result);
    }
}