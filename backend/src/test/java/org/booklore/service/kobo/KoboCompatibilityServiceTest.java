package org.booklore.service.kobo;

import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.settings.KoboSettings;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.service.appsettings.AppSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Kobo Compatibility Service Tests")
@ExtendWith(MockitoExtension.class)
class KoboCompatibilityServiceTest {

    @Mock
    private AppSettingService appSettingService;

    @InjectMocks
    private KoboCompatibilityService koboCompatibilityService;

    private AppSettings appSettings;
    private KoboSettings koboSettings;

    @BeforeEach
    void setUp() {
        koboSettings = KoboSettings.builder()
                .convertToKepub(false)
                .conversionLimitInMb(100)
                .convertCbxToEpub(false)
                .conversionLimitInMbForCbx(50)
                .build();
        
        appSettings = new AppSettings();
        appSettings.setKoboSettings(koboSettings);
    }

    @Test
    @DisplayName("Should always support EPUB files regardless of settings")
    void shouldAlwaysSupportEpubFiles() {
        BookEntity epubBook = createBookEntity(1L, BookFileType.EPUB, 1000L);

        boolean isSupported = koboCompatibilityService.isBookSupportedForKobo(epubBook);

        assertThat(isSupported).isTrue();
        verifyNoInteractions(appSettingService);
    }

    @Test
    @DisplayName("Should support CBX files when CBX conversion is enabled and within size limit")
    void shouldSupportCbxWhenConversionEnabledAndWithinSizeLimit() {
        BookEntity cbxBook = createBookEntity(1L, BookFileType.CBX, 1000L);
        koboSettings.setConvertCbxToEpub(true);
        koboSettings.setConversionLimitInMbForCbx(50);
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        boolean isSupported = koboCompatibilityService.isBookSupportedForKobo(cbxBook);

        assertThat(isSupported).isTrue();
        verify(appSettingService, atLeastOnce()).getAppSettings();
    }

    @Test
    @DisplayName("Should not support CBX files when CBX conversion is disabled")
    void shouldNotSupportCbxWhenConversionDisabled() {
        BookEntity cbxBook = createBookEntity(1L, BookFileType.CBX, 1000L);
        koboSettings.setConvertCbxToEpub(false);
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        boolean isSupported = koboCompatibilityService.isBookSupportedForKobo(cbxBook);

        assertThat(isSupported).isFalse();
        verify(appSettingService).getAppSettings();
    }

    @Test
    @DisplayName("Should not support CBX files when they exceed the size limit")
    void shouldNotSupportCbxWhenExceedsSizeLimit() {
        BookEntity largeCbxBook = createBookEntity(1L, BookFileType.CBX, 75_000L);
        koboSettings.setConvertCbxToEpub(true);
        koboSettings.setConversionLimitInMbForCbx(50);
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        boolean isSupported = koboCompatibilityService.isBookSupportedForKobo(largeCbxBook);

        assertThat(isSupported).isFalse();
        verify(appSettingService, atLeastOnce()).getAppSettings();
    }

    @Test
    @DisplayName("Should support CBX files that exactly meet the size limit")
    void shouldSupportCbxAtExactSizeLimit() {
        BookEntity cbxBookAtLimit = createBookEntity(1L, BookFileType.CBX, 51_200L);
        koboSettings.setConvertCbxToEpub(true);
        koboSettings.setConversionLimitInMbForCbx(50);
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        boolean isSupported = koboCompatibilityService.isBookSupportedForKobo(cbxBookAtLimit);


        assertThat(isSupported).isTrue();
        verify(appSettingService, atLeastOnce()).getAppSettings();
    }

    @Test
    @DisplayName("Should not support CBX files when size limit check fails due to settings error")
    void shouldNotSupportCbxWhenSizeLimitCheckFails() {

        BookEntity cbxBook = createBookEntity(1L, BookFileType.CBX, 1000L);
        koboSettings.setConvertCbxToEpub(true);
        // First call succeeds for conversion check, second call fails for size check
        when(appSettingService.getAppSettings())
                .thenReturn(appSettings)
                .thenThrow(new RuntimeException("Settings error"));


        boolean isSupported = koboCompatibilityService.isBookSupportedForKobo(cbxBook);


        assertThat(isSupported).isFalse();
        verify(appSettingService, atLeastOnce()).getAppSettings();
    }

    @Test
    @DisplayName("Should not support CBX files when Kobo settings are null")
    void shouldNotSupportCbxWhenSettingsAreNull() {

        BookEntity cbxBook = createBookEntity(1L, BookFileType.CBX, 1000L);
        appSettings.setKoboSettings(null);
        when(appSettingService.getAppSettings()).thenReturn(appSettings);


        boolean isSupported = koboCompatibilityService.isBookSupportedForKobo(cbxBook);


        assertThat(isSupported).isFalse();
        verify(appSettingService, atLeastOnce()).getAppSettings();
    }

    @Test
    @DisplayName("Should not support PDF files")
    void shouldNotSupportPdfFiles() {

        BookEntity pdfBook = createBookEntity(1L, BookFileType.PDF, 1000L);


        boolean isSupported = koboCompatibilityService.isBookSupportedForKobo(pdfBook);


        assertThat(isSupported).isFalse();
        verifyNoInteractions(appSettingService);
    }

    @Test
    @DisplayName("Should handle null book gracefully")
    void shouldHandleNullBookGracefully() {
        assertThatThrownBy(() -> koboCompatibilityService.isBookSupportedForKobo(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Book cannot be null");
    }

    @Test
    @DisplayName("Should handle null book type gracefully")
    void shouldHandleNullBookTypeGracefully() {

        BookEntity bookWithNullType = new BookEntity();
        bookWithNullType.setId(1L);

        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(bookWithNullType);
        primaryFile.setBookType(null);
        bookWithNullType.setBookFiles(List.of(primaryFile));


        boolean isSupported = koboCompatibilityService.isBookSupportedForKobo(bookWithNullType);


        assertThat(isSupported).isFalse();
    }

    @Test
    @DisplayName("Should return true when CBX conversion is enabled")
    void shouldReturnTrueWhenCbxConversionEnabled() {

        koboSettings.setConvertCbxToEpub(true);
        when(appSettingService.getAppSettings()).thenReturn(appSettings);


        boolean isEnabled = koboCompatibilityService.isCbxConversionEnabled();


        assertThat(isEnabled).isTrue();
        verify(appSettingService).getAppSettings();
    }

    @Test
    @DisplayName("Should return false when CBX conversion is disabled")
    void shouldReturnFalseWhenCbxConversionDisabled() {

        koboSettings.setConvertCbxToEpub(false);
        when(appSettingService.getAppSettings()).thenReturn(appSettings);


        boolean isEnabled = koboCompatibilityService.isCbxConversionEnabled();


        assertThat(isEnabled).isFalse();
        verify(appSettingService).getAppSettings();
    }

    @Test
    @DisplayName("Should handle settings exception gracefully")
    void shouldHandleSettingsExceptionGracefully() {

        when(appSettingService.getAppSettings()).thenThrow(new RuntimeException("Settings error"));


        boolean isEnabled = koboCompatibilityService.isCbxConversionEnabled();


        assertThat(isEnabled).isFalse();
        verify(appSettingService).getAppSettings();
    }

    @Test
    @DisplayName("Should validate CBX file meets size limit")
    void shouldValidateCbxFileMeetsSizeLimit() {

        BookEntity smallCbxBook = createBookEntity(1L, BookFileType.CBX, 1000L);
        koboSettings.setConversionLimitInMbForCbx(50);
        when(appSettingService.getAppSettings()).thenReturn(appSettings);


        boolean meetsLimit = koboCompatibilityService.meetsCbxConversionSizeLimit(smallCbxBook);


        assertThat(meetsLimit).isTrue();
        verify(appSettingService).getAppSettings();
    }

    @Test
    @DisplayName("Should validate CBX file exceeds size limit")
    void shouldValidateCbxFileExceedsSizeLimit() {

        BookEntity largeCbxBook = createBookEntity(1L, BookFileType.CBX, 100_000L);
        koboSettings.setConversionLimitInMbForCbx(50);
        when(appSettingService.getAppSettings()).thenReturn(appSettings);


        boolean meetsLimit = koboCompatibilityService.meetsCbxConversionSizeLimit(largeCbxBook);


        assertThat(meetsLimit).isFalse();
        verify(appSettingService).getAppSettings();
    }

    @Test
    @DisplayName("Should return false for size limit check on non-CBX book")
    void shouldReturnFalseForSizeLimitCheckOnNonCbxBook() {

        BookEntity epubBook = createBookEntity(1L, BookFileType.EPUB, 1000L);


        boolean meetsLimit = koboCompatibilityService.meetsCbxConversionSizeLimit(epubBook);


        assertThat(meetsLimit).isFalse();
        verifyNoInteractions(appSettingService);
    }

    @Test
    @DisplayName("Should handle null file size gracefully")
    void shouldHandleNullFileSizeGracefully() {

        BookEntity cbxBook = createBookEntity(1L, BookFileType.CBX, null);
        when(appSettingService.getAppSettings()).thenReturn(appSettings);


        boolean meetsLimit = koboCompatibilityService.meetsCbxConversionSizeLimit(cbxBook);


        assertThat(meetsLimit).isTrue();
        verify(appSettingService).getAppSettings();
    }

    private BookEntity createBookEntity(Long id, BookFileType bookType, Long fileSizeKb) {
        BookEntity book = new BookEntity();
        book.setId(id);

        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(book);
        primaryFile.setBookType(bookType);
        primaryFile.setFileSizeKb(fileSizeKb);
        book.setBookFiles(List.of(primaryFile));

        return book;
    }
}