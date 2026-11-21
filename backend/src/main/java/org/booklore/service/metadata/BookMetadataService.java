package org.booklore.service.metadata;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.ApiError;
import org.booklore.mapper.BookMapper;
import org.booklore.mapper.BookMetadataMapper;
import org.booklore.mapper.MetadataClearFlagsMapper;
import org.booklore.model.MetadataClearFlags;
import org.booklore.model.MetadataUpdateContext;
import org.booklore.model.MetadataUpdateWrapper;
import org.booklore.model.dto.ComicMetadata;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.BulkMetadataUpdateFailure;
import org.booklore.model.dto.BulkMetadataUpdateProgress;
import org.booklore.model.dto.BulkMetadataUpdateResult;
import org.booklore.model.dto.request.BulkMetadataUpdateRequest;
import org.booklore.model.dto.request.FetchMetadataRequest;
import org.booklore.model.dto.request.ToggleAllLockRequest;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.enums.*;
import org.booklore.model.websocket.Topic;
import org.booklore.repository.BookMetadataRepository;
import org.booklore.repository.BookRepository;
import org.booklore.service.NotificationService;
import org.booklore.service.audit.AuditService;
import org.booklore.service.book.BookQueryService;
import org.booklore.service.metadata.extractor.CbxMetadataExtractor;
import org.booklore.service.metadata.extractor.MetadataExtractorFactory;
import org.booklore.service.metadata.parser.BookParser;
import org.booklore.service.metadata.parser.DetailedMetadataProvider;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.model.dto.request.MetadataRefreshOptions;
import org.booklore.util.FileUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import org.booklore.model.dto.request.IsbnLookupRequest;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Slf4j
@Service
@AllArgsConstructor
public class BookMetadataService {

    private static final int BULK_UPDATE_BATCH_SIZE = 100;

    private final BookRepository bookRepository;
    private final BookMapper bookMapper;
    private final BookMetadataMapper bookMetadataMapper;
    private final BookMetadataUpdater bookMetadataUpdater;
    private final NotificationService notificationService;
    private final BookMetadataRepository bookMetadataRepository;
    private final BookQueryService bookQueryService;
    private final AuditService auditService;
    private final Map<MetadataProvider, BookParser> parserMap;
    private final CbxMetadataExtractor cbxMetadataExtractor;
    private final MetadataExtractorFactory metadataExtractorFactory;
    private final MetadataClearFlagsMapper metadataClearFlagsMapper;
    private final PlatformTransactionManager transactionManager;
    private final AppSettingService appSettingService;

    @Transactional
    public BookMetadata updateBookMetadata(long bookId, MetadataUpdateWrapper metadataUpdateWrapper,
                                           boolean mergeCategories, MetadataReplaceMode replaceMode) {
        BookEntity bookEntity = bookRepository.findAllWithMetadataByIds(Collections.singleton(bookId)).stream()
                .findFirst()
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(metadataUpdateWrapper)
                .updateThumbnail(true)
                .mergeCategories(mergeCategories)
                .replaceMode(replaceMode)
                .mergeMoods(false)
                .mergeTags(false)
                .build();

        bookMetadataUpdater.setBookMetadata(context);
        auditService.log(AuditAction.METADATA_UPDATED, "Book", bookId,
                "Updated metadata for book: " + bookEntity.getMetadata().getTitle());
        return bookMetadataMapper.toBookMetadata(bookEntity.getMetadata(), true);
    }

    @Transactional(readOnly = true)
    public Flux<BookMetadata> getProspectiveMetadataListForBookId(long bookId, FetchMetadataRequest request) {
        BookEntity bookEntity = bookRepository.findByIdWithBookFiles(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        Book book = bookMapper.toBook(bookEntity);

        return Flux.fromIterable(request.getProviders())
                .flatMap(provider -> {
                    var parser = getParser(provider);

                    if (!parser.isEnabled()) {
                        return Flux.empty();
                    }

                    return Flux.defer(() -> parser.fetchMetadataStream(book, request))
                            .subscribeOn(Schedulers.boundedElastic())
                            .onErrorResume(e -> {
                                log.error("Error fetching metadata from provider: {}", provider, e);
                                return Flux.empty();
                            });
                });
    }

    public List<BookMetadata> fetchMetadataListFromAProvider(MetadataProvider provider, Book book, FetchMetadataRequest request) {
        var parser = getParser(provider);

        if (!parser.isEnabled()) {
            return List.of();
        }

        return parser.fetchMetadata(book, request);
    }


    public BookMetadata lookupByIsbn(IsbnLookupRequest request) {
        List<MetadataProvider> providers = deriveProviderChainFromSettings();

        FetchMetadataRequest fetchRequest = FetchMetadataRequest.builder()
                .isbn(request.getIsbn())
                .providers(providers)
                .build();

        Book emptyBook = Book.builder().build();

        for (MetadataProvider provider : providers) {
            try {
                List<BookMetadata> results = fetchMetadataListFromAProvider(provider, emptyBook, fetchRequest);
                if (results != null && !results.isEmpty()) {
                    return results.getFirst();
                }
            } catch (Exception e) {
                log.warn("ISBN lookup failed for provider {}: {}", provider, e.getMessage());
            }
        }
        return null;
    }

    private List<MetadataProvider> deriveProviderChainFromSettings() {
        try {
            MetadataRefreshOptions options = appSettingService.getAppSettings().getDefaultMetadataRefreshOptions();
            if (options != null && options.getFieldOptions() != null) {
                MetadataRefreshOptions.FieldProvider titleProvider = options.getFieldOptions().getTitle();
                if (titleProvider != null) {
                    List<MetadataProvider> chain = Stream.of(
                                    titleProvider.getP1(), titleProvider.getP2(),
                                    titleProvider.getP3(), titleProvider.getP4())
                            .filter(Objects::nonNull)
                            .distinct()
                            .toList();
                    if (!chain.isEmpty()) {
                        return chain;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to derive provider chain from settings, falling back to default: {}", e.getMessage());
        }
        return List.of(MetadataProvider.Google);
    }

    public BookMetadata getDetailedProviderMetadata(MetadataProvider provider, String providerItemId) {
        BookParser parser = getParser(provider);

        if (!parser.isEnabled()) {
            return null;
        }

        if (parser instanceof DetailedMetadataProvider detailedProvider) {
            return detailedProvider.fetchDetailedMetadata(providerItemId);
        }
        return null;
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
        return books.stream().map(b -> bookMetadataMapper.toBookMetadata(b.getMetadata(), false)).toList();
    }

    public BookMetadata getComicInfoMetadata(long bookId) {
        log.info("Extracting ComicInfo metadata for book ID: {}", bookId);
        BookEntity bookEntity = bookRepository.findByIdWithBookFiles(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        var primaryFile = bookEntity.getPrimaryBookFile();
        if (primaryFile == null || primaryFile.getBookType() != BookFileType.CBX) {
            log.info("Unsupported operation for book ID {} - no file or not CBX type", bookId);
            return null;
        }
        return cbxMetadataExtractor.extractMetadata(FileUtils.getBookFullPath(bookEntity).toFile());
    }

    public BookMetadata getFileMetadata(long bookId) {
        log.info("Extracting file metadata for book ID: {}", bookId);
        BookEntity bookEntity = bookRepository.findByIdWithBookFiles(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        var primaryFile = bookEntity.getPrimaryBookFile();
        if (primaryFile == null) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("Book has no file to extract metadata from");
        }
        return metadataExtractorFactory.extractMetadata(primaryFile.getBookType(), FileUtils.getBookFullPath(bookEntity).toFile());
    }

    public BulkMetadataUpdateResult bulkUpdateMetadata(
            BulkMetadataUpdateRequest request,
            boolean mergeCategories,
            boolean mergeMoods,
            boolean mergeTags,
            Consumer<BulkMetadataUpdateProgress> progressConsumer) {
        MetadataClearFlags clearFlags = metadataClearFlagsMapper.toClearFlags(request);
        if (request.isReplaceAll()) {
            clearFlags = getReplaceAllClearFlags(request);
        }

        BookMetadata bookMetadata = BookMetadata.builder()
                .authors(request.getAuthors())
                .publisher(request.getPublisher())
                .language(request.getLanguage())
                .seriesName(request.getSeriesName())
                .seriesTotal(request.getSeriesTotal())
                .publishedDate(request.getPublishedDate())
                .categories(request.getGenres() != null ? request.getGenres() : Collections.emptySet())
                .moods(request.getMoods() != null ? request.getMoods() : Collections.emptySet())
                .tags(request.getTags() != null ? request.getTags() : Collections.emptySet())
                .ageRating(request.getAgeRating())
                .contentRating(request.getContentRating())
                .build();

        int totalCount = request.getBookIds().size();
        int processedCount = 0;
        int succeededCount = 0;
        int failedCount = 0;
        List<Long> updatedBookIds = new ArrayList<>(BULK_UPDATE_BATCH_SIZE);
        List<BulkMetadataUpdateFailure> batchFailures = new ArrayList<>(BULK_UPDATE_BATCH_SIZE);

        for (Long bookId : request.getBookIds()) {
            try {
                if (processSingleBookUpdate(
                        bookId,
                        bookMetadata,
                        clearFlags,
                        request.isReplaceAll() ? false : mergeCategories,
                        request.isReplaceAll() ? false : mergeMoods,
                        request.isReplaceAll() ? false : mergeTags,
                        request.isReplaceAll())) {
                    updatedBookIds.add(bookId);
                    succeededCount++;
                } else {
                    log.warn("Book not found for metadata update: {}", bookId);
                    batchFailures.add(new BulkMetadataUpdateFailure(bookId, null, "Book not found"));
                    failedCount++;
                }
            } catch (Exception e) {
                log.error("Failed to update metadata for book ID {}", bookId, e);
                String title = e instanceof BookUpdateException updateException ? updateException.getBookTitle() : null;
                Exception reason = e instanceof BookUpdateException updateException && updateException.getCause() instanceof Exception cause
                        ? cause
                        : e;
                batchFailures.add(new BulkMetadataUpdateFailure(bookId, title, getFailureReason(reason)));
                failedCount++;
            } finally {
                processedCount++;
            }

            if (updatedBookIds.size() == BULK_UPDATE_BATCH_SIZE) {
                notificationService.sendMessage(Topic.BOOK_UPDATE, List.copyOf(updatedBookIds));
                updatedBookIds.clear();
            }

            if (batchFailures.size() == BULK_UPDATE_BATCH_SIZE
                    || processedCount % BULK_UPDATE_BATCH_SIZE == 0
                    || processedCount == totalCount) {
                progressConsumer.accept(new BulkMetadataUpdateProgress(
                        processedCount,
                        totalCount,
                        succeededCount,
                        failedCount,
                        List.copyOf(batchFailures)));
                batchFailures.clear();
            }
        }

        if (!updatedBookIds.isEmpty()) {
            notificationService.sendMessage(Topic.BOOK_UPDATE, List.copyOf(updatedBookIds));
        }

        if (totalCount == 0) {
            progressConsumer.accept(new BulkMetadataUpdateProgress(0, 0, 0, 0, List.of()));
        }

        return new BulkMetadataUpdateResult(processedCount, totalCount, succeededCount, failedCount);
    }

    private boolean processSingleBookUpdate(
            Long bookId,
            BookMetadata bookMetadata,
            MetadataClearFlags clearFlags,
            boolean mergeCategories,
            boolean mergeMoods,
            boolean mergeTags,
            boolean replaceAll) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        Boolean updated = transactionTemplate.execute(status -> {
            BookEntity book = bookRepository.findByIdFull(bookId).orElse(null);
            if (book == null) {
                return false;
            }

            MetadataUpdateContext context = MetadataUpdateContext.builder()
                    .bookEntity(book)
                    .metadataUpdateWrapper(MetadataUpdateWrapper.builder()
                            .metadata(getMetadataForBook(bookMetadata, book, replaceAll))
                            .clearFlags(clearFlags)
                            .build())
                    .updateThumbnail(false)
                    .mergeCategories(mergeCategories)
                    .mergeMoods(mergeMoods)
                    .mergeTags(mergeTags)
                    .replaceMode(replaceAll ? MetadataReplaceMode.REPLACE_ALL : null)
                    .build();

            try {
                bookMetadataUpdater.setBookMetadata(context);
            } catch (RuntimeException exception) {
                String title = Optional.ofNullable(book.getMetadata())
                        .map(BookMetadataEntity::getTitle)
                        .orElse(null);
                throw new BookUpdateException(title, exception);
            }
            return true;
        });
        return Boolean.TRUE.equals(updated);
    }

    private BookMetadata getMetadataForBook(BookMetadata metadata, BookEntity book, boolean replaceAll) {
        if (!replaceAll) {
            return metadata;
        }
        BookMetadata.BookMetadataBuilder updatedMetadata = metadata.toBuilder().title(book.getMetadata().getTitle());
        if (book.getMetadata().getComicMetadata() != null) {
            updatedMetadata.comicMetadata(ComicMetadata.builder().build());
        }
        return updatedMetadata.build();
    }

    private MetadataClearFlags getReplaceAllClearFlags(BulkMetadataUpdateRequest request) {
        MetadataClearFlags flags = new MetadataClearFlags();
        flags.setSubtitle(true);
        flags.setPublisher(true);
        flags.setPublishedDate(true);
        flags.setDescription(true);
        flags.setSeriesName(true);
        flags.setSeriesNumber(true);
        flags.setSeriesTotal(true);
        flags.setIsbn13(true);
        flags.setIsbn10(true);
        flags.setOpenlibraryId(true);
        flags.setAsin(true);
        flags.setGoodreadsId(true);
        flags.setBabelioUrl(true);
        flags.setComicvineId(true);
        flags.setHardcoverId(true);
        flags.setHardcoverBookId(true);
        flags.setGoogleId(true);
        flags.setPageCount(true);
        flags.setRating(true);
        flags.setReviewCount(true);
        flags.setLanguage(true);
        flags.setAmazonRating(true);
        flags.setAmazonReviewCount(true);
        flags.setGoodreadsRating(true);
        flags.setGoodreadsReviewCount(true);
        flags.setHardcoverRating(true);
        flags.setHardcoverReviewCount(true);
        flags.setLubimyczytacId(true);
        flags.setLubimyczytacRating(true);
        flags.setRanobedbId(true);
        flags.setRanobedbRating(true);
        flags.setAudibleId(true);
        flags.setAudibleRating(true);
        flags.setAudibleReviewCount(true);
        flags.setApplebooksId(true);
        flags.setApplebooksRating(true);
        flags.setApplebooksReviewCount(true);
        flags.setDoubanId(true);
        flags.setDoubanRating(true);
        flags.setDoubanReviewCount(true);
        flags.setAuthors(!hasUsableValues(request.getAuthors()) || request.isClearAuthors());
        flags.setCategories(!hasUsableValues(request.getGenres()) || request.isClearGenres());
        flags.setMoods(!hasUsableValues(request.getMoods()) || request.isClearMoods());
        flags.setTags(!hasUsableValues(request.getTags()) || request.isClearTags());
        flags.setReviews(true);
        flags.setNarrator(true);
        flags.setAbridged(true);
        flags.setAgeRating(request.getAgeRating() == null || request.isClearAgeRating());
        flags.setContentRating(isBlank(request.getContentRating()) || request.isClearContentRating());

        flags.setPublisher(isBlank(request.getPublisher()) || request.isClearPublisher());
        flags.setPublishedDate(request.getPublishedDate() == null || request.isClearPublishedDate());
        flags.setSeriesName(isBlank(request.getSeriesName()) || request.isClearSeriesName());
        flags.setSeriesTotal(request.getSeriesTotal() == null || request.isClearSeriesTotal());
        flags.setLanguage(isBlank(request.getLanguage()) || request.isClearLanguage());
        return flags;
    }

    private boolean hasUsableValues(Collection<?> values) {
        return values != null && values.stream().anyMatch(value ->
                value instanceof String text && !text.isBlank());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String getFailureReason(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return message.length() <= 180 ? message : message.substring(0, 177) + "...";
    }

    private static class BookUpdateException extends RuntimeException {
        private final String bookTitle;

        private BookUpdateException(String bookTitle, RuntimeException cause) {
            super(cause);
            this.bookTitle = bookTitle;
        }

        private String getBookTitle() {
            return bookTitle;
        }
    }
}
