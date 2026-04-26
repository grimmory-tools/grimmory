package org.booklore.service.metadata.writer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.booklore.model.MetadataClearFlags;
import org.booklore.model.dto.settings.MetadataPersistenceSettings;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.service.appsettings.AppSettingService;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.images.ArtworkFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;

@Slf4j
@Component
@RequiredArgsConstructor
public class AudiobookMetadataWriter implements MetadataWriter {

    static {
        Logger.getLogger("org.jaudiotagger").setLevel(Level.WARNING);
    }

    private final AppSettingService appSettingService;

    @Override
    public void saveMetadataToFile(File audioFile, BookMetadataEntity metadata, String thumbnailUrl, MetadataClearFlags clear) {
        if (audioFile.isDirectory()) {
            if (StringUtils.isNotBlank(thumbnailUrl)) {
                byte[] coverData = loadImage(thumbnailUrl);
                if (coverData != null) {
                    saveCoverToFolder(audioFile.toPath(), coverData);
                }
            }
            return;
        }

        if (!shouldSaveMetadataToFile(audioFile)) {
            return;
        }

        File backupFile = new File(audioFile.getParentFile(), audioFile.getName() + ".bak");
        try {
            Files.copy(audioFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            log.warn("Failed to create backup of audiobook {}: {}", audioFile.getName(), ex.getMessage());
            return;
        }

        try {
            AudioFile f = AudioFileIO.read(audioFile);
            Tag tag = f.getTagOrCreateAndSetDefault();

            boolean[] hasChanges = {false};
            MetadataCopyHelper helper = new MetadataCopyHelper(metadata);

            helper.copyTitle(clear != null && clear.isTitle(), val -> {
                setTagField(tag, FieldKey.ALBUM, val, hasChanges);
                setTagField(tag, FieldKey.TITLE, val, hasChanges);
            });

            helper.copyAuthors(clear != null && clear.isAuthors(), authors -> {
                String authorStr = authors != null ? String.join("; ", authors) : null;
                setTagField(tag, FieldKey.ALBUM_ARTIST, authorStr, hasChanges);
                setTagField(tag, FieldKey.ARTIST, authorStr, hasChanges);
            });

            if (StringUtils.isNotBlank(metadata.getNarrator())) {
                setTagField(tag, FieldKey.COMPOSER, metadata.getNarrator(), hasChanges);
            }

            helper.copyDescription(clear != null && clear.isDescription(), val -> {
                setTagField(tag, FieldKey.COMMENT, val, hasChanges);
            });

            helper.copyPublisher(clear != null && clear.isPublisher(), val -> {
                setTagField(tag, FieldKey.RECORD_LABEL, val, hasChanges);
            });

            helper.copyPublishedDate(clear != null && clear.isPublishedDate(), val -> {
                String year = val != null ? String.valueOf(val.getYear()) : null;
                setTagField(tag, FieldKey.YEAR, year, hasChanges);
            });

            helper.copyCategories(clear != null && clear.isCategories(), categories -> {
                String genre = categories != null && !categories.isEmpty()
                        ? String.join("; ", categories)
                        : null;
                setTagField(tag, FieldKey.GENRE, genre, hasChanges);
            });

            helper.copyLanguage(clear != null && clear.isLanguage(), val -> {
                setTagField(tag, FieldKey.LANGUAGE, val, hasChanges);
            });

            helper.copySeriesName(clear != null && clear.isSeriesName(), val -> {
                setTagField(tag, FieldKey.GROUPING, val, hasChanges);
            });

            helper.copySeriesNumber(clear != null && clear.isSeriesNumber(), val -> {
                String trackNo = val != null ? String.format("%.0f", val) : null;
                setTagField(tag, FieldKey.TRACK, trackNo, hasChanges);
            });

            helper.copySeriesTotal(clear != null && clear.isSeriesTotal(), val -> {
                String trackTotal = val != null ? String.valueOf(val) : null;
                setTagField(tag, FieldKey.TRACK_TOTAL, trackTotal, hasChanges);
            });

            if (StringUtils.isNotBlank(thumbnailUrl)) {
                byte[] coverData = loadImage(thumbnailUrl);
                if (coverData != null) {
                    try {
                        tag.deleteArtworkField();
                        Artwork artwork = ArtworkFactory.getNew();
                        artwork.setBinaryData(coverData);
                        artwork.setMimeType(detectMimeType(coverData));
                        tag.setField(artwork);
                        hasChanges[0] = true;
                    } catch (Exception e) {
                        log.warn("Failed to set cover art for {}: {}", audioFile.getName(), e.getMessage());
                    }
                }
            }

            if (hasChanges[0]) {
                f.commit();
                log.info("Metadata updated in audiobook: {}", audioFile.getName());
            } else {
                log.debug("No changes detected. Skipping audiobook write for: {}", audioFile.getName());
            }

        } catch (Exception e) {
            log.warn("Failed to write metadata to audiobook file {}: {}", audioFile.getName(), e.getMessage(), e);
            if (backupFile.exists()) {
                try {
                    Files.copy(backupFile.toPath(), audioFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    log.info("Restored audiobook from backup: {}", audioFile.getName());
                } catch (IOException io) {
                    log.error("Failed to restore audiobook from backup for {}: {}", audioFile.getName(), io.getMessage(), io);
                }
            }
        } finally {
            if (backupFile.exists()) {
                try {
                    Files.delete(backupFile.toPath());
                } catch (IOException ex) {
                    log.warn("Failed to delete backup for {}: {}", audioFile.getName(), ex.getMessage());
                }
            }
        }
    }

    public void saveCoverToFolder(Path folderPath, byte[] coverData) {
        if (coverData == null || coverData.length == 0 || folderPath == null) {
            return;
        }

        try {
            String extension = detectImageExtension(coverData);
            String filename = "cover" + extension;
            Path coverPath = folderPath.resolve(filename);

            deleteExistingCovers(folderPath);

            Files.write(coverPath, coverData);
            log.info("Cover image saved to folder: {}", coverPath);
        } catch (IOException e) {
            log.warn("Failed to save cover to folder {}: {}", folderPath, e.getMessage());
        }
    }

    private void deleteExistingCovers(Path folderPath) throws IOException {
        String[] coverNames = {"cover.jpg", "cover.jpeg", "cover.png", "folder.jpg", "folder.jpeg", "folder.png"};
        for (String name : coverNames) {
            Path existing = folderPath.resolve(name);
            Files.deleteIfExists(existing);
        }
    }

    private String detectImageExtension(byte[] data) {
        if (data.length > 3 && data[0] == (byte) 0x89 && data[1] == (byte) 0x50 && data[2] == (byte) 0x4E && data[3] == (byte) 0x47) {
            return ".png";
        }
        return ".jpg";
    }

    private void setTagField(Tag tag, FieldKey key, String value, boolean[] hasChanges) {
        try {
            String existingValue = tag.getFirst(key);
            if (value == null || value.isBlank()) {
                if (StringUtils.isNotBlank(existingValue)) {
                    tag.deleteField(key);
                    hasChanges[0] = true;
                }
            } else if (!value.equals(existingValue)) {
                tag.setField(key, value);
                hasChanges[0] = true;
            }
        } catch (Exception e) {
            log.debug("Failed to set tag field {}: {}", key, e.getMessage());
        }
    }

    @Override
    public void replaceCoverImageFromBytes(BookEntity bookEntity, byte[] coverData) {
        if (coverData == null || coverData.length == 0) {
            log.warn("Cover update failed: empty or null byte array.");
            return;
        }

        BookFileEntity audioFile = getAudiobookFile(bookEntity);
        if (audioFile == null) {
            return;
        }

        if (audioFile.isFolderBased()) {
            Path folderPath = audioFile.getFullFilePath();
            saveCoverToFolder(folderPath, coverData);
        } else {
            File file = audioFile.getFullFilePath().toFile();
            if (!shouldSaveMetadataToFile(file)) {
                return;
            }
            replaceCoverImageInternal(file, coverData, "byte array");
        }
    }

    @Override
    public void replaceCoverImageFromUpload(BookEntity bookEntity, MultipartFile multipartFile) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            log.warn("Cover upload failed: empty or null file.");
            return;
        }

        try {
            byte[] coverData = multipartFile.getBytes();
            replaceCoverImageFromBytes(bookEntity, coverData);
        } catch (IOException e) {
            log.warn("Failed to read uploaded cover image: {}", e.getMessage(), e);
        }
    }

    @Override
    public void replaceCoverImageFromUrl(BookEntity bookEntity, String url) {
        if (url == null || url.isBlank()) {
            log.warn("Cover update via URL failed: empty or null URL.");
            return;
        }

        byte[] coverData = loadImage(url);
        if (coverData == null) {
            log.warn("Failed to load image from URL: {}", url);
            return;
        }

        replaceCoverImageFromBytes(bookEntity, coverData);
    }

    private void replaceCoverImageInternal(File audioFile, byte[] coverData, String source) {
        try {
            AudioFile f = AudioFileIO.read(audioFile);
            Tag tag = f.getTagOrCreateAndSetDefault();

            tag.deleteArtworkField();
            Artwork artwork = ArtworkFactory.getNew();
            artwork.setBinaryData(coverData);
            artwork.setMimeType(detectMimeType(coverData));
            tag.setField(artwork);
            f.commit();

            log.info("Cover image updated in audiobook from {}: {}", source, audioFile.getName());
        } catch (Exception e) {
            log.warn("Failed to update audiobook cover image from {}: {}", source, e.getMessage(), e);
        }
    }

    private BookFileEntity getAudiobookFile(BookEntity bookEntity) {
        if (bookEntity == null || bookEntity.getBookFiles() == null) {
            return null;
        }
        return bookEntity.getBookFiles().stream()
                .filter(bf -> bf.getBookType() == BookFileType.AUDIOBOOK)
                .findFirst()
                .orElse(null);
    }

    @Override
    public BookFileType getSupportedBookType() {
        return BookFileType.AUDIOBOOK;
    }

    @Override
    public boolean shouldSaveMetadataToFile(File audioFile) {
        MetadataPersistenceSettings.SaveToOriginalFile settings = appSettingService.getAppSettings()
                .getMetadataPersistenceSettings().getSaveToOriginalFile();

        MetadataPersistenceSettings.FormatSettings audiobookSettings = settings.getAudiobook();
        if (audiobookSettings == null || !audiobookSettings.isEnabled()) {
            log.debug("Audiobook metadata writing is disabled. Skipping: {}", audioFile.getName());
            return false;
        }

        long fileSizeInMb = audioFile.length() / (1024 * 1024);
        if (fileSizeInMb > audiobookSettings.getMaxFileSizeInMb()) {
            log.info("Audiobook file {} ({} MB) exceeds max size limit ({} MB). Skipping metadata write.",
                    audioFile.getName(), fileSizeInMb, audiobookSettings.getMaxFileSizeInMb());
            return false;
        }

        return true;
    }

    private byte[] loadImage(String pathOrUrl) {
        try (InputStream stream = pathOrUrl.startsWith("http")
                ? URI.create(pathOrUrl).toURL().openStream()
                : new FileInputStream(pathOrUrl)) {
            return stream.readAllBytes();
        } catch (IOException e) {
            log.warn("Failed to load image from {}: {}", pathOrUrl, e.getMessage());
            return null;
        }
    }

    private String detectMimeType(byte[] data) {
        if (data.length > 3 && data[0] == (byte) 0x89 && data[1] == (byte) 0x50 && data[2] == (byte) 0x4E && data[3] == (byte) 0x47) {
            return "image/png";
        }
        return "image/jpeg";
    }
}
