package org.grimmory.service;

import org.grimmory.config.security.service.AuthenticationService;
import org.grimmory.model.dto.Book;
import org.grimmory.model.dto.BookLoreUser;
import org.grimmory.model.dto.settings.AppSettings;
import org.grimmory.model.dto.kobo.KoboBookMetadata;
import org.grimmory.model.dto.kobo.KoboTag;
import org.grimmory.model.dto.kobo.KoboTagWrapper;
import org.grimmory.model.dto.settings.KoboSettings;
import org.grimmory.model.entity.BookFileEntity;
import org.grimmory.model.entity.BookEntity;
import org.grimmory.model.entity.BookMetadataEntity;
import org.grimmory.model.entity.MagicShelfEntity;
import org.grimmory.model.entity.ShelfEntity;
import org.grimmory.model.enums.BookFileType;
import org.grimmory.model.enums.KoboBookFormat;
import org.grimmory.model.enums.ShelfType;
import org.grimmory.repository.MagicShelfRepository;
import org.grimmory.repository.ShelfRepository;
import org.grimmory.service.appsettings.AppSettingService;
import org.grimmory.service.book.BookQueryService;
import org.grimmory.service.kobo.KoboCompatibilityService;
import org.grimmory.mapper.KoboReadingStateMapper;
import org.grimmory.model.dto.KoboSyncSettings;
import org.grimmory.model.dto.kobo.*;
import org.grimmory.model.entity.KoboReadingStateEntity;
import org.grimmory.model.entity.UserBookFileProgressEntity;
import org.grimmory.model.entity.UserBookProgressEntity;
import org.grimmory.model.enums.KoboReadStatus;
import org.grimmory.model.enums.ReadStatus;
import org.grimmory.repository.KoboReadingStateRepository;
import org.grimmory.repository.UserBookFileProgressRepository;
import org.grimmory.repository.UserBookProgressRepository;
import org.grimmory.service.kobo.KoboEntitlementService;
import org.grimmory.service.kobo.KoboReadingStateBuilder;
import org.grimmory.service.kobo.KoboSettingsService;
import org.grimmory.service.kobo.KoboSpanMapService;
import org.grimmory.service.opds.MagicShelfBookService;
import org.grimmory.util.kobo.KoboUrlBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import org.springframework.data.domain.PageImpl;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KoboEntitlementServiceTest {

    @Mock
    private KoboUrlBuilder koboUrlBuilder;

    @Mock
    private BookQueryService bookQueryService;

    @Mock
    private AppSettingService appSettingService;

    @Mock
    private KoboCompatibilityService koboCompatibilityService;

    @Mock
    private AuthenticationService authenticationService;

    @Mock
    private ShelfRepository shelfRepository;

    @Mock
    private MagicShelfRepository magicShelfRepository;

    @Mock
    private MagicShelfBookService magicShelfBookService;

    @Mock
    private UserBookProgressRepository progressRepository;

    @Mock
    private UserBookFileProgressRepository fileProgressRepository;

    @Mock
    private KoboReadingStateRepository readingStateRepository;

    @Mock
    private KoboReadingStateMapper readingStateMapper;

    @Mock
    private KoboReadingStateBuilder readingStateBuilder;

    @Mock
    private KoboSettingsService koboSettingsService;

    @Mock
    private KoboSpanMapService koboSpanMapService;

    @InjectMocks
    private KoboEntitlementService koboEntitlementService;

    private BookLoreUser user;

    @BeforeEach
    void setUp() {
        BookLoreUser.UserPermissions permissions = new BookLoreUser.UserPermissions();
        user = BookLoreUser.builder().id(1L).permissions(permissions).build();
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(koboSpanMapService.getValidMaps(anyMap())).thenReturn(Map.of());
        when(progressRepository.findByUserIdAndBookIdForKoboSync(anyLong(), anyLong()))
                .thenAnswer(invocation -> progressRepository.findByUserIdAndBookId(
                        invocation.getArgument(0),
                        invocation.getArgument(1)));
    }

    @Test
    void getMetadataForBook_shouldUseCompatibilityServiceFilter() {
        long bookId = 1L;
        String token = "test-token";

        BookEntity cbxBook = createCbxBookEntity(bookId);
        when(bookQueryService.findAllWithMetadataByIds(Set.of(bookId)))
                .thenReturn(List.of(cbxBook));
        when(koboCompatibilityService.isBookSupportedForKobo(cbxBook))
                .thenReturn(true);
        when(koboUrlBuilder.downloadUrl(token, bookId))
                .thenReturn("http://test.com/download/" + bookId);
        when(appSettingService.getAppSettings())
                .thenReturn(createAppSettingsWithKoboSettings());

        KoboBookMetadata result = koboEntitlementService.getMetadataForBook(bookId, token);

        assertNotNull(result);
        assertEquals("Test CBX Book", result.getTitle());
        verify(koboCompatibilityService).isBookSupportedForKobo(cbxBook);
    }

    @Test
    void getMetadataForBook_shouldReturnNullWhenNoBook() {
        long bookId = 1L;
        String token = "test-token";

        BookEntity cbxBook = createCbxBookEntity(bookId);
        when(bookQueryService.findAllWithMetadataByIds(Set.of(bookId)))
                .thenReturn(Collections.emptyList());
        when(koboCompatibilityService.isBookSupportedForKobo(cbxBook))
                .thenReturn(true);
        when(koboUrlBuilder.downloadUrl(token, bookId))
                .thenReturn("http://test.com/download/" + bookId);
        when(appSettingService.getAppSettings())
                .thenReturn(createAppSettingsWithKoboSettings());

        KoboBookMetadata result = koboEntitlementService.getMetadataForBook(bookId, token);

        assertNull(result);
    }

    @Test
    void mapToKoboMetadata_cbxBookWithConversionEnabled_shouldReturnEpubFormat() {
        long bookId = 1L;
        BookEntity cbxBook = createCbxBookEntity(bookId);
        String token = "test-token";

        when(bookQueryService.findAllWithMetadataByIds(Set.of(bookId)))
                .thenReturn(List.of(cbxBook));
        when(koboCompatibilityService.isBookSupportedForKobo(cbxBook))
                .thenReturn(true);
        when(koboUrlBuilder.downloadUrl(token, cbxBook.getId()))
                .thenReturn("http://test.com/download/" + cbxBook.getId());
        when(appSettingService.getAppSettings())
                .thenReturn(createAppSettingsWithKoboSettings());

        KoboBookMetadata result = koboEntitlementService.getMetadataForBook(bookId, token);

        assertNotNull(result);
        assertEquals(1, result.getDownloadUrls().size());
        assertEquals(KoboBookFormat.EPUB3.toString(), result.getDownloadUrls().getFirst().getFormat());
    }

    @Test
    void mapToKoboMetadata_epubBookShouldReturnKepubFormatEvenWhenToggleIsOff() {
        long bookId = 1L;
        BookEntity epubBook = createEpubBookEntity(bookId);
        String token = "test-token";

        when(bookQueryService.findAllWithMetadataByIds(Set.of(bookId)))
                .thenReturn(List.of(epubBook));
        when(koboCompatibilityService.isBookSupportedForKobo(epubBook))
                .thenReturn(true);
        when(koboUrlBuilder.downloadUrl(token, epubBook.getId()))
                .thenReturn("http://test.com/download/" + epubBook.getId());
        when(appSettingService.getAppSettings())
                .thenReturn(createAppSettingsWithKoboSettings());

        KoboBookMetadata result = koboEntitlementService.getMetadataForBook(bookId, token);

        assertNotNull(result);
        assertEquals(1, result.getDownloadUrls().size());
        assertEquals(KoboBookFormat.KEPUB.toString(), result.getDownloadUrls().getFirst().getFormat());
    }

    private BookEntity createCbxBookEntity(Long id) {
        BookEntity book = new BookEntity();
        book.setId(id);

        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(book);
        primaryFile.setBookType(BookFileType.CBX);
        primaryFile.setFileSizeKb(1024L);
        book.setBookFiles(List.of(primaryFile));

        BookMetadataEntity metadata = new BookMetadataEntity();
        metadata.setTitle("Test CBX Book");
        metadata.setDescription("A test CBX comic book");
        metadata.setBookId(id);
        book.setMetadata(metadata);

        return book;
    }

    private AppSettings createAppSettingsWithKoboSettings() {
        var appSettings = new AppSettings();
        KoboSettings koboSettings = KoboSettings.builder()
                .convertCbxToEpub(true)
                .conversionLimitInMbForCbx(50)
                .conversionLimitInMb(50)
                .build();
        appSettings.setKoboSettings(koboSettings);
        return appSettings;
    }

    @Test
    void generateTags_shouldReturnTagsForShelvesAndMagicShelves() {
        BookEntity book1 = createEpubBookEntity(1L);
        BookEntity book2 = createEpubBookEntity(2L);

        ShelfEntity koboShelf = ShelfEntity.builder().id(100L).name(ShelfType.KOBO.getName()).bookEntities(Set.of(book1, book2)).build();
        ShelfEntity userShelf = ShelfEntity.builder().id(101L).name("My Favorites").bookEntities(Set.of(book1)).build();

        MagicShelfEntity magicShelf = MagicShelfEntity.builder()
                .id(201L)
                .userId(user.getId())
                .name("Sci-Fi Books")
                .icon("pi-book")
                .filterJson("{}")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(shelfRepository.findByUserIdAndName(user.getId(), ShelfType.KOBO.getName()))
                .thenReturn(Optional.of(koboShelf));
        when(koboCompatibilityService.isBookSupportedForKobo(any(BookEntity.class))).thenReturn(true);
        when(shelfRepository.findByUserId(user.getId())).thenReturn(List.of(koboShelf, userShelf));
        when(magicShelfRepository.findAllByUserId(user.getId())).thenReturn(List.of(magicShelf));
        when(magicShelfBookService.getBookIdsByMagicShelfId(eq(user.getId()), eq(201L), anyInt()))
                .thenReturn(List.of(2L));

        List<KoboTagWrapper> tags = koboEntitlementService.generateTags();

        assertEquals(2, tags.size());

        // Verify shelf tag
        KoboTagWrapper shelfTag = tags.stream()
                .filter(t -> t.getChangedTag() != null && t.getChangedTag().getTag().getId().equals("BL-S-101"))
                .findFirst()
                .orElseThrow();
        assertEquals("My Favorites", shelfTag.getChangedTag().getTag().getName());
        assertEquals("UserTag", shelfTag.getChangedTag().getTag().getType());
        assertEquals(1, shelfTag.getChangedTag().getTag().getItems().size());
        assertEquals("1", shelfTag.getChangedTag().getTag().getItems().getFirst().getRevisionId());

        // Verify magic shelf tag
        KoboTagWrapper magicShelfTag = tags.stream()
                .filter(t -> t.getChangedTag() != null && t.getChangedTag().getTag().getId().equals("BL-MS-201"))
                .findFirst()
                .orElseThrow();
        assertEquals("Sci-Fi Books", magicShelfTag.getChangedTag().getTag().getName());
        assertEquals(1, magicShelfTag.getChangedTag().getTag().getItems().size());
    }

    @Test
    void generateTags_shouldExcludeKoboShelfFromTags() {
        BookEntity book1 = createEpubBookEntity(1L);
        ShelfEntity koboShelf = ShelfEntity.builder().id(100L).name(ShelfType.KOBO.getName()).bookEntities(Set.of(book1)).build();

        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(shelfRepository.findByUserIdAndName(user.getId(), ShelfType.KOBO.getName()))
                .thenReturn(Optional.of(koboShelf));
        when(koboCompatibilityService.isBookSupportedForKobo(any(BookEntity.class))).thenReturn(true);
        when(shelfRepository.findByUserId(user.getId())).thenReturn(List.of(koboShelf));
        when(magicShelfRepository.findAllByUserId(user.getId())).thenReturn(List.of());

        List<KoboTagWrapper> tags = koboEntitlementService.generateTags();

        assertTrue(tags.isEmpty());
    }

    @Test
    void generateTags_shouldReturnDeletedTagWhenNoMatchingBooks() {
        BookEntity book1 = createEpubBookEntity(1L);
        BookEntity book2 = createEpubBookEntity(2L);

        // Kobo shelf only has book1
        ShelfEntity koboShelf = ShelfEntity.builder().id(100L).name(ShelfType.KOBO.getName()).bookEntities(Set.of(book1)).build();
        // User shelf only has book2, which is not in Kobo shelf
        ShelfEntity userShelf = ShelfEntity.builder().id(101L).name("My Favorites").bookEntities(Set.of(book2)).build();

        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(shelfRepository.findByUserIdAndName(user.getId(), ShelfType.KOBO.getName()))
                .thenReturn(Optional.of(koboShelf));
        when(koboCompatibilityService.isBookSupportedForKobo(any(BookEntity.class))).thenReturn(true);
        when(shelfRepository.findByUserId(user.getId())).thenReturn(List.of(koboShelf, userShelf));
        when(magicShelfRepository.findAllByUserId(user.getId())).thenReturn(List.of());

        List<KoboTagWrapper> tags = koboEntitlementService.generateTags();

        assertEquals(1, tags.size());

        // Should be a deleted tag since book2 is not in Kobo shelf
        KoboTagWrapper deletedTag = tags.getFirst();
        assertNotNull(deletedTag.getDeletedTag());
        assertNull(deletedTag.getChangedTag());
        assertEquals("BL-S-101", deletedTag.getDeletedTag().getTag().getId());
    }

    @Test
    void generateTags_shouldFilterBooksNotSupportedForKobo() {
        BookEntity supportedBook = createEpubBookEntity(1L);
        BookEntity unsupportedBook = createEpubBookEntity(2L);

        ShelfEntity koboShelf = ShelfEntity.builder().id(100L).name(ShelfType.KOBO.getName()).bookEntities(Set.of(supportedBook, unsupportedBook)).build();
        ShelfEntity userShelf = ShelfEntity.builder().id(101L).name("My Favorites").bookEntities(Set.of(supportedBook, unsupportedBook)).build();

        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(shelfRepository.findByUserIdAndName(user.getId(), ShelfType.KOBO.getName()))
                .thenReturn(Optional.of(koboShelf));
        when(koboCompatibilityService.isBookSupportedForKobo(supportedBook)).thenReturn(true);
        when(koboCompatibilityService.isBookSupportedForKobo(unsupportedBook)).thenReturn(false);
        when(shelfRepository.findByUserId(user.getId())).thenReturn(List.of(koboShelf, userShelf));
        when(magicShelfRepository.findAllByUserId(user.getId())).thenReturn(List.of());

        List<KoboTagWrapper> tags = koboEntitlementService.generateTags();

        assertEquals(1, tags.size());

        KoboTagWrapper shelfTag = tags.getFirst();
        assertNotNull(shelfTag.getChangedTag());
        // Only supported book should be included
        assertEquals(1, shelfTag.getChangedTag().getTag().getItems().size());
        assertEquals("1", shelfTag.getChangedTag().getTag().getItems().getFirst().getRevisionId());
    }

    @Test
    void generateTags_shouldSetCorrectTagItemType() {
        BookEntity book1 = createEpubBookEntity(1L);
        ShelfEntity koboShelf = ShelfEntity.builder().id(100L).name(ShelfType.KOBO.getName()).bookEntities(Set.of(book1)).build();
        ShelfEntity userShelf = ShelfEntity.builder().id(101L).name("Reading").bookEntities(Set.of(book1)).build();

        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(shelfRepository.findByUserIdAndName(user.getId(), ShelfType.KOBO.getName()))
                .thenReturn(Optional.of(koboShelf));
        when(koboCompatibilityService.isBookSupportedForKobo(any(BookEntity.class))).thenReturn(true);
        when(shelfRepository.findByUserId(user.getId())).thenReturn(List.of(koboShelf, userShelf));
        when(magicShelfRepository.findAllByUserId(user.getId())).thenReturn(List.of());

        List<KoboTagWrapper> tags = koboEntitlementService.generateTags();

        assertEquals(1, tags.size());

        KoboTag.KoboTagItem item = tags.getFirst().getChangedTag().getTag().getItems().getFirst();
        assertEquals("ProductRevisionTagItem", item.getType());
    }

    @Test
    void generateTags_shouldUseMagicShelfTimestamps() {
        BookEntity book1 = createEpubBookEntity(1L);
        ShelfEntity koboShelf = ShelfEntity.builder().id(100L).name(ShelfType.KOBO.getName()).bookEntities(Set.of(book1)).build();

        LocalDateTime createdAt = LocalDateTime.of(2024, 1, 15, 10, 30, 0);
        LocalDateTime updatedAt = LocalDateTime.of(2024, 6, 20, 14, 45, 0);
        MagicShelfEntity magicShelf = MagicShelfEntity.builder()
                .id(201L)
                .userId(user.getId())
                .name("Fantasy")
                .icon("pi-book")
                .filterJson("{}")
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();

        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(shelfRepository.findByUserIdAndName(user.getId(), ShelfType.KOBO.getName()))
                .thenReturn(Optional.of(koboShelf));
        when(koboCompatibilityService.isBookSupportedForKobo(any(BookEntity.class))).thenReturn(true);
        when(shelfRepository.findByUserId(user.getId())).thenReturn(List.of(koboShelf));
        when(magicShelfRepository.findAllByUserId(user.getId())).thenReturn(List.of(magicShelf));
        when(magicShelfBookService.getBookIdsByMagicShelfId(eq(user.getId()), eq(201L), anyInt()))
                .thenReturn(List.of(1L));

        List<KoboTagWrapper> tags = koboEntitlementService.generateTags();

        assertEquals(1, tags.size());

        KoboTag tag = tags.getFirst().getChangedTag().getTag();
        assertEquals(createdAt.atOffset(ZoneOffset.UTC).toString(), tag.getCreated());
        assertEquals(updatedAt.atOffset(ZoneOffset.UTC).toString(), tag.getLastModified());
    }

    @Test
    void generateTags_shouldHandleEmptyShelvesAndMagicShelves() {
        BookEntity book1 = createEpubBookEntity(1L);
        ShelfEntity koboShelf = ShelfEntity.builder().id(100L).name(ShelfType.KOBO.getName()).bookEntities(Set.of(book1)).build();

        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(shelfRepository.findByUserIdAndName(user.getId(), ShelfType.KOBO.getName()))
                .thenReturn(Optional.of(koboShelf));
        when(koboCompatibilityService.isBookSupportedForKobo(any(BookEntity.class))).thenReturn(true);
        when(shelfRepository.findByUserId(user.getId())).thenReturn(List.of(koboShelf));
        when(magicShelfRepository.findAllByUserId(user.getId())).thenReturn(List.of());

        List<KoboTagWrapper> tags = koboEntitlementService.generateTags();

        assertTrue(tags.isEmpty());
    }

    @Test
    void generateTags_shouldIncludeMultipleBooksInSingleTag() {
        BookEntity book1 = createEpubBookEntity(1L);
        BookEntity book2 = createEpubBookEntity(2L);
        BookEntity book3 = createEpubBookEntity(3L);

        ShelfEntity koboShelf = ShelfEntity.builder().id(100L).name(ShelfType.KOBO.getName()).bookEntities(Set.of(book1, book2, book3)).build();
        ShelfEntity userShelf = ShelfEntity.builder().id(101L).name("Collection").bookEntities(Set.of(book1, book2, book3)).build();

        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(shelfRepository.findByUserIdAndName(user.getId(), ShelfType.KOBO.getName()))
                .thenReturn(Optional.of(koboShelf));
        when(koboCompatibilityService.isBookSupportedForKobo(any(BookEntity.class))).thenReturn(true);
        when(shelfRepository.findByUserId(user.getId())).thenReturn(List.of(koboShelf, userShelf));
        when(magicShelfRepository.findAllByUserId(user.getId())).thenReturn(List.of());

        List<KoboTagWrapper> tags = koboEntitlementService.generateTags();

        assertEquals(1, tags.size());

        KoboTag tag = tags.getFirst().getChangedTag().getTag();
        assertEquals(3, tag.getItems().size());

        Set<String> revisionIds = new HashSet<>();
        tag.getItems().forEach(item -> revisionIds.add(item.getRevisionId()));
        assertTrue(revisionIds.contains("1"));
        assertTrue(revisionIds.contains("2"));
        assertTrue(revisionIds.contains("3"));
    }

    private BookEntity createEpubBookEntity(Long id) {
        BookEntity book = new BookEntity();
        book.setId(id);
        book.setBookFiles(new java.util.ArrayList<>());

        BookFileEntity bookFile = BookFileEntity.builder()
                .book(book)
                .fileName("test-book-" + id + ".epub")
                .fileSubPath("")
                .isBookFormat(true)
                .bookType(BookFileType.EPUB)
                .fileSizeKb(1024L)
                .build();
        book.getBookFiles().add(bookFile);

        BookMetadataEntity metadata = new BookMetadataEntity();
        metadata.setTitle("Test EPUB Book " + id);
        metadata.setDescription("A test EPUB book");
        metadata.setBookId(id);
        book.setMetadata(metadata);

        return book;
    }

    @Nested
    @DisplayName("Generate New Entitlements")
    class GenerateNewEntitlements {

        @Test
        @DisplayName("Should generate new entitlements for supported books")
        void generateNewEntitlements_supportedBooks() {
            BookEntity book = createEpubBookEntity(1L);
            book.setAddedOn(Instant.parse("2025-01-01T00:00:00Z"));

            when(bookQueryService.findAllWithMetadataByIds(Set.of(1L))).thenReturn(List.of(book));
            when(koboCompatibilityService.isBookSupportedForKobo(book)).thenReturn(true);
            when(koboUrlBuilder.downloadUrl("token1", 1L)).thenReturn("http://test.com/download/1");
            when(appSettingService.getAppSettings()).thenReturn(createAppSettingsWithKoboSettings());
            when(authenticationService.getAuthenticatedUser()).thenReturn(user);
            when(progressRepository.findByUserIdAndBookId(any(), eq(1L))).thenReturn(Optional.empty());
            when(readingStateRepository.findByEntitlementIdAndUserId(anyString(), any())).thenReturn(Optional.empty());
            when(readingStateRepository.findFirstByEntitlementIdAndUserIdIsNullOrderByPriorityTimestampDescLastModifiedStringDescIdDesc(anyString()))
                    .thenReturn(Optional.empty());

            KoboSyncSettings settings = new KoboSyncSettings();
            when(koboSettingsService.getCurrentUserSettings()).thenReturn(settings);
            when(readingStateBuilder.buildEmptyBookmark(any(OffsetDateTime.class)))
                    .thenReturn(KoboReadingState.CurrentBookmark.builder().build());

            List<NewEntitlement> result = koboEntitlementService.generateNewEntitlements(Set.of(1L), "token1");

            assertEquals(1, result.size());
            assertNotNull(result.getFirst().getNewEntitlement());
            assertNotNull(result.getFirst().getNewEntitlement().getBookMetadata());
            assertEquals("Test EPUB Book 1", result.getFirst().getNewEntitlement().getBookMetadata().getTitle());
        }

        @Test
        @DisplayName("Should filter out unsupported books")
        void generateNewEntitlements_filterUnsupported() {
            BookEntity book = createEpubBookEntity(1L);

            when(bookQueryService.findAllWithMetadataByIds(Set.of(1L))).thenReturn(List.of(book));
            when(koboCompatibilityService.isBookSupportedForKobo(book)).thenReturn(false);

            List<NewEntitlement> result = koboEntitlementService.generateNewEntitlements(Set.of(1L), "token1");

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should prefer href-only web reader bookmark over existing state when two-way sync is on")
        void generateNewEntitlements_prefersHrefOnlyWebReaderBookmark() {
            BookEntity book = createEpubBookEntity(1L);
            book.setAddedOn(Instant.parse("2025-01-01T00:00:00Z"));
            book.getPrimaryBookFile().setId(10L);

            UserBookProgressEntity progress = new UserBookProgressEntity();
            progress.setBook(book);
            progress.setEpubProgressHref("OPS/chapter3.xhtml");
            progress.setEpubProgressPercent(54.6f);

            KoboReadingState.CurrentBookmark existingBookmark = KoboReadingState.CurrentBookmark.builder()
                    .progressPercent(12)
                    .build();
            KoboReadingState existingState = KoboReadingState.builder()
                    .entitlementId("1")
                    .currentBookmark(existingBookmark)
                    .build();
            KoboReadingStateEntity existingEntity = new KoboReadingStateEntity();

            KoboReadingState.CurrentBookmark webReaderBookmark = KoboReadingState.CurrentBookmark.builder()
                    .progressPercent(55)
                    .build();

            KoboSyncSettings settings = new KoboSyncSettings();
            settings.setTwoWayProgressSync(true);

            when(bookQueryService.findAllWithMetadataByIds(Set.of(1L))).thenReturn(List.of(book));
            when(koboCompatibilityService.isBookSupportedForKobo(book)).thenReturn(true);
            when(koboUrlBuilder.downloadUrl("token1", 1L)).thenReturn("http://test.com/download/1");
            when(appSettingService.getAppSettings()).thenReturn(createAppSettingsWithKoboSettings());
            when(koboSettingsService.getCurrentUserSettings()).thenReturn(settings);
            when(progressRepository.findByUserIdAndBookId(user.getId(), 1L)).thenReturn(Optional.of(progress));
            when(fileProgressRepository.findByUserIdAndBookFileId(user.getId(), 10L)).thenReturn(Optional.empty());
            when(readingStateRepository.findByEntitlementIdAndUserId("1", user.getId()))
                    .thenReturn(Optional.of(existingEntity));
            when(readingStateMapper.toDto(existingEntity)).thenReturn(existingState);
            when(readingStateBuilder.shouldUseWebReaderProgress(progress)).thenReturn(true);
            when(readingStateBuilder.buildBookmarkFromProgress(eq(progress), isNull(), any(OffsetDateTime.class)))
                    .thenReturn(webReaderBookmark);
            when(readingStateBuilder.buildStatusInfoFromProgress(eq(progress), anyString()))
                    .thenReturn(KoboReadingState.StatusInfo.builder()
                            .status(KoboReadStatus.READING)
                            .timesStartedReading(1)
                            .build());

            List<NewEntitlement> result = koboEntitlementService.generateNewEntitlements(Set.of(1L), "token1");

            assertEquals(1, result.size());
            assertEquals(55, result.getFirst().getNewEntitlement().getReadingState().getCurrentBookmark().getProgressPercent());
            verify(readingStateBuilder).buildBookmarkFromProgress(eq(progress), isNull(), any(OffsetDateTime.class));
        }

        @Test
        @DisplayName("Should keep stored Kobo bookmark when mirrored EPUB progress is not newer")
        void generateNewEntitlements_preservesStoredKoboBookmarkWhenKoboIsFreshest() {
            BookEntity book = createEpubBookEntity(1L);
            book.setAddedOn(Instant.parse("2025-01-01T00:00:00Z"));
            book.getPrimaryBookFile().setId(10L);

            UserBookProgressEntity progress = new UserBookProgressEntity();
            progress.setBook(book);
            progress.setKoboProgressPercent(55f);
            progress.setKoboLocation("kobo.12.18");
            progress.setKoboLocationType("KoboSpan");
            progress.setKoboLocationSource("OPS/chapter3.xhtml");
            progress.setKoboProgressReceivedTime(Instant.parse("2025-06-15T12:00:00Z"));
            progress.setEpubProgressPercent(55f);
            progress.setLastReadTime(Instant.parse("2025-06-15T12:00:00Z"));
            progress.setReadStatus(ReadStatus.READING);

            KoboReadingState.CurrentBookmark existingBookmark = KoboReadingState.CurrentBookmark.builder()
                    .progressPercent(55)
                    .contentSourceProgressPercent(23)
                    .location(KoboReadingState.CurrentBookmark.Location.builder()
                            .value("kobo.12.18")
                            .type("KoboSpan")
                            .source("OPS/chapter3.xhtml")
                            .build())
                    .lastModified("2025-06-15T12:00:00Z")
                    .build();
            KoboReadingState existingState = KoboReadingState.builder()
                    .entitlementId("1")
                    .currentBookmark(existingBookmark)
                    .build();
            KoboReadingStateEntity existingEntity = new KoboReadingStateEntity();

            KoboSyncSettings settings = new KoboSyncSettings();
            settings.setTwoWayProgressSync(true);

            when(bookQueryService.findAllWithMetadataByIds(Set.of(1L))).thenReturn(List.of(book));
            when(koboCompatibilityService.isBookSupportedForKobo(book)).thenReturn(true);
            when(koboUrlBuilder.downloadUrl("token1", 1L)).thenReturn("http://test.com/download/1");
            when(appSettingService.getAppSettings()).thenReturn(createAppSettingsWithKoboSettings());
            when(koboSettingsService.getCurrentUserSettings()).thenReturn(settings);
            when(progressRepository.findByUserIdAndBookId(user.getId(), 1L)).thenReturn(Optional.of(progress));
            when(fileProgressRepository.findByUserIdAndBookFileId(user.getId(), 10L)).thenReturn(Optional.empty());
            when(readingStateRepository.findByEntitlementIdAndUserId("1", user.getId()))
                    .thenReturn(Optional.of(existingEntity));
            when(readingStateMapper.toDto(existingEntity)).thenReturn(existingState);
            when(readingStateBuilder.shouldUseWebReaderProgress(progress)).thenReturn(false);
            when(readingStateBuilder.buildStatusInfoFromProgress(eq(progress), anyString()))
                    .thenReturn(KoboReadingState.StatusInfo.builder()
                            .status(KoboReadStatus.READING)
                            .timesStartedReading(1)
                            .build());

            List<NewEntitlement> result = koboEntitlementService.generateNewEntitlements(Set.of(1L), "token1");

            assertEquals(1, result.size());
            KoboReadingState.CurrentBookmark bookmark = result.getFirst().getNewEntitlement().getReadingState().getCurrentBookmark();
            assertEquals(55, bookmark.getProgressPercent());
            assertEquals(23, bookmark.getContentSourceProgressPercent());
            assertNotNull(bookmark.getLocation());
            assertEquals("kobo.12.18", bookmark.getLocation().getValue());
            assertEquals("OPS/chapter3.xhtml", bookmark.getLocation().getSource());
            verify(readingStateBuilder).shouldUseWebReaderProgress(progress);
            verify(readingStateBuilder, never()).buildBookmarkFromProgress(eq(progress), isNull(), any(OffsetDateTime.class));
        }

        @Test
        @DisplayName("Should handle empty book IDs")
        void generateNewEntitlements_emptyIds() {
            when(bookQueryService.findAllWithMetadataByIds(Set.of())).thenReturn(List.of());

            List<NewEntitlement> result = koboEntitlementService.generateNewEntitlements(Set.of(), "token1");

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Generate Changed Entitlements")
    class GenerateChangedEntitlements {

        @Test
        @DisplayName("Should generate removed entitlements with minimal metadata")
        void generateChangedEntitlements_removed() {
            BookEntity book = createEpubBookEntity(1L);

            when(bookQueryService.findAllWithMetadataByIds(Set.of(1L))).thenReturn(List.of(book));
            when(koboCompatibilityService.isBookSupportedForKobo(book)).thenReturn(true);

            List<? extends Entitlement> result = koboEntitlementService.generateChangedEntitlements(Set.of(1L), "token1", true);

            assertEquals(1, result.size());
            assertTrue(result.getFirst() instanceof ChangedEntitlement);
            ChangedEntitlement changed = (ChangedEntitlement) result.getFirst();
            assertTrue(changed.getChangedEntitlement().getBookEntitlement().getRemoved());
        }

        @Test
        @DisplayName("Should generate changed product metadata for non-removed EPUB books")
        void generateChangedEntitlements_changed() {
            BookEntity book = createEpubBookEntity(1L);

            when(bookQueryService.findAllWithMetadataByIds(Set.of(1L))).thenReturn(List.of(book));
            when(koboCompatibilityService.isBookSupportedForKobo(book)).thenReturn(true);
            when(koboUrlBuilder.downloadUrl("token1", 1L)).thenReturn("http://test.com/download/1");
            when(appSettingService.getAppSettings()).thenReturn(createAppSettingsWithKoboSettings());

            List<? extends Entitlement> result = koboEntitlementService.generateChangedEntitlements(Set.of(1L), "token1", false);

            assertEquals(1, result.size());
            assertTrue(result.getFirst() instanceof ChangedProductMetadata);
        }
    }

    @Nested
    @DisplayName("Generate Changed Reading States")
    class GenerateChangedReadingStates {

        @Test
        @DisplayName("Should generate changed reading states from progress entries")
        void generateChangedReadingStates_fromProgress() {
            BookEntity book = new BookEntity();
            book.setId(1L);
            BookFileEntity primaryFile = new BookFileEntity();
            primaryFile.setId(10L);
            primaryFile.setBook(book);
            primaryFile.setBookType(BookFileType.EPUB);
            book.setBookFiles(List.of(primaryFile));

            UserBookProgressEntity progress = new UserBookProgressEntity();
            progress.setBook(book);
            progress.setKoboProgressPercent(50f);
            progress.setReadStatus(ReadStatus.READING);

            KoboSyncSettings settings = new KoboSyncSettings();
            settings.setTwoWayProgressSync(true);
            when(koboSettingsService.getCurrentUserSettings()).thenReturn(settings);

            UserBookFileProgressEntity fileProgress = new UserBookFileProgressEntity();
            fileProgress.setBookFile(primaryFile);
            fileProgress.setContentSourceProgressPercent(20f);
            when(fileProgressRepository.findByUserIdAndBookFileIdIn(eq(1L), any())).thenReturn(List.of(fileProgress));

            KoboReadingState.CurrentBookmark bookmark = KoboReadingState.CurrentBookmark.builder()
                    .progressPercent(50)
                    .contentSourceProgressPercent(20)
                    .build();
            when(readingStateBuilder.buildBookmarkFromProgress(eq(progress), eq(fileProgress), any(OffsetDateTime.class), anyMap()))
                    .thenReturn(bookmark);
            when(readingStateBuilder.buildStatusInfoFromProgress(eq(progress), anyString()))
                    .thenReturn(KoboReadingState.StatusInfo.builder()
                            .status(KoboReadStatus.READING)
                            .timesStartedReading(1)
                            .build());

            List<ChangedReadingState> result = koboEntitlementService.generateChangedReadingStates(List.of(progress));

            assertEquals(1, result.size());
            assertNotNull(result.getFirst().getChangedReadingState());
            KoboReadingState state = result.getFirst().getChangedReadingState().getReadingState();
            assertEquals("1", state.getEntitlementId());
            assertNotNull(state.getCurrentBookmark());
            assertEquals(20, state.getCurrentBookmark().getContentSourceProgressPercent());
            assertNotNull(state.getStatusInfo());
        }

        @Test
        @DisplayName("Should use synced EPUB file progress when book has multiple files")
        void generateChangedReadingStates_usesSyncedEpubFileProgress() {
            BookEntity book = new BookEntity();
            book.setId(1L);

            BookFileEntity primaryEpub = new BookFileEntity();
            primaryEpub.setId(10L);
            primaryEpub.setBook(book);
            primaryEpub.setBookType(BookFileType.EPUB);

            BookFileEntity alternateAudiobook = new BookFileEntity();
            alternateAudiobook.setId(20L);
            alternateAudiobook.setBook(book);
            alternateAudiobook.setBookType(BookFileType.AUDIOBOOK);

            book.setBookFiles(List.of(primaryEpub, alternateAudiobook));

            UserBookProgressEntity progress = new UserBookProgressEntity();
            progress.setBook(book);
            progress.setEpubProgressPercent(70f);
            progress.setReadStatus(ReadStatus.READING);

            KoboSyncSettings settings = new KoboSyncSettings();
            settings.setTwoWayProgressSync(true);
            when(koboSettingsService.getCurrentUserSettings()).thenReturn(settings);

            UserBookFileProgressEntity epubFileProgress = new UserBookFileProgressEntity();
            epubFileProgress.setBookFile(primaryEpub);
            epubFileProgress.setContentSourceProgressPercent(21f);

            UserBookFileProgressEntity audiobookProgress = new UserBookFileProgressEntity();
            audiobookProgress.setBookFile(alternateAudiobook);
            audiobookProgress.setContentSourceProgressPercent(99f);

            when(fileProgressRepository.findByUserIdAndBookFileIdIn(eq(1L), any()))
                    .thenReturn(List.of(epubFileProgress, audiobookProgress));

            KoboReadingState.CurrentBookmark bookmark = KoboReadingState.CurrentBookmark.builder()
                    .progressPercent(70)
                    .contentSourceProgressPercent(21)
                    .build();
            when(readingStateBuilder.buildBookmarkFromProgress(eq(progress), eq(epubFileProgress), any(OffsetDateTime.class), anyMap()))
                    .thenReturn(bookmark);
            when(readingStateBuilder.buildStatusInfoFromProgress(eq(progress), anyString()))
                    .thenReturn(KoboReadingState.StatusInfo.builder()
                            .status(KoboReadStatus.READING)
                            .timesStartedReading(1)
                            .build());

            List<ChangedReadingState> result = koboEntitlementService.generateChangedReadingStates(List.of(progress));

            assertEquals(1, result.size());
            assertEquals(21, result.getFirst().getChangedReadingState().getReadingState()
                    .getCurrentBookmark().getContentSourceProgressPercent());
            verify(readingStateBuilder).buildBookmarkFromProgress(eq(progress), eq(epubFileProgress), any(OffsetDateTime.class), anyMap());
        }

        @Test
        @DisplayName("Should use empty bookmark when no progress data exists")
        void generateChangedReadingStates_emptyBookmark() {
            BookEntity book = new BookEntity();
            book.setId(1L);

            UserBookProgressEntity progress = new UserBookProgressEntity();
            progress.setBook(book);
            progress.setReadStatus(ReadStatus.UNREAD);

            KoboSyncSettings settings = new KoboSyncSettings();
            when(koboSettingsService.getCurrentUserSettings()).thenReturn(settings);

            KoboReadingState.CurrentBookmark emptyBookmark = KoboReadingState.CurrentBookmark.builder().build();
            when(readingStateBuilder.buildEmptyBookmark(any(OffsetDateTime.class))).thenReturn(emptyBookmark);
            when(readingStateBuilder.buildStatusInfoFromProgress(eq(progress), anyString()))
                    .thenReturn(KoboReadingState.StatusInfo.builder()
                            .status(KoboReadStatus.READY_TO_READ)
                            .timesStartedReading(0)
                            .build());

            List<ChangedReadingState> result = koboEntitlementService.generateChangedReadingStates(List.of(progress));

            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("Should include epub progress in bookmark when two-way sync ON")
        void generateChangedReadingStates_twoWaySync() {
            BookEntity book = new BookEntity();
            book.setId(1L);

            UserBookProgressEntity progress = new UserBookProgressEntity();
            progress.setBook(book);
            progress.setEpubProgressPercent(70f);
            progress.setReadStatus(ReadStatus.READING);

            KoboSyncSettings settings = new KoboSyncSettings();
            settings.setTwoWayProgressSync(true);
            when(koboSettingsService.getCurrentUserSettings()).thenReturn(settings);

            KoboReadingState.CurrentBookmark bookmark = KoboReadingState.CurrentBookmark.builder()
                    .progressPercent(70)
                    .build();
            when(readingStateBuilder.buildBookmarkFromProgress(eq(progress), isNull(), any(OffsetDateTime.class), anyMap()))
                    .thenReturn(bookmark);
            when(readingStateBuilder.buildStatusInfoFromProgress(eq(progress), anyString()))
                    .thenReturn(KoboReadingState.StatusInfo.builder()
                            .status(KoboReadStatus.READING)
                            .timesStartedReading(1)
                            .build());

            List<ChangedReadingState> result = koboEntitlementService.generateChangedReadingStates(List.of(progress));

            assertEquals(1, result.size());
            assertEquals(70, result.getFirst().getChangedReadingState().getReadingState()
                    .getCurrentBookmark().getProgressPercent());
        }

        @Test
        @DisplayName("Should NOT include epub progress in bookmark when two-way sync OFF")
        void generateChangedReadingStates_twoWaySyncOff() {
            BookEntity book = new BookEntity();
            book.setId(1L);

            UserBookProgressEntity progress = new UserBookProgressEntity();
            progress.setBook(book);
            progress.setKoboProgressPercent(null);
            progress.setEpubProgressPercent(70f);
            progress.setReadStatus(ReadStatus.READING);

            KoboSyncSettings settings = new KoboSyncSettings();
            settings.setTwoWayProgressSync(false);
            when(koboSettingsService.getCurrentUserSettings()).thenReturn(settings);

            KoboReadingState.CurrentBookmark emptyBookmark = KoboReadingState.CurrentBookmark.builder().build();
            when(readingStateBuilder.buildEmptyBookmark(any(OffsetDateTime.class))).thenReturn(emptyBookmark);
            when(readingStateBuilder.buildStatusInfoFromProgress(eq(progress), anyString()))
                    .thenReturn(KoboReadingState.StatusInfo.builder()
                            .status(KoboReadStatus.READING)
                            .timesStartedReading(1)
                            .build());

            List<ChangedReadingState> result = koboEntitlementService.generateChangedReadingStates(List.of(progress));

            assertEquals(1, result.size());
            verify(readingStateBuilder).buildEmptyBookmark(any(OffsetDateTime.class));
        }
    }
}
