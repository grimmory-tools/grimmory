package org.booklore.controller;

import org.booklore.model.dto.Annotation;
import org.booklore.model.dto.CreateAnnotationRequest;
import org.booklore.model.dto.UpdateAnnotationRequest;
import org.booklore.service.book.AnnotationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/annotations")
@Tag(name = "Annotations", description = "Endpoints for managing book annotations (highlights, underlines, etc.)")
public class AnnotationController {

    private final AnnotationService annotationService;

    @Operation(summary = "Get annotations for a book", description = "Retrieve all annotations for a specific book.")
    @ApiResponse(responseCode = "200", description = "Annotations returned successfully")
    @GetMapping("/book/{bookId}")
    public List<Annotation> getAnnotationsForBook(
            @Parameter(description = "ID of the book") @PathVariable Long bookId) {
        return annotationService.getAnnotationsForBook(bookId);
    }

    @Operation(summary = "Get a specific annotation", description = "Retrieve a specific annotation by its ID.")
    @ApiResponse(responseCode = "200", description = "Annotation returned successfully")
    @GetMapping("/{annotationId}")
    public Annotation getAnnotationById(
            @Parameter(description = "ID of the annotation") @PathVariable Long annotationId) {
        return annotationService.getAnnotationById(annotationId);
    }

    @Operation(summary = "Create an annotation", description = "Create a new annotation (highlight, underline, etc.) for a book.")
    @ApiResponse(responseCode = "200", description = "Annotation created successfully")
    @PostMapping
    public Annotation createAnnotation(
            @Parameter(description = "Annotation creation request") @Valid @RequestBody CreateAnnotationRequest request) {
        return annotationService.createAnnotation(request);
    }

    @Operation(summary = "Update an annotation", description = "Update an existing annotation's properties (color, style, note).")
    @ApiResponse(responseCode = "200", description = "Annotation updated successfully")
    @PutMapping("/{annotationId}")
    public Annotation updateAnnotation(
            @Parameter(description = "ID of the annotation to update") @PathVariable Long annotationId,
            @Parameter(description = "Annotation update request") @Valid @RequestBody UpdateAnnotationRequest request) {
        return annotationService.updateAnnotation(annotationId, request);
    }

    @Operation(summary = "Delete an annotation", description = "Delete a specific annotation by its ID.")
    @ApiResponse(responseCode = "204", description = "Annotation deleted successfully")
    @DeleteMapping("/{annotationId}")
    public ResponseEntity<Void> deleteAnnotation(
            @Parameter(description = "ID of the annotation to delete") @PathVariable Long annotationId) {
        annotationService.deleteAnnotation(annotationId);
        return ResponseEntity.noContent().build();
    }
}
