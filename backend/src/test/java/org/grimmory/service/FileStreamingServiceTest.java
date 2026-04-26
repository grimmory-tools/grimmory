package org.grimmory.service;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.grimmory.exception.APIException;
import org.springframework.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileStreamingServiceTest {

    private FileStreamingService fileStreamingService;

    @TempDir
    Path tempDir;

    Path testFile;
    byte[] testContent;

    @BeforeEach
    void setUp() throws IOException {
        fileStreamingService = new FileStreamingService();

        // Create a test file with known content
        testContent = new byte[10000];
        for (int i = 0; i < testContent.length; i++) {
            testContent[i] = (byte) (i % 256);
        }
        testFile = tempDir.resolve("test.bin");
        Files.write(testFile, testContent);
    }

    // ==================== streamWithRangeSupport - Full file tests ====================

    @Test
    void streamWithRangeSupport_noRangeHeader_streamsFullFile() throws IOException {
        var request = mock(HttpServletRequest.class);
        var response = mock(HttpServletResponse.class);
        var outputStream = new ByteArrayOutputStream();
        var servletOutputStream = createServletOutputStream(outputStream);

        when(request.getHeader("Range")).thenReturn(null);
        when(response.getOutputStream()).thenReturn(servletOutputStream);

        fileStreamingService.streamWithRangeSupport(testFile, "application/octet-stream", request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        verify(response).setContentLengthLong(testContent.length);
        verify(response).setHeader("Accept-Ranges", "bytes");
        verify(response).setContentType("application/octet-stream");
        assertArrayEquals(testContent, outputStream.toByteArray());
    }

    @Test
    void streamWithRangeSupport_fileNotFound_sends404() throws IOException {
        var request = mock(HttpServletRequest.class);
        var response = mock(HttpServletResponse.class);

        Path nonexistent = tempDir.resolve("nonexistent.bin");

        APIException exception = assertThrows(APIException.class, () -> 
            fileStreamingService.streamWithRangeSupport(nonexistent, "audio/mp4", request, response)
        );
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    // ==================== streamWithRangeSupport - Range request tests ====================

    @Test
    void streamWithRangeSupport_fullRange_streamsPartialContent() throws IOException {
        var request = mock(HttpServletRequest.class);
        var response = mock(HttpServletResponse.class);
        var outputStream = new ByteArrayOutputStream();
        var servletOutputStream = createServletOutputStream(outputStream);

        when(request.getHeader("Range")).thenReturn("bytes=0-99");
        when(response.getOutputStream()).thenReturn(servletOutputStream);

        fileStreamingService.streamWithRangeSupport(testFile, "audio/mp4", request, response);

        verify(response).setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        verify(response).setContentLengthLong(100);
        verify(response).setHeader("Content-Range", "bytes 0-99/" + testContent.length);

        byte[] expected = new byte[100];
        System.arraycopy(testContent, 0, expected, 0, 100);
        assertArrayEquals(expected, outputStream.toByteArray());
    }

    @Test
    void streamWithRangeSupport_openEndedRange_streamsToEnd() throws IOException {
        var request = mock(HttpServletRequest.class);
        var response = mock(HttpServletResponse.class);
        var outputStream = new ByteArrayOutputStream();
        var servletOutputStream = createServletOutputStream(outputStream);

        when(request.getHeader("Range")).thenReturn("bytes=9900-");
        when(response.getOutputStream()).thenReturn(servletOutputStream);

        fileStreamingService.streamWithRangeSupport(testFile, "audio/mp4", request, response);

        verify(response).setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        verify(response).setContentLengthLong(100);
        verify(response).setHeader("Content-Range", "bytes 9900-9999/" + testContent.length);

        byte[] expected = new byte[100];
        System.arraycopy(testContent, 9900, expected, 0, 100);
        assertArrayEquals(expected, outputStream.toByteArray());
    }

    @Test
    void streamWithRangeSupport_suffixRange_streamsLastNBytes() throws IOException {
        var request = mock(HttpServletRequest.class);
        var response = mock(HttpServletResponse.class);
        var outputStream = new ByteArrayOutputStream();
        var servletOutputStream = createServletOutputStream(outputStream);

        when(request.getHeader("Range")).thenReturn("bytes=-500");
        when(response.getOutputStream()).thenReturn(servletOutputStream);

        fileStreamingService.streamWithRangeSupport(testFile, "audio/mp4", request, response);

        verify(response).setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        verify(response).setContentLengthLong(500);
        verify(response).setHeader("Content-Range", "bytes 9500-9999/" + testContent.length);

        byte[] expected = new byte[500];
        System.arraycopy(testContent, 9500, expected, 0, 500);
        assertArrayEquals(expected, outputStream.toByteArray());
    }

    @Test
    void streamWithRangeSupport_invalidRange_sends416() throws IOException {
        var request = mock(HttpServletRequest.class);
        var response = mock(HttpServletResponse.class);

        when(request.getHeader("Range")).thenReturn("bytes=50000-60000"); // Beyond file size

        fileStreamingService.streamWithRangeSupport(testFile, "audio/mp4", request, response);

        verify(response).setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
        verify(response).setHeader("Content-Range", "bytes */" + testContent.length);
    }

    @Test
    void streamWithRangeSupport_rangeEndBeyondFileSize_clampsToFileSize() throws IOException {
        var request = mock(HttpServletRequest.class);
        var response = mock(HttpServletResponse.class);
        var outputStream = new ByteArrayOutputStream();
        var servletOutputStream = createServletOutputStream(outputStream);

        when(request.getHeader("Range")).thenReturn("bytes=9990-99999"); // End beyond file
        when(response.getOutputStream()).thenReturn(servletOutputStream);

        fileStreamingService.streamWithRangeSupport(testFile, "audio/mp4", request, response);

        verify(response).setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        verify(response).setContentLengthLong(10); // 9990 to 9999
        verify(response).setHeader("Content-Range", "bytes 9990-9999/" + testContent.length);
    }
    @Test
    void streamWithRangeSupport_setsETagAndCacheHeaders() throws IOException {
        var request = mock(HttpServletRequest.class);
        var response = mock(HttpServletResponse.class);
        var outputStream = new ByteArrayOutputStream();
        var servletOutputStream = createServletOutputStream(outputStream);

        when(request.getHeader("Range")).thenReturn(null);
        when(response.getOutputStream()).thenReturn(servletOutputStream);

        fileStreamingService.streamWithRangeSupport(testFile, "audio/mp4", request, response);

        String expectedETag = computeExpectedETag(testFile);
        verify(response).setHeader("ETag", expectedETag);
        verify(response).setHeader("Cache-Control", "no-cache");
        verify(response, never()).setHeader(eq("Pragma"), any());
    }

    @Test
    void streamWithRangeSupport_ifNoneMatchMatchingEtag_returns304() throws IOException {
        var request = mock(HttpServletRequest.class);
        var response = mock(HttpServletResponse.class);

        String etag = computeExpectedETag(testFile);
        when(request.getHeader("If-None-Match")).thenReturn(etag);

        fileStreamingService.streamWithRangeSupport(testFile, "audio/mp4", request, response);

        verify(response).setStatus(HttpServletResponse.SC_NOT_MODIFIED);
        verify(response, never()).getOutputStream();
    }

    @Test
    void streamWithRangeSupport_ifNoneMatchStaleEtag_streamsNormally() throws IOException {
        var request = mock(HttpServletRequest.class);
        var response = mock(HttpServletResponse.class);
        var outputStream = new ByteArrayOutputStream();
        var servletOutputStream = createServletOutputStream(outputStream);

        when(request.getHeader("If-None-Match")).thenReturn("\"stale-etag\"");
        when(request.getHeader("Range")).thenReturn(null);
        when(response.getOutputStream()).thenReturn(servletOutputStream);

        fileStreamingService.streamWithRangeSupport(testFile, "audio/mp4", request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        assertArrayEquals(testContent, outputStream.toByteArray());
    }

    @Test
    void streamWithRangeSupport_ifNoneMatchWildcard_returns304() throws IOException {
        var request = mock(HttpServletRequest.class);
        var response = mock(HttpServletResponse.class);

        when(request.getHeader("If-None-Match")).thenReturn("*");

        fileStreamingService.streamWithRangeSupport(testFile, "audio/mp4", request, response);

        verify(response).setStatus(HttpServletResponse.SC_NOT_MODIFIED);
        verify(response, never()).getOutputStream();
    }

    @Test
    void streamWithRangeSupport_ifNoneMatchList_returns304() throws IOException {
        var request = mock(HttpServletRequest.class);
        var response = mock(HttpServletResponse.class);

        String etag = computeExpectedETag(testFile);
        when(request.getHeader("If-None-Match")).thenReturn("\"other\", " + etag);

        fileStreamingService.streamWithRangeSupport(testFile, "audio/mp4", request, response);

        verify(response).setStatus(HttpServletResponse.SC_NOT_MODIFIED);
    }

    @Test
    void streamWithRangeSupport_ifNoneMatchWeakTag_returns304() throws IOException {
        var request = mock(HttpServletRequest.class);
        var response = mock(HttpServletResponse.class);

        String etag = computeExpectedETag(testFile);
        String weakEtag = "W/" + etag;
        when(request.getHeader("If-None-Match")).thenReturn(weakEtag);

        fileStreamingService.streamWithRangeSupport(testFile, "audio/mp4", request, response);

        verify(response).setStatus(HttpServletResponse.SC_NOT_MODIFIED);
    }

    @Test
    void streamWithRangeSupport_headRequest_returns200WithContentLength() throws IOException {
        var request = mock(HttpServletRequest.class);
        var response = mock(HttpServletResponse.class);

        when(request.getMethod()).thenReturn("HEAD");

        fileStreamingService.streamWithRangeSupport(testFile, "audio/mp4", request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        verify(response).setContentLengthLong(testContent.length);
        verify(response).setHeader("Accept-Ranges", "bytes");
        verify(response, never()).getOutputStream();
    }

    @Test
    void streamWithRangeSupport_ifRangeMatchingEtag_servesRange() throws IOException {
        var request = mock(HttpServletRequest.class);
        var response = mock(HttpServletResponse.class);
        var outputStream = new ByteArrayOutputStream();
        var servletOutputStream = createServletOutputStream(outputStream);

        String etag = computeExpectedETag(testFile);
        when(request.getHeader("Range")).thenReturn("bytes=0-99");
        when(request.getHeader("If-Range")).thenReturn(etag);
        when(response.getOutputStream()).thenReturn(servletOutputStream);

        fileStreamingService.streamWithRangeSupport(testFile, "audio/mp4", request, response);

        verify(response).setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        verify(response).setContentLengthLong(100);
        assertEquals(100, outputStream.size());
    }

    @Test
    void streamWithRangeSupport_ifRangeStaleEtag_servesFullFile() throws IOException {
        var request = mock(HttpServletRequest.class);
        var response = mock(HttpServletResponse.class);
        var outputStream = new ByteArrayOutputStream();
        var servletOutputStream = createServletOutputStream(outputStream);

        when(request.getHeader("Range")).thenReturn("bytes=0-99");
        when(request.getHeader("If-Range")).thenReturn("\"stale-etag\"");
        when(response.getOutputStream()).thenReturn(servletOutputStream);

        fileStreamingService.streamWithRangeSupport(testFile, "audio/mp4", request, response);

        // Stale If-Range ignore range, return full file
        verify(response).setStatus(HttpServletResponse.SC_OK);
        verify(response).setContentLengthLong(testContent.length);
        assertArrayEquals(testContent, outputStream.toByteArray());
    }

    @Test
    void streamWithRangeSupport_setsLastModifiedHeader() throws IOException {
        var request = mock(HttpServletRequest.class);
        var response = mock(HttpServletResponse.class);
        var outputStream = new ByteArrayOutputStream();
        var servletOutputStream = createServletOutputStream(outputStream);

        when(request.getHeader("Range")).thenReturn(null);
        when(response.getOutputStream()).thenReturn(servletOutputStream);

        fileStreamingService.streamWithRangeSupport(testFile, "audio/mp4", request, response);

        var attrs = Files.readAttributes(testFile, BasicFileAttributes.class);
        long expectedMs = attrs.lastModifiedTime().toInstant().toEpochMilli();
        verify(response).setDateHeader("Last-Modified", expectedMs);
    }

    // ==================== parseRange tests ====================

    @Test
    void parseRange_fullRange_parsesCorrectly() {
        var result = fileStreamingService.parseRange("bytes=100-199", 1000);

        assertNotNull(result);
        assertEquals(100, result.start());
        assertEquals(199, result.end());
    }

    @Test
    void parseRange_openEndedRange_parsesCorrectly() {
        var result = fileStreamingService.parseRange("bytes=500-", 1000);

        assertNotNull(result);
        assertEquals(500, result.start());
        assertEquals(999, result.end());
    }

    @Test
    void parseRange_suffixRange_parsesCorrectly() {
        var result = fileStreamingService.parseRange("bytes=-200", 1000);

        assertNotNull(result);
        assertEquals(800, result.start());
        assertEquals(999, result.end());
    }

    @Test
    void parseRange_suffixRangeLargerThanFile_startsAtZero() {
        var result = fileStreamingService.parseRange("bytes=-2000", 1000);

        assertNotNull(result);
        assertEquals(0, result.start());
        assertEquals(999, result.end());
    }

    @Test
    void parseRange_clampsEndToFileSize() {
        var result = fileStreamingService.parseRange("bytes=900-2000", 1000);

        assertNotNull(result);
        assertEquals(900, result.start());
        assertEquals(999, result.end());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "invalid",
            "bytes",
            "bytes=",
            "bytes=abc-def",
            "bytes=100-50", // End before start
            "characters=0-100"
    })
    void parseRange_invalidFormats_returnsNull(String rangeHeader) {
        var result = fileStreamingService.parseRange(rangeHeader, 1000);
        assertNull(result);
    }

    @Test
    void parseRange_startBeyondFileSize_returnsNull() {
        var result = fileStreamingService.parseRange("bytes=2000-3000", 1000);
        assertNull(result);
    }

    @Test
    void parseRange_multipleRanges_usesFirstOnly() {
        var result = fileStreamingService.parseRange("bytes=0-99, 200-299", 1000);

        assertNotNull(result);
        assertEquals(0, result.start());
        assertEquals(99, result.end());
    }

    @Test
    void parseRange_withLeadingTrailingWhitespace_parsesCorrectly() {
        var result = fileStreamingService.parseRange("bytes=100-199", 1000);

        assertNotNull(result);
        assertEquals(100, result.start());
        assertEquals(199, result.end());
    }

    @Test
    void parseRange_withWhitespaceAroundComma_parsesFirstRange() {
        var result = fileStreamingService.parseRange("bytes= 100-199 , 300-399", 1000);

        assertNotNull(result);
        assertEquals(100, result.start());
        assertEquals(199, result.end());
    }

    // ==================== isClientDisconnect tests ====================

    @Test
    void isClientDisconnect_socketTimeoutException_returnsTrue() {
        assertTrue(fileStreamingService.isClientDisconnect(new SocketTimeoutException("timeout")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Connection reset",
            "Broken pipe",
            "connection was aborted",
            "An established connection was aborted",
            "SocketTimeout occurred",
            "Connection timed out"
    })
    void isClientDisconnect_knownMessages_returnsTrue(String message) {
        assertTrue(fileStreamingService.isClientDisconnect(new IOException(message)));
    }

    @Test
    void isClientDisconnect_unknownMessage_returnsFalse() {
        assertFalse(fileStreamingService.isClientDisconnect(new IOException("Unknown error")));
    }

    @Test
    void isClientDisconnect_nullMessage_checksClassName() {
        IOException timeoutException = new SocketTimeoutException();
        assertTrue(fileStreamingService.isClientDisconnect(timeoutException));
    }

    @Test
    void isClientDisconnect_regularIOException_returnsFalse() {
        assertFalse(fileStreamingService.isClientDisconnect(new IOException("Read failed")));
    }

    // ==================== Edge cases ====================

    @Test
    void streamWithRangeSupport_emptyFile_handlesCorrectly() throws IOException {
        Path emptyFile = tempDir.resolve("empty.bin");
        Files.createFile(emptyFile);

        var request = mock(HttpServletRequest.class);
        var response = mock(HttpServletResponse.class);
        var outputStream = new ByteArrayOutputStream();
        var servletOutputStream = createServletOutputStream(outputStream);

        when(request.getHeader("Range")).thenReturn(null);
        when(response.getOutputStream()).thenReturn(servletOutputStream);

        fileStreamingService.streamWithRangeSupport(emptyFile, "audio/mp4", request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        verify(response).setContentLengthLong(0);
    }

    @Test
    void streamWithRangeSupport_singleByteRange_streamsOneByte() throws IOException {
        var request = mock(HttpServletRequest.class);
        var response = mock(HttpServletResponse.class);
        var outputStream = new ByteArrayOutputStream();
        var servletOutputStream = createServletOutputStream(outputStream);

        when(request.getHeader("Range")).thenReturn("bytes=50-50");
        when(response.getOutputStream()).thenReturn(servletOutputStream);

        fileStreamingService.streamWithRangeSupport(testFile, "audio/mp4", request, response);

        verify(response).setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        verify(response).setContentLengthLong(1);
        assertEquals(1, outputStream.size());
        assertEquals(testContent[50], outputStream.toByteArray()[0]);
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "0, 9999",
            "5000, 5000",
            "9999, 9999"
    })
    void streamWithRangeSupport_variousValidRanges_streamsCorrectContent(int start, int end) throws IOException {
        var request = mock(HttpServletRequest.class);
        var response = mock(HttpServletResponse.class);
        var outputStream = new ByteArrayOutputStream();
        var servletOutputStream = createServletOutputStream(outputStream);

        when(request.getHeader("Range")).thenReturn("bytes=" + start + "-" + end);
        when(response.getOutputStream()).thenReturn(servletOutputStream);

        fileStreamingService.streamWithRangeSupport(testFile, "audio/mp4", request, response);

        int expectedLength = end - start + 1;
        assertEquals(expectedLength, outputStream.size());

        byte[] expected = new byte[expectedLength];
        System.arraycopy(testContent, start, expected, 0, expectedLength);
        assertArrayEquals(expected, outputStream.toByteArray());
    }

    // ==================== Helpers ====================

    private String computeExpectedETag(Path file) throws IOException {
        var attrs = Files.readAttributes(file, BasicFileAttributes.class);
        return fileStreamingService.generateETag(
                attrs.size(), attrs.lastModifiedTime().toInstant());
    }

    private ServletOutputStream createServletOutputStream(ByteArrayOutputStream outputStream) {
        return new ServletOutputStream() {
            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(jakarta.servlet.WriteListener writeListener) {
            }

            @Override
            public void write(int b) throws IOException {
                outputStream.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                outputStream.write(b, off, len);
            }
        };
    }
}
