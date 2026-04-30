package org.booklore.service.bookdrop;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.request.MetadataRefreshOptions;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.entity.BookdropFileEntity;
import org.booklore.model.enums.BookFileExtension;
import org.booklore.model.enums.MetadataProvider;
import org.booklore.repository.BookdropFileRepository;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.metadata.MetadataRefreshService;
import org.booklore.service.metadata.extractor.MetadataExtractorFactory;
import org.booklore.util.BookUtils;
import org.booklore.util.FileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static org.booklore.model.entity.BookdropFileEntity.Status.PENDING_REVIEW;
import static org.booklore.util.FileService.truncate;

@Slf4j
@AllArgsConstructor
@Service
public class BookdropMetadataService {

    private final BookdropFileRepository bookdropFileRepository;
    private final AppSettingService appSettingService;
    private final ObjectMapper objectMapper;
    private final MetadataExtractorFactory metadataExtractorFactory;
    private final MetadataRefreshService metadataRefreshService;
    private final FileService fileService;
    private final BookdropMetadataPersistenceService persistenceService;

    @Transactional
    public BookdropFileEntity attachInitialMetadata(Long bookdropFileId) throws JacksonException {
        BookdropFileEntity entity = getOrThrow(bookdropFileId);
        BookMetadata initial = extractInitialMetadata(entity);
        if (initial == null) {
            log.warn("Metadata extraction returned null for file: {}. Using empty metadata as fallback.", entity.getFileName());
            initial = BookMetadata.builder().build();
        }
        if (initial.getTitle() == null || initial.getTitle().isBlank()) {
            log.warn("Metadata extraction returned empty title for file: {}. Using filename as fallback.", entity.getFileName());
            initial.setTitle(FilenameUtils.getBaseName(entity.getFileName()));
        }
        extractAndSaveCover(entity);
        String initialJson = objectMapper.writeValueAsString(initial);
        entity.setOriginalMetadata(initialJson);
        entity.setUpdatedAt(Instant.now());
        return bookdropFileRepository.save(entity);
    }


    public BookdropFileEntity attachFetchedMetadata(Long bookdropFileId) {
        BookdropFileEntity entity = getOrThrow(bookdropFileId);
        try {
            BookMetadata initial = objectMapper.readValue(entity.getOriginalMetadata(), BookMetadata.class);

            if (!hasSearchableMetadata(initial, entity)) {
                log.info("Skipping online metadata fetch for '{}' — no reliable search data (title derived from filename, no ISBN or ASIN).", entity.getFileName());
                return persistenceService.saveRefetchedMetadata(entity, null, PENDING_REVIEW);
            }

            String fetchedJson = fetchOnlineMetadata(bookdropFileId, initial);
            return persistenceService.saveRefetchedMetadata(entity, fetchedJson, PENDING_REVIEW);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to parse/serialize metadata for file " + bookdropFileId, e);
        }
    }

    public BookdropFileEntity refetchMetadata(Long bookdropFileId, BookMetadata manualMetadata) {
        BookdropFileEntity entity = getOrThrow(bookdropFileId);
        try {
            String fetchedJson = fetchOnlineMetadata(bookdropFileId, manualMetadata);
            return persistenceService.saveRefetchedMetadata(entity, fetchedJson, null);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to parse/serialize metadata for file " + bookdropFileId, e);
        }
    }

    private String fetchOnlineMetadata(Long bookdropFileId, BookMetadata searchMetadata) throws JacksonException {
        AppSettings appSettings = appSettingService.getAppSettings();
        MetadataRefreshOptions refreshOptions = appSettings.getDefaultMetadataRefreshOptions();

        List<MetadataProvider> providers = metadataRefreshService.prepareProviders(refreshOptions);
        Book book = Book.builder()
                .metadata(searchMetadata)
                .build();

        if (providers.contains(MetadataProvider.GoodReads)) {
            try {
                Thread.sleep(ThreadLocalRandom.current().nextLong(250, 1250));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }

        Map<MetadataProvider, BookMetadata> metadataMap = metadataRefreshService.fetchMetadataForBook(providers, book);
        BookMetadata fetchedMetadata = metadataRefreshService.buildFetchMetadata(searchMetadata, book.getId(), refreshOptions, metadataMap);
        return objectMapper.writeValueAsString(fetchedMetadata);
    }

    private boolean hasSearchableMetadata(BookMetadata metadata, BookdropFileEntity entity) {
        if (hasAnyKnownIdentifier(metadata)) {
            return true;
        }
        String title = metadata.getTitle();
        if (title == null || title.isBlank()) {
            return false;
        }

        String filenameFallback = FilenameUtils.getBaseName(entity.getFileName());
        
        // If title is NOT the same as filename, it's definitely searchable (likely from internal metadata)
        if (!title.strip().equalsIgnoreCase(filenameFallback.strip())) {
            return true;
        }

        // If title matches filename, we only skip if it's "junk" (too short or just generic)
        String cleanedTitle = BookUtils.cleanFileName(entity.getFileName());
        return cleanedTitle != null && cleanedTitle.length() >= 3;
    }

    private boolean hasAnyKnownIdentifier(BookMetadata m) {
        // Keep in sync with identifier fields in BookMetadata when new providers are added.
        return Stream.of(
                m.getIsbn13(), m.getIsbn10(), m.getAsin(),
                m.getGoodreadsId(), m.getGoogleId(),
                m.getHardcoverId(), m.getHardcoverBookId(),
                m.getComicvineId(), m.getDoubanId(),
                m.getLubimyczytacId(), m.getRanobedbId(), m.getAudibleId()
        ).anyMatch(id -> id != null && !id.isBlank());
    }

    private BookdropFileEntity getOrThrow(Long id) {
        return bookdropFileRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Bookdrop file not found: " + id));
    }

    private BookMetadata cleanInitialMetadata(BookMetadata extracted) {
        if (extracted == null) {
            return null;
        }

        // Created a builder from `extracted` to perform a shallow clone
        // where we overwrite only truncated fields.

        return extracted.toBuilder()

                // Basic Fields
                .title(truncate(extracted.getTitle(), 1000))
                .subtitle(truncate(extracted.getSubtitle(), 1000))
                .description(truncate(extracted.getDescription(), 5000))
                .publisher(truncate(extracted.getPublisher(), 1000))
                .publishedDate(extracted.getPublishedDate())
                .seriesName(truncate(extracted.getSeriesName(), 1000))
                .language(truncate(extracted.getLanguage(), 10))

                // ISBN
                .isbn10(truncate(extracted.getIsbn10(), 10))
                .isbn13(truncate(extracted.getIsbn13(), 13))

                // External IDs
                .asin(truncate(extracted.getAsin(), 10))
                .audibleId(truncate(extracted.getAudibleId(), 10))
                .goodreadsId(truncate(extracted.getGoodreadsId(), 100))
                .hardcoverId(truncate(extracted.getHardcoverId(), 100))
                .hardcoverBookId(truncate(extracted.getHardcoverBookId(), 100))
                .googleId(truncate(extracted.getGoogleId(), 100))
                .comicvineId(truncate(extracted.getComicvineId(), 100))
                .lubimyczytacId(truncate(extracted.getLubimyczytacId(), 100))
                .ranobedbId(truncate(extracted.getRanobedbId(), 100))
                .doubanId(truncate(extracted.getDoubanId(), 100))

                .build();
    }

    private BookMetadata extractInitialMetadata(BookdropFileEntity entity) {
        File file = new File(entity.getFilePath());
        BookFileExtension fileExt = BookFileExtension.fromFileName(file.getName())
            .orElseThrow(() -> ApiError.INVALID_FILE_FORMAT.createException("Unsupported file extension"));
        return cleanInitialMetadata(metadataExtractorFactory.extractMetadata(fileExt, file));
    }

    private void extractAndSaveCover(BookdropFileEntity entity) {
        File file = new File(entity.getFilePath());
        BookFileExtension fileExt = BookFileExtension.fromFileName(file.getName())
            .orElseThrow(() -> ApiError.INVALID_FILE_FORMAT.createException("Unsupported file extension"));
        byte[] coverBytes = metadataExtractorFactory.extractCover(fileExt, file);
        if (coverBytes != null) {
            try {
                FileService.saveImage(coverBytes, fileService.getTempBookdropCoverImagePath(entity.getId()));
            } catch (IOException e) {
                log.warn("Failed to save extracted cover for file: {}", entity.getFilePath(), e);
            }
        }
    }
}
