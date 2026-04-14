package org.booklore.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

@Slf4j
@Service
public class FileStreamingService {

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
        } catch (NoSuchFileException e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "File not found");
            return;
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
        if (etag.equals(ifNoneMatch)) {
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
            if (rangeHeader != null && ifRange != null && !etag.equals(ifRange)) {
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

        } catch (IOException e) {
            if (isClientDisconnect(e)) {
                log.debug("Client disconnected during streaming: {}", e.getMessage());
            } else {
                log.error("Error during file streaming: {}", filePath, e);
                if (!response.isCommitted()) {
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Streaming error");
                }
            }
        }
    }

    /**
     * Zero-copy transfer from file channel to output stream via NIO.
     * Delegates to sendfile(2) on Linux / equivalent on macOS when the
     * servlet container's OutputStream maps to a socket channel.
     * Does not close the output stream the caller owns its lifecycle.
     */
    private void transferFile(FileChannel source, long position, long count, OutputStream out) throws IOException {
        WritableByteChannel destination = Channels.newChannel(out);
        long remaining = count;
        long currentPos = position;
        int zeroTransferCount = 0;

        while (remaining > 0) {
            long transferred = source.transferTo(currentPos, remaining, destination);
            if (transferred <= 0) {
                if (++zeroTransferCount > 100) break; // Give up after repeated zeros
                Thread.onSpinWait();
                continue;
            }
            zeroTransferCount = 0;
            currentPos += transferred;
            remaining -= transferred;
        }
    }

    /**
     * Weak ETag derived from file size and last-modified epoch millis.
     * Sufficient for static-file identity without content hashing overhead.
     */
    String generateETag(long fileSize, Instant lastModified) {
        return "W/\"" + Long.toHexString(fileSize) + "-" + Long.toHexString(lastModified.toEpochMilli()) + "\"";
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
            case SocketTimeoutException _ -> true;
            case IOException io when io.getClass().getSimpleName().equals("AsyncRequestNotUsableException") -> true;
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
