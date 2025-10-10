package com.adityachandel.booklore.service.metadata;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.mapper.BookMetadataMapper;
import com.adityachandel.booklore.mapper.MetadataClearFlagsMapper;
import com.adityachandel.booklore.model.MetadataClearFlags;
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
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.BookMetadataRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.BookQueryService;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
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
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static com.adityachandel.booklore.model.websocket.LogNotification.createLogNotification;

@Slf4j
@Service
@AllArgsConstructor
public class BookMetadataService {

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
                    .ifPresent(writer -> writerAction.accept(writer, bookEntity));
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
        SecurityContextVirtualThread.runWithSecurityContext(() -> {
            try {
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
            } catch (Exception e) {
                log.error("Error during cover regeneration: {}", e.getMessage(), e);
                notificationService.sendMessage(Topic.LOG, createLogNotification("Error during cover regeneration: " + e.getMessage()));
            }
        });
    }

    private void regenerateCoverForBook(BookEntity book, String progress) {
        String title = book.getMetadata().getTitle();
        String message = progress + "Regenerating cover for: " + title;
        notificationService.sendMessage(Topic.LOG, createLogNotification(message));

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
                    .moods(request.getMoods())
                    .tags(request.getTags())
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
}
