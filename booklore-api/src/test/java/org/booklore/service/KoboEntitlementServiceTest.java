package org.booklore.service;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.kobo.KoboBookMetadata;
import org.booklore.model.dto.kobo.KoboTag;
import org.booklore.model.dto.kobo.KoboTagWrapper;
import org.booklore.model.dto.settings.KoboSettings;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.MagicShelfEntity;
import org.booklore.model.entity.ShelfEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.KoboBookFormat;
import org.booklore.model.enums.ShelfType;
import org.booklore.repository.MagicShelfRepository;
import org.booklore.repository.ShelfRepository;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.book.BookQueryService;
import org.booklore.service.kobo.KoboCompatibilityService;
import org.booklore.service.kobo.KoboEntitlementService;
import org.booklore.service.opds.MagicShelfBookService;
import org.booklore.util.kobo.KoboUrlBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.PageImpl;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
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

    @InjectMocks
    private KoboEntitlementService koboEntitlementService;

    private BookLoreUser user;

    @BeforeEach
    void setUp() {
        BookLoreUser.UserPermissions permissions = new BookLoreUser.UserPermissions();
        user = BookLoreUser.builder().permissions(permissions).build();
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
                .convertToKepub(false)
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
        when(magicShelfBookService.getBooksByMagicShelfId(eq(user.getId()), eq(201L), eq(0), eq(Integer.MAX_VALUE)))
                .thenReturn(new PageImpl<>(List.of(Book.builder().id(2L).build())));

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
        when(magicShelfBookService.getBooksByMagicShelfId(eq(user.getId()), eq(201L), eq(0), eq(Integer.MAX_VALUE)))
                .thenReturn(new PageImpl<>(List.of(Book.builder().id(1L).build())));

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

}