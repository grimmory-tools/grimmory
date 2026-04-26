package org.grimmory.controller;

import org.booklore.config.security.annotation.CheckBookAccess;
import org.booklore.model.dto.response.CbxPageDimension;
import org.booklore.model.dto.response.CbxPageInfo;
import org.booklore.service.reader.CbxReaderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cbx")
@RequiredArgsConstructor
@Tag(name = "CBX Reader", description = "Endpoints for reading CBX format books")
public class CbxReaderController {

    private final CbxReaderService cbxReaderService;

    @Operation(summary = "List pages in a CBX book", description = "Retrieve a list of available page numbers for a CBX book.")
    @ApiResponse(responseCode = "200", description = "Page numbers returned successfully")
    @GetMapping("/{bookId}/pages")
    @CheckBookAccess(bookIdParam = "bookId")
    public List<Integer> listPages(
            @Parameter(description = "ID of the book") @PathVariable Long bookId,
            @Parameter(description = "Optional book type for alternative format (e.g., PDF, CBX)") @RequestParam(required = false) String bookType) {
        return cbxReaderService.getAvailablePages(bookId, bookType);
    }

    @Operation(summary = "Get page info for a CBX book", description = "Retrieve page information including display names for a CBX book.")
    @ApiResponse(responseCode = "200", description = "Page info returned successfully")
    @GetMapping("/{bookId}/page-info")
    @CheckBookAccess(bookIdParam = "bookId")
    public List<CbxPageInfo> getPageInfo(
            @Parameter(description = "ID of the book") @PathVariable Long bookId,
            @Parameter(description = "Optional book type for alternative format (e.g., PDF, CBX)") @RequestParam(required = false) String bookType) {
        return cbxReaderService.getPageInfo(bookId, bookType);
    }

    @Operation(summary = "Get page dimensions for a CBX book", description = "Retrieve width, height, and wide flag for each page in a CBX book.")
    @ApiResponse(responseCode = "200", description = "Page dimensions returned successfully")
    @GetMapping("/{bookId}/page-dimensions")
    @CheckBookAccess(bookIdParam = "bookId")
    public List<CbxPageDimension> getPageDimensions(
            @Parameter(description = "ID of the book") @PathVariable Long bookId,
            @Parameter(description = "Optional book type for alternative format (e.g., PDF, CBX)") @RequestParam(required = false) String bookType) {
        return cbxReaderService.getPageDimensions(bookId, bookType);
    }
}