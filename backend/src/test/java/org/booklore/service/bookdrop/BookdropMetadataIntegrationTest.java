package org.booklore.service.bookdrop;

import org.booklore.model.dto.BookMetadata;
import org.booklore.model.entity.BookdropFileEntity;
import org.booklore.model.enums.BookFileExtension;
import org.booklore.repository.BookdropFileRepository;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.metadata.MetadataRefreshService;
import org.booklore.service.metadata.extractor.EpubMetadataExtractor;
import org.booklore.service.metadata.extractor.MetadataExtractorFactory;
import org.booklore.util.FileService;
import org.booklore.util.epub.CoverDetectorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookdropMetadataIntegrationTest {

    @Test
    void preservesNormalizedIsbnThroughBookdropImport(@TempDir Path tempDir) throws IOException {
        File epubFile = tempDir.resolve("book.epub").toFile();
        try (var epubResource = getClass().getResourceAsStream("/epub/formatted-isbn.epub")) {
            assertThat(epubResource).isNotNull();
            Files.copy(epubResource, epubFile.toPath());
        }
        BookdropFileEntity bookdropFile = new BookdropFileEntity();
        bookdropFile.setId(1L);
        bookdropFile.setFileName(epubFile.getName());
        bookdropFile.setFilePath(epubFile.getAbsolutePath());

        ObjectMapper objectMapper = JsonMapper.shared();
        EpubMetadataExtractor extractor = new EpubMetadataExtractor(objectMapper, mock(CoverDetectorService.class));
        BookdropFileRepository repository = mock(BookdropFileRepository.class);
        MetadataExtractorFactory factory = mock(MetadataExtractorFactory.class);
        BookdropMetadataService service = new BookdropMetadataService(repository, mock(AppSettingService.class),
                objectMapper, factory, mock(MetadataRefreshService.class), mock(FileService.class));

        when(repository.findById(1L)).thenReturn(Optional.of(bookdropFile));
        when(factory.extractMetadata(BookFileExtension.EPUB, epubFile))
                .thenAnswer(invocation -> extractor.extractMetadata(invocation.getArgument(1)));
        when(repository.save(bookdropFile)).thenReturn(bookdropFile);

        BookdropFileEntity result = service.attachInitialMetadata(1L);

        BookMetadata savedMetadata = objectMapper.readValue(result.getOriginalMetadata(), BookMetadata.class);
        assertThat(savedMetadata.getIsbn13()).isEqualTo("9781938570513");
        verify(repository).save(result);
    }
}
