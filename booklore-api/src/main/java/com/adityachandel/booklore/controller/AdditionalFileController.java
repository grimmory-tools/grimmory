package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.config.security.annotation.CheckBookAccess;
import com.adityachandel.booklore.model.dto.AdditionalFile;
import com.adityachandel.booklore.model.enums.AdditionalFileType;
import com.adityachandel.booklore.service.AdditionalFileService;
import com.adityachandel.booklore.service.upload.FileUploadService;
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
public class AdditionalFileController {

    private final AdditionalFileService additionalFileService;
    private final FileUploadService fileUploadService;

    @GetMapping
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<List<AdditionalFile>> getAdditionalFiles(@PathVariable Long bookId) {
        List<AdditionalFile> files = additionalFileService.getAdditionalFilesByBookId(bookId);
        return ResponseEntity.ok(files);
    }

    @GetMapping(params = "type")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<List<AdditionalFile>> getAdditionalFilesByType(
            @PathVariable Long bookId,
            @RequestParam AdditionalFileType type) {
        List<AdditionalFile> files = additionalFileService.getAdditionalFilesByBookIdAndType(bookId, type);
        return ResponseEntity.ok(files);
    }

    @PostMapping(consumes = "multipart/form-data")
    @CheckBookAccess(bookIdParam = "bookId")
    @PreAuthorize("@securityUtil.canUpload() or @securityUtil.isAdmin()")
    public ResponseEntity<AdditionalFile> uploadAdditionalFile(
            @PathVariable Long bookId,
            @RequestParam("file") MultipartFile file,
            @RequestParam AdditionalFileType additionalFileType,
            @RequestParam(required = false) String description) throws IOException {
        AdditionalFile additionalFile = fileUploadService.uploadAdditionalFile(bookId, file, additionalFileType, description);
        return ResponseEntity.ok(additionalFile);
    }

    @GetMapping("/{fileId}/download")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<Resource> downloadAdditionalFile(
            @PathVariable Long bookId,
            @PathVariable Long fileId) throws IOException {
        return additionalFileService.downloadAdditionalFile(fileId);
    }

    @DeleteMapping("/{fileId}")
    @CheckBookAccess(bookIdParam = "bookId")
    @PreAuthorize("@securityUtil.canDeleteBook() or @securityUtil.isAdmin()")
    public ResponseEntity<Void> deleteAdditionalFile(
            @PathVariable Long bookId,
            @PathVariable Long fileId) throws IOException {
        additionalFileService.deleteAdditionalFile(fileId);
        return ResponseEntity.noContent().build();
    }
}
