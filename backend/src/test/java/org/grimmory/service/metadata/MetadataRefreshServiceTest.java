package org.grimmory.service.metadata;

import org.grimmory.config.security.service.AuthenticationService;
import org.grimmory.mapper.BookMapper;
import org.grimmory.model.dto.*;
import org.grimmory.model.dto.request.FetchMetadataRequest;
import org.grimmory.model.dto.request.MetadataRefreshOptions;
import org.grimmory.model.dto.request.MetadataRefreshRequest;
import org.grimmory.model.dto.settings.AppSettings;
import org.grimmory.model.dto.settings.MetadataProviderSettings;
import org.grimmory.model.MetadataUpdateContext;
import org.grimmory.model.MetadataUpdateWrapper;
import org.grimmory.model.entity.BookEntity;
import org.grimmory.model.entity.BookMetadataEntity;
import org.grimmory.model.entity.LibraryEntity;
import org.grimmory.model.entity.MetadataFetchJobEntity;
import org.grimmory.model.enums.MetadataProvider;
import org.grimmory.model.enums.MetadataReplaceMode;
import org.grimmory.repository.BookRepository;
import org.grimmory.repository.LibraryRepository;
import org.grimmory.repository.MetadataFetchJobRepository;
import org.grimmory.service.NotificationService;
import org.grimmory.service.appsettings.AppSettingService;
import org.grimmory.service.metadata.parser.BookParser;
import org.grimmory.task.TaskCancellationManager;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetadataRefreshServiceTest {

    @Mock private LibraryRepository libraryRepository;
    @Mock private MetadataFetchJobRepository metadataFetchJobRepository;
    @Mock private BookMapper bookMapper;
    @Mock private BookMetadataUpdater bookMetadataUpdater;
    @Mock private NotificationService notificationService;
    @Mock private AppSettingService appSettingService;
    @Mock private Map<MetadataProvider, BookParser> parserMap;
    @Mock private ObjectMapper objectMapper;
    @Mock private BookRepository bookRepository;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private AuthenticationService authenticationService;
    @Mock private TaskCancellationManager cancellationManager;

    @InjectMocks
    private MetadataRefreshService service;

    @Test
    void resolveMetadataRefreshOptions_returnsLibrarySpecificWhenMatched() {
        MetadataRefreshOptions defaultOpts = MetadataRefreshOptions.builder().refreshCovers(false).build();
        MetadataRefreshOptions libraryOpts = MetadataRefreshOptions.builder().libraryId(5L).refreshCovers(true).build();

        AppSettings settings = AppSettings.builder()
                .defaultMetadataRefreshOptions(defaultOpts)
                .libraryMetadataRefreshOptions(List.of(libraryOpts))
                .build();

        MetadataRefreshOptions result = service.resolveMetadataRefreshOptions(5L, settings);

        assertThat(result).isSameAs(libraryOpts);
    }

    @Test
    void resolveMetadataRefreshOptions_returnsDefaultWhenNoLibraryMatch() {
        MetadataRefreshOptions defaultOpts = MetadataRefreshOptions.builder().refreshCovers(false).build();
        MetadataRefreshOptions libraryOpts = MetadataRefreshOptions.builder().libraryId(5L).build();

        AppSettings settings = AppSettings.builder()
                .defaultMetadataRefreshOptions(defaultOpts)
                .libraryMetadataRefreshOptions(List.of(libraryOpts))
                .build();

        MetadataRefreshOptions result = service.resolveMetadataRefreshOptions(99L, settings);

        assertThat(result).isSameAs(defaultOpts);
    }

    @Test
    void resolveMetadataRefreshOptions_returnsDefaultWhenLibraryIdIsNull() {
        MetadataRefreshOptions defaultOpts = MetadataRefreshOptions.builder().build();

        AppSettings settings = AppSettings.builder()
                .defaultMetadataRefreshOptions(defaultOpts)
                .libraryMetadataRefreshOptions(List.of())
                .build();

        MetadataRefreshOptions result = service.resolveMetadataRefreshOptions(null, settings);

        assertThat(result).isSameAs(defaultOpts);
    }

    @Test
    void resolveMetadataRefreshOptions_returnsDefaultWhenLibraryOptionsAreNull() {
        MetadataRefreshOptions defaultOpts = MetadataRefreshOptions.builder().build();

        AppSettings settings = AppSettings.builder()
                .defaultMetadataRefreshOptions(defaultOpts)
                .libraryMetadataRefreshOptions(null)
                .build();

        MetadataRefreshOptions result = service.resolveMetadataRefreshOptions(5L, settings);

        assertThat(result).isSameAs(defaultOpts);
    }

    @Test
    void prepareProviders_collectsUniqueProvidersFromFieldOptions() {
        MetadataRefreshOptions.FieldProvider titleProvider =
                MetadataRefreshOptions.FieldProvider.builder().p1(MetadataProvider.Amazon).p2(MetadataProvider.Google).build();
        MetadataRefreshOptions.FieldProvider descProvider =
                MetadataRefreshOptions.FieldProvider.builder().p1(MetadataProvider.Amazon).p2(MetadataProvider.GoodReads).build();

        MetadataRefreshOptions options = MetadataRefreshOptions.builder()
                .fieldOptions(MetadataRefreshOptions.FieldOptions.builder()
                        .title(titleProvider)
                        .description(descProvider)
                        .build())
                .build();

        MetadataProviderSettings providerSettings = new MetadataProviderSettings();
        MetadataProviderSettings.Amazon amazon = new MetadataProviderSettings.Amazon();
        amazon.setEnabled(true);
        providerSettings.setAmazon(amazon);
        MetadataProviderSettings.Google google = new MetadataProviderSettings.Google();
        google.setEnabled(true);
        providerSettings.setGoogle(google);
        MetadataProviderSettings.Goodreads goodreads = new MetadataProviderSettings.Goodreads();
        goodreads.setEnabled(true);
        providerSettings.setGoodReads(goodreads);

        AppSettings appSettings = AppSettings.builder().metadataProviderSettings(providerSettings).build();
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        List<MetadataProvider> result = service.prepareProviders(options);

        assertThat(result).containsExactlyInAnyOrder(MetadataProvider.Amazon, MetadataProvider.Google, MetadataProvider.GoodReads);
    }

    @Test
    void prepareProviders_excludesDisabledProviders() {
        MetadataRefreshOptions.FieldProvider titleProvider =
                MetadataRefreshOptions.FieldProvider.builder().p1(MetadataProvider.Amazon).p2(MetadataProvider.Google).build();

        MetadataRefreshOptions options = MetadataRefreshOptions.builder()
                .fieldOptions(MetadataRefreshOptions.FieldOptions.builder()
                        .title(titleProvider)
                        .build())
                .build();

        MetadataProviderSettings providerSettings = new MetadataProviderSettings();
        MetadataProviderSettings.Amazon amazon = new MetadataProviderSettings.Amazon();
        amazon.setEnabled(false);
        providerSettings.setAmazon(amazon);
        MetadataProviderSettings.Google google = new MetadataProviderSettings.Google();
        google.setEnabled(true);
        providerSettings.setGoogle(google);

        AppSettings appSettings = AppSettings.builder().metadataProviderSettings(providerSettings).build();
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        List<MetadataProvider> result = service.prepareProviders(options);

        assertThat(result).containsExactly(MetadataProvider.Google);
        assertThat(result).doesNotContain(MetadataProvider.Amazon);
    }

    @Test
    void isProviderEnabled_returnsTrueWhenSettingsNull() {
        boolean result = service.isProviderEnabled(MetadataProvider.Amazon, null);
        assertThat(result).isTrue();
    }

    @Test
    void isProviderEnabled_returnsTrueWhenProviderSettingsNull() {
        AppSettings settings = AppSettings.builder().metadataProviderSettings(null).build();
        boolean result = service.isProviderEnabled(MetadataProvider.Amazon, settings);
        assertThat(result).isTrue();
    }

    @Test
    void isProviderEnabled_returnsTrueWhenProviderNull() {
        boolean result = service.isProviderEnabled(null, AppSettings.builder().build());
        assertThat(result).isTrue();
    }

    @Test
    void fetchMetadataForBook_collectsResultsFromProviders() {
        BookParser amazonParser = mock(BookParser.class);
        BookParser googleParser = mock(BookParser.class);

        BookMetadata amazonMeta = BookMetadata.builder().provider(MetadataProvider.Amazon).title("Amazon Title").build();
        BookMetadata googleMeta = BookMetadata.builder().provider(MetadataProvider.Google).title("Google Title").build();

        when(parserMap.get(MetadataProvider.Amazon)).thenReturn(amazonParser);
        when(parserMap.get(MetadataProvider.Google)).thenReturn(googleParser);
        when(amazonParser.fetchTopMetadata(any(), any())).thenReturn(amazonMeta);
        when(googleParser.fetchTopMetadata(any(), any())).thenReturn(googleMeta);

        Book book = Book.builder().id(1L).metadata(BookMetadata.builder().title("Test").build()).build();

        Map<MetadataProvider, BookMetadata> result = service.fetchMetadataForBook(
                List.of(MetadataProvider.Amazon, MetadataProvider.Google), book);

        assertThat(result).hasSize(2);
        assertThat(result.get(MetadataProvider.Amazon).getTitle()).isEqualTo("Amazon Title");
        assertThat(result.get(MetadataProvider.Google).getTitle()).isEqualTo("Google Title");
    }

    @Test
    void fetchMetadataForBook_skipsNullResults() {
        BookParser parser = mock(BookParser.class);
        when(parserMap.get(MetadataProvider.Amazon)).thenReturn(parser);
        when(parser.fetchTopMetadata(any(), any())).thenReturn(null);

        Book book = Book.builder().id(1L).metadata(BookMetadata.builder().title("Test").build()).build();

        Map<MetadataProvider, BookMetadata> result = service.fetchMetadataForBook(
                List.of(MetadataProvider.Amazon), book);

        assertThat(result).isEmpty();
    }

    @Test
    void fetchMetadataForBook_entityOverload_convertsAndFetches() {
        BookParser parser = mock(BookParser.class);
        when(parserMap.get(MetadataProvider.Amazon)).thenReturn(parser);

        BookMetadata meta = BookMetadata.builder().provider(MetadataProvider.Amazon).title("T").build();
        when(parser.fetchTopMetadata(any(), any())).thenReturn(meta);

        BookEntity entity = BookEntity.builder().id(1L).build();
        Book book = Book.builder().id(1L).metadata(BookMetadata.builder().title("T").build()).build();
        when(bookMapper.toBook(entity)).thenReturn(book);

        Map<MetadataProvider, BookMetadata> result = service.fetchMetadataForBook(
                List.of(MetadataProvider.Amazon), entity);

        assertThat(result).hasSize(1);
    }

    @Test
    void getBookEntities_returnsBookIdsForBookRefresh() {
        MetadataRefreshRequest request = MetadataRefreshRequest.builder()
                .refreshType(MetadataRefreshRequest.RefreshType.BOOKS)
                .bookIds(Set.of(1L, 2L, 3L))
                .build();

        Set<Long> result = service.getBookEntities(request);

        assertThat(result).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void getBookEntities_returnsBookIdsForLibraryRefresh() {
        LibraryEntity library = new LibraryEntity();
        library.setId(5L);

        when(libraryRepository.findById(5L)).thenReturn(Optional.of(library));
        when(bookRepository.findBookIdsByLibraryId(5L)).thenReturn(Set.of(10L, 11L));

        MetadataRefreshRequest request = MetadataRefreshRequest.builder()
                .refreshType(MetadataRefreshRequest.RefreshType.LIBRARY)
                .libraryId(5L)
                .build();

        Set<Long> result = service.getBookEntities(request);

        assertThat(result).containsExactlyInAnyOrder(10L, 11L);
    }

    @Test
    void buildFetchMetadata_resolvesTitleFromPriorityProvider() {
        Map<MetadataProvider, BookMetadata> metadataMap = new HashMap<>();
        metadataMap.put(MetadataProvider.Amazon, BookMetadata.builder().title("Amazon Title").build());
        metadataMap.put(MetadataProvider.Google, BookMetadata.builder().title("Google Title").build());

        MetadataRefreshOptions.FieldProvider titleProvider =
                MetadataRefreshOptions.FieldProvider.builder().p1(MetadataProvider.Google).p2(MetadataProvider.Amazon).build();
        MetadataRefreshOptions options = MetadataRefreshOptions.builder()
                .fieldOptions(MetadataRefreshOptions.FieldOptions.builder().title(titleProvider).build())
                .enabledFields(MetadataRefreshOptions.EnabledFields.builder().title(true).build())
                .build();

        BookMetadata result = service.buildFetchMetadata(null, 1L, options, metadataMap);

        assertThat(result.getTitle()).isEqualTo("Google Title");
    }

    @Test
    void buildFetchMetadata_fallsToP2WhenP1HasNoValue() {
        Map<MetadataProvider, BookMetadata> metadataMap = new HashMap<>();
        metadataMap.put(MetadataProvider.Amazon, BookMetadata.builder().title("Amazon Title").build());
        metadataMap.put(MetadataProvider.Google, BookMetadata.builder().title(null).build());

        MetadataRefreshOptions.FieldProvider titleProvider =
                MetadataRefreshOptions.FieldProvider.builder().p1(MetadataProvider.Google).p2(MetadataProvider.Amazon).build();
        MetadataRefreshOptions options = MetadataRefreshOptions.builder()
                .fieldOptions(MetadataRefreshOptions.FieldOptions.builder().title(titleProvider).build())
                .enabledFields(MetadataRefreshOptions.EnabledFields.builder().title(true).build())
                .build();

        BookMetadata result = service.buildFetchMetadata(null, 1L, options, metadataMap);

        assertThat(result.getTitle()).isEqualTo("Amazon Title");
    }

    @Test
    void buildFetchMetadata_disabledFieldNotSet() {
        Map<MetadataProvider, BookMetadata> metadataMap = new HashMap<>();
        metadataMap.put(MetadataProvider.Amazon, BookMetadata.builder().title("Amazon Title").build());

        MetadataRefreshOptions options = MetadataRefreshOptions.builder()
                .fieldOptions(new MetadataRefreshOptions.FieldOptions())
                .enabledFields(MetadataRefreshOptions.EnabledFields.builder().title(false).build())
                .replaceMode(MetadataReplaceMode.REPLACE_MISSING)
                .build();

        BookMetadata result = service.buildFetchMetadata(null, 1L, options, metadataMap);

        assertThat(result.getTitle()).isNull();
    }

    @Test
    void buildFetchMetadata_replaceAllPreservesExistingWhenFieldDisabled() {
        BookMetadata existing = BookMetadata.builder().title("Existing Title").build();

        Map<MetadataProvider, BookMetadata> metadataMap = new HashMap<>();
        metadataMap.put(MetadataProvider.Amazon, BookMetadata.builder().title("New Title").build());

        MetadataRefreshOptions options = MetadataRefreshOptions.builder()
                .fieldOptions(new MetadataRefreshOptions.FieldOptions())
                .enabledFields(MetadataRefreshOptions.EnabledFields.builder().title(false).build())
                .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                .build();

        BookMetadata result = service.buildFetchMetadata(existing, 1L, options, metadataMap);

        assertThat(result.getTitle()).isEqualTo("Existing Title");
    }

    @Test
    void buildFetchMetadata_amazonRatingFromAmazonProvider() {
        Map<MetadataProvider, BookMetadata> metadataMap = new HashMap<>();
        metadataMap.put(MetadataProvider.Amazon, BookMetadata.builder().amazonRating(4.5).amazonReviewCount(1000).build());

        MetadataRefreshOptions options = MetadataRefreshOptions.builder()
                .fieldOptions(new MetadataRefreshOptions.FieldOptions())
                .enabledFields(new MetadataRefreshOptions.EnabledFields())
                .build();

        BookMetadata result = service.buildFetchMetadata(null, 1L, options, metadataMap);

        assertThat(result.getAmazonRating()).isEqualTo(4.5);
        assertThat(result.getAmazonReviewCount()).isEqualTo(1000);
    }

    @Test
    void buildFetchMetadata_goodreadsFieldsFromGoodReadsProvider() {
        Map<MetadataProvider, BookMetadata> metadataMap = new HashMap<>();
        metadataMap.put(MetadataProvider.GoodReads, BookMetadata.builder()
                .goodreadsRating(4.2)
                .goodreadsReviewCount(500)
                .goodreadsId("gr123")
                .build());

        MetadataRefreshOptions options = MetadataRefreshOptions.builder()
                .fieldOptions(new MetadataRefreshOptions.FieldOptions())
                .enabledFields(new MetadataRefreshOptions.EnabledFields())
                .build();

        BookMetadata result = service.buildFetchMetadata(null, 1L, options, metadataMap);

        assertThat(result.getGoodreadsRating()).isEqualTo(4.2);
        assertThat(result.getGoodreadsReviewCount()).isEqualTo(500);
        assertThat(result.getGoodreadsId()).isEqualTo("gr123");
    }

    @Test
    void buildFetchMetadata_categoriesMergedWhenMergeCategoriesEnabled() {
        Map<MetadataProvider, BookMetadata> metadataMap = new HashMap<>();
        metadataMap.put(MetadataProvider.Amazon, BookMetadata.builder().categories(Set.of("Fiction")).build());
        metadataMap.put(MetadataProvider.Google, BookMetadata.builder().categories(Set.of("Thriller")).build());

        MetadataRefreshOptions.FieldProvider catProvider = MetadataRefreshOptions.FieldProvider.builder()
                .p1(MetadataProvider.Amazon)
                .p2(MetadataProvider.Google)
                .build();

        MetadataRefreshOptions options = MetadataRefreshOptions.builder()
                .mergeCategories(true)
                .fieldOptions(MetadataRefreshOptions.FieldOptions.builder().categories(catProvider).build())
                .enabledFields(new MetadataRefreshOptions.EnabledFields())
                .build();

        BookMetadata result = service.buildFetchMetadata(null, 1L, options, metadataMap);

        assertThat(result.getCategories()).containsExactlyInAnyOrder("Fiction", "Thriller");
    }

    @Test
    void buildFetchMetadata_categoriesNotMergedUsesProviderPriority() {
        Map<MetadataProvider, BookMetadata> metadataMap = new HashMap<>();
        metadataMap.put(MetadataProvider.Amazon, BookMetadata.builder().categories(Set.of("Fiction")).build());
        metadataMap.put(MetadataProvider.Google, BookMetadata.builder().categories(Set.of("Thriller")).build());

        MetadataRefreshOptions.FieldProvider catProvider = MetadataRefreshOptions.FieldProvider.builder()
                .p1(MetadataProvider.Amazon)
                .p2(MetadataProvider.Google)
                .build();

        MetadataRefreshOptions options = MetadataRefreshOptions.builder()
                .mergeCategories(false)
                .fieldOptions(MetadataRefreshOptions.FieldOptions.builder().categories(catProvider).build())
                .enabledFields(new MetadataRefreshOptions.EnabledFields())
                .build();

        BookMetadata result = service.buildFetchMetadata(null, 1L, options, metadataMap);

        assertThat(result.getCategories()).containsExactly("Fiction");
    }

    @Test
    void buildFetchMetadata_collectsReviewsFromAllProviders() {
        BookReview r1 = new BookReview();
        BookReview r2 = new BookReview();

        Map<MetadataProvider, BookMetadata> metadataMap = new HashMap<>();
        metadataMap.put(MetadataProvider.Amazon, BookMetadata.builder().bookReviews(List.of(r1)).build());
        metadataMap.put(MetadataProvider.GoodReads, BookMetadata.builder().bookReviews(List.of(r2)).build());

        MetadataRefreshOptions options = MetadataRefreshOptions.builder()
                .fieldOptions(new MetadataRefreshOptions.FieldOptions())
                .enabledFields(new MetadataRefreshOptions.EnabledFields())
                .build();

        BookMetadata result = service.buildFetchMetadata(null, 1L, options, metadataMap);

        assertThat(result.getBookReviews()).containsExactlyInAnyOrder(r1, r2);
    }

    @Test
    void buildFetchMetadata_hardcoverFieldsResolved() {
        Map<MetadataProvider, BookMetadata> metadataMap = new HashMap<>();
        metadataMap.put(MetadataProvider.Hardcover, BookMetadata.builder()
                .hardcoverRating(4.0)
                .hardcoverReviewCount(200)
                .hardcoverId("hc1")
                .hardcoverBookId("hcb1")
                .moods(Set.of("Dark"))
                .tags(Set.of("SFF"))
                .build());

        MetadataRefreshOptions options = MetadataRefreshOptions.builder()
                .fieldOptions(new MetadataRefreshOptions.FieldOptions())
                .enabledFields(new MetadataRefreshOptions.EnabledFields())
                .build();

        BookMetadata result = service.buildFetchMetadata(null, 1L, options, metadataMap);

        assertThat(result.getHardcoverRating()).isEqualTo(4.0);
        assertThat(result.getHardcoverId()).isEqualTo("hc1");
        assertThat(result.getHardcoverBookId()).isEqualTo("hcb1");
        assertThat(result.getMoods()).containsExactly("Dark");
        assertThat(result.getTags()).containsExactly("SFF");
    }

    @Test
    void buildFetchMetadata_preservesLockStatesFromExistingMetadata() {
        BookMetadata existing = BookMetadata.builder()
                .title("Existing")
                .titleLocked(true)
                .descriptionLocked(true)
                .authorsLocked(true)
                .coverLocked(true)
                .allMetadataLocked(false)
                .build();

        Map<MetadataProvider, BookMetadata> metadataMap = new HashMap<>();
        metadataMap.put(MetadataProvider.Google, BookMetadata.builder().title("New Title").build());

        MetadataRefreshOptions options = MetadataRefreshOptions.builder()
                .fieldOptions(MetadataRefreshOptions.FieldOptions.builder()
                        .title(MetadataRefreshOptions.FieldProvider.builder().p1(MetadataProvider.Google).build())
                        .build())
                .enabledFields(MetadataRefreshOptions.EnabledFields.builder().title(true).build())
                .build();

        BookMetadata result = service.buildFetchMetadata(existing, 1L, options, metadataMap);

        assertThat(result.getTitleLocked()).isTrue();
        assertThat(result.getDescriptionLocked()).isTrue();
        assertThat(result.getAuthorsLocked()).isTrue();
        assertThat(result.getCoverLocked()).isTrue();
        assertThat(result.getAllMetadataLocked()).isFalse();
    }

    @Test
    void buildFetchMetadata_fallsBackToExistingDescriptionWhenNotResolved() {
        BookMetadata existing = BookMetadata.builder()
                .description("Existing description")
                .build();

        Map<MetadataProvider, BookMetadata> metadataMap = new HashMap<>();
        metadataMap.put(MetadataProvider.Google, BookMetadata.builder().title("Title").build());

        MetadataRefreshOptions options = MetadataRefreshOptions.builder()
                .fieldOptions(MetadataRefreshOptions.FieldOptions.builder()
                        .description(MetadataRefreshOptions.FieldProvider.builder().p1(MetadataProvider.Google).build())
                        .build())
                .enabledFields(MetadataRefreshOptions.EnabledFields.builder().description(true).build())
                .build();

        BookMetadata result = service.buildFetchMetadata(existing, 1L, options, metadataMap);

        assertThat(result.getDescription()).isEqualTo("Existing description");
    }

    @Test
    void buildFetchMetadata_fallsBackToExistingValuesForUnresolvedFields() {
        BookMetadata existing = BookMetadata.builder()
                .title("Existing Title")
                .subtitle("Existing Sub")
                .description("Existing Desc")
                .authors(List.of("Author"))
                .publisher("Pub")
                .language("en")
                .isbn13("978")
                .build();

        MetadataRefreshOptions options = MetadataRefreshOptions.builder()
                .fieldOptions(new MetadataRefreshOptions.FieldOptions())
                .enabledFields(MetadataRefreshOptions.EnabledFields.builder()
                        .title(false).subtitle(false).description(false).authors(false)
                        .publisher(false).language(false).isbn13(false)
                        .build())
                .replaceMode(MetadataReplaceMode.REPLACE_MISSING)
                .build();

        BookMetadata result = service.buildFetchMetadata(existing, 1L, options, new HashMap<>());

        assertThat(result.getTitle()).isEqualTo("Existing Title");
        assertThat(result.getSubtitle()).isEqualTo("Existing Sub");
        assertThat(result.getDescription()).isEqualTo("Existing Desc");
        assertThat(result.getAuthors()).containsExactly("Author");
        assertThat(result.getPublisher()).isEqualTo("Pub");
        assertThat(result.getLanguage()).isEqualTo("en");
        assertThat(result.getIsbn13()).isEqualTo("978");
    }

    @Test
    void getAllCategories_mergesFromAllProviders() {
        Map<MetadataProvider, BookMetadata> metadataMap = new HashMap<>();
        metadataMap.put(MetadataProvider.Amazon, BookMetadata.builder().categories(Set.of("A", "B")).build());
        metadataMap.put(MetadataProvider.Google, BookMetadata.builder().categories(Set.of("B", "C")).build());

        MetadataRefreshOptions.FieldProvider fp = MetadataRefreshOptions.FieldProvider.builder()
                .p1(MetadataProvider.Amazon)
                .p2(MetadataProvider.Google)
                .build();

        Set<String> result = service.getAllCategories(metadataMap, fp, BookMetadata::getCategories);

        assertThat(result).containsExactlyInAnyOrder("A", "B", "C");
    }

    @Test
    void getAllCategories_returnsEmptyWhenFieldProviderNull() {
        Set<String> result = service.getAllCategories(new HashMap<>(), null, BookMetadata::getCategories);
        assertThat(result).isEmpty();
    }

    @Test
    void updateBookMetadata_delegatesToBookMetadataUpdater() {
        BookEntity entity = BookEntity.builder().id(1L).build();
        BookMetadata meta = BookMetadata.builder().title("Test").build();
        Book book = Book.builder().id(1L).build();

        when(bookMapper.toBookWithDescription(entity, true)).thenReturn(book);
        when(authenticationService.getAuthenticatedUser()).thenReturn(null);

        service.updateBookMetadata(entity, meta, true, false);

        verify(bookMetadataUpdater).setBookMetadata(any());
        verify(notificationService).sendMessage(any(), any());
    }

    @Test
    void updateBookMetadata_skipsWhenMetadataNull() {
        BookEntity entity = BookEntity.builder().id(1L).build();

        service.updateBookMetadata(entity, null, false, false);

        verify(bookMetadataUpdater, never()).setBookMetadata(any());
    }

    @Test
    void updateBookMetadata_filtersShelvesByUser() {
        BookEntity entity = BookEntity.builder().id(1L).build();
        BookMetadata meta = BookMetadata.builder().title("Test").build();

        Shelf userShelf = Shelf.builder().id(1L).userId(10L).build();
        Shelf otherShelf = Shelf.builder().id(2L).userId(20L).build();
        Book book = Book.builder().id(1L).shelves(new HashSet<>(Set.of(userShelf, otherShelf))).build();

        when(bookMapper.toBookWithDescription(entity, true)).thenReturn(book);
        BookLoreUser user = new BookLoreUser();
        user.setId(10L);
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);

        service.updateBookMetadata(entity, meta, false, false);

        assertThat(book.getShelves()).containsExactly(userShelf);
    }

    @Test
    void addProviderToSet_addsAllNonNullEnabledProviders() {
        MetadataRefreshOptions.FieldProvider fp = MetadataRefreshOptions.FieldProvider.builder()
                .p1(MetadataProvider.Amazon)
                .p2(null)
                .p3(MetadataProvider.Google)
                .p4(MetadataProvider.GoodReads)
                .build();

        MetadataProviderSettings ps = new MetadataProviderSettings();
        MetadataProviderSettings.Amazon amazon = new MetadataProviderSettings.Amazon();
        amazon.setEnabled(true);
        ps.setAmazon(amazon);
        MetadataProviderSettings.Google google = new MetadataProviderSettings.Google();
        google.setEnabled(true);
        ps.setGoogle(google);
        MetadataProviderSettings.Goodreads gr = new MetadataProviderSettings.Goodreads();
        gr.setEnabled(true);
        ps.setGoodReads(gr);

        AppSettings appSettings = AppSettings.builder().metadataProviderSettings(ps).build();
        Set<MetadataProvider> set = new HashSet<>();

        service.addProviderToSet(fp, set, appSettings);

        assertThat(set).containsExactlyInAnyOrder(MetadataProvider.Amazon, MetadataProvider.Google, MetadataProvider.GoodReads);
    }

    @Test
    void addProviderToSet_skipsWhenFieldProviderNull() {
        Set<MetadataProvider> set = new HashSet<>();
        service.addProviderToSet(null, set, AppSettings.builder().build());
        assertThat(set).isEmpty();
    }

    @Test
    void resolveFieldWithProviders_returnsNullWhenFieldProviderIsNull() {
        Map<MetadataProvider, BookMetadata> metadataMap = new HashMap<>();
        String result = service.resolveFieldAsString(metadataMap, null, BookMetadata::getTitle);
        assertThat(result).isNull();
    }

    @Test
    void resolveFieldAsList_returnsNullWhenFieldProviderNull() {
        Map<MetadataProvider, BookMetadata> metadataMap = new HashMap<>();
        List<String> result = service.resolveFieldAsList(metadataMap, null, BookMetadata::getCategories);
        assertThat(result).isNull();
    }

    @Test
    void resolveFieldAsList_skipsEmptyCollections() {
        Map<MetadataProvider, BookMetadata> metadataMap = new HashMap<>();
        metadataMap.put(MetadataProvider.Amazon, BookMetadata.builder().categories(Set.of()).build());
        metadataMap.put(MetadataProvider.Google, BookMetadata.builder().categories(Set.of("Cat")).build());

        MetadataRefreshOptions.FieldProvider fp = MetadataRefreshOptions.FieldProvider.builder()
                .p1(MetadataProvider.Amazon)
                .p2(MetadataProvider.Google)
                .build();

        List<String> result = service.resolveFieldAsList(metadataMap, fp, BookMetadata::getCategories);
        assertThat(result).containsExactly("Cat");
    }

    @Test
    void buildFetchMetadata_comicMetadataFromComicvine() {
        ComicMetadata comic = new ComicMetadata();
        Map<MetadataProvider, BookMetadata> metadataMap = new HashMap<>();
        metadataMap.put(MetadataProvider.Comicvine, BookMetadata.builder()
                .comicvineId("cv123")
                .comicMetadata(comic)
                .build());

        MetadataRefreshOptions options = MetadataRefreshOptions.builder()
                .fieldOptions(new MetadataRefreshOptions.FieldOptions())
                .enabledFields(new MetadataRefreshOptions.EnabledFields())
                .build();

        BookMetadata result = service.buildFetchMetadata(null, 1L, options, metadataMap);

        assertThat(result.getComicvineId()).isEqualTo("cv123");
        assertThat(result.getComicMetadata()).isSameAs(comic);
    }

    @Nested
    class IsProviderEnabledTests {

        @Test
        void hardcover_enabled() {
            MetadataProviderSettings ps = new MetadataProviderSettings();
            MetadataProviderSettings.Hardcover hc = new MetadataProviderSettings.Hardcover();
            hc.setEnabled(true);
            ps.setHardcover(hc);
            AppSettings settings = AppSettings.builder().metadataProviderSettings(ps).build();

            assertThat(service.isProviderEnabled(MetadataProvider.Hardcover, settings)).isTrue();
        }

        @Test
        void hardcover_disabled() {
            MetadataProviderSettings ps = new MetadataProviderSettings();
            MetadataProviderSettings.Hardcover hc = new MetadataProviderSettings.Hardcover();
            hc.setEnabled(false);
            ps.setHardcover(hc);
            AppSettings settings = AppSettings.builder().metadataProviderSettings(ps).build();

            assertThat(service.isProviderEnabled(MetadataProvider.Hardcover, settings)).isFalse();
        }

        @Test
        void hardcover_nullSetting() {
            MetadataProviderSettings ps = new MetadataProviderSettings();
            ps.setHardcover(null);
            AppSettings settings = AppSettings.builder().metadataProviderSettings(ps).build();

            assertThat(service.isProviderEnabled(MetadataProvider.Hardcover, settings)).isFalse();
        }

        @Test
        void comicvine_enabled() {
            MetadataProviderSettings ps = new MetadataProviderSettings();
            MetadataProviderSettings.Comicvine cv = new MetadataProviderSettings.Comicvine();
            cv.setEnabled(true);
            ps.setComicvine(cv);
            AppSettings settings = AppSettings.builder().metadataProviderSettings(ps).build();

            assertThat(service.isProviderEnabled(MetadataProvider.Comicvine, settings)).isTrue();
        }

        @Test
        void comicvine_disabled() {
            MetadataProviderSettings ps = new MetadataProviderSettings();
            MetadataProviderSettings.Comicvine cv = new MetadataProviderSettings.Comicvine();
            cv.setEnabled(false);
            ps.setComicvine(cv);
            AppSettings settings = AppSettings.builder().metadataProviderSettings(ps).build();

            assertThat(service.isProviderEnabled(MetadataProvider.Comicvine, settings)).isFalse();
        }

        @Test
        void douban_enabled() {
            MetadataProviderSettings ps = new MetadataProviderSettings();
            MetadataProviderSettings.Douban db = new MetadataProviderSettings.Douban();
            db.setEnabled(true);
            ps.setDouban(db);
            AppSettings settings = AppSettings.builder().metadataProviderSettings(ps).build();

            assertThat(service.isProviderEnabled(MetadataProvider.Douban, settings)).isTrue();
        }

        @Test
        void douban_disabled() {
            MetadataProviderSettings ps = new MetadataProviderSettings();
            MetadataProviderSettings.Douban db = new MetadataProviderSettings.Douban();
            db.setEnabled(false);
            ps.setDouban(db);
            AppSettings settings = AppSettings.builder().metadataProviderSettings(ps).build();

            assertThat(service.isProviderEnabled(MetadataProvider.Douban, settings)).isFalse();
        }

        @Test
        void lubimyczytac_enabled() {
            MetadataProviderSettings ps = new MetadataProviderSettings();
            MetadataProviderSettings.Lubimyczytac lc = new MetadataProviderSettings.Lubimyczytac();
            lc.setEnabled(true);
            ps.setLubimyczytac(lc);
            AppSettings settings = AppSettings.builder().metadataProviderSettings(ps).build();

            assertThat(service.isProviderEnabled(MetadataProvider.Lubimyczytac, settings)).isTrue();
        }

        @Test
        void ranobedb_enabled() {
            MetadataProviderSettings ps = new MetadataProviderSettings();
            MetadataProviderSettings.Ranobedb rn = new MetadataProviderSettings.Ranobedb();
            rn.setEnabled(true);
            ps.setRanobedb(rn);
            AppSettings settings = AppSettings.builder().metadataProviderSettings(ps).build();

            assertThat(service.isProviderEnabled(MetadataProvider.Ranobedb, settings)).isTrue();
        }

        @Test
        void ranobedb_disabled() {
            MetadataProviderSettings ps = new MetadataProviderSettings();
            MetadataProviderSettings.Ranobedb rn = new MetadataProviderSettings.Ranobedb();
            rn.setEnabled(false);
            ps.setRanobedb(rn);
            AppSettings settings = AppSettings.builder().metadataProviderSettings(ps).build();

            assertThat(service.isProviderEnabled(MetadataProvider.Ranobedb, settings)).isFalse();
        }

        @Test
        void audible_defaultCase_returnsTrue() {
            MetadataProviderSettings ps = new MetadataProviderSettings();
            AppSettings settings = AppSettings.builder().metadataProviderSettings(ps).build();

            assertThat(service.isProviderEnabled(MetadataProvider.Audible, settings)).isTrue();
        }
    }

    @Nested
    class GetBookEntitiesTests {

        @Test
        void throwsWhenLibraryNotFound() {
            when(libraryRepository.findById(99L)).thenReturn(Optional.empty());

            MetadataRefreshRequest request = MetadataRefreshRequest.builder()
                    .refreshType(MetadataRefreshRequest.RefreshType.LIBRARY)
                    .libraryId(99L)
                    .build();

            assertThatThrownBy(() -> service.getBookEntities(request))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    class FetchTopMetadataTests {

        @Test
        void throwsWhenParserNotFound() {
            when(parserMap.get(MetadataProvider.Amazon)).thenReturn(null);

            Book book = Book.builder().id(1L).metadata(BookMetadata.builder().title("T").build()).build();

            assertThatThrownBy(() -> service.fetchTopMetadataFromAProvider(MetadataProvider.Amazon, book))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        void buildsFetchRequestWithIsbn10WhenIsbn13BlankOrNull() {
            BookParser parser = mock(BookParser.class);
            when(parserMap.get(MetadataProvider.Amazon)).thenReturn(parser);
            when(parser.fetchTopMetadata(any(), any())).thenReturn(null);

            Book book = Book.builder().id(1L)
                    .metadata(BookMetadata.builder().title("T").isbn13("").isbn10("1234567890").build())
                    .build();

            service.fetchTopMetadataFromAProvider(MetadataProvider.Amazon, book);

            verify(parser).fetchTopMetadata(eq(book), argThat(req -> "1234567890".equals(req.getIsbn())));
        }

        @Test
        void buildsFetchRequestWithNullMetadata() {
            BookParser parser = mock(BookParser.class);
            when(parserMap.get(MetadataProvider.Amazon)).thenReturn(parser);
            when(parser.fetchTopMetadata(any(), any())).thenReturn(null);

            Book book = Book.builder().id(1L).metadata(null).build();

            service.fetchTopMetadataFromAProvider(MetadataProvider.Amazon, book);

            verify(parser).fetchTopMetadata(eq(book), argThat(req -> req.getBookId() == 1L && req.getIsbn() == null));
        }

        @Test
        void buildsFetchRequestWithIsbn13() {
            BookParser parser = mock(BookParser.class);
            when(parserMap.get(MetadataProvider.Google)).thenReturn(parser);
            when(parser.fetchTopMetadata(any(), any())).thenReturn(null);

            Book book = Book.builder().id(1L)
                    .metadata(BookMetadata.builder().title("T").isbn13("9781234567890").isbn10("1234567890").build())
                    .build();

            service.fetchTopMetadataFromAProvider(MetadataProvider.Google, book);

            verify(parser).fetchTopMetadata(eq(book), argThat(req -> "9781234567890".equals(req.getIsbn())));
        }
    }

    @Nested
    class UpdateBookMetadataTests {

        @Test
        void fiveArgOverloadSetsReplaceMode() {
            BookEntity entity = BookEntity.builder().id(1L).build();
            BookMetadata meta = BookMetadata.builder().title("Test").build();
            Book book = Book.builder().id(1L).build();

            when(bookMapper.toBookWithDescription(entity, true)).thenReturn(book);
            when(authenticationService.getAuthenticatedUser()).thenReturn(null);

            service.updateBookMetadata(entity, meta, true, false, MetadataReplaceMode.REPLACE_ALL);

            verify(bookMetadataUpdater).setBookMetadata(argThat(ctx ->
                    ctx.getReplaceMode() == MetadataReplaceMode.REPLACE_ALL
                            && ctx.isUpdateThumbnail()
                            && !ctx.isMergeCategories()
                            && ctx.isMergeMoods()
                            && ctx.isMergeTags()
            ));
        }

        @Test
        void contextWithNullWrapper_skipsUpdate() {
            MetadataUpdateContext context = MetadataUpdateContext.builder()
                    .bookEntity(BookEntity.builder().id(1L).build())
                    .metadataUpdateWrapper(null)
                    .build();

            service.updateBookMetadata(context);

            verify(bookMetadataUpdater, never()).setBookMetadata(any());
        }

        @Test
        void contextWithNullMetadataInsideWrapper_skipsUpdate() {
            MetadataUpdateContext context = MetadataUpdateContext.builder()
                    .bookEntity(BookEntity.builder().id(1L).build())
                    .metadataUpdateWrapper(MetadataUpdateWrapper.builder().metadata(null).build())
                    .build();

            service.updateBookMetadata(context);

            verify(bookMetadataUpdater, never()).setBookMetadata(any());
        }

        @Test
        void shelvesNullOnBook_noFiltering() {
            BookEntity entity = BookEntity.builder().id(1L).build();
            BookMetadata meta = BookMetadata.builder().title("Test").build();
            Book book = Book.builder().id(1L).shelves(null).build();

            when(bookMapper.toBookWithDescription(entity, true)).thenReturn(book);
            BookLoreUser user = new BookLoreUser();
            user.setId(10L);
            when(authenticationService.getAuthenticatedUser()).thenReturn(user);

            service.updateBookMetadata(entity, meta, false, false);

            assertThat(book.getShelves()).isNull();
        }
    }

    @Nested
    class BuildFetchMetadataProviderFieldsTests {

        @Test
        void lubimyczytacFields() {
            Map<MetadataProvider, BookMetadata> metadataMap = new HashMap<>();
            metadataMap.put(MetadataProvider.Lubimyczytac, BookMetadata.builder()
                    .lubimyczytacId("lc123")
                    .lubimyczytacRating(4.1)
                    .build());

            MetadataRefreshOptions options = MetadataRefreshOptions.builder()
                    .fieldOptions(new MetadataRefreshOptions.FieldOptions())
                    .enabledFields(new MetadataRefreshOptions.EnabledFields())
                    .build();

            BookMetadata result = service.buildFetchMetadata(null, 1L, options, metadataMap);

            assertThat(result.getLubimyczytacId()).isEqualTo("lc123");
            assertThat(result.getLubimyczytacRating()).isEqualTo(4.1);
        }

        @Test
        void ranobedbFields() {
            Map<MetadataProvider, BookMetadata> metadataMap = new HashMap<>();
            metadataMap.put(MetadataProvider.Ranobedb, BookMetadata.builder()
                    .ranobedbId("rn456")
                    .ranobedbRating(3.8)
                    .build());

            MetadataRefreshOptions options = MetadataRefreshOptions.builder()
                    .fieldOptions(new MetadataRefreshOptions.FieldOptions())
                    .enabledFields(new MetadataRefreshOptions.EnabledFields())
                    .build();

            BookMetadata result = service.buildFetchMetadata(null, 1L, options, metadataMap);

            assertThat(result.getRanobedbId()).isEqualTo("rn456");
            assertThat(result.getRanobedbRating()).isEqualTo(3.8);
        }

        @Test
        void googleIdFromGoogleProvider() {
            Map<MetadataProvider, BookMetadata> metadataMap = new HashMap<>();
            metadataMap.put(MetadataProvider.Google, BookMetadata.builder()
                    .googleId("goog123")
                    .build());

            MetadataRefreshOptions options = MetadataRefreshOptions.builder()
                    .fieldOptions(new MetadataRefreshOptions.FieldOptions())
                    .enabledFields(new MetadataRefreshOptions.EnabledFields())
                    .build();

            BookMetadata result = service.buildFetchMetadata(null, 1L, options, metadataMap);

            assertThat(result.getGoogleId()).isEqualTo("goog123");
        }

        @Test
        void asinFromAmazonProvider() {
            Map<MetadataProvider, BookMetadata> metadataMap = new HashMap<>();
            metadataMap.put(MetadataProvider.Amazon, BookMetadata.builder()
                    .asin("B012345678")
                    .build());

            MetadataRefreshOptions options = MetadataRefreshOptions.builder()
                    .fieldOptions(new MetadataRefreshOptions.FieldOptions())
                    .enabledFields(new MetadataRefreshOptions.EnabledFields())
                    .build();

            BookMetadata result = service.buildFetchMetadata(null, 1L, options, metadataMap);

            assertThat(result.getAsin()).isEqualTo("B012345678");
        }

        @Test
        void replaceAll_preservesExistingProviderFields_whenDisabled() {
            BookMetadata existing = BookMetadata.builder()
                    .amazonRating(3.5)
                    .amazonReviewCount(100)
                    .goodreadsRating(4.0)
                    .goodreadsReviewCount(200)
                    .goodreadsId("gr1")
                    .hardcoverRating(4.5)
                    .hardcoverReviewCount(50)
                    .hardcoverId("hc1")
                    .hardcoverBookId("hcb1")
                    .asin("B000")
                    .googleId("g1")
                    .comicvineId("cv1")
                    .lubimyczytacId("lc1")
                    .lubimyczytacRating(3.0)
                    .ranobedbId("rn1")
                    .ranobedbRating(2.5)
                    .moods(Set.of("Dark"))
                    .tags(Set.of("Epic"))
                    .categories(Set.of("Fantasy"))
                    .thumbnailUrl("http://cover.jpg")
                    .build();

            MetadataRefreshOptions options = MetadataRefreshOptions.builder()
                    .fieldOptions(new MetadataRefreshOptions.FieldOptions())
                    .enabledFields(MetadataRefreshOptions.EnabledFields.builder()
                            .amazonRating(false).amazonReviewCount(false)
                            .goodreadsRating(false).goodreadsReviewCount(false).goodreadsId(false)
                            .hardcoverRating(false).hardcoverReviewCount(false).hardcoverId(false)
                            .asin(false).googleId(false).comicvineId(false)
                            .lubimyczytacId(false).lubimyczytacRating(false)
                            .ranobedbId(false).ranobedbRating(false)
                            .moods(false).tags(false).categories(false).cover(false)
                            .build())
                    .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                    .build();

            BookMetadata result = service.buildFetchMetadata(existing, 1L, options, new HashMap<>());

            assertThat(result.getAmazonRating()).isEqualTo(3.5);
            assertThat(result.getAmazonReviewCount()).isEqualTo(100);
            assertThat(result.getGoodreadsRating()).isEqualTo(4.0);
            assertThat(result.getGoodreadsReviewCount()).isEqualTo(200);
            assertThat(result.getGoodreadsId()).isEqualTo("gr1");
            assertThat(result.getHardcoverRating()).isEqualTo(4.5);
            assertThat(result.getHardcoverReviewCount()).isEqualTo(50);
            assertThat(result.getHardcoverId()).isEqualTo("hc1");
            assertThat(result.getHardcoverBookId()).isEqualTo("hcb1");
            assertThat(result.getAsin()).isEqualTo("B000");
            assertThat(result.getGoogleId()).isEqualTo("g1");
            assertThat(result.getComicvineId()).isEqualTo("cv1");
            assertThat(result.getLubimyczytacId()).isEqualTo("lc1");
            assertThat(result.getLubimyczytacRating()).isEqualTo(3.0);
            assertThat(result.getRanobedbId()).isEqualTo("rn1");
            assertThat(result.getRanobedbRating()).isEqualTo(2.5);
            assertThat(result.getMoods()).containsExactly("Dark");
            assertThat(result.getTags()).containsExactly("Epic");
            assertThat(result.getCategories()).containsExactly("Fantasy");
            assertThat(result.getThumbnailUrl()).isEqualTo("http://cover.jpg");
        }

        @Test
        void nullFieldOptionsAndEnabledFields_usesDefaults() {
            Map<MetadataProvider, BookMetadata> metadataMap = new HashMap<>();
            metadataMap.put(MetadataProvider.Amazon, BookMetadata.builder()
                    .title("A Title")
                    .amazonRating(4.0)
                    .asin("B123")
                    .build());

            MetadataRefreshOptions options = MetadataRefreshOptions.builder()
                    .fieldOptions(null)
                    .enabledFields(null)
                    .build();

            BookMetadata result = service.buildFetchMetadata(null, 1L, options, metadataMap);

            assertThat(result.getAmazonRating()).isEqualTo(4.0);
            assertThat(result.getAsin()).isEqualTo("B123");
        }

        @Test
        void replaceAll_preservesSubtitleDescriptionPublisherSeriesIsbnLangPageCount() {
            BookMetadata existing = BookMetadata.builder()
                    .subtitle("Sub")
                    .description("Desc")
                    .publisher("Pub")
                    .authors(List.of("Auth"))
                    .publishedDate(java.time.LocalDate.of(2020, 1, 1))
                    .seriesName("Series")
                    .seriesNumber(1.0f)
                    .seriesTotal(5)
                    .isbn13("978")
                    .isbn10("123")
                    .language("en")
                    .pageCount(300)
                    .build();

            MetadataRefreshOptions options = MetadataRefreshOptions.builder()
                    .fieldOptions(new MetadataRefreshOptions.FieldOptions())
                    .enabledFields(MetadataRefreshOptions.EnabledFields.builder()
                            .title(false).subtitle(false).description(false).authors(false)
                            .publisher(false).publishedDate(false).seriesName(false)
                            .seriesNumber(false).seriesTotal(false).isbn13(false).isbn10(false)
                            .language(false).pageCount(false)
                            .build())
                    .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                    .build();

            BookMetadata result = service.buildFetchMetadata(existing, 1L, options, new HashMap<>());

            assertThat(result.getSubtitle()).isEqualTo("Sub");
            assertThat(result.getDescription()).isEqualTo("Desc");
            assertThat(result.getPublisher()).isEqualTo("Pub");
            assertThat(result.getAuthors()).containsExactly("Auth");
            assertThat(result.getPublishedDate()).isEqualTo(java.time.LocalDate.of(2020, 1, 1));
            assertThat(result.getSeriesName()).isEqualTo("Series");
            assertThat(result.getSeriesNumber()).isEqualTo(1f);
            assertThat(result.getSeriesTotal()).isEqualTo(5);
            assertThat(result.getIsbn13()).isEqualTo("978");
            assertThat(result.getIsbn10()).isEqualTo("123");
            assertThat(result.getLanguage()).isEqualTo("en");
            assertThat(result.getPageCount()).isEqualTo(300);
        }

        @Test
        void enabledFields_resolveSubtitleDescriptionAuthorsPublisherFromProviders() {
            Map<MetadataProvider, BookMetadata> metadataMap = new HashMap<>();
            metadataMap.put(MetadataProvider.Amazon, BookMetadata.builder()
                    .subtitle("Amazon Sub")
                    .description("Amazon Desc")
                    .authors(List.of("Author A"))
                    .publisher("Amazon Pub")
                    .publishedDate(java.time.LocalDate.of(2021, 6, 15))
                    .seriesName("Amazon Series")
                    .seriesNumber(2f)
                    .seriesTotal(10)
                    .isbn13("9780000000000")
                    .isbn10("0000000000")
                    .language("en")
                    .pageCount(250)
                    .thumbnailUrl("http://cover.jpg")
                    .build());

            MetadataRefreshOptions.FieldProvider fp = MetadataRefreshOptions.FieldProvider.builder()
                    .p1(MetadataProvider.Amazon).build();
            MetadataRefreshOptions options = MetadataRefreshOptions.builder()
                    .fieldOptions(MetadataRefreshOptions.FieldOptions.builder()
                            .subtitle(fp).description(fp).authors(fp).publisher(fp)
                            .publishedDate(fp).seriesName(fp).seriesNumber(fp).seriesTotal(fp)
                            .isbn13(fp).isbn10(fp).language(fp).pageCount(fp).cover(fp)
                            .build())
                    .enabledFields(MetadataRefreshOptions.EnabledFields.builder()
                            .subtitle(true).description(true).authors(true).publisher(true)
                            .publishedDate(true).seriesName(true).seriesNumber(true).seriesTotal(true)
                            .isbn13(true).isbn10(true).language(true).pageCount(true).cover(true)
                            .build())
                    .build();

            BookMetadata result = service.buildFetchMetadata(null, 1L, options, metadataMap);

            assertThat(result.getSubtitle()).isEqualTo("Amazon Sub");
            assertThat(result.getDescription()).isEqualTo("Amazon Desc");
            assertThat(result.getAuthors()).containsExactly("Author A");
            assertThat(result.getPublisher()).isEqualTo("Amazon Pub");
            assertThat(result.getPublishedDate()).isEqualTo(java.time.LocalDate.of(2021, 6, 15));
            assertThat(result.getSeriesName()).isEqualTo("Amazon Series");
            assertThat(result.getSeriesNumber()).isEqualTo(2f);
            assertThat(result.getSeriesTotal()).isEqualTo(10);
            assertThat(result.getIsbn13()).isEqualTo("9780000000000");
            assertThat(result.getIsbn10()).isEqualTo("0000000000");
            assertThat(result.getLanguage()).isEqualTo("en");
            assertThat(result.getPageCount()).isEqualTo(250);
            assertThat(result.getThumbnailUrl()).isEqualTo("http://cover.jpg");
        }

        @Test
        void noReviewsInMetadata_reviewsNotSet() {
            Map<MetadataProvider, BookMetadata> metadataMap = new HashMap<>();
            metadataMap.put(MetadataProvider.Amazon, BookMetadata.builder().title("T").bookReviews(null).build());

            MetadataRefreshOptions options = MetadataRefreshOptions.builder()
                    .fieldOptions(new MetadataRefreshOptions.FieldOptions())
                    .enabledFields(new MetadataRefreshOptions.EnabledFields())
                    .build();

            BookMetadata result = service.buildFetchMetadata(null, 1L, options, metadataMap);

            assertThat(result.getBookReviews()).isNull();
        }

        @Test
        void providerEnabledButNoDataInMap_fieldStaysNull() {
            MetadataRefreshOptions options = MetadataRefreshOptions.builder()
                    .fieldOptions(new MetadataRefreshOptions.FieldOptions())
                    .enabledFields(new MetadataRefreshOptions.EnabledFields())
                    .build();

            BookMetadata result = service.buildFetchMetadata(null, 1L, options, new HashMap<>());

            assertThat(result.getAmazonRating()).isNull();
            assertThat(result.getGoodreadsRating()).isNull();
            assertThat(result.getHardcoverRating()).isNull();
            assertThat(result.getAsin()).isNull();
            assertThat(result.getGoodreadsId()).isNull();
            assertThat(result.getHardcoverId()).isNull();
            assertThat(result.getGoogleId()).isNull();
            assertThat(result.getComicvineId()).isNull();
            assertThat(result.getLubimyczytacId()).isNull();
            assertThat(result.getRanobedbId()).isNull();
            assertThat(result.getMoods()).isNull();
            assertThat(result.getTags()).isNull();
        }
    }

    @Nested
    class GetAllCategoriesTests {

        @Test
        void nullExtractedCategories_skipped() {
            Map<MetadataProvider, BookMetadata> metadataMap = new HashMap<>();
            metadataMap.put(MetadataProvider.Amazon, BookMetadata.builder().categories(null).build());
            metadataMap.put(MetadataProvider.Google, BookMetadata.builder().categories(Set.of("X")).build());

            MetadataRefreshOptions.FieldProvider fp = MetadataRefreshOptions.FieldProvider.builder()
                    .p1(MetadataProvider.Amazon)
                    .p2(MetadataProvider.Google)
                    .build();

            Set<String> result = service.getAllCategories(metadataMap, fp, BookMetadata::getCategories);
            assertThat(result).containsExactly("X");
        }

        @Test
        void providerNotInMap_skipped() {
            Map<MetadataProvider, BookMetadata> metadataMap = new HashMap<>();
            metadataMap.put(MetadataProvider.Amazon, BookMetadata.builder().categories(Set.of("A")).build());

            MetadataRefreshOptions.FieldProvider fp = MetadataRefreshOptions.FieldProvider.builder()
                    .p1(MetadataProvider.Google)
                    .p2(MetadataProvider.Amazon)
                    .build();

            Set<String> result = service.getAllCategories(metadataMap, fp, BookMetadata::getCategories);
            assertThat(result).containsExactly("A");
        }
    }

    @Nested
    class ResolveFieldTests {

        @Test
        void resolveField_returnsNullWhenFieldProviderNull() {
            Map<MetadataProvider, BookMetadata> metadataMap = new HashMap<>();
            Object result = service.resolveField(metadataMap, null, BookMetadata::getPublishedDate);
            assertThat(result).isNull();
        }

        @Test
        void resolveFieldAsInteger_returnsNullWhenFieldProviderNull() {
            Map<MetadataProvider, BookMetadata> metadataMap = new HashMap<>();
            Integer result = service.resolveFieldAsInteger(metadataMap, null, BookMetadata::getPageCount);
            assertThat(result).isNull();
        }

        @Test
        void resolveField_providerNotInMap_returnsNull() {
            Map<MetadataProvider, BookMetadata> metadataMap = new HashMap<>();
            MetadataRefreshOptions.FieldProvider fp = MetadataRefreshOptions.FieldProvider.builder()
                    .p1(MetadataProvider.Amazon).build();

            Object result = service.resolveField(metadataMap, fp, BookMetadata::getPublishedDate);
            assertThat(result).isNull();
        }

        @Test
        void resolveFieldAsInteger_fallsThroughNullValues() {
            Map<MetadataProvider, BookMetadata> metadataMap = new HashMap<>();
            metadataMap.put(MetadataProvider.Amazon, BookMetadata.builder().pageCount(null).build());
            metadataMap.put(MetadataProvider.Google, BookMetadata.builder().pageCount(300).build());

            MetadataRefreshOptions.FieldProvider fp = MetadataRefreshOptions.FieldProvider.builder()
                    .p1(MetadataProvider.Amazon)
                    .p2(MetadataProvider.Google)
                    .build();

            Integer result = service.resolveFieldAsInteger(metadataMap, fp, BookMetadata::getPageCount);
            assertThat(result).isEqualTo(300);
        }
    }

    @Nested
    class PrepareProvidersTests {

        @Test
        void nullFieldOptions_returnsEmptyList() {
            MetadataRefreshOptions options = MetadataRefreshOptions.builder()
                    .fieldOptions(null)
                    .build();

            when(appSettingService.getAppSettings()).thenReturn(AppSettings.builder().build());

            List<MetadataProvider> result = service.prepareProviders(options);
            assertThat(result).isEmpty();
        }
    }
}
