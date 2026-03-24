package org.booklore.service.fileprocessor;

import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.ApiError;
import org.booklore.mapper.BookMapper;
import org.booklore.model.dto.AudiobookMetadata;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.settings.LibraryFile;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookAdditionalFileRepository;
import org.booklore.repository.BookRepository;
import org.booklore.service.book.BookCreatorService;
import org.booklore.service.metadata.MetadataMatchService;
import org.booklore.service.metadata.extractor.AudiobookMetadataExtractor;
import org.booklore.service.metadata.sidecar.SidecarMetadataWriter;
import org.booklore.util.BookCoverUtils;
import org.booklore.util.FileService;
import org.booklore.util.FileUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.booklore.util.FileService.truncate;

@Slf4j
@Service
@Transactional
public class AudiobookProcessor extends AbstractFileProcessor implements BookFileProcessor {

    private final AudiobookMetadataExtractor audiobookMetadataExtractor;

    public AudiobookProcessor(BookRepository bookRepository,
                              BookAdditionalFileRepository bookAdditionalFileRepository,
                              BookCreatorService bookCreatorService,
                              BookMapper bookMapper,
                              FileService fileService,
                              MetadataMatchService metadataMatchService,
                              SidecarMetadataWriter sidecarMetadataWriter,
                              AudiobookMetadataExtractor audiobookMetadataExtractor) {
        super(bookRepository, bookAdditionalFileRepository, bookCreatorService, bookMapper, fileService, metadataMatchService, sidecarMetadataWriter);
        this.audiobookMetadataExtractor = audiobookMetadataExtractor;
    }

    @Override
    public BookEntity processNewFile(LibraryFile libraryFile) {
        BookEntity bookEntity = bookCreatorService.createShellBook(libraryFile, BookFileType.AUDIOBOOK);
        setBookMetadata(bookEntity, libraryFile.isFolderBased());
        boolean coverGenerated = generateCover(bookEntity, libraryFile.isFolderBased());
        if (!coverGenerated) {
            var folder = getBookFolderForCoverFallback(libraryFile);
            if (folder != null) {
                coverGenerated = generateAudiobookCoverFromFolderImage(bookEntity, folder);
            }
        }
        if (coverGenerated) {
            bookEntity.getMetadata().setAudiobookCoverUpdatedOn(Instant.now());
            bookEntity.setAudiobookCoverHash(BookCoverUtils.generateCoverHash());
        }
        return bookEntity;
    }

    @Override
    public boolean generateCover(BookEntity bookEntity) {
        return generateCover(bookEntity, bookEntity.getPrimaryBookFile().isFolderBased());
    }

    @Override
    public boolean generateAudiobookCover(BookEntity bookEntity) {
        var audiobookFile = bookEntity.getBookFiles().stream()
                .filter(f -> f.getBookType() == BookFileType.AUDIOBOOK)
                .findFirst()
                .orElse(null);
        if (audiobookFile == null) {
            return false;
        }
        return generateCoverFromFile(bookEntity, audiobookFile);
    }

    public boolean generateCover(BookEntity bookEntity, boolean isFolderBased) {
        try {
            File audioFile = getAudioFileForMetadata(bookEntity, isFolderBased);
            if (audioFile == null || !audioFile.exists()) {
                log.debug("Audio file not found for audiobook '{}'", bookEntity.getPrimaryBookFile().getFileName());
                return false;
            }

            return extractAndSaveCover(bookEntity, audioFile, false);

        } catch (Exception e) {
            log.error("Error generating cover for audiobook '{}': {}", bookEntity.getPrimaryBookFile().getFileName(), e.getMessage(), e);
            return false;
        }
    }

    private boolean generateCoverFromFile(BookEntity bookEntity, BookFileEntity audiobookFile) {
        try {
            File audioFile = getAudioFileForBookFile(bookEntity, audiobookFile);
            if (audioFile == null) {
                log.warn("Could not resolve audio file path for audiobook '{}' (bookId: {})", audiobookFile.getFileName(), bookEntity.getId());
                return false;
            }
            if (!audioFile.exists()) {
                log.warn("Audio file does not exist: {} for audiobook '{}' (bookId: {})", audioFile.getAbsolutePath(), audiobookFile.getFileName(), bookEntity.getId());
                return false;
            }

            log.debug("Extracting cover from audio file: {}", audioFile.getAbsolutePath());
            return extractAndSaveCover(bookEntity, audioFile, true);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error generating cover for audiobook '{}' (bookId: {}): {}",
                    audiobookFile.getFileName(), bookEntity.getId(), e.getMessage(), e);
            return false;
        }
    }

    private boolean extractAndSaveCover(BookEntity bookEntity, File audioFile, boolean throwOnNoCover) throws Exception {
        byte[] coverData = audiobookMetadataExtractor.extractCover(audioFile);

        if (coverData == null) {
            log.warn("No embedded cover image found in audiobook file '{}' (bookId: {})", audioFile.getAbsolutePath(), bookEntity.getId());
            if (throwOnNoCover) {
                throw ApiError.NO_COVER_IN_FILE.createException();
            }
            return false;
        }

        log.debug("Found cover data ({} bytes) in audiobook file '{}'", coverData.length, audioFile.getName());

        boolean saved;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(coverData)) {
            BufferedImage originalImage = FileService.readImage(bais);
            if (originalImage == null) {
                log.warn("Failed to decode cover image for audiobook '{}'", audioFile.getName());
                return false;
            }
            saved = fileService.saveAudiobookCoverImages(originalImage, bookEntity.getId());
            originalImage.flush();
        }

        return saved;
    }

    private File getAudioFileForMetadata(BookEntity bookEntity, boolean isFolderBased) {
        if (isFolderBased) {
            java.nio.file.Path folderPath = bookEntity.getFullFilePath();
            return FileUtils.getFirstAudioFileInFolder(folderPath)
                    .map(java.nio.file.Path::toFile)
                    .orElse(null);
        } else {
            return new File(FileUtils.getBookFullPath(bookEntity));
        }
    }

    private File getAudioFileForBookFile(BookEntity bookEntity, BookFileEntity bookFile) {
        if (bookEntity.getLibraryPath() == null || bookFile == null) {
            log.debug("Cannot get audio file: libraryPath or bookFile is null");
            return null;
        }

        String libraryPath = bookEntity.getLibraryPath().getPath();
        String subPath = bookFile.getFileSubPath();
        String fileName = bookFile.getFileName();

        if (libraryPath == null || fileName == null) {
            log.debug("Cannot get audio file: libraryPath='{}', fileName='{}'", libraryPath, fileName);
            return null;
        }

        java.nio.file.Path filePath;
        if (subPath == null || subPath.isEmpty()) {
            filePath = java.nio.file.Paths.get(libraryPath, fileName).normalize();
        } else {
            filePath = java.nio.file.Paths.get(libraryPath, subPath, fileName).normalize();
        }

        log.debug("Constructed audiobook path: {}, isFolderBased: {}", filePath, bookFile.isFolderBased());

        if (bookFile.isFolderBased()) {
            var audioFile = FileUtils.getFirstAudioFileInFolder(filePath);
            if (audioFile.isEmpty()) {
                log.debug("No audio file found in folder: {}", filePath);
            }
            return audioFile.map(java.nio.file.Path::toFile).orElse(null);
        } else {
            return filePath.toFile();
        }
    }

    @Override
    public List<BookFileType> getSupportedTypes() {
        return List.of(BookFileType.AUDIOBOOK);
    }

    private void setBookMetadata(BookEntity bookEntity, boolean isFolderBased) {
        File bookFile = getAudioFileForMetadata(bookEntity, isFolderBased);
        if (bookFile == null || !bookFile.exists()) {
            log.warn("Audio file not found for metadata extraction: {}", bookEntity.getPrimaryBookFile().getFileName());
            return;
        }

        BookMetadata audioMetadata = audiobookMetadataExtractor.extractMetadata(bookFile);
        if (audioMetadata == null) return;

        BookMetadataEntity metadata = bookEntity.getMetadata();

        metadata.setTitle(truncate(audioMetadata.getTitle(), 1000));
        metadata.setSubtitle(truncate(audioMetadata.getSubtitle(), 1000));
        metadata.setDescription(truncate(audioMetadata.getDescription(), 2000));
        metadata.setPublisher(truncate(audioMetadata.getPublisher(), 1000));
        metadata.setPublishedDate(audioMetadata.getPublishedDate());
        metadata.setSeriesName(truncate(audioMetadata.getSeriesName(), 1000));
        metadata.setSeriesNumber(audioMetadata.getSeriesNumber());
        metadata.setSeriesTotal(audioMetadata.getSeriesTotal());

        String lang = audioMetadata.getLanguage();
        metadata.setLanguage(truncate((lang == null || "UND".equalsIgnoreCase(lang)) ? "en" : lang, 1000));

        metadata.setNarrator(truncate(audioMetadata.getNarrator(), 500));

        bookCreatorService.addAuthorsToBook(audioMetadata.getAuthors(), bookEntity);

        if (audioMetadata.getCategories() != null) {
            Set<String> validCategories = audioMetadata.getCategories().stream()
                    .filter(s -> s != null && !s.isBlank() && s.length() <= 100 && !s.contains("\n") && !s.contains("\r") && !s.contains("  "))
                    .collect(Collectors.toSet());
            bookCreatorService.addCategoriesToBook(validCategories, bookEntity);
        }

        setAudiobookTechnicalMetadata(bookEntity, audioMetadata);
    }

    public void setAudiobookTechnicalMetadata(BookEntity bookEntity, BookMetadata audioMetadata) {
        AudiobookMetadata audiobookDto = audioMetadata.getAudiobookMetadata();
        if (audiobookDto == null) {
            return;
        }

        BookFileEntity audiobookFile = bookEntity.getBookFiles().stream()
                .filter(bf -> bf.getBookType() == BookFileType.AUDIOBOOK && bf.isBook())
                .findFirst()
                .orElse(null);

        if (audiobookFile == null) {
            log.warn("No audiobook file found for book ID {}", bookEntity.getId());
            return;
        }

        audiobookFile.setDurationSeconds(audiobookDto.getDurationSeconds());
        audiobookFile.setBitrate(audiobookDto.getBitrate());
        audiobookFile.setSampleRate(audiobookDto.getSampleRate());
        audiobookFile.setChannels(audiobookDto.getChannels());
        audiobookFile.setCodec(truncate(audiobookDto.getCodec(), 50));
        audiobookFile.setChapterCount(audiobookDto.getChapterCount());
        audiobookFile.setChapters(mapChapters(audiobookDto.getChapters()));
    }

    private List<BookFileEntity.AudioFileChapter> mapChapters(List<AudiobookMetadata.ChapterInfo> chapters) {
        if (chapters == null || chapters.isEmpty()) {
            return null;
        }
        return chapters.stream()
                .map(ch -> BookFileEntity.AudioFileChapter.builder()
                        .index(ch.getIndex())
                        .title(ch.getTitle())
                        .startTimeMs(ch.getStartTimeMs())
                        .endTimeMs(ch.getEndTimeMs())
                        .durationMs(ch.getDurationMs())
                        .build())
                .collect(Collectors.toList());
    }
}
