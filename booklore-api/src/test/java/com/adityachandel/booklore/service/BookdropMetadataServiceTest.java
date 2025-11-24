package com.adityachandel.booklore.service;

import com.adityachandel.booklore.exception.APIException;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.settings.AppSettings;
import com.adityachandel.booklore.model.entity.BookdropFileEntity;
import com.adityachandel.booklore.model.enums.BookFileExtension;
import com.adityachandel.booklore.model.enums.MetadataProvider;
import com.adityachandel.booklore.repository.BookdropFileRepository;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.bookdrop.BookdropMetadataService;
import com.adityachandel.booklore.service.metadata.MetadataRefreshService;
import com.adityachandel.booklore.service.metadata.extractor.CbxMetadataExtractor;
import com.adityachandel.booklore.service.metadata.extractor.EpubMetadataExtractor;
import com.adityachandel.booklore.service.metadata.extractor.MetadataExtractorFactory;
import com.adityachandel.booklore.service.metadata.extractor.PdfMetadataExtractor;
import com.adityachandel.booklore.util.FileService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.adityachandel.booklore.model.entity.BookdropFileEntity.Status.PENDING_REVIEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookdropMetadataServiceTest {

    @Mock
    private BookdropFileRepository bookdropFileRepository;
    @Mock
    private AppSettingService appSettingService;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private EpubMetadataExtractor epubMetadataExtractor;
    @Mock
    private PdfMetadataExtractor pdfMetadataExtractor;
    @Mock
    private CbxMetadataExtractor cbxMetadataExtractor;
    @Mock
    private MetadataRefreshService metadataRefreshService;
    @Mock
    private FileService fileService;
    @Mock
    private MetadataExtractorFactory metadataExtractorFactory;

    @InjectMocks
    private BookdropMetadataService bookdropMetadataService;

    private BookdropFileEntity sampleFile;

    @BeforeEach
    void setup() {
        sampleFile = new BookdropFileEntity();
        sampleFile.setId(1L);
        sampleFile.setFileName("book.epub");
        sampleFile.setFilePath("/tmp/book.epub");
    }

    @Test
    void attachInitialMetadata_shouldExtractAndSaveMetadata() throws Exception {
        BookMetadata metadata = BookMetadata.builder().title("Test Book").build();

        when(bookdropFileRepository.findById(1L)).thenReturn(Optional.of(sampleFile));
        when(metadataExtractorFactory.extractMetadata(eq(BookFileExtension.EPUB), any(File.class))).thenReturn(metadata);
        when(objectMapper.writeValueAsString(any(BookMetadata.class))).thenReturn("{\"title\":\"Test Book\"}");
        when(bookdropFileRepository.save(any(BookdropFileEntity.class))).thenReturn(sampleFile);

        BookdropFileEntity result = bookdropMetadataService.attachInitialMetadata(1L);

        assertThat(result).isNotNull();
        assertThat(result.getOriginalMetadata()).contains("Test Book");
        assertThat(result.getUpdatedAt()).isBeforeOrEqualTo(Instant.now());
        verify(bookdropFileRepository).save(any(BookdropFileEntity.class));
    }

    @Test
    void attachInitialMetadata_shouldThrowWhenFileMissing() {
        when(bookdropFileRepository.findById(99L)).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> bookdropMetadataService.attachInitialMetadata(99L));
    }

    @Test
    void attachFetchedMetadata_shouldUpdateEntityWithFetchedData() throws Exception {
        sampleFile.setOriginalMetadata("{\"title\":\"Old Book\"}");
        AppSettings settings = new AppSettings();
        BookMetadata fetched = BookMetadata.builder().title("New Title").build();

        when(bookdropFileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(bookdropFileRepository.findById(1L)).thenReturn(Optional.of(sampleFile));
        when(appSettingService.getAppSettings()).thenReturn(settings);
        when(metadataRefreshService.prepareProviders(any())).thenReturn(List.of());
        when(objectMapper.readValue(sampleFile.getOriginalMetadata(), BookMetadata.class)).thenReturn(fetched);
        when(metadataRefreshService.fetchMetadataForBook(any(), any(Book.class))).thenReturn(Map.of());
        when(metadataRefreshService.buildFetchMetadata(any(), any(), any())).thenReturn(fetched);
        when(objectMapper.writeValueAsString(fetched)).thenReturn("{\"title\":\"New Title\"}");

        BookdropFileEntity result = bookdropMetadataService.attachFetchedMetadata(1L);

        assertThat(result.getFetchedMetadata()).contains("New Title");
        assertThat(result.getStatus()).isEqualTo(PENDING_REVIEW);
        verify(bookdropFileRepository).save(result);
    }

    @Test
    void attachInitialMetadata_shouldHandleNullCoverGracefully() throws Exception {
        BookMetadata metadata = BookMetadata.builder().title("No Cover Book").build();

        when(bookdropFileRepository.findById(1L)).thenReturn(Optional.of(sampleFile));
        when(metadataExtractorFactory.extractMetadata(eq(BookFileExtension.EPUB), any(File.class))).thenReturn(metadata);
        when(objectMapper.writeValueAsString(metadata)).thenReturn("{\"title\":\"No Cover Book\"}");
        when(bookdropFileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BookdropFileEntity result = bookdropMetadataService.attachInitialMetadata(1L);

        assertThat(result.getOriginalMetadata()).contains("No Cover Book");
        verify(bookdropFileRepository).save(result);
    }

    @Test
    void extractInitialMetadata_shouldThrowForUnsupportedFileExtension() {
        sampleFile.setFileName("book.txt");
        sampleFile.setFilePath("/tmp/book.txt");

        when(bookdropFileRepository.findById(sampleFile.getId())).thenReturn(Optional.of(sampleFile));

        assertThatThrownBy(() -> bookdropMetadataService.attachInitialMetadata(sampleFile.getId())).isInstanceOf(APIException.class)
                .hasMessageContaining("Invalid file format");
    }

    @Test
    void attachFetchedMetadata_shouldSleepIfGoodreadsIncluded() throws Exception {
        sampleFile.setOriginalMetadata("{\"title\":\"Book\"}");
        AppSettings settings = new AppSettings();
        BookMetadata fetched = BookMetadata.builder().title("Fetched Book").build();

        when(bookdropFileRepository.findById(1L)).thenReturn(Optional.of(sampleFile));
        when(appSettingService.getAppSettings()).thenReturn(settings);
        when(metadataRefreshService.prepareProviders(any())).thenReturn(List.of(MetadataProvider.GoodReads));
        when(objectMapper.readValue(anyString(), eq(BookMetadata.class))).thenReturn(fetched);
        when(metadataRefreshService.fetchMetadataForBook(any(), any(Book.class))).thenReturn(Map.of());
        when(metadataRefreshService.buildFetchMetadata(any(), any(), any())).thenReturn(fetched);
        when(objectMapper.writeValueAsString(fetched)).thenReturn("{\"title\":\"Fetched Book\"}");
        when(bookdropFileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BookdropFileEntity result = bookdropMetadataService.attachFetchedMetadata(1L);

        assertThat(result.getFetchedMetadata()).contains("Fetched Book");
        assertThat(result.getStatus()).isEqualTo(PENDING_REVIEW);
        verify(bookdropFileRepository).save(result);
    }

    @Test
    void attachFetchedMetadata_shouldThrowOnJsonProcessingError() throws Exception {
        sampleFile.setOriginalMetadata("{invalidJson}");

        when(bookdropFileRepository.findById(1L)).thenReturn(Optional.of(sampleFile));
        when(appSettingService.getAppSettings()).thenReturn(new AppSettings());
        when(objectMapper.readValue(anyString(), eq(BookMetadata.class)))
                .thenThrow(new JsonProcessingException("Invalid JSON") {
                });

        assertThatThrownBy(() -> bookdropMetadataService.attachFetchedMetadata(1L))
                .isInstanceOf(JsonProcessingException.class);
    }
}