package org.booklore.service.epub;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookFileRepository;
import org.booklore.service.file.FileFingerprint;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class EpubRepairService {

    private static final int BATCH_SIZE = 100;
    private static final int PROGRESS_INTERVAL = 25;
    private static final String MIMETYPE_NAME = "mimetype";
    private static final byte[] MIMETYPE_CONTENT = "application/epub+zip".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    private final BookFileRepository bookFileRepository;

    public EpubRepairResult repairAll(
            String taskId,
            BooleanSupplier cancellationRequested,
            Consumer<EpubRepairProgress> progressConsumer) {
        long totalCount = bookFileRepository.countActiveBookFilesByType(BookFileType.EPUB);
        int processedCount = 0;
        int repairedCount = 0;
        int unchangedCount = 0;
        int failedCount = 0;
        long afterId = 0;

        while (true) {
            if (cancellationRequested.getAsBoolean()) {
                break;
            }

            List<Long> fileIds = bookFileRepository.findActiveBookFileIdsByTypeAfter(
                    afterId,
                    BookFileType.EPUB,
                    PageRequest.of(0, BATCH_SIZE));
            if (fileIds.isEmpty()) {
                break;
            }
            afterId = fileIds.getLast();

            Map<Long, BookFileEntity> filesById = bookFileRepository.findAllForRepairByIdIn(fileIds).stream()
                    .collect(Collectors.toMap(BookFileEntity::getId, file -> file));
            for (Long fileId : fileIds) {
                if (cancellationRequested.getAsBoolean()) {
                    break;
                }

                BookFileEntity file = filesById.get(fileId);
                if (file == null) {
                    failedCount++;
                    log.warn("EPUB repair task {} could not load book file {}", taskId, fileId);
                } else {
                    try {
                        Path epubPath = file.getFullFilePath();
                        if (repairContainer(epubPath, taskId)) {
                            long fileSizeKb = Files.size(epubPath) / 1024;
                            String currentHash = FileFingerprint.generateHash(epubPath);
                            bookFileRepository.updateFingerprintAndFileSize(fileId, currentHash, fileSizeKb);
                            repairedCount++;
                        } else {
                            unchangedCount++;
                        }
                    } catch (IOException | RuntimeException exception) {
                        failedCount++;
                        log.warn("EPUB repair task {} failed for file '{}' (id {}): {}",
                                taskId,
                                file.getFileName(),
                                fileId,
                                exception.getMessage(),
                                exception);
                    }
                }

                processedCount++;
                if (processedCount % PROGRESS_INTERVAL == 0 || processedCount == totalCount) {
                    progressConsumer.accept(new EpubRepairProgress(
                            processedCount,
                            totalCount,
                            repairedCount,
                            unchangedCount,
                            failedCount));
                }
            }
        }

        return new EpubRepairResult(
                processedCount,
                totalCount,
                repairedCount,
                unchangedCount,
                failedCount,
                cancellationRequested.getAsBoolean());
    }

    boolean repairContainer(Path epubPath, String taskId) throws IOException {
        if (isContainerCompliant(epubPath)) {
            return false;
        }

        Path absolutePath = epubPath.toAbsolutePath().normalize();
        long originalSize = Files.size(absolutePath);
        FileTime originalModified = Files.getLastModifiedTime(absolutePath);
        Path backupPath = absolutePath.resolveSibling(
                absolutePath.getFileName() + "." + taskId.replaceAll("[^A-Za-z0-9._-]", "_") + ".bak");
        Files.copy(absolutePath, backupPath, StandardCopyOption.COPY_ATTRIBUTES);

        Path temporaryPath = Files.createTempFile(absolutePath.getParent(), ".epub-repair-", ".tmp");
        try {
            rewriteContainer(backupPath, temporaryPath);
            validateRebuiltContainer(temporaryPath);

            if (Files.size(absolutePath) != originalSize
                    || !Files.getLastModifiedTime(absolutePath).equals(originalModified)) {
                throw new IOException("EPUB changed while it was being repaired");
            }

            Files.setLastModifiedTime(temporaryPath, originalModified);
            replaceOriginal(temporaryPath, absolutePath);
            log.info("Repaired EPUB container for '{}' (backup: '{}')", absolutePath, backupPath);
            return true;
        } finally {
            Files.deleteIfExists(temporaryPath);
        }
    }

    private boolean isContainerCompliant(Path epubPath) throws IOException {
        try (ZipFile zipFile = new ZipFile(epubPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            if (!entries.hasMoreElements()) {
                return false;
            }

            ZipEntry firstEntry = entries.nextElement();
            if (!MIMETYPE_NAME.equals(firstEntry.getName()) || firstEntry.getMethod() != ZipEntry.STORED) {
                return false;
            }

            int mimetypeCount = 1;
            while (entries.hasMoreElements()) {
                if (MIMETYPE_NAME.equals(entries.nextElement().getName())) {
                    mimetypeCount++;
                }
            }

            return mimetypeCount == 1 && hasCanonicalMimetype(zipFile, firstEntry);
        }
    }

    private boolean hasCanonicalMimetype(ZipFile zipFile, ZipEntry entry) throws IOException {
        try (InputStream input = zipFile.getInputStream(entry)) {
            return java.util.Arrays.equals(input.readNBytes(MIMETYPE_CONTENT.length + 1), MIMETYPE_CONTENT);
        }
    }

    private void rewriteContainer(Path sourcePath, Path targetPath) throws IOException {
        try (ZipFile source = new ZipFile(sourcePath.toFile());
             ZipOutputStream target = new ZipOutputStream(Files.newOutputStream(targetPath))) {
            writeCanonicalMimetype(target);
            Set<String> names = new HashSet<>();
            Enumeration<? extends ZipEntry> entries = source.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (MIMETYPE_NAME.equals(entry.getName())) {
                    continue;
                }
                if (!names.add(entry.getName())) {
                    throw new IOException("Duplicate EPUB entry: " + entry.getName());
                }
                copyEntry(source, entry, target);
            }
        }
    }

    private void writeCanonicalMimetype(ZipOutputStream zipOutputStream) throws IOException {
        CRC32 crc = new CRC32();
        crc.update(MIMETYPE_CONTENT);
        ZipEntry mimetype = new ZipEntry(MIMETYPE_NAME);
        mimetype.setMethod(ZipEntry.STORED);
        mimetype.setSize(MIMETYPE_CONTENT.length);
        mimetype.setCompressedSize(MIMETYPE_CONTENT.length);
        mimetype.setCrc(crc.getValue());
        zipOutputStream.putNextEntry(mimetype);
        zipOutputStream.write(MIMETYPE_CONTENT);
        zipOutputStream.closeEntry();
    }

    private void copyEntry(ZipFile source, ZipEntry entry, ZipOutputStream target) throws IOException {
        ZipEntry copy = new ZipEntry(entry.getName());
        if (entry.getTime() >= 0) {
            copy.setTime(entry.getTime());
        }
        if (entry.getComment() != null) {
            copy.setComment(entry.getComment());
        }
        if (entry.getMethod() == ZipEntry.STORED) {
            copy.setMethod(ZipEntry.STORED);
            copy.setSize(entry.getSize());
            copy.setCompressedSize(entry.getCompressedSize());
            copy.setCrc(entry.getCrc());
        } else if (entry.getMethod() != ZipEntry.DEFLATED) {
            throw new IOException("Unsupported compression method for EPUB entry: " + entry.getName());
        }

        target.putNextEntry(copy);
        try (InputStream input = source.getInputStream(entry)) {
            input.transferTo(target);
        }
        target.closeEntry();
    }

    private void validateRebuiltContainer(Path epubPath) throws IOException {
        try (ZipFile zipFile = new ZipFile(epubPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            if (!entries.hasMoreElements()) {
                throw new IOException("Rebuilt EPUB archive is empty");
            }

            ZipEntry firstEntry = entries.nextElement();
            if (!MIMETYPE_NAME.equals(firstEntry.getName())
                    || firstEntry.getMethod() != ZipEntry.STORED
                    || !hasCanonicalMimetype(zipFile, firstEntry)) {
                throw new IOException("Rebuilt EPUB archive has an invalid mimetype entry");
            }

            while (entries.hasMoreElements()) {
                if (MIMETYPE_NAME.equals(entries.nextElement().getName())) {
                    throw new IOException("Rebuilt EPUB archive contains duplicate mimetype entries");
                }
            }
        }
    }

    private void replaceOriginal(Path temporaryPath, Path originalPath) throws IOException {
        try {
            Files.move(temporaryPath, originalPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryPath, originalPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public record EpubRepairProgress(
            int processedCount,
            long totalCount,
            int repairedCount,
            int unchangedCount,
            int failedCount) {
    }

    public record EpubRepairResult(
            int processedCount,
            long totalCount,
            int repairedCount,
            int unchangedCount,
            int failedCount,
            boolean cancelled) {
    }
}
