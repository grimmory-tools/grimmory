package com.adityachandel.booklore.service.fileprocessor;

import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.repository.BookMetadataRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.BookCreatorService;
import com.adityachandel.booklore.service.metadata.MetadataMatchService;
import com.adityachandel.booklore.util.FileUtils;
import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
public class CbxProcessor extends AbstractFileProcessor implements BookFileProcessor {

    private final BookMetadataRepository bookMetadataRepository;

    public CbxProcessor(BookRepository bookRepository,
                        BookCreatorService bookCreatorService,
                        BookMapper bookMapper,
                        FileProcessingUtils fileProcessingUtils,
                        BookMetadataRepository bookMetadataRepository,
                        MetadataMatchService metadataMatchService) {
        super(bookRepository, bookCreatorService, bookMapper, fileProcessingUtils, bookMetadataRepository, metadataMatchService);
        this.bookMetadataRepository = bookMetadataRepository;
    }

    @Override
    public BookEntity processNewFile(LibraryFile libraryFile) {
        BookEntity bookEntity = bookCreatorService.createShellBook(libraryFile, BookFileType.CBX);
        if (generateCover(bookEntity)) {
            fileProcessingUtils.setBookCoverPath(bookEntity.getId(), bookEntity.getMetadata());
        }
        setMetadata(bookEntity);
        return bookEntity;
    }

    @Override
    public boolean generateCover(BookEntity bookEntity) {
        File file = new File(FileUtils.getBookFullPath(bookEntity));
        try {
            Optional<BufferedImage> imageOptional = extractImagesFromArchive(file);
            if (imageOptional.isPresent()) {
                boolean saved = fileProcessingUtils.saveCoverImage(imageOptional.get(), bookEntity.getId());
                if (saved) {
                    bookEntity.getMetadata().setCoverUpdatedOn(Instant.now());
                    bookMetadataRepository.save(bookEntity.getMetadata());
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("Error generating cover for '{}': {}", bookEntity.getFileName(), e.getMessage());
        }
        return false;
    }

    @Override
    public List<BookFileType> getSupportedTypes() {
        return List.of(BookFileType.CBX);
    }

    private Optional<BufferedImage> extractImagesFromArchive(File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".cbz")) {
            return extractFirstImageFromZip(file);
        } else if (name.endsWith(".cb7")) {
            return extractFirstImageFrom7z(file);
        } else if (name.endsWith(".cbr")) {
            return extractFirstImageFromRar(file);
        } else {
            log.warn("Unsupported CBX format: {}", name);
            return Optional.empty();
        }
    }

    private Optional<BufferedImage> extractFirstImageFromZip(File file) {
        try (ZipFile zipFile = new ZipFile(file)) {
            return Collections.list(zipFile.getEntries()).stream()
                    .filter(e -> !e.isDirectory() && e.getName().matches("(?i).*\\.(jpg|jpeg|png|webp)"))
                    .min(Comparator.comparing(ZipArchiveEntry::getName))
                    .map(entry -> {
                        try (InputStream is = zipFile.getInputStream(entry)) {
                            return ImageIO.read(is);
                        } catch (Exception e) {
                            log.warn("Failed to read image from ZIP entry {}: {}", entry.getName(), e.getMessage());
                            return null;
                        }
                    });
        } catch (Exception e) {
            log.error("Error extracting ZIP: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<BufferedImage> extractFirstImageFrom7z(File file) {
        try (SevenZFile sevenZFile = new SevenZFile(file)) {
            List<SevenZArchiveEntry> imageEntries = new ArrayList<>();
            SevenZArchiveEntry entry;
            while ((entry = sevenZFile.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().matches("(?i).*\\.(jpg|jpeg|png|webp)")) {
                    imageEntries.add(entry);
                }
            }
            imageEntries.sort(Comparator.comparing(SevenZArchiveEntry::getName));

            try (SevenZFile sevenZFileReset = new SevenZFile(file)) {
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
                            return Optional.ofNullable(ImageIO.read(new ByteArrayInputStream(content)));
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
                    .filter(h -> !h.isDirectory() && h.getFileNameString().toLowerCase().matches(".*\\.(jpg|jpeg|png|webp)"))
                    .sorted(Comparator.comparing(FileHeader::getFileNameString))
                    .toList();

            for (FileHeader header : imageHeaders) {
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    archive.extractFile(header, baos);
                    return Optional.ofNullable(ImageIO.read(new ByteArrayInputStream(baos.toByteArray())));
                } catch (Exception e) {
                    log.warn("Error reading RAR entry {}: {}", header.getFileNameString(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error extracting RAR: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private void setMetadata(BookEntity bookEntity) {
        String baseName = new File(bookEntity.getFileName()).getName();
        String title = baseName
                .replaceAll("(?i)\\.cb[rz7]$", "")
                .replaceAll("[_\\-]", " ")
                .trim();
        bookEntity.getMetadata().setTitle(FileProcessingUtils.truncate(title, 1000));
    }
}