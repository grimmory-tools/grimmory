package com.adityachandel.booklore.service.metadata;

import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.model.MetadataUpdateWrapper;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.request.FetchMetadataRequest;
import com.adityachandel.booklore.model.dto.request.MetadataRefreshOptions;
import com.adityachandel.booklore.model.dto.request.MetadataRefreshRequest;
import com.adityachandel.booklore.model.dto.settings.AppSettings;
import com.adityachandel.booklore.model.entity.*;
import com.adityachandel.booklore.model.enums.MetadataProvider;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.repository.MetadataFetchJobRepository;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.metadata.parser.BookParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetadataRefreshServiceTest {

    @Mock
    private LibraryRepository libraryRepository;
    @Mock
    private MetadataFetchJobRepository metadataFetchJobRepository;
    @Mock
    private BookMapper bookMapper;
    @Mock
    private BookMetadataUpdater bookMetadataUpdater;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AppSettingService appSettingService;
    @Mock
    private Map<MetadataProvider, BookParser> parserMap;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private BookParser goodreadsParser;
    @Mock
    private BookParser googleParser;
    @Mock
    private BookParser hardcoverParser;

    @InjectMocks
    private MetadataRefreshService metadataRefreshService;

    private AppSettings appSettings;
    private MetadataRefreshOptions defaultOptions;
    private MetadataRefreshOptions libraryOptions;
    private BookEntity testBook;
    private LibraryEntity testLibrary;

    @BeforeEach
    void setUp() {
        setupDefaultOptions();
        setupLibraryOptions();
        setupAppSettings();
        setupTestEntities();
    }

    private void setupDefaultOptions() {
        MetadataRefreshOptions.FieldProvider titleProvider = MetadataRefreshOptions.FieldProvider.builder()
                .p3(MetadataProvider.Google)
                .p1(MetadataProvider.GoodReads)
                .build();
        MetadataRefreshOptions.FieldProvider descriptionProvider = MetadataRefreshOptions.FieldProvider.builder()
                .p1(MetadataProvider.Google)
                .build();
        MetadataRefreshOptions.FieldProvider authorsProvider = MetadataRefreshOptions.FieldProvider.builder()
                .p1(MetadataProvider.GoodReads)
                .build();
        MetadataRefreshOptions.FieldProvider categoriesProvider = MetadataRefreshOptions.FieldProvider.builder()
                .p1(MetadataProvider.Google)
                .build();
        MetadataRefreshOptions.FieldProvider moodProvider = MetadataRefreshOptions.FieldProvider.builder()
                .p1(MetadataProvider.Google)
                .build();
        MetadataRefreshOptions.FieldProvider tagProvider = MetadataRefreshOptions.FieldProvider.builder()
                .p1(MetadataProvider.Google)
                .build();
        MetadataRefreshOptions.FieldProvider coverProvider = MetadataRefreshOptions.FieldProvider.builder()
                .p1(MetadataProvider.GoodReads)
                .build();

        MetadataRefreshOptions.FieldOptions fieldOptions = MetadataRefreshOptions.FieldOptions.builder()
                .title(titleProvider)
                .description(descriptionProvider)
                .authors(authorsProvider)
                .categories(categoriesProvider)
                .moods(moodProvider)
                .tags(tagProvider)
                .cover(coverProvider)
                .build();

        MetadataRefreshOptions.SkipFields skipFields = MetadataRefreshOptions.SkipFields.builder().build();

        defaultOptions = MetadataRefreshOptions.builder()
                .refreshCovers(true)
                .mergeCategories(false)
                .reviewBeforeApply(false)
                .fieldOptions(fieldOptions)
                .skipFields(skipFields)
                .build();
    }

    private void setupLibraryOptions() {
        MetadataRefreshOptions.FieldProvider titleProvider = MetadataRefreshOptions.FieldProvider.builder()
                .p1(MetadataProvider.Google)
                .build();

        MetadataRefreshOptions.FieldOptions fieldOptions = MetadataRefreshOptions.FieldOptions.builder()
                .title(titleProvider)
                .build();

        MetadataRefreshOptions.SkipFields skipFields = MetadataRefreshOptions.SkipFields.builder().build();

        libraryOptions = MetadataRefreshOptions.builder()
                .libraryId(1L)
                .refreshCovers(false)
                .mergeCategories(true)
                .reviewBeforeApply(true)
                .fieldOptions(fieldOptions)
                .skipFields(skipFields)
                .build();
    }

    private void setupAppSettings() {
        appSettings = AppSettings.builder()
                .defaultMetadataRefreshOptions(defaultOptions)
                .libraryMetadataRefreshOptions(List.of(libraryOptions))
                .build();
    }

    private void setupTestEntities() {
        testLibrary = new LibraryEntity();
        testLibrary.setId(1L);
        testLibrary.setName("Test Library");

        // Create AuthorEntity for proper type compatibility
        AuthorEntity authorEntity = new AuthorEntity();
        authorEntity.setName("Test Author");

        BookMetadataEntity metadata = new BookMetadataEntity();
        metadata.setTitle("Test Book");
        metadata.setAuthors(Set.of(authorEntity));

        testBook = new BookEntity();
        testBook.setId(1L);
        testBook.setFileName("test-book.epub");
        testBook.setLibrary(testLibrary);
        testBook.setMetadata(metadata);
    }

    private void setupBasicMocks() {
        when(appSettingService.getAppSettings()).thenReturn(appSettings);
    }

    private void setupBookRepositoryMocks() {
        when(bookRepository.findAllWithMetadataByIds(Set.of(1L))).thenReturn(List.of(testBook));
    }

    private void setupParserMocksForGoodreadsAndGoogle() {
        BookMetadata goodreadsMetadata = BookMetadata.builder()
                .provider(MetadataProvider.GoodReads)
                .title("Goodreads Title")
                .authors(Set.of("Author 1"))
                .build();

        BookMetadata googleMetadata = BookMetadata.builder()
                .provider(MetadataProvider.Google)
                .description("Google Description")
                .categories(Set.of("Fiction"))
                .build();

        when(parserMap.get(MetadataProvider.GoodReads)).thenReturn(goodreadsParser);
        when(parserMap.get(MetadataProvider.Google)).thenReturn(googleParser);

        when(goodreadsParser.fetchTopMetadata(any(Book.class), any(FetchMetadataRequest.class)))
                .thenReturn(null);
        when(googleParser.fetchTopMetadata(any(Book.class), any(FetchMetadataRequest.class)))
                .thenReturn(googleMetadata);

        Book book = Book.builder()
                .id(1L)
                .fileName("test-book.epub")
                .metadata(BookMetadata.builder().title("Test Book").authors(Set.of("Test Author")).build())
                .build();
        when(bookMapper.toBook(testBook)).thenReturn(book);
    }

    private void setupParserMocksForGoogle() {
        BookMetadata googleMetadata = BookMetadata.builder()
                .provider(MetadataProvider.Google)
                .title("Google Title")
                .description("Google Description")
                .categories(Set.of("Fiction"))
                .build();

        when(parserMap.get(MetadataProvider.Google)).thenReturn(googleParser);
        when(googleParser.fetchTopMetadata(any(Book.class), any(FetchMetadataRequest.class)))
                .thenReturn(googleMetadata);

        Book book = Book.builder()
                .id(1L)
                .fileName("test-book.epub")
                .metadata(BookMetadata.builder().title("Test Book").authors(Set.of("Test Author")).build())
                .build();
        when(bookMapper.toBook(testBook)).thenReturn(book);
    }

    private void setupParserMocksForHardcover() {
        BookMetadata hardcoverMetadata = BookMetadata.builder()
                .provider(MetadataProvider.Hardcover)
                .title("Hardcover Title")
                .build();

        when(parserMap.get(MetadataProvider.Hardcover)).thenReturn(hardcoverParser);
        when(hardcoverParser.fetchTopMetadata(any(Book.class), any(FetchMetadataRequest.class)))
                .thenReturn(hardcoverMetadata);

        Book book = Book.builder()
                .id(1L)
                .fileName("test-book.epub")
                .metadata(BookMetadata.builder().title("Test Book").authors(Set.of("Test Author")).build())
                .build();
        when(bookMapper.toBook(testBook)).thenReturn(book);
    }

    @Test
    void testRefreshMetadata_WithRequestOptions_ShouldUseRequestOptions() {
        // Given
        MetadataRefreshOptions.FieldProvider titleProvider = MetadataRefreshOptions.FieldProvider.builder()
                .p1(MetadataProvider.Hardcover)
                .build();
        MetadataRefreshOptions.FieldOptions fieldOptions = MetadataRefreshOptions.FieldOptions.builder()
                .title(titleProvider)
                .build();
        MetadataRefreshOptions.SkipFields skipFields = MetadataRefreshOptions.SkipFields.builder().build();

        MetadataRefreshOptions requestOptions = MetadataRefreshOptions.builder()
                .refreshCovers(true)
                .mergeCategories(false)
                .reviewBeforeApply(false)
                .fieldOptions(fieldOptions)
                .skipFields(skipFields)
                .build();

        MetadataRefreshRequest request = MetadataRefreshRequest.builder()
                .refreshType(MetadataRefreshRequest.RefreshType.BOOKS)
                .bookIds(Set.of(1L))
                .refreshOptions(requestOptions)
                .build();

        setupBasicMocks();
        setupBookRepositoryMocks();
        setupParserMocksForHardcover();

        metadataRefreshService.refreshMetadata(request, 1L, "job-1");

        verify(bookRepository).findAllWithMetadataByIds(Set.of(1L));
    }

    @Test
    void testRefreshMetadata_LibraryRefresh_ShouldUseLibraryOptions() {
        MetadataRefreshRequest request = MetadataRefreshRequest.builder()
                .refreshType(MetadataRefreshRequest.RefreshType.LIBRARY)
                .libraryId(1L)
                .build();

        setupBasicMocks();
        when(libraryRepository.findById(1L)).thenReturn(Optional.of(testLibrary));
        when(bookRepository.findBookIdsByLibraryId(1L)).thenReturn(Set.of(1L));
        setupBookRepositoryMocks();
        setupParserMocksForGoogle();

        metadataRefreshService.refreshMetadata(request, 1L, "job-1");

        verify(libraryRepository).findById(1L);
        verify(bookRepository).findBookIdsByLibraryId(1L);
        verify(bookRepository).findAllWithMetadataByIds(Set.of(1L));
    }

    @Test
    void testRefreshMetadata_BookRefresh_ShouldUsePerBookLibraryOptions() {
        MetadataRefreshRequest request = MetadataRefreshRequest.builder()
                .refreshType(MetadataRefreshRequest.RefreshType.BOOKS)
                .bookIds(Set.of(1L))
                .build();

        setupBasicMocks();
        setupBookRepositoryMocks();
        setupParserMocksForGoogle();

        metadataRefreshService.refreshMetadata(request, 1L, "job-1");

        verify(bookRepository).findAllWithMetadataByIds(Set.of(1L));
    }

    @Test
    void testRefreshMetadata_WithReviewMode_ShouldCreateTaskAndProposals() throws JsonProcessingException {
        MetadataRefreshOptions.SkipFields skipFields = MetadataRefreshOptions.SkipFields.builder().build();

        MetadataRefreshOptions reviewOptions = MetadataRefreshOptions.builder()
                .refreshCovers(true)
                .mergeCategories(false)
                .reviewBeforeApply(true)
                .fieldOptions(defaultOptions.getFieldOptions())
                .skipFields(skipFields)
                .build();

        MetadataRefreshRequest request = MetadataRefreshRequest.builder()
                .refreshType(MetadataRefreshRequest.RefreshType.BOOKS)
                .bookIds(Set.of(1L))
                .refreshOptions(reviewOptions)
                .build();

        setupBasicMocks();
        setupBookRepositoryMocks();
        setupParserMocksForGoodreadsAndGoogle();
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        MetadataFetchJobEntity savedTask = new MetadataFetchJobEntity();
        when(metadataFetchJobRepository.save(any(MetadataFetchJobEntity.class))).thenReturn(savedTask);

        metadataRefreshService.refreshMetadata(request, 1L, "job-1");

        ArgumentCaptor<MetadataFetchJobEntity> taskCaptor = ArgumentCaptor.forClass(MetadataFetchJobEntity.class);
        verify(metadataFetchJobRepository, atLeast(1)).save(taskCaptor.capture());

        MetadataFetchJobEntity capturedTask = taskCaptor.getValue();
        assertNotNull(capturedTask);
        verify(objectMapper).writeValueAsString(any(BookMetadata.class));
    }

    @Test
    void testRefreshMetadata_LockedBook_ShouldSkip() {
        BookMetadataEntity lockedMetadata = spy(testBook.getMetadata());
        when(lockedMetadata.areAllFieldsLocked()).thenReturn(true);
        testBook.setMetadata(lockedMetadata);

        MetadataRefreshRequest request = MetadataRefreshRequest.builder()
                .refreshType(MetadataRefreshRequest.RefreshType.BOOKS)
                .bookIds(Set.of(1L))
                .build();

        setupBasicMocks();
        setupBookRepositoryMocks();

        metadataRefreshService.refreshMetadata(request, 1L, "job-1");
    }

    @Test
    void testRefreshMetadata_BookNotFound_ShouldThrowException() {
        MetadataRefreshRequest request = MetadataRefreshRequest.builder()
                .refreshType(MetadataRefreshRequest.RefreshType.BOOKS)
                .bookIds(Set.of(999L))
                .build();

        setupBasicMocks();
        when(bookRepository.findAllWithMetadataByIds(Set.of(999L))).thenReturn(Collections.emptyList());

        assertThrows(RuntimeException.class, () ->
                metadataRefreshService.refreshMetadata(request, 1L, "job-1"));
    }

    @Test
    void testRefreshMetadata_LibraryNotFound_ShouldThrowException() {
        MetadataRefreshRequest request = MetadataRefreshRequest.builder()
                .refreshType(MetadataRefreshRequest.RefreshType.LIBRARY)
                .libraryId(999L)
                .build();

        setupBasicMocks();
        when(libraryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () ->
                metadataRefreshService.refreshMetadata(request, 1L, "job-1"));
    }

    @Test
    void testResolveMetadataRefreshOptions_WithLibraryId_ShouldReturnLibraryOptions() {
        MetadataRefreshOptions result = metadataRefreshService.resolveMetadataRefreshOptions(1L, appSettings);

        assertEquals(libraryOptions, result);
        assertTrue(result.getReviewBeforeApply());
        assertFalse(result.isRefreshCovers());
        assertTrue(result.isMergeCategories());
    }

    @Test
    void testResolveMetadataRefreshOptions_WithoutLibraryId_ShouldReturnDefaultOptions() {
        MetadataRefreshOptions result = metadataRefreshService.resolveMetadataRefreshOptions(null, appSettings);

        assertEquals(defaultOptions, result);
        assertFalse(result.getReviewBeforeApply());
        assertTrue(result.isRefreshCovers());
        assertFalse(result.isMergeCategories());
    }

    @Test
    void testResolveMetadataRefreshOptions_NonExistentLibrary_ShouldReturnDefaultOptions() {
        MetadataRefreshOptions result = metadataRefreshService.resolveMetadataRefreshOptions(999L, appSettings);

        assertEquals(defaultOptions, result);
    }

    @Test
    void testPrepareProviders_ShouldReturnUniqueProviders() {
        List<MetadataProvider> providers = metadataRefreshService.prepareProviders(defaultOptions);

        assertNotNull(providers);
        assertTrue(providers.contains(MetadataProvider.GoodReads));
        assertTrue(providers.contains(MetadataProvider.Google));
        assertEquals(2, providers.size());
    }

    @Test
    void testFetchMetadataForBook_ShouldReturnMetadataMap() {
        Book book = Book.builder()
                .id(1L)
                .fileName("test.epub")
                .metadata(BookMetadata.builder().title("Test").build())
                .build();

        BookMetadata goodreadsMetadata = BookMetadata.builder()
                .provider(MetadataProvider.GoodReads)
                .title("Goodreads Title")
                .build();

        when(parserMap.get(MetadataProvider.GoodReads)).thenReturn(goodreadsParser);
        when(goodreadsParser.fetchTopMetadata(eq(book), any(FetchMetadataRequest.class)))
                .thenReturn(goodreadsMetadata);

        List<MetadataProvider> providers = List.of(MetadataProvider.GoodReads);

        Map<MetadataProvider, BookMetadata> result = metadataRefreshService.fetchMetadataForBook(providers, book);

        assertEquals(1, result.size());
        assertEquals(goodreadsMetadata, result.get(MetadataProvider.GoodReads));
    }

    @Test
    void testBuildFetchMetadata_ShouldCombineMetadataCorrectly() {
        Map<MetadataProvider, BookMetadata> metadataMap = new HashMap<>();
        metadataMap.put(MetadataProvider.GoodReads, BookMetadata.builder()
                .title("Goodreads Title")
                .authors(Set.of("Author 1"))
                .goodreadsId("gr123")
                .build());
        metadataMap.put(MetadataProvider.Google, BookMetadata.builder()
                .description("Google Description")
                .categories(Set.of("Fiction"))
                .googleId("google123")
                .build());

        BookMetadata result = metadataRefreshService.buildFetchMetadata(1L, defaultOptions, metadataMap);

        assertEquals("Goodreads Title", result.getTitle());
        assertEquals("Google Description", result.getDescription());
        assertEquals(Set.of("Author 1"), result.getAuthors());
        assertEquals(Set.of("Fiction"), result.getCategories());
        assertEquals("gr123", result.getGoodreadsId());
        assertEquals("google123", result.getGoogleId());
    }

    @Test
    void testBuildFetchMetadata_WithMergeCategories_ShouldMergeAllCategories() {
        MetadataRefreshOptions.FieldProvider titleProvider = MetadataRefreshOptions.FieldProvider.builder()
                .p1(MetadataProvider.Google)
                .build();
        MetadataRefreshOptions.FieldProvider descriptionProvider = MetadataRefreshOptions.FieldProvider.builder()
                .p1(MetadataProvider.Google)
                .build();
        MetadataRefreshOptions.FieldProvider authorsProvider = MetadataRefreshOptions.FieldProvider.builder()
                .p1(MetadataProvider.Google)
                .build();
        MetadataRefreshOptions.FieldProvider moodProvider = MetadataRefreshOptions.FieldProvider.builder()
                .p1(MetadataProvider.Google)
                .build();
        MetadataRefreshOptions.FieldProvider tagProvider = MetadataRefreshOptions.FieldProvider.builder()
                .p1(MetadataProvider.Google)
                .build();
        MetadataRefreshOptions.FieldProvider categoriesProvider = MetadataRefreshOptions.FieldProvider.builder()
                .p3(MetadataProvider.Google)
                .p1(MetadataProvider.GoodReads)
                .build();
        MetadataRefreshOptions.FieldProvider coverProvider = MetadataRefreshOptions.FieldProvider.builder()
                .p1(MetadataProvider.Google)
                .build();

        MetadataRefreshOptions.FieldOptions fieldOptions = MetadataRefreshOptions.FieldOptions.builder()
                .title(titleProvider)
                .description(descriptionProvider)
                .authors(authorsProvider)
                .categories(categoriesProvider)
                .moods(moodProvider)
                .tags(tagProvider)
                .cover(coverProvider)
                .build();

        MetadataRefreshOptions.SkipFields skipFields = MetadataRefreshOptions.SkipFields.builder().build();

        MetadataRefreshOptions mergeOptions = MetadataRefreshOptions.builder()
                .refreshCovers(true)
                .mergeCategories(true)
                .reviewBeforeApply(false)
                .fieldOptions(fieldOptions)
                .skipFields(skipFields)
                .build();

        Map<MetadataProvider, BookMetadata> metadataMap = new HashMap<>();
        metadataMap.put(MetadataProvider.GoodReads, BookMetadata.builder()
                .categories(Set.of("Fiction", "Drama"))
                .build());
        metadataMap.put(MetadataProvider.Google, BookMetadata.builder()
                .categories(Set.of("Literature", "Fiction"))
                .build());

        BookMetadata result = metadataRefreshService.buildFetchMetadata(1L, mergeOptions, metadataMap);

        assertNotNull(result.getCategories());
        Set<String> expectedCategories = Set.of("Fiction", "Drama", "Literature");

        assertEquals(3, result.getCategories().size(), "Should have 3 unique categories when merging");
        assertTrue(result.getCategories().containsAll(expectedCategories), "Should contain all expected categories");
    }

    @Test
    void testGetBookEntities_WithLibraryRefresh_ShouldReturnLibraryBooks() {
        MetadataRefreshRequest request = MetadataRefreshRequest.builder()
                .refreshType(MetadataRefreshRequest.RefreshType.LIBRARY)
                .libraryId(1L)
                .build();

        when(libraryRepository.findById(1L)).thenReturn(Optional.of(testLibrary));
        when(bookRepository.findBookIdsByLibraryId(1L)).thenReturn(Set.of(1L, 2L, 3L));

        Set<Long> result = metadataRefreshService.getBookEntities(request);

        assertEquals(Set.of(1L, 2L, 3L), result);
    }

    @Test
    void testGetBookEntities_WithBooksRefresh_ShouldReturnRequestedBooks() {
        MetadataRefreshRequest request = MetadataRefreshRequest.builder()
                .refreshType(MetadataRefreshRequest.RefreshType.BOOKS)
                .bookIds(Set.of(1L, 2L))
                .build();

        Set<Long> result = metadataRefreshService.getBookEntities(request);

        assertEquals(Set.of(1L, 2L), result);
    }

    @Test
    void testUpdateBookMetadata_ShouldCallUpdaterAndNotification() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Updated Title")
                .build();
        Book book = Book.builder().id(1L).build();

        when(bookMapper.toBook(testBook)).thenReturn(book);

        metadataRefreshService.updateBookMetadata(testBook, metadata, true, false);

        verify(bookMetadataUpdater).setBookMetadata(eq(testBook), any(MetadataUpdateWrapper.class), eq(true), eq(false));
        verify(notificationService).sendMessage(eq(Topic.BOOK_METADATA_UPDATE), eq(book));
    }
}
