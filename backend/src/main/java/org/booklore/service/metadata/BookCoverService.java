package org.booklore.service.metadata;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.settings.MetadataPersistenceSettings;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.websocket.LogNotification;
import org.booklore.model.websocket.Topic;
import org.booklore.repository.BookRepository;
import org.booklore.repository.projection.BookCoverUpdateProjection;
import org.booklore.service.NotificationService;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.book.BookQueryService;
import org.booklore.service.file.FileFingerprint;
import org.booklore.service.fileprocessor.BookFileProcessor;
import org.booklore.service.fileprocessor.BookFileProcessorRegistry;
import org.booklore.service.metadata.sidecar.SidecarMetadataWriter;
import org.booklore.service.metadata.writer.MetadataWriter;
import org.booklore.service.metadata.writer.MetadataWriterFactory;
import org.booklore.util.BookCoverUtils;
import org.booklore.util.FileService;
import org.booklore.util.MimeDetector;
import org.booklore.config.AppProperties;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.enums.PermissionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
@Transactional
public class BookCoverService {

    private static final int BATCH_SIZE = 100;

    private final AppProperties appProperties;
    private final BookRepository bookRepository;
    private final NotificationService notificationService;
    private final AppSettingService appSettingService;
    private final FileService fileService;
    private final BookFileProcessorRegistry processorRegistry;
    private final BookQueryService bookQueryService;
    private final CoverImageGenerator coverImageGenerator;
    private final MetadataWriterFactory metadataWriterFactory;
    private final SidecarMetadataWriter sidecarMetadataWriter;
    private final Executor taskExecutor;
    private final TransactionTemplate transactionTemplate;
    private final AuthenticationService authenticationService;

    private void sendNotification(String username, Topic topic, Object message) {
        if (username != null) {
            notificationService.sendMessageToUser(username, topic, message);
        } else {
            notificationService.sendMessageToPermissions(topic, message, Set.of(PermissionType.ADMIN, PermissionType.MANAGE_LIBRARY));
        }
    }

    private String getCurrentUsername() {
        var user = authenticationService.getAuthenticatedUser();
        return user != null ? user.getUsername() : null;
    }

    private record BookCoverInfo(Long id, String title) {
    }

    // =========================
    // SECTION: COVER UPDATES
    // =========================

    /**
     * Generate a custom cover for a single book.
     */
    public void generateCustomCover(long bookId) {
        BookEntity bookEntity = bookRepository.findByIdWithBookFiles(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        if (isCoverLocked(bookEntity)) {
            throw ApiError.METADATA_LOCKED.createException();
        }

        applyCustomBookCover(bookEntity);
        bookRepository.save(bookEntity);
        notifyBookCoverUpdate(bookEntity);
    }

    /**
     * Update cover image from uploaded file for a single book.
     */
    @Transactional
    public void updateCoverFromFile(Long bookId, MultipartFile file) {
        BookEntity bookEntity = bookRepository.findByIdWithBookFiles(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        if (isCoverLocked(bookEntity)) {
            throw ApiError.METADATA_LOCKED.createException();
        }

        fileService.createThumbnailFromFile(bookId, file);
        writeCoverToBookFile(bookEntity, (writer, book) -> writer.replaceCoverImageFromUpload(book, file));
        updateBookCoverMetadata(bookEntity);
        bookRepository.save(bookEntity);
        notifyBookCoverUpdate(bookEntity);
        writeSidecarMetadata(bookEntity);
    }

    /**
     * Update cover image from a URL for a single book.
     */
    @Transactional
    public void updateCoverFromUrl(Long bookId, String url) {
        BookEntity bookEntity = bookRepository.findByIdWithBookFiles(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        if (isCoverLocked(bookEntity)) {
            throw ApiError.METADATA_LOCKED.createException();
        }

        fileService.createThumbnailFromUrl(bookId, url);
        writeCoverToBookFile(bookEntity, (writer, book) -> writer.replaceCoverImageFromUrl(book, url));
        updateBookCoverMetadata(bookEntity);
        bookRepository.save(bookEntity);
        notifyBookCoverUpdate(bookEntity);
        writeSidecarMetadata(bookEntity);
    }

    // =========================
    // SECTION: AUDIOBOOK COVER UPDATES
    // =========================

    /**
     * Update audiobook cover image from uploaded file for a single book.
     */
    @Transactional
    public void updateAudiobookCoverFromFile(Long bookId, MultipartFile file) {
        BookEntity bookEntity = bookRepository.findByIdWithBookFiles(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        if (isAudiobookCoverLocked(bookEntity)) {
            throw ApiError.METADATA_LOCKED.createException();
        }

        fileService.createAudiobookThumbnailFromFile(bookId, file);
        writeAudiobookCoverToFile(bookEntity, (writer, book) -> writer.replaceCoverImageFromUpload(book, file));
        updateAudiobookCoverMetadata(bookEntity);
        bookRepository.save(bookEntity);
        notifyBookCoverUpdate(bookEntity);
        writeSidecarMetadata(bookEntity);
    }

    /**
     * Update audiobook cover image from a URL for a single book.
     */
    @Transactional
    public void updateAudiobookCoverFromUrl(Long bookId, String url) {
        BookEntity bookEntity = bookRepository.findByIdWithBookFiles(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        if (isAudiobookCoverLocked(bookEntity)) {
            throw ApiError.METADATA_LOCKED.createException();
        }

        fileService.createAudiobookThumbnailFromUrl(bookId, url);
        writeAudiobookCoverToFile(bookEntity, (writer, book) -> writer.replaceCoverImageFromUrl(book, url));
        updateAudiobookCoverMetadata(bookEntity);
        bookRepository.save(bookEntity);
        notifyBookCoverUpdate(bookEntity);
        writeSidecarMetadata(bookEntity);
    }

    /**
     * Regenerate audiobook cover for a single book by extracting from the audiobook file.
     */
    public void regenerateAudiobookCover(long bookId) {
        BookEntity bookEntity = bookRepository.findByIdWithBookFiles(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        if (isAudiobookCoverLocked(bookEntity)) {
            throw ApiError.METADATA_LOCKED.createException();
        }

        // Find the audiobook file
        var audiobookFile = bookEntity.getBookFiles().stream()
                .filter(f -> f.getBookType() == BookFileType.AUDIOBOOK)
                .min(Comparator.comparingLong(BookFileEntity::getId))
                .orElseThrow(() -> ApiError.FAILED_TO_REGENERATE_COVER.createException("no audiobook file found"));

        BookFileProcessor processor = processorRegistry.getProcessorOrThrow(audiobookFile.getBookType());
        boolean success = processor.generateAudiobookCover(bookEntity);
        if (!success) {
            throw ApiError.FAILED_TO_REGENERATE_COVER.createException("no embedded cover image found in the audiobook file");
        }
        updateAudiobookCoverMetadata(bookEntity);
        bookRepository.save(bookEntity);
        notifyBookCoverUpdate(bookEntity);
    }

    /**
     * Generate a custom cover for the audiobook cover of a single book.
     * Uses square cover format appropriate for audiobooks.
     */
    public void generateCustomAudiobookCover(long bookId) {
        BookEntity bookEntity = bookRepository.findByIdWithBookFiles(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        if (isAudiobookCoverLocked(bookEntity)) {
            throw ApiError.METADATA_LOCKED.createException();
        }

        applyCustomAudiobookCover(bookEntity);
        bookRepository.save(bookEntity);
        notifyBookCoverUpdate(bookEntity);
    }

    /**
     * Bulk update cover images from a file for multiple books.
     */
    public void updateCoverFromFileForBooks(Set<Long> bookIds, MultipartFile file) {
        validateCoverFile(file);
        byte[] coverImageBytes = extractBytesFromMultipartFile(file);
        List<BookCoverInfo> unlockedBooks = getBooksWithAnyUnlockedCoverSlot(bookIds);
        String username = getCurrentUsername();
        taskExecutor.execute(() -> processBulkCoverOperation(unlockedBooks, username,
                new BulkCoverMessages("Started updating covers for %d selected book(s)", "Updating cover for",
                        "Finished updating covers for selected books"),
                book -> applyUploadedCoverSlots(book, coverImageBytes)));
    }

    // =========================
    // SECTION: COVER REGENERATION
    // =========================

    /**
     * Regenerate cover for a single book from its ebook file.
     * For books with multiple formats, this specifically uses an ebook (non-audiobook) file,
     * respecting the library's format priority setting.
     */
    public void regenerateCover(long bookId) {
        BookEntity bookEntity = bookRepository.findByIdWithBookFiles(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        if (isCoverLocked(bookEntity)) {
            throw ApiError.METADATA_LOCKED.createException();
        }

        BookFileEntity ebookFile = findEbookFile(bookEntity);
        if (ebookFile == null) {
            throw ApiError.FAILED_TO_REGENERATE_COVER.createException("no ebook file found for the book");
        }

        BookFileProcessor processor = processorRegistry.getProcessorOrThrow(ebookFile.getBookType());
        boolean success = processor.generateCover(bookEntity, ebookFile);
        if (!success) {
            throw ApiError.FAILED_TO_REGENERATE_COVER.createException("no embedded cover image found in the file");
        }
        updateBookCoverMetadata(bookEntity);
        bookRepository.save(bookEntity);
    }

    /**
     * Regenerate covers for a set of books.
     */
    public void regenerateCoversForBooks(Set<Long> bookIds) {
        List<BookCoverInfo> unlockedBooks = bookQueryService.findAllWithMetadataByIds(bookIds).stream()
                .filter(book -> book.getMetadata() != null)
                .filter(book -> needsRegeneration(book, false))
                .map(book -> new BookCoverInfo(book.getId(), book.getMetadata().getTitle()))
                .toList();
        String username = getCurrentUsername();
        taskExecutor.execute(() -> processBulkCoverOperation(unlockedBooks, username,
                new BulkCoverMessages("Started regenerating covers for %d selected book(s)", "Regenerating cover for",
                        "Finished regenerating covers for selected books"),
                book -> regenerateCoverSlots(book, false)));
    }

    /**
     * Generate custom covers for a set of books.
     */
    public void generateCustomCoversForBooks(Set<Long> bookIds) {
        List<BookCoverInfo> unlockedBooks = getBooksWithAnyUnlockedCoverSlot(bookIds);
        String username = getCurrentUsername();
        taskExecutor.execute(() -> processBulkCoverOperation(unlockedBooks, username,
                new BulkCoverMessages("Started generating custom covers for %d selected book(s)", "Generating custom cover for",
                        "Finished generating custom covers for selected books"),
                this::generateCustomCoverSlots));
    }

    /**
     * Regenerate covers for all books, optionally only for books with missing covers.
     */
    public void regenerateCovers(boolean missingOnly) {
        String username = getCurrentUsername();
        taskExecutor.execute(() -> {
            try {
                List<BookCoverInfo> books = bookQueryService.getAllFullBookEntitiesWithFiles().stream()
                        .filter(book -> book.getMetadata() != null)
                        .filter(book -> needsRegeneration(book, missingOnly))
                        .map(book -> new BookCoverInfo(book.getId(), book.getMetadata().getTitle()))
                        .toList();
                String label = missingOnly ? "missing" : "all";
                processBulkCoverOperation(books, username,
                        new BulkCoverMessages("Started regenerating covers for %d books (" + label + ")", "Regenerating cover for",
                                "Finished regenerating covers"),
                        book -> regenerateCoverSlots(book, missingOnly));
            } catch (Exception e) {
                log.error("Error during cover regeneration: {}", e.getMessage(), e);
                sendNotification(username, Topic.LOG, LogNotification.error("Error occurred during cover regeneration"));
            }
        });
    }

    // =========================
    // SECTION: BULK OPERATIONS
    // =========================

    private record BulkCoverMessages(String startFormat, String itemVerb, String finishMessage) {
    }

    @FunctionalInterface
    private interface CoverBatchAction {
        boolean apply(BookEntity book);
    }

    private void processBulkCoverOperation(List<BookCoverInfo> books, String username,
                                           BulkCoverMessages messages, CoverBatchAction action) {
        try {
            int total = books.size();
            sendNotification(username, Topic.LOG, LogNotification.info(String.format(messages.startFormat(), total)));

            int current = 1;

            for (BookCoverInfo bookInfo : books) {
                try {
                    String progress = "(" + current + "/" + total + ") ";
                    sendNotification(username, Topic.LOG, LogNotification.info(progress + messages.itemVerb() + ": " + bookInfo.title()));

                    Boolean updated = transactionTemplate.execute(status ->
                            bookRepository.findByIdWithBookFiles(bookInfo.id())
                                    .map(book -> {
                                        boolean changed = action.apply(book);
                                        if (changed) {
                                            bookRepository.save(book);
                                            notifyBulkCoverUpdate(List.of(book.getId()), username);
                                        }
                                        return changed;
                                    })
                                    .orElse(false)
                    );

                    if (Boolean.TRUE.equals(updated)) {
                        log.info("{}{} book ID {} ({})", progress, messages.itemVerb(), bookInfo.id(), bookInfo.title());
                    } else {
                        log.warn("{}No cover updated for book ID {} ({})", progress, bookInfo.id(), bookInfo.title());
                    }
                } catch (Exception e) {
                    log.error("Failed cover operation for book ID {}: {}", bookInfo.id(), e.getMessage(), e);
                }
                current++;
            }

            sendNotification(username, Topic.LOG, LogNotification.info(messages.finishMessage()));
        } catch (Exception e) {
            log.error("Error during bulk cover operation: {}", e.getMessage(), e);
            sendNotification(username, Topic.LOG, LogNotification.error("Error occurred during cover operation"));
        }
    }

    private boolean applyUploadedCoverSlots(BookEntity book, byte[] coverBytes) {
        boolean updated = false;
        if (hasUnlockedEbookSlot(book)) {
            fileService.createThumbnailFromBytes(book.getId(), coverBytes);
            writeCoverToBookFile(book, (writer, b) -> writer.replaceCoverImageFromBytes(b, coverBytes));
            updateBookCoverMetadata(book);
            updated = true;
        }
        if (hasUnlockedAudiobookSlot(book)) {
            fileService.createAudiobookThumbnailFromBytes(book.getId(), coverBytes);
            writeAudiobookCoverToFile(book, (writer, b) -> writer.replaceCoverImageFromBytes(b, coverBytes));
            updateAudiobookCoverMetadata(book);
            updated = true;
        }
        return updated;
    }

    private boolean regenerateCoverSlots(BookEntity book, boolean missingOnly) {
        boolean updated = false;
        if (ebookSlotNeedsRegeneration(book, missingOnly)) {
            try {
                BookFileEntity ebookFile = findEbookFile(book);
                BookFileProcessor processor = processorRegistry.getProcessorOrThrow(ebookFile.getBookType());
                if (processor.generateCover(book, ebookFile)) {
                    updateBookCoverMetadata(book);
                    updated = true;
                }
            } catch (Exception e) {
                log.error("Failed to regenerate book cover slot for book ID {}: {}", book.getId(), e.getMessage(), e);
            }
        }
        if (audiobookSlotNeedsRegeneration(book, missingOnly)) {
            try {
                BookFileProcessor processor = processorRegistry.getProcessorOrThrow(BookFileType.AUDIOBOOK);
                if (processor.generateAudiobookCover(book)) {
                    updateAudiobookCoverMetadata(book);
                    updated = true;
                }
            } catch (Exception e) {
                log.error("Failed to regenerate audiobook cover slot for book ID {}: {}", book.getId(), e.getMessage(), e);
            }
        }
        return updated;
    }

    private boolean generateCustomCoverSlots(BookEntity book) {
        boolean updated = false;
        if (hasUnlockedEbookSlot(book)) {
            applyCustomBookCover(book);
            updated = true;
        }
        if (hasUnlockedAudiobookSlot(book)) {
            applyCustomAudiobookCover(book);
            updated = true;
        }
        return updated;
    }

    // =========================
    // SECTION: INTERNAL HELPERS
    // =========================

    private long getMaxFileUploadSizeMb() {
        AppSettings appSettings = this.appSettingService.getAppSettings();

        Integer maxFileUploadSizeMb = appSettings.getMaxFileUploadSizeInMb();

        if (maxFileUploadSizeMb == null) {
            log.warn("Max File Upload Size is unset, cannot continue");
            throw ApiError.INTERNAL_SERVER_ERROR.createException("Max File Upload Size is Unset");
        }

        return maxFileUploadSizeMb.longValue();
    }

    private void validateCoverFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw ApiError.INVALID_INPUT.createException("Uploaded file is empty");
        }
        long maxSizeMb = getMaxFileUploadSizeMb();
        long maxFileSize = maxSizeMb * 1024 * 1024;
        if (file.getSize() > maxFileSize) {
            throw ApiError.FILE_TOO_LARGE.createException(maxSizeMb);
        }
        // Detect MIME from content byte never trust the client-supplied Content-Type header
        try (var inputStream = file.getInputStream()) {
            String detectedMime = MimeDetector.detect(inputStream);
            if (!"image/jpeg".equals(detectedMime) && !"image/png".equals(detectedMime)) {
                throw ApiError.INVALID_INPUT.createException("Only JPEG and PNG files are allowed (detected: " + detectedMime + ")");
            }
        } catch (IOException e) {
            throw ApiError.INVALID_INPUT.createException("Failed to read uploaded file for MIME detection");
        }
    }

    private byte[] extractBytesFromMultipartFile(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (Exception e) {
            log.error("Failed to read cover file: {}", e.getMessage());
            throw new RuntimeException("Failed to read cover file", e);
        }
    }

    private List<BookCoverInfo> getBooksWithAnyUnlockedCoverSlot(Set<Long> bookIds) {
        return bookQueryService.findAllWithMetadataByIds(bookIds).stream()
                .filter(book -> book.getMetadata() != null)
                .filter(book -> hasUnlockedEbookSlot(book) || hasUnlockedAudiobookSlot(book))
                .map(book -> new BookCoverInfo(book.getId(), book.getMetadata().getTitle()))
                .toList();
    }

    private boolean isCoverLocked(BookEntity book) {
        return book.getMetadata() != null && Boolean.TRUE.equals(book.getMetadata().getCoverLocked());
    }

    private boolean isAudiobookCoverLocked(BookEntity book) {
        return book.getMetadata() != null && Boolean.TRUE.equals(book.getMetadata().getAudiobookCoverLocked());
    }

    private boolean isEbookBookFile(BookFileEntity file) {
        return file.isBookFormat() && file.getBookType() != BookFileType.AUDIOBOOK;
    }

    private boolean hasAudiobookFile(BookEntity book) {
        var files = book.getBookFiles();
        return files != null && files.stream()
                .anyMatch(f -> f.isBookFormat() && f.getBookType() == BookFileType.AUDIOBOOK);
    }

    private boolean hasEbookFile(BookEntity book) {
        var files = book.getBookFiles();
        return files != null && files.stream().anyMatch(this::isEbookBookFile);
    }

    private BookFileEntity findEbookFile(BookEntity bookEntity) {
        var bookFiles = bookEntity.getBookFiles();
        if (bookFiles == null || bookFiles.isEmpty()) {
            return null;
        }

        var library = bookEntity.getLibrary();
        if (library != null && library.getFormatPriority() != null && !library.getFormatPriority().isEmpty()) {
            for (BookFileType format : library.getFormatPriority()) {
                if (format == BookFileType.AUDIOBOOK) {
                    continue;
                }
                var match = bookFiles.stream()
                        .filter(bf -> bf.isBookFormat() && bf.getBookType() == format)
                        .min(Comparator.comparingLong(BookFileEntity::getId));
                if (match.isPresent()) {
                    return match.get();
                }
            }
        }

        return bookFiles.stream()
                .filter(this::isEbookBookFile)
                .min(Comparator.comparingLong(BookFileEntity::getId))
                .orElse(null);
    }

    private boolean hasUnlockedEbookSlot(BookEntity book) {
        return (!hasAudiobookFile(book) || hasEbookFile(book)) && !isCoverLocked(book);
    }

    private boolean hasUnlockedAudiobookSlot(BookEntity book) {
        return hasAudiobookFile(book) && !isAudiobookCoverLocked(book);
    }

    private boolean ebookSlotNeedsRegeneration(BookEntity book, boolean missingOnly) {
        return hasEbookFile(book) && !isCoverLocked(book) && (!missingOnly || book.getBookCoverHash() == null);
    }

    private boolean audiobookSlotNeedsRegeneration(BookEntity book, boolean missingOnly) {
        return hasUnlockedAudiobookSlot(book) && (!missingOnly || book.getAudiobookCoverHash() == null);
    }

    private boolean needsRegeneration(BookEntity book, boolean missingOnly) {
        return ebookSlotNeedsRegeneration(book, missingOnly) || audiobookSlotNeedsRegeneration(book, missingOnly);
    }

    private void applyCustomBookCover(BookEntity bookEntity) {
        byte[] coverBytes = coverImageGenerator.generateCover(bookEntity.getMetadata().getTitle(), getAuthorNames(bookEntity));
        fileService.createThumbnailFromBytes(bookEntity.getId(), coverBytes);
        writeCoverToBookFile(bookEntity, (writer, book) -> writer.replaceCoverImageFromBytes(book, coverBytes));
        updateBookCoverMetadata(bookEntity);
    }

    private void applyCustomAudiobookCover(BookEntity bookEntity) {
        byte[] coverBytes = coverImageGenerator.generateSquareCover(bookEntity.getMetadata().getTitle(), getAuthorNames(bookEntity));
        fileService.createAudiobookThumbnailFromBytes(bookEntity.getId(), coverBytes);
        writeAudiobookCoverToFile(bookEntity, (writer, book) -> writer.replaceCoverImageFromBytes(book, coverBytes));
        updateAudiobookCoverMetadata(bookEntity);
    }

    private String getAuthorNames(BookEntity bookEntity) {
        if (bookEntity.getMetadata().getAuthors() != null && !bookEntity.getMetadata().getAuthors().isEmpty()) {
            return bookEntity.getMetadata().getAuthors().stream()
                    .map(AuthorEntity::getName)
                    .collect(Collectors.joining(", "));
        }
        return null;
    }

    private void writeCoverToBookFile(BookEntity bookEntity, BiConsumer<MetadataWriter, BookEntity> writerAction) {
        if (!appProperties.isLocalStorage()) {
            return;
        }
        var primaryFile = bookEntity.getPrimaryBookFile();
        if (primaryFile == null) {
            return;
        }

        MetadataPersistenceSettings settings = appSettingService.getAppSettings().getMetadataPersistenceSettings();
        boolean convertCbrCb7ToCbz = settings.isConvertCbrCb7ToCbz();

        if ((primaryFile.getBookType() != BookFileType.CBX || convertCbrCb7ToCbz)) {
            metadataWriterFactory.getWriter(primaryFile.getBookType())
                    .ifPresent(writer -> {
                        writerAction.accept(writer, bookEntity);
                        String newHash = FileFingerprint.generateHash(bookEntity.getFullFilePath());
                        primaryFile.setCurrentHash(newHash);
                    });
        }
    }

    private void writeAudiobookCoverToFile(BookEntity bookEntity, BiConsumer<MetadataWriter, BookEntity> writerAction) {
        if (!appProperties.isLocalStorage()) {
            return;
        }
        var audiobookFile = bookEntity.getBookFiles().stream()
                .filter(f -> f.getBookType() == BookFileType.AUDIOBOOK)
                .min(Comparator.comparingLong(BookFileEntity::getId))
                .orElse(null);

        if (audiobookFile == null) {
            return;
        }

        metadataWriterFactory.getWriter(BookFileType.AUDIOBOOK)
                .ifPresent(writer -> {
                    writerAction.accept(writer, bookEntity);
                    if (!audiobookFile.isFolderBased()) {
                        String newHash = FileFingerprint.generateHash(audiobookFile.getFullFilePath());
                        audiobookFile.setCurrentHash(newHash);
                    }
                });
    }

    private void updateBookCoverMetadata(BookEntity bookEntity) {
        Instant now = Instant.now();
        bookEntity.setMetadataUpdatedAt(now);
        bookEntity.getMetadata().setCoverUpdatedOn(now);
        bookEntity.setBookCoverHash(BookCoverUtils.generateCoverHash());
    }

    private void updateAudiobookCoverMetadata(BookEntity bookEntity) {
        Instant now = Instant.now();
        bookEntity.setMetadataUpdatedAt(now);
        bookEntity.getMetadata().setAudiobookCoverUpdatedOn(now);
        bookEntity.setAudiobookCoverHash(BookCoverUtils.generateCoverHash());
    }

    /**
     * Refresh the sidecar files for a book when sidecar write-on-update is enabled.
     */
    private void writeSidecarMetadata(BookEntity bookEntity) {
        if (!sidecarMetadataWriter.isWriteOnUpdateEnabled()) {
            return;
        }
        try {
            sidecarMetadataWriter.writeSidecarMetadata(bookEntity);
        } catch (Exception e) {
            log.warn("Failed to write sidecar metadata for book ID {}: {}", bookEntity.getId(), e.getMessage());
        }
    }

    private void notifyBookCoverUpdate(BookEntity bookEntity) {
        List<BookCoverUpdateProjection> updates = bookRepository.findCoverUpdateInfoByIds(List.of(bookEntity.getId()));
        if (!updates.isEmpty()) {
            notificationService.sendMessage(Topic.BOOKS_COVER_UPDATE, updates);
        }
    }

    private void notifyBulkCoverUpdate(List<Long> refreshedIds, String username) {
        if (refreshedIds.isEmpty()) {
            return;
        }
        List<BookCoverUpdateProjection> updates = bookRepository.findCoverUpdateInfoByIds(refreshedIds);
        if (!updates.isEmpty()) {
            sendNotification(username, Topic.BOOKS_COVER_UPDATE, updates);
        }
    }
}
