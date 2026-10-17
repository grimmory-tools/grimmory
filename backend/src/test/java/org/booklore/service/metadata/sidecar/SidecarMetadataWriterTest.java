package org.booklore.service.metadata.sidecar;

import org.booklore.config.AppProperties;
import org.booklore.model.entity.BookEntity;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.util.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SidecarMetadataWriterTest {

    @TempDir
    Path directory;

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

    @ParameterizedTest
    @ValueSource(strings = {"./artwork.png", "artwork/front.png"})
    void moveSidecarFiles_movesDeclaredCoverWithTheBook(String declaredPath) throws IOException {
        Path oldBook = prepareBookPath("old/Old.epub");
        Path newBook = prepareBookPath("new/New.epub");
        Path oldCover = oldBook.resolveSibling(declaredPath).normalize();
        Files.createDirectories(oldCover.getParent());
        Files.writeString(oldCover, "custom artwork");
        writeSidecar(oldBook, declaredPath);

        sidecarMetadataWriter.moveSidecarFiles(oldBook, newBook);

        assertThat(readSidecar(newBook).path("cover").path("path").asText()).isEqualTo(declaredPath);
        assertThat(readSidecar(newBook).path("metadata").path("title").asText()).isEqualTo("Keep this title");
        assertThat(Files.readString(newBook.resolveSibling(declaredPath))).isEqualTo("custom artwork");
        assertThat(oldCover).doesNotExist();
        assertThat(sidecarMetadataWriter.getSidecarPath(oldBook)).doesNotExist();
    }

    @Test
    void moveSidecarFiles_keepsDeclaredCoverForSameDirectoryRename() throws IOException {
        Path oldBook = prepareBookPath("Old.epub");
        Path newBook = prepareBookPath("New.epub");
        Files.writeString(directory.resolve("artwork.jpg"), "custom artwork");
        writeSidecar(oldBook, "./artwork.jpg");

        sidecarMetadataWriter.moveSidecarFiles(oldBook, newBook);

        assertThat(readSidecar(newBook).path("cover").path("path").asText()).isEqualTo("./artwork.jpg");
        assertThat(Files.readString(directory.resolve("artwork.jpg"))).isEqualTo("custom artwork");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Old.cover.jpg", "./Old.cover.jpg", "artwork/../Old.cover.jpg"})
    void moveSidecarFiles_movesStandardCompanionAndRepairsAliases(String declaredPath) throws IOException {
        Path oldBook = prepareBookPath("old/Old.epub");
        Path newBook = prepareBookPath("new/New.epub");
        Files.writeString(oldBook.resolveSibling("Old.cover.jpg"), "standard artwork");
        writeSidecar(oldBook, declaredPath);

        sidecarMetadataWriter.moveSidecarFiles(oldBook, newBook);

        assertThat(readSidecar(newBook).path("cover").path("path").asText()).isEqualTo("New.cover.jpg");
        assertThat(Files.readString(newBook.resolveSibling("New.cover.jpg"))).isEqualTo("standard artwork");
        assertThat(oldBook.resolveSibling("Old.cover.jpg")).doesNotExist();
    }

    @ParameterizedTest
    @ValueSource(strings = {"old", "new"})
    void moveSidecarFiles_repairsCoverInsideMovedAudiobookFolder(String targetDirectory) throws IOException {
        Path oldBook = prepareBookPath("old/Old Audio");
        Path newBook = prepareBookPath(targetDirectory + "/New Audio");
        Files.createDirectories(oldBook.resolve("artwork"));
        Files.writeString(oldBook.resolve("artwork/front.png"), "audiobook artwork");
        writeSidecar(oldBook, "Old Audio/artwork/front.png");
        Files.move(oldBook, newBook);

        sidecarMetadataWriter.moveSidecarFiles(oldBook, newBook);

        assertThat(readSidecar(newBook).path("cover").path("path").asText()).isEqualTo("New Audio/artwork/front.png");
        assertThat(Files.readString(newBook.resolve("artwork/front.png"))).isEqualTo("audiobook artwork");
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing.png", "../outside.png"})
    void moveSidecarFiles_leavesUnresolvableCoverAlone(String declaredPath) throws IOException {
        Path oldBook = prepareBookPath("old/Old.epub");
        Path newBook = prepareBookPath("new/New.epub");
        Files.writeString(directory.resolve("outside.png"), "outside artwork");
        writeSidecar(oldBook, declaredPath);

        sidecarMetadataWriter.moveSidecarFiles(oldBook, newBook);

        assertThat(readSidecar(newBook).path("cover").path("path").asText()).isEqualTo(declaredPath);
        assertThat(Files.readString(directory.resolve("outside.png"))).isEqualTo("outside artwork");
        assertThat(Files.list(newBook.getParent())).containsExactly(sidecarMetadataWriter.getSidecarPath(newBook));
    }

    @Test
    void deleteSidecarFiles_deletesDeclaredCoverWithTheSidecar() throws IOException {
        Path book = prepareBookPath("Book.epub");
        Files.writeString(directory.resolve("artwork.png"), "custom artwork");
        Files.writeString(directory.resolve("Book.cover.jpg"), "standard artwork");
        writeSidecar(book, "artwork.png");

        sidecarMetadataWriter.deleteSidecarFiles(book);

        assertThat(Files.list(directory)).isEmpty();
    }

    private Path prepareBookPath(String relativePath) throws IOException {
        Path bookPath = directory.resolve(relativePath);
        Files.createDirectories(bookPath.getParent());
        return bookPath;
    }

    private void writeSidecar(Path bookPath, String coverPath) throws IOException {
        var sidecar = JsonMapper.builder().build().createObjectNode();
        sidecar.putObject("metadata").put("title", "Keep this title");
        sidecar.putObject("cover").put("path", coverPath);
        Files.writeString(sidecarMetadataWriter.getSidecarPath(bookPath), sidecar.toString());
    }

    private JsonNode readSidecar(Path bookPath) throws IOException {
        return JsonMapper.builder().build().readTree(Files.readString(sidecarMetadataWriter.getSidecarPath(bookPath)));
    }
}
