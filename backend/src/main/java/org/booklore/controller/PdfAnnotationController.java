package org.booklore.controller;

import org.booklore.config.security.annotation.CheckBookAccess;
import org.booklore.service.book.PdfAnnotationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pdf-annotations")
@Tag(name = "PDF Annotations", description = "Endpoints for managing PDF annotation data")
public class PdfAnnotationController {

    private final PdfAnnotationService pdfAnnotationService;

    @Operation(summary = "Get PDF annotations for a book", description = "Retrieve serialized PDF annotations for a specific book.")
    @ApiResponse(responseCode = "200", description = "Annotations returned successfully")
    @ApiResponse(responseCode = "204", description = "No annotations found")
    @GetMapping("/book/{bookId}")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<Map<String, String>> getAnnotations(
            @Parameter(description = "ID of the book") @PathVariable Long bookId) {
        return pdfAnnotationService.getAnnotations(bookId)
                .map(data -> ResponseEntity.ok(Map.of("data", data)))
                .orElse(ResponseEntity.noContent().build());
    }

    @Operation(summary = "Save PDF annotations for a book", description = "Save or update serialized PDF annotations for a specific book.")
    @ApiResponse(responseCode = "204", description = "Annotations saved successfully")
    @PutMapping("/book/{bookId}")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<Void> saveAnnotations(
            @Parameter(description = "ID of the book") @PathVariable Long bookId,
            @RequestBody Map<String, String> body) {
        pdfAnnotationService.saveAnnotations(bookId, body.get("data"));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete PDF annotations for a book", description = "Delete all PDF annotations for a specific book.")
    @ApiResponse(responseCode = "204", description = "Annotations deleted successfully")
    @DeleteMapping("/book/{bookId}")
    @CheckBookAccess(bookIdParam = "bookId")
    public ResponseEntity<Void> deleteAnnotations(
            @Parameter(description = "ID of the book") @PathVariable Long bookId) {
        pdfAnnotationService.deleteAnnotations(bookId);
        return ResponseEntity.noContent().build();
    }
}
