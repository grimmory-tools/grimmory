package org.grimmory.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.grimmory.config.security.annotation.CheckBookAccess;
import org.grimmory.model.dto.BookFile;
import org.grimmory.model.dto.request.DetachBookFileRequest;
import org.grimmory.model.dto.response.DetachBookFileResponse;
import org.grimmory.model.enums.BookFileType;
import org.grimmory.service.book.BookFileDetachmentService;
import org.grimmory.service.file.AdditionalFileService;
import org.grimmory.service.upload.FileUploadService;
import lombok.AllArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RequestMapping("/api/v1/books/{bookId}/files")
@RestController
@AllArgsConstructor
@Tag(name = "Book Files", description = "Endpoints for managing additional files attached to books")
public class AdditionalFileController {

    private final AdditionalFileService additionalFileService;
    private final FileUploadService fileUploadService;
    private final BookFileDetachmentService bookFileDetachmentService;

    @Operation(
            summary = "List additional book files",
            description = "Retrieve additional files for a specific book.",
            operationId = "additionalFileGetAdditionalFiles"
    )
    @GetMapping
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<List<BookFile>> getAdditionalFiles(@PathVariable Long bookId) {
        List<BookFile> files = additionalFileService.getAdditionalFilesByBookId(bookId);
        return ResponseEntity.ok(files);
    }

    @Operation(
            summary = "List additional files by type",
            description = "Retrieve additional files for a specific book filtered by whether they are primary book files.",
            operationId = "additionalFileGetFilesByIsBook"
    )
    @GetMapping(params = "isBook")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<List<BookFile>> getFilesByIsBook(
            @PathVariable Long bookId,
            @RequestParam boolean isBook) {
        List<BookFile> files = additionalFileService.getAdditionalFilesByBookIdAndIsBook(bookId, isBook);
        return ResponseEntity.ok(files);
    }

    @Operation(
            summary = "Upload additional book file",
            description = "Upload and attach a new additional file to a specific book.",
            operationId = "additionalFileUploadAdditionalFile"
    )
    @PostMapping(consumes = "multipart/form-data")
    @CheckBookAccess(bookIdParam = "bookId")
    @PreAuthorize("@securityUtil.canUpload() or @securityUtil.isAdmin()")
    public ResponseEntity<BookFile> uploadAdditionalFile(
            @PathVariable Long bookId,
            @RequestParam("file") MultipartFile file,
            @RequestParam boolean isBook,
            @RequestParam(required = false) BookFileType bookType,
            @RequestParam(required = false) String description) {
        BookFile additionalFile = fileUploadService.uploadAdditionalFile(bookId, file, isBook, bookType, description);
        return ResponseEntity.ok(additionalFile);
    }

    @Operation(
            summary = "Download additional book file",
            description = "Download a specific additional file attached to a book.",
            operationId = "additionalFileDownloadAdditionalFile"
    )
    @GetMapping("/{fileId}/download")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<Resource> downloadAdditionalFile(
            @PathVariable Long bookId,
            @PathVariable Long fileId) throws IOException {
        return additionalFileService.downloadAdditionalFile(bookId, fileId);
    }

    @Operation(
            summary = "Delete additional book file",
            description = "Delete a specific additional file attached to a book.",
            operationId = "additionalFileDeleteAdditionalFile"
    )
    @DeleteMapping("/{fileId}")
    @CheckBookAccess(bookIdParam = "bookId")
    @PreAuthorize("@securityUtil.canDeleteBook() or @securityUtil.isAdmin()")
    public ResponseEntity<Void> deleteAdditionalFile(
            @PathVariable Long bookId,
            @PathVariable Long fileId) {
        additionalFileService.deleteAdditionalFile(bookId, fileId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Detach additional book file",
            description = "Detach a specific additional file from a book and optionally copy metadata.",
            operationId = "additionalFileDetachFile"
    )
    @PostMapping("/{fileId}/detach")
    @CheckBookAccess(bookIdParam = "bookId")
    @PreAuthorize("@securityUtil.canManageLibrary() or @securityUtil.isAdmin()")
    public ResponseEntity<DetachBookFileResponse> detachFile(
            @PathVariable Long bookId,
            @PathVariable Long fileId,
            @RequestBody DetachBookFileRequest request) {
        return ResponseEntity.ok(bookFileDetachmentService.detachBookFile(bookId, fileId, request.copyMetadata()));
    }
}
