package org.booklore.service.book;

import org.booklore.util.FileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalibreEbookConversionServiceTest {

    @Mock
    private FileService fileService;

    @TempDir
    Path tempDir;

    @Test
    void isAvailable_returnsFalseWhenEbookConvertIsAbsent() {
        CalibreEbookConversionService service = new CalibreEbookConversionService(fileService);
        when(fileService.findSystemFile("ebook-convert")).thenReturn(null);

        assertThat(service.isAvailable()).isFalse();
    }

    @Test
    void convert_runsExecutableAndReturnsCreatedOutputFile() throws Exception {
        Path binary = createFakeEbookConvert();
        Path input = tempDir.resolve("input.epub");
        Path output = tempDir.resolve("nested/output.mobi");
        Files.writeString(input, "source");

        CalibreEbookConversionService service = new CalibreEbookConversionService(fileService);
        when(fileService.findSystemFile("ebook-convert")).thenReturn(binary);

        Path converted = service.convert(input, output);

        assertThat(converted).isEqualTo(output);
        assertThat(output).exists().hasContent("converted");
    }

    private Path createFakeEbookConvert() throws IOException {
        Path binary = tempDir.resolve("ebook-convert");
        Files.writeString(binary, "#!/bin/sh\nprintf 'converted' > \"$2\"\n");
        assertThat(binary.toFile().setExecutable(true)).isTrue();
        return binary;
    }
}
