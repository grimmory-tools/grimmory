package org.booklore.service.metadata.sidecar;

import org.booklore.model.dto.sidecar.SidecarCoverInfo;
import org.booklore.model.dto.sidecar.SidecarMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SidecarMetadataReaderTest {

    @TempDir
    Path tempDir;

    private SidecarMetadataReader sidecarMetadataReader;

    @BeforeEach
    void setUp() {
        sidecarMetadataReader = new SidecarMetadataReader(mock(SidecarMetadataMapper.class), new ObjectMapper());
    }

    @Test
    void readSidecarCover_resolvesExplicitCoverPathFromJson() throws Exception {
        Path bookDir = tempDir.resolve("books");
        Files.createDirectories(bookDir);
        Path bookPath = bookDir.resolve("libro.pdf");
        Files.createFile(bookPath);
        byte[] coverBytes = {1, 2, 3, 4};
        Files.write(bookDir.resolve("portada.jpg"), coverBytes);

        byte[] result = sidecarMetadataReader.readSidecarCover(bookPath, sidecarMetadataWithCoverPath("portada.jpg"));

        assertThat(result).isEqualTo(coverBytes);
    }

    @Test
    void readSidecarCover_resolvesExplicitCoverPathInSubdirectory() throws Exception {
        Path bookDir = tempDir.resolve("books");
        Files.createDirectories(bookDir.resolve("covers"));
        Path bookPath = bookDir.resolve("libro.pdf");
        Files.createFile(bookPath);
        byte[] coverBytes = {7, 8, 9};
        Files.write(bookDir.resolve("covers").resolve("portada.jpg"), coverBytes);

        byte[] result = sidecarMetadataReader.readSidecarCover(bookPath, sidecarMetadataWithCoverPath("covers/portada.jpg"));

        assertThat(result).isEqualTo(coverBytes);
    }

    @Test
    void readSidecarCover_fallsBackToConventionWhenNoExplicitPath() throws Exception {
        Path bookDir = tempDir.resolve("books");
        Files.createDirectories(bookDir);
        Path bookPath = bookDir.resolve("libro.pdf");
        Files.createFile(bookPath);
        byte[] coverBytes = {5, 6, 7};
        Files.write(bookDir.resolve("libro.cover.jpg"), coverBytes);
        SidecarMetadata sidecar = SidecarMetadata.builder()
                .cover(SidecarCoverInfo.builder().source("external").build())
                .build();

        byte[] result = sidecarMetadataReader.readSidecarCover(bookPath, sidecar);

        assertThat(result).isEqualTo(coverBytes);
    }

    @Test
    void readSidecarCover_returnsNullWhenCoverFileMissing() throws Exception {
        Path bookDir = tempDir.resolve("books");
        Files.createDirectories(bookDir);
        Path bookPath = bookDir.resolve("libro.pdf");
        Files.createFile(bookPath);

        byte[] result = sidecarMetadataReader.readSidecarCover(bookPath, sidecarMetadataWithCoverPath("portada.jpg"));

        assertThat(result).isNull();
    }

    @Test
    void readSidecarCover_rejectsPathOutsideBookDirectory() throws Exception {
        Path bookDir = tempDir.resolve("books");
        Files.createDirectories(bookDir);
        Path bookPath = bookDir.resolve("libro.pdf");
        Files.createFile(bookPath);
        Files.write(tempDir.resolve("outside.jpg"), new byte[]{9, 9, 9});

        byte[] result = sidecarMetadataReader.readSidecarCover(bookPath, sidecarMetadataWithCoverPath("../outside.jpg"));

        assertThat(result).isNull();
    }

    @Test
    void readSidecarCover_returnsNullWhenBookPathIsNull() {
        byte[] result = sidecarMetadataReader.readSidecarCover(null, sidecarMetadataWithCoverPath("portada.jpg"));

        assertThat(result).isNull();
    }

    private SidecarMetadata sidecarMetadataWithCoverPath(String path) {
        return SidecarMetadata.builder()
                .cover(SidecarCoverInfo.builder().source("external").path(path).build())
                .build();
    }
}
