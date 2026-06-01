package org.booklore.service.metadata;

import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.CoverImage;
import org.booklore.model.dto.request.CoverFetchRequest;
import org.booklore.model.dto.request.FetchMetadataRequest;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.settings.MetadataProviderSettings;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.metadata.parser.ItunesParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ItunesCoverServiceTest {

    @Mock
    private ItunesParser itunesParser;

    @Mock
    private AppSettingService appSettingService;

    @InjectMocks
    private ItunesCoverService itunesCoverService;

    private AppSettings appSettings;
    private MetadataProviderSettings.Itunes itunesSettings;

    @BeforeEach
    void setUp() {
        appSettings = new AppSettings();
        MetadataProviderSettings providerSettings = new MetadataProviderSettings();
        itunesSettings = new MetadataProviderSettings.Itunes();
        providerSettings.setItunes(itunesSettings);
        appSettings.setMetadataProviderSettings(providerSettings);
    }

    @Test
    void getCovers_whenItunesDisabled_returnsEmptyFlux() {
        itunesSettings.setEnabled(false);
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        CoverFetchRequest request = CoverFetchRequest.builder()
                .title("Test Book")
                .author("Test Author")
                .coverType("ebook")
                .build();

        List<CoverImage> result = itunesCoverService.getCovers(request).collectList().block();
        assertThat(result).isEmpty();

        verifyNoInteractions(itunesParser);
    }

    @Test
    void getCovers_whenItunesEnabled_returnsCoverImages() {
        itunesSettings.setEnabled(true);
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        BookMetadata metadata = BookMetadata.builder()
                .thumbnailUrl("https://is1-ssl.mzstatic.com/image/thumb/Publication/v4/foo/600x600bb.jpg")
                .build();

        when(itunesParser.fetchMetadata(any(Book.class), any(FetchMetadataRequest.class)))
                .thenReturn(List.of(metadata));

        CoverFetchRequest request = CoverFetchRequest.builder()
                .title("Test Book")
                .author("Test Author")
                .coverType("ebook")
                .build();

        List<CoverImage> result = itunesCoverService.getCovers(request).collectList().block();
        assertThat(result).hasSize(1);

        CoverImage first = result.get(0);
        assertThat(first.getUrl()).isEqualTo("https://is1-ssl.mzstatic.com/image/thumb/Publication/v4/foo/1000x1000bb.jpg");
        assertThat(first.getWidth()).isEqualTo(1000);
        assertThat(first.getHeight()).isEqualTo(1000);
        assertThat(first.getIndex()).isEqualTo(1);
    }

    @Test
    void getCovers_whenItunesParserThrowsException_returnsEmptyFlux() {
        itunesSettings.setEnabled(true);
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        when(itunesParser.fetchMetadata(any(Book.class), any(FetchMetadataRequest.class)))
                .thenThrow(new RuntimeException("API failure"));

        CoverFetchRequest request = CoverFetchRequest.builder()
                .title("Test Book")
                .author("Test Author")
                .coverType("ebook")
                .build();

        List<CoverImage> result = itunesCoverService.getCovers(request).collectList().block();
        assertThat(result).isEmpty();
    }
}
