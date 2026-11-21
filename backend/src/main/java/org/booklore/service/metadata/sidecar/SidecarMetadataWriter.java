package org.booklore.service.metadata.sidecar;

import lombok.extern.slf4j.Slf4j;
import org.booklore.config.AppProperties;
import org.booklore.model.dto.settings.MetadataPersistenceSettings;
import org.booklore.model.dto.settings.SidecarSettings;
import org.booklore.model.dto.sidecar.SidecarMetadata;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.util.FileService;
import org.booklore.util.FileUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Slf4j
@Service
public class SidecarMetadataWriter {

    private final AppProperties appProperties;
    private final SidecarMetadataMapper mapper;
    private final FileService fileService;
    private final AppSettingService appSettingService;
    private final ObjectMapper objectMapper;

    public SidecarMetadataWriter(AppProperties appProperties, SidecarMetadataMapper mapper, FileService fileService, AppSettingService appSettingService) {
        this.appProperties = appProperties;
        this.mapper = mapper;
        this.fileService = fileService;
        this.appSettingService = appSettingService;
        this.objectMapper = JsonMapper.builder()
                .findAndAddModules()
                .configure(SerializationFeature.INDENT_OUTPUT, true)
                .build();
    }

    public void writeSidecarMetadata(BookEntity book) {
        if (!appProperties.isLocalStorage()) {
            return;
        }
        if (book == null || book.getMetadata() == null) {
            log.warn("Cannot write sidecar metadata: book or metadata is null");
            return;
        }

        SidecarSettings settings = getSidecarSettings();
        if (settings == null || !settings.isEnabled()) {
            log.debug("Sidecar metadata is disabled");
            return;
        }

        try {
            Path bookPath = book.getFullFilePath();
            if (bookPath == null || !Files.exists(bookPath)) {
                log.warn("Cannot write sidecar metadata: book file does not exist");
                return;
            }

            Path sidecarPath = getSidecarPath(bookPath);
            BookMetadataEntity metadata = book.getMetadata();

            String coverFileName = null;
            if (settings.isIncludeCoverFile()) {
                String fileName = mapper.getCoverFileName(bookPath);
                if (writeCoverFile(book, bookPath.getParent().resolve(fileName))) {
                    coverFileName = fileName;
                }
            }

            SidecarMetadata sidecarMetadata = mapper.toSidecarMetadata(metadata, coverFileName);
            String json = objectMapper.writeValueAsString(sidecarMetadata);
            json = json.replace(" : ", ": ").replace("[ ]", "[]");
            Files.writeString(sidecarPath, json);

            log.info("Wrote sidecar metadata to: {}", sidecarPath);
        } catch (IOException e) {
            log.error("Failed to write sidecar metadata for book ID {}: {}", book.getId(), e.getMessage());
        }
    }

    public void deleteSidecarFiles(Path bookPath) {
        if (bookPath == null) {
            return;
        }

        try {
            Path sidecarPath = getSidecarPath(bookPath);
            if (Files.exists(sidecarPath)) {
                try {
                    String declaredCoverPath = objectMapper.readTree(Files.readString(sidecarPath)).path("cover").path("path").asText();
                    if (StringUtils.hasText(declaredCoverPath)
                            && Files.deleteIfExists(FileUtils.resolvePathWithinBase(bookPath.getParent(), declaredCoverPath))) {
                        log.info("Deleted sidecar cover file declared by {}", sidecarPath);
                    }
                } catch (IOException | JacksonException | IllegalArgumentException e) {
                    log.warn("Failed to delete declared sidecar cover for {}: {}", bookPath, e.getMessage());
                }
                Files.delete(sidecarPath);
                log.info("Deleted sidecar file: {}", sidecarPath);
            }

            Path coverPath = getCoverPath(bookPath);
            if (Files.exists(coverPath)) {
                Files.delete(coverPath);
                log.info("Deleted sidecar cover file: {}", coverPath);
            }
        } catch (IOException e) {
            log.warn("Failed to delete sidecar files for {}: {}", bookPath, e.getMessage());
        }
    }

    public void moveSidecarFiles(Path oldBookPath, Path newBookPath) {
        if (oldBookPath == null || newBookPath == null) {
            return;
        }

        try {
            oldBookPath = oldBookPath.toAbsolutePath().normalize();
            newBookPath = newBookPath.toAbsolutePath().normalize();
            Path oldSidecarPath = getSidecarPath(oldBookPath);
            Path newSidecarPath = getSidecarPath(newBookPath);
            boolean hasSidecar = Files.exists(oldSidecarPath);
            if (hasSidecar) {
                Files.createDirectories(newSidecarPath.getParent());
                Files.move(oldSidecarPath, newSidecarPath, StandardCopyOption.REPLACE_EXISTING);
                log.info("Moved sidecar file from {} to {}", oldSidecarPath, newSidecarPath);
            }

            Path oldCoverPath = getCoverPath(oldBookPath);
            Path newCoverPath = getCoverPath(newBookPath);
            if (Files.exists(oldCoverPath)) {
                Files.move(oldCoverPath, newCoverPath, StandardCopyOption.REPLACE_EXISTING);
                log.info("Moved sidecar cover from {} to {}", oldCoverPath, newCoverPath);
            }

            if (!hasSidecar) {
                return;
            }
            JsonNode sidecar = objectMapper.readTree(Files.readString(newSidecarPath));
            String declaredCoverPath = sidecar.path("cover").path("path").asText();
            if (!(sidecar.path("cover") instanceof ObjectNode cover) || !StringUtils.hasText(declaredCoverPath)) {
                return;
            }

            Path oldDirectory = oldBookPath.getParent();
            Path newDirectory = newBookPath.getParent();
            Path oldCover = FileUtils.resolvePathWithinBase(oldDirectory, declaredCoverPath);
            Path newCover;
            if (oldCover.equals(oldCoverPath)) {
                newCover = newCoverPath;
            } else if (oldCover.startsWith(oldBookPath)) {
                newCover = newBookPath.resolve(oldBookPath.relativize(oldCover));
            } else {
                newCover = newDirectory.resolve(oldDirectory.relativize(oldCover));
                if (!newCover.equals(oldCover) && Files.isRegularFile(oldCover)) {
                    Files.createDirectories(newCover.getParent());
                    Files.move(oldCover, newCover, StandardCopyOption.REPLACE_EXISTING);
                    log.info("Moved sidecar cover from {} to {}", oldCover, newCover);
                }
            }

            if (!newCover.equals(newDirectory.resolve(declaredCoverPath).normalize())) {
                cover.put("path", newDirectory.relativize(newCover).toString());
                Files.writeString(newSidecarPath, objectMapper.writeValueAsString(sidecar));
            }
        } catch (IOException | JacksonException | IllegalArgumentException e) {
            log.warn("Failed to move or update sidecar files from {} to {}: {}", oldBookPath, newBookPath, e.getMessage());
        }
    }

    public Path getSidecarPath(Path bookPath) {
        String fileName = bookPath.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        String baseName = (dotIndex > 0) ? fileName.substring(0, dotIndex) : fileName;
        return bookPath.getParent().resolve(baseName + ".metadata.json");
    }

    public Path getCoverPath(Path bookPath) {
        String fileName = bookPath.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        String baseName = (dotIndex > 0) ? fileName.substring(0, dotIndex) : fileName;
        return bookPath.getParent().resolve(baseName + ".cover.jpg");
    }

    private boolean writeCoverFile(BookEntity book, Path coverPath) {
        try {
            String coverFile = book.getPrimaryBookFile().getBookType() == BookFileType.AUDIOBOOK
                    ? fileService.getAudiobookCoverFile(book.getId()) : fileService.getCoverFile(book.getId());
            Path sourceCoverPath = Path.of(coverFile);
            if (Files.exists(sourceCoverPath)) {
                Files.copy(sourceCoverPath, coverPath, StandardCopyOption.REPLACE_EXISTING);
                log.info("Wrote cover file to: {}", coverPath);
                return true;
            }
        } catch (IOException e) {
            log.warn("Failed to write cover file for book ID {}: {}", book.getId(), e.getMessage());
        }
        return false;
    }

    private SidecarSettings getSidecarSettings() {
        MetadataPersistenceSettings settings = appSettingService.getAppSettings().getMetadataPersistenceSettings();
        return settings != null ? settings.getSidecarSettings() : null;
    }

    public boolean isWriteOnUpdateEnabled() {
        SidecarSettings settings = getSidecarSettings();
        return settings != null && settings.isEnabled() && settings.isWriteOnUpdate();
    }

    public boolean isWriteOnScanEnabled() {
        SidecarSettings settings = getSidecarSettings();
        return settings != null && settings.isEnabled() && settings.isWriteOnScan();
    }
}
