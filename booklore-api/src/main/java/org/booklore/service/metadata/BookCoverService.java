package org.booklore.service.metadata;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.ApiError;
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
import org.booklore.service.metadata.writer.MetadataWriter;
import org.booklore.service.metadata.writer.MetadataWriterFactory;
import org.booklore.util.BookCoverUtils;
import org.booklore.util.FileService;
import org.booklore.config.AppProperties;
import org.booklore.util.SecurityContextVirtualThread;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
    private final TransactionTemplate transactionTemplate;

    private record BookCoverInfo(Long id, String title) {
    }

    private record BookRegenerationInfo(Long id, String title, BookFileType bookType, boolean coverLocked) {
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

        String title = bookEntity.getMetadata().getTitle();
        String author = getAuthorNames(bookEntity);
        byte[] coverBytes = coverImageGenerator.generateCover(title, author);

        fileService.createThumbnailFromBytes(bookId, coverBytes);
        writeCoverToBookFile(bookEntity, (writer, book) -> writer.replaceCoverImageFromBytes(book, coverBytes));
        updateBookCoverMetadata(bookEntity);
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
                .findFirst()
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

        String title = bookEntity.getMetadata().getTitle();
        String author = getAuthorNames(bookEntity);
        byte[] coverBytes = coverImageGenerator.generateSquareCover(title, author);

        fileService.createAudiobookThumbnailFromBytes(bookId, coverBytes);
        writeAudiobookCoverToFile(bookEntity, (writer, book) -> writer.replaceCoverImageFromBytes(book, coverBytes));
        updateAudiobookCoverMetadata(bookEntity);
        bookRepository.save(bookEntity);
        notifyBookCoverUpdate(bookEntity);
    }

    /**
     * Bulk update cover images from a file for multiple books.
     */
    public void updateCoverFromFileForBooks(Set<Long> bookIds, MultipartFile file) {
        validateCoverFile(file);
        byte[] coverImageBytes = extractBytesFromMultipartFile(file);
        List<BookCoverInfo> unlockedBooks = getUnlockedBookCoverInfos(bookIds);
        SecurityContextVirtualThread.runWithSecurityContext(() -> processBulkCoverUpdate(unlockedBooks, coverImageBytes));
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
     * Find the best ebook (non-audiobook) file for a book, respecting library format priority.
     */
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
                        .findFirst();
                if (match.isPresent()) {
                    return match.get();
                }
            }
        }

        // Fallback: return first non-audiobook file
        return bookFiles.stream()
                .filter(f -> f.getBookType() != BookFileType.AUDIOBOOK)
                .findFirst()
                .orElse(null);
    }

    /**
     * Regenerate covers for a set of books.
     */
    public void regenerateCoversForBooks(Set<Long> bookIds) {
        List<BookRegenerationInfo> unlockedBooks = getUnlockedBookRegenerationInfos(bookIds);
        SecurityContextVirtualThread.runWithSecurityContext(() -> processBulkCoverRegeneration(unlockedBooks));
    }

    /**
     * Generate custom covers for a set of books.
     */
    public void generateCustomCoversForBooks(Set<Long> bookIds) {
        List<BookCoverInfo> unlockedBooks = getUnlockedBookCoverInfos(bookIds);
        SecurityContextVirtualThread.runWithSecurityContext(() -> processBulkCustomCoverGeneration(unlockedBooks));
    }

    /**
     * Regenerate covers for all books, optionally only for books with missing covers.
     */
    public void regenerateCovers(boolean missingOnly) {
        SecurityContextVirtualThread.runWithSecurityContext(() -> {
            try {
                List<BookRegenerationInfo> books = bookQueryService.getAllFullBookEntities().stream()
                        .filter(book -> !isCoverLocked(book))
                        .filter(book -> book.getPrimaryBookFile() != null)
                        .filter(book -> !missingOnly || book.getBookCoverHash() == null)
                        .map(book -> new BookRegenerationInfo(book.getId(), book.getMetadata().getTitle(), book.getPrimaryBookFile().getBookType(), false))
                        .toList();
                int total = books.size();
                String label = missingOnly ? "missing" : "all";
                notificationService.sendMessage(Topic.LOG, LogNotification.info("Started regenerating covers for " + total + " books (" + label + ")"));

                int current = 1;
                List<Long> refreshedIds = new ArrayList<>();

                for (BookRegenerationInfo bookInfo : books) {
                    try {
                        String progress = "(" + current + "/" + total + ") ";
                        notificationService.sendMessage(Topic.LOG, LogNotification.info(progress + "Regenerating cover for: " + bookInfo.title()));

                        transactionTemplate.execute(status -> {
                            bookRepository.findByIdWithBookFiles(bookInfo.id()).ifPresent(book -> {
                                var primaryFile = book.getPrimaryBookFile();
                                if (primaryFile == null) {
                                    log.warn("{}Skipping physical book ID {} ({}) - no file to regenerate cover from", progress, book.getId(), bookInfo.title());
                                    return;
                                }
                                BookFileProcessor processor = processorRegistry.getProcessorOrThrow(primaryFile.getBookType());
                                boolean success = processor.generateCover(book);

                                if (success) {
                                    updateBookCoverMetadata(book);
                                    bookRepository.save(book);
                                    refreshedIds.add(book.getId());
                                    log.info("{}Successfully regenerated cover for book ID {} ({})", progress, book.getId(), bookInfo.title());
                                } else {
                                    log.warn("{}Failed to regenerate cover for book ID {} ({})", progress, book.getId(), bookInfo.title());
                                }
                            });
                            return null;
                        });
                    } catch (Exception e) {
                        log.error("Failed to regenerate cover for book ID {}: {}", bookInfo.id(), e.getMessage(), e);
                    }
                    current++;
                }

                notifyBulkCoverUpdate(refreshedIds);
                notificationService.sendMessage(Topic.LOG, LogNotification.info("Finished regenerating covers"));
            } catch (Exception e) {
                log.error("Error during cover regeneration: {}", e.getMessage(), e);
                notificationService.sendMessage(Topic.LOG, LogNotification.error("Error occurred during cover regeneration"));
            }
        });
    }

    // =========================
    // SECTION: BULK OPERATIONS
    // =========================

    private void processBulkCoverUpdate(List<BookCoverInfo> books, byte[] coverImageBytes) {
        try {
            int total = books.size();
            notificationService.sendMessage(Topic.LOG, LogNotification.info("Started updating covers for " + total + " selected book(s)"));

            int current = 1;
            List<Long> refreshedIds = new ArrayList<>();

            for (BookCoverInfo bookInfo : books) {
                try {
                    String progress = "(" + current + "/" + total + ") ";
                    notificationService.sendMessage(Topic.LOG, LogNotification.info(progress + "Updating cover for: " + bookInfo.title()));

                    transactionTemplate.execute(status -> {
                        bookRepository.findByIdWithBookFiles(bookInfo.id()).ifPresent(book -> {
                            fileService.createThumbnailFromBytes(bookInfo.id(), coverImageBytes);
                            writeCoverToBookFile(book, (writer, b) -> writer.replaceCoverImageFromBytes(b, coverImageBytes));
                            updateBookCoverMetadata(book);
                            bookRepository.save(book);
                            refreshedIds.add(book.getId());
                        });
                        return null;
                    });

                    log.info("{}Successfully updated cover for book ID {} ({})", progress, bookInfo.id(), bookInfo.title());
                } catch (Exception e) {
                    log.error("Failed to update cover for book ID {}: {}", bookInfo.id(), e.getMessage(), e);
                }
                current++;
            }

            notifyBulkCoverUpdate(refreshedIds);
            notificationService.sendMessage(Topic.LOG, LogNotification.info("Finished updating covers for selected books"));
        } catch (Exception e) {
            log.error("Error during cover update: {}", e.getMessage(), e);
            notificationService.sendMessage(Topic.LOG, LogNotification.error("Error occurred during cover update"));
        }
    }

    private void processBulkCoverRegeneration(List<BookRegenerationInfo> books) {
        try {
            int total = books.size();
            notificationService.sendMessage(Topic.LOG, LogNotification.info("Started regenerating covers for " + total + " selected book(s)"));

            int current = 1;
            List<Long> refreshedIds = new ArrayList<>();

            for (BookRegenerationInfo bookInfo : books) {
                try {
                    String progress = "(" + current + "/" + total + ") ";
                    notificationService.sendMessage(Topic.LOG, LogNotification.info(progress + "Regenerating cover for: " + bookInfo.title()));

                    transactionTemplate.execute(status -> {
                        bookRepository.findByIdWithBookFiles(bookInfo.id()).ifPresent(book -> {
                            BookFileProcessor processor = processorRegistry.getProcessorOrThrow(bookInfo.bookType());
                            boolean success = processor.generateCover(book);

                            if (success) {
                                updateBookCoverMetadata(book);
                                bookRepository.save(book);
                                refreshedIds.add(book.getId());
                            }
                        });
                        return null;
                    });

                    log.info("{}Successfully regenerated cover for book ID {} ({})", progress, bookInfo.id(), bookInfo.title());
                } catch (Exception e) {
                    log.error("Failed to regenerate cover for book ID {}: {}", bookInfo.id(), e.getMessage(), e);
                }
                current++;
            }

            notifyBulkCoverUpdate(refreshedIds);
            notificationService.sendMessage(Topic.LOG, LogNotification.info("Finished regenerating covers for selected books"));
        } catch (Exception e) {
            log.error("Error during cover regeneration: {}", e.getMessage(), e);
            notificationService.sendMessage(Topic.LOG, LogNotification.error("Error occurred during cover regeneration"));
        }
    }

    private void processBulkCustomCoverGeneration(List<BookCoverInfo> books) {
        try {
            int total = books.size();
            notificationService.sendMessage(Topic.LOG, LogNotification.info("Started generating custom covers for " + total + " selected book(s)"));

            int current = 1;
            List<Long> refreshedIds = new ArrayList<>();

            for (BookCoverInfo bookInfo : books) {
                try {
                    String progress = "(" + current + "/" + total + ") ";
                    notificationService.sendMessage(Topic.LOG, LogNotification.info(progress + "Generating custom cover for: " + bookInfo.title()));

                    transactionTemplate.execute(status -> {
                        bookRepository.findByIdWithBookFiles(bookInfo.id()).ifPresent(book -> {
                            String title = book.getMetadata().getTitle();
                            String author = getAuthorNames(book);
                            byte[] coverBytes = coverImageGenerator.generateCover(title, author);

                            fileService.createThumbnailFromBytes(book.getId(), coverBytes);
                            writeCoverToBookFile(book, (writer, b) -> writer.replaceCoverImageFromBytes(b, coverBytes));
                            updateBookCoverMetadata(book);
                            bookRepository.save(book);
                            refreshedIds.add(book.getId());
                        });
                        return null;
                    });

                    log.info("{}Successfully generated custom cover for book ID {} ({})", progress, bookInfo.id(), bookInfo.title());
                } catch (Exception e) {
                    log.error("Failed to generate custom cover for book ID {}: {}", bookInfo.id(), e.getMessage(), e);
                }
                current++;
            }

            notifyBulkCoverUpdate(refreshedIds);
            notificationService.sendMessage(Topic.LOG, LogNotification.info("Finished generating custom covers for selected books"));
        } catch (Exception e) {
            log.error("Error during custom cover generation: {}", e.getMessage(), e);
            notificationService.sendMessage(Topic.LOG, LogNotification.error("Error occurred during custom cover generation"));
        }
    }

    // =========================
    // SECTION: INTERNAL HELPERS
    // =========================

    private void validateCoverFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw ApiError.INVALID_INPUT.createException("Uploaded file is empty");
        }
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.toLowerCase().startsWith("image/jpeg") && !contentType.toLowerCase().startsWith("image/png"))) {
            throw ApiError.INVALID_INPUT.createException("Only JPEG and PNG files are allowed");
        }
        long maxFileSize = 5L * 1024 * 1024;
        if (file.getSize() > maxFileSize) {
            throw ApiError.FILE_TOO_LARGE.createException(5);
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

    private List<BookCoverInfo> getUnlockedBookCoverInfos(Set<Long> bookIds) {
        return bookQueryService.findAllWithMetadataByIds(bookIds).stream()
                .filter(book -> !isCoverLocked(book))
                .map(book -> new BookCoverInfo(book.getId(), book.getMetadata().getTitle()))
                .toList();
    }

    private List<BookRegenerationInfo> getUnlockedBookRegenerationInfos(Set<Long> bookIds) {
        return bookQueryService.findAllWithMetadataByIds(bookIds).stream()
                .filter(book -> !isCoverLocked(book))
                .filter(book -> book.getPrimaryBookFile() != null)
                .map(book -> new BookRegenerationInfo(book.getId(), book.getMetadata().getTitle(), book.getPrimaryBookFile().getBookType(), false))
                .toList();
    }

    private boolean isCoverLocked(BookEntity book) {
        return book.getMetadata().getCoverLocked() != null && book.getMetadata().getCoverLocked();
    }

    private boolean isAudiobookCoverLocked(BookEntity book) {
        return book.getMetadata().getAudiobookCoverLocked() != null && book.getMetadata().getAudiobookCoverLocked();
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
                .findFirst()
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

    private void notifyBookCoverUpdate(BookEntity bookEntity) {
        List<BookCoverUpdateProjection> updates = bookRepository.findCoverUpdateInfoByIds(List.of(bookEntity.getId()));
        if (!updates.isEmpty()) {
            notificationService.sendMessage(Topic.BOOKS_COVER_UPDATE, updates);
        }
    }

    private void notifyBulkCoverUpdate(List<Long> refreshedIds) {
        if (refreshedIds.isEmpty()) {
            return;
        }
        List<BookCoverUpdateProjection> updates = bookRepository.findCoverUpdateInfoByIds(refreshedIds);
        if (!updates.isEmpty()) {
            notificationService.sendMessage(Topic.BOOKS_COVER_UPDATE, updates);
        }
    }
}
