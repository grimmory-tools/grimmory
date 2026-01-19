package com.adityachandel.booklore.mapper.komga;

import com.adityachandel.booklore.context.KomgaCleanContext;
import com.adityachandel.booklore.model.dto.komga.KomgaBookDto;
import com.adityachandel.booklore.model.dto.komga.KomgaSeriesDto;
import com.adityachandel.booklore.model.dto.settings.AppSettings;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookFileEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KomgaMapperTest {

    @Mock
    private AppSettingService appSettingService;

    @InjectMocks
    private KomgaMapper mapper;
    
    @AfterEach
    void cleanup() {
        // Always clean up the context after each test
        KomgaCleanContext.clear();
    }
    
    @BeforeEach
    void setUp() {
        // Mock app settings for all tests
        AppSettings appSettings = new AppSettings();
        appSettings.setKomgaGroupUnknown(true);
        when(appSettingService.getAppSettings()).thenReturn(appSettings);
    }

    @Test
    void shouldHandleNullPageCountInMetadata() {
        // Given: A book with metadata that has null pageCount
        LibraryEntity library = new LibraryEntity();
        library.setId(1L);

        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("Test Book")
                .seriesName("Test Series")
                .pageCount(null)  // Explicitly null
                .build();

        BookEntity book = new BookEntity();
        book.setId(100L);
        book.setLibrary(library);
        book.setMetadata(metadata);
        book.setAddedOn(Instant.now());

        BookFileEntity pdf = new BookFileEntity();
        pdf.setId(100L);
        pdf.setBook(book);
        pdf.setFileSubPath("author/title");
        pdf.setFileName("test-book.pdf");
        pdf.setBookType(BookFileType.PDF);
        pdf.setBookFormat(true);

        book.setBookFiles(List.of(pdf));

        // When: Converting to DTO
        KomgaBookDto dto = mapper.toKomgaBookDto(book);

        // Then: Should not throw NPE and pageCount should default to 0
        assertThat(dto).isNotNull();
        assertThat(dto.getMedia()).isNotNull();
        assertThat(dto.getMedia().getPagesCount()).isEqualTo(0);
    }

    @Test
    void shouldHandleNullMetadata() {
        // Given: A book with null metadata
        LibraryEntity library = new LibraryEntity();
        library.setId(1L);

        BookEntity book = new BookEntity();
        book.setId(100L);
        book.setLibrary(library);
        book.setMetadata(null);  // Null metadata
        book.setAddedOn(Instant.now());

        BookFileEntity pdf = new BookFileEntity();
        pdf.setId(100L);
        pdf.setBook(book);
        pdf.setFileSubPath("author/title");
        pdf.setFileName("test-book.pdf");
        pdf.setBookType(BookFileType.PDF);
        pdf.setBookFormat(true);

        book.setBookFiles(List.of(pdf));

        // When: Converting to DTO
        KomgaBookDto dto = mapper.toKomgaBookDto(book);

        // Then: Should not throw NPE and pageCount should default to 0
        assertThat(dto).isNotNull();
        assertThat(dto.getMedia()).isNotNull();
        assertThat(dto.getMedia().getPagesCount()).isEqualTo(0);
    }

    @Test
    void shouldHandleValidPageCount() {
        // Given: A book with metadata that has valid pageCount
        LibraryEntity library = new LibraryEntity();
        library.setId(1L);

        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("Test Book")
                .seriesName("Test Series")
                .pageCount(250)
                .build();

        BookEntity book = new BookEntity();
        book.setId(100L);
        book.setLibrary(library);
        book.setMetadata(metadata);
        book.setAddedOn(Instant.now());

        BookFileEntity pdf = new BookFileEntity();
        pdf.setId(100L);
        pdf.setBook(book);
        pdf.setFileSubPath("author/title");
        pdf.setFileName("test-book.pdf");
        pdf.setBookType(BookFileType.PDF);
        pdf.setBookFormat(true);

        book.setBookFiles(List.of(pdf));

        // When: Converting to DTO
        KomgaBookDto dto = mapper.toKomgaBookDto(book);

        // Then: Should use the actual pageCount
        assertThat(dto).isNotNull();
        assertThat(dto.getMedia()).isNotNull();
        assertThat(dto.getMedia().getPagesCount()).isEqualTo(250);
    }
    
    @Test
    void shouldReturnNullForEmptyFieldsInCleanMode() {
        // Given: Clean mode is enabled
        KomgaCleanContext.setCleanMode(true);
        
        LibraryEntity library = new LibraryEntity();
        library.setId(1L);
        
        List<BookEntity> books = new ArrayList<>();
        BookEntity book = new BookEntity();
        book.setId(100L);
        book.setLibrary(library);
        book.setAddedOn(Instant.now());

        BookFileEntity pdf = new BookFileEntity();
        pdf.setId(100L);
        pdf.setBook(book);
        pdf.setFileSubPath("author/title");
        pdf.setFileName("test-book.pdf");
        pdf.setBookType(BookFileType.PDF);
        pdf.setBookFormat(true);

        book.setBookFiles(List.of(pdf));
        
        // Book with metadata but empty fields
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("Test Book")
                .seriesName("Test Series")
                .description(null)
                .language(null)
                .publisher(null)
                .build();
        book.setMetadata(metadata);
        books.add(book);
        
        // When: Converting to series DTO
        KomgaSeriesDto seriesDto = mapper.toKomgaSeriesDto("Test Series", 1L, books);
        
        // Then: Empty fields should be null (not empty strings) in clean mode
        assertThat(seriesDto).isNotNull();
        assertThat(seriesDto.getMetadata()).isNotNull();
        assertThat(seriesDto.getMetadata().getSummary()).isNull();
        assertThat(seriesDto.getMetadata().getLanguage()).isNull();
        assertThat(seriesDto.getMetadata().getPublisher()).isNull();
    }
    
    @Test
    void shouldReturnDefaultValuesWhenCleanModeDisabled() {
        // Given: Clean mode is disabled (default)
        KomgaCleanContext.setCleanMode(false);
        
        LibraryEntity library = new LibraryEntity();
        library.setId(1L);
        
        List<BookEntity> books = new ArrayList<>();
        BookEntity book = new BookEntity();
        book.setId(100L);
        book.setLibrary(library);
        book.setAddedOn(Instant.now());

        BookFileEntity pdf = new BookFileEntity();
        pdf.setId(100L);
        pdf.setBook(book);
        pdf.setFileSubPath("author/title");
        pdf.setFileName("test-book.pdf");
        pdf.setBookType(BookFileType.PDF);
        pdf.setBookFormat(true);

        book.setBookFiles(List.of(pdf));
        
        // Book with metadata but empty fields
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("Test Book")
                .seriesName("Test Series")
                .description(null)
                .language(null)
                .publisher(null)
                .build();
        book.setMetadata(metadata);
        books.add(book);
        
        // When: Converting to series DTO
        KomgaSeriesDto seriesDto = mapper.toKomgaSeriesDto("Test Series", 1L, books);
        
        // Then: Empty fields should have default values (not null)
        assertThat(seriesDto).isNotNull();
        assertThat(seriesDto.getMetadata()).isNotNull();
        assertThat(seriesDto.getMetadata().getSummary()).isEqualTo("");
        assertThat(seriesDto.getMetadata().getLanguage()).isEqualTo("en");
        assertThat(seriesDto.getMetadata().getPublisher()).isEqualTo("");
    }
}