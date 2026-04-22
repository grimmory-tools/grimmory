package org.booklore.util;

import org.booklore.config.AppProperties;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.settings.CoverCroppingSettings;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.service.appsettings.AppSettingService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
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
                .smartCroppingEnabled(true).build();
        AppSettings appSettings = AppSettings.builder()
                .coverCroppingSettings(coverCroppingSettings)
                .build();
        lenient().when(appSettingService.getAppSettings()).thenReturn(appSettings);

        RestTemplate mockRestTemplate = mock(RestTemplate.class);
        RestTemplate mockNoRedirectRestTemplate = mock(RestTemplate.class);
        fileService = new FileService(appProperties, mockRestTemplate, appSettingService, mockNoRedirectRestTemplate);
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
        @DisplayName("findSystemFile")
        class FindSystemFileTest {
            @Test
            void searchesLocalBinFolderFirst() {
                Path expected = Path.of("bin/example").toAbsolutePath().normalize();

                try (
                    MockedStatic<Files> filesMock = mockStatic(Files.class);
                ) {
                    filesMock.when(() -> Files.isRegularFile(any())).thenReturn(true);

                    Path actual = fileService.findSystemFile("example");

                    assertEquals(expected, actual);
                }
            }

            @Test
            void searchesAppDataToolsFolder() {
                Path expected = tempDir.resolve("tools", "example");

                try (
                        MockedStatic<Files> filesMock = mockStatic(Files.class);
                ) {
                    filesMock.when(() -> Files.isRegularFile(any())).thenReturn(false, true);

                    Path actual = fileService.findSystemFile("example");

                    assertEquals(expected, actual);
                }
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
        }
    }

    @Nested
    @DisplayName("Image Operations")
    class ImageOperationsTests {

        @Nested
        @DisplayName("readImage")
        class ReadImageTests {
            @Test
            void validData_returnsImage() throws IOException {
                BufferedImage image = createTestImage(100, 100);
                byte[] imageData = imageToBytes(image);

                BufferedImage result = FileService.readImage(imageData);

                assertNotNull(result);
                assertEquals(100, result.getWidth());
                assertEquals(100, result.getHeight());
            }

            @Test
            void nullData_throwsException() {
                IOException ex = assertThrows(IOException.class, () -> FileService.readImage((byte[]) null));
                assertEquals("Image data is null or empty", ex.getMessage());
            }

            @Test
            void emptyData_throwsException() {
                IOException ex = assertThrows(IOException.class, () -> FileService.readImage(new byte[0]));
                assertEquals("Image data is null or empty", ex.getMessage());
            }

            @Test
            void invalidData_throwsException() {
                byte[] invalidData = "not an image".getBytes();
                assertThrows(IOException.class, () -> FileService.readImage(invalidData));
            }
        }

        @Nested
        @DisplayName("resizeImage")
        class ResizeImageTests {

            @Test
            void shrinks_imageProperly() {
                BufferedImage original = createTestImage(100, 100);

                BufferedImage resized = FileService.resizeImage(original, 50, 50);

                assertAll(
                        () -> assertEquals(50, resized.getWidth()),
                        () -> assertEquals(50, resized.getHeight())
                );
            }

            @Test
            void enlarges_imageProperly() {
                BufferedImage original = createTestImage(50, 50);

                BufferedImage resized = FileService.resizeImage(original, 200, 200);

                assertAll(
                        () -> assertEquals(200, resized.getWidth()),
                        () -> assertEquals(200, resized.getHeight())
                );
            }

            @Test
            void changesAspectRatio() {
                BufferedImage original = createTestImage(100, 100);

                BufferedImage resized = FileService.resizeImage(original, 200, 50);

                assertAll(
                        () -> assertEquals(200, resized.getWidth()),
                        () -> assertEquals(50, resized.getHeight())
                );
            }

            @Test
            void sameSize_worksCorrectly() {
                BufferedImage original = createTestImage(100, 100);

                BufferedImage resized = FileService.resizeImage(original, 100, 100);

                assertAll(
                        () -> assertEquals(100, resized.getWidth()),
                        () -> assertEquals(100, resized.getHeight()),
                        () -> assertNotSame(original, resized)
                );
            }

            @Test
            void returnsRGBType() {
                BufferedImage original = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);

                BufferedImage resized = FileService.resizeImage(original, 50, 50);

                assertEquals(BufferedImage.TYPE_INT_RGB, resized.getType());
            }

            @Test
            void handlesVerySmallDimensions() {
                BufferedImage original = createTestImage(100, 100);

                BufferedImage resized = FileService.resizeImage(original, 1, 1);

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

                FileService.saveImage(imageData, outputPath.toString());

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

                FileService.saveImage(imageData, outputPath.toString());

                assertTrue(Files.exists(outputPath));
            }

            @Test
            void invalidImageData_throwsException() {
                byte[] invalidData = "not an image".getBytes();
                Path outputPath = tempDir.resolve("invalid.jpg");

                assertThrows(IOException.class, () ->
                        FileService.saveImage(invalidData, outputPath.toString()));
                assertFalse(Files.exists(outputPath));
            }

            @Test
            void emptyImageData_throwsException() {
                byte[] emptyData = new byte[0];
                Path outputPath = tempDir.resolve("empty.jpg");

                IOException ex = assertThrows(IOException.class, () ->
                        FileService.saveImage(emptyData, outputPath.toString()));
                assertEquals("Image data is null or empty", ex.getMessage());
            }

            @Test
            void savedImage_isReadable() throws IOException {
                BufferedImage original = createTestImage(100, 100);
                byte[] imageData = imageToBytes(original);
                Path outputPath = tempDir.resolve("readable.jpg");

                FileService.saveImage(imageData, outputPath.toString());
                BufferedImage loaded = ImageIO.read(outputPath.toFile());

                assertAll(
                        () -> assertNotNull(loaded),
                        () -> assertEquals(100, loaded.getWidth()),
                        () -> assertEquals(100, loaded.getHeight())
                );
            }

            @Test
            void directoryCreationSucceeds_nestedPaths() throws IOException {
                BufferedImage image = createTestImage(100, 100);
                byte[] imageData = imageToBytes(image);
                Path nestedPath = tempDir.resolve("nested/deep/folder/test.jpg");
                assertDoesNotThrow(() -> FileService.saveImage(imageData, nestedPath.toString()),
                        "Should create nested directories automatically");
                assertTrue(Files.exists(nestedPath), "File should be created in nested directory");
            }

            @Test
            void nullImageData_throwsException() {
                Path outputPath = tempDir.resolve("null.jpg");
                IOException ex = assertThrows(IOException.class, () ->
                        FileService.saveImage(null, outputPath.toString()));
                assertEquals("Image data is null or empty", ex.getMessage());
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
                        .smartCroppingEnabled(true).build();
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

            @Test
            @DisplayName("smart cropping enabled for tall image finds content start")
            void smartCroppingEnabled_tallImage_cropsFromContent() throws IOException {
                CoverCroppingSettings smartCropSettings = CoverCroppingSettings.builder()
                        .verticalCroppingEnabled(true)
                        .horizontalCroppingEnabled(true)
                        .aspectRatioThreshold(2.5)
                        .smartCroppingEnabled(true)
                        .build();
                AppSettings appSettings = AppSettings.builder()
                        .coverCroppingSettings(smartCropSettings)
                        .build();
                when(appSettingService.getAppSettings()).thenReturn(appSettings);

                int width = 500;
                int height = 3000;  // ratio = 6:1
                BufferedImage tallImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = tallImage.createGraphics();
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, width, 200);
                g.setColor(Color.BLUE);
                g.fillRect(0, 200, width, height - 200);
                g.dispose();

                boolean result = fileService.saveCoverImages(tallImage, 104L);
                assertTrue(result);

                BufferedImage savedCover = ImageIO.read(new File(fileService.getCoverFile(104L)));
                assertNotNull(savedCover);
                double savedRatio = (double) savedCover.getHeight() / savedCover.getWidth();
                assertTrue(savedRatio < 3.0, "Cropped image should have reasonable aspect ratio");
            }

            @Test
            @DisplayName("smart cropping enabled for wide image finds content start")
            void smartCroppingEnabled_wideImage_cropsFromContent() throws IOException {
                CoverCroppingSettings smartCropSettings = CoverCroppingSettings.builder()
                        .verticalCroppingEnabled(true)
                        .horizontalCroppingEnabled(true)
                        .aspectRatioThreshold(2.5)
                        .smartCroppingEnabled(true)
                        .build();
                AppSettings appSettings = AppSettings.builder()
                        .coverCroppingSettings(smartCropSettings)
                        .build();
                when(appSettingService.getAppSettings()).thenReturn(appSettings);

                int width = 3000;
                int height = 400;  // ratio = 7.5:1
                BufferedImage wideImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = wideImage.createGraphics();
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, 200, height);
                g.setColor(Color.BLUE);
                g.fillRect(200, 0, width - 200, height);
                g.dispose();

                boolean result = fileService.saveCoverImages(wideImage, 105L);
                assertTrue(result);

                BufferedImage savedCover = ImageIO.read(new File(fileService.getCoverFile(105L)));
                assertNotNull(savedCover);
                double savedRatio = (double) savedCover.getWidth() / savedCover.getHeight();
                assertTrue(savedRatio < 3.0, "Cropped image should have reasonable aspect ratio");
            }

            @Test
            @DisplayName("smart cropping with uniform color image uses top/left")
            void smartCroppingEnabled_uniformColorImage_usesTopLeft() throws IOException {
                CoverCroppingSettings smartCropSettings = CoverCroppingSettings.builder()
                        .verticalCroppingEnabled(true)
                        .horizontalCroppingEnabled(true)
                        .aspectRatioThreshold(2.5)
                        .smartCroppingEnabled(true)
                        .build();
                AppSettings appSettings = AppSettings.builder()
                        .coverCroppingSettings(smartCropSettings)
                        .build();
                when(appSettingService.getAppSettings()).thenReturn(appSettings);

                int width = 500;
                int height = 3000;
                BufferedImage uniformImage = createTestImage(width, height, Color.BLUE);

                boolean result = fileService.saveCoverImages(uniformImage, 106L);
                assertTrue(result);

                BufferedImage savedCover = ImageIO.read(new File(fileService.getCoverFile(106L)));
                assertNotNull(savedCover);
                double savedRatio = (double) savedCover.getHeight() / savedCover.getWidth();
                assertTrue(savedRatio < 3.0, "Cropped image should have reasonable aspect ratio");
            }

            @Test
            @DisplayName("null cover cropping settings returns image unchanged")
            void nullCoverCroppingSettings_returnsImageUnchanged() throws IOException {
                AppSettings appSettings = AppSettings.builder()
                        .coverCroppingSettings(null)
                        .build();
                when(appSettingService.getAppSettings()).thenReturn(appSettings);

                int width = 500;
                int height = 3000;  // Very tall image
                BufferedImage tallImage = createTestImage(width, height);

                boolean result = fileService.saveCoverImages(tallImage, 107L);
                assertTrue(result);

                BufferedImage savedCover = ImageIO.read(new File(fileService.getCoverFile(107L)));
                assertNotNull(savedCover);
                assertTrue(savedCover.getWidth() <= 1000);
                assertTrue(savedCover.getHeight() <= 1500);
            }
        }

        @Nested
        @DisplayName("createThumbnailFromBytes")
        class CreateThumbnailFromBytesTests {

            @Test
            void validImageBytes_succeeds() throws IOException {
                BufferedImage image = createTestImage(300, 400);
                byte[] imageBytes = imageToBytes(image);

                assertDoesNotThrow(() ->
                        fileService.createThumbnailFromBytes(15L, imageBytes));
                assertTrue(Files.exists(Path.of(fileService.getCoverFile(15L))));
            }

            @Test
            void invalidImageBytes_throwsException() {
                byte[] invalidData = "not an image".getBytes();

                assertThrows(RuntimeException.class, () ->
                        fileService.createThumbnailFromBytes(16L, invalidData));
            }

            @Test
            void emptyImageBytes_throwsRuntimeException() {
                byte[] emptyData = new byte[0];

                RuntimeException exception = assertThrows(RuntimeException.class, () ->
                        fileService.createThumbnailFromBytes(17L, emptyData));
                assertEquals("Error reading files from path: Image data is null or empty", exception.getMessage());
            }

            @Test
            void nullImageBytes_throwsRuntimeException() {
                RuntimeException exception = assertThrows(RuntimeException.class, () ->
                        fileService.createThumbnailFromBytes(18L, null));
                assertEquals("Error reading files from path: Image data is null or empty", exception.getMessage());
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
            void emptyFile_throwsRuntimeException() {
                MockMultipartFile emptyFile = new MockMultipartFile(
                        "file", "empty.jpg", "image/jpeg", new byte[0]);

                // validateCoverFile throws IllegalArgumentException, but it's caught and wrapped in RuntimeException via ApiError
                RuntimeException exception = assertThrows(RuntimeException.class, () ->
                        fileService.createThumbnailFromFile(7L, emptyFile));
                assertTrue(exception.getMessage().contains("empty") ||
                                exception.getCause() instanceof IllegalArgumentException,
                        "Exception message should indicate file is empty or wrap IllegalArgumentException");
            }

            @Test
            void invalidMimeType_throwsRuntimeException() {
                MockMultipartFile gifFile = new MockMultipartFile(
                        "file", "test.gif", "image/gif", "fake data".getBytes());

                RuntimeException exception = assertThrows(RuntimeException.class, () ->
                        fileService.createThumbnailFromFile(8L, gifFile));
                assertTrue(exception.getMessage().contains("Only JPEG and PNG files are allowed") ||
                                exception.getCause() instanceof IllegalArgumentException,
                        "Exception message should indicate only JPEG and PNG are allowed or wrap IllegalArgumentException");
            }

            @Test
            void fileTooLarge_throwsRuntimeException() {
                byte[] largeData = new byte[6 * 1024 * 1024]; // 6MB
                MockMultipartFile largeFile = new MockMultipartFile(
                        "file", "large.jpg", "image/jpeg", largeData);

                // validateCoverFile throws IllegalArgumentException, but it's caught and wrapped in RuntimeException via ApiError
                RuntimeException exception = assertThrows(RuntimeException.class, () ->
                        fileService.createThumbnailFromFile(9L, largeFile));
                assertTrue(exception.getMessage().contains("5 MB") ||
                                exception.getCause() instanceof IllegalArgumentException,
                        "Exception message should indicate file size limit or wrap IllegalArgumentException");
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

                assertThrows(RuntimeException.class, () ->
                        fileService.createThumbnailFromFile(12L, corruptFile));
            }

            @Test
            void unsupportedMimeType_gif_throwsRuntimeException() {
                byte[] gifData = "GIF89a...".getBytes(); // Fake GIF header
                MockMultipartFile gifFile = new MockMultipartFile(
                        "file", "test.gif", "image/gif", gifData);

                // validateCoverFile throws IllegalArgumentException, but it's caught and wrapped in RuntimeException via ApiError
                assertThrows(RuntimeException.class, () ->
                                fileService.createThumbnailFromFile(13L, gifFile),
                        "Should throw RuntimeException (wrapping IllegalArgumentException) for unsupported MIME type");
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
                    .smartCroppingEnabled(true).build();
            AppSettings appSettings = AppSettings.builder()
                    .coverCroppingSettings(coverCroppingSettings)
                    .build();
            lenient().when(appSettingServiceForNetwork.getAppSettings()).thenReturn(appSettings);

            fileService = new FileService(appProperties, restTemplate, appSettingServiceForNetwork, restTemplate);
        }

        @Nested
        @DisplayName("downloadImageFromUrl")
        class DownloadImageFromUrlTests {

            @Test
            @DisplayName("downloads and returns valid image")
            @Timeout(5)
            void downloadImageFromUrl_validImage_returnsBufferedImage() throws IOException {
                String imageUrl = "http://1.1.1.1/image.jpg";
                BufferedImage testImage = createTestImage(100, 100);
                byte[] imageBytes = imageToBytes(testImage);

                RestTemplate mockRestTemplate = mock(RestTemplate.class);
                AppSettingService mockAppSettingService = mock(AppSettingService.class);
                FileService testFileService = new FileService(appProperties, mockRestTemplate, mockAppSettingService, mockRestTemplate);

                ResponseEntity<byte[]> responseEntity = ResponseEntity.ok(imageBytes);
                when(mockRestTemplate.exchange(
                        anyString(),
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
                String imageUrl = "http://1.1.1.1/image.jpg";
                ResponseEntity<byte[]> responseEntity = ResponseEntity.ok(null);
                when(restTemplate.exchange(
                        anyString(),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(byte[].class)
                )).thenReturn(responseEntity);

                assertThrows(IOException.class, () ->
                        fileService.downloadImageFromUrl(imageUrl));
            }

            @Test
            @DisplayName("throws IOException when ImageIO cannot read bytes")
            @Timeout(5)
            void downloadImageFromUrl_invalidImageData_throwsException() throws IOException {
                String imageUrl = "http://1.1.1.1/image.jpg";
                byte[] invalidBytes = "not an image".getBytes();
                ResponseEntity<byte[]> responseEntity = ResponseEntity.ok(invalidBytes);
                RestTemplate noRedirectMock = (RestTemplate) ReflectionTestUtils.getField(fileService, "noRedirectRestTemplate");

                when(noRedirectMock.exchange(
                        anyString(),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(byte[].class)
                )).thenReturn(responseEntity);

                assertThrows(IOException.class, () -> fileService.downloadImageFromUrl(imageUrl));
            }

            @Test
            @DisplayName("throws exception on HTTP error status")
            @Timeout(5)
            void downloadImageFromUrl_httpError_throwsException() {
                String imageUrl = "http://1.1.1.1/image.jpg";
                ResponseEntity<byte[]> responseEntity = ResponseEntity.notFound().build();
                when(restTemplate.exchange(
                        anyString(),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(byte[].class)
                )).thenReturn(responseEntity);

                assertThrows(IOException.class, () ->
                        fileService.downloadImageFromUrl(imageUrl));
            }

            @Test
            @DisplayName("rewrites redirect URL to preserve hostname when CDN redirects to raw IP")
            @Timeout(5)
            void downloadImageFromUrl_redirectToRawIp_rewritesUrlWithOriginalHost() throws IOException {
                String originalUrl = "http://example.com/cover.jpg";
                String cdnIpRedirect = "http://3.168.64.124/cover.jpg";
                BufferedImage testImage = createTestImage(100, 100);
                byte[] imageBytes = imageToBytes(testImage);

                RestTemplate mockRestTemplate = mock(RestTemplate.class);
                FileService testFileService = new FileService(appProperties, mockRestTemplate, appSettingServiceForNetwork, mockRestTemplate);

                ResponseEntity<byte[]> redirectResponse = ResponseEntity.status(302)
                        .header("Location", cdnIpRedirect).build();
                ResponseEntity<byte[]> imageResponse = ResponseEntity.ok(imageBytes);

                var urlCaptor = ArgumentCaptor.forClass(String.class);
                when(mockRestTemplate.exchange(
                        urlCaptor.capture(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class)
                )).thenReturn(redirectResponse, imageResponse);

                BufferedImage result = testFileService.downloadImageFromUrl(originalUrl);

                assertNotNull(result);
                assertEquals(originalUrl, urlCaptor.getAllValues().get(0));
                assertEquals("http://example.com/cover.jpg", urlCaptor.getAllValues().get(1));
            }

            @Test
            @DisplayName("preserves redirect path when rewriting raw IP URL back to hostname")
            @Timeout(5)
            void downloadImageFromUrl_redirectToRawIpDifferentPath_preservesPath() throws IOException {
                String originalUrl = "http://example.com/images/cover.jpg";
                String cdnIpRedirect = "http://3.168.64.124/cdn/optimized/cover.jpg?token=abc";
                BufferedImage testImage = createTestImage(100, 100);
                byte[] imageBytes = imageToBytes(testImage);

                RestTemplate mockRestTemplate = mock(RestTemplate.class);
                FileService testFileService = new FileService(appProperties, mockRestTemplate, appSettingServiceForNetwork, mockRestTemplate);

                ResponseEntity<byte[]> redirectResponse = ResponseEntity.status(302)
                        .header("Location", cdnIpRedirect).build();
                ResponseEntity<byte[]> imageResponse = ResponseEntity.ok(imageBytes);

                var urlCaptor = ArgumentCaptor.forClass(String.class);
                when(mockRestTemplate.exchange(
                        urlCaptor.capture(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class)
                )).thenReturn(redirectResponse, imageResponse);

                testFileService.downloadImageFromUrl(originalUrl);

                assertEquals("http://example.com/cdn/optimized/cover.jpg?token=abc", urlCaptor.getAllValues().get(1));
            }

            @Test
            @DisplayName("does not rewrite URL when redirect target is a hostname")
            @Timeout(5)
            void downloadImageFromUrl_redirectToHostname_keepsRedirectUrl() throws IOException {
                String originalUrl = "http://example.com/cover.jpg";
                String hostnameRedirect = "http://www.example.com/cover.jpg";
                BufferedImage testImage = createTestImage(100, 100);
                byte[] imageBytes = imageToBytes(testImage);

                RestTemplate mockRestTemplate = mock(RestTemplate.class);
                FileService testFileService = new FileService(appProperties, mockRestTemplate, appSettingServiceForNetwork, mockRestTemplate);

                ResponseEntity<byte[]> redirectResponse = ResponseEntity.status(301)
                        .header("Location", hostnameRedirect).build();
                ResponseEntity<byte[]> imageResponse = ResponseEntity.ok(imageBytes);

                var urlCaptor = ArgumentCaptor.forClass(String.class);
                when(mockRestTemplate.exchange(
                        urlCaptor.capture(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class)
                )).thenReturn(redirectResponse, imageResponse);

                testFileService.downloadImageFromUrl(originalUrl);

                assertEquals(hostnameRedirect, urlCaptor.getAllValues().get(1));
            }

            @Test
            @DisplayName("chain: hostname -> hostname -> raw IP uses last hostname for rewrite")
            @Timeout(5)
            void downloadImageFromUrl_multipleRedirectsToRawIp_usesLastHostname() throws IOException {
                String originalUrl = "http://example.com/cover.jpg";
                String hostnameRedirect = "http://www.example.com/cover.jpg";
                String ipRedirect = "http://52.84.12.99/cover.jpg";
                BufferedImage testImage = createTestImage(100, 100);
                byte[] imageBytes = imageToBytes(testImage);

                RestTemplate mockRestTemplate = mock(RestTemplate.class);
                FileService testFileService = new FileService(appProperties, mockRestTemplate, appSettingServiceForNetwork, mockRestTemplate);

                ResponseEntity<byte[]> redirect1 = ResponseEntity.status(301)
                        .header("Location", hostnameRedirect).build();
                ResponseEntity<byte[]> redirect2 = ResponseEntity.status(302)
                        .header("Location", ipRedirect).build();
                ResponseEntity<byte[]> imageResponse = ResponseEntity.ok(imageBytes);

                var urlCaptor = ArgumentCaptor.forClass(String.class);
                when(mockRestTemplate.exchange(
                        urlCaptor.capture(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class)
                )).thenReturn(redirect1, redirect2, imageResponse);

                testFileService.downloadImageFromUrl(originalUrl);

                assertEquals(originalUrl, urlCaptor.getAllValues().get(0));
                assertEquals(hostnameRedirect, urlCaptor.getAllValues().get(1));
                assertEquals("http://www.example.com/cover.jpg", urlCaptor.getAllValues().get(2));
            }

            @Test
            @DisplayName("throws exception when redirect exceeds max limit")
            @Timeout(5)
            void downloadImageFromUrl_tooManyRedirects_throwsException() {
                String imageUrl = "http://1.1.1.1/cover.jpg";

                RestTemplate mockRestTemplate = mock(RestTemplate.class);
                FileService testFileService = new FileService(appProperties, mockRestTemplate, appSettingServiceForNetwork, mockRestTemplate);

                ResponseEntity<byte[]> redirectResponse = ResponseEntity.status(302)
                        .header("Location", "http://2.2.2.2/cover.jpg")
                        .build();

                when(mockRestTemplate.exchange(
                        anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class)
                )).thenReturn(redirectResponse);

                IOException ex = assertThrows(IOException.class, () ->
                        testFileService.downloadImageFromUrl(imageUrl));
                assertTrue(ex.getMessage().contains("Too many redirects"));
            }

            @Test
            @DisplayName("throws exception when redirect has no Location header")
            @Timeout(5)
            void downloadImageFromUrl_redirectWithoutLocation_throwsException() {
                String imageUrl = "http://1.1.1.1/image.jpg";

                ResponseEntity<byte[]> redirectResponse = ResponseEntity.status(302).build();
                when(restTemplate.exchange(
                        anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class)
                )).thenReturn(redirectResponse);

                IOException ex = assertThrows(IOException.class, () ->
                        fileService.downloadImageFromUrl(imageUrl));
                assertTrue(ex.getMessage().contains("Location"));
            }
        }

        @Nested
        @DisplayName("createThumbnailFromUrl")
        class CreateThumbnailFromUrlTests {

            @Test
            @DisplayName("downloads and saves cover images successfully")
            @Timeout(5)
            void createThumbnailFromUrl_validImage_createsCoverAndThumbnail() throws IOException {
                String imageUrl = "http://1.1.1.1/cover.jpg";
                long bookId = 42L;
                BufferedImage testImage = createTestImage(800, 1200); // Portrait image
                byte[] imageBytes = imageToBytes(testImage);

                ResponseEntity<byte[]> responseEntity = ResponseEntity.ok(imageBytes);
                when(restTemplate.exchange(
                        anyString(),
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
                        anyString(),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(byte[].class)
                )).thenThrow(new RuntimeException("Network error"));

                // FileService wraps exceptions in RuntimeException via ApiError
                RuntimeException exception = assertThrows(RuntimeException.class, () ->
                        fileService.createThumbnailFromUrl(bookId, imageUrl));
                assertTrue(exception.getMessage().contains("Network error") ||
                                exception.getMessage().contains("Failed"),
                        "Exception message should indicate download failure");
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