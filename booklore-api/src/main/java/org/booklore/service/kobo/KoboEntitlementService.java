package org.booklore.service.kobo;

import org.apache.commons.lang3.StringUtils;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.mapper.KoboReadingStateMapper;
import org.booklore.model.dto.kobo.*;
import org.booklore.model.dto.settings.KoboSettings;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.KoboBookFormat;
import org.booklore.model.enums.KoboReadStatus;
import org.booklore.model.enums.ShelfType;
import org.booklore.repository.KoboReadingStateRepository;
import org.booklore.repository.MagicShelfRepository;
import org.booklore.repository.ShelfRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.booklore.service.book.BookQueryService;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.opds.MagicShelfBookService;
import org.booklore.util.SecureXmlUtils;
import org.booklore.util.kobo.KoboUrlBuilder;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;

import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilder;

@AllArgsConstructor
@Service
@Slf4j
public class KoboEntitlementService {

    private static final int MAX_MAGIC_SHELF_BOOKS_FOR_KOBO = 250;
    private static final Pattern NON_ALPHANUMERIC_LOWERCASE_PATTERN = Pattern.compile("[^a-z0-9]");
    private final KoboUrlBuilder koboUrlBuilder;
    private final BookQueryService bookQueryService;
    private final AppSettingService appSettingService;
    private final KoboCompatibilityService koboCompatibilityService;
    private final UserBookProgressRepository progressRepository;
    private final KoboReadingStateRepository readingStateRepository;
    private final KoboReadingStateMapper readingStateMapper;
    private final AuthenticationService authenticationService;
    private final KoboReadingStateBuilder readingStateBuilder;
    private final ShelfRepository shelfRepository;
    private final MagicShelfRepository magicShelfRepository;
    private final MagicShelfBookService magicShelfBookService;
    private final KoboSettingsService koboSettingsService;

    public List<NewEntitlement> generateNewEntitlements(Set<Long> bookIds, String token) {
        List<BookEntity> books = bookQueryService.findAllWithMetadataByIds(bookIds);

        return books.stream()
                .filter(koboCompatibilityService::isBookSupportedForKobo)
                .map(book -> NewEntitlement.builder()
                        .newEntitlement(BookEntitlementContainer.builder()
                                .bookEntitlement(buildBookEntitlement(book, false))
                                .bookMetadata(mapToKoboMetadata(book, token))
                                .readingState(getReadingStateForBook(book))
                                .build())
                        .build())
                .collect(Collectors.toList());
    }

    public List<? extends Entitlement> generateChangedEntitlements(Set<Long> bookIds, String token, boolean removed) {

        List<BookEntity> books = bookQueryService.findAllWithMetadataByIds(bookIds);


        if (removed) {
            return books.stream()
                    .filter(koboCompatibilityService::isBookSupportedForKobo)
                    .map(book -> {
                        KoboBookMetadata metadata = KoboBookMetadata.builder()
                                .coverImageId(book.getBookCoverHash())
                                .crossRevisionId(String.valueOf(book.getId()))
                                .entitlementId(String.valueOf(book.getId()))
                                .revisionId(String.valueOf(book.getId()))
                                .workId(String.valueOf(book.getId()))
                                .title(String.valueOf(book.getId()))
                                .build();

                        return ChangedEntitlement.builder()
                                .changedEntitlement(BookEntitlementContainer.builder()
                                        .bookEntitlement(buildBookEntitlement(book, removed))
                                        .bookMetadata(metadata)
                                        .build())
                                .build();
                    })
                    .collect(Collectors.toList());
        }
        return books.stream()
                .filter(bookEntity -> bookEntity.getPrimaryBookFile() != null && bookEntity.getPrimaryBookFile().getBookType() == BookFileType.EPUB)
                .map(book -> ChangedProductMetadata.builder()
                        .changedProductMetadata(BookEntitlementContainer.builder()
                                .bookEntitlement(buildBookEntitlement(book, false))
                                .bookMetadata(mapToKoboMetadata(book, token))
                                .build())
                        .build())
                .collect(Collectors.toList());
    }

    public List<ChangedReadingState> generateChangedReadingStates(List<UserBookProgressEntity> progressEntries) {
        OffsetDateTime now = getCurrentUtc();
        String timestamp = now.toString();

        return progressEntries.stream()
                .map(progress -> buildChangedReadingState(progress, timestamp, now))
                .toList();
    }

    public List<KoboTagWrapper> generateTags() {
        Long userId = authenticationService.getAuthenticatedUser().getId();
        Set<Long> koboBookIDs = shelfRepository.findByUserIdAndName(userId, ShelfType.KOBO.getName())
                .orElseThrow(() -> new NoSuchElementException("Kobo shelf not found for user: " + userId))
                .getBookEntities().stream().filter(koboCompatibilityService::isBookSupportedForKobo)
                .map(BookEntity::getId)
                .collect(Collectors.toSet());

        List<KoboTagWrapper> tags = new ArrayList<>();
        // Shelves
        shelfRepository.findByUserId(userId).stream()
                .filter(shelf -> !Objects.equals(shelf.getName(), ShelfType.KOBO.getName()))
                .map(shelf -> buildKoboTag("BL-S-" + shelf.getId(), shelf.getName(), null, null,
                        shelf.getBookEntities().stream().map(BookEntity::getId).toList(), koboBookIDs))
                .forEach(tags::add);

        // Magic Shelves
        magicShelfRepository.findAllByUserId(userId).forEach(magicShelf -> {
            try {
                List<Long> bookIds = magicShelfBookService.getBookIdsByMagicShelfId(userId, magicShelf.getId(), MAX_MAGIC_SHELF_BOOKS_FOR_KOBO);
                tags.add(buildKoboTag("BL-MS-" + magicShelf.getId(), magicShelf.getName(),
                        magicShelf.getCreatedAt().atOffset(ZoneOffset.UTC).toString(), magicShelf.getUpdatedAt().atOffset(ZoneOffset.UTC).toString(),
                        bookIds, koboBookIDs));
            } catch (Exception e) {
                log.warn("Skipping magic shelf '{}' during Kobo tag generation: {}", magicShelf.getName(), e.getMessage());
            }
        });

        log.info("Synced {} tags to Kobo", tags.size());
        return tags;
    }

    private KoboTagWrapper buildKoboTag(String id, String name, String created, String modified, List<Long> bookIds, Set<Long> koboFilterIds) {
        List<KoboTag.KoboTagItem> items = bookIds.stream()
                .filter(koboFilterIds::contains)
                .map(bookId -> KoboTag.KoboTagItem.builder()
                        .revisionId(bookId.toString())
                        .type("ProductRevisionTagItem")
                        .build())
                .toList();

        if (items.isEmpty()) {
            return KoboTagWrapper.builder()
                    .deletedTag(KoboTagWrapper.WrappedTag.builder()
                            .tag(KoboTag.builder()
                                    .id(id)
                                    .lastModified(modified)
                                    .build())
                            .build())
                    .build();
        }

        return KoboTagWrapper.builder()
                .changedTag(KoboTagWrapper.WrappedTag.builder()
                        .tag(KoboTag.builder()
                                .id(id)
                                .name(name)
                                .created(created)
                                .lastModified(modified)
                                .type("UserTag")
                                .items(items)
                                .build())
                        .build())
                .build();
    }

    private ChangedReadingState buildChangedReadingState(UserBookProgressEntity progress, String timestamp, OffsetDateTime now) {
        String entitlementId = String.valueOf(progress.getBook().getId());

        boolean twoWaySync = koboSettingsService.getCurrentUserSettings().isTwoWayProgressSync();
        KoboReadingState.CurrentBookmark bookmark = (progress.getKoboProgressPercent() != null || (twoWaySync && progress.getEpubProgressPercent() != null))
                ? readingStateBuilder.buildBookmarkFromProgress(progress, now)
                : readingStateBuilder.buildEmptyBookmark(now);

        KoboReadingState readingState = KoboReadingState.builder()
                .entitlementId(entitlementId)
                .created(timestamp)
                .lastModified(timestamp)
                .priorityTimestamp(timestamp)
                .statusInfo(readingStateBuilder.buildStatusInfoFromProgress(progress, timestamp))
                .currentBookmark(bookmark)
                .statistics(KoboReadingState.Statistics.builder().lastModified(timestamp).build())
                .build();

        return ChangedReadingState.builder()
                .changedReadingState(ChangedReadingState.WrappedReadingState.builder()
                        .readingState(readingState)
                        .build())
                .build();
    }

    private KoboReadingState getReadingStateForBook(BookEntity book) {
        OffsetDateTime now = getCurrentUtc();
        String entitlementId = String.valueOf(book.getId());
        Long userId = authenticationService.getAuthenticatedUser().getId();

        KoboReadingState existingState = readingStateRepository.findByEntitlementIdAndUserId(entitlementId, userId)
                .or(() -> readingStateRepository
                        .findFirstByEntitlementIdAndUserIdIsNullOrderByPriorityTimestampDescLastModifiedStringDescIdDesc(
                                entitlementId))
                .map(readingStateMapper::toDto)
                .orElse(null);

        Optional<UserBookProgressEntity> userProgress = progressRepository
                .findByUserIdAndBookId(authenticationService.getAuthenticatedUser().getId(), book.getId());

        boolean twoWaySync = koboSettingsService.getCurrentUserSettings().isTwoWayProgressSync();

        KoboReadingState.CurrentBookmark webReaderBookmark = twoWaySync
                ? userProgress
                    .filter(progress -> progress.getEpubProgress() != null && progress.getEpubProgressPercent() != null)
                    .map(progress -> readingStateBuilder.buildBookmarkFromProgress(progress, now))
                    .orElse(null)
                : null;

        KoboReadingState.CurrentBookmark bookmark = webReaderBookmark != null
                ? webReaderBookmark
                : existingState != null && existingState.getCurrentBookmark() != null
                ? existingState.getCurrentBookmark()
                : userProgress
                .filter(progress -> progress.getKoboProgressPercent() != null || (twoWaySync && progress.getEpubProgressPercent() != null))
                .map(progress -> readingStateBuilder.buildBookmarkFromProgress(progress, now))
                .orElseGet(() -> readingStateBuilder.buildEmptyBookmark(now));

        KoboReadingState.StatusInfo statusInfo = userProgress
                .map(progress -> readingStateBuilder.buildStatusInfoFromProgress(progress, now.toString()))
                .orElseGet(() -> KoboReadingState.StatusInfo.builder()
                        .lastModified(now.toString())
                        .status(KoboReadStatus.READY_TO_READ)
                        .timesStartedReading(0)
                        .build());

        return KoboReadingState.builder()
                .entitlementId(entitlementId)
                .created(getCreatedOn(book).toString())
                .lastModified(now.toString())
                .statusInfo(statusInfo)
                .currentBookmark(bookmark)
                .statistics(KoboReadingState.Statistics.builder().lastModified(now.toString()).build())
                .priorityTimestamp(now.toString())
                .build();
    }

    private BookEntitlement buildBookEntitlement(BookEntity book, boolean removed) {
        OffsetDateTime now = getCurrentUtc();
        OffsetDateTime createdOn = getCreatedOn(book);

        return BookEntitlement.builder()
                .activePeriod(BookEntitlement.ActivePeriod.builder()
                        .from(now.toString())
                        .build())
                .removed(removed)
                .status("Active")
                .crossRevisionId(String.valueOf(book.getId()))
                .revisionId(String.valueOf(book.getId()))
                .id(String.valueOf(book.getId()))
                .created(createdOn.toString())
                .lastModified(now.toString())
                .build();
    }

    public KoboBookMetadata getMetadataForBook(long bookId, String token) {
        List<BookEntity> books = bookQueryService.findAllWithMetadataByIds(Set.of(bookId))
                .stream()
                .filter(koboCompatibilityService::isBookSupportedForKobo)
                .toList();
        return mapToKoboMetadata(books.getFirst(), token);
    }

    private boolean checkEpubFixedLayout(BookEntity book) {
        if (book.getPrimaryBookFile().getBookType() != BookFileType.EPUB) return false;
        Path filePath = book.getFullFilePath();
        if (filePath == null) return false;

        File epubFile = book.getFullFilePath().toFile();
        try (ZipFile zip = new ZipFile(epubFile)) {
            DocumentBuilder builder = SecureXmlUtils.createSecureDocumentBuilder(true);

            FileHeader containerHdr = zip.getFileHeader("META-INF/container.xml");
            if (containerHdr == null) return false;

            try (InputStream cis = zip.getInputStream(containerHdr)) {
                Document containerDoc = builder.parse(cis);
                NodeList roots = containerDoc.getElementsByTagName("rootfile");
                if (roots.getLength() == 0) return false;

                String opfPath = ((Element) roots.item(0)).getAttribute("full-path");
                if (StringUtils.isBlank(opfPath)) return false;

                FileHeader opfHdr = zip.getFileHeader(opfPath);
                if (opfHdr == null) return false;

                try (InputStream in = zip.getInputStream(opfHdr)) {
                    Document doc = builder.parse(in);
                    NodeList manifestItems = doc.getElementsByTagName("meta");

                    for (int i = 0; i < manifestItems.getLength(); i++) {
                        Element item = (Element) manifestItems.item(i);
                        String prop = item.getAttribute("property");
                        if (prop.equals("rendition:layout") && item.getTextContent().equals("pre-paginated")) return true;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to determine if epub is pre-paginated for Kobo sync: {}", e.getMessage());
        }
        return false;
    }

    private KoboBookMetadata mapToKoboMetadata(BookEntity book, String token) {
        BookMetadataEntity metadata = book.getMetadata();

        KoboBookMetadata.Publisher publisher = KoboBookMetadata.Publisher.builder()
                .name(metadata.getPublisher())
                .imprint(metadata.getPublisher())
                .build();

        List<String> authors = Optional.ofNullable(metadata.getAuthors())
                .map(list -> list.stream().map(AuthorEntity::getName).toList())
                .orElse(Collections.emptyList());

        List<String> categories = Optional.ofNullable(metadata.getCategories())
                .map(list -> list.stream().map(CategoryEntity::getName).toList())
                .orElse(Collections.emptyList());

        KoboBookMetadata.Series series = null;
        if (metadata.getSeriesName() != null && !metadata.getSeriesName().isBlank()) {
            series = KoboBookMetadata.Series.builder()
                    .id("series_" + metadata.getSeriesName().hashCode())
                    .name(metadata.getSeriesName())
                    .number(metadata.getSeriesNumber() != null 
                        ? BigDecimal.valueOf(metadata.getSeriesNumber()).stripTrailingZeros().toPlainString() 
                        : "1")
                    .numberFloat(metadata.getSeriesNumber() != null ? metadata.getSeriesNumber().doubleValue() : 1.0)
                    .build();
        } else {
            series = KoboBookMetadata.Series.builder()
                    .id("")
                    .name("")
                    .number("")
                    .numberFloat(0.0)
                    .build();
        }

        String downloadUrl = koboUrlBuilder.downloadUrl(token, book.getId());

        KoboBookFormat bookFormat = KoboBookFormat.EPUB3;
        KoboSettings koboSettings = appSettingService.getAppSettings().getKoboSettings();

        var primaryFile = book.getPrimaryBookFile();
        if (primaryFile == null) {
            return null;
        }
        boolean isEpubFile = primaryFile.getBookType() == BookFileType.EPUB;
        boolean isCbxFile = primaryFile.getBookType() == BookFileType.CBX;

        if (koboSettings != null) {
            if (isEpubFile && checkEpubFixedLayout(book)) {
                bookFormat = KoboBookFormat.EPUB3FL;
            } else if (isEpubFile && koboSettings.isConvertToKepub()) {
                bookFormat = KoboBookFormat.KEPUB;
            } else if (isCbxFile && koboSettings.isConvertCbxToEpub()) {
                bookFormat = KoboBookFormat.EPUB3;
            }
        }

        return KoboBookMetadata.builder()
                .crossRevisionId(String.valueOf(book.getId()))
                .revisionId(String.valueOf(book.getId()))
                .publisher(publisher)
                .publicationDate(metadata.getPublishedDate() != null
                        ? metadata.getPublishedDate().atStartOfDay().atOffset(ZoneOffset.UTC).toString()
                        : null)
                .isbn(metadata.getIsbn13() != null ? metadata.getIsbn13() : metadata.getIsbn10())
                .genre(categories.isEmpty() ? null : categories.getFirst())
                .slug(metadata.getTitle() != null
                        ? NON_ALPHANUMERIC_LOWERCASE_PATTERN.matcher(metadata.getTitle().toLowerCase()).replaceAll("-")
                        : null)
                .coverImageId(book.getBookCoverHash())
                .workId(String.valueOf(book.getId()))
                .preOrder(false)
                .contributorRoles(Collections.emptyList())
                .entitlementId(String.valueOf(book.getId()))
                .title(metadata.getTitle())
                .description(metadata.getDescription())
                .contributors(authors)
                .series(series)
                .language(metadata.getLanguage())
                .downloadUrls(List.of(
                        KoboBookMetadata.DownloadUrl.builder()
                                .url(downloadUrl)
                                .format(bookFormat.toString())
                                .size(primaryFile.getFileSizeKb() * 1024)
                                .build()
                ))
                .build();
    }

    private OffsetDateTime getCurrentUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private OffsetDateTime getCreatedOn(BookEntity book) {
        return book.getAddedOn() != null ? book.getAddedOn().atOffset(ZoneOffset.UTC) : getCurrentUtc();
    }
}
