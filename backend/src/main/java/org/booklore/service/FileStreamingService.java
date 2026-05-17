package org.booklore.service;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.ApiError;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.io.OutputStream;
import java.io.EOFException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.AccessDeniedException;
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

    @Value("${app.streaming.buffer-size:131072}")
    private int bufferSize = 131072; // 128 KB

    @Value("${app.streaming.min-sendfile-size:49152}")
    private int minSendfileSize = 49152; // 48 KB

    @PostConstruct
    public void validateProperties() {
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("app.streaming.buffer-size must be greater than 0");
        }
        if (minSendfileSize < 0) {
            throw new IllegalArgumentException("app.streaming.min-sendfile-size must be non-negative");
        }
    }

    /**
     * Streams a file with HTTP Range support for seeking.
     * Uses Java NIO FileChannel or Tomcat sendfile where supported.
     * Supports conditional requests via strong ETag / If-None-Match,
     * If-Modified-Since, and If-Range validation for efficient caching and seeking.
     * Multi-range requests are intentionally served as full 200 OK responses
     */
    public void streamWithRangeSupport(
            Path filePath,
            String contentType,
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        // Atomic retrieval of file metadata
        BasicFileAttributes attrs;
        try {
            attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
        } catch (NoSuchFileException _) {
            throw ApiError.FILE_NOT_FOUND.createException(filePath.toString());
        } catch (AccessDeniedException e) {
            throw ApiError.PERMISSION_DENIED.createException(e.getMessage());
        }

        long fileSize = attrs.size();
        long lastModified = attrs.lastModifiedTime().toMillis();
        String etag = generateETag(fileSize, lastModified);
        String rangeHeader = request.getHeader("Range");

        // Initialize response buffer size and headers for media streaming
        response.setBufferSize(bufferSize);
        response.setHeader("Accept-Ranges", "bytes");
        response.setContentType(contentType);
        response.setHeader("ETag", etag);
        response.setDateHeader("Last-Modified", lastModified);
        // Allow caching with mandatory revalidation via ETag eliminates
        // redundant byte transfers on seeks while keeping access-control checks.
        response.setHeader("Cache-Control", "private, no-cache, must-revalidate");
        response.setHeader("Content-Disposition", "inline");

        // Handle ETag-based conditional revalidation
        String ifNoneMatch = request.getHeader("If-None-Match");
        if (evaluateIfNoneMatch(ifNoneMatch, etag)) {
            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            return;
        }

        // Handle date-based conditional revalidation
        // Only defined for GET and HEAD
        String method = request.getMethod();
        boolean isGet = "GET".equalsIgnoreCase(method);
        boolean isHead = "HEAD".equalsIgnoreCase(method);

        if ((ifNoneMatch == null || ifNoneMatch.isBlank()) && (isGet || isHead)) {
            long ifModifiedSince;
            try {
                ifModifiedSince = request.getDateHeader("If-Modified-Since");
            } catch (IllegalArgumentException _) {
                ifModifiedSince = -1;
            }
            if (ifModifiedSince != -1 && lastModified / 1000 <= ifModifiedSince / 1000) {
                response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                return;
            }
        }

        // Respond to HEAD requests with metadata only
        if (isHead) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentLengthLong(fileSize);
            return;
        }

        try {
            String ifRange = request.getHeader("If-Range");
            if (rangeHeader != null && ifRange != null && !validateIfRange(ifRange, etag, lastModified)) {
                // Precondition failed: fall back to full content delivery
                streamFullContent(request, response, filePath, fileSize);
                return;
            }

            // Full content delivery
            if (rangeHeader == null) {
                streamFullContent(request, response, filePath, fileSize);
                return;
            }

            // Serve full content for multi-range requests
            if (hasMultipleRanges(rangeHeader)) {
                streamFullContent(request, response, filePath, fileSize);
                return;
            }

            // Partial content delivery
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

            streamContent(request, response, filePath, range.start, length);

        } catch (AccessDeniedException e) {
            log.warn("Access denied during file streaming: {}", filePath, e);
            if (!response.isCommitted()) {
                throw ApiError.PERMISSION_DENIED.createException(e.getMessage());
            }
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

    private void streamFullContent(
            HttpServletRequest request,
            HttpServletResponse response,
            Path filePath,
            long fileSize
    ) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentLengthLong(fileSize);
        streamContent(request, response, filePath, 0, fileSize);
    }

    private void streamContent(
            HttpServletRequest request,
            HttpServletResponse response,
            Path filePath,
            long start,
            long length
    ) throws IOException {
        if (length >= minSendfileSize && tryTomcatSendfile(request, filePath, start, length)) {
            return;
        }
        OutputStream out = response.getOutputStream();
        try (var fileChannel = FileChannel.open(filePath, StandardOpenOption.READ)) {
            copyToResponse(fileChannel, start, length, out);
        }
    }

    private boolean tryTomcatSendfile(
            HttpServletRequest request,
            Path filePath,
            long start,
            long length
    ) {
        Object supported = request.getAttribute("org.apache.tomcat.sendfile.support");
        if (!Boolean.TRUE.equals(supported)) {
            return false;
        }

        // Once sendfile attributes are set, the application MUST NOT 
        // write any data to the response body. Tomcat will handle the transfer.
        try {
            request.setAttribute("org.apache.tomcat.sendfile.filename", filePath.toRealPath().toString());
            request.setAttribute("org.apache.tomcat.sendfile.start", start);
            request.setAttribute("org.apache.tomcat.sendfile.end", Math.addExact(start, length));
            return true;
        } catch (IOException | ArithmeticException e) {
            log.debug("Sendfile fallback: {}", e.getMessage());
            return false;
        }
    }

    private static boolean hasMultipleRanges(String header) {
        if (header == null || header.length() < 7 || !header.regionMatches(true, 0, "bytes=", 0, 6)) {
            return false;
        }
        // Skip whitespace after "bytes="
        int pos = 6;
        while (pos < header.length() && header.charAt(pos) <= ' ') pos++;
        
        // Find comma
        while (pos < header.length()) {
            if (header.charAt(pos) == ',') {
                // Found a comma, check if there's anything after it
                do pos++;
                while (pos < header.length() && header.charAt(pos) <= ' ');
                return pos < header.length();
            }
            pos++;
        }
        return false;
    }

    /**
     * Validates the If-Range header against current ETag and lastModified.
     * If-Range can be a strong ETag OR an HTTP-date.
     */
    private static boolean validateIfRange(String ifRange, String etag, long lastModified) {
        if (!ifRange.isBlank() && ifRange.charAt(0) == '"') {
            // If-Range MUST use strong comparison only.
            // Do NOT use isWeakMatch() here.
            return etag.equals(ifRange);
        } else {
            try {
                Instant ifRangeDate = Instant.from(HTTP_DATE_FORMAT.parse(ifRange));
                // Exact second-precision equality is required for If-Range date validators.
                return lastModified / 1000 == ifRangeDate.getEpochSecond();
            } catch (DateTimeParseException e) {
                log.trace("Failed to parse If-Range date: {}", ifRange);
                return false;
            }
        }
    }

    /**
     * Evaluates the If-None-Match header against the current ETag.
     * If-None-Match can be "*", a single entity-tag, or a list of them.
     */
    private static boolean evaluateIfNoneMatch(String ifNoneMatch, String currentEtag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) return false;

        int start = 0, len = ifNoneMatch.length();
        while (start < len) {
            int end = ifNoneMatch.indexOf(',', start);
            if (end == -1) end = len;

            // Trim whitespace
            int s = start, e = end;
            while (s < e && ifNoneMatch.charAt(s) <= ' ') s++;
            while (e > s && ifNoneMatch.charAt(e - 1) <= ' ') e--;

            if (e > s) {
                if (e - s == 1 && ifNoneMatch.charAt(s) == '*') return true;
                if (isWeakMatch(ifNoneMatch, s, e, currentEtag)) return true;
            }
            start = end + 1;
        }
        return false;
    }

    private static boolean isWeakMatch(String input, int start, int end, String etag) {
        // Strip optional W/ prefix from both sides before comparing opaque-tags
        int s = (end - start >= 2 && input.charAt(start) == 'W' && input.charAt(start + 1) == '/') ? start + 2 : start;
        int cs = etag.startsWith("W/") ? 2 : 0;
        int matchLen = end - s;
        return matchLen == etag.length() - cs && input.regionMatches(s, etag, cs, matchLen);
    }


    private void copyToResponse(
            FileChannel source,
            long position,
            long count,
            OutputStream out
    ) throws IOException {
        if (count < 8192) {
            copyWithHeapBuffer(source, position, count, out);
            out.flush();
            return;
        }
        WritableByteChannel outChannel = Channels.newChannel(out);
        long remaining = count;
        long offset = position;
        int zeroCount = 0;

        // Try zero-copy first (works on many servlet containers)
        while (remaining > 0) {
            long n = source.transferTo(offset, remaining, outChannel);
            if (n > 0) {
                offset += n;
                remaining -= n;
                zeroCount = 0;
                continue;
            }

            if (++zeroCount >= 3) {
                // transferTo repeatedly returned 0 - fall back to heap copy.
                copyWithHeapBuffer(source, offset, remaining, out);
                break;
            }
        }
        out.flush();
    }

    private void copyWithHeapBuffer(
            FileChannel source,
            long position,
            long count,
            OutputStream out
    ) throws IOException {
        byte[] buf = new byte[bufferSize];
        ByteBuffer bb = ByteBuffer.wrap(buf);
        long remaining = count;
        long offset = position;

        while (remaining > 0) {
            bb.clear();
            bb.limit((int) Math.min(buf.length, remaining));

            int read = source.read(bb, offset);
            if (read < 0) {
                throw new EOFException("Unexpected EOF at position " + offset);
            }

            out.write(buf, 0, read);
            offset += read;
            remaining -= read;
        }
    }

    /**
     * Strong ETag derived from file size and last-modified epoch millis.
     * Sufficient for static-file identity without content hashing overhead.
     */

    public static String generateETag(long fileSize, long lastModified) {
        return "\"%x-%x\"".formatted(fileSize, lastModified);
    }

    // Byte-range parser
    static Range parseRange(String header, long size) {
        if (size <= 0) return null;
        if (header == null || header.length() < 6 || !header.regionMatches(true, 0, "bytes=", 0, 6)) return null;

        int startPos = 6, len = header.length();
        while (startPos < len && header.charAt(startPos) <= ' ') startPos++;
        if (startPos >= len) return null;

        // Only parse the first range if multiple are provided
        int comma = header.indexOf(',', startPos);
        int endPos = (comma >= 0) ? comma : len;
        while (endPos > startPos && header.charAt(endPos - 1) <= ' ') endPos--;

        int dash = header.indexOf('-', startPos);
        if (dash < 0 || dash >= endPos) return null;

        try {
            if (dash == startPos) { // Suffix: "-N"
                long suffix = Long.parseLong(header, dash + 1, endPos, 10);
                return suffix <= 0 ? null : new Range(size - Math.min(suffix, size), size - 1);
            }

            long start = Long.parseLong(header, startPos, dash, 10);
            if (dash == endPos - 1) { // Open-ended: "N-"
                return start >= size ? null : new Range(start, size - 1);
            }

            long end = Long.parseLong(header, dash + 1, endPos, 10); // Bounded: "N-M"
            if (start > end) return null;
            return (start >= size) ? null : new Range(start, Math.min(end, size - 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Client disconnection detection
    static boolean isClientDisconnect(IOException e) {
        return switch (e) {
            case EOFException _, SocketTimeoutException _, AsyncRequestNotUsableException _ -> true;
            case IOException io when io.getMessage() != null -> {
                String msg = io.getMessage().toLowerCase(Locale.ROOT);
                yield msg.contains("broken pipe")
                        || msg.contains("connection reset")
                        || msg.contains("connection was aborted")
                        || msg.contains("an established connection was aborted")
                        || msg.contains("response not usable")
                        || msg.contains("sockettimeout")
                        || msg.contains("timed out");
            }
            default -> false;
        };
    }

    record Range(long start, long end) {}
}
