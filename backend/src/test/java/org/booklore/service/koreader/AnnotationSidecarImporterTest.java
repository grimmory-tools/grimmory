package org.booklore.service.koreader;

import org.booklore.config.AppProperties;
import org.booklore.model.entity.AnnotationEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.repository.AnnotationRepository;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserRepository;
import org.booklore.util.koreader.EpubCfiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnnotationSidecarImporterTest {

    @Mock BookRepository bookRepository;
    @Mock UserRepository userRepository;
    @Mock AnnotationRepository annotationRepository;
    @Mock EpubCfiService epubCfiService;
    @Mock AppProperties appProperties;

    AnnotationSidecarImporter importer;

    @TempDir
    Path tempDir;

    private static final String SIDECAR_ALICE = """
            -- KOReader bookmark file
            -- version: 1
            -- booklore:schema_version=1
            {
                ["booklore_meta"] = {
                    ["username"] = "alice",
                    ["user_id"] = 7,
                    ["book_id"] = 99,
                    ["exported_at"] = "2024-01-15 10:30:00",
                    ["schema_version"] = 1,
                },
                ["highlights"] = {
                    [1] = {
                        ["chapter"] = "Chapter One",
                        ["datetime"] = "2024-01-15 10:30:00",
                        ["drawer"] = "lighten",
                        ["notes"] = "highlighted text",
                        ["page"] = "/body/DocFragment[1]/body/p",
                        ["pos0"] = "/body/DocFragment[1]/body/p/text().5",
                        ["pos1"] = "/body/DocFragment[1]/body/p/text().20",
                        ["booklore"] = {
                            ["id"] = 42,
                            ["color"] = "#FFFF00",
                            ["style"] = "highlight",
                            ["note"] = "my note",
                            ["version"] = 1,
                        },
                    },
                },
                ["bookmarks"] = {},
            }
            """;

    @BeforeEach
    void setUp() {
        importer = new AnnotationSidecarImporter(
                bookRepository, userRepository, annotationRepository,
                epubCfiService, appProperties);
        when(appProperties.isLocalStorage()).thenReturn(true);
    }

    // ── guard ─────────────────────────────────────────────────────────────────

    @Test
    void importAll_networkStorage_skips() {
        when(appProperties.isLocalStorage()).thenReturn(false);
        AnnotationSidecarImporter.ImportResult result = importer.importAll();
        assertThat(result.booksScanned()).isZero();
        verifyNoInteractions(bookRepository);
    }

    // ── file discovery ────────────────────────────────────────────────────────

    @Test
    void importAll_bookWithNoSdrDir_noImport() throws Exception {
        Path bookFile = tempDir.resolve("book.epub");
        Files.createFile(bookFile);
        BookEntity book = mockBook(bookFile);
        when(bookRepository.findAll()).thenReturn(List.of(book));

        AnnotationSidecarImporter.ImportResult result = importer.importAll();

        assertThat(result.booksScanned()).isEqualTo(1);
        assertThat(result.sidecarFiles()).isZero();
        verifyNoInteractions(annotationRepository);
    }

    @Test
    void importAll_sdrDirWithNoLuaFiles_noImport() throws Exception {
        Path bookFile = tempDir.resolve("book.epub");
        Files.createFile(bookFile);
        Files.createDirectories(tempDir.resolve("book.epub.sdr"));
        BookEntity book = mockBook(bookFile);
        when(bookRepository.findAll()).thenReturn(List.of(book));

        AnnotationSidecarImporter.ImportResult result = importer.importAll();

        assertThat(result.sidecarFiles()).isZero();
        verifyNoInteractions(annotationRepository);
    }

    // ── user resolution ───────────────────────────────────────────────────────

    @Test
    void importAll_unknownUser_skipsAnnotations() throws Exception {
        Path bookFile = writeSidecar("book.epub", "alice", SIDECAR_ALICE);
        BookEntity book = mockBook(bookFile);
        when(bookRepository.findAll()).thenReturn(List.of(book));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(userRepository.findById(7L)).thenReturn(Optional.empty());

        AnnotationSidecarImporter.ImportResult result = importer.importAll();

        assertThat(result.skipped()).isEqualTo(1);
        verify(annotationRepository, never()).save(any());
    }

    // ── import / skip logic ───────────────────────────────────────────────────

    @Test
    void importAll_newAnnotation_importsAndSaves() throws Exception {
        Path bookFile = writeSidecar("book.epub", "alice", SIDECAR_ALICE);
        BookEntity book = mockBook(bookFile);
        BookLoreUserEntity alice = mockUser(7L, "alice");
        when(bookRepository.findAll()).thenReturn(List.of(book));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(annotationRepository.findByIdAndUserId(42L, 7L)).thenReturn(Optional.empty());
        when(annotationRepository.existsByCfiAndBookIdAndUserId(any(), anyLong(), anyLong())).thenReturn(false);
        when(epubCfiService.convertXPointerRangeToCfi(eq(bookFile), any(), any()))
                .thenReturn("epubcfi(/6/2!/4/2/2:5)");

        AnnotationSidecarImporter.ImportResult result = importer.importAll();

        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.skipped()).isZero();
        ArgumentCaptor<AnnotationEntity> cap = ArgumentCaptor.forClass(AnnotationEntity.class);
        verify(annotationRepository).save(cap.capture());
        AnnotationEntity saved = cap.getValue();
        assertThat(saved.getText()).isEqualTo("highlighted text");
        assertThat(saved.getNote()).isEqualTo("my note");
        assertThat(saved.getColor()).isEqualTo("#FFFF00");
        assertThat(saved.getStyle()).isEqualTo("highlight");
        assertThat(saved.getCfi()).isEqualTo("epubcfi(/6/2!/4/2/2:5)");
    }

    @Test
    void importAll_annotationAlreadyInDb_skips() throws Exception {
        Path bookFile = writeSidecar("book.epub", "alice", SIDECAR_ALICE);
        BookEntity book = mockBook(bookFile);
        BookLoreUserEntity alice = mockUser(7L, "alice");
        when(bookRepository.findAll()).thenReturn(List.of(book));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        // bookloreId 42 is already in the DB
        when(annotationRepository.findByIdAndUserId(42L, 7L))
                .thenReturn(Optional.of(mock(AnnotationEntity.class)));

        AnnotationSidecarImporter.ImportResult result = importer.importAll();

        assertThat(result.imported()).isZero();
        assertThat(result.skipped()).isEqualTo(1);
        verify(annotationRepository, never()).save(any());
    }

    @Test
    void importAll_cfiAlreadyExists_skips() throws Exception {
        String noBookloreId = SIDECAR_ALICE.replace(
                "            [\"booklore\"] = {\n                [\"id\"] = 42,\n                [\"color\"] = \"#FFFF00\",\n                [\"style\"] = \"highlight\",\n                [\"note\"] = \"my note\",\n                [\"version\"] = 1,\n            },\n",
                "");
        Path bookFile = writeSidecar("book.epub", "alice", noBookloreId);
        BookEntity book = mockBook(bookFile);
        BookLoreUserEntity alice = mockUser(7L, "alice");
        when(bookRepository.findAll()).thenReturn(List.of(book));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(epubCfiService.convertXPointerRangeToCfi(eq(bookFile), any(), any()))
                .thenReturn("epubcfi(/6/2!/4/2/2:5)");
        when(annotationRepository.existsByCfiAndBookIdAndUserId(eq("epubcfi(/6/2!/4/2/2:5)"), anyLong(), eq(7L)))
                .thenReturn(true);

        AnnotationSidecarImporter.ImportResult result = importer.importAll();

        assertThat(result.imported()).isZero();
        assertThat(result.skipped()).isEqualTo(1);
    }

    // ── filename helper ───────────────────────────────────────────────────────

    @Nested
    class ExtractUsernameTests {

        @Test
        void extractUsername_normalFilename() {
            assertThat(AnnotationSidecarImporter.extractUsernameFromFilename("metadata.epub.alice.lua"))
                    .isEqualTo("alice");
        }

        @Test
        void extractUsername_usernameWithDots() {
            assertThat(AnnotationSidecarImporter.extractUsernameFromFilename("metadata.epub.first.last.lua"))
                    .isEqualTo("first.last");
        }

        @Test
        void extractUsername_wrongPrefix_returnsNull() {
            assertThat(AnnotationSidecarImporter.extractUsernameFromFilename("metadata.lua")).isNull();
        }

        @Test
        void extractUsername_emptyUsername_returnsNull() {
            assertThat(AnnotationSidecarImporter.extractUsernameFromFilename("metadata.epub..lua")).isNull();
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Path writeSidecar(String bookFilename, String username, String content) throws Exception {
        Path bookFile = tempDir.resolve(bookFilename);
        if (!Files.exists(bookFile)) Files.createFile(bookFile);
        Path sdrDir = tempDir.resolve(bookFilename + ".sdr");
        Files.createDirectories(sdrDir);
        Path sidecar = sdrDir.resolve("metadata.epub." + username + ".lua");
        Files.writeString(sidecar, content);
        return bookFile;
    }

    private BookEntity mockBook(Path bookFile) {
        BookEntity book = mock(BookEntity.class);
        when(book.getId()).thenReturn(99L);
        when(book.getFullFilePath()).thenReturn(bookFile);
        return book;
    }

    private BookLoreUserEntity mockUser(long id, String username) {
        BookLoreUserEntity user = mock(BookLoreUserEntity.class);
        when(user.getId()).thenReturn(id);
        when(user.getUsername()).thenReturn(username);
        return user;
    }
}
