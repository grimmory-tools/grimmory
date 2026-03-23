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
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
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
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.booklore.model.dto.request.IsbnLookupRequest;

import java.io.File;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@AllArgsConstructor
public class BookMetadataService {

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


    @Transactional(readOnly = true)
    public Flux<BookMetadata> getProspectiveMetadataListForBookId(long bookId, FetchMetadataRequest request) {
        BookEntity bookEntity = bookRepository.findByIdWithBookFiles(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        Book book = bookMapper.toBook(bookEntity);

        return Flux.fromIterable(request.getProviders())
                .flatMap(provider ->
                    Mono.fromCallable(() -> fetchMetadataListFromAProvider(provider, book, request))
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMapMany(Flux::fromIterable)
                            .onErrorResume(e -> {
                                log.error("Error fetching metadata from provider: {}", provider, e);
                                return Flux.empty();
                            })
                );
    }

    public List<BookMetadata> fetchMetadataListFromAProvider(MetadataProvider provider, Book book, FetchMetadataRequest request) {
        return getParser(provider).fetchMetadata(book, request);
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
        return books.stream().map(b -> bookMetadataMapper.toBookMetadata(b.getMetadata(), false)).collect(Collectors.toList());
    }

    public BookMetadata getComicInfoMetadata(long bookId) {
        log.info("Extracting ComicInfo metadata for book ID: {}", bookId);
        BookEntity bookEntity = bookRepository.findByIdWithBookFiles(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        var primaryFile = bookEntity.getPrimaryBookFile();
        if (primaryFile == null || primaryFile.getBookType() != BookFileType.CBX) {
            log.info("Unsupported operation for book ID {} - no file or not CBX type", bookId);
            return null;
        }
        return cbxMetadataExtractor.extractMetadata(new File(FileUtils.getBookFullPath(bookEntity)));
    }

    public BookMetadata getFileMetadata(long bookId) {
        log.info("Extracting file metadata for book ID: {}", bookId);
        BookEntity bookEntity = bookRepository.findById(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        var primaryFile = bookEntity.getPrimaryBookFile();
        if (primaryFile == null) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("Book has no file to extract metadata from");
        }
        return metadataExtractorFactory.extractMetadata(primaryFile.getBookType(), new File(FileUtils.getBookFullPath(bookEntity)));
    }

    @Transactional
    public BookMetadata updateMetadata(long bookId, MetadataUpdateWrapper wrapper, boolean mergeCategories) {
        BookEntity bookEntity = bookRepository.findByIdFull(bookId)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(wrapper)
                .updateThumbnail(true)
                .mergeCategories(mergeCategories)
                .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                .mergeMoods(false)
                .mergeTags(false)
                .build();

        bookMetadataUpdater.setBookMetadata(context);
        bookRepository.save(bookEntity);
        auditService.log(AuditAction.METADATA_UPDATED, "Book", bookId, "Updated metadata for book: " + bookEntity.getMetadata().getTitle());
        return bookMetadataMapper.toBookMetadata(bookEntity.getMetadata(), true);
    }

    @Transactional
    public void bulkUpdateMetadata(BulkMetadataUpdateRequest request, boolean mergeCategories, boolean mergeMoods, boolean mergeTags) {
        MetadataClearFlags clearFlags = metadataClearFlagsMapper.toClearFlags(request);

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

        for (Long bookId : request.getBookIds()) {
            try {
                processSingleBookUpdate(bookId, bookMetadata, clearFlags, mergeCategories, mergeMoods, mergeTags);
            } catch (Exception e) {
                log.error("Failed to update metadata for book ID {}", bookId, e);
            }
        }
    }

    private void processSingleBookUpdate(Long bookId, BookMetadata bookMetadata, MetadataClearFlags clearFlags, boolean mergeCategories, boolean mergeMoods, boolean mergeTags) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactionTemplate.execute(status -> {
            BookEntity book = bookRepository.findByIdFull(bookId).orElse(null);
            if (book == null) {
                log.warn("Book not found for metadata update: {}", bookId);
                return null;
            }

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
            notificationService.sendMessage(Topic.BOOK_UPDATE, bookMapper.toBookWithDescription(book, true));
            return null;
        });
    }
}
