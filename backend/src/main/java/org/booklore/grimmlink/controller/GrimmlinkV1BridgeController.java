package org.booklore.grimmlink.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.booklore.grimmlink.GrimmlinkRoutes;
import org.booklore.grimmlink.service.GrimmlinkPdfBridgeService;
import org.booklore.model.dto.progress.KoreaderProgress;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping(GrimmlinkRoutes.API_PREFIX + "/books/{bookId}")
public class GrimmlinkV1BridgeController {

    private final GrimmlinkPdfBridgeService pdfBridgeService;

    @GetMapping(value = {"/pdf-progress", "/web-progress"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<KoreaderProgress> getPdfProgress(@PathVariable Long bookId) {
        return ResponseEntity.ok(pdfBridgeService.getPdfProgress(bookId));
    }

    @PutMapping(value = {"/pdf-progress", "/web-progress"}, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<KoreaderProgress> updatePdfProgress(@PathVariable Long bookId,
                                                              @Valid @RequestBody KoreaderProgress request) {
        return ResponseEntity.ok(pdfBridgeService.updatePdfProgress(bookId, request));
    }
}
