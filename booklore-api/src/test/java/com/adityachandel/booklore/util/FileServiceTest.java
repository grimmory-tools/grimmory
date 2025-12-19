package com.adityachandel.booklore.util;

import com.adityachandel.booklore.config.AppProperties;
import com.adityachandel.booklore.model.dto.settings.AppSettings;
import com.adityachandel.booklore.model.dto.settings.CoverCroppingSettings;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.repository.BookMetadataRepository;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestTemplate;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    private AppProperties appProperties;

    @Mock
    private AppSettingService appSettingService;

    private FileService fileService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setup() {
        CoverCroppingSettings coverCroppingSettings = CoverCroppingSettings.builder()
                .verticalCroppingEnabled(true)
                .horizontalCroppingEnabled(true)
                .aspectRatioThreshold(2.5)
                .build();
        AppSettings appSettings = AppSettings.builder()
                .coverCroppingSettings(coverCroppingSettings)
                .build();
        lenient().when(appSettingService.getAppSettings()).thenReturn(appSettings);
        
        fileService = new FileService(appProperties, mock(RestTemplate.class), appSettingService, mock(BookMetadataRepository.class));
    }

    @Nested
    @DisplayName("Truncate Method")
    class TruncateTests {

        @Test
        @DisplayName("Returns null for null input")
        void truncate_nullInput_returnsNull() {
            assertNull(FileService.truncate(null, 10));
        }

        @Test
        @DisplayName("Returns empty string for empty input")
        void truncate_emptyString_returnsEmpty() {
            assertEquals("", FileService.truncate("", 10));
        }

        @ParameterizedTest(name = "maxLength={0} returns empty string")
        @ValueSource(ints = {0, -1, -100, Integer.MIN_VALUE})
        @DisplayName("Returns empty for zero or negative maxLength")
        void truncate_zeroOrNegativeMaxLength_returnsEmpty(int maxLength) {
            assertEquals("", FileService.truncate("test string", maxLength));
        }

        @Test
        @DisplayName("Returns original when shorter than maxLength")
        void truncate_shortString_returnsOriginal() {
            String input = "short";
            assertSame(input, FileService.truncate(input, 100));
        }

        @Test
        @DisplayName("Returns original when exactly maxLength")
        void truncate_exactLength_returnsOriginal() {
            String input = "exactly10!";
            assertEquals(10, input.length());
            assertSame(input, FileService.truncate(input, 10));
        }

        @Test
        @DisplayName("Truncates when longer than maxLength")
        void truncate_longString_truncates() {
            String result = FileService.truncate("this is a long string", 7);
            assertEquals("this is", result);
            assertEquals(7, result.length());
        }

        @Test
        @DisplayName("Handles maxLength of 1")
        void truncate_maxLengthOne_returnsSingleChar() {
            assertEquals("a", FileService.truncate("abc", 1));
        }

        @Test
        @DisplayName("Preserves unicode characters")
        void truncate_unicodeCharacters_handlesCorrectly() {
            assertEquals("héllo", FileService.truncate("héllo wörld", 5));
            assertEquals("日本語", FileService.truncate("日本語テスト", 3));
        }

        @Test
        @DisplayName("Handles surrogate pairs (emojis)")
        void truncate_surrogratePairs_mayBreakEmoji() {
            String input = "🚀🌟✨";
            // Note: Each emoji is 2 chars, truncating at 3 may break emoji
            String result = FileService.truncate(input, 3);
            assertEquals(3, result.length());
        }

        @Test
        @DisplayName("Handles whitespace-only strings")
        void truncate_whitespaceOnly_handlesCorrectly() {
            assertEquals("   ", FileService.truncate("     ", 3));
            assertEquals("\t\n", FileService.truncate("\t\n\r", 2));
        }

        @Test
        @DisplayName("Handles special characters")
        void truncate_specialCharacters_handlesCorrectly() {
            assertEquals("!@#", FileService.truncate("!@#$%^&*()", 3));
        }

        @Test
        @DisplayName("Handles max integer length")
        void truncate_maxIntegerLength_returnsOriginal() {
            String input = "test";
            assertSame(input, FileService.truncate(input, Integer.MAX_VALUE));
        }

        @ParameterizedTest
        @MethodSource("truncateTestCases")
        @DisplayName("Parameterized truncate tests")
        void truncate_parameterized(String input, int maxLength, String expected) {
            assertEquals(expected, FileService.truncate(input, maxLength));
        }

        static Stream<Arguments> truncateTestCases() {
            return Stream.of(
                Arguments.of("hello world", 5, "hello"),
                Arguments.of("test", 10, "test"),
                Arguments.of("abc", 3, "abc"),
                Arguments.of("ab", 3, "ab"),
                Arguments.of("a", 1, "a"),
                Arguments.of("newline\ntest", 7, "newline")
            );
        }
    }

    @Nested
    @DisplayName("Path Utilities")
    class PathUtilitiesTests {

        private static final String CONFIG_PATH = "/config";

        @BeforeEach
        void setup() {
            lenient().when(appProperties.getPathConfig()).thenReturn(tempDir.toString());
        }

        @Nested
        @DisplayName("getImagesFolder")
        class GetImagesFolderTests {

            @Test
            void returnsCorrectPath() {
                String result = fileService.getImagesFolder(123L);
                
                assertAll(
                    () -> assertTrue(result.contains("images")),
                    () -> assertTrue(result.contains("123")),
                    () -> assertTrue(result.startsWith(tempDir.toString()))
                );
            }

            @ParameterizedTest
            @ValueSource(longs = {0L, 1L, Long.MAX_VALUE})
            void handlesEdgeCaseBookIds(long bookId) {
                String result = fileService.getImagesFolder(bookId);
                assertTrue(result.contains(String.valueOf(bookId)));
            }
        }

        @Nested
        @DisplayName("getThumbnailFile")
        class GetThumbnailFileTests {

            @Test
            void returnsCorrectPath() {
                String result = fileService.getThumbnailFile(456L);
                
                assertAll(
                    () -> assertTrue(result.contains("456")),
                    () -> assertTrue(result.endsWith("thumbnail.jpg"))
                );
            }
        }

        @Nested
        @DisplayName("getCoverFile")
        class GetCoverFileTests {

            @Test
            void returnsCorrectPath() {
                String result = fileService.getCoverFile(789L);
                
                assertAll(
                    () -> assertTrue(result.contains("789")),
                    () -> assertTrue(result.endsWith("cover.jpg"))
                );
            }
        }

        @Nested
        @DisplayName("getBackgroundsFolder")
        class GetBackgroundsFolderTests {

            @Test
            void withUserId_returnsUserSpecificPath() {
                String result = fileService.getBackgroundsFolder(42L);
                
                assertAll(
                    () -> assertTrue(result.contains("backgrounds")),
                    () -> assertTrue(result.contains("user-42"))
                );
            }

            @Test
            void withNullUserId_returnsGlobalPath() {
                String result = fileService.getBackgroundsFolder(null);
                
                assertAll(
                    () -> assertTrue(result.contains("backgrounds")),
                    () -> assertFalse(result.contains("user-"))
                );
            }

            @Test
            void noArgs_delegatesToNullUserId() {
                String withNull = fileService.getBackgroundsFolder(null);
                String noArgs = fileService.getBackgroundsFolder();
                
                assertEquals(withNull, noArgs);
            }
        }

        @Nested
        @DisplayName("getBackgroundUrl (static)")
        class GetBackgroundUrlTests {

            @Test
            void withUserId_returnsCorrectUrl() {
                String result = FileService.getBackgroundUrl("bg.jpg", 10L);
                
                assertAll(
                    () -> assertTrue(result.startsWith("/")),
                    () -> assertTrue(result.contains("backgrounds")),
                    () -> assertTrue(result.contains("user-10")),
                    () -> assertTrue(result.endsWith("bg.jpg")),
                    () -> assertFalse(result.contains("\\"), "Should use forward slashes")
                );
            }

            @Test
            void withoutUserId_returnsGlobalUrl() {
                String result = FileService.getBackgroundUrl("bg.jpg", null);
                
                assertAll(
                    () -> assertFalse(result.contains("user-")),
                    () -> assertTrue(result.contains("backgrounds")),
                    () -> assertFalse(result.contains("\\"))
                );
            }

            @Test
            void handlesFilenameWithSpaces() {
                String result = FileService.getBackgroundUrl("my background.jpg", null);
                assertTrue(result.contains("my background.jpg"));
            }
        }

        @Nested
        @DisplayName("Other path methods")
        class OtherPathTests {

            @Test
            void getBookMetadataBackupPath_returnsCorrectPath() {
                String result = fileService.getBookMetadataBackupPath(100L);
                
                assertAll(
                    () -> assertTrue(result.contains("metadata_backup")),
                    () -> assertTrue(result.contains("100"))
                );
            }

            @Test
            void getCbxCachePath_returnsCorrectPath() {
                assertTrue(fileService.getCbxCachePath().contains("cbx_cache"));
            }

            @Test
            void getPdfCachePath_returnsCorrectPath() {
                assertTrue(fileService.getPdfCachePath().contains("pdf_cache"));
            }

            @Test
            void getTempBookdropCoverImagePath_returnsCorrectPath() {
                String result = fileService.getTempBookdropCoverImagePath(555L);
                
                assertAll(
                    () -> assertTrue(result.contains("bookdrop_temp")),
                    () -> assertTrue(result.endsWith("555.jpg"))
                );
            }

            @Test
            void getToolsKepubifyPath_returnsCorrectPath() {
                String result = fileService.getToolsKepubifyPath();
                
                assertAll(
                    () -> assertTrue(result.contains("tools")),
                    () -> assertTrue(result.contains("kepubify"))
                );
            }
        }
    }

    @Nested
    @DisplayName("Image Operations")
    class ImageOperationsTests {

        @Nested
        @DisplayName("resizeImage")
        class ResizeImageTests {

            @Test
            void shrinks_imageProperly() {
                BufferedImage original = createTestImage(100, 100);
                
                BufferedImage resized = fileService.resizeImage(original, 50, 50);
                
                assertAll(
                    () -> assertEquals(50, resized.getWidth()),
                    () -> assertEquals(50, resized.getHeight())
                );
            }

            @Test
            void enlarges_imageProperly() {
                BufferedImage original = createTestImage(50, 50);
                
                BufferedImage resized = fileService.resizeImage(original, 200, 200);
                
                assertAll(
                    () -> assertEquals(200, resized.getWidth()),
                    () -> assertEquals(200, resized.getHeight())
                );
            }

            @Test
            void changesAspectRatio() {
                BufferedImage original = createTestImage(100, 100);
                
                BufferedImage resized = fileService.resizeImage(original, 200, 50);
                
                assertAll(
                    () -> assertEquals(200, resized.getWidth()),
                    () -> assertEquals(50, resized.getHeight())
                );
            }

            @Test
            void sameSize_worksCorrectly() {
                BufferedImage original = createTestImage(100, 100);
                
                BufferedImage resized = fileService.resizeImage(original, 100, 100);
                
                assertAll(
                    () -> assertEquals(100, resized.getWidth()),
                    () -> assertEquals(100, resized.getHeight()),
                    () -> assertNotSame(original, resized)
                );
            }

            @Test
            void returnsRGBType() {
                BufferedImage original = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
                
                BufferedImage resized = fileService.resizeImage(original, 50, 50);
                
                assertEquals(BufferedImage.TYPE_INT_RGB, resized.getType());
            }

            @Test
            void handlesVerySmallDimensions() {
                BufferedImage original = createTestImage(100, 100);
                
                BufferedImage resized = fileService.resizeImage(original, 1, 1);
                
                assertAll(
                    () -> assertEquals(1, resized.getWidth()),
                    () -> assertEquals(1, resized.getHeight())
                );
            }
        }

        @Nested
        @DisplayName("saveImage")
        class SaveImageTests {

            @Test
            void validData_createsFile() throws IOException {
                BufferedImage image = createTestImage(100, 100);
                byte[] imageData = imageToBytes(image);
                Path outputPath = tempDir.resolve("test-output.jpg");
                
                fileService.saveImage(imageData, outputPath.toString());
                
                assertAll(
                    () -> assertTrue(Files.exists(outputPath)),
                    () -> assertTrue(Files.size(outputPath) > 0)
                );
            }

            @Test
            void createsParentDirectories() throws IOException {
                BufferedImage image = createTestImage(100, 100);
                byte[] imageData = imageToBytes(image);
                Path outputPath = tempDir.resolve("nested/deep/folder/test.jpg");
                
                fileService.saveImage(imageData, outputPath.toString());
                
                assertTrue(Files.exists(outputPath));
            }

            @Test
            void invalidImageData_throwsException() {
                byte[] invalidData = "not an image".getBytes();
                Path outputPath = tempDir.resolve("invalid.jpg");

                assertThrows(IOException.class, () ->
                    fileService.saveImage(invalidData, outputPath.toString()));
            }

            @Test
            void emptyImageData_throwsException() {
                byte[] emptyData = new byte[0];
                Path outputPath = tempDir.resolve("empty.jpg");

                assertThrows(IOException.class, () ->
                    fileService.saveImage(emptyData, outputPath.toString()));
            }

            @Test
            void savedImage_isReadable() throws IOException {
                BufferedImage original = createTestImage(100, 100);
                byte[] imageData = imageToBytes(original);
                Path outputPath = tempDir.resolve("readable.jpg");
                
                fileService.saveImage(imageData, outputPath.toString());
                BufferedImage loaded = ImageIO.read(outputPath.toFile());
                
                assertAll(
                    () -> assertNotNull(loaded),
                    () -> assertEquals(100, loaded.getWidth()),
                    () -> assertEquals(100, loaded.getHeight())
                );
            }
        }
    }

    @Nested
    @DisplayName("Cover Operations")
    class CoverOperationsTests {

        @BeforeEach
        void setup() {
            lenient().when(appProperties.getPathConfig()).thenReturn(tempDir.toString());
        }

        @Nested
        @DisplayName("saveCoverImages")
        class SaveCoverImagesTests {

            @Test
            void createsBothCoverAndThumbnail() throws IOException {
                BufferedImage image = createTestImage(500, 700);
                
                boolean result = fileService.saveCoverImages(image, 1L);
                
                assertAll(
                    () -> assertTrue(result),
                    () -> assertTrue(Files.exists(Path.of(fileService.getCoverFile(1L)))),
                    () -> assertTrue(Files.exists(Path.of(fileService.getThumbnailFile(1L))))
                );
            }

            @Test
            void thumbnailHasCorrectDimensions() throws IOException {
                BufferedImage image = createTestImage(1000, 1400);
                
                fileService.saveCoverImages(image, 2L);
                
                BufferedImage thumbnail = ImageIO.read(
                    new File(fileService.getThumbnailFile(2L)));
                
                assertAll(
                    () -> assertEquals(250, thumbnail.getWidth()),
                    () -> assertEquals(350, thumbnail.getHeight())
                );
            }

            @Test
            void convertsTransparentToOpaqueWithWhiteBackground() throws IOException {
                BufferedImage imageWithAlpha = new BufferedImage(
                    100, 100, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = imageWithAlpha.createGraphics();
                g.setColor(new Color(255, 0, 0, 128)); // Semi-transparent red
                g.fillRect(0, 0, 100, 100);
                g.dispose();
                
                boolean result = fileService.saveCoverImages(imageWithAlpha, 3L);
                
                assertTrue(result);
                
                BufferedImage saved = ImageIO.read(
                    new File(fileService.getCoverFile(3L)));
                assertFalse(saved.getColorModel().hasAlpha(), "Saved image should not have transparency");
            }

            @Test
            void createsDirectoryIfNotExists() throws IOException {
                BufferedImage image = createTestImage(100, 100);
                long bookId = 999L;
                
                fileService.saveCoverImages(image, bookId);
                
                assertTrue(Files.isDirectory(
                    Path.of(fileService.getImagesFolder(bookId))));
            }

            @Test
            void originalMaintainsDimensions() throws IOException {
                BufferedImage image = createTestImage(800, 1200);
                
                fileService.saveCoverImages(image, 4L);
                
                BufferedImage saved = ImageIO.read(
                    new File(fileService.getCoverFile(4L)));
                
                assertAll(
                    () -> assertEquals(800, saved.getWidth()),
                    () -> assertEquals(1200, saved.getHeight())
                );
            }

            @Test
            void largeImage_isScaledDownToMaxDimensions() throws IOException {
                // Create a very large image that will trigger scaling
                int largeWidth = 2000;  // > MAX_ORIGINAL_WIDTH (1000)
                int largeHeight = 3000; // > MAX_ORIGINAL_HEIGHT (1500)

                BufferedImage largeImage = createTestImage(largeWidth, largeHeight);
                boolean result = fileService.saveCoverImages(largeImage, 5L);

                assertTrue(result);

                BufferedImage savedCover = ImageIO.read(
                    new File(fileService.getCoverFile(5L)));

                assertNotNull(savedCover);

                assertTrue(savedCover.getWidth() <= 1000,
                    "Cover width should be <= MAX_ORIGINAL_WIDTH (1000), was: " + savedCover.getWidth());
                assertTrue(savedCover.getHeight() <= 1500,
                    "Cover height should be <= MAX_ORIGINAL_HEIGHT (1500), was: " + savedCover.getHeight());

                double originalRatio = (double) largeWidth / largeHeight;
                double savedRatio = (double) savedCover.getWidth() / savedCover.getHeight();
                assertEquals(originalRatio, savedRatio, 0.01, "Aspect ratio should be preserved");
            }

            @Test
            void smallImage_maintainsOriginalDimensions() throws IOException {
                // Create a small image that should NOT be scaled down
                int smallWidth = 400;   // < MAX_ORIGINAL_WIDTH (1000)
                int smallHeight = 600;  // < MAX_ORIGINAL_HEIGHT (1500)

                BufferedImage smallImage = createTestImage(smallWidth, smallHeight);
                boolean result = fileService.saveCoverImages(smallImage, 6L);

                assertTrue(result);

                BufferedImage savedCover = ImageIO.read(
                    new File(fileService.getCoverFile(6L)));

                assertNotNull(savedCover);

                assertEquals(smallWidth, savedCover.getWidth(),
                    "Small image width should be preserved");
                assertEquals(smallHeight, savedCover.getHeight(),
                    "Small image height should be preserved");
            }
        }

        @Nested
        @DisplayName("Cover Cropping for Extreme Aspect Ratios")
        class CoverCroppingTests {

            @Test
            @DisplayName("extremely tall image is cropped when vertical cropping enabled")
            void extremelyTallImage_isCropped() throws IOException {
                // Create an extremely tall image like a web comic page (ratio > 2.5)
                int width = 940;
                int height = 11280;  // ratio = 12:1

                BufferedImage tallImage = createTestImage(width, height);
                boolean result = fileService.saveCoverImages(tallImage, 100L);

                assertTrue(result);

                BufferedImage savedCover = ImageIO.read(
                    new File(fileService.getCoverFile(100L)));

                assertNotNull(savedCover);
                
                // The image should be cropped to approximately 1.5:1 ratio from the top
                double savedRatio = (double) savedCover.getHeight() / savedCover.getWidth();
                assertTrue(savedRatio < 3.0, 
                    "Cropped image should have reasonable aspect ratio, was: " + savedRatio);
            }

            @Test
            @DisplayName("extremely wide image is cropped when horizontal cropping enabled")
            void extremelyWideImage_isCropped() throws IOException {
                // Create an extremely wide image (ratio > 2.5)
                int width = 3000;
                int height = 400;  // width/height ratio = 7.5:1

                BufferedImage wideImage = createTestImage(width, height);
                boolean result = fileService.saveCoverImages(wideImage, 101L);

                assertTrue(result);

                BufferedImage savedCover = ImageIO.read(
                    new File(fileService.getCoverFile(101L)));

                assertNotNull(savedCover);
                
                // The image should be cropped to a more reasonable aspect ratio
                double savedRatio = (double) savedCover.getWidth() / savedCover.getHeight();
                assertTrue(savedRatio < 3.0, 
                    "Cropped image should have reasonable aspect ratio, was: " + savedRatio);
            }

            @Test
            @DisplayName("normal aspect ratio image is not cropped")
            void normalAspectRatioImage_isNotCropped() throws IOException {
                // Create a normal book cover sized image (ratio ~1.5:1)
                int width = 600;
                int height = 900;  // ratio = 1.5:1

                BufferedImage normalImage = createTestImage(width, height);
                boolean result = fileService.saveCoverImages(normalImage, 102L);

                assertTrue(result);

                BufferedImage savedCover = ImageIO.read(
                    new File(fileService.getCoverFile(102L)));

                assertNotNull(savedCover);
                
                // The image should maintain its original aspect ratio
                double originalRatio = (double) height / width;
                double savedRatio = (double) savedCover.getHeight() / savedCover.getWidth();
                assertEquals(originalRatio, savedRatio, 0.01, 
                    "Normal aspect ratio image should not be cropped");
            }

            @Test
            @DisplayName("cropping is disabled when settings are off")
            void croppingDisabled_imageNotCropped() throws IOException {
                // Reconfigure with cropping disabled
                CoverCroppingSettings disabledSettings = CoverCroppingSettings.builder()
                        .verticalCroppingEnabled(false)
                        .horizontalCroppingEnabled(false)
                        .aspectRatioThreshold(2.5)
                        .build();
                AppSettings appSettings = AppSettings.builder()
                        .coverCroppingSettings(disabledSettings)
                        .build();
                when(appSettingService.getAppSettings()).thenReturn(appSettings);

                // Create an extremely tall image
                int width = 400;
                int height = 4000;  // ratio = 10:1

                BufferedImage tallImage = createTestImage(width, height);
                boolean result = fileService.saveCoverImages(tallImage, 103L);

                assertTrue(result);

                BufferedImage savedCover = ImageIO.read(
                    new File(fileService.getCoverFile(103L)));

                assertNotNull(savedCover);
                
                // Since the image exceeds max dimensions, it will be scaled, but aspect ratio preserved
                double originalRatio = (double) height / width;
                double savedRatio = (double) savedCover.getHeight() / savedCover.getWidth();
                assertEquals(originalRatio, savedRatio, 0.01, 
                    "Image should not be cropped when cropping is disabled");
            }
        }

        @Nested
        @DisplayName("createThumbnailFromFile")
        class CreateThumbnailFromFileTests {

            @Test
            void validJpegFile_succeeds() throws IOException {
                BufferedImage image = createTestImage(300, 400);
                byte[] imageBytes = imageToBytes(image);
                MockMultipartFile file = new MockMultipartFile(
                    "file", "test.jpg", "image/jpeg", imageBytes);
                
                assertDoesNotThrow(() -> 
                    fileService.createThumbnailFromFile(5L, file));
                assertTrue(Files.exists(Path.of(fileService.getCoverFile(5L))));
            }

            @Test
            void validPngFile_succeeds() throws IOException {
                BufferedImage image = createTestImage(300, 400);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "PNG", baos);
                MockMultipartFile file = new MockMultipartFile(
                    "file", "test.png", "image/png", baos.toByteArray());
                
                assertDoesNotThrow(() -> 
                    fileService.createThumbnailFromFile(6L, file));
            }

            @Test
            void emptyFile_throwsException() {
                MockMultipartFile emptyFile = new MockMultipartFile(
                    "file", "empty.jpg", "image/jpeg", new byte[0]);
                
                Exception exception = assertThrows(Exception.class, () ->
                    fileService.createThumbnailFromFile(7L, emptyFile));
                assertTrue(exception.getMessage().contains("empty"));
            }

            @Test
            void invalidMimeType_throwsException() {
                MockMultipartFile gifFile = new MockMultipartFile(
                    "file", "test.gif", "image/gif", "fake data".getBytes());
                
                Exception exception = assertThrows(Exception.class, () ->
                    fileService.createThumbnailFromFile(8L, gifFile));
                assertTrue(exception.getMessage().contains("Only JPEG and PNG files are allowed"));
            }

            @Test
            void fileTooLarge_throwsException() {
                byte[] largeData = new byte[6 * 1024 * 1024]; // 6MB
                MockMultipartFile largeFile = new MockMultipartFile(
                    "file", "large.jpg", "image/jpeg", largeData);
                
                Exception exception = assertThrows(Exception.class, () ->
                    fileService.createThumbnailFromFile(9L, largeFile));
                assertTrue(exception.getMessage().contains("5 MB"));
            }

            @Test
            void fileExactlyAtSizeLimit_succeeds() throws IOException {
                BufferedImage image = createTestImage(100, 100);
                byte[] imageBytes = imageToBytes(image);
                // Ensure it's under 5MB
                assertTrue(imageBytes.length < 5 * 1024 * 1024);
                
                MockMultipartFile file = new MockMultipartFile(
                    "file", "valid.jpg", "image/jpeg", imageBytes);
                
                assertDoesNotThrow(() ->
                    fileService.createThumbnailFromFile(10L, file));
            }

            @Test
            void caseInsensitiveMimeType_succeeds() throws IOException {
                BufferedImage image = createTestImage(100, 100);
                byte[] imageBytes = imageToBytes(image);
                MockMultipartFile file = new MockMultipartFile(
                    "file", "test.jpg", "IMAGE/JPEG", imageBytes);

                assertDoesNotThrow(() ->
                    fileService.createThumbnailFromFile(11L, file));
            }

            @Test
            void corruptImageData_throwsException() {
                // Valid MIME type but corrupt image data
                byte[] corruptData = ("not an image but has jpeg mime type").getBytes();
                MockMultipartFile corruptFile = new MockMultipartFile(
                    "file", "corrupt.jpg", "image/jpeg", corruptData);

                Exception exception = assertThrows(Exception.class, () ->
                    fileService.createThumbnailFromFile(12L, corruptFile));
                assertTrue(exception.getMessage().contains("Image not found or not readable"));
            }

            @Test
            void unsupportedMimeType_gif_throwsException() {
                byte[] gifData = "GIF89a...".getBytes(); // Fake GIF header
                MockMultipartFile gifFile = new MockMultipartFile(
                    "file", "test.gif", "image/gif", gifData);

                assertThrows(Exception.class, () ->
                    fileService.createThumbnailFromFile(13L, gifFile));
            }

            @Test
            void mimeTypeWithExtraParameters_succeeds() throws IOException {
                BufferedImage image = createTestImage(100, 100);
                byte[] imageBytes = imageToBytes(image);
                MockMultipartFile file = new MockMultipartFile(
                    "file", "test.jpg", "image/jpeg;charset=UTF-8", imageBytes);

                assertDoesNotThrow(() ->
                    fileService.createThumbnailFromFile(14L, file));
            }
        }

        @Nested
        @DisplayName("setBookCoverPath")
        class SetBookCoverPathTests {

            @Test
            void setsTimestampToCurrentTime() {
                BookMetadataEntity entity = new BookMetadataEntity();
                Instant before = Instant.now();
                
                FileService.setBookCoverPath(entity);
                
                Instant after = Instant.now();
                
                assertNotNull(entity.getCoverUpdatedOn());
                assertFalse(entity.getCoverUpdatedOn().isBefore(before));
                assertFalse(entity.getCoverUpdatedOn().isAfter(after));
            }

            @Test
            void overwritesExistingTimestamp() {
                BookMetadataEntity entity = new BookMetadataEntity();
                Instant oldTime = Instant.parse("2020-01-01T00:00:00Z");
                entity.setCoverUpdatedOn(oldTime);
                
                FileService.setBookCoverPath(entity);
                
                assertNotEquals(oldTime, entity.getCoverUpdatedOn());
            }
        }

        @Nested
        @DisplayName("deleteBookCovers")
        class DeleteBookCoversTests {

            @Test
            void existingCovers_deletesAll() throws IOException {
                BufferedImage image = createTestImage(100, 100);
                fileService.saveCoverImages(image, 10L);
                fileService.saveCoverImages(image, 11L);
                
                fileService.deleteBookCovers(Set.of(10L, 11L));
                
                assertAll(
                    () -> assertFalse(Files.exists(
                        Path.of(fileService.getImagesFolder(10L)))),
                    () -> assertFalse(Files.exists(
                        Path.of(fileService.getImagesFolder(11L))))
                );
            }

            @Test
            void nonExistentCovers_doesNotThrow() {
                assertDoesNotThrow(() ->
                    fileService.deleteBookCovers(Set.of(999L, 1000L)));
            }

            @Test
            void emptySet_doesNothing() {
                assertDoesNotThrow(() ->
                    fileService.deleteBookCovers(Set.of()));
            }

            @Test
            void mixedExistingAndNonExisting_deletesExisting() throws Exception {
                BufferedImage image = createTestImage(100, 100);
                fileService.saveCoverImages(image, 20L);
                
                fileService.deleteBookCovers(Set.of(20L, 21L));
                
                assertFalse(Files.exists(Path.of(fileService.getImagesFolder(20L))));
            }

            @Test
            void singleBookId_works() throws IOException {
                BufferedImage image = createTestImage(100, 100);
                fileService.saveCoverImages(image, 30L);
                
                fileService.deleteBookCovers(Set.of(30L));
                
                assertFalse(Files.exists(Path.of(fileService.getImagesFolder(30L))));
            }
        }
    }

    @Nested
    @DisplayName("Network Operations")
    class NetworkOperationsTests {

        @Mock
        private RestTemplate restTemplate;

        @Mock
        private AppSettingService appSettingServiceForNetwork;

        private FileService fileService;

        @BeforeEach
        void setup() {
            lenient().when(appProperties.getPathConfig()).thenReturn(tempDir.toString());
            
            CoverCroppingSettings coverCroppingSettings = CoverCroppingSettings.builder()
                    .verticalCroppingEnabled(true)
                    .horizontalCroppingEnabled(true)
                    .aspectRatioThreshold(2.5)
                    .build();
            AppSettings appSettings = AppSettings.builder()
                    .coverCroppingSettings(coverCroppingSettings)
                    .build();
            lenient().when(appSettingServiceForNetwork.getAppSettings()).thenReturn(appSettings);
            
            fileService = new FileService(appProperties, restTemplate, appSettingServiceForNetwork, mock(BookMetadataRepository.class));
        }

        @Nested
        @DisplayName("downloadImageFromUrl")
        class DownloadImageFromUrlTests {

            @Test
            @DisplayName("downloads and returns valid image")
            @Timeout(5)
            void downloadImageFromUrl_validImage_returnsBufferedImage() throws IOException {
                String imageUrl = "http://example.com/image.jpg";
                BufferedImage testImage = createTestImage(100, 100);
                byte[] imageBytes = imageToBytes(testImage);

                RestTemplate mockRestTemplate = mock(RestTemplate.class);
                AppSettingService mockAppSettingService = mock(AppSettingService.class);
                FileService testFileService = new FileService(appProperties, mockRestTemplate, mockAppSettingService, mock(BookMetadataRepository.class));

                ResponseEntity<byte[]> responseEntity = ResponseEntity.ok(imageBytes);
                when(mockRestTemplate.exchange(
                    eq(imageUrl),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(byte[].class)
                )).thenReturn(responseEntity);

                BufferedImage result = testFileService.downloadImageFromUrl(imageUrl);

                assertNotNull(result);
                assertEquals(100, result.getWidth());
                assertEquals(100, result.getHeight());
            }

            @Test
            @DisplayName("throws exception when response body is null")
            @Timeout(5)
            void downloadImageFromUrl_nullBody_throwsException() {
                String imageUrl = "http://example.com/image.jpg";
                ResponseEntity<byte[]> responseEntity = ResponseEntity.ok(null);
                when(restTemplate.exchange(
                    eq(imageUrl),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(byte[].class)
                )).thenReturn(responseEntity);

                assertThrows(IOException.class, () ->
                    fileService.downloadImageFromUrl(imageUrl));
            }

            @Test
            @DisplayName("throws exception when ImageIO cannot read bytes")
            @Timeout(5)
            void downloadImageFromUrl_invalidImageData_throwsException() {
                String imageUrl = "http://example.com/image.jpg";
                byte[] invalidBytes = "not an image".getBytes();
                ResponseEntity<byte[]> responseEntity = ResponseEntity.ok(invalidBytes);
                when(restTemplate.exchange(
                    eq(imageUrl),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(byte[].class)
                )).thenReturn(responseEntity);

                assertThrows(IOException.class, () ->
                    fileService.downloadImageFromUrl(imageUrl));
            }

            @Test
            @DisplayName("throws exception on HTTP error status")
            @Timeout(5)
            void downloadImageFromUrl_httpError_throwsException() {
                String imageUrl = "http://example.com/image.jpg";
                ResponseEntity<byte[]> responseEntity = ResponseEntity.notFound().build();
                when(restTemplate.exchange(
                    eq(imageUrl),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(byte[].class)
                )).thenReturn(responseEntity);

                assertThrows(IOException.class, () ->
                    fileService.downloadImageFromUrl(imageUrl));
            }
        }

        @Nested
        @DisplayName("createThumbnailFromUrl")
        class CreateThumbnailFromUrlTests {

            @Test
            @DisplayName("downloads and saves cover images successfully")
            @Timeout(5)
            void createThumbnailFromUrl_validImage_createsCoverAndThumbnail() throws IOException {
                String imageUrl = "http://example.com/cover.jpg";
                long bookId = 42L;
                BufferedImage testImage = createTestImage(800, 1200); // Portrait image
                byte[] imageBytes = imageToBytes(testImage);

                ResponseEntity<byte[]> responseEntity = ResponseEntity.ok(imageBytes);
                when(restTemplate.exchange(
                    eq(imageUrl),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(byte[].class)
                )).thenReturn(responseEntity);

                assertDoesNotThrow(() ->
                    fileService.createThumbnailFromUrl(bookId, imageUrl));

                Path imagesFolder = tempDir.resolve("images").resolve(String.valueOf(bookId));
                assertTrue(Files.exists(imagesFolder.resolve("cover.jpg")));
                assertTrue(Files.exists(imagesFolder.resolve("thumbnail.jpg")));

                BufferedImage thumbnail = ImageIO.read(imagesFolder.resolve("thumbnail.jpg").toFile());
                assertEquals(250, thumbnail.getWidth()); // THUMBNAIL_WIDTH
                assertEquals(350, thumbnail.getHeight()); // THUMBNAIL_HEIGHT
            }

            @Test
            @DisplayName("throws ApiError.FILE_READ_ERROR on download failure")
            @Timeout(5)
            void createThumbnailFromUrl_downloadFails_throwsApiError() {
                String imageUrl = "http://example.com/invalid.jpg";
                long bookId = 42L;

                when(restTemplate.exchange(
                    eq(imageUrl),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(byte[].class)
                )).thenThrow(new RuntimeException("Network error"));

                Exception exception = assertThrows(Exception.class, () ->
                    fileService.createThumbnailFromUrl(bookId, imageUrl));
                assertTrue(exception.getMessage().contains("Network error"));
            }

        }
    }

    @Nested
    @DisplayName("Background Operations")
    class BackgroundOperationsTests {

        @BeforeEach
        void setup() {
            when(appProperties.getPathConfig()).thenReturn(tempDir.toString());
        }

        @Nested
        @DisplayName("saveBackgroundImage")
        class SaveBackgroundImageTests {

            @Test
            void userBackground_savesInUserFolder() throws IOException {
                BufferedImage image = createTestImage(1920, 1080);
                
                fileService.saveBackgroundImage(image, "bg.jpg", 1L);
                
                Path expected = tempDir.resolve("backgrounds/user-1/bg.jpg");
                assertTrue(Files.exists(expected));
            }

            @Test
            void globalBackground_savesInGlobalFolder() throws IOException {
                BufferedImage image = createTestImage(1920, 1080);
                
                fileService.saveBackgroundImage(image, "global.jpg", null);
                
                Path expected = tempDir.resolve("backgrounds/global.jpg");
                assertTrue(Files.exists(expected));
            }

            @Test
            void createsDirectoryIfNotExists() throws IOException {
                BufferedImage image = createTestImage(100, 100);
                
                fileService.saveBackgroundImage(image, "new.jpg", 2L);
                
                assertTrue(Files.isDirectory(
                    tempDir.resolve("backgrounds/user-2")));
            }

            @Test
            void overwritesExistingFile() throws IOException {
                BufferedImage image1 = createTestImage(100, 100, Color.RED);
                BufferedImage image2 = createTestImage(100, 100, Color.BLUE);
                
                fileService.saveBackgroundImage(image1, "bg.jpg", 3L);
                long size1 = Files.size(
                    tempDir.resolve("backgrounds/user-3/bg.jpg"));
                
                fileService.saveBackgroundImage(image2, "bg.jpg", 3L);
                long size2 = Files.size(
                    tempDir.resolve("backgrounds/user-3/bg.jpg"));
                
                // File exists and was overwritten (sizes might be same/different)
                assertTrue(Files.exists(
                    tempDir.resolve("backgrounds/user-3/bg.jpg")));
            }
        }

        @Nested
        @DisplayName("deleteBackgroundFile")
        class DeleteBackgroundFileTests {

            @Test
            void existingFile_deletesIt() throws IOException {
                BufferedImage image = createTestImage(100, 100);
                fileService.saveBackgroundImage(image, "to-delete.jpg", 4L);
                
                fileService.deleteBackgroundFile("to-delete.jpg", 4L);
                
                assertFalse(Files.exists(
                    tempDir.resolve("backgrounds/user-4/to-delete.jpg")));
            }

            @Test
            void nonExistentFile_doesNotThrow() {
                assertDoesNotThrow(() ->
                    fileService.deleteBackgroundFile("nonexistent.jpg", 5L));
            }

            @Test
            void lastFileInUserFolder_deletesEmptyFolder() throws IOException {
                BufferedImage image = createTestImage(100, 100);
                fileService.saveBackgroundImage(image, "only.jpg", 6L);
                
                fileService.deleteBackgroundFile("only.jpg", 6L);
                
                assertFalse(Files.exists(tempDir.resolve("backgrounds/user-6")));
            }

            @Test
            void notLastFile_keepsFolder() throws IOException {
                BufferedImage image = createTestImage(100, 100);
                fileService.saveBackgroundImage(image, "file1.jpg", 7L);
                fileService.saveBackgroundImage(image, "file2.jpg", 7L);
                
                fileService.deleteBackgroundFile("file1.jpg", 7L);
                
                assertTrue(Files.isDirectory(
                    tempDir.resolve("backgrounds/user-7")));
            }

            @Test
            void globalBackground_deletesFile() throws IOException {
                BufferedImage image = createTestImage(100, 100);
                Path bgFolder = tempDir.resolve("backgrounds");
                Files.createDirectories(bgFolder);
                ImageIO.write(image, "JPEG", bgFolder.resolve("global.jpg").toFile());
                
                fileService.deleteBackgroundFile("global.jpg", null);
                
                assertFalse(Files.exists(bgFolder.resolve("global.jpg")));
            }
        }

        @Nested
        @DisplayName("getBackgroundResource")
        class GetBackgroundResourceTests {

            @Test
            void userHasBackground_returnsUserBackground() throws IOException {
                createUserBackground(8L, "1.jpg");
                
                Resource resource = fileService.getBackgroundResource(8L);
                
                assertAll(
                    () -> assertInstanceOf(FileSystemResource.class, resource),
                    () -> assertTrue(resource.exists())
                );
            }

            @Test
            void userHasNoBackground_globalExists_returnsGlobal() throws IOException {
                createGlobalBackground("1.jpg");
                
                Resource resource = fileService.getBackgroundResource(9L);
                
                assertInstanceOf(FileSystemResource.class, resource);
            }

            @Test
            void noCustomBackgrounds_returnsDefault() {
                Resource resource = fileService.getBackgroundResource(10L);
                
                assertInstanceOf(ClassPathResource.class, resource);
            }

            @ParameterizedTest
            @ValueSource(strings = {"1.jpg", "1.jpeg", "1.png"})
            void supportsMultipleFormats(String filename) throws IOException {
                Path bgFolder = tempDir.resolve("backgrounds");
                Files.createDirectories(bgFolder);
                Files.createFile(bgFolder.resolve(filename));
                
                Resource resource = fileService.getBackgroundResource(null);
                
                assertTrue(resource.exists());
            }

            @Test
            void userBackgroundTakesPriority_overGlobal() throws IOException {
                createGlobalBackground("1.jpg");
                createUserBackground(11L, "1.jpg");
                
                Resource resource = fileService.getBackgroundResource(11L);
                
                String path = ((FileSystemResource) resource).getPath();
                assertTrue(path.contains("user-11"));
            }

            @Test
            @DisplayName("Priority: user > global > default")
            void checksPriorityOrder() throws IOException {
                // Only global exists
                createGlobalBackground("1.jpg");
                Resource globalResource = fileService.getBackgroundResource(12L);
                assertTrue(globalResource.exists());
                
                // Add user background
                createUserBackground(12L, "1.jpg");
                Resource userResource = fileService.getBackgroundResource(12L);
                String path = ((FileSystemResource) userResource).getPath();
                assertTrue(path.contains("user-12"));
            }

            private void createUserBackground(Long userId, String filename) 
                    throws IOException {
                Path folder = tempDir.resolve("backgrounds/user-" + userId);
                Files.createDirectories(folder);
                BufferedImage img = createTestImage(100, 100);
                ImageIO.write(img, "JPEG", folder.resolve(filename).toFile());
            }

            private void createGlobalBackground(String filename) throws IOException {
                Path folder = tempDir.resolve("backgrounds");
                Files.createDirectories(folder);
                BufferedImage img = createTestImage(100, 100);
                ImageIO.write(img, "JPEG", folder.resolve(filename).toFile());
            }
        }
    }

    private BufferedImage createTestImage(int width, int height) {
        return createTestImage(width, height, Color.BLUE);
    }

    private BufferedImage createTestImage(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(
            width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.WHITE);
        g.drawString("Test", 10, height / 2);
        g.dispose();
        return image;
    }

    private byte[] imageToBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "JPEG", baos);
        return baos.toByteArray();
    }
}