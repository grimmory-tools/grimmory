package com.adityachandel.booklore.service.bookdrop;

import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.request.BookdropPatternExtractRequest;
import com.adityachandel.booklore.model.dto.response.BookdropPatternExtractResult;
import com.adityachandel.booklore.model.entity.BookdropFileEntity;
import com.adityachandel.booklore.repository.BookdropFileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FilenamePatternExtractorTest {

    @Mock
    private BookdropFileRepository bookdropFileRepository;

    @Mock
    private BookdropMetadataHelper metadataHelper;

    @InjectMocks
    private FilenamePatternExtractor extractor;

    private BookdropFileEntity createFileEntity(Long id, String fileName) {
        BookdropFileEntity entity = new BookdropFileEntity();
        entity.setId(id);
        entity.setFileName(fileName);
        entity.setFilePath("/bookdrop/" + fileName);
        return entity;
    }

    @Test
    void extractFromFilename_WithSeriesAndChapter_ShouldExtractBoth() {
        String filename = "Chronicles of Earth - Ch 25.cbz";
        String pattern = "{SeriesName} - Ch {SeriesNumber}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("Chronicles of Earth", result.getSeriesName());
        assertEquals(25.0f, result.getSeriesNumber());
    }

    @Test
    void extractFromFilename_WithVolumeAndIssuePattern_ShouldExtractCorrectly() {
        String filename = "Chronicles of Earth Vol.3 (of 150).cbz";
        String pattern = "{SeriesName} Vol.{SeriesNumber} (of {SeriesTotal})";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("Chronicles of Earth", result.getSeriesName());
        assertEquals(3.0f, result.getSeriesNumber());
        assertEquals(150, result.getSeriesTotal());
    }

    @Test
    void extractFromFilename_WithPublishedYearPattern_ShouldExtractYear() {
        String filename = "Chronicles of Earth (2016) 001.cbz";
        String pattern = "{SeriesName} ({Published:yyyy}) {SeriesNumber}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("Chronicles of Earth", result.getSeriesName());
        assertEquals(2016, result.getPublishedDate().getYear());
        assertEquals(1.0f, result.getSeriesNumber());
    }

    @Test
    void extractFromFilename_WithAuthorAndTitle_ShouldExtractBoth() {
        String filename = "John Smith - The Lost City.epub";
        String pattern = "{Authors} - {Title}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals(Set.of("John Smith"), result.getAuthors());
        assertEquals("The Lost City", result.getTitle());
    }

    @Test
    void extractFromFilename_WithMultipleAuthors_ShouldParseAll() {
        String filename = "John Smith, Jane Doe - The Lost City.epub";
        String pattern = "{Authors} - {Title}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertTrue(result.getAuthors().contains("John Smith"));
        assertTrue(result.getAuthors().contains("Jane Doe"));
        assertEquals("The Lost City", result.getTitle());
    }

    @Test
    void extractFromFilename_WithDecimalSeriesNumber_ShouldParseCorrectly() {
        String filename = "Chronicles of Earth - Ch 10.5.cbz";
        String pattern = "{SeriesName} - Ch {SeriesNumber}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("Chronicles of Earth", result.getSeriesName());
        assertEquals(10.5f, result.getSeriesNumber());
    }

    @Test
    void extractFromFilename_WithNonMatchingPattern_ShouldReturnNull() {
        String filename = "Random File Name.pdf";
        String pattern = "{SeriesName} - Ch {SeriesNumber}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNull(result);
    }

    @Test
    void extractFromFilename_WithNullPattern_ShouldReturnNull() {
        String filename = "Test File.pdf";

        BookMetadata result = extractor.extractFromFilename(filename, null);

        assertNull(result);
    }

    @Test
    void extractFromFilename_WithEmptyPattern_ShouldReturnNull() {
        String filename = "Test File.pdf";

        BookMetadata result = extractor.extractFromFilename(filename, "");

        assertNull(result);
    }

    @Test
    void extractFromFilename_WithPublisherYearAndIssue_ShouldExtractAll() {
        String filename = "Epic Press - Chronicles of Earth #001 (2011).cbz";
        String pattern = "{Publisher} - {SeriesName} #{SeriesNumber} ({Published:yyyy})";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("Epic Press", result.getPublisher());
        assertEquals("Chronicles of Earth", result.getSeriesName());
        assertEquals(1.0f, result.getSeriesNumber());
        assertEquals(2011, result.getPublishedDate().getYear());
    }

    @Test
    void extractFromFilename_WithLanguageTag_ShouldExtractLanguage() {
        String filename = "Chronicles of Earth - Ch 500 [EN].cbz";
        String pattern = "{SeriesName} - Ch {SeriesNumber} [{Language}]";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("Chronicles of Earth", result.getSeriesName());
        assertEquals(500.0f, result.getSeriesNumber());
        assertEquals("EN", result.getLanguage());
    }

    @Test
    void bulkExtract_WithPreviewMode_ShouldReturnExtractionResults() {
        BookdropFileEntity file1 = createFileEntity(1L, "Chronicles A - Ch 1.cbz");
        BookdropFileEntity file2 = createFileEntity(2L, "Chronicles B - Ch 2.cbz");
        BookdropFileEntity file3 = createFileEntity(3L, "Random Name.cbz");

        BookdropPatternExtractRequest request = new BookdropPatternExtractRequest();
        request.setPattern("{SeriesName} - Ch {SeriesNumber}");
        request.setSelectAll(false);
        request.setSelectedIds(List.of(1L, 2L, 3L));
        request.setPreview(true);

        when(metadataHelper.resolveFileIds(false, null, List.of(1L, 2L, 3L)))
                .thenReturn(List.of(1L, 2L, 3L));
        when(bookdropFileRepository.findAllById(anyList())).thenReturn(List.of(file1, file2, file3));

        BookdropPatternExtractResult result = extractor.bulkExtract(request);

        assertNotNull(result);
        assertEquals(3, result.getTotalFiles());
        assertEquals(2, result.getSuccessfullyExtracted());
        assertEquals(1, result.getFailed());

        var successResults = result.getResults().stream()
                .filter(BookdropPatternExtractResult.FileExtractionResult::isSuccess)
                .toList();
        assertEquals(2, successResults.size());
    }

    @Test
    void bulkExtract_WithFullExtraction_ShouldProcessAndPersistAll() {
        BookdropFileEntity file1 = createFileEntity(1L, "Chronicles A - Ch 1.cbz");
        BookdropFileEntity file2 = createFileEntity(2L, "Chronicles B - Ch 2.cbz");
        BookdropFileEntity file3 = createFileEntity(3L, "Random Name.cbz");

        BookdropPatternExtractRequest request = new BookdropPatternExtractRequest();
        request.setPattern("{SeriesName} - Ch {SeriesNumber}");
        request.setSelectAll(false);
        request.setSelectedIds(List.of(1L, 2L, 3L));
        request.setPreview(false);

        when(metadataHelper.resolveFileIds(false, null, List.of(1L, 2L, 3L)))
                .thenReturn(List.of(1L, 2L, 3L));
        when(bookdropFileRepository.findAllById(anyList())).thenReturn(List.of(file1, file2, file3));
        when(metadataHelper.getCurrentMetadata(any())).thenReturn(new BookMetadata());

        BookdropPatternExtractResult result = extractor.bulkExtract(request);

        assertNotNull(result);
        assertEquals(3, result.getTotalFiles());
        assertEquals(2, result.getSuccessfullyExtracted());
        assertEquals(1, result.getFailed());

        // Verify metadata was updated for successful extractions (2 files matched pattern)
        verify(metadataHelper, times(2)).updateFetchedMetadata(any(), any());
        // Verify all files were saved (even the one that failed extraction keeps original metadata)
        verify(bookdropFileRepository, times(1)).saveAll(anyList());
    }

    @Test
    void extractFromFilename_WithSpecialCharacters_ShouldHandleCorrectly() {
        String filename = "Chronicles (Special Edition) - Ch 5.cbz";
        String pattern = "{SeriesName} - Ch {SeriesNumber}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("Chronicles (Special Edition)", result.getSeriesName());
        assertEquals(5.0f, result.getSeriesNumber());
    }

    // ===== Greedy Matching Tests =====

    @Test
    void extractFromFilename_SeriesNameOnly_ShouldCaptureFullName() {
        String filename = "Chronicles of Earth.cbz";
        String pattern = "{SeriesName}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("Chronicles of Earth", result.getSeriesName());
    }

    @Test
    void extractFromFilename_TitleOnly_ShouldCaptureFullTitle() {
        String filename = "The Last Kingdom.epub";
        String pattern = "{Title}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("The Last Kingdom", result.getTitle());
    }

    // ===== Complex Pattern Tests =====

    @Test
    void extractFromFilename_SeriesNumberAndTitle_ShouldExtractBoth() {
        String filename = "Chronicles of Earth 01 - The Beginning.epub";
        String pattern = "{SeriesName} {SeriesNumber} - {Title}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("Chronicles of Earth", result.getSeriesName());
        assertEquals(1.0f, result.getSeriesNumber());
        assertEquals("The Beginning", result.getTitle());
    }

    @Test
    void extractFromFilename_AuthorSeriesTitleFormat_ShouldExtractAll() {
        String filename = "Chronicles of Earth 07 - The Final Battle - John Smith.epub";
        String pattern = "{SeriesName} {SeriesNumber} - {Title} - {Authors}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("Chronicles of Earth", result.getSeriesName());
        assertEquals(7.0f, result.getSeriesNumber());
        assertEquals("The Final Battle", result.getTitle());
        assertEquals(Set.of("John Smith"), result.getAuthors());
    }

    @Test
    void extractFromFilename_AuthorTitleYear_ShouldExtractAll() {
        String filename = "John Smith - The Lost City (1949).epub";
        String pattern = "{Authors} - {Title} ({Published:yyyy})";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals(Set.of("John Smith"), result.getAuthors());
        assertEquals("The Lost City", result.getTitle());
        assertEquals(1949, result.getPublishedDate().getYear());
    }

    @Test
    void extractFromFilename_AuthorWithCommas_ShouldParseProperly() {
        String filename = "Smith, John R. - The Lost City.epub";
        String pattern = "{Authors} - {Title}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals(Set.of("Smith", "John R."), result.getAuthors());
        assertEquals("The Lost City", result.getTitle());
    }

    @Test
    void extractFromFilename_PartNumberFormat_ShouldExtractCorrectly() {
        String filename = "Chronicles of Earth - Part 2 - Rising Darkness.epub";
        String pattern = "{SeriesName} - Part {SeriesNumber} - {Title}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("Chronicles of Earth", result.getSeriesName());
        assertEquals(2.0f, result.getSeriesNumber());
        assertEquals("Rising Darkness", result.getTitle());
    }

    @Test
    void extractFromFilename_PublisherBracketFormat_ShouldExtractCorrectly() {
        String filename = "[Epic Press] Chronicles of Earth Vol.5 [5 of 20].epub";
        String pattern = "[{Publisher}] {SeriesName} Vol.{SeriesNumber} [* of {SeriesTotal}]";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("Epic Press", result.getPublisher());
        assertEquals("Chronicles of Earth", result.getSeriesName());
        assertEquals(5.0f, result.getSeriesNumber());
        assertEquals(20, result.getSeriesTotal());
    }

    @Test
    void extractFromFilename_CalibreStyleFormat_ShouldExtractCorrectly() {
        String filename = "Chronicles of Earth 01 The Beginning - John Smith.epub";
        String pattern = "{SeriesName} {SeriesNumber} {Title} - {Authors}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("Chronicles of Earth", result.getSeriesName());
        assertEquals(1.0f, result.getSeriesNumber());
        assertEquals("The Beginning", result.getTitle());
        assertEquals(Set.of("John Smith"), result.getAuthors());
    }

    // ===== New Placeholder Tests =====

    @Test
    void extractFromFilename_WithSubtitle_ShouldExtractBoth() {
        String filename = "The Lost City - A Tale of Adventure.epub";
        String pattern = "{Title} - {Subtitle}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("The Lost City", result.getTitle());
        assertEquals("A Tale of Adventure", result.getSubtitle());
    }

    @Test
    void extractFromFilename_WithISBN13_ShouldExtractISBN13() {
        String filename = "The Lost City [1234567890123].epub";
        String pattern = "{Title} [{ISBN13}]";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("The Lost City", result.getTitle());
        assertEquals("1234567890123", result.getIsbn13());
    }

    @Test
    void extractFromFilename_WithISBN10_ShouldExtractCorrectly() {
        String filename = "Chronicles of Tomorrow - 0553293354.epub";
        String pattern = "{Title} - {ISBN10}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("Chronicles of Tomorrow", result.getTitle());
        assertEquals("0553293354", result.getIsbn10());
    }

    @Test
    void extractFromFilename_WithISBN10EndingInX_ShouldExtractCorrectly() {
        String filename = "Test Book - 043942089X.epub";
        String pattern = "{Title} - {ISBN10}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("Test Book", result.getTitle());
        assertEquals("043942089X", result.getIsbn10());
    }

    @Test
    void extractFromFilename_WithASIN_ShouldExtractCorrectly() {
        String filename = "Chronicles of Earth - B001234567.epub";
        String pattern = "{Title} - {ASIN}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("Chronicles of Earth", result.getTitle());
        assertEquals("B001234567", result.getAsin());
    }

    // ===== Published Date Format Tests =====

    @Test
    void extractFromFilename_WithPublishedDateYYYYMMDD_ShouldExtractCorrectly() {
        String filename = "The Lost City - 1925-04-10.epub";
        String pattern = "{Title} - {Published:yyyy-MM-dd}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("The Lost City", result.getTitle());
        assertEquals(1925, result.getPublishedDate().getYear());
        assertEquals(4, result.getPublishedDate().getMonthValue());
        assertEquals(10, result.getPublishedDate().getDayOfMonth());
    }

    @Test
    void extractFromFilename_WithPublishedDateCompact_ShouldExtractCorrectly() {
        String filename = "Chronicles of Tomorrow_19650801.epub";
        String pattern = "{Title}_{Published:yyyyMMdd}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("Chronicles of Tomorrow", result.getTitle());
        assertEquals(1965, result.getPublishedDate().getYear());
        assertEquals(8, result.getPublishedDate().getMonthValue());
        assertEquals(1, result.getPublishedDate().getDayOfMonth());
    }

    @Test
    void extractFromFilename_WithPublishedDateDots_ShouldExtractCorrectly() {
        String filename = "Chronicles of Tomorrow (1951.05.01).epub";
        String pattern = "{Title} ({Published:yyyy.MM.dd})";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("Chronicles of Tomorrow", result.getTitle());
        assertEquals(1951, result.getPublishedDate().getYear());
        assertEquals(5, result.getPublishedDate().getMonthValue());
        assertEquals(1, result.getPublishedDate().getDayOfMonth());
    }

    @Test
    void extractFromFilename_WithPublishedDateDashes_ShouldExtractCorrectly() {
        String filename = "Chronicles of Earth [05-15-2020].epub";
        String pattern = "{Title} [{Published:MM-dd-yyyy}]";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("Chronicles of Earth", result.getTitle());
        assertEquals(2020, result.getPublishedDate().getYear());
        assertEquals(5, result.getPublishedDate().getMonthValue());
        assertEquals(15, result.getPublishedDate().getDayOfMonth());
    }

    @Test
    void extractFromFilename_WithPublishedDateSingleDigits_ShouldExtractCorrectly() {
        String filename = "Chronicles of Earth - 2023-1-5.epub";
        String pattern = "{Title} - {Published:yyyy-M-d}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("Chronicles of Earth", result.getTitle());
        assertEquals(2023, result.getPublishedDate().getYear());
        assertEquals(1, result.getPublishedDate().getMonthValue());
        assertEquals(5, result.getPublishedDate().getDayOfMonth());
    }

    @Test
    void extractFromFilename_ComplexPatternWithMultiplePlaceholders_ShouldExtractAll() {
        String filename = "Chronicles of Earth - The Beginning [1234567890123] - 2020-05-15.epub";
        String pattern = "{SeriesName} - {Title} [{ISBN13}] - {Published:yyyy-MM-dd}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("Chronicles of Earth", result.getSeriesName());
        assertEquals("The Beginning", result.getTitle());
        assertEquals("1234567890123", result.getIsbn13());
        assertEquals(2020, result.getPublishedDate().getYear());
        assertEquals(5, result.getPublishedDate().getMonthValue());
        assertEquals(15, result.getPublishedDate().getDayOfMonth());
    }

    @Test
    void extractFromFilename_PublishedWithoutFormat_AutoDetectsISODate() {
        String filename = "The Lost City (2023-05-15).epub";
        String pattern = "{Title} ({Published})";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("The Lost City", result.getTitle());
        assertEquals(2023, result.getPublishedDate().getYear());
        assertEquals(5, result.getPublishedDate().getMonthValue());
        assertEquals(15, result.getPublishedDate().getDayOfMonth());
    }

    @Test
    void extractFromFilename_PublishedWithoutFormat_AutoDetectsCompactDate() {
        String filename = "The Beginning [20231225].epub";
        String pattern = "{Title} [{Published}]";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("The Beginning", result.getTitle());
        assertEquals(2023, result.getPublishedDate().getYear());
        assertEquals(12, result.getPublishedDate().getMonthValue());
        assertEquals(25, result.getPublishedDate().getDayOfMonth());
    }

    @Test
    void extractFromFilename_PublishedWithoutFormat_AutoDetectsYear() {
        String filename = "The Lost City (2023).epub";
        String pattern = "{Title} ({Published})";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("The Lost City", result.getTitle());
        assertEquals(2023, result.getPublishedDate().getYear());
        assertEquals(1, result.getPublishedDate().getMonthValue());
        assertEquals(1, result.getPublishedDate().getDayOfMonth());
    }

    @Test
    void extractFromFilename_PublishedWithoutFormat_AutoDetectsTwoDigitYear() {
        String filename = "Chronicles of Tomorrow (99).epub";
        String pattern = "{Title} ({Published})";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("Chronicles of Tomorrow", result.getTitle());
        assertEquals(1999, result.getPublishedDate().getYear());
    }

    @Test
    void extractFromFilename_PublishedWithoutFormat_AutoDetectsFlexibleFormat() {
        String filename = "Tomorrow (15|05|2023).epub";
        String pattern = "{Title} ({Published})";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("Tomorrow", result.getTitle());
        assertEquals(2023, result.getPublishedDate().getYear());
        assertEquals(5, result.getPublishedDate().getMonthValue());
        assertEquals(15, result.getPublishedDate().getDayOfMonth());
    }

    @Test
    void extractFromFilename_WildcardBeforePlaceholder_SkipsUnwantedText() {
        String filename = "[Extra] Chronicles of Earth - Ch 42.cbz";
        String pattern = "[*] {SeriesName} - Ch {SeriesNumber}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("Chronicles of Earth", result.getSeriesName());
        assertEquals(42.0f, result.getSeriesNumber());
    }

    @Test
    void extractFromFilename_WildcardBetweenPlaceholders_SkipsMiddleText() {
        String filename = "The Lost City (extra) John Smith.epub";
        String pattern = "{Title} (*) {Authors}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("The Lost City", result.getTitle());
        assertEquals(Set.of("John Smith"), result.getAuthors());
    }

    @Test
    void extractFromFilename_WildcardAtEnd_SkipsTrailingText() {
        String filename = "Chronicles of Earth v1 - extra.cbz";
        String pattern = "{SeriesName} v{SeriesNumber} - *";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("Chronicles of Earth", result.getSeriesName());
        assertEquals(1.0f, result.getSeriesNumber());
    }

    @Test
    void extractFromFilename_WildcardAtEnd_AllowsPartialMatch() {
        String filename = "Chronicles of Earth - Chapter 20.cbz";
        String pattern = "{SeriesName} - * {SeriesNumber}";

        BookMetadata result = extractor.extractFromFilename(filename, pattern);

        assertNotNull(result);
        assertEquals("Chronicles of Earth", result.getSeriesName());
        assertEquals(20.0f, result.getSeriesNumber());
    }

    @Test
    void extractFromFilename_WildcardWithVariousPlacements_HandlesCorrectly() {
        String filename1 = "Chronicles of Tomorrow - Chapter 8.1 (2025).cbz";
        String pattern1 = "{SeriesName} - * {SeriesNumber}";
        BookMetadata result1 = extractor.extractFromFilename(filename1, pattern1);
        assertNotNull(result1);
        assertEquals("Chronicles of Tomorrow", result1.getSeriesName());
        assertEquals(8.1f, result1.getSeriesNumber());

        String filename2 = "Junk - Chapter 20.cbz";
        String pattern2 = "* - Chapter {SeriesNumber}";
        BookMetadata result2 = extractor.extractFromFilename(filename2, pattern2);
        assertNotNull(result2);
        assertEquals(20.0f, result2.getSeriesNumber());
    }
}

