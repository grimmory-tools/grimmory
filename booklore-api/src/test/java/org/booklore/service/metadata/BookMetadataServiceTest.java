package org.booklore.service.metadata;

import org.booklore.exception.APIException;
import org.booklore.exception.ApiError;
import org.booklore.mapper.BookMapper;
import org.booklore.mapper.BookMetadataMapper;
import org.booklore.mapper.MetadataClearFlagsMapper;
import org.booklore.model.MetadataClearFlags;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.request.BulkMetadataUpdateRequest;
import org.booklore.model.dto.request.FetchMetadataRequest;
import org.booklore.model.dto.request.IsbnLookupRequest;
import org.booklore.model.dto.request.MetadataRefreshOptions;
import org.booklore.model.dto.request.ToggleAllLockRequest;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.Lock;
import org.booklore.model.enums.MetadataProvider;
import org.booklore.repository.BookMetadataRepository;
import org.booklore.repository.BookRepository;
import org.booklore.service.NotificationService;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.book.BookQueryService;
import org.booklore.service.metadata.extractor.CbxMetadataExtractor;
import org.booklore.service.metadata.extractor.MetadataExtractorFactory;
import org.booklore.service.metadata.parser.BookParser;
import org.booklore.service.metadata.parser.DetailedMetadataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookMetadataServiceTest {

    @Mock private BookRepository bookRepository;
    @Mock private BookMapper bookMapper;
    @Mock private BookMetadataMapper bookMetadataMapper;
    @Mock private BookMetadataUpdater bookMetadataUpdater;
    @Mock private NotificationService notificationService;
    @Mock private BookMetadataRepository bookMetadataRepository;
    @Mock private BookQueryService bookQueryService;
    @Mock private org.booklore.service.audit.AuditService auditService;
    @Mock private CbxMetadataExtractor cbxMetadataExtractor;
    @Mock private MetadataExtractorFactory metadataExtractorFactory;
    @Mock private MetadataClearFlagsMapper metadataClearFlagsMapper;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private AppSettingService appSettingService;

    private Map<MetadataProvider, BookParser> parserMap;
    private BookMetadataService service;

    @BeforeEach
    void setUp() {
        parserMap = new HashMap<>();
        service = new BookMetadataService(
                bookRepository, bookMapper, bookMetadataMapper, bookMetadataUpdater,
                notificationService, bookMetadataRepository, bookQueryService,
                auditService, parserMap, cbxMetadataExtractor, metadataExtractorFactory,
                metadataClearFlagsMapper, transactionManager, appSettingService
        );
    }

    @Nested
    class FetchMetadataListFromAProvider {

        @Test
        void delegatesToParser() {
            BookParser parser = mock(BookParser.class);
            parserMap.put(MetadataProvider.Google, parser);
            Book book = Book.builder().build();
            FetchMetadataRequest request = FetchMetadataRequest.builder().build();
            List<BookMetadata> expected = List.of(BookMetadata.builder().title("Test").build());
            when(parser.fetchMetadata(book, request)).thenReturn(expected);

            List<BookMetadata> result = service.fetchMetadataListFromAProvider(MetadataProvider.Google, book, request);

            assertThat(result).isEqualTo(expected);
        }

        @Test
        void throwsWhenProviderNotInMap() {
            assertThatThrownBy(() -> service.fetchMetadataListFromAProvider(MetadataProvider.Amazon, Book.builder().build(), FetchMetadataRequest.builder().build()))
                    .isInstanceOf(APIException.class);
        }
    }

    @Nested
    class GetDetailedProviderMetadata {

        @Test
        void returnsDetailedMetadataWhenParserImplementsDetailedProvider() {
            DetailedBookParser parser = mock(DetailedBookParser.class);
            parserMap.put(MetadataProvider.Google, parser);
            BookMetadata expected = BookMetadata.builder().title("Detailed").build();
            when(parser.fetchDetailedMetadata("item-123")).thenReturn(expected);

            BookMetadata result = service.getDetailedProviderMetadata(MetadataProvider.Google, "item-123");

            assertThat(result).isEqualTo(expected);
        }

        @Test
        void returnsNullWhenParserDoesNotImplementDetailedProvider() {
            BookParser parser = mock(BookParser.class);
            parserMap.put(MetadataProvider.Google, parser);

            BookMetadata result = service.getDetailedProviderMetadata(MetadataProvider.Google, "item-123");

            assertThat(result).isNull();
        }

        @Test
        void throwsWhenProviderNotFound() {
            assertThatThrownBy(() -> service.getDetailedProviderMetadata(MetadataProvider.Amazon, "item-123"))
                    .isInstanceOf(APIException.class);
        }

        interface DetailedBookParser extends BookParser, DetailedMetadataProvider {}
    }

    @Nested
    class LookupByIsbn {

        @Test
        void returnsFirstResultFromFirstSuccessfulProvider() {
            BookParser googleParser = mock(BookParser.class);
            parserMap.put(MetadataProvider.Google, googleParser);

            AppSettings settings = AppSettings.builder()
                    .defaultMetadataRefreshOptions(MetadataRefreshOptions.builder()
                            .fieldOptions(MetadataRefreshOptions.FieldOptions.builder()
                                    .title(MetadataRefreshOptions.FieldProvider.builder()
                                            .p1(MetadataProvider.Google)
                                            .build())
                                    .build())
                            .build())
                    .build();
            when(appSettingService.getAppSettings()).thenReturn(settings);

            BookMetadata expected = BookMetadata.builder().title("Found").build();
            when(googleParser.fetchMetadata(any(Book.class), any(FetchMetadataRequest.class)))
                    .thenReturn(List.of(expected));

            BookMetadata result = service.lookupByIsbn(IsbnLookupRequest.builder().isbn("978-0123456789").build());

            assertThat(result).isEqualTo(expected);
        }

        @Test
        void skipsFailingProviderAndTriesNext() {
            BookParser googleParser = mock(BookParser.class);
            BookParser amazonParser = mock(BookParser.class);
            parserMap.put(MetadataProvider.Google, googleParser);
            parserMap.put(MetadataProvider.Amazon, amazonParser);

            AppSettings settings = AppSettings.builder()
                    .defaultMetadataRefreshOptions(MetadataRefreshOptions.builder()
                            .fieldOptions(MetadataRefreshOptions.FieldOptions.builder()
                                    .title(MetadataRefreshOptions.FieldProvider.builder()
                                            .p1(MetadataProvider.Google)
                                            .p2(MetadataProvider.Amazon)
                                            .build())
                                    .build())
                            .build())
                    .build();
            when(appSettingService.getAppSettings()).thenReturn(settings);

            when(googleParser.fetchMetadata(any(Book.class), any(FetchMetadataRequest.class)))
                    .thenThrow(new RuntimeException("timeout"));
            BookMetadata expected = BookMetadata.builder().title("Amazon Result").build();
            when(amazonParser.fetchMetadata(any(Book.class), any(FetchMetadataRequest.class)))
                    .thenReturn(List.of(expected));

            BookMetadata result = service.lookupByIsbn(IsbnLookupRequest.builder().isbn("978-0123456789").build());

            assertThat(result).isEqualTo(expected);
        }

        @Test
        void returnsNullWhenAllProvidersFail() {
            BookParser googleParser = mock(BookParser.class);
            parserMap.put(MetadataProvider.Google, googleParser);

            AppSettings settings = AppSettings.builder()
                    .defaultMetadataRefreshOptions(MetadataRefreshOptions.builder()
                            .fieldOptions(MetadataRefreshOptions.FieldOptions.builder()
                                    .title(MetadataRefreshOptions.FieldProvider.builder()
                                            .p1(MetadataProvider.Google)
                                            .build())
                                    .build())
                            .build())
                    .build();
            when(appSettingService.getAppSettings()).thenReturn(settings);
            when(googleParser.fetchMetadata(any(Book.class), any(FetchMetadataRequest.class)))
                    .thenThrow(new RuntimeException("fail"));

            BookMetadata result = service.lookupByIsbn(IsbnLookupRequest.builder().isbn("978-0123456789").build());

            assertThat(result).isNull();
        }

        @Test
        void returnsNullWhenProviderReturnsEmptyList() {
            BookParser googleParser = mock(BookParser.class);
            parserMap.put(MetadataProvider.Google, googleParser);

            AppSettings settings = AppSettings.builder()
                    .defaultMetadataRefreshOptions(MetadataRefreshOptions.builder()
                            .fieldOptions(MetadataRefreshOptions.FieldOptions.builder()
                                    .title(MetadataRefreshOptions.FieldProvider.builder()
                                            .p1(MetadataProvider.Google)
                                            .build())
                                    .build())
                            .build())
                    .build();
            when(appSettingService.getAppSettings()).thenReturn(settings);
            when(googleParser.fetchMetadata(any(Book.class), any(FetchMetadataRequest.class)))
                    .thenReturn(Collections.emptyList());

            BookMetadata result = service.lookupByIsbn(IsbnLookupRequest.builder().isbn("978-0123456789").build());

            assertThat(result).isNull();
        }

        @Test
        void fallsBackToGoogleWhenSettingsHaveNoFieldOptions() {
            BookParser googleParser = mock(BookParser.class);
            parserMap.put(MetadataProvider.Google, googleParser);

            AppSettings settings = AppSettings.builder()
                    .defaultMetadataRefreshOptions(MetadataRefreshOptions.builder()
                            .fieldOptions(null)
                            .build())
                    .build();
            when(appSettingService.getAppSettings()).thenReturn(settings);

            BookMetadata expected = BookMetadata.builder().title("Google").build();
            when(googleParser.fetchMetadata(any(Book.class), any(FetchMetadataRequest.class)))
                    .thenReturn(List.of(expected));

            BookMetadata result = service.lookupByIsbn(IsbnLookupRequest.builder().isbn("978-0123456789").build());

            assertThat(result).isEqualTo(expected);
        }

        @Test
        void fallsBackToGoogleWhenSettingsThrow() {
            BookParser googleParser = mock(BookParser.class);
            parserMap.put(MetadataProvider.Google, googleParser);

            when(appSettingService.getAppSettings()).thenThrow(new RuntimeException("config error"));

            BookMetadata expected = BookMetadata.builder().title("Google").build();
            when(googleParser.fetchMetadata(any(Book.class), any(FetchMetadataRequest.class)))
                    .thenReturn(List.of(expected));

            BookMetadata result = service.lookupByIsbn(IsbnLookupRequest.builder().isbn("978-0123456789").build());

            assertThat(result).isEqualTo(expected);
        }

        @Test
        void fallsBackToGoogleWhenTitleProviderIsNull() {
            BookParser googleParser = mock(BookParser.class);
            parserMap.put(MetadataProvider.Google, googleParser);

            AppSettings settings = AppSettings.builder()
                    .defaultMetadataRefreshOptions(MetadataRefreshOptions.builder()
                            .fieldOptions(MetadataRefreshOptions.FieldOptions.builder()
                                    .title(null)
                                    .build())
                            .build())
                    .build();
            when(appSettingService.getAppSettings()).thenReturn(settings);

            BookMetadata expected = BookMetadata.builder().title("Google").build();
            when(googleParser.fetchMetadata(any(Book.class), any(FetchMetadataRequest.class)))
                    .thenReturn(List.of(expected));

            BookMetadata result = service.lookupByIsbn(IsbnLookupRequest.builder().isbn("978-0123456789").build());

            assertThat(result).isEqualTo(expected);
        }

        @Test
        void deduplicatesProviders() {
            BookParser googleParser = mock(BookParser.class);
            parserMap.put(MetadataProvider.Google, googleParser);

            AppSettings settings = AppSettings.builder()
                    .defaultMetadataRefreshOptions(MetadataRefreshOptions.builder()
                            .fieldOptions(MetadataRefreshOptions.FieldOptions.builder()
                                    .title(MetadataRefreshOptions.FieldProvider.builder()
                                            .p1(MetadataProvider.Google)
                                            .p2(MetadataProvider.Google)
                                            .p3(MetadataProvider.Google)
                                            .build())
                                    .build())
                            .build())
                    .build();
            when(appSettingService.getAppSettings()).thenReturn(settings);
            when(googleParser.fetchMetadata(any(Book.class), any(FetchMetadataRequest.class)))
                    .thenReturn(Collections.emptyList());

            service.lookupByIsbn(IsbnLookupRequest.builder().isbn("978-0123456789").build());

            verify(googleParser, times(1)).fetchMetadata(any(Book.class), any(FetchMetadataRequest.class));
        }

        @Test
        void fallsBackToGoogleWhenDefaultMetadataRefreshOptionsIsNull() {
            BookParser googleParser = mock(BookParser.class);
            parserMap.put(MetadataProvider.Google, googleParser);

            AppSettings settings = AppSettings.builder().defaultMetadataRefreshOptions(null).build();
            when(appSettingService.getAppSettings()).thenReturn(settings);

            BookMetadata expected = BookMetadata.builder().title("Google").build();
            when(googleParser.fetchMetadata(any(Book.class), any(FetchMetadataRequest.class)))
                    .thenReturn(List.of(expected));

            BookMetadata result = service.lookupByIsbn(IsbnLookupRequest.builder().isbn("978-0123456789").build());

            assertThat(result).isEqualTo(expected);
        }
    }

    @Nested
    class ToggleFieldLocks {

        @Test
        void locksSingleField() {
            BookMetadataEntity entity = BookMetadataEntity.builder().bookId(1L).titleLocked(false).build();
            when(bookMetadataRepository.getMetadataForBookIds(List.of(1L))).thenReturn(List.of(entity));

            service.toggleFieldLocks(List.of(1L), Map.of("titleLocked", "LOCK"));

            assertThat(entity.getTitleLocked()).isTrue();
            verify(bookMetadataRepository).saveAll(List.of(entity));
        }

        @Test
        void unlocksSingleField() {
            BookMetadataEntity entity = BookMetadataEntity.builder().bookId(1L).titleLocked(true).build();
            when(bookMetadataRepository.getMetadataForBookIds(List.of(1L))).thenReturn(List.of(entity));

            service.toggleFieldLocks(List.of(1L), Map.of("titleLocked", "UNLOCK"));

            assertThat(entity.getTitleLocked()).isFalse();
            verify(bookMetadataRepository).saveAll(List.of(entity));
        }

        @Test
        void mapsThumbnailLockedToCoverLocked() {
            BookMetadataEntity entity = BookMetadataEntity.builder().bookId(1L).coverLocked(false).build();
            when(bookMetadataRepository.getMetadataForBookIds(List.of(1L))).thenReturn(List.of(entity));

            service.toggleFieldLocks(List.of(1L), Map.of("thumbnailLocked", "LOCK"));

            assertThat(entity.getCoverLocked()).isTrue();
        }

        @Test
        void handlesMultipleBooks() {
            BookMetadataEntity entity1 = BookMetadataEntity.builder().bookId(1L).titleLocked(false).build();
            BookMetadataEntity entity2 = BookMetadataEntity.builder().bookId(2L).titleLocked(false).build();
            when(bookMetadataRepository.getMetadataForBookIds(List.of(1L, 2L))).thenReturn(List.of(entity1, entity2));

            service.toggleFieldLocks(List.of(1L, 2L), Map.of("titleLocked", "LOCK"));

            assertThat(entity1.getTitleLocked()).isTrue();
            assertThat(entity2.getTitleLocked()).isTrue();
        }

        @Test
        void throwsForInvalidField() {
            BookMetadataEntity entity = BookMetadataEntity.builder().bookId(1L).build();
            when(bookMetadataRepository.getMetadataForBookIds(List.of(1L))).thenReturn(List.of(entity));

            assertThatThrownBy(() -> service.toggleFieldLocks(List.of(1L), Map.of("nonExistentField", "LOCK")))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to invoke setter for field: nonExistentField");
        }
    }

    @Nested
    class ToggleAllLock {

        @Test
        void locksAllFieldsForBooks() {
            BookMetadataEntity metadata = BookMetadataEntity.builder().bookId(1L).build();
            BookEntity bookEntity = BookEntity.builder().id(1L).metadata(metadata).build();
            when(bookQueryService.findAllWithMetadataByIds(Set.of(1L))).thenReturn(List.of(bookEntity));
            BookMetadata dto = BookMetadata.builder().build();
            when(bookMetadataMapper.toBookMetadata(metadata, false)).thenReturn(dto);

            ToggleAllLockRequest request = new ToggleAllLockRequest();
            request.setBookIds(Set.of(1L));
            request.setLock(Lock.LOCK);

            List<BookMetadata> result = service.toggleAllLock(request);

            assertThat(result).hasSize(1);
            verify(bookRepository).saveAll(anyList());
        }

        @Test
        void unlocksAllFieldsForBooks() {
            BookMetadataEntity metadata = BookMetadataEntity.builder().bookId(1L).build();
            metadata.applyLockToAllFields(true);
            BookEntity bookEntity = BookEntity.builder().id(1L).metadata(metadata).build();
            when(bookQueryService.findAllWithMetadataByIds(Set.of(1L))).thenReturn(List.of(bookEntity));
            BookMetadata dto = BookMetadata.builder().build();
            when(bookMetadataMapper.toBookMetadata(metadata, false)).thenReturn(dto);

            ToggleAllLockRequest request = new ToggleAllLockRequest();
            request.setBookIds(Set.of(1L));
            request.setLock(Lock.UNLOCK);

            List<BookMetadata> result = service.toggleAllLock(request);

            assertThat(result).hasSize(1);
            assertThat(metadata.getTitleLocked()).isFalse();
            assertThat(metadata.getCoverLocked()).isFalse();
        }
    }

    @Nested
    class GetComicInfoMetadata {

        @Test
        void throwsWhenBookNotFound() {
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getComicInfoMetadata(1L))
                    .isInstanceOf(APIException.class);
        }

        @Test
        void returnsNullWhenPrimaryFileIsNull() {
            BookEntity bookEntity = BookEntity.builder().id(1L).bookFiles(new ArrayList<>()).build();
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(bookEntity));

            BookMetadata result = service.getComicInfoMetadata(1L);

            assertThat(result).isNull();
        }

        @Test
        void returnsNullWhenFileTypeIsNotCbx() {
            BookFileEntity file = BookFileEntity.builder().bookType(BookFileType.PDF).isBookFormat(true).build();
            BookEntity bookEntity = BookEntity.builder().id(1L).bookFiles(List.of(file)).build();
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(bookEntity));

            BookMetadata result = service.getComicInfoMetadata(1L);

            assertThat(result).isNull();
        }
    }

    @Nested
    class GetFileMetadata {

        @Test
        void throwsWhenBookNotFound() {
            when(bookRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getFileMetadata(1L))
                    .isInstanceOf(APIException.class);
        }

        @Test
        void throwsWhenPrimaryFileIsNull() {
            BookEntity bookEntity = BookEntity.builder().id(1L).bookFiles(new ArrayList<>()).build();
            when(bookRepository.findById(1L)).thenReturn(Optional.of(bookEntity));

            assertThatThrownBy(() -> service.getFileMetadata(1L))
                    .isInstanceOf(APIException.class);
        }
    }

    @Nested
    class BulkUpdateMetadata {

        @Test
        void processesEachBookId() {
            BulkMetadataUpdateRequest request = new BulkMetadataUpdateRequest();
            request.setBookIds(Set.of(1L, 2L));
            request.setGenres(Set.of("Fiction"));
            request.setMoods(Set.of("Dark"));
            request.setTags(Set.of("Favorite"));

            MetadataClearFlags clearFlags = new MetadataClearFlags();
            when(metadataClearFlagsMapper.toClearFlags(request)).thenReturn(clearFlags);

            BookEntity book1 = BookEntity.builder().id(1L).build();
            BookEntity book2 = BookEntity.builder().id(2L).build();
            when(bookRepository.findByIdFull(1L)).thenReturn(Optional.of(book1));
            when(bookRepository.findByIdFull(2L)).thenReturn(Optional.of(book2));

            var txStatus = mock(org.springframework.transaction.TransactionStatus.class);
            when(transactionManager.getTransaction(any())).thenReturn(txStatus);

            service.bulkUpdateMetadata(request, true, false, true);

            verify(bookMetadataUpdater, times(2)).setBookMetadata(any());
            verify(notificationService, times(2)).sendMessage(any(), any());
        }

        @Test
        void continuesProcessingWhenOneBookFails() {
            BulkMetadataUpdateRequest request = new BulkMetadataUpdateRequest();
            // Use a LinkedHashSet to maintain order
            Set<Long> bookIds = new LinkedHashSet<>();
            bookIds.add(1L);
            bookIds.add(2L);
            request.setBookIds(bookIds);

            MetadataClearFlags clearFlags = new MetadataClearFlags();
            when(metadataClearFlagsMapper.toClearFlags(request)).thenReturn(clearFlags);

            var txStatus = mock(org.springframework.transaction.TransactionStatus.class);
            when(transactionManager.getTransaction(any())).thenReturn(txStatus);

            BookEntity book2 = BookEntity.builder().id(2L).build();
            when(bookRepository.findByIdFull(1L)).thenReturn(Optional.empty());
            when(bookRepository.findByIdFull(2L)).thenReturn(Optional.of(book2));

            service.bulkUpdateMetadata(request, false, false, false);

            verify(bookMetadataUpdater, times(1)).setBookMetadata(any());
        }

        @Test
        void usesEmptySetWhenGenresMoodsTagsAreNull() {
            BulkMetadataUpdateRequest request = new BulkMetadataUpdateRequest();
            request.setBookIds(Set.of(1L));
            request.setGenres(null);
            request.setMoods(null);
            request.setTags(null);

            MetadataClearFlags clearFlags = new MetadataClearFlags();
            when(metadataClearFlagsMapper.toClearFlags(request)).thenReturn(clearFlags);

            var txStatus = mock(org.springframework.transaction.TransactionStatus.class);
            when(transactionManager.getTransaction(any())).thenReturn(txStatus);

            BookEntity book = BookEntity.builder().id(1L).build();
            when(bookRepository.findByIdFull(1L)).thenReturn(Optional.of(book));

            service.bulkUpdateMetadata(request, false, false, false);

            verify(bookMetadataUpdater).setBookMetadata(argThat(ctx -> {
                BookMetadata md = ctx.getMetadataUpdateWrapper().getMetadata();
                return md.getCategories().isEmpty() && md.getMoods().isEmpty() && md.getTags().isEmpty();
            }));
        }
    }

    @Nested
    class GetProspectiveMetadataListForBookId {

        @Test
        void throwsWhenBookNotFound() {
            when(bookRepository.findByIdWithBookFiles(99L)).thenReturn(Optional.empty());
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .providers(List.of(MetadataProvider.Google))
                    .build();

            assertThatThrownBy(() -> service.getProspectiveMetadataListForBookId(99L, request))
                    .isInstanceOf(APIException.class);
        }
    }
}
