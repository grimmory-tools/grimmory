package org.booklore.controller;

import org.booklore.config.security.annotation.CheckBookAccess;
import org.booklore.model.dto.response.AudiobookInfo;
import org.booklore.service.reader.AudiobookReaderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.util.FileUtils;
import org.booklore.util.MimeDetector;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;

@Slf4j
@RestController
@RequestMapping("/api/v1/audiobooks")
@RequiredArgsConstructor
@Tag(name = "Audiobook Reader", description = "Endpoints for streaming audiobooks with HTTP Range support")
public class AudiobookReaderController {

    private final AudiobookReaderService audiobookReaderService;

    @Operation(summary = "Get audiobook info",
            description = "Retrieve metadata including duration, chapters/tracks, and audio details for an audiobook.")
    @ApiResponse(responseCode = "200", description = "Audiobook info returned successfully")
    @CheckBookAccess(bookIdParam = "bookId")
    @GetMapping("/{bookId}/info")
    public ResponseEntity<AudiobookInfo> getAudiobookInfo(
            @Parameter(description = "ID of the book") @PathVariable Long bookId,
            @Parameter(description = "Optional book type for alternative format (e.g., AUDIOBOOK)") @RequestParam(required = false) String bookType) {
        return ResponseEntity.ok(audiobookReaderService.getAudiobookInfo(bookId, bookType));
    }

    @Operation(summary = "Stream audiobook audio",
            description = "Stream the audiobook audio file with HTTP Range support for seeking. " +
                    "Uses token query parameter for authentication to support HTML5 audio element.")
    @ApiResponse(responseCode = "200", description = "Full audio file returned")
    @ApiResponse(responseCode = "206", description = "Partial content returned (range request)")
    @ApiResponse(responseCode = "416", description = "Range not satisfiable")
    @CheckBookAccess(bookIdParam = "bookId")
    @GetMapping("/{bookId}/stream")
    public ResponseEntity<? extends Resource> streamAudiobook(
            @Parameter(description = "ID of the book") @PathVariable Long bookId,
            @Parameter(description = "Optional book type for alternative format") @RequestParam(required = false) String bookType,
            @Parameter(description = "Track index for folder-based audiobooks (0-indexed)") @RequestParam(required = false) Integer trackIndex) {

        Path audioPath = audiobookReaderService.getAudioFilePath(bookId, bookType, trackIndex);
        return getFile(audioPath);
    }

    @Operation(summary = "Stream specific track", description = "Stream a specific track from a folder-based audiobook.")
    @ApiResponse(responseCode = "200", description = "Full track file returned")
    @ApiResponse(responseCode = "206", description = "Partial content returned (range request)")
    @ApiResponse(responseCode = "416", description = "Range not satisfiable")
    @CheckBookAccess(bookIdParam = "bookId")
    @GetMapping("/{bookId}/track/{trackIndex}/stream")
    public ResponseEntity<? extends Resource> streamTrack(
            @Parameter(description = "ID of the book") @PathVariable Long bookId,
            @Parameter(description = "Track index (0-indexed)") @PathVariable Integer trackIndex,
            @Parameter(description = "Optional book type for alternative format") @RequestParam(required = false) String bookType) {

        Path audioPath = audiobookReaderService.getAudioFilePath(bookId, bookType, trackIndex);
        return getFile(audioPath);
    }

    @Operation(summary = "Get embedded cover art",
            description = "Extract and return the embedded cover art from the audiobook file. " +
                    "Uses token query parameter for authentication.")
    @ApiResponse(responseCode = "200", description = "Cover art returned successfully")
    @ApiResponse(responseCode = "404", description = "No embedded cover art found")
    @CheckBookAccess(bookIdParam = "bookId")
    @GetMapping("/{bookId}/cover")
    public ResponseEntity<byte[]> getEmbeddedCover(
            @Parameter(description = "ID of the book") @PathVariable Long bookId,
            @Parameter(description = "Optional book type for alternative format") @RequestParam(required = false) String bookType) {

        byte[] coverData = audiobookReaderService.getEmbeddedCoverArt(bookId, bookType);
        if (coverData == null) {
            return ResponseEntity.notFound().build();
        }

        String mimeType = audiobookReaderService.getCoverArtMimeType(bookId, bookType);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(mimeType));
        headers.setContentLength(coverData.length);
        headers.setCacheControl("public, max-age=86400");

        return new ResponseEntity<>(coverData, headers, HttpStatus.OK);
    }

    private ResponseEntity<FileSystemResource> getFile(Path filePath) {
        Long lastModified = FileUtils.getFileLastModified(filePath);
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();

        if (lastModified != null) {
            builder.lastModified(lastModified);
        }

        return builder
                .cacheControl(CacheControl.noCache().cachePrivate())
                .contentType(MediaType.parseMediaType(MimeDetector.detectSafe(filePath)))
                .body(new FileSystemResource(filePath));
    }
}
