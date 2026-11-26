package org.booklore.service.metadata.sidecar;

import org.booklore.config.AppProperties;
import org.booklore.model.entity.BookEntity;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.util.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SidecarMetadataWriterTest {

    @Mock
    private AppProperties appProperties;

    @Mock
    private SidecarMetadataMapper mapper;

    @Mock
    private FileService fileService;

    @Mock
    private AppSettingService appSettingService;

    @InjectMocks
    private SidecarMetadataWriter sidecarMetadataWriter;

    @BeforeEach
    void setUp() {
        lenient().when(appProperties.isLocalStorage()).thenReturn(true);
    }

    @Test
    void writeSidecarMetadata_networkStorage_skipsWrite() {
        when(appProperties.isLocalStorage()).thenReturn(false);

        sidecarMetadataWriter.writeSidecarMetadata(new BookEntity());

        verify(appSettingService, never()).getAppSettings();
    }

    @Test
    void writeSidecarMetadata_localStorage_proceedsNormally() {
        sidecarMetadataWriter.writeSidecarMetadata(null);

        verify(appProperties).isLocalStorage();
    }

    @Test
    void moveSidecarsDoesNotOverwriteExistingMetadata(@TempDir Path directory) throws IOException {
        Path oldBook = directory.resolve("old.epub");
        Path newBook = directory.resolve("new.epub");
        Files.writeString(directory.resolve("old.metadata.json"), "old");
        Files.writeString(directory.resolve("new.metadata.json"), "existing");

        sidecarMetadataWriter.moveSidecarFiles(oldBook, newBook);

        assertEquals("old", Files.readString(directory.resolve("old.metadata.json")));
        assertEquals("existing", Files.readString(directory.resolve("new.metadata.json")));
    }

    @Test
    void moveSidecarsDoesNotOverwriteExistingCover(@TempDir Path directory) throws IOException {
        Path oldBook = directory.resolve("old.epub");
        Path newBook = directory.resolve("new.epub");
        Files.writeString(directory.resolve("old.cover.jpg"), "old");
        Files.writeString(directory.resolve("new.cover.jpg"), "existing");

        sidecarMetadataWriter.moveSidecarFiles(oldBook, newBook);

        assertEquals("old", Files.readString(directory.resolve("old.cover.jpg")));
        assertEquals("existing", Files.readString(directory.resolve("new.cover.jpg")));
    }
}
