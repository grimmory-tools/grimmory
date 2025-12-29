package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.service.reader.PdfReaderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/pdf")
@RequiredArgsConstructor
@Tag(name = "PDF Reader", description = "Endpoints for reading PDF format books")
public class PdfReaderController {

    private final PdfReaderService pdfReaderService;

    @Operation(summary = "List pages in a PDF book", description = "Retrieve a list of available page numbers for a PDF book.")
    @ApiResponse(responseCode = "200", description = "Page numbers returned successfully")
    @GetMapping("/{bookId}/pages")
    public List<Integer> listPages(
            @Parameter(description = "ID of the book") @PathVariable Long bookId) throws IOException {
        return pdfReaderService.getAvailablePages(bookId);
    }
}