package org.booklore.controller;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.CustomFontDto;
import org.booklore.model.enums.FontFormat;
import org.booklore.service.customfont.CustomFontService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "Custom Fonts", description = "Endpoints for managing custom fonts for EPUB reader")
@RestController
@RequestMapping("/api/v1/custom-fonts")
@RequiredArgsConstructor
public class CustomFontController {

    private final CustomFontService customFontService;
    private final AuthenticationService authenticationService;

    @Operation(summary = "Upload a custom font", description = "Upload a custom font file (.ttf, .otf, .woff, .woff2) for the authenticated user")
    @ApiResponse(responseCode = "200", description = "Font uploaded successfully")
    @ApiResponse(responseCode = "400", description = "Invalid file or quota exceeded")
    @PostMapping("/upload")
    @PreAuthorize("@securityUtil.canManageFonts() or @securityUtil.isAdmin()")
    public ResponseEntity<CustomFontDto> uploadFont(
            @Parameter(description = "Font file (.ttf, .otf, .woff, .woff2)") @RequestParam("file") MultipartFile file,
            @Parameter(description = "Font display name") @RequestParam(value = "fontName", required = false) String fontName) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        CustomFontDto fontDto = customFontService.uploadFont(file, fontName, user.getId());
        return ResponseEntity.ok(fontDto);
    }

    @Operation(summary = "Get all user's custom fonts", description = "Retrieve all custom fonts for the authenticated user")
    @ApiResponse(responseCode = "200", description = "Fonts retrieved successfully")
    @GetMapping
    @PreAuthorize("@securityUtil.canManageFonts() or @securityUtil.isAdmin()")
    public ResponseEntity<List<CustomFontDto>> getUserFonts() {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        List<CustomFontDto> fonts = customFontService.getUserFonts(user.getId());
        return ResponseEntity.ok(fonts);
    }

    @Operation(summary = "Delete a custom font", description = "Delete a custom font file and database record")
    @ApiResponse(responseCode = "200", description = "Font deleted successfully")
    @ApiResponse(responseCode = "404", description = "Font not found or access denied")
    @DeleteMapping("/{fontId}")
    @PreAuthorize("@securityUtil.canManageFonts() or @securityUtil.isAdmin()")
    public ResponseEntity<Void> deleteFont(@PathVariable Long fontId) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        customFontService.deleteFont(fontId, user.getId());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Get font file", description = "Retrieve the font file for use in the browser")
    @ApiResponse(responseCode = "200", description = "Font file retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Font not found or access denied")
    @GetMapping("/{fontId}/file")
    @PreAuthorize("@securityUtil.canManageFonts() or @securityUtil.isAdmin()")
    public ResponseEntity<Resource> getFontFile(@PathVariable Long fontId) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Resource resource = customFontService.getFontFile(fontId, user.getId());
        FontFormat format = customFontService.getFontFormat(fontId, user.getId());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(format.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(resource);
    }
}
