package com.adityachandel.booklore.service.metadata;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.model.MetadataUpdateContext;
import com.adityachandel.booklore.model.MetadataUpdateWrapper;
import com.adityachandel.booklore.model.dto.*;
import com.adityachandel.booklore.model.dto.request.FetchMetadataRequest;
import com.adityachandel.booklore.model.dto.request.MetadataRefreshOptions;
import com.adityachandel.booklore.model.dto.request.MetadataRefreshRequest;
import com.adityachandel.booklore.model.dto.settings.AppSettings;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.MetadataFetchJobEntity;
import com.adityachandel.booklore.model.entity.MetadataFetchProposalEntity;
import com.adityachandel.booklore.model.enums.FetchedMetadataProposalStatus;
import com.adityachandel.booklore.model.enums.MetadataFetchTaskStatus;
import com.adityachandel.booklore.model.enums.MetadataProvider;
import com.adityachandel.booklore.model.enums.MetadataReplaceMode;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.repository.MetadataFetchJobRepository;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.metadata.parser.BookParser;
import com.adityachandel.booklore.task.TaskCancellationManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.adityachandel.booklore.model.enums.MetadataProvider.*;

@Slf4j
@AllArgsConstructor
@Service
public class MetadataRefreshService {

    private final LibraryRepository libraryRepository;
    private final MetadataFetchJobRepository metadataFetchJobRepository;
    private final BookMapper bookMapper;
    private final BookMetadataUpdater bookMetadataUpdater;
    private final NotificationService notificationService;
    private final AppSettingService appSettingService;
    private final Map<MetadataProvider, BookParser> parserMap;
    private final ObjectMapper objectMapper;
    private final BookRepository bookRepository;
    private final PlatformTransactionManager transactionManager;
    private final AuthenticationService authenticationService;
    private final TaskCancellationManager cancellationManager;


    public void refreshMetadata(MetadataRefreshRequest request, String jobId) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user != null ? user.getId() : null;
        final Set<Long> bookIds = null;
        final int totalBooks;
        try {
            AppSettings appSettings = appSettingService.getAppSettings();

            final boolean isLibraryRefresh = request.getRefreshType() == MetadataRefreshRequest.RefreshType.LIBRARY;
            final MetadataRefreshOptions requestRefreshOptions = request.getRefreshOptions();

            final boolean useRequestOptions = requestRefreshOptions != null;
            final MetadataRefreshOptions libraryRefreshOptions = !useRequestOptions && isLibraryRefresh ? resolveMetadataRefreshOptions(request.getLibraryId(), appSettings) : null;
            final List<MetadataProvider> fixedProviders = useRequestOptions ?
                    prepareProviders(requestRefreshOptions) :
                    (isLibraryRefresh ? prepareProviders(libraryRefreshOptions) : null);

            final Set<Long> actualBookIds = getBookEntities(request);
            totalBooks = actualBookIds.size();

            MetadataRefreshOptions reviewModeOptions = requestRefreshOptions != null ?
                    requestRefreshOptions :
                    (libraryRefreshOptions != null ? libraryRefreshOptions : appSettings.getDefaultMetadataRefreshOptions());
            boolean isReviewMode = Boolean.TRUE.equals(reviewModeOptions.getReviewBeforeApply());

            MetadataFetchJobEntity task = MetadataFetchJobEntity.builder()
                    .taskId(jobId)
                    .userId(userId)
                    .status(MetadataFetchTaskStatus.IN_PROGRESS)
                    .startedAt(Instant.now())
                    .totalBooksCount(totalBooks)
                    .completedBooks(0)
                    .build();
            metadataFetchJobRepository.save(task);

            TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
            int completedCount = 0;

            for (Long bookId : actualBookIds) {
                if (cancellationManager.isTaskCancelled(jobId)) {
                    log.info("RefreshMetadataTask {} was cancelled, stopping execution", jobId);
                    cancelTask(task);
                    cancellationManager.clearCancellation(jobId);
                    return;
                }

                int finalCompletedCount = completedCount;
                txTemplate.execute(status -> {
                    BookEntity book = bookRepository.findAllWithMetadataByIds(Collections.singleton(bookId))
                            .stream().findFirst()
                            .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
                    try {
                        if (book.getMetadata().areAllFieldsLocked()) {
                            log.info("Skipping locked book: {}", book.getFileName());
                            sendBatchProgressNotification(jobId, finalCompletedCount, totalBooks, "Skipped locked book: " + book.getMetadata().getTitle(), MetadataFetchTaskStatus.IN_PROGRESS, isReviewMode);
                            return null;
                        }

                        MetadataRefreshOptions refreshOptions;
                        List<MetadataProvider> providers;

                        if (useRequestOptions) {
                            refreshOptions = requestRefreshOptions;
                            providers = fixedProviders;
                        } else if (isLibraryRefresh) {
                            refreshOptions = libraryRefreshOptions;
                            providers = fixedProviders;
                        } else {
                            refreshOptions = resolveMetadataRefreshOptions(book.getLibrary().getId(), appSettings);
                            providers = prepareProviders(refreshOptions);
                        }

                        reportProgressIfNeeded(task, jobId, finalCompletedCount, totalBooks, book, isReviewMode);
                        Map<MetadataProvider, BookMetadata> metadataMap = fetchMetadataForBook(providers, book);
                        if (providers.contains(GoodReads)) {
                            try {
                                Thread.sleep(ThreadLocalRandom.current().nextLong(500, 1500));
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                status.setRollbackOnly();
                                return null;
                            }
                        }
                        BookMetadata fetched = buildFetchMetadata(book.getId(), refreshOptions, metadataMap);

                        boolean bookReviewMode = Boolean.TRUE.equals(refreshOptions.getReviewBeforeApply());
                        if (bookReviewMode) {
                            saveProposal(task, book.getId(), fetched);
                        } else {
                            updateBookMetadata(book, fetched, refreshOptions.isRefreshCovers(), refreshOptions.isMergeCategories());
                        }

                        sendBatchProgressNotification(jobId, finalCompletedCount + 1, totalBooks, "Processed: " + book.getMetadata().getTitle(), MetadataFetchTaskStatus.IN_PROGRESS, bookReviewMode);
                    } catch (Exception e) {
                        if (Thread.currentThread().isInterrupted()) {
                            log.info("Processing interrupted for book: {}", book.getFileName());
                            status.setRollbackOnly();
                            return null;
                        }
                        log.error("Metadata update failed for book: {}", book.getFileName(), e);
                        sendBatchProgressNotification(jobId, finalCompletedCount, totalBooks, String.format("Failed to process: %s - %s", book.getMetadata().getTitle(), e.getMessage()), MetadataFetchTaskStatus.ERROR, isReviewMode);
                    }
                    bookRepository.saveAndFlush(book);
                    return null;
                });
                completedCount++;
            }

            completeTask(task, completedCount, totalBooks, isReviewMode);
            cancellationManager.clearCancellation(jobId);
            log.info("Metadata refresh task {} completed successfully", jobId);

        } catch (RuntimeException e) {
            cancellationManager.clearCancellation(jobId);
            if (e.getCause() instanceof InterruptedException) {
                log.info("Metadata refresh task {} cancelled successfully", jobId);
                return;
            }
            log.error("Fatal error during metadata refresh", e);
            int totalBooksForError = 0;
            sendBatchProgressNotification(jobId, 0, totalBooksForError, "Fatal error during metadata refresh: " + e.getMessage(), MetadataFetchTaskStatus.ERROR, false);
            throw e;
        } catch (Exception fatal) {
            cancellationManager.clearCancellation(jobId);
            log.error("Fatal error during metadata refresh", fatal);
            int totalBooksForError = bookIds != null ? bookIds.size() : 0;
            sendBatchProgressNotification(jobId, 0, totalBooksForError, "Fatal error during metadata refresh: " + fatal.getMessage(), MetadataFetchTaskStatus.ERROR, false);
            throw fatal;
        }
    }

    MetadataRefreshOptions resolveMetadataRefreshOptions(Long libraryId, AppSettings appSettings) {
        MetadataRefreshOptions defaultOptions = appSettings.getDefaultMetadataRefreshOptions();
        List<MetadataRefreshOptions> libraryOptions = appSettings.getLibraryMetadataRefreshOptions();

        if (libraryId != null && libraryOptions != null) {
            return libraryOptions.stream()
                    .filter(options -> libraryId.equals(options.getLibraryId()))
                    .findFirst()
                    .orElse(defaultOptions);
        }

        return defaultOptions;
    }

    public Map<MetadataProvider, BookMetadata> fetchMetadataForBook(List<MetadataProvider> providers, Book book) {
        return providers.stream()
                .map(provider -> fetchTopMetadataFromAProvider(provider, book))
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        BookMetadata::getProvider,
                        metadata -> metadata,
                        (existing, replacement) -> existing
                ));
    }

    public Map<MetadataProvider, BookMetadata> fetchMetadataForBook(List<MetadataProvider> providers, BookEntity bookEntity) {
        Book book = bookMapper.toBook(bookEntity);
        return providers.stream()
                .map(provider -> fetchTopMetadataFromAProvider(provider, book))
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        BookMetadata::getProvider,
                        metadata -> metadata,
                        (existing, replacement) -> existing
                ));
    }

    private void reportProgressIfNeeded(MetadataFetchJobEntity task, String taskId, int completedCount, int total, BookEntity book, boolean isReviewMode) {
        if (task == null) return;
        task.setCompletedBooks(completedCount);
        metadataFetchJobRepository.save(task);
        String message = String.format("Processing '%s'", book.getMetadata().getTitle());
        sendBatchProgressNotification(taskId, completedCount, total, message, MetadataFetchTaskStatus.IN_PROGRESS, isReviewMode);
    }

    private void sendBatchProgressNotification(String taskId, int current, int total, String message, MetadataFetchTaskStatus status, boolean isReview) {
        notificationService.sendMessage(Topic.BOOK_METADATA_BATCH_PROGRESS, new MetadataBatchProgressNotification(taskId, current, total, message, status.name(), isReview));
    }

    private void completeTask(MetadataFetchJobEntity task, int completed, int total, boolean isReviewMode) {
        task.setStatus(MetadataFetchTaskStatus.COMPLETED);
        task.setCompletedAt(Instant.now());
        task.setCompletedBooks(completed);
        metadataFetchJobRepository.save(task);
        sendBatchProgressNotification(task.getTaskId(), completed, total, "Batch metadata fetch successfully completed!", MetadataFetchTaskStatus.COMPLETED, isReviewMode);
    }

    private void cancelTask(MetadataFetchJobEntity task) {
        task.setStatus(MetadataFetchTaskStatus.CANCELLED);
        task.setCompletedAt(Instant.now());
        metadataFetchJobRepository.save(task);
        sendBatchProgressNotification(task.getTaskId(), task.getCompletedBooks(), task.getTotalBooksCount(), "Task cancelled by user", MetadataFetchTaskStatus.CANCELLED, false);
    }

    private void saveProposal(MetadataFetchJobEntity job, Long bookId, BookMetadata metadata) throws JsonProcessingException {
        if (job.getProposals() == null) {
            job.setProposals(new ArrayList<>());
        }
        MetadataFetchProposalEntity proposal = MetadataFetchProposalEntity.builder()
                .job(job)
                .bookId(bookId)
                .metadataJson(objectMapper.writeValueAsString(metadata))
                .status(FetchedMetadataProposalStatus.FETCHED)
                .fetchedAt(Instant.now())
                .build();
        job.getProposals().add(proposal);
    }


    public void updateBookMetadata(BookEntity bookEntity, BookMetadata metadata, boolean replaceCover, boolean mergeCategories) {
        if (metadata != null) {

            MetadataUpdateContext context = MetadataUpdateContext.builder()
                    .bookEntity(bookEntity)
                    .metadataUpdateWrapper(MetadataUpdateWrapper.builder()
                            .metadata(metadata)
                            .build())
                    .updateThumbnail(replaceCover)
                    .mergeCategories(mergeCategories)
                    .replaceMode(MetadataReplaceMode.REPLACE_MISSING)
                    .build();

            bookMetadataUpdater.setBookMetadata(context);

            Book book = bookMapper.toBook(bookEntity);
            notificationService.sendMessage(Topic.BOOK_METADATA_UPDATE, book);
        }
    }

    public List<MetadataProvider> prepareProviders(MetadataRefreshOptions refreshOptions) {
        Set<MetadataProvider> allProviders = new HashSet<>(getAllProvidersUsingIndividualFields(refreshOptions));
        return new ArrayList<>(allProviders);
    }

    protected Set<MetadataProvider> getAllProvidersUsingIndividualFields(MetadataRefreshOptions refreshOptions) {
        MetadataRefreshOptions.FieldOptions fieldOptions = refreshOptions.getFieldOptions();
        Set<MetadataProvider> uniqueProviders = new HashSet<>();

        if (fieldOptions != null) {
            addProviderToSet(fieldOptions.getTitle(), uniqueProviders);
            addProviderToSet(fieldOptions.getSubtitle(), uniqueProviders);
            addProviderToSet(fieldOptions.getDescription(), uniqueProviders);
            addProviderToSet(fieldOptions.getAuthors(), uniqueProviders);
            addProviderToSet(fieldOptions.getPublisher(), uniqueProviders);
            addProviderToSet(fieldOptions.getPublishedDate(), uniqueProviders);
            addProviderToSet(fieldOptions.getSeriesName(), uniqueProviders);
            addProviderToSet(fieldOptions.getSeriesNumber(), uniqueProviders);
            addProviderToSet(fieldOptions.getSeriesTotal(), uniqueProviders);
            addProviderToSet(fieldOptions.getIsbn13(), uniqueProviders);
            addProviderToSet(fieldOptions.getIsbn10(), uniqueProviders);
            addProviderToSet(fieldOptions.getLanguage(), uniqueProviders);
            addProviderToSet(fieldOptions.getCategories(), uniqueProviders);
            addProviderToSet(fieldOptions.getCover(), uniqueProviders);
            addProviderToSet(fieldOptions.getPageCount(), uniqueProviders);
            addProviderToSet(fieldOptions.getAsin(), uniqueProviders);
            addProviderToSet(fieldOptions.getGoodreadsId(), uniqueProviders);
            addProviderToSet(fieldOptions.getComicvineId(), uniqueProviders);
            addProviderToSet(fieldOptions.getHardcoverId(), uniqueProviders);
            addProviderToSet(fieldOptions.getGoogleId(), uniqueProviders);
            addProviderToSet(fieldOptions.getAmazonRating(), uniqueProviders);
            addProviderToSet(fieldOptions.getAmazonReviewCount(), uniqueProviders);
            addProviderToSet(fieldOptions.getGoodreadsRating(), uniqueProviders);
            addProviderToSet(fieldOptions.getGoodreadsReviewCount(), uniqueProviders);
            addProviderToSet(fieldOptions.getHardcoverRating(), uniqueProviders);
            addProviderToSet(fieldOptions.getHardcoverReviewCount(), uniqueProviders);
            addProviderToSet(fieldOptions.getMoods(), uniqueProviders);
            addProviderToSet(fieldOptions.getTags(), uniqueProviders);
        }

        return uniqueProviders;
    }

    protected void addProviderToSet(MetadataRefreshOptions.FieldProvider fieldProvider, Set<MetadataProvider> providerSet) {
        if (fieldProvider != null) {
            if (fieldProvider.getP4() != null) providerSet.add(fieldProvider.getP4());
            if (fieldProvider.getP3() != null) providerSet.add(fieldProvider.getP3());
            if (fieldProvider.getP2() != null) providerSet.add(fieldProvider.getP2());
            if (fieldProvider.getP1() != null) providerSet.add(fieldProvider.getP1());
        }
    }

    public BookMetadata fetchTopMetadataFromAProvider(MetadataProvider provider, Book book) {
        return getParser(provider).fetchTopMetadata(book, buildFetchMetadataRequestFromBook(book));
    }

    private BookParser getParser(MetadataProvider provider) {
        BookParser parser = parserMap.get(provider);
        if (parser == null) {
            throw ApiError.METADATA_SOURCE_NOT_IMPLEMENT_OR_DOES_NOT_EXIST.createException();
        }
        return parser;
    }

    private FetchMetadataRequest buildFetchMetadataRequestFromBook(Book book) {
        BookMetadata metadata = book.getMetadata();
        return FetchMetadataRequest.builder()
                .isbn(metadata.getIsbn10())
                .asin(metadata.getAsin())
                .author(metadata.getAuthors() != null ? String.join(", ", metadata.getAuthors()) : null)
                .title(metadata.getTitle())
                .bookId(book.getId())
                .build();
    }

    public BookMetadata buildFetchMetadata(Long bookId, MetadataRefreshOptions refreshOptions, Map<MetadataProvider, BookMetadata> metadataMap) {
        BookMetadata metadata = BookMetadata.builder().bookId(bookId).build();
        MetadataRefreshOptions.FieldOptions fieldOptions = refreshOptions.getFieldOptions();
        MetadataRefreshOptions.EnabledFields enabledFields = refreshOptions.getEnabledFields();

        if (enabledFields.isTitle()) {
            metadata.setTitle(resolveFieldAsString(metadataMap, fieldOptions.getTitle(), BookMetadata::getTitle));
        }
        if (enabledFields.isSubtitle()) {
            metadata.setSubtitle(resolveFieldAsString(metadataMap, fieldOptions.getSubtitle(), BookMetadata::getSubtitle));
        }
        if (enabledFields.isDescription()) {
            metadata.setDescription(resolveFieldAsString(metadataMap, fieldOptions.getDescription(), BookMetadata::getDescription));
        }
        if (enabledFields.isAuthors()) {
            metadata.setAuthors(resolveFieldAsList(metadataMap, fieldOptions.getAuthors(), BookMetadata::getAuthors));
        }
        if (enabledFields.isPublisher()) {
            metadata.setPublisher(resolveFieldAsString(metadataMap, fieldOptions.getPublisher(), BookMetadata::getPublisher));
        }
        if (enabledFields.isPublishedDate()) {
            metadata.setPublishedDate(resolveField(metadataMap, fieldOptions.getPublishedDate(), BookMetadata::getPublishedDate));
        }
        if (enabledFields.isSeriesName()) {
            metadata.setSeriesName(resolveFieldAsString(metadataMap, fieldOptions.getSeriesName(), BookMetadata::getSeriesName));
        }
        if (enabledFields.isSeriesNumber()) {
            metadata.setSeriesNumber(resolveField(metadataMap, fieldOptions.getSeriesNumber(), BookMetadata::getSeriesNumber));
        }
        if (enabledFields.isSeriesTotal()) {
            metadata.setSeriesTotal(resolveFieldAsInteger(metadataMap, fieldOptions.getSeriesTotal(), BookMetadata::getSeriesTotal));
        }
        if (enabledFields.isIsbn13()) {
            metadata.setIsbn13(resolveFieldAsString(metadataMap, fieldOptions.getIsbn13(), BookMetadata::getIsbn13));
        }
        if (enabledFields.isIsbn10()) {
            metadata.setIsbn10(resolveFieldAsString(metadataMap, fieldOptions.getIsbn10(), BookMetadata::getIsbn10));
        }
        if (enabledFields.isLanguage()) {
            metadata.setLanguage(resolveFieldAsString(metadataMap, fieldOptions.getLanguage(), BookMetadata::getLanguage));
        }
        if (enabledFields.isPageCount()) {
            metadata.setPageCount(resolveFieldAsInteger(metadataMap, fieldOptions.getPageCount(), BookMetadata::getPageCount));
        }
        if (enabledFields.isCover()) {
            metadata.setThumbnailUrl(resolveFieldAsString(metadataMap, fieldOptions.getCover(), BookMetadata::getThumbnailUrl));
        }
        if (enabledFields.isAmazonRating()) {
            if (metadataMap.containsKey(Amazon)) {
                metadata.setAmazonRating(metadataMap.get(Amazon).getAmazonRating());
            }
        }
        if (enabledFields.isAmazonReviewCount()) {
            if (metadataMap.containsKey(Amazon)) {
                metadata.setAmazonReviewCount(metadataMap.get(Amazon).getAmazonReviewCount());
            }
        }
        if (enabledFields.isGoodreadsRating()) {
            if (metadataMap.containsKey(GoodReads)) {
                metadata.setGoodreadsRating(metadataMap.get(GoodReads).getGoodreadsRating());
            }
        }
        if (enabledFields.isGoodreadsReviewCount()) {
            if (metadataMap.containsKey(GoodReads)) {
                metadata.setGoodreadsReviewCount(metadataMap.get(GoodReads).getGoodreadsReviewCount());
            }
        }
        if (enabledFields.isHardcoverRating()) {
            if (metadataMap.containsKey(Hardcover)) {
                metadata.setHardcoverRating(metadataMap.get(Hardcover).getHardcoverRating());
            }
        }
        if (enabledFields.isHardcoverReviewCount()) {
            if (metadataMap.containsKey(Hardcover)) {
                metadata.setHardcoverReviewCount(metadataMap.get(Hardcover).getHardcoverReviewCount());
            }
        }
        if (enabledFields.isAsin()) {
            if (metadataMap.containsKey(Amazon)) {
                metadata.setAsin(metadataMap.get(Amazon).getAsin());
            }
        }
        if (enabledFields.isGoodreadsId()) {
            if (metadataMap.containsKey(GoodReads)) {
                metadata.setGoodreadsId(metadataMap.get(GoodReads).getGoodreadsId());
            }
        }
        if (enabledFields.isHardcoverId()) {
            if (metadataMap.containsKey(Hardcover)) {
                metadata.setHardcoverId(metadataMap.get(Hardcover).getHardcoverId());
            }
        }
        if (enabledFields.isGoogleId()) {
            if (metadataMap.containsKey(Google)) {
                metadata.setGoogleId(metadataMap.get(Google).getGoogleId());
            }
        }
        if (enabledFields.isComicvineId()) {
            if (metadataMap.containsKey(Comicvine)) {
                metadata.setComicvineId(metadataMap.get(Comicvine).getComicvineId());
            }
        }
        if (enabledFields.isMoods()) {
            if (metadataMap.containsKey(Hardcover)) {
                metadata.setMoods(metadataMap.get(Hardcover).getMoods());
            }
        }
        if (enabledFields.isTags()) {
            if (metadataMap.containsKey(Hardcover)) {
                metadata.setTags(metadataMap.get(Hardcover).getTags());
            }
        }
        if (enabledFields.isCategories()) {
            if (refreshOptions.isMergeCategories()) {
                metadata.setCategories(getAllCategories(metadataMap, fieldOptions.getCategories(), BookMetadata::getCategories));
            } else {
                metadata.setCategories(resolveFieldAsList(metadataMap, fieldOptions.getCategories(), BookMetadata::getCategories));
            }
        }

        List<BookReview> allReviews = metadataMap.values().stream()
                .filter(Objects::nonNull)
                .flatMap(md -> Optional.ofNullable(md.getBookReviews()).stream().flatMap(Collection::stream))
                .collect(Collectors.toList());
        if (!allReviews.isEmpty()) {
            metadata.setBookReviews(allReviews);
        }

        return metadata;
    }

    protected <T> T resolveField(Map<MetadataProvider, BookMetadata> metadataMap, MetadataRefreshOptions.FieldProvider fieldProvider, Function<BookMetadata, T> extractor) {
        return resolveFieldWithProviders(metadataMap, fieldProvider, extractor, (value) -> value != null);
    }

    protected Integer resolveFieldAsInteger(Map<MetadataProvider, BookMetadata> metadataMap, MetadataRefreshOptions.FieldProvider fieldProvider, Function<BookMetadata, Integer> fieldValueExtractor) {
        return resolveFieldWithProviders(metadataMap, fieldProvider, fieldValueExtractor, (value) -> value != null);
    }

    protected String resolveFieldAsString(Map<MetadataProvider, BookMetadata> metadataMap, MetadataRefreshOptions.FieldProvider fieldProvider, FieldValueExtractor fieldValueExtractor) {
        return resolveFieldWithProviders(metadataMap, fieldProvider, fieldValueExtractor::extract, (value) -> value != null);
    }

    protected Set<String> resolveFieldAsList(Map<MetadataProvider, BookMetadata> metadataMap, MetadataRefreshOptions.FieldProvider fieldProvider, FieldValueExtractorList fieldValueExtractor) {
        return resolveFieldWithProviders(metadataMap, fieldProvider, fieldValueExtractor::extract, (value) -> value != null && !value.isEmpty());
    }

    private <T> T resolveFieldWithProviders(Map<MetadataProvider, BookMetadata> metadataMap, MetadataRefreshOptions.FieldProvider fieldProvider, Function<BookMetadata, T> extractor, Predicate<T> isValidValue) {
        if (fieldProvider == null) {
            return null;
        }
        MetadataProvider[] providers = {
                fieldProvider.getP4(),
                fieldProvider.getP3(),
                fieldProvider.getP2(),
                fieldProvider.getP1()
        };
        for (MetadataProvider provider : providers) {
            if (provider != null && metadataMap.containsKey(provider)) {
                T value = extractor.apply(metadataMap.get(provider));
                if (isValidValue.test(value)) {
                    return value;
                }
            }
        }
        return null;
    }

    Set<String> getAllCategories(Map<MetadataProvider, BookMetadata> metadataMap, MetadataRefreshOptions.FieldProvider fieldProvider, FieldValueExtractorList fieldValueExtractor) {
        Set<String> uniqueCategories = new HashSet<>();
        if (fieldProvider == null) {
            return uniqueCategories;
        }

        MetadataProvider[] providers = {
                fieldProvider.getP4(),
                fieldProvider.getP3(),
                fieldProvider.getP2(),
                fieldProvider.getP1()
        };

        for (MetadataProvider provider : providers) {
            if (provider != null && metadataMap.containsKey(provider)) {
                Set<String> extracted = fieldValueExtractor.extract(metadataMap.get(provider));
                if (extracted != null) {
                    uniqueCategories.addAll(extracted);
                }
            }
        }

        return uniqueCategories;
    }

    protected Set<Long> getBookEntities(MetadataRefreshRequest request) {
        MetadataRefreshRequest.RefreshType refreshType = request.getRefreshType();
        if (refreshType != MetadataRefreshRequest.RefreshType.LIBRARY && refreshType != MetadataRefreshRequest.RefreshType.BOOKS) {
            throw ApiError.INVALID_REFRESH_TYPE.createException();
        }
        return switch (refreshType) {
            case LIBRARY -> {
                LibraryEntity libraryEntity = libraryRepository.findById(request.getLibraryId()).orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(request.getLibraryId()));
                yield bookRepository.findBookIdsByLibraryId(libraryEntity.getId());
            }
            case BOOKS -> request.getBookIds();
        };
    }
}

