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
import org.booklore.service.book.BookCreatorService;
import org.booklore.service.metadata.MetadataMatchService;
import org.booklore.service.metadata.extractor.CbxMetadataExtractor;
import org.booklore.service.metadata.sidecar.SidecarMetadataWriter;
import org.booklore.util.ArchiveUtils;
import org.booklore.util.BookCoverUtils;
import org.booklore.util.FileService;
import org.booklore.util.FileUtils;
import org.booklore.util.UnrarHelper;
import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Path;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.booklore.util.FileService.truncate;


@Slf4j
@Service
public class CbxProcessor extends AbstractFileProcessor implements BookFileProcessor {

    private static final Pattern UNDERSCORE_HYPHEN_PATTERN = Pattern.compile("[_\\-]");
    private static final Pattern IMAGE_EXTENSION_PATTERN = Pattern.compile(".*\\.(jpg|jpeg|png|webp)");
    private static final Pattern IMAGE_EXTENSION_CASE_INSENSITIVE_PATTERN = Pattern.compile("(?i).*\\.(jpg|jpeg|png|webp)");
    private final CbxMetadataExtractor cbxMetadataExtractor;

    public CbxProcessor(BookRepository bookRepository,
                        BookAdditionalFileRepository bookAdditionalFileRepository,
                        BookCreatorService bookCreatorService,
                        BookMapper bookMapper,
                        FileService fileService,
                        MetadataMatchService metadataMatchService,
                        SidecarMetadataWriter sidecarMetadataWriter,
                        CbxMetadataExtractor cbxMetadataExtractor) {
        super(bookRepository, bookAdditionalFileRepository, bookCreatorService, bookMapper, fileService, metadataMatchService, sidecarMetadataWriter);
        this.cbxMetadataExtractor = cbxMetadataExtractor;
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
        File file = FileUtils.getBookFullPath(bookEntity, bookFile).toFile();
        try {
            Optional<BufferedImage> imageOptional = extractImagesFromArchive(file);
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

    private Optional<BufferedImage> extractImagesFromArchive(File file) {
        ArchiveUtils.ArchiveType type = ArchiveUtils.detectArchiveType(file);

        return switch (type) {
            case ZIP -> extractFirstImageFromZip(file);
            case SEVEN_ZIP -> extractFirstImageFrom7z(file);
            case RAR -> extractFirstImageFromRar(file);
            default -> Optional.empty();
        };
    }

    private Optional<BufferedImage> extractFirstImageFromZip(File file) {
        // Fast path: Try reading from Central Directory
        try (ZipFile zipFile = ZipFile.builder()
                .setFile(file)
                .setUseUnicodeExtraFields(true)
                .setIgnoreLocalFileHeader(true)
                .get()) {
            Optional<BufferedImage> image = findAndReadFirstImage(zipFile);
            if (image.isPresent()) return image;
        } catch (Exception e) {
            log.debug("Fast path failed for ZIP extraction: {}", e.getMessage());
        }

        // Slow path: Fallback to scanning local file headers
        try (ZipFile zipFile = ZipFile.builder()
                .setFile(file)
                .setUseUnicodeExtraFields(true)
                .setIgnoreLocalFileHeader(false)
                .get()) {
            return findAndReadFirstImage(zipFile);
        } catch (Exception e) {
            log.error("Error extracting ZIP: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<BufferedImage> findAndReadFirstImage(ZipFile zipFile) {
        return Collections.list(zipFile.getEntries()).stream()
                .filter(e -> !e.isDirectory() && IMAGE_EXTENSION_CASE_INSENSITIVE_PATTERN.matcher(e.getName()).matches())
                .min(Comparator.comparing(ZipArchiveEntry::getName))
                .map(entry -> {
                    try (InputStream is = zipFile.getInputStream(entry)) {
                        return FileService.readImage(is);
                    } catch (Exception e) {
                        log.warn("Failed to read image from ZIP entry {}: {}", entry.getName(), e.getMessage());
                        return null;
                    }
                });
    }

    private Optional<BufferedImage> extractFirstImageFrom7z(File file) {
        try (SevenZFile sevenZFile = SevenZFile.builder().setFile(file).get()) {
            List<SevenZArchiveEntry> imageEntries = new ArrayList<>();
            SevenZArchiveEntry entry;
            while ((entry = sevenZFile.getNextEntry()) != null) {
                if (!entry.isDirectory() && IMAGE_EXTENSION_CASE_INSENSITIVE_PATTERN.matcher(entry.getName()).matches()) {
                    imageEntries.add(entry);
                }
            }
            imageEntries.sort(Comparator.comparing(SevenZArchiveEntry::getName));

            try (SevenZFile sevenZFileReset = SevenZFile.builder().setFile(file).get()) {
                for (SevenZArchiveEntry imgEntry : imageEntries) {
                    SevenZArchiveEntry current;
                    while ((current = sevenZFileReset.getNextEntry()) != null) {
                        if (current.equals(imgEntry)) {
                            byte[] content = new byte[(int) current.getSize()];
                            int offset = 0;
                            while (offset < content.length) {
                                int bytesRead = sevenZFileReset.read(content, offset, content.length - offset);
                                if (bytesRead < 0) break;
                                offset += bytesRead;
                            }
                            return Optional.ofNullable(FileService.readImage(new ByteArrayInputStream(content)));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error extracting 7z: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private Optional<BufferedImage> extractFirstImageFromRar(File file) {
        try (Archive archive = new Archive(file)) {
            List<FileHeader> imageHeaders = archive.getFileHeaders().stream()
                    .filter(h -> !h.isDirectory() && IMAGE_EXTENSION_PATTERN.matcher(h.getFileName().toLowerCase()).matches())
                    .sorted(Comparator.comparing(FileHeader::getFileName))
                    .toList();

            for (FileHeader header : imageHeaders) {
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    archive.extractFile(header, baos);
                    return Optional.ofNullable(FileService.readImage(new ByteArrayInputStream(baos.toByteArray())));
                } catch (Exception e) {
                    log.warn("Error reading RAR entry {}: {}", header.getFileName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            if (UnrarHelper.isAvailable()) {
                log.info("junrar failed for {}, falling back to unrar CLI: {}", file.getName(), e.getMessage());
                return extractFirstImageFromRarViaCli(file);
            }
            log.error("Error extracting RAR: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private Optional<BufferedImage> extractFirstImageFromRarViaCli(File file) {
        try {
            List<String> entries = UnrarHelper.listEntries(file.toPath());
            List<String> imageEntries = entries.stream()
                    .filter(name -> IMAGE_EXTENSION_CASE_INSENSITIVE_PATTERN.matcher(name).matches())
                    .sorted()
                    .toList();
            for (String entry : imageEntries) {
                try {
                    byte[] bytes = UnrarHelper.extractEntryBytes(file.toPath(), entry);
                    BufferedImage image = FileService.readImage(new ByteArrayInputStream(bytes));
                    if (image != null) return Optional.of(image);
                } catch (Exception ex) {
                    log.warn("Error reading RAR entry via CLI {}: {}", entry, ex.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("unrar CLI fallback also failed for {}: {}", file.getName(), e.getMessage());
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

