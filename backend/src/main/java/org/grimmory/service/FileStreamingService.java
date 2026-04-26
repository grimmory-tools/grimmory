package org.grimmory.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.ApiError;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

@Slf4j
@Service
public class FileStreamingService {

    private static final DateTimeFormatter HTTP_DATE_FORMAT = DateTimeFormatter
            .ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
            .withZone(ZoneId.of("GMT"));

    /**
     * Streams a file with HTTP Range support for seeking.
     * Uses Java NIO FileChannel for zero-copy transfer (sendfile) where supported.
     * Supports conditional requests via ETag / If-None-Match and If-Range (RFC 7233).
     */
    public void streamWithRangeSupport(
            Path filePath,
            String contentType,
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        // Single syscall: existence check + size + last-modified
        BasicFileAttributes attrs;
        try {
            attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
        } catch (NoSuchFileException _) {
            throw ApiError.FILE_NOT_FOUND.createException(filePath.toString());
        }

        long fileSize = attrs.size();
        Instant lastModified = attrs.lastModifiedTime().toInstant();
        String etag = generateETag(fileSize, lastModified);
        String rangeHeader = request.getHeader("Range");

        // Standard headers for media streaming
        response.setHeader("Accept-Ranges", "bytes");
        response.setContentType(contentType);
        response.setHeader("ETag", etag);
        response.setDateHeader("Last-Modified", lastModified.toEpochMilli());
        // Allow caching with mandatory revalidation via ETag eliminates
        // redundant byte transfers on seeks while keeping access-control checks.
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Content-Disposition", "inline");

        // Conditional: If-None-Match, 304
        String ifNoneMatch = request.getHeader("If-None-Match");
        if (evaluateIfNoneMatch(ifNoneMatch, etag)) {
            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            return;
        }

        // HEAD request 200 with Content-Length, no body
        if ("HEAD".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentLengthLong(fileSize);
            return;
        }

        try (var fileChannel = FileChannel.open(filePath, StandardOpenOption.READ)) {

            String ifRange = request.getHeader("If-Range");
            if (rangeHeader != null && ifRange != null && !validateIfRange(ifRange, etag, lastModified)) {
                // If-Range failed: return full file
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentLengthLong(fileSize);
                transferFile(fileChannel, 0, fileSize, response.getOutputStream());
                return;
            }

            // NO RANGE
            if (rangeHeader == null) {
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentLengthLong(fileSize);
                transferFile(fileChannel, 0, fileSize, response.getOutputStream());
                return;
            }

            // RANGE
            Range range = parseRange(rangeHeader, fileSize);
            if (range == null) {
                response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                response.setHeader("Content-Range", "bytes */" + fileSize);
                return;
            }

            long length = range.end - range.start + 1;
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            response.setHeader("Content-Range", "bytes " + range.start + "-" + range.end + "/" + fileSize);
            response.setContentLengthLong(length);

            transferFile(fileChannel, range.start, length, response.getOutputStream());

        } catch (NoSuchFileException _) {
            throw ApiError.FILE_NOT_FOUND.createException(filePath.toString());
        } catch (IOException e) {
            if (isClientDisconnect(e)) {
                log.debug("Client disconnected during streaming: {}", e.getMessage());
            } else {
                log.error("Error during file streaming: {}", filePath, e);
                if (!response.isCommitted()) {
                    throw ApiError.INTERNAL_SERVER_ERROR.createException("Streaming error: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Validates the If-Range header against current ETag and lastModified.
     * RFC 7233: If-Range can be a strong ETag OR an HTTP-date.
     */
    private boolean validateIfRange(String ifRange, String etag, Instant lastModified) {
        if (!ifRange.isEmpty() && ifRange.charAt(0) == '\"') {
            // ETag comparison (Strong match required)
            return etag.equals(ifRange);
        } else {
            // HTTP-date comparison
            try {
                Instant ifRangeDate = Instant.from(HTTP_DATE_FORMAT.parse(ifRange));
                // Exact match on seconds required by RFC
                return lastModified.getEpochSecond() == ifRangeDate.getEpochSecond();
            } catch (DateTimeParseException e) {
                log.trace("Failed to parse If-Range date: {}", ifRange);
                return false;
            }
        }
    }

    /**
     * Evaluates the If-None-Match header against the current ETag.
     * RFC 7232: If-None-Match can be "*", a single entity-tag, or a list of them.
     */
    private boolean evaluateIfNoneMatch(String ifNoneMatch, String currentEtag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }

        String trimmedValue = ifNoneMatch.trim();
        if ("*".equals(trimmedValue)) {
            return true;
        }

        // Split by comma. Entity-tags are [weak] opaque-tag.
        String[] parts = trimmedValue.split(",");
        for (String part : parts) {
            String tag = part.trim();
            if (tag.isEmpty()) {
                continue;
            }
            if (isWeakMatch(tag, currentEtag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Weak comparison of two ETags.
     * RFC 7232 Section 2.3.2: Two entity-tags are a weak match if their opaque-tags are equal,
     * regardless of whether either or both have a "W/" prefix.
     */
    private boolean isWeakMatch(String tag1, String tag2) {
        String opaque1 = tag1.startsWith("W/") ? tag1.substring(2) : tag1;
        String opaque2 = tag2.startsWith("W/") ? tag2.substring(2) : tag2;
        return opaque1.equals(opaque2);
    }

    /**
     * Zero-copy transfer from file channel to output stream via NIO.
     * Delegates to sendfile(2) on Linux / equivalent on macOS when the
     * servlet container's OutputStream maps to a socket channel.
     * Closes the output stream upon completion as the channel wrapper propagates close.
     */
    private void transferFile(FileChannel source, long position, long count, OutputStream out) throws IOException {
        try (WritableByteChannel destination = Channels.newChannel(out)) {
            long remaining = count;
            long currentPos = position;
            int zeroTransferCount = 0;

            while (remaining > 0) {
                long transferred = source.transferTo(currentPos, remaining, destination);
                if (transferred <= 0) {
                    ++zeroTransferCount;
                    if (zeroTransferCount > 100) {
                        throw new IOException("File transfer stalled with " + remaining + " bytes remaining");
                    }
                    Thread.onSpinWait();
                    continue;
                }
                zeroTransferCount = 0;
                currentPos += transferred;
                remaining -= transferred;
            }
        }
    }

    /**
     * Strong ETag derived from file size and last-modified epoch millis.
     * Sufficient for static-file identity without content hashing overhead.
     */
    String generateETag(long fileSize, Instant lastModified) {
        return "\"" + Long.toHexString(fileSize) + "-" + Long.toHexString(lastModified.toEpochMilli()) + "\"";
    }

    // RANGE PARSER (not) RFC 7233 compliant
    Range parseRange(String header, long size) {
        if (header == null || !header.startsWith("bytes=")) {
            return null;
        }

        String value = header.substring(6).trim();
        String[] parts = value.split(",", 2);
        String range = parts[0].trim();

        int dash = range.indexOf('-');
        if (dash < 0) return null;

        try {
            // suffix-byte-range-spec: "-<length>"
            if (dash == 0) {
                long suffix = Long.parseLong(range.substring(1));
                if (suffix <= 0) return null;
                suffix = Math.min(suffix, size);
                return new Range(size - suffix, size - 1);
            }

            long start = Long.parseLong(range.substring(0, dash));

            // open-ended: "<start>-"
            if (dash == range.length() - 1) {
                if (start >= size) return null;
                return new Range(start, size - 1);
            }

            // "<start>-<end>"
            long end = Long.parseLong(range.substring(dash + 1));
            if (start > end || start >= size) return null;
            end = Math.min(end, size - 1);

            return new Range(start, end);

        } catch (NumberFormatException e) {
            return null;
        }
    }

    // DISCONNECT DETECTION
    boolean isClientDisconnect(IOException e) {
        return switch (e) {
            case SocketTimeoutException _, AsyncRequestNotUsableException _ -> true;
            case IOException io when io.getMessage() != null -> {
                String msg = io.getMessage();
                yield msg.contains("Broken pipe")
                        || msg.contains("Connection reset")
                        || msg.contains("connection was aborted")
                        || msg.contains("An established connection was aborted")
                        || msg.contains("Response not usable")
                        || msg.contains("SocketTimeout")
                        || msg.contains("timed out");
            }
            default -> false;
        };
    }

    record Range(long start, long end) {}
}
