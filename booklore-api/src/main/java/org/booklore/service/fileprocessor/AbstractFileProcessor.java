package org.booklore.service.fileprocessor;

import org.booklore.mapper.BookMapper;
import org.booklore.model.FileProcessResult;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.settings.LibraryFile;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.enums.FileProcessStatus;
import org.booklore.model.enums.LibraryOrganizationMode;
import org.booklore.repository.BookAdditionalFileRepository;
import org.booklore.repository.BookRepository;
import org.booklore.service.book.BookCreatorService;
import org.booklore.service.file.FileFingerprint;
import org.booklore.service.metadata.MetadataMatchService;
import org.booklore.service.metadata.sidecar.SidecarMetadataWriter;
import org.booklore.util.FileService;
import org.booklore.util.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Slf4j
public abstract class AbstractFileProcessor implements BookFileProcessor {

    protected final BookRepository bookRepository;
    protected final BookAdditionalFileRepository bookAdditionalFileRepository;
    protected final BookCreatorService bookCreatorService;
    protected final BookMapper bookMapper;
    protected final MetadataMatchService metadataMatchService;
    protected final FileService fileService;
    protected final SidecarMetadataWriter sidecarMetadataWriter;


    protected AbstractFileProcessor(BookRepository bookRepository,
                                    BookAdditionalFileRepository bookAdditionalFileRepository,
                                    BookCreatorService bookCreatorService,
                                    BookMapper bookMapper,
                                    FileService fileService,
                                    MetadataMatchService metadataMatchService,
                                    SidecarMetadataWriter sidecarMetadataWriter) {
        this.bookRepository = bookRepository;
        this.bookAdditionalFileRepository = bookAdditionalFileRepository;
        this.bookCreatorService = bookCreatorService;
        this.bookMapper = bookMapper;
        this.metadataMatchService = metadataMatchService;
        this.fileService = fileService;
        this.sidecarMetadataWriter = sidecarMetadataWriter;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    @Override
    public FileProcessResult processFile(LibraryFile libraryFile) {
        Path path = libraryFile.getFullPath();
        String hash = libraryFile.isFolderBased()
                ? FileFingerprint.generateFolderHash(path)
                : FileFingerprint.generateHash(path);
        Book book = createAndMapBook(libraryFile, hash);
        return new FileProcessResult(book, FileProcessStatus.NEW);
    }

    private Book createAndMapBook(LibraryFile libraryFile, String hash) {
        BookEntity entity = processNewFile(libraryFile);
        entity.getPrimaryBookFile().setCurrentHash(hash);
        entity.setMetadataMatchScore(metadataMatchService.calculateMatchScore(entity));
        bookCreatorService.saveConnections(entity);

        if (sidecarMetadataWriter.isWriteOnScanEnabled()) {
            try {
                sidecarMetadataWriter.writeSidecarMetadata(entity);
            } catch (Exception e) {
                log.warn("Failed to write sidecar metadata for book ID {}: {}", entity.getId(), e.getMessage());
            }
        }

        return bookMapper.toBook(entity);
    }

    protected abstract BookEntity processNewFile(LibraryFile libraryFile);

    protected Path getBookFolderForCoverFallback(LibraryFile libraryFile) {
        if (libraryFile.isFolderBased()) {
            return libraryFile.getFullPath();
        }
        if (libraryFile.getLibraryEntity().getOrganizationMode() == LibraryOrganizationMode.BOOK_PER_FOLDER) {
            return libraryFile.getFullPath().getParent();
        }
        return null;
    }

    protected boolean generateCoverFromFolderImage(BookEntity bookEntity, Path bookFolder) {
        Optional<Path> coverImage = FileUtils.findCoverImageInFolder(bookFolder);
        if (coverImage.isEmpty()) return false;
        try {
            BufferedImage image = ImageIO.read(coverImage.get().toFile());
            if (image == null) return false;
            try {
                return fileService.saveCoverImages(image, bookEntity.getId());
            } finally {
                image.flush();
            }
        } catch (Exception e) {
            log.debug("Failed to use folder cover image {}: {}", coverImage.get(), e.getMessage());
            return false;
        }
    }

    protected boolean generateAudiobookCoverFromFolderImage(BookEntity bookEntity, Path bookFolder) {
        Optional<Path> coverImage = FileUtils.findCoverImageInFolder(bookFolder);
        if (coverImage.isEmpty()) return false;
        try {
            BufferedImage image = FileService.readImage(Files.readAllBytes(coverImage.get()));
            if (image == null) return false;
            try {
                return fileService.saveAudiobookCoverImages(image, bookEntity.getId());
            } finally {
                image.flush();
            }
        } catch (Exception e) {
            log.debug("Failed to use folder cover image {}: {}", coverImage.get(), e.getMessage());
            return false;
        }
    }
}