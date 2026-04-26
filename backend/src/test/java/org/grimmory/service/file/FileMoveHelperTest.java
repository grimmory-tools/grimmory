package org.booklore.service.file;

import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.monitoring.MonitoringRegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FileMoveHelper Tests")
class FileMoveHelperTest {

    @Mock
    private MonitoringRegistrationService monitoringRegistrationService;

    @Mock
    private AppSettingService appSettingService;

    private FileMoveHelper fileMoveHelper;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        fileMoveHelper = new FileMoveHelper(monitoringRegistrationService, appSettingService);
    }

    @Nested
    @DisplayName("SMB Share Behavior Tests")
    class SmbShareBehaviorTests {

        @Test
        @DisplayName("waitForFileAccessible retries when file appears after delay (simulates SMB latency)")
        void waitForFileAccessible_fileAppearsAfterDelay_eventuallySucceeds() throws Exception {
            Path filePath = tempDir.resolve("delayed-file.txt");

            ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
            try {
                executor.schedule(() -> {
                    try {
                        Files.writeString(filePath, "content");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }, 150, TimeUnit.MILLISECONDS);

                boolean result = fileMoveHelper.waitForFileAccessible(filePath);

                assertTrue(result, "File should become accessible after retries");
                assertTrue(Files.exists(filePath));
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        @DisplayName("waitForFileAccessible returns false when file never appears (simulates SMB file not found)")
        void waitForFileAccessible_fileNeverAppears_returnsFalse() {
            Path nonExistentFile = tempDir.resolve("does-not-exist.txt");

            long startTime = System.currentTimeMillis();
            boolean result = fileMoveHelper.waitForFileAccessible(nonExistentFile);
            long elapsed = System.currentTimeMillis() - startTime;

            assertFalse(result);
            assertTrue(elapsed >= 200, "Should have waited for at least 2 retry delays (100ms each)");
        }

        @Test
        @DisplayName("moveFile throws NoSuchFileException when source never becomes accessible")
        void moveFile_sourceNeverAccessible_throwsNoSuchFileException() {
            Path source = tempDir.resolve("missing-source.txt");
            Path target = tempDir.resolve("target.txt");

            NoSuchFileException exception = assertThrows(NoSuchFileException.class,
                    () -> fileMoveHelper.moveFile(source, target));

            assertTrue(exception.getMessage().contains("Source file not accessible after retries"));
        }

        @Test
        @DisplayName("moveFile succeeds when source appears during retry (simulates SMB eventual consistency)")
        void moveFile_sourceAppearsAfterRetry_succeeds() throws Exception {
            Path source = tempDir.resolve("eventual-source.txt");
            Path target = tempDir.resolve("subdir/target.txt");

            ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
            try {
                executor.schedule(() -> {
                    try {
                        Files.writeString(source, "delayed content");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }, 50, TimeUnit.MILLISECONDS);

                fileMoveHelper.moveFile(source, target);

                assertTrue(Files.exists(target));
                assertFalse(Files.exists(source));
                assertEquals("delayed content", Files.readString(target));
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        @DisplayName("moveFileWithBackup creates temp file and returns its path")
        void moveFileWithBackup_createsTemporaryFile() throws Exception {
            Path source = tempDir.resolve("original.txt");
            Files.writeString(source, "original content");

            Path tempPath = fileMoveHelper.moveFileWithBackup(source);

            assertFalse(Files.exists(source), "Original should be moved");
            assertTrue(Files.exists(tempPath), "Temp file should exist");
            assertTrue(tempPath.getFileName().toString().endsWith(".tmp_move"));
            assertEquals("original content", Files.readString(tempPath));
        }

        @Test
        @DisplayName("commitMove moves temp file to final destination")
        void commitMove_movesToFinalDestination() throws Exception {
            Path tempPath = tempDir.resolve("file.txt.tmp_move");
            Path target = tempDir.resolve("subdir/final.txt");
            Files.writeString(tempPath, "content");

            fileMoveHelper.commitMove(tempPath, target);

            assertFalse(Files.exists(tempPath), "Temp file should be removed");
            assertTrue(Files.exists(target), "Target should exist");
            assertEquals("content", Files.readString(target));
        }

        @Test
        @DisplayName("rollbackMove restores original file from temp location")
        void rollbackMove_restoresOriginalFile() throws Exception {
            Path tempPath = tempDir.resolve("file.txt.tmp_move");
            Path originalSource = tempDir.resolve("original.txt");
            Files.writeString(tempPath, "content");

            fileMoveHelper.rollbackMove(tempPath, originalSource);

            assertFalse(Files.exists(tempPath), "Temp file should be removed");
            assertTrue(Files.exists(originalSource), "Original should be restored");
            assertEquals("content", Files.readString(originalSource));
        }

        @Test
        @DisplayName("rollbackMove does nothing when temp file doesn't exist")
        void rollbackMove_noTempFile_doesNothing() {
            Path tempPath = tempDir.resolve("nonexistent.tmp_move");
            Path originalSource = tempDir.resolve("original.txt");

            assertDoesNotThrow(() -> fileMoveHelper.rollbackMove(tempPath, originalSource));
            assertFalse(Files.exists(originalSource));
        }
    }

    @Nested
    @DisplayName("Retry Mechanism Tests")
    class RetryMechanismTests {

        @Test
        @DisplayName("waitForFileAccessible succeeds immediately when file exists")
        void waitForFileAccessible_fileExists_succeedsImmediately() throws Exception {
            Path existingFile = tempDir.resolve("existing.txt");
            Files.writeString(existingFile, "content");

            long startTime = System.currentTimeMillis();
            boolean result = fileMoveHelper.waitForFileAccessible(existingFile);
            long elapsed = System.currentTimeMillis() - startTime;

            assertTrue(result);
            assertTrue(elapsed < 50, "Should return immediately without delays");
        }
    }

    @Nested
    @DisplayName("Directory Cleanup Tests")
    class DirectoryCleanupTests {

        @Test
        @DisplayName("Deletes empty parent directories up to library root")
        void deleteEmptyParentDirs_deletesEmptyDirectories() throws Exception {
            Path libraryRoot = tempDir.resolve("library");
            Path nestedDir = libraryRoot.resolve("author/series/book");
            Files.createDirectories(nestedDir);

            fileMoveHelper.deleteEmptyParentDirsUpToLibraryFolders(nestedDir, Set.of(libraryRoot));

            assertFalse(Files.exists(nestedDir));
            assertFalse(Files.exists(libraryRoot.resolve("author/series")));
            assertFalse(Files.exists(libraryRoot.resolve("author")));
            assertTrue(Files.exists(libraryRoot), "Library root should not be deleted");
        }

        @Test
        @DisplayName("Stops at directory containing non-ignored files")
        void deleteEmptyParentDirs_stopsAtNonEmptyDirectory() throws Exception {
            Path libraryRoot = tempDir.resolve("library");
            Path authorDir = libraryRoot.resolve("author");
            Path seriesDir = authorDir.resolve("series");
            Path bookDir = seriesDir.resolve("book");
            Files.createDirectories(bookDir);
            Files.writeString(authorDir.resolve("other-file.txt"), "content");

            fileMoveHelper.deleteEmptyParentDirsUpToLibraryFolders(bookDir, Set.of(libraryRoot));

            assertFalse(Files.exists(bookDir));
            assertFalse(Files.exists(seriesDir));
            assertTrue(Files.exists(authorDir), "Should stop at directory with other files");
            assertTrue(Files.exists(authorDir.resolve("other-file.txt")));
        }

        @Test
        @DisplayName("Deletes directories containing only ignored files (.DS_Store, Thumbs.db)")
        void deleteEmptyParentDirs_handlesMultipleIgnoredFiles() throws Exception {
            Path libraryRoot = tempDir.resolve("library");
            Path nestedDir = libraryRoot.resolve("author");
            Files.createDirectories(nestedDir);
            Files.writeString(nestedDir.resolve(".DS_Store"), "mac metadata");
            Files.writeString(nestedDir.resolve("Thumbs.db"), "windows thumbnail cache");

            fileMoveHelper.deleteEmptyParentDirsUpToLibraryFolders(nestedDir, Set.of(libraryRoot));

            assertFalse(Files.exists(nestedDir));
            assertTrue(Files.exists(libraryRoot));
        }

        @Test
        @DisplayName("Does not delete library root even if empty")
        void deleteEmptyParentDirs_neverDeletesLibraryRoot() throws Exception {
            Path libraryRoot = tempDir.resolve("library");
            Files.createDirectories(libraryRoot);

            fileMoveHelper.deleteEmptyParentDirsUpToLibraryFolders(libraryRoot, Set.of(libraryRoot));

            assertTrue(Files.exists(libraryRoot), "Library root should never be deleted");
        }

        @Test
        @DisplayName("Recursively deletes nested empty subdirectories")
        void deleteEmptyParentDirs_deletesNestedEmptySubdirectories() throws Exception {
            Path libraryRoot = tempDir.resolve("library");
            Path authorDir = libraryRoot.resolve("author");
            Path seriesDir = authorDir.resolve("series");
            Path deepEmptyDir = seriesDir.resolve("volume/chapter");
            Files.createDirectories(deepEmptyDir);

            fileMoveHelper.deleteEmptyParentDirsUpToLibraryFolders(authorDir, Set.of(libraryRoot));

            assertFalse(Files.exists(deepEmptyDir), "Deep empty directory should be deleted");
            assertFalse(Files.exists(seriesDir), "Series directory should be deleted");
            assertFalse(Files.exists(authorDir), "Author directory should be deleted");
            assertTrue(Files.exists(libraryRoot), "Library root should remain");
        }

        @Test
        @DisplayName("Does not delete subdirectories containing real files")
        void deleteEmptyParentDirs_preservesSubdirectoriesWithFiles() throws Exception {
            Path libraryRoot = tempDir.resolve("library");
            Path authorDir = libraryRoot.resolve("author");
            Path seriesWithBook = authorDir.resolve("series-with-book");
            Path emptySeriesDir = authorDir.resolve("empty-series");
            Files.createDirectories(seriesWithBook);
            Files.createDirectories(emptySeriesDir);
            Files.writeString(seriesWithBook.resolve("book.epub"), "ebook content");

            fileMoveHelper.deleteEmptyParentDirsUpToLibraryFolders(authorDir, Set.of(libraryRoot));

            assertFalse(Files.exists(emptySeriesDir), "Empty subdirectory should be deleted");
            assertTrue(Files.exists(seriesWithBook), "Subdirectory with files should remain");
            assertTrue(Files.exists(seriesWithBook.resolve("book.epub")), "File should remain");
            assertTrue(Files.exists(authorDir), "Parent should remain because it has non-empty subdirectory");
        }

        @Test
        @DisplayName("Deletes subdirectories containing only ignored files")
        void deleteEmptyParentDirs_deletesSubdirectoriesWithOnlyIgnoredFiles() throws Exception {
            Path libraryRoot = tempDir.resolve("library");
            Path authorDir = libraryRoot.resolve("author");
            Path subDirWithIgnored = authorDir.resolve("series");
            Files.createDirectories(subDirWithIgnored);
            Files.writeString(subDirWithIgnored.resolve(".DS_Store"), "mac metadata");
            Files.writeString(subDirWithIgnored.resolve("Thumbs.db"), "windows cache");

            fileMoveHelper.deleteEmptyParentDirsUpToLibraryFolders(authorDir, Set.of(libraryRoot));

            assertFalse(Files.exists(subDirWithIgnored), "Subdirectory with only ignored files should be deleted");
            assertFalse(Files.exists(authorDir), "Parent should be deleted");
            assertTrue(Files.exists(libraryRoot), "Library root should remain");
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("moveFile creates target directory if it doesn't exist")
        void moveFile_createsTargetDirectory() throws Exception {
            Path source = tempDir.resolve("source.txt");
            Path target = tempDir.resolve("deep/nested/directory/target.txt");
            Files.writeString(source, "content");

            fileMoveHelper.moveFile(source, target);

            assertTrue(Files.exists(target));
            assertTrue(Files.isDirectory(target.getParent()));
        }

        @Test
        @DisplayName("moveFile handles file with special characters in name")
        void moveFile_specialCharactersInName() throws Exception {
            Path source = tempDir.resolve("file with spaces & special (chars).txt");
            Path target = tempDir.resolve("renamed [file].txt");
            Files.writeString(source, "content");

            fileMoveHelper.moveFile(source, target);

            assertTrue(Files.exists(target));
            assertEquals("content", Files.readString(target));
        }

        @Test
        @DisplayName("deleteEmptyParentDirs handles symlinks safely")
        void deleteEmptyParentDirs_handlesSymlinks() throws Exception {
            Path libraryRoot = tempDir.resolve("library");
            Path realDir = tempDir.resolve("realdir");
            Path nestedDir = libraryRoot.resolve("nested");
            Files.createDirectories(realDir);
            Files.createDirectories(nestedDir);

            try {
                Path symlink = nestedDir.resolve("symlink");
                Files.createSymbolicLink(symlink, realDir);

                fileMoveHelper.deleteEmptyParentDirsUpToLibraryFolders(nestedDir, Set.of(libraryRoot));

                assertTrue(Files.exists(nestedDir), "Should not delete dir with symlink");
                assertTrue(Files.exists(realDir), "Real directory should still exist");
            } catch (UnsupportedOperationException e) {
                // Symlinks not supported on this system, skip test
            }
        }
    }
}
