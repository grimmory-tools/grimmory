package com.adityachandel.booklore.service.metadata;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.mapper.BookMetadataMapper;
import com.adityachandel.booklore.mapper.MetadataClearFlagsMapper;
import com.adityachandel.booklore.model.MetadataClearFlags;
import com.adityachandel.booklore.model.MetadataUpdateContext;
import com.adityachandel.booklore.model.MetadataUpdateWrapper;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.request.BulkMetadataUpdateRequest;
import com.adityachandel.booklore.model.dto.request.FetchMetadataRequest;
import com.adityachandel.booklore.model.dto.request.ToggleAllLockRequest;
import com.adityachandel.booklore.model.dto.settings.MetadataPersistenceSettings;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.enums.Lock;
import com.adityachandel.booklore.model.enums.MetadataProvider;
import com.adityachandel.booklore.model.websocket.LogNotification;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.BookMetadataRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.book.BookQueryService;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.file.FileFingerprint;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessor;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessorRegistry;
import com.adityachandel.booklore.service.metadata.extractor.CbxMetadataExtractor;
import com.adityachandel.booklore.service.metadata.parser.BookParser;
import com.adityachandel.booklore.service.metadata.writer.MetadataWriter;
import com.adityachandel.booklore.service.metadata.writer.MetadataWriterFactory;
import com.adityachandel.booklore.util.FileService;
import com.adityachandel.booklore.util.FileUtils;
import com.adityachandel.booklore.util.SecurityContextVirtualThread;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class BookMetadataService {

    private static final int BATCH_SIZE = 100;

    private final BookRepository bookRepository;
    private final BookMapper bookMapper;
    private final BookMetadataMapper bookMetadataMapper;
    private final BookMetadataUpdater bookMetadataUpdater;
    private final NotificationService notificationService;
    private final AppSettingService appSettingService;
    private final BookMetadataRepository bookMetadataRepository;
    private final FileService fileService;
    private final BookFileProcessorRegistry processorRegistry;
    private final BookQueryService bookQueryService;
    private final Map<MetadataProvider, BookParser> parserMap;
    private final CbxMetadataExtractor cbxMetadataExtractor;
    private final MetadataWriterFactory metadataWriterFactory;
    private final MetadataClearFlagsMapper metadataClearFlagsMapper;

    public List<BookMetadata> getProspectiveMetadataListForBookId(long bookId, FetchMetadataRequest request) {
        BookEntity bookEntity = bookRepository.findById(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        Book book = bookMapper.toBook(bookEntity);
        List<List<BookMetadata>> allMetadata = request.getProviders().stream()
                .map(provider -> CompletableFuture.supplyAsync(() -> fetchMetadataListFromAProvider(provider, book, request))
                        .exceptionally(e -> {
                            log.error("Error fetching metadata from provider: {}", provider, e);
                            return List.of();
                        }))
                .toList()
                .stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .toList();

        List<BookMetadata> interleavedMetadata = new ArrayList<>();
        int maxSize = allMetadata.stream().mapToInt(List::size).max().orElse(0);

        for (int i = 0; i < maxSize; i++) {
            for (List<BookMetadata> metadataList : allMetadata) {
                if (i < metadataList.size()) {
                    interleavedMetadata.add(metadataList.get(i));
                }
            }
        }

        return interleavedMetadata;
    }

    public List<BookMetadata> fetchMetadataListFromAProvider(MetadataProvider provider, Book book, FetchMetadataRequest request) {
        return getParser(provider).fetchMetadata(book, request);
    }


    private BookParser getParser(MetadataProvider provider) {
        BookParser parser = parserMap.get(provider);
        if (parser == null) {
            throw ApiError.METADATA_SOURCE_NOT_IMPLEMENT_OR_DOES_NOT_EXIST.createException();
        }
        return parser;
    }

    public void toggleFieldLocks(List<Long> bookIds, Map<String, String> fieldActions) {
        Map<String, String> fieldMapping = Map.of(
                "thumbnailLocked", "coverLocked"
        );
        List<BookMetadataEntity> metadataEntities = bookMetadataRepository
                .getMetadataForBookIds(bookIds)
                .stream()
                .distinct()
                .toList();

        for (BookMetadataEntity metadataEntity : metadataEntities) {
            fieldActions.forEach((field, action) -> {
                String entityField = fieldMapping.getOrDefault(field, field);
                try {
                    String setterName = "set" + Character.toUpperCase(entityField.charAt(0)) + entityField.substring(1);
                    Method setter = BookMetadataEntity.class.getMethod(setterName, Boolean.class);
                    setter.invoke(metadataEntity, "LOCK".equalsIgnoreCase(action));
                } catch (Exception e) {
                    throw new RuntimeException("Failed to invoke setter for field: " + entityField + " on bookId: " + metadataEntity.getBookId(), e);
                }
            });
        }

        bookMetadataRepository.saveAll(metadataEntities);
    }

    @Transactional
    public List<BookMetadata> toggleAllLock(ToggleAllLockRequest request) {
        boolean lock = request.getLock() == Lock.LOCK;
        List<BookEntity> books = bookQueryService.findAllWithMetadataByIds(request.getBookIds())
                .stream()
                .peek(book -> book.getMetadata().applyLockToAllFields(lock))
                .toList();
        bookRepository.saveAll(books);
        return books.stream().map(b -> bookMetadataMapper.toBookMetadata(b.getMetadata(), false)).collect(Collectors.toList());
    }

    @Transactional
    public BookMetadata updateCoverImageFromFile(Long bookId, MultipartFile file) {
        fileService.createThumbnailFromFile(bookId, file);
        return updateCover(bookId, (writer, book) -> writer.replaceCoverImageFromUpload(book, file));
    }

    public void updateCoverImageFromFileForBooks(Set<Long> bookIds, MultipartFile file) {
        validateCoverFile(file);
        byte[] coverImageBytes = extractBytesFromMultipartFile(file);
        List<BookCoverInfo> unlockedBooks = getUnlockedBookCoverInfos(bookIds);

        SecurityContextVirtualThread.runWithSecurityContext(() -> 
            processBulkCoverUpdate(unlockedBooks, coverImageBytes));
    }

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

    private record BookCoverInfo(Long id, String title) {}

    private List<BookCoverInfo> getUnlockedBookCoverInfos(Set<Long> bookIds) {
        return bookQueryService.findAllWithMetadataByIds(bookIds).stream()
                .filter(book -> !isCoverLocked(book))
                .map(book -> new BookCoverInfo(book.getId(), book.getMetadata().getTitle()))
                .toList();
    }

    private boolean isCoverLocked(BookEntity book) {
        return book.getMetadata().getCoverLocked() != null && book.getMetadata().getCoverLocked();
    }

    private void processBulkCoverUpdate(List<BookCoverInfo> books, byte[] coverImageBytes) {
        try {
            int total = books.size();
            notificationService.sendMessage(Topic.LOG, LogNotification.info("Started updating covers for " + total + " selected book(s)"));

            int current = 1;
            for (BookCoverInfo bookInfo : books) {
                try {
                    String progress = "(" + current + "/" + total + ") ";
                    notificationService.sendMessage(Topic.LOG, LogNotification.info(progress + "Updating cover for: " + bookInfo.title()));
                    fileService.createThumbnailFromBytes(bookInfo.id(), coverImageBytes);
                    log.info("{}Successfully updated cover for book ID {} ({})", progress, bookInfo.id(), bookInfo.title());
                } catch (Exception e) {
                    log.error("Failed to update cover for book ID {}: {}", bookInfo.id(), e.getMessage(), e);
                }
                pauseAfterBatchIfNeeded(current, total);
                current++;
            }
            notificationService.sendMessage(Topic.LOG, LogNotification.info("Finished updating covers for selected books"));
        } catch (Exception e) {
            log.error("Error during cover update: {}", e.getMessage(), e);
            notificationService.sendMessage(Topic.LOG, LogNotification.error("Error occurred during cover update"));
        }
    }

    @Transactional
    public BookMetadata updateCoverImageFromUrl(Long bookId, String url) {
        fileService.createThumbnailFromUrl(bookId, url);
        return updateCover(bookId, (writer, book) -> writer.replaceCoverImageFromUrl(book, url));
    }

    private BookMetadata updateCover(Long bookId, BiConsumer<MetadataWriter, BookEntity> writerAction) {
        BookEntity bookEntity = bookRepository.findById(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        bookEntity.getMetadata().setCoverUpdatedOn(Instant.now());
        MetadataPersistenceSettings settings = appSettingService.getAppSettings().getMetadataPersistenceSettings();
        boolean saveToOriginalFile = settings.isSaveToOriginalFile();
        boolean convertCbrCb7ToCbz = settings.isConvertCbrCb7ToCbz();
        if (saveToOriginalFile && (bookEntity.getBookType() != BookFileType.CBX || convertCbrCb7ToCbz)) {
            metadataWriterFactory.getWriter(bookEntity.getBookType())
                    .ifPresent(writer -> {
                        writerAction.accept(writer, bookEntity);
                        String newHash = FileFingerprint.generateHash(bookEntity.getFullFilePath());
                        bookEntity.setCurrentHash(newHash);
                    });
        }
        bookRepository.save(bookEntity);
        return bookMetadataMapper.toBookMetadata(bookEntity.getMetadata(), true);
    }

    public void regenerateCover(long bookId) {
        BookEntity bookEntity = bookRepository.findById(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        if (bookEntity.getMetadata().getCoverLocked() != null && bookEntity.getMetadata().getCoverLocked()) {
            throw ApiError.METADATA_LOCKED.createException();
        } else {
            regenerateCoverForBook(bookEntity, "");
        }
    }

    private record BookRegenerationInfo(Long id, String title, BookFileType bookType) {}

    public void regenerateCoversForBooks(Set<Long> bookIds) {
        List<BookRegenerationInfo> unlockedBooks = getUnlockedBookRegenerationInfos(bookIds);
        SecurityContextVirtualThread.runWithSecurityContext(() -> 
            processBulkCoverRegeneration(unlockedBooks));
    }

    private List<BookRegenerationInfo> getUnlockedBookRegenerationInfos(Set<Long> bookIds) {
        return bookQueryService.findAllWithMetadataByIds(bookIds).stream()
                .filter(book -> !isCoverLocked(book))
                .map(book -> new BookRegenerationInfo(book.getId(), book.getMetadata().getTitle(), book.getBookType()))
                .toList();
    }

    private void processBulkCoverRegeneration(List<BookRegenerationInfo> books) {
        try {
            int total = books.size();
            notificationService.sendMessage(Topic.LOG, LogNotification.info("Started regenerating covers for " + total + " selected book(s)"));

            int current = 1;
            for (BookRegenerationInfo bookInfo : books) {
                try {
                    String progress = "(" + current + "/" + total + ") ";
                    notificationService.sendMessage(Topic.LOG, LogNotification.info(progress + "Regenerating cover for: " + bookInfo.title()));
                    regenerateCoverForBookId(bookInfo);
                    log.info("{}Successfully regenerated cover for book ID {} ({})", progress, bookInfo.id(), bookInfo.title());
                } catch (Exception e) {
                    log.error("Failed to regenerate cover for book ID {}: {}", bookInfo.id(), e.getMessage(), e);
                }
                pauseAfterBatchIfNeeded(current, total);
                current++;
            }
            notificationService.sendMessage(Topic.LOG, LogNotification.info("Finished regenerating covers for selected books"));
        } catch (Exception e) {
            log.error("Error during cover regeneration: {}", e.getMessage(), e);
            notificationService.sendMessage(Topic.LOG, LogNotification.error("Error occurred during cover regeneration"));
        }
    }

    private void pauseAfterBatchIfNeeded(int current, int total) {
        if (current % BATCH_SIZE == 0 && current < total) {
            try {
                log.info("Processed {} items, pausing briefly before next batch...", current);
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Batch pause interrupted");
            }
        }
    }

    private void regenerateCoverForBookId(BookRegenerationInfo bookInfo) {
        bookRepository.findById(bookInfo.id()).ifPresent(book -> {
            BookFileProcessor processor = processorRegistry.getProcessorOrThrow(bookInfo.bookType());
            processor.generateCover(book);
        });
    }

    public void regenerateCovers() {
        SecurityContextVirtualThread.runWithSecurityContext(() -> {
            try {
                List<BookEntity> books = bookQueryService.getAllFullBookEntities().stream()
                        .filter(book -> !isCoverLocked(book))
                        .toList();
                int total = books.size();
                notificationService.sendMessage(Topic.LOG, LogNotification.info("Started regenerating covers for " + total + " books"));

                int current = 1;
                for (BookEntity book : books) {
                    try {
                        String progress = "(" + current + "/" + total + ") ";
                        regenerateCoverForBook(book, progress);
                    } catch (Exception e) {
                        log.error("Failed to regenerate cover for book ID {}: {}", book.getId(), e.getMessage(), e);
                    }
                    current++;
                }
                notificationService.sendMessage(Topic.LOG, LogNotification.info("Finished regenerating covers"));
            } catch (Exception e) {
                log.error("Error during cover regeneration: {}", e.getMessage(), e);
                notificationService.sendMessage(Topic.LOG, LogNotification.error("Error occurred during cover regeneration"));
            }
        });
    }

    private void regenerateCoverForBook(BookEntity book, String progress) {
        String title = book.getMetadata().getTitle();
        notificationService.sendMessage(Topic.LOG, LogNotification.info(progress + "Regenerating cover for: " + title));

        BookFileProcessor processor = processorRegistry.getProcessorOrThrow(book.getBookType());
        processor.generateCover(book);

        log.info("{}Successfully regenerated cover for book ID {} ({})", progress, book.getId(), title);
    }

    public BookMetadata getComicInfoMetadata(long bookId) {
        log.info("Extracting ComicInfo metadata for book ID: {}", bookId);
        BookEntity bookEntity = bookRepository.findById(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        if (bookEntity.getBookType() != BookFileType.CBX) {
            log.info("Unsupported operation for file type: {}", bookEntity.getBookType().name());
            return null;
        }
        return cbxMetadataExtractor.extractMetadata(new File(FileUtils.getBookFullPath(bookEntity)));
    }

    @Transactional
    public void bulkUpdateMetadata(BulkMetadataUpdateRequest request, boolean mergeCategories, boolean mergeMoods, boolean mergeTags) {
        List<BookEntity> books = bookRepository.findAllWithMetadataByIds(request.getBookIds());

        MetadataClearFlags clearFlags = metadataClearFlagsMapper.toClearFlags(request);

        for (BookEntity book : books) {
            BookMetadata bookMetadata = BookMetadata.builder()
                    .authors(request.getAuthors())
                    .publisher(request.getPublisher())
                    .language(request.getLanguage())
                    .seriesName(request.getSeriesName())
                    .seriesTotal(request.getSeriesTotal())
                    .publishedDate(request.getPublishedDate())
                    .categories(request.getGenres())
                    .moods(request.getMoods())
                    .tags(request.getTags())
                    .build();

            MetadataUpdateContext context = MetadataUpdateContext.builder()
                    .bookEntity(book)
                    .metadataUpdateWrapper(MetadataUpdateWrapper.builder()
                            .metadata(bookMetadata)
                            .clearFlags(clearFlags)
                            .build())
                    .updateThumbnail(false)
                    .mergeCategories(mergeCategories)
                    .mergeMoods(mergeMoods)
                    .mergeTags(mergeTags)
                    .build();

            bookMetadataUpdater.setBookMetadata(context);
            notificationService.sendMessage(Topic.BOOK_UPDATE, bookMapper.toBook(book));
        }
    }
}
