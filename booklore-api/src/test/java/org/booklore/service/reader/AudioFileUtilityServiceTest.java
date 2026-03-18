package org.booklore.service.reader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AudioFileUtilityServiceTest {

    private AudioFileUtilityService audioFileUtility;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        audioFileUtility = new AudioFileUtilityService();
    }

    // ==================== listAudioFiles tests ====================

    @Test
    void listAudioFiles_returnsEmptyListForEmptyFolder() {
        List<Path> result = audioFileUtility.listAudioFiles(tempDir);
        assertTrue(result.isEmpty());
    }

    @Test
    void listAudioFiles_returnsOnlyAudioFiles() throws IOException {
        // Create audio files
        Files.createFile(tempDir.resolve("track1.mp3"));
        Files.createFile(tempDir.resolve("track2.m4b"));
        Files.createFile(tempDir.resolve("track3.m4a"));

        // Create non-audio files
        Files.createFile(tempDir.resolve("readme.txt"));
        Files.createFile(tempDir.resolve("cover.jpg"));
        Files.createFile(tempDir.resolve("metadata.xml"));

        List<Path> result = audioFileUtility.listAudioFiles(tempDir);

        assertEquals(3, result.size());
        assertTrue(result.stream().anyMatch(p -> p.getFileName().toString().equals("track1.mp3")));
        assertTrue(result.stream().anyMatch(p -> p.getFileName().toString().equals("track2.m4b")));
        assertTrue(result.stream().anyMatch(p -> p.getFileName().toString().equals("track3.m4a")));
    }

    @Test
    void listAudioFiles_sortsNaturally() throws IOException {
        // Create files in non-natural order
        Files.createFile(tempDir.resolve("Track 10.mp3"));
        Files.createFile(tempDir.resolve("Track 2.mp3"));
        Files.createFile(tempDir.resolve("Track 1.mp3"));
        Files.createFile(tempDir.resolve("Track 20.mp3"));
        Files.createFile(tempDir.resolve("Track 3.mp3"));

        List<Path> result = audioFileUtility.listAudioFiles(tempDir);

        assertEquals(5, result.size());
        assertEquals("Track 1.mp3", result.get(0).getFileName().toString());
        assertEquals("Track 2.mp3", result.get(1).getFileName().toString());
        assertEquals("Track 3.mp3", result.get(2).getFileName().toString());
        assertEquals("Track 10.mp3", result.get(3).getFileName().toString());
        assertEquals("Track 20.mp3", result.get(4).getFileName().toString());
    }

    @Test
    void listAudioFiles_handlesNumericPrefixes() throws IOException {
        Files.createFile(tempDir.resolve("01 - Introduction.mp3"));
        Files.createFile(tempDir.resolve("02 - Chapter One.mp3"));
        Files.createFile(tempDir.resolve("10 - Chapter Nine.mp3"));
        Files.createFile(tempDir.resolve("11 - Epilogue.mp3"));

        List<Path> result = audioFileUtility.listAudioFiles(tempDir);

        assertEquals(4, result.size());
        assertEquals("01 - Introduction.mp3", result.get(0).getFileName().toString());
        assertEquals("02 - Chapter One.mp3", result.get(1).getFileName().toString());
        assertEquals("10 - Chapter Nine.mp3", result.get(2).getFileName().toString());
        assertEquals("11 - Epilogue.mp3", result.get(3).getFileName().toString());
    }

    @Test
    void listAudioFiles_excludesDirectories() throws IOException {
        Files.createFile(tempDir.resolve("track1.mp3"));
        Files.createDirectory(tempDir.resolve("subfolder.mp3")); // Directory with mp3 extension

        List<Path> result = audioFileUtility.listAudioFiles(tempDir);

        assertEquals(1, result.size());
        assertEquals("track1.mp3", result.get(0).getFileName().toString());
    }

    @Test
    void listAudioFiles_returnsEmptyListForNonexistentFolder() {
        Path nonexistent = tempDir.resolve("nonexistent");
        List<Path> result = audioFileUtility.listAudioFiles(nonexistent);
        assertTrue(result.isEmpty());
    }

    @Test
    void listAudioFiles_handlesAllSupportedExtensions() throws IOException {
        Files.createFile(tempDir.resolve("track.mp3"));
        Files.createFile(tempDir.resolve("track.m4a"));
        Files.createFile(tempDir.resolve("track.m4b"));

        List<Path> result = audioFileUtility.listAudioFiles(tempDir);

        assertEquals(3, result.size());
    }

    // ==================== isAudioFile tests ====================

    @ParameterizedTest
    @ValueSource(strings = {"track.mp3", "book.m4b", "audio.m4a"})
    void isAudioFile_returnsTrueForAudioExtensions(String filename) throws IOException {
        Path file = tempDir.resolve(filename);
        Files.createFile(file);
        assertTrue(audioFileUtility.isAudioFile(file));
    }

    @ParameterizedTest
    @ValueSource(strings = {"TRACK.MP3", "Book.M4B", "Audio.M4A"})
    void isAudioFile_isCaseInsensitive(String filename) throws IOException {
        Path file = tempDir.resolve(filename);
        Files.createFile(file);
        assertTrue(audioFileUtility.isAudioFile(file));
    }

    @ParameterizedTest
    @ValueSource(strings = {"image.jpg", "document.pdf", "video.mp4", "text.txt", "archive.zip", "cover.png"})
    void isAudioFile_returnsFalseForNonAudioExtensions(String filename) throws IOException {
        Path file = tempDir.resolve(filename);
        Files.createFile(file);
        assertFalse(audioFileUtility.isAudioFile(file));
    }

    @Test
    void isAudioFile_returnsFalseForFileWithoutExtension() throws IOException {
        Path file = tempDir.resolve("noextension");
        Files.createFile(file);
        assertFalse(audioFileUtility.isAudioFile(file));
    }

    // ==================== getContentType tests ====================

    @ParameterizedTest
    @CsvSource({
            "track.m4b, audio/mp4",
            "track.m4a, audio/mp4",
            "track.mp3, audio/mpeg"
    })
    void getContentType_returnsCorrectMimeType(String filename, String expectedMimeType) {
        Path file = tempDir.resolve(filename);
        assertEquals(expectedMimeType, audioFileUtility.getContentType(file));
    }

    @ParameterizedTest
    @CsvSource({
            "TRACK.M4B, audio/mp4",
            "TRACK.MP3, audio/mpeg"
    })
    void getContentType_isCaseInsensitive(String filename, String expectedMimeType) {
        Path file = tempDir.resolve(filename);
        assertEquals(expectedMimeType, audioFileUtility.getContentType(file));
    }

    @Test
    void getContentType_returnsOctetStreamForUnknownExtension() {
        Path file = tempDir.resolve("unknown.xyz");
        assertEquals("application/octet-stream", audioFileUtility.getContentType(file));
    }

    @Test
    void getContentType_returnsOctetStreamForNoExtension() {
        Path file = tempDir.resolve("noextension");
        assertEquals("application/octet-stream", audioFileUtility.getContentType(file));
    }

    // ==================== getTrackTitleFromFilename tests ====================

    @Test
    void getTrackTitleFromFilename_removesExtension() {
        assertEquals("Track Title", audioFileUtility.getTrackTitleFromFilename("Track Title.mp3"));
    }

    @Test
    void getTrackTitleFromFilename_replacesUnderscoresWithSpaces() {
        assertEquals("Chapter One Introduction", audioFileUtility.getTrackTitleFromFilename("Chapter_One_Introduction.mp3"));
    }

    @Test
    void getTrackTitleFromFilename_replacesHyphensWithSpaces() {
        assertEquals("Chapter One Introduction", audioFileUtility.getTrackTitleFromFilename("Chapter-One-Introduction.mp3"));
    }

    @Test
    void getTrackTitleFromFilename_replacesMultipleUnderscoresAndHyphens() {
        assertEquals("Chapter One", audioFileUtility.getTrackTitleFromFilename("Chapter___One---.mp3"));
    }

    @Test
    void getTrackTitleFromFilename_trimsWhitespace() {
        assertEquals("Track", audioFileUtility.getTrackTitleFromFilename("  Track  .mp3"));
    }

    @Test
    void getTrackTitleFromFilename_handlesNoExtension() {
        assertEquals("Track Title", audioFileUtility.getTrackTitleFromFilename("Track_Title"));
    }

    @Test
    void getTrackTitleFromFilename_handlesMultipleDots() {
        assertEquals("Track.v2", audioFileUtility.getTrackTitleFromFilename("Track.v2.mp3"));
    }

    @Test
    void getTrackTitleFromFilename_preservesNumbers() {
        assertEquals("01 Chapter One", audioFileUtility.getTrackTitleFromFilename("01_Chapter_One.mp3"));
    }

    // ==================== getSupportedExtensions tests ====================

    @Test
    void getSupportedExtensions_returnsAllExpectedExtensions() {
        var extensions = audioFileUtility.getSupportedExtensions();

        assertEquals(4, extensions.size());
        assertTrue(extensions.contains(".mp3"));
        assertTrue(extensions.contains(".m4a"));
        assertTrue(extensions.contains(".m4b"));
        assertTrue(extensions.contains(".opus"));
    }

    @Test
    void getSupportedExtensions_returnsUnmodifiableSet() {
        var extensions = audioFileUtility.getSupportedExtensions();
        assertThrows(UnsupportedOperationException.class, () -> extensions.add(".wav"));
    }

    // ==================== Natural sorting edge cases ====================

    @Test
    void listAudioFiles_handlesMixedAlphanumericNames() throws IOException {
        Files.createFile(tempDir.resolve("a1.mp3"));
        Files.createFile(tempDir.resolve("a10.mp3"));
        Files.createFile(tempDir.resolve("a2.mp3"));
        Files.createFile(tempDir.resolve("b1.mp3"));

        List<Path> result = audioFileUtility.listAudioFiles(tempDir);

        assertEquals(4, result.size());
        assertEquals("a1.mp3", result.get(0).getFileName().toString());
        assertEquals("a2.mp3", result.get(1).getFileName().toString());
        assertEquals("a10.mp3", result.get(2).getFileName().toString());
        assertEquals("b1.mp3", result.get(3).getFileName().toString());
    }

    @Test
    void listAudioFiles_handlesLeadingZeros() throws IOException {
        Files.createFile(tempDir.resolve("001.mp3"));
        Files.createFile(tempDir.resolve("002.mp3"));
        Files.createFile(tempDir.resolve("010.mp3"));
        Files.createFile(tempDir.resolve("100.mp3"));

        List<Path> result = audioFileUtility.listAudioFiles(tempDir);

        assertEquals(4, result.size());
        assertEquals("001.mp3", result.get(0).getFileName().toString());
        assertEquals("002.mp3", result.get(1).getFileName().toString());
        assertEquals("010.mp3", result.get(2).getFileName().toString());
        assertEquals("100.mp3", result.get(3).getFileName().toString());
    }

    @Test
    void listAudioFiles_sortsCaseInsensitively() throws IOException {
        Files.createFile(tempDir.resolve("Alpha.mp3"));
        Files.createFile(tempDir.resolve("beta.mp3"));
        Files.createFile(tempDir.resolve("GAMMA.mp3"));

        List<Path> result = audioFileUtility.listAudioFiles(tempDir);

        assertEquals(3, result.size());
        assertEquals("Alpha.mp3", result.get(0).getFileName().toString());
        assertEquals("beta.mp3", result.get(1).getFileName().toString());
        assertEquals("GAMMA.mp3", result.get(2).getFileName().toString());
    }
}
