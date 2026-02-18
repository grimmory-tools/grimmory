package org.booklore.controller;

import org.booklore.service.book.BookDownloadService;
import org.booklore.service.book.BookService;
import org.booklore.service.opds.OpdsFeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "OPDS", description = "Endpoints for OPDS catalog feeds, book downloads, covers, and search description")
@Slf4j
@RestController
@RequestMapping("/api/v1/opds")
@RequiredArgsConstructor
public class OpdsController {

    private static final String OPDS_CATALOG_MEDIA_TYPE = "application/atom+xml;profile=opds-catalog;kind=navigation;charset=utf-8";
    private static final String OPDS_ACQUISITION_MEDIA_TYPE = "application/atom+xml;profile=opds-catalog;kind=acquisition;charset=utf-8";

    private final OpdsFeedService opdsFeedService;
    private final BookService bookService;
    private final BookDownloadService bookDownloadService;

    @Operation(summary = "Download book file", description = "Download the book file by its ID. Optionally specify a fileId to download a specific format.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Book file downloaded successfully"),
        @ApiResponse(responseCode = "404", description = "Book not found")
    })
    @GetMapping("/{bookId}/download")
    public ResponseEntity<Resource> downloadBook(
            @Parameter(description = "ID of the book to download") @PathVariable("bookId") Long bookId,
            @Parameter(description = "Optional ID of a specific file format to download") @RequestParam(required = false) Long fileId) {
        if (fileId != null) {
            return bookDownloadService.downloadBookFile(bookId, fileId);
        }
        return bookService.downloadBook(bookId);
    }

    @Operation(summary = "Get book cover image", description = "Retrieve the cover image for a book by its ID.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cover image returned successfully"),
        @ApiResponse(responseCode = "404", description = "Book or cover not found")
    })
    @GetMapping("/{bookId}/cover")
    public ResponseEntity<Resource> getBookCover(@Parameter(description = "ID of the book") @PathVariable long bookId) {
        Resource coverImage = bookService.getBookThumbnail(bookId);
        String contentType = "image/jpeg";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + coverImage.getFilename() + "\"")
                .body(coverImage);
    }

    @Operation(summary = "Get OPDS root catalog", description = "Retrieve the OPDS root navigation feed.")
    @ApiResponse(responseCode = "200", description = "Root OPDS catalog returned successfully")
    @GetMapping(produces = OPDS_CATALOG_MEDIA_TYPE)
    public ResponseEntity<String> getRootCatalog(@Parameter(hidden = true) HttpServletRequest request) {
        String feed = opdsFeedService.generateRootNavigation(request);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(OPDS_CATALOG_MEDIA_TYPE))
                .body(feed);
    }

    @Operation(summary = "Get OPDS libraries navigation", description = "Retrieve the OPDS libraries navigation feed.")
    @ApiResponse(responseCode = "200", description = "Libraries navigation feed returned successfully")
    @GetMapping(value = "/libraries", produces = OPDS_CATALOG_MEDIA_TYPE)
    public ResponseEntity<String> getLibrariesNavigation(@Parameter(hidden = true) HttpServletRequest request) {
        String feed = opdsFeedService.generateLibrariesNavigation(request);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(OPDS_CATALOG_MEDIA_TYPE))
                .body(feed);
    }

    @Operation(summary = "Get OPDS shelves navigation", description = "Retrieve the OPDS shelves navigation feed.")
    @ApiResponse(responseCode = "200", description = "Shelves navigation feed returned successfully")
    @GetMapping(value = "/shelves", produces = OPDS_CATALOG_MEDIA_TYPE)
    public ResponseEntity<String> getShelvesNavigation(@Parameter(hidden = true) HttpServletRequest request) {
        String feed = opdsFeedService.generateShelvesNavigation(request);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(OPDS_CATALOG_MEDIA_TYPE))
                .body(feed);
    }

    @Operation(summary = "Get OPDS magic shelves navigation", description = "Retrieve the OPDS magic shelves navigation feed.")
    @ApiResponse(responseCode = "200", description = "Magic shelves navigation feed returned successfully")
    @GetMapping(value = "/magic-shelves", produces = OPDS_CATALOG_MEDIA_TYPE)
    public ResponseEntity<String> getMagicShelvesNavigation(@Parameter(hidden = true) HttpServletRequest request) {
        String feed = opdsFeedService.generateMagicShelvesNavigation(request);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(OPDS_CATALOG_MEDIA_TYPE))
                .body(feed);
    }

    @Operation(summary = "Get OPDS authors navigation", description = "Retrieve the OPDS authors navigation feed.")
    @ApiResponse(responseCode = "200", description = "Authors navigation feed returned successfully")
    @GetMapping(value = "/authors", produces = OPDS_CATALOG_MEDIA_TYPE)
    public ResponseEntity<String> getAuthorsNavigation(@Parameter(hidden = true) HttpServletRequest request) {
        String feed = opdsFeedService.generateAuthorsNavigation(request);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(OPDS_CATALOG_MEDIA_TYPE))
                .body(feed);
    }

    @Operation(summary = "Get OPDS series navigation", description = "Retrieve the OPDS series navigation feed.")
    @ApiResponse(responseCode = "200", description = "Series navigation feed returned successfully")
    @GetMapping(value = "/series", produces = OPDS_CATALOG_MEDIA_TYPE)
    public ResponseEntity<String> getSeriesNavigation(@Parameter(hidden = true) HttpServletRequest request) {
        String feed = opdsFeedService.generateSeriesNavigation(request);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(OPDS_CATALOG_MEDIA_TYPE))
                .body(feed);
    }

    @Operation(summary = "Get library navigation", description = "Retrieve the OPDS navigation feed for a specific library.")
    @ApiResponse(responseCode = "200", description = "Library navigation feed returned successfully")
    @GetMapping(value = "/libraries/{libraryId}", produces = OPDS_CATALOG_MEDIA_TYPE)
    public ResponseEntity<String> getLibraryNavigation(
            @Parameter(description = "ID of the library") @PathVariable Long libraryId,
            @Parameter(hidden = true) HttpServletRequest request) {
        String feed = opdsFeedService.generateLibraryNavigation(libraryId, request);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(OPDS_CATALOG_MEDIA_TYPE))
                .body(feed);
    }

    @Operation(summary = "Get library authors navigation", description = "Retrieve the OPDS authors navigation feed for a specific library.")
    @ApiResponse(responseCode = "200", description = "Library authors navigation feed returned successfully")
    @GetMapping(value = "/libraries/{libraryId}/authors", produces = OPDS_CATALOG_MEDIA_TYPE)
    public ResponseEntity<String> getLibraryAuthorsNavigation(
            @Parameter(description = "ID of the library") @PathVariable Long libraryId,
            @Parameter(hidden = true) HttpServletRequest request) {
        String feed = opdsFeedService.generateLibraryAuthorsNavigation(libraryId, request);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(OPDS_CATALOG_MEDIA_TYPE))
                .body(feed);
    }

    @Operation(summary = "Get library series navigation", description = "Retrieve the OPDS series navigation feed for a specific library.")
    @ApiResponse(responseCode = "200", description = "Library series navigation feed returned successfully")
    @GetMapping(value = "/libraries/{libraryId}/series", produces = OPDS_CATALOG_MEDIA_TYPE)
    public ResponseEntity<String> getLibrarySeriesNavigation(
            @Parameter(description = "ID of the library") @PathVariable Long libraryId,
            @Parameter(hidden = true) HttpServletRequest request) {
        String feed = opdsFeedService.generateLibrarySeriesNavigation(libraryId, request);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(OPDS_CATALOG_MEDIA_TYPE))
                .body(feed);
    }

    @Operation(summary = "Get library recent books", description = "Retrieve the OPDS recent books feed for a specific library.")
    @ApiResponse(responseCode = "200", description = "Library recent books feed returned successfully")
    @GetMapping(value = "/libraries/{libraryId}/recent", produces = OPDS_ACQUISITION_MEDIA_TYPE)
    public ResponseEntity<String> getLibraryRecentBooks(
            @Parameter(description = "ID of the library") @PathVariable Long libraryId,
            @Parameter(hidden = true) HttpServletRequest request) {
        // Create a wrapper request that adds libraryId parameter and forces recent sorting
        HttpServletRequest wrappedRequest = new HttpServletRequestWrapper(request) {
            @Override
            public String getParameter(String name) {
                if ("libraryId".equals(name)) {
                    return String.valueOf(libraryId);
                }
                return super.getParameter(name);
            }
        };
        
        String feed = opdsFeedService.generateRecentFeed(wrappedRequest);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(OPDS_ACQUISITION_MEDIA_TYPE))
                .body(feed);
    }

    @Operation(summary = "Get library catalog", description = "Retrieve the OPDS catalog feed for a specific library.")
    @ApiResponse(responseCode = "200", description = "Library catalog feed returned successfully")
    @GetMapping(value = "/libraries/{libraryId}/catalog", produces = OPDS_ACQUISITION_MEDIA_TYPE)
    public ResponseEntity<String> getLibraryCatalog(
            @Parameter(description = "ID of the library") @PathVariable Long libraryId,
            @Parameter(hidden = true) HttpServletRequest request) {
        // Create a wrapper request that adds libraryId parameter
        HttpServletRequest wrappedRequest = new HttpServletRequestWrapper(request) {
            @Override
            public String getParameter(String name) {
                if ("libraryId".equals(name)) {
                    return String.valueOf(libraryId);
                }
                return super.getParameter(name);
            }
        };
        
        String feed = opdsFeedService.generateCatalogFeed(wrappedRequest);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(OPDS_ACQUISITION_MEDIA_TYPE))
                .body(feed);
    }

    @Operation(summary = "Get OPDS catalog feed", description = "Retrieve the OPDS acquisition catalog feed.")
    @ApiResponse(responseCode = "200", description = "Catalog feed returned successfully")
    @GetMapping(value = "/catalog", produces = OPDS_ACQUISITION_MEDIA_TYPE)
    public ResponseEntity<String> getCatalog(@Parameter(hidden = true) HttpServletRequest request) {
        String feed = opdsFeedService.generateCatalogFeed(request);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(OPDS_ACQUISITION_MEDIA_TYPE))
                .body(feed);
    }

    @Operation(summary = "Get recent books feed", description = "Retrieve the OPDS feed for recently added books.")
    @ApiResponse(responseCode = "200", description = "Recent books feed returned successfully")
    @GetMapping(value = "/recent", produces = OPDS_ACQUISITION_MEDIA_TYPE)
    public ResponseEntity<String> getRecentBooks(@Parameter(hidden = true) HttpServletRequest request) {
        String feed = opdsFeedService.generateRecentFeed(request);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(OPDS_ACQUISITION_MEDIA_TYPE))
                .body(feed);
    }

    @Operation(summary = "Get surprise feed", description = "Retrieve the OPDS feed for surprise/random books.")
    @ApiResponse(responseCode = "200", description = "Surprise feed returned successfully")
    @GetMapping(value = "/surprise", produces = OPDS_ACQUISITION_MEDIA_TYPE)
    public ResponseEntity<String> getSurpriseFeed(@Parameter(hidden = true) HttpServletRequest request) {
        String feed = opdsFeedService.generateSurpriseFeed(request);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(OPDS_ACQUISITION_MEDIA_TYPE))
                .body(feed);
    }

    @Operation(summary = "Get OPDS search description", description = "Retrieve the OpenSearch description document for OPDS search.")
    @ApiResponse(responseCode = "200", description = "OpenSearch description returned successfully")
    @GetMapping(value = "/search.opds", produces = {
            "application/opensearchdescription+xml",
            "application/atom+xml",
            "application/xml",
            "text/xml"
    })
    public ResponseEntity<String> getSearchDescription() {
        String searchDoc = opdsFeedService.getOpenSearchDescription();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/opensearchdescription+xml;charset=utf-8"))
                .body(searchDoc);
    }
}
