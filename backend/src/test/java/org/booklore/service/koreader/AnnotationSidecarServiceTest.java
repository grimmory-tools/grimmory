package org.booklore.service.koreader;

import org.booklore.config.AppProperties;
import org.booklore.model.entity.AnnotationEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.util.koreader.CfiConvertor;
import org.booklore.util.koreader.EpubCfiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnnotationSidecarServiceTest {

    @Mock
    EpubCfiService epubCfiService;
    @Mock
    AppProperties appProperties;

    AnnotationSidecarService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new AnnotationSidecarService(epubCfiService, appProperties);
        when(appProperties.isLocalStorage()).thenReturn(true);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private BookEntity bookAt(Path path) {
        BookEntity book = mock(BookEntity.class);
        when(book.getId()).thenReturn(1L);
        when(book.getFullFilePath()).thenReturn(path);
        return book;
    }

    private BookLoreUserEntity user(String username) {
        BookLoreUserEntity u = mock(BookLoreUserEntity.class);
        when(u.getUsername()).thenReturn(username);
        return u;
    }

    private AnnotationEntity annotation(String cfi, String text, String style) {
        AnnotationEntity ann = mock(AnnotationEntity.class);
        when(ann.getId()).thenReturn(42L);
        when(ann.getCfi()).thenReturn(cfi);
        when(ann.getText()).thenReturn(text);
        when(ann.getStyle()).thenReturn(style);
        when(ann.getChapterTitle()).thenReturn("Chapter One");
        when(ann.getCreatedAt()).thenReturn(LocalDateTime.of(2024, 1, 15, 10, 30, 0));
        return ann;
    }

    private CfiConvertor.XPointerResult xpr(String pos0, String pos1) {
        return new CfiConvertor.XPointerResult(pos0, pos0, pos1);
    }

    // ── guard conditions ──────────────────────────────────────────────────────

    @Test
    void writeSidecar_networkStorage_doesNothing() throws Exception {
        when(appProperties.isLocalStorage()).thenReturn(false);
        Path bookFile = tempDir.resolve("book.epub");
        Files.createFile(bookFile);

        service.writeSidecar(bookAt(bookFile), user("alice"), List.of(annotation("cfi", "text", "highlight")));

        Path sidecar = AnnotationSidecarService.resolveSidecarPath(bookFile, "alice");
        assertThat(sidecar).doesNotExist();
        verifyNoInteractions(epubCfiService);
    }

    @Test
    void writeSidecar_nullFilePath_doesNothing() {
        BookEntity book = mock(BookEntity.class);
        when(book.getId()).thenReturn(1L);
        when(book.getFullFilePath()).thenReturn(null);

        service.writeSidecar(book, user("alice"), List.of(annotation("cfi", "text", "highlight")));

        verifyNoInteractions(epubCfiService);
    }

    // ── file path ─────────────────────────────────────────────────────────────

    @Test
    void writeSidecar_createsCorrectPath() throws Exception {
        Path bookFile = tempDir.resolve("mybook.epub");
        Files.createFile(bookFile);
        when(epubCfiService.convertCfiToXPointer(eq(bookFile), any()))
                .thenReturn(xpr("/body/DocFragment[1]/body/p/text().0",
                                "/body/DocFragment[1]/body/p/text().10"));

        service.writeSidecar(bookAt(bookFile), user("alice"), List.of(annotation("cfi1", "hello", "highlight")));

        Path expected = tempDir.resolve("mybook.epub.sdr/metadata.epub.alice.lua");
        assertThat(expected).exists();
    }

    @Test
    void writeSidecar_multipleUsers_separateFiles() throws Exception {
        Path bookFile = tempDir.resolve("book.epub");
        Files.createFile(bookFile);
        when(epubCfiService.convertCfiToXPointer(eq(bookFile), any()))
                .thenReturn(xpr("/body/DocFragment[1]/body/p/text().0",
                                "/body/DocFragment[1]/body/p/text().5"));

        service.writeSidecar(bookAt(bookFile), user("alice"), List.of(annotation("c1", "a", "highlight")));
        service.writeSidecar(bookAt(bookFile), user("bob"), List.of(annotation("c2", "b", "underline")));

        assertThat(tempDir.resolve("book.epub.sdr/metadata.epub.alice.lua")).exists();
        assertThat(tempDir.resolve("book.epub.sdr/metadata.epub.bob.lua")).exists();
    }

    // ── Lua content ───────────────────────────────────────────────────────────

    @Test
    void writeSidecar_luaContainsExpectedFields() throws Exception {
        Path bookFile = tempDir.resolve("book.epub");
        Files.createFile(bookFile);
        when(epubCfiService.convertCfiToXPointer(eq(bookFile), any()))
                .thenReturn(xpr("/body/DocFragment[1]/body/p/text().5",
                                "/body/DocFragment[1]/body/p/text().20"));

        service.writeSidecar(bookAt(bookFile), user("alice"),
                List.of(annotation("rangeCfi", "highlighted text", "highlight")));

        String lua = Files.readString(tempDir.resolve("book.epub.sdr/metadata.epub.alice.lua"));
        assertThat(lua).contains("[\"pos0\"] = \"/body/DocFragment[1]/body/p/text().5\"");
        assertThat(lua).contains("[\"pos1\"] = \"/body/DocFragment[1]/body/p/text().20\"");
        assertThat(lua).contains("[\"drawer\"] = \"lighten\"");
        assertThat(lua).contains("[\"datetime\"] = \"2024-01-15 10:30:00\"");
        assertThat(lua).contains("[\"notes\"] = \"highlighted text\"");
        assertThat(lua).contains("[\"chapter\"] = \"Chapter One\"");
    }

    @Test
    void writeSidecar_styleMapping_underlineBecomesUnderscore() throws Exception {
        Path bookFile = tempDir.resolve("book.epub");
        Files.createFile(bookFile);
        when(epubCfiService.convertCfiToXPointer(eq(bookFile), any()))
                .thenReturn(xpr("/body/DocFragment[1]/body/p/text().0",
                                "/body/DocFragment[1]/body/p/text().5"));

        service.writeSidecar(bookAt(bookFile), user("alice"),
                List.of(annotation("c", "text", "underline")));

        String lua = Files.readString(tempDir.resolve("book.epub.sdr/metadata.epub.alice.lua"));
        assertThat(lua).contains("[\"drawer\"] = \"underscore\"");
    }

    // ── empty / delete ────────────────────────────────────────────────────────

    @Test
    void writeSidecar_emptyAnnotations_deletesSidecarIfExists() throws Exception {
        Path bookFile = tempDir.resolve("book.epub");
        Files.createFile(bookFile);
        Path sidecar = tempDir.resolve("book.epub.sdr/metadata.epub.alice.lua");
        Files.createDirectories(sidecar.getParent());
        Files.writeString(sidecar, "old content");

        service.writeSidecar(bookAt(bookFile), user("alice"), List.of());

        assertThat(sidecar).doesNotExist();
    }

    @Test
    void writeSidecar_emptyAnnotations_noSidecarExists_doesNothing() throws Exception {
        Path bookFile = tempDir.resolve("book.epub");
        Files.createFile(bookFile);

        // Should not throw
        service.writeSidecar(bookAt(bookFile), user("alice"), List.of());

        assertThat(tempDir.resolve("book.epub.sdr/metadata.epub.alice.lua")).doesNotExist();
    }

    // ── error resilience ──────────────────────────────────────────────────────

    @Test
    void writeSidecar_cfiConversionFailure_skipsAnnotationAndContinues() throws Exception {
        Path bookFile = tempDir.resolve("book.epub");
        Files.createFile(bookFile);
        when(epubCfiService.convertCfiToXPointer(eq(bookFile), eq("bad-cfi")))
                .thenThrow(new IllegalArgumentException("bad cfi"));
        when(epubCfiService.convertCfiToXPointer(eq(bookFile), eq("good-cfi")))
                .thenReturn(xpr("/body/DocFragment[1]/body/p/text().0",
                                "/body/DocFragment[1]/body/p/text().5"));

        service.writeSidecar(bookAt(bookFile), user("alice"), List.of(
                annotation("bad-cfi", "skip me", "highlight"),
                annotation("good-cfi", "keep me", "highlight")
        ));

        String lua = Files.readString(tempDir.resolve("book.epub.sdr/metadata.epub.alice.lua"));
        assertThat(lua).contains("keep me");
        assertThat(lua).doesNotContain("skip me");
    }
}
