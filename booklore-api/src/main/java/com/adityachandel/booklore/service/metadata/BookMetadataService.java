package com.adityachandel.booklore.service.metadata;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.mapper.BookMetadataMapper;
import com.adityachandel.booklore.mapper.MetadataClearFlagsMapper;
import com.adityachandel.booklore.model.MetadataClearFlags;
import com.adityachandel.booklore.model.MetadataUpdateWrapper;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.request.*;
import com.adityachandel.booklore.model.dto.settings.AppSettings;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.enums.Lock;
import com.adityachandel.booklore.model.enums.MetadataProvider;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.BookMetadataRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.service.BookQueryService;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.fileprocessor.CbxProcessor;
import com.adityachandel.booklore.service.fileprocessor.EpubProcessor;
import com.adityachandel.booklore.service.fileprocessor.PdfProcessor;
import com.adityachandel.booklore.service.metadata.backuprestore.MetadataBackupRestore;
import com.adityachandel.booklore.service.metadata.backuprestore.MetadataBackupRestoreFactory;
import com.adityachandel.booklore.service.metadata.parser.BookParser;
import com.adityachandel.booklore.service.metadata.writer.MetadataWriterFactory;
import com.adityachandel.booklore.util.FileService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static com.adityachandel.booklore.model.enums.MetadataProvider.*;
import static com.adityachandel.booklore.model.websocket.LogNotification.createLogNotification;

@Slf4j
@Service
@AllArgsConstructor
public class BookMetadataService {

    private final BookRepository bookRepository;
    private final LibraryRepository libraryRepository;
    private final BookMapper bookMapper;
    private final BookMetadataMapper bookMetadataMapper;
    private final BookMetadataUpdater bookMetadataUpdater;
    private final NotificationService notificationService;
    private final AppSettingService appSettingService;
    private final BookMetadataRepository bookMetadataRepository;
    private final FileService fileService;
    private final PdfProcessor pdfProcessor;
    private final EpubProcessor epubProcessor;
    private final CbxProcessor cbxProcessor;
    private final BookQueryService bookQueryService;
    private final Map<MetadataProvider, BookParser> parserMap;
    private final MetadataBackupRestoreFactory metadataBackupRestoreFactory;
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

    public BookMetadata fetchTopMetadataFromAProvider(MetadataProvider provider, Book book) {
        return getParser(provider).fetchTopMetadata(book, buildFetchMetadataRequestFromBook(book));
    }

    @Transactional
    public void refreshMetadata(MetadataRefreshRequest request) {
        log.info("Refresh Metadata task started!");

        if (request.getQuick() != null && request.getQuick()) {
            AppSettings appSettings = appSettingService.getAppSettings();
            request.setRefreshOptions(appSettings.getMetadataRefreshOptions());
        }

        List<MetadataProvider> providers = prepareProviders(request);
        List<BookEntity> books = getBookEntities(request);
        for (BookEntity bookEntity : books) {
            try {
                if (bookEntity.getMetadata().areAllFieldsLocked()) {
                    log.info("Skipping metadata refresh for fully locked book: {}", bookEntity.getFileName());
                    notificationService.sendMessage(Topic.LOG, createLogNotification("Book '" + bookEntity.getMetadata().getTitle() + "' is locked. Please unlock it before updating metadata."));
                    continue;
                }
                Map<MetadataProvider, BookMetadata> metadataMap = fetchMetadataForBook(providers, bookEntity);
                if (providers.contains(GoodReads)) {
                    Thread.sleep(ThreadLocalRandom.current().nextLong(500, 1500));
                }
                BookMetadata fetchedBookMetadata = buildFetchMetadata(bookEntity.getId(), request, metadataMap);
                updateBookMetadata(bookEntity, fetchedBookMetadata, request.getRefreshOptions().isRefreshCovers(), request.getRefreshOptions().isMergeCategories());
            } catch (Exception e) {
                log.error("Error while updating book metadata, book: {}", bookEntity.getFileName(), e);
            }
        }
        log.info("Refresh Metadata task completed!");
    }

    @Transactional
    protected void updateBookMetadata(BookEntity bookEntity, BookMetadata metadata, boolean replaceCover, boolean mergeCategories) {
        if (metadata != null) {
            MetadataUpdateWrapper metadataUpdateWrapper = MetadataUpdateWrapper.builder()
                    .metadata(metadata)
                    .build();
            bookMetadataUpdater.setBookMetadata(bookEntity, metadataUpdateWrapper, replaceCover, mergeCategories);
            Book book = bookMapper.toBook(bookEntity);
            notificationService.sendMessage(Topic.BOOK_METADATA_UPDATE, book);
            notificationService.sendMessage(Topic.LOG, createLogNotification("Book metadata updated: " + book.getMetadata().getTitle()));
        }
    }

    @Transactional
    protected List<MetadataProvider> prepareProviders(MetadataRefreshRequest request) {
        Set<MetadataProvider> allProviders = new HashSet<>(getAllProvidersUsingIndividualFields(request));
        return new ArrayList<>(allProviders);
    }

    @Transactional
    protected Map<MetadataProvider, BookMetadata> fetchMetadataForBook(List<MetadataProvider> providers, BookEntity bookEntity) {
        return providers.stream()
                .map(provider -> CompletableFuture.supplyAsync(() -> fetchTopMetadataFromAProvider(provider, bookMapper.toBook(bookEntity)))
                        .exceptionally(e -> {
                            log.error("Error fetching metadata from provider: {}", provider, e);
                            return null;
                        }))
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        BookMetadata::getProvider,
                        metadata -> metadata,
                        (existing, replacement) -> existing
                ));
    }

    @Transactional
    protected BookMetadata buildFetchMetadata(Long bookId, MetadataRefreshRequest request, Map<MetadataProvider, BookMetadata> metadataMap) {
        BookMetadata metadata = BookMetadata.builder().bookId(bookId).build();
        MetadataRefreshOptions.FieldOptions fieldOptions = request.getRefreshOptions().getFieldOptions();

        metadata.setTitle(resolveFieldAsString(metadataMap, fieldOptions.getTitle(), BookMetadata::getTitle));
        metadata.setDescription(resolveFieldAsString(metadataMap, fieldOptions.getDescription(), BookMetadata::getDescription));
        metadata.setAuthors(resolveFieldAsList(metadataMap, fieldOptions.getAuthors(), BookMetadata::getAuthors));

        if (metadataMap.containsKey(GoodReads)) {
            metadata.setGoodreadsId(metadataMap.get(GoodReads).getGoodreadsId());
        }
        if (metadataMap.containsKey(Hardcover)) {
            metadata.setHardcoverId(metadataMap.get(Hardcover).getHardcoverId());
        }
        if (metadataMap.containsKey(Google)) {
            metadata.setGoogleId(metadataMap.get(Google).getGoogleId());
        }

        if (request.getRefreshOptions().isMergeCategories()) {
            metadata.setCategories(getAllCategories(metadataMap, fieldOptions.getCategories(), BookMetadata::getCategories));
        } else {
            metadata.setCategories(resolveFieldAsList(metadataMap, fieldOptions.getCategories(), BookMetadata::getCategories));
        }
        metadata.setThumbnailUrl(resolveFieldAsString(metadataMap, fieldOptions.getCover(), BookMetadata::getThumbnailUrl));

        if (request.getRefreshOptions().getAllP4() != null) {
            setOtherUnspecifiedMetadata(metadataMap, metadata, request.getRefreshOptions().getAllP4());
        }
        if (request.getRefreshOptions().getAllP3() != null) {
            setOtherUnspecifiedMetadata(metadataMap, metadata, request.getRefreshOptions().getAllP3());
        }
        if (request.getRefreshOptions().getAllP2() != null) {
            setOtherUnspecifiedMetadata(metadataMap, metadata, request.getRefreshOptions().getAllP2());
        }
        if (request.getRefreshOptions().getAllP1() != null) {
            setOtherUnspecifiedMetadata(metadataMap, metadata, request.getRefreshOptions().getAllP1());
        }

        return metadata;
    }

    @Transactional
    protected void setOtherUnspecifiedMetadata(Map<MetadataProvider, BookMetadata> metadataMap, BookMetadata metadataCombined, MetadataProvider provider) {
        if (metadataMap.containsKey(provider)) {
            BookMetadata metadata = metadataMap.get(provider);
            metadataCombined.setSubtitle(metadata.getSubtitle() != null ? metadata.getSubtitle() : metadata.getTitle());
            metadataCombined.setPublisher(metadata.getPublisher() != null ? metadata.getPublisher() : metadataCombined.getPublisher());
            metadataCombined.setPublishedDate(metadata.getPublishedDate() != null ? metadata.getPublishedDate() : metadataCombined.getPublishedDate());
            metadataCombined.setIsbn10(metadata.getIsbn10() != null ? metadata.getIsbn10() : metadataCombined.getIsbn10());
            metadataCombined.setIsbn13(metadata.getIsbn13() != null ? metadata.getIsbn13() : metadataCombined.getIsbn13());
            metadataCombined.setAsin(metadata.getAsin() != null ? metadata.getAsin() : metadataCombined.getAsin());
            metadataCombined.setPageCount(metadata.getPageCount() != null ? metadata.getPageCount() : metadataCombined.getPageCount());
            metadataCombined.setLanguage(metadata.getLanguage() != null ? metadata.getLanguage() : metadataCombined.getLanguage());
            metadataCombined.setGoodreadsRating(metadata.getGoodreadsRating() != null ? metadata.getGoodreadsRating() : metadataCombined.getGoodreadsRating());
            metadataCombined.setGoodreadsReviewCount(metadata.getGoodreadsReviewCount() != null ? metadata.getGoodreadsReviewCount() : metadataCombined.getGoodreadsReviewCount());
            metadataCombined.setAmazonRating(metadata.getAmazonRating() != null ? metadata.getAmazonRating() : metadataCombined.getAmazonRating());
            metadataCombined.setAmazonReviewCount(metadata.getAmazonReviewCount() != null ? metadata.getAmazonReviewCount() : metadataCombined.getAmazonReviewCount());
            metadataCombined.setHardcoverRating(metadata.getHardcoverRating() != null ? metadata.getHardcoverRating() : metadataCombined.getHardcoverRating());
            metadataCombined.setHardcoverReviewCount(metadata.getHardcoverReviewCount() != null ? metadata.getHardcoverReviewCount() : metadataCombined.getHardcoverReviewCount());
            metadataCombined.setPersonalRating(metadata.getPersonalRating() != null ? metadata.getPersonalRating() : metadataCombined.getPersonalRating());
            metadataCombined.setSeriesName(metadata.getSeriesName() != null ? metadata.getSeriesName() : metadataCombined.getSeriesName());
            metadataCombined.setSeriesNumber(metadata.getSeriesNumber() != null ? metadata.getSeriesNumber() : metadataCombined.getSeriesNumber());
            metadataCombined.setSeriesTotal(metadata.getSeriesTotal() != null ? metadata.getSeriesTotal() : metadataCombined.getSeriesTotal());
        }
    }

    @Transactional
    protected String resolveFieldAsString(Map<MetadataProvider, BookMetadata> metadataMap, MetadataRefreshOptions.FieldProvider fieldProvider, FieldValueExtractor fieldValueExtractor) {
        String value = null;
        if (fieldProvider.getP4() != null && metadataMap.containsKey(fieldProvider.getP4())) {
            String newValue = fieldValueExtractor.extract(metadataMap.get(fieldProvider.getP4()));
            if (newValue != null) {
                value = newValue;
            }
        }
        if (fieldProvider.getP3() != null && metadataMap.containsKey(fieldProvider.getP3())) {
            String newValue = fieldValueExtractor.extract(metadataMap.get(fieldProvider.getP3()));
            if (newValue != null) {
                value = newValue;
            }
        }
        if (fieldProvider.getP2() != null && metadataMap.containsKey(fieldProvider.getP2())) {
            String newValue = fieldValueExtractor.extract(metadataMap.get(fieldProvider.getP2()));
            if (newValue != null) {
                value = newValue;
            }
        }
        if (fieldProvider.getP1() != null && metadataMap.containsKey(fieldProvider.getP1())) {
            String newValue = fieldValueExtractor.extract(metadataMap.get(fieldProvider.getP1()));
            if (newValue != null) {
                value = newValue;
            }
        }
        return value;
    }

    Set<String> getAllCategories(Map<MetadataProvider, BookMetadata> metadataMap, MetadataRefreshOptions.FieldProvider fieldProvider, FieldValueExtractorList fieldValueExtractor) {
        Set<String> uniqueCategories = new HashSet<>();
        if (fieldProvider.getP4() != null && metadataMap.containsKey(fieldProvider.getP4())) {
            Set<String> extracted = fieldValueExtractor.extract(metadataMap.get(fieldProvider.getP4()));
            if (extracted != null) {
                uniqueCategories.addAll(extracted);
            }
        }
        if (fieldProvider.getP3() != null && metadataMap.containsKey(fieldProvider.getP3())) {
            Set<String> extracted = fieldValueExtractor.extract(metadataMap.get(fieldProvider.getP3()));
            if (extracted != null) {
                uniqueCategories.addAll(extracted);
            }
        }
        if (fieldProvider.getP2() != null && metadataMap.containsKey(fieldProvider.getP2())) {
            Set<String> extracted = fieldValueExtractor.extract(metadataMap.get(fieldProvider.getP2()));
            if (extracted != null) {
                uniqueCategories.addAll(extracted);
            }
        }
        if (fieldProvider.getP1() != null && metadataMap.containsKey(fieldProvider.getP1())) {
            Set<String> extracted = fieldValueExtractor.extract(metadataMap.get(fieldProvider.getP1()));
            if (extracted != null) {
                uniqueCategories.addAll(extracted);
            }
        }
        return new HashSet<>(uniqueCategories);
    }

    @Transactional
    protected Set<String> resolveFieldAsList(Map<MetadataProvider, BookMetadata> metadataMap, MetadataRefreshOptions.FieldProvider fieldProvider, FieldValueExtractorList fieldValueExtractor) {
        Set<String> values = new HashSet<>();
        if (fieldProvider.getP4() != null && metadataMap.containsKey(fieldProvider.getP4())) {
            Set<String> newValues = fieldValueExtractor.extract(metadataMap.get(fieldProvider.getP4()));
            if (newValues != null && !newValues.isEmpty()) {
                values = newValues;
            }
        }
        if (fieldProvider.getP3() != null && metadataMap.containsKey(fieldProvider.getP3())) {
            Set<String> newValues = fieldValueExtractor.extract(metadataMap.get(fieldProvider.getP3()));
            if (newValues != null && !newValues.isEmpty()) {
                values = newValues;
            }
        }
        if (values.isEmpty() && fieldProvider.getP2() != null && metadataMap.containsKey(fieldProvider.getP2())) {
            Set<String> newValues = fieldValueExtractor.extract(metadataMap.get(fieldProvider.getP2()));
            if (newValues != null && !newValues.isEmpty()) {
                values = newValues;
            }
        }
        if (values.isEmpty() && fieldProvider.getP1() != null && metadataMap.containsKey(fieldProvider.getP1())) {
            Set<String> newValues = fieldValueExtractor.extract(metadataMap.get(fieldProvider.getP1()));
            if (newValues != null && !newValues.isEmpty()) {
                values = newValues;
            }
        }
        return values;
    }

    @Transactional
    protected List<BookEntity> getBookEntities(MetadataRefreshRequest request) {
        MetadataRefreshRequest.RefreshType refreshType = request.getRefreshType();
        if (refreshType != MetadataRefreshRequest.RefreshType.LIBRARY && refreshType != MetadataRefreshRequest.RefreshType.BOOKS) {
            throw ApiError.INVALID_REFRESH_TYPE.createException();
        }
        List<BookEntity> books = switch (refreshType) {
            case LIBRARY -> {
                LibraryEntity libraryEntity = libraryRepository.findById(request.getLibraryId()).orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(request.getLibraryId()));
                yield libraryEntity.getBookEntities();
            }
            case BOOKS -> bookQueryService.findAllWithMetadataByIds(request.getBookIds());
        };
        books.sort(Comparator.comparing(BookEntity::getFileName, Comparator.nullsLast(String::compareTo)));
        return books;
    }

    @Transactional
    protected Set<MetadataProvider> getAllProvidersUsingIndividualFields(MetadataRefreshRequest request) {
        MetadataRefreshOptions.FieldOptions fieldOptions = request.getRefreshOptions().getFieldOptions();
        Set<MetadataProvider> uniqueProviders = new HashSet<>();

        if (fieldOptions != null) {
            addProviderToSet(fieldOptions.getTitle(), uniqueProviders);
            addProviderToSet(fieldOptions.getDescription(), uniqueProviders);
            addProviderToSet(fieldOptions.getAuthors(), uniqueProviders);
            addProviderToSet(fieldOptions.getCategories(), uniqueProviders);
            addProviderToSet(fieldOptions.getCover(), uniqueProviders);
        }

        return uniqueProviders;
    }

    @Transactional
    protected void addProviderToSet(MetadataRefreshOptions.FieldProvider fieldProvider, Set<MetadataProvider> providerSet) {
        if (fieldProvider != null) {
            if (fieldProvider.getP4() != null) {
                providerSet.add(fieldProvider.getP4());
            }
            if (fieldProvider.getP3() != null) {
                providerSet.add(fieldProvider.getP3());
            }
            if (fieldProvider.getP2() != null) {
                providerSet.add(fieldProvider.getP2());
            }
            if (fieldProvider.getP1() != null) {
                providerSet.add(fieldProvider.getP1());
            }
        }
    }

    private FetchMetadataRequest buildFetchMetadataRequestFromBook(Book book) {
        return FetchMetadataRequest.builder()
                .isbn(book.getMetadata().getIsbn10())
                .asin(book.getMetadata().getAsin())
                .author(String.join(", ", book.getMetadata().getAuthors()))
                .title(book.getMetadata().getTitle())
                .bookId(book.getId())
                .build();
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
    public BookMetadata handleCoverUpload(Long bookId, MultipartFile file) {
        fileService.createThumbnailFromFile(bookId, file);
        BookEntity bookEntity = bookRepository.findById(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        bookEntity.getMetadata().setCoverUpdatedOn(Instant.now());
        boolean saveToOriginalFile = appSettingService.getAppSettings().getMetadataPersistenceSettings().isSaveToOriginalFile();
        if (saveToOriginalFile) {
            metadataWriterFactory.getWriter(bookEntity.getBookType())
                    .ifPresent(writer -> writer.replaceCoverImageFromUpload(bookEntity, file));
        }
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

    public void regenerateCovers() {
        Thread.startVirtualThread(() -> {
            List<BookEntity> books = bookQueryService.getAllFullBookEntities().stream()
                    .filter(book -> book.getMetadata().getCoverLocked() == null || !book.getMetadata().getCoverLocked())
                    .toList();
            int total = books.size();
            notificationService.sendMessage(Topic.LOG, createLogNotification("Started regenerating covers for " + total + " books"));
            int[] current = {1};
            for (BookEntity book : books) {
                try {
                    String progress = "(" + current[0] + "/" + total + ") ";
                    regenerateCoverForBook(book, progress);
                } catch (Exception e) {
                    log.error("Failed to regenerate cover for book ID {}: {}", book.getId(), e.getMessage());
                }
                current[0]++;
            }
            notificationService.sendMessage(Topic.LOG, createLogNotification("Finished regenerating covers"));
        });
    }

    private void regenerateCoverForBook(BookEntity book, String progress) {
        String title = book.getMetadata().getTitle();
        String message = progress + "Regenerating cover for: " + title;
        notificationService.sendMessage(Topic.LOG, createLogNotification(message));
        switch (book.getBookType()) {
            case PDF -> pdfProcessor.generateCover(book);
            case EPUB -> epubProcessor.generateCover(book);
            case CBX -> cbxProcessor.generateCover(book);
            default -> throw ApiError.UNSUPPORTED_BOOK_TYPE.createException(book.getBookType());
        }
        log.info("{}Successfully regenerated cover for book ID {} ({})", progress, book.getId(), title);
    }

    public BookMetadata restoreMetadataFromBackup(Long bookId) throws IOException {
        BookEntity bookEntity = bookRepository.findById(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        metadataBackupRestoreFactory.getService(bookEntity.getBookType()).restoreEmbeddedMetadata(bookEntity);
        bookRepository.saveAndFlush(bookEntity);
        return bookMetadataMapper.toBookMetadata(bookEntity.getMetadata(), true);
    }

    public BookMetadata getBackedUpMetadata(Long bookId) {
        BookEntity bookEntity = bookRepository.findById(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        return metadataBackupRestoreFactory.getService(bookEntity.getBookType()).getBackedUpMetadata(bookId);
    }

    @Transactional
    public List<BookMetadata> bulkUpdateMetadata(BulkMetadataUpdateRequest request, boolean mergeCategories) {
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
                    .build();

            MetadataUpdateWrapper metadataUpdateWrapper = MetadataUpdateWrapper.builder()
                    .metadata(bookMetadata)
                    .clearFlags(clearFlags)
                    .build();

            bookMetadataUpdater.setBookMetadata(book, metadataUpdateWrapper, false, mergeCategories);
        }

        return books.stream()
                .map(BookEntity::getMetadata)
                .map(m -> bookMetadataMapper.toBookMetadata(m, false))
                .toList();
    }

    public Resource getBackupCoverForBook(long bookId) {
        BookEntity bookEntity = bookRepository.findById(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        MetadataBackupRestore backupRestore = metadataBackupRestoreFactory.getService(bookEntity.getBookType());
        try {
            return backupRestore.getBackupCover(bookId);
        } catch (UnsupportedOperationException e) {
            log.info("Cover backup not supported for file type: {}", bookEntity.getBookType());
            return null;
        }
    }
}