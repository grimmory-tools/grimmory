package org.booklore.service.fileprocessor;

import org.booklore.mapper.BookMapper;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.ComicMetadata;
import org.booklore.model.dto.settings.LibraryFile;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.ComicMetadataEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookAdditionalFileRepository;
import org.booklore.repository.BookRepository;
import org.booklore.service.ArchiveService;
import org.booklore.service.book.BookCreatorService;
import org.booklore.service.metadata.MetadataMatchService;
import org.booklore.service.metadata.extractor.CbxMetadataExtractor;
import org.booklore.service.metadata.sidecar.SidecarMetadataWriter;
import org.booklore.util.ArchiveUtils;
import org.booklore.util.BookCoverUtils;
import org.booklore.util.FileService;
import org.booklore.util.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.booklore.util.FileService.truncate;


@Slf4j
@Service
public class CbxProcessor extends AbstractFileProcessor implements BookFileProcessor {

    private static final Pattern UNDERSCORE_HYPHEN_PATTERN = Pattern.compile("[_\\-]");
    private static final Pattern IMAGE_EXTENSION_PATTERN = Pattern.compile(".*\\.(jpg|jpeg|png|webp)");
    private final CbxMetadataExtractor cbxMetadataExtractor;
    private final ArchiveService archiveService;

    public CbxProcessor(BookRepository bookRepository,
                        BookAdditionalFileRepository bookAdditionalFileRepository,
                        BookCreatorService bookCreatorService,
                        BookMapper bookMapper,
                        FileService fileService,
                        MetadataMatchService metadataMatchService,
                        SidecarMetadataWriter sidecarMetadataWriter,
                        CbxMetadataExtractor cbxMetadataExtractor,
                        ArchiveService archiveService) {
        super(bookRepository, bookAdditionalFileRepository, bookCreatorService, bookMapper, fileService, metadataMatchService, sidecarMetadataWriter);
        this.cbxMetadataExtractor = cbxMetadataExtractor;
        this.archiveService = archiveService;
    }

    @Override
    public BookEntity processNewFile(LibraryFile libraryFile) {
        BookEntity bookEntity = bookCreatorService.createShellBook(libraryFile, BookFileType.CBX);
        bookEntity.getPrimaryBookFile().setArchiveType(ArchiveUtils.detectArchiveType(FileUtils.getBookFullPath(bookEntity).toFile()));
        boolean coverGenerated = generateCover(bookEntity);
        if (!coverGenerated) {
            var folder = getBookFolderForCoverFallback(libraryFile);
            if (folder != null) {
                coverGenerated = generateCoverFromFolderImage(bookEntity, folder);
            }
        }
        if (coverGenerated) {
            FileService.setBookCoverPath(bookEntity.getMetadata());
            bookEntity.setBookCoverHash(BookCoverUtils.generateCoverHash());
        }
        extractAndSetMetadata(bookEntity);
        return bookEntity;
    }

    @Override
    public boolean generateCover(BookEntity bookEntity) {
        return generateCover(bookEntity, bookEntity.getPrimaryBookFile());
    }

    @Override
    public boolean generateCover(BookEntity bookEntity, BookFileEntity bookFile) {
        Path bookPath = FileUtils.getBookFullPath(bookEntity, bookFile);

        try {
            Optional<BufferedImage> imageOptional = extractImagesFromArchive(bookPath);
            if (imageOptional.isPresent()) {
                BufferedImage image = imageOptional.get();
                try {
                    boolean saved = fileService.saveCoverImages(image, bookEntity.getId());
                    if (saved) {
                        return true;
                    } else {
                        log.warn("Could not save image extracted from CBZ as cover for '{}'", bookFile.getFileName());
                    }
                } finally {
                    image.flush(); // Release resources after processing
                }
            } else {
                log.warn("Could not find cover image in '{}' archive", bookFile.getFileName());
            }
        } catch (Exception e) {
            log.error("Error generating cover for '{}': {}", bookFile.getFileName(), e.getMessage());
        }
        return false;
    }

    @Override
    public List<BookFileType> getSupportedTypes() {
        return List.of(BookFileType.CBX);
    }

    private Optional<BufferedImage> extractImagesFromArchive(Path path) {
        List<String> archiveEntries;

        try {
            archiveEntries = archiveService.streamEntryNames(path)
                    .filter(name -> IMAGE_EXTENSION_PATTERN.matcher(name.toLowerCase()).matches())
                    .sorted()
                    .toList();
        } catch (Exception e) {
            log.warn("Error reading archive {}: {}", path.getFileName(), e.getMessage());
            return Optional.empty();
        }

        for (String entryName : archiveEntries) {
            try (
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ) {
                archiveService.transferEntryTo(path, entryName, outputStream);
                return Optional.ofNullable(FileService.readImage(new ByteArrayInputStream(outputStream.toByteArray())));
            } catch (Exception e) {
                log.warn("Error reading archive {} entry {}: {}", path.getFileName(), entryName, e.getMessage());
            }
        }

        return Optional.empty();
    }

    private void extractAndSetMetadata(BookEntity bookEntity) {
        try {
            BookMetadata extracted = cbxMetadataExtractor.extractMetadata(FileUtils.getBookFullPath(bookEntity).toFile());
            if (extracted == null) {
                // Fallback to filename-derived title
                setMetadata(bookEntity);
                return;
            }

            BookMetadataEntity metadata = bookEntity.getMetadata();

            // Basic fields
            metadata.setTitle(truncate(extracted.getTitle(), 1000));
            metadata.setSubtitle(truncate(extracted.getSubtitle(), 1000));
            metadata.setDescription(truncate(extracted.getDescription(), 5000));
            metadata.setPublisher(truncate(extracted.getPublisher(), 1000));
            metadata.setPublishedDate(extracted.getPublishedDate());
            metadata.setSeriesName(truncate(extracted.getSeriesName(), 1000));
            metadata.setSeriesNumber(extracted.getSeriesNumber());
            metadata.setSeriesTotal(extracted.getSeriesTotal());
            metadata.setPageCount(extracted.getPageCount());
            metadata.setLanguage(truncate(extracted.getLanguage(), 1000));

            // ISBN fields
            metadata.setIsbn13(truncate(extracted.getIsbn13(), 64));
            metadata.setIsbn10(truncate(extracted.getIsbn10(), 64));

            // External IDs
            metadata.setAsin(truncate(extracted.getAsin(), 20));
            metadata.setGoodreadsId(truncate(extracted.getGoodreadsId(), 100));
            metadata.setHardcoverId(truncate(extracted.getHardcoverId(), 100));
            metadata.setHardcoverBookId(truncate(extracted.getHardcoverBookId(), 100));
            metadata.setGoogleId(truncate(extracted.getGoogleId(), 100));
            metadata.setComicvineId(truncate(extracted.getComicvineId(), 100));
            metadata.setLubimyczytacId(truncate(extracted.getLubimyczytacId(), 100));
            metadata.setRanobedbId(truncate(extracted.getRanobedbId(), 100));

            // Ratings
            metadata.setAmazonRating(extracted.getAmazonRating());
            metadata.setAmazonReviewCount(extracted.getAmazonReviewCount());
            metadata.setGoodreadsRating(extracted.getGoodreadsRating());
            metadata.setGoodreadsReviewCount(extracted.getGoodreadsReviewCount());
            metadata.setHardcoverRating(extracted.getHardcoverRating());
            metadata.setHardcoverReviewCount(extracted.getHardcoverReviewCount());
            metadata.setLubimyczytacRating(extracted.getLubimyczytacRating());
            metadata.setRanobedbRating(extracted.getRanobedbRating());

            // Authors
            if (extracted.getAuthors() != null) {
                bookCreatorService.addAuthorsToBook(extracted.getAuthors(), bookEntity);
            }

            // Categories
            if (extracted.getCategories() != null) {
                Set<String> validCategories = extracted.getCategories().stream()
                        .filter(s -> s != null && !s.isBlank() && s.length() <= 100 && !s.contains("\n") && !s.contains("\r") && !s.contains("  "))
                        .collect(Collectors.toSet());
                bookCreatorService.addCategoriesToBook(validCategories, bookEntity);
            }

            // Moods
            if (extracted.getMoods() != null && !extracted.getMoods().isEmpty()) {
                Set<String> validMoods = extracted.getMoods().stream()
                        .filter(s -> s != null && !s.isBlank() && s.length() <= 255)
                        .collect(Collectors.toSet());
                bookCreatorService.addMoodsToBook(validMoods, bookEntity);
            }

            // Tags
            if (extracted.getTags() != null && !extracted.getTags().isEmpty()) {
                Set<String> validTags = extracted.getTags().stream()
                        .filter(s -> s != null && !s.isBlank() && s.length() <= 255)
                        .collect(Collectors.toSet());
                bookCreatorService.addTagsToBook(validTags, bookEntity);
            }
            if (extracted.getComicMetadata() != null) {
                saveComicMetadata(bookEntity, extracted.getComicMetadata());
            }
        } catch (Exception e) {
            log.warn("Failed to extract ComicInfo metadata for '{}': {}", bookEntity.getPrimaryBookFile().getFileName(), e.getMessage());
            // Fallback to filename-derived title
            setMetadata(bookEntity);
        }
    }

    private void setMetadata(BookEntity bookEntity) {
        String baseName = new File(bookEntity.getPrimaryBookFile().getFileName()).getName();
        String extension = FileUtils.getExtension(baseName);
        if (BookFileType.CBX.supports(extension)) {
            baseName = baseName.substring(0, baseName.length() - extension.length() - 1);
        }
        String title = UNDERSCORE_HYPHEN_PATTERN.matcher(baseName).replaceAll(" ").trim();
        bookEntity.getMetadata().setTitle(truncate(title, 1000));
    }

    private void saveComicMetadata(BookEntity bookEntity, ComicMetadata comicDto) {
        Long bookId = bookEntity.getId();
        if (bookId == null) {
            log.warn("Cannot save comic metadata - book ID is null for '{}'",
                    bookEntity.getPrimaryBookFile().getFileName());
            return;
        }

        ComicMetadataEntity comic = new ComicMetadataEntity();
        comic.setBookId(bookId);
        comic.setBookMetadata(bookEntity.getMetadata());
        comic.setIssueNumber(comicDto.getIssueNumber());
        comic.setVolumeName(comicDto.getVolumeName());
        comic.setVolumeNumber(comicDto.getVolumeNumber());
        comic.setStoryArc(comicDto.getStoryArc());
        comic.setStoryArcNumber(comicDto.getStoryArcNumber());
        comic.setAlternateSeries(comicDto.getAlternateSeries());
        comic.setAlternateIssue(comicDto.getAlternateIssue());
        comic.setImprint(comicDto.getImprint());
        comic.setFormat(comicDto.getFormat());
        comic.setBlackAndWhite(comicDto.getBlackAndWhite() != null ? comicDto.getBlackAndWhite() : Boolean.FALSE);
        comic.setManga(comicDto.getManga() != null ? comicDto.getManga() : Boolean.FALSE);
        comic.setReadingDirection(comicDto.getReadingDirection() != null ? comicDto.getReadingDirection() : "ltr");
        comic.setWebLink(comicDto.getWebLink());
        comic.setNotes(comicDto.getNotes());

        // Set on parent - relationships will be populated in saveConnections()
        bookEntity.getMetadata().setComicMetadata(comic);

        // Store the DTO for later processing in saveConnections
        bookCreatorService.setComicMetadataDto(bookEntity, comicDto);
    }
}

