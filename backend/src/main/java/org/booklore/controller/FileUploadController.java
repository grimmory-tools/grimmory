package org.booklore.controller;

import org.booklore.model.dto.Book;
import org.booklore.service.upload.FileUploadService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Parameter;

import java.io.IOException;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/files")
@Tag(name = "File Upload", description = "Endpoints for uploading files and books")
public class FileUploadController {

    private final FileUploadService fileUploadService;

    @Operation(summary = "Upload a file", description = "Upload a file to a specific library and path. Requires upload permission or admin.")
    @ApiResponse(responseCode = "204", description = "File uploaded successfully")
    @PreAuthorize("@securityUtil.isAdmin() or @securityUtil.canUpload()")
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<?> uploadFile(
            @Parameter(description = "File to upload") @RequestParam("file") MultipartFile file,
            @Parameter(description = "Library ID") @RequestParam("libraryId") long libraryId,
            @Parameter(description = "Path ID") @RequestParam("pathId") long pathId) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is missing.");
        }
        fileUploadService.uploadFile(file, libraryId, pathId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Upload a book via BookDrop", description = "Upload a book using BookDrop. Requires upload permission or admin.")
    @ApiResponse(responseCode = "200", description = "Book uploaded successfully")
    @PreAuthorize("@securityUtil.isAdmin() or @securityUtil.canUpload()")
    @PostMapping(value = "/upload/bookdrop", consumes = "multipart/form-data")
    public ResponseEntity<Book> uploadFile(
            @Parameter(description = "Book file to upload") @RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is missing.");
        }
        return ResponseEntity.ok(fileUploadService.uploadFileBookDrop(file));
    }
}
