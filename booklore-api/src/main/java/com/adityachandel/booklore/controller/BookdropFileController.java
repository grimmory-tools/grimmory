package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.BookdropFile;
import com.adityachandel.booklore.model.dto.BookdropFileNotification;
import com.adityachandel.booklore.model.dto.request.BookdropFinalizeRequest;
import com.adityachandel.booklore.model.dto.request.BookdropSelectionRequest;
import com.adityachandel.booklore.model.dto.response.BookdropFinalizeResult;
import com.adityachandel.booklore.service.bookdrop.BookDropService;
import com.adityachandel.booklore.service.bookdrop.BookdropMonitoringService;
import com.adityachandel.booklore.service.monitoring.MonitoringService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/bookdrop")
public class BookdropFileController {

    private final BookDropService bookDropService;
    private final BookdropMonitoringService monitoringService;

    @GetMapping("/notification")
    public BookdropFileNotification getSummary() {
        return bookDropService.getFileNotificationSummary();
    }

    @GetMapping("/files")
    public Page<BookdropFile> getFilesByStatus(@RequestParam(required = false) String status, Pageable pageable) {
        return bookDropService.getFilesByStatus(status, pageable);
    }

    @PostMapping("/files/discard")
    public ResponseEntity<Void> discardSelectedFiles(@RequestBody BookdropSelectionRequest request) {
        bookDropService.discardSelectedFiles(request.isSelectAll(), request.getExcludedIds(), request.getSelectedIds());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/imports/finalize")
    public ResponseEntity<BookdropFinalizeResult> finalizeImport(@RequestBody BookdropFinalizeRequest request) {
        BookdropFinalizeResult result = bookDropService.finalizeImport(request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/rescan")
    public ResponseEntity<Void> rescanBookdrop() {
        monitoringService.rescanBookdropFolder();
        return ResponseEntity.ok().build();
    }
}