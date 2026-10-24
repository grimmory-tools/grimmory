package org.booklore.service.upload.lcp;

import org.booklore.exception.APIException;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.service.appsettings.AppSettingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LcpImportServiceTest {
    @TempDir Path directory;
    @Mock LcpPublicationDownloader downloader;
    @Mock LcpSettingsService settings;
    @Mock AppSettingService appSettings;

    private LcpImportService service() {
        return new LcpImportService(new LcpEpubConverter(new ObjectMapper()), downloader, settings, appSettings);
    }

    @Test
    void normalUploadsRemainUnchanged() throws Exception {
        var file = new MockMultipartFile("file", "book.epub", "application/epub+zip", new byte[]{1, 2});
        try (var prepared = service().prepare(file, null)) {
            assertThat(prepared.file()).isSameAs(file);
            assertThat(prepared.temporary()).isNull();
        }
        verifyNoInteractions(downloader, settings, appSettings);
    }

    @Test
    void requiresCodeBeforeDownloading() throws Exception {
        var fixture = new LcpEpubConverterTest();
        fixture.directory = directory;
        var file = new MockMultipartFile("file", "book.lcpl", "application/json", fixture.license(true));
        when(settings.getDefaultUserKey()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().prepare(file, null)).isInstanceOf(APIException.class).hasMessageContaining("required");
        verifyNoInteractions(downloader);
    }

    @Test
    void usesDefaultAndProvidesRealEpubThenCleansTemporaryFiles() throws Exception {
        runConversion(null, true);
    }

    @Test
    void explicitCodeOverridesDefaultWithoutReadingIt() throws Exception {
        runConversion(LcpEpubConverterTest.PASSWORD, false);
        verify(settings, never()).getDefaultUserKey();
    }

    private void runConversion(String passphrase, boolean useDefault) throws Exception {
        var fixture = new LcpEpubConverterTest();
        fixture.directory = directory;
        Path book = fixture.publication(true, false, false);
        var file = new MockMultipartFile("file", "book.LCPL", "application/json", fixture.license(true));
        if (useDefault) when(settings.getDefaultUserKey()).thenReturn(Optional.of(LcpEpubConverter.deriveUserKey(LcpEpubConverterTest.PASSWORD)));
        AppSettings configuration = new AppSettings();
        configuration.setMaxFileUploadSizeInMb(10);
        when(appSettings.getAppSettings()).thenReturn(configuration);
        AtomicReference<Path> download = new AtomicReference<>();
        doAnswer(invocation -> {
            Path target = invocation.getArgument(1);
            download.set(target);
            Files.copy(book, target, StandardCopyOption.REPLACE_EXISTING);
            return null;
        }).when(downloader).download(any(), any(), anyLong());
        Path temporary;
        try (var prepared = service().prepare(file, passphrase)) {
            temporary = prepared.temporary();
            assertThat(prepared.file().getOriginalFilename()).isEqualTo("book.epub");
            assertThat(prepared.file().getContentType()).isEqualTo("application/epub+zip");
            Path transferred = directory.resolve("imported.epub");
            prepared.file().transferTo(transferred);
            try (ZipFile zip = new ZipFile(transferred.toFile())) {
                assertThat(zip.getInputStream(zip.getEntry("OPS/chapter+one.xhtml")).readAllBytes()).isEqualTo(LcpEpubConverterTest.CHAPTER);
            }
        }
        assertThat(Files.exists(temporary)).isFalse();
        assertThat(Files.exists(download.get())).isFalse();
    }
}
