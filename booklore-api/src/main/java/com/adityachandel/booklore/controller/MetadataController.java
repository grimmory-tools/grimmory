package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.config.security.annotation.CheckBookAccess;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.BookMetadataMapper;
import com.adityachandel.booklore.model.MetadataUpdateWrapper;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.CoverImage;
import com.adityachandel.booklore.model.dto.request.*;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.quartz.JobSchedulerService;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.metadata.*;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/books")
@AllArgsConstructor
public class MetadataController {

    private final BookMetadataService bookMetadataService;
    private final BookMetadataUpdater bookMetadataUpdater;
    private final JobSchedulerService jobSchedulerService;
    private final AuthenticationService authenticationService;
    private final BookMetadataMapper bookMetadataMapper;
    private final MetadataMatchService metadataMatchService;
    private final DuckDuckGoCoverService duckDuckGoCoverService;
    private final BookRepository bookRepository;
    private final MetadataManagementService metadataManagementService;

    @PostMapping("/{bookId}/metadata/prospective")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<List<BookMetadata>> getMetadataList(@RequestBody(required = false) FetchMetadataRequest fetchMetadataRequest, @PathVariable Long bookId) {
        return ResponseEntity.ok(bookMetadataService.getProspectiveMetadataListForBookId(bookId, fetchMetadataRequest));
    }

    @PutMapping("/{bookId}/metadata")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<BookMetadata> updateMetadata(@RequestBody MetadataUpdateWrapper metadataUpdateWrapper, @PathVariable long bookId, @RequestParam(defaultValue = "true") boolean mergeCategories) {
        BookEntity bookEntity = bookRepository.findById(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        bookMetadataUpdater.setBookMetadata(bookEntity, metadataUpdateWrapper, true, mergeCategories);
        bookRepository.save(bookEntity);
        BookMetadata bookMetadata = bookMetadataMapper.toBookMetadata(bookEntity.getMetadata(), true);
        return ResponseEntity.ok(bookMetadata);
    }

    @PutMapping("/bulk-edit-metadata")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    public ResponseEntity<List<BookMetadata>> bulkEditMetadata(@RequestBody BulkMetadataUpdateRequest bulkMetadataUpdateRequest, @RequestParam boolean mergeCategories) {
        return ResponseEntity.ok(bookMetadataService.bulkUpdateMetadata(bulkMetadataUpdateRequest, mergeCategories));
    }

    @PutMapping(path = "/metadata/refresh")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    public ResponseEntity<String> scheduleRefreshV2(@Validated @RequestBody MetadataRefreshRequest request) {
        Long userId = authenticationService.getAuthenticatedUser().getId();
        jobSchedulerService.scheduleMetadataRefresh(request, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{bookId}/metadata/cover/upload")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<BookMetadata> uploadCoverFromFile(@PathVariable Long bookId, @RequestParam("file") MultipartFile file) {
        BookMetadata updated = bookMetadataService.updateCoverImageFromFile(bookId, file);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{bookId}/metadata/cover/from-url")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<BookMetadata> uploadCoverFromUrl(@PathVariable Long bookId, @RequestBody Map<String, String> body) {
        BookMetadata updated = bookMetadataService.updateCoverImageFromUrl(bookId, body.get("url"));
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/metadata/toggle-all-lock")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    public ResponseEntity<List<BookMetadata>> toggleAllMetadata(@RequestBody ToggleAllLockRequest request) {
        return ResponseEntity.ok(bookMetadataService.toggleAllLock(request));
    }

    @PutMapping("/metadata/toggle-field-locks")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    public ResponseEntity<List<BookMetadata>> toggleFieldLocks(@RequestBody ToggleFieldLocksRequest request) {
        bookMetadataService.toggleFieldLocks(request.getBookIds(), request.getFieldActions());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/regenerate-covers")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    public void regenerateCovers() {
        bookMetadataService.regenerateCovers();
    }

    @PostMapping("/{bookId}/regenerate-cover")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    @CheckBookAccess(bookIdParam = "bookId")
    public void regenerateCovers(@PathVariable Long bookId) {
        bookMetadataService.regenerateCover(bookId);
    }

    @PostMapping("/metadata/recalculate-match-scores")
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<Void> recalculateMatchScores() {
        metadataMatchService.recalculateAllMatchScores();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{bookId}/metadata/covers")
    public ResponseEntity<List<CoverImage>> getImages(@RequestBody CoverFetchRequest request) {
        return ResponseEntity.ok(duckDuckGoCoverService.getCovers(request));
    }

    @PostMapping("/metadata/manage/consolidate")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    public ResponseEntity<Void> mergeMetadata(@Validated @RequestBody MergeMetadataRequest request) {
        metadataManagementService.consolidateMetadata(request.getMetadataType(), request.getTargetValues(), request.getValuesToMerge());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/metadata/manage/delete")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    public ResponseEntity<Void> deleteMetadata(@Validated @RequestBody DeleteMetadataRequest request) {
        metadataManagementService.deleteMetadata(request.getMetadataType(), request.getValuesToDelete());
        return ResponseEntity.noContent().build();
    }
}