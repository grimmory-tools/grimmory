package org.booklore.service.fileprocessor;

import org.booklore.mapper.BookMapper;
import org.booklore.model.dto.settings.LibraryFile;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.LibraryOrganizationMode;
import org.booklore.repository.BookAdditionalFileRepository;
import org.booklore.repository.BookRepository;
import org.booklore.service.book.BookCreatorService;
import org.booklore.service.metadata.MetadataMatchService;
import org.booklore.service.metadata.sidecar.SidecarMetadataWriter;
import org.booklore.util.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AbstractFileProcessorTest {

    @Mock private BookRepository bookRepository;
    @Mock private BookAdditionalFileRepository bookAdditionalFileRepository;
    @Mock private BookCreatorService bookCreatorService;
    @Mock private BookMapper bookMapper;
    @Mock private FileService fileService;
    @Mock private MetadataMatchService metadataMatchService;
    @Mock private SidecarMetadataWriter sidecarMetadataWriter;

    @TempDir
    Path tempDir;

    private TestableFileProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new TestableFileProcessor(
                bookRepository, bookAdditionalFileRepository, bookCreatorService,
                bookMapper, fileService, metadataMatchService, sidecarMetadataWriter
        );
    }

    // ========== getBookFolderForCoverFallback ==========

    @Test
    void getBookFolderForCoverFallback_folderBased_returnsFullPath() {
        var libraryFile = buildLibraryFile(tempDir, "audiobooks", "MyAudiobook", true, LibraryOrganizationMode.AUTO_DETECT);
        var result = processor.exposedGetBookFolderForCoverFallback(libraryFile);
        assertThat(result).isEqualTo(libraryFile.getFullPath());
    }

    @Test
    void getBookFolderForCoverFallback_bookPerFolder_returnsParent() {
        var libraryFile = buildLibraryFile(tempDir, "books/author", "book.epub", false, LibraryOrganizationMode.BOOK_PER_FOLDER);
        var result = processor.exposedGetBookFolderForCoverFallback(libraryFile);
        assertThat(result).isEqualTo(libraryFile.getFullPath().getParent());
    }

    @Test
    void getBookFolderForCoverFallback_autoDetectNotFolderBased_returnsNull() {
        var libraryFile = buildLibraryFile(tempDir, "books", "book.epub", false, LibraryOrganizationMode.AUTO_DETECT);
        var result = processor.exposedGetBookFolderForCoverFallback(libraryFile);
        assertThat(result).isNull();
    }

    @Test
    void getBookFolderForCoverFallback_folderBasedInBookPerFolder_returnsFullPath() {
        var libraryFile = buildLibraryFile(tempDir, "audiobooks", "MyAudiobook", true, LibraryOrganizationMode.BOOK_PER_FOLDER);
        var result = processor.exposedGetBookFolderForCoverFallback(libraryFile);
        assertThat(result).isEqualTo(libraryFile.getFullPath());
    }

    // ========== generateCoverFromFolderImage ==========

    @Test
    void generateCoverFromFolderImage_coverExists_savesCover() throws IOException {
        Path folder = tempDir.resolve("bookfolder");
        Files.createDirectories(folder);
        createTestImage(folder.resolve("cover.jpg"));

        var bookEntity = new BookEntity();
        bookEntity.setId(1L);

        when(fileService.saveCoverImages(any(BufferedImage.class), eq(1L))).thenReturn(true);

        boolean result = processor.exposedGenerateCoverFromFolderImage(bookEntity, folder);

        assertThat(result).isTrue();
        verify(fileService).saveCoverImages(any(BufferedImage.class), eq(1L));
    }

    @Test
    void generateCoverFromFolderImage_noCoverImage_returnsFalse() {
        Path folder = tempDir.resolve("emptyfolder");

        var bookEntity = new BookEntity();
        bookEntity.setId(1L);

        boolean result = processor.exposedGenerateCoverFromFolderImage(bookEntity, folder);

        assertThat(result).isFalse();
    }

    @Test
    void generateCoverFromFolderImage_noCoverImage_doesNotCallSave() throws IOException {
        Path folder = tempDir.resolve("noimages");
        Files.createDirectories(folder);
        Files.createFile(folder.resolve("book.epub"));

        var bookEntity = new BookEntity();
        bookEntity.setId(1L);

        processor.exposedGenerateCoverFromFolderImage(bookEntity, folder);

        verify(fileService, never()).saveCoverImages(any(), any(long.class));
    }

    // ========== generateAudiobookCoverFromFolderImage ==========

    @Test
    void generateAudiobookCoverFromFolderImage_coverExists_savesAudiobookCover() throws IOException {
        Path folder = tempDir.resolve("audiobookfolder");
        Files.createDirectories(folder);
        createTestImage(folder.resolve("cover.jpg"));

        var bookEntity = new BookEntity();
        bookEntity.setId(2L);

        when(fileService.saveAudiobookCoverImages(any(BufferedImage.class), eq(2L))).thenReturn(true);

        boolean result = processor.exposedGenerateAudiobookCoverFromFolderImage(bookEntity, folder);

        assertThat(result).isTrue();
        verify(fileService).saveAudiobookCoverImages(any(BufferedImage.class), eq(2L));
    }

    @Test
    void generateAudiobookCoverFromFolderImage_noCoverImage_returnsFalse() {
        Path folder = tempDir.resolve("empty");

        var bookEntity = new BookEntity();
        bookEntity.setId(2L);

        boolean result = processor.exposedGenerateAudiobookCoverFromFolderImage(bookEntity, folder);

        assertThat(result).isFalse();
    }

    // ========== helpers ==========

    private void createTestImage(Path path) throws IOException {
        var image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(image, "jpg", path.toFile());
    }

    private LibraryFile buildLibraryFile(Path libraryRoot, String subPath, String fileName,
                                         boolean folderBased, LibraryOrganizationMode mode) {
        var libraryPath = new LibraryPathEntity();
        libraryPath.setPath(libraryRoot.toString());

        var library = new LibraryEntity();
        library.setOrganizationMode(mode);

        return LibraryFile.builder()
                .libraryEntity(library)
                .libraryPathEntity(libraryPath)
                .fileSubPath(subPath)
                .fileName(fileName)
                .folderBased(folderBased)
                .build();
    }

    /**
     * Minimal concrete subclass to expose protected methods for testing.
     */
    static class TestableFileProcessor extends AbstractFileProcessor {

        TestableFileProcessor(BookRepository bookRepository,
                              BookAdditionalFileRepository bookAdditionalFileRepository,
                              BookCreatorService bookCreatorService,
                              BookMapper bookMapper,
                              FileService fileService,
                              MetadataMatchService metadataMatchService,
                              SidecarMetadataWriter sidecarMetadataWriter) {
            super(bookRepository, bookAdditionalFileRepository, bookCreatorService,
                    bookMapper, fileService, metadataMatchService, sidecarMetadataWriter);
        }

        @Override
        protected BookEntity processNewFile(LibraryFile libraryFile) {
            return null;
        }

        @Override
        public boolean generateCover(BookEntity bookEntity) {
            return false;
        }

        @Override
        public java.util.List<org.booklore.model.enums.BookFileType> getSupportedTypes() {
            return java.util.List.of();
        }

        Path exposedGetBookFolderForCoverFallback(LibraryFile libraryFile) {
            return getBookFolderForCoverFallback(libraryFile);
        }

        boolean exposedGenerateCoverFromFolderImage(BookEntity bookEntity, Path folder) {
            return generateCoverFromFolderImage(bookEntity, folder);
        }

        boolean exposedGenerateAudiobookCoverFromFolderImage(BookEntity bookEntity, Path folder) {
            return generateAudiobookCoverFromFolderImage(bookEntity, folder);
        }
    }
}
