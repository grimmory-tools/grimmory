package org.booklore.service.koreader;

import org.booklore.config.AppProperties;
import org.booklore.model.entity.AnnotationEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.BookNoteV2Entity;
import org.booklore.repository.AnnotationRepository;
import org.booklore.repository.BookNoteV2Repository;
import org.booklore.util.koreader.EpubCfiService;
import org.grimmory.epub4j.cfi.XPointerResult;
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

    @Mock EpubCfiService epubCfiService;
    @Mock AppProperties appProperties;
    @Mock AnnotationRepository annotationRepository;
    @Mock BookNoteV2Repository bookNoteV2Repository;

    AnnotationSidecarService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new AnnotationSidecarService(epubCfiService, appProperties,
                annotationRepository, bookNoteV2Repository);
        when(appProperties.isLocalStorage()).thenReturn(true);
        when(annotationRepository.findByBookIdAndUserIdOrderByCreatedAtDesc(anyLong(), anyLong()))
                .thenReturn(List.of());
        when(bookNoteV2Repository.findByBookIdAndUserIdOrderByCreatedAtDesc(anyLong(), anyLong()))
                .thenReturn(List.of());
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
        when(u.getId()).thenReturn(7L);
        return u;
    }

    private AnnotationEntity annotation(String cfi, String text, String style) {
        AnnotationEntity ann = mock(AnnotationEntity.class);
        when(ann.getId()).thenReturn(42L);
        when(ann.getCfi()).thenReturn(cfi);
        when(ann.getText()).thenReturn(text);
        when(ann.getStyle()).thenReturn(style);
        when(ann.getColor()).thenReturn("#FFFF00");
        when(ann.getNote()).thenReturn("a personal note");
        when(ann.getVersion()).thenReturn(1L);
        when(ann.getChapterTitle()).thenReturn("Chapter One");
        when(ann.getCreatedAt()).thenReturn(LocalDateTime.of(2024, 1, 15, 10, 30, 0));
        return ann;
    }

    private BookNoteV2Entity bookNote(String cfi, String selectedText, String noteContent) {
        BookNoteV2Entity note = mock(BookNoteV2Entity.class);
        when(note.getId()).thenReturn(55L);
        when(note.getCfi()).thenReturn(cfi);
        when(note.getSelectedText()).thenReturn(selectedText);
        when(note.getNoteContent()).thenReturn(noteContent);
        when(note.getColor()).thenReturn("#FFC107");
        when(note.getVersion()).thenReturn(1L);
        when(note.getChapterTitle()).thenReturn("Chapter Two");
        when(note.getCreatedAt()).thenReturn(LocalDateTime.of(2024, 2, 1, 9, 0, 0));
        return note;
    }

    private XPointerResult xpr(String pos0, String pos1) {
        return new XPointerResult(pos0, pos0, pos1);
    }

    // ── guard conditions ──────────────────────────────────────────────────────

    @Test
    void writeSidecar_networkStorage_doesNothing() throws Exception {
        when(appProperties.isLocalStorage()).thenReturn(false);
        Path bookFile = tempDir.resolve("book.epub");
        Files.createFile(bookFile);
        BookEntity book = bookAt(bookFile);
        BookLoreUserEntity alice = user("alice");

        service.writeSidecar(book, alice);

        Path sidecar = AnnotationSidecarService.resolveSidecarPath(bookFile, "alice");
        assertThat(sidecar).doesNotExist();
        verifyNoInteractions(epubCfiService);
    }

    @Test
    void writeSidecar_nullFilePath_doesNothing() {
        BookEntity book = mock(BookEntity.class);
        when(book.getId()).thenReturn(1L);
        when(book.getFullFilePath()).thenReturn(null);

        service.writeSidecar(book, user("alice"));

        verifyNoInteractions(epubCfiService);
    }

    // ── file path ─────────────────────────────────────────────────────────────

    @Test
    void writeSidecar_createsCorrectPath() throws Exception {
        Path bookFile = tempDir.resolve("mybook.epub");
        Files.createFile(bookFile);
        AnnotationEntity ann = annotation("cfi1", "hello", "highlight");
        when(annotationRepository.findByBookIdAndUserIdOrderByCreatedAtDesc(anyLong(), anyLong()))
                .thenReturn(List.of(ann));
        when(epubCfiService.convertCfiToXPointer(eq(bookFile), any()))
                .thenReturn(xpr("/body/DocFragment[1]/body/p/text().0",
                                "/body/DocFragment[1]/body/p/text().10"));

        service.writeSidecar(bookAt(bookFile), user("alice"));

        assertThat(tempDir.resolve("mybook.epub.sdr/metadata.epub.alice.lua")).exists();
    }

    @Test
    void writeSidecar_multipleUsers_separateFiles() throws Exception {
        Path bookFile = tempDir.resolve("book.epub");
        Files.createFile(bookFile);
        when(epubCfiService.convertCfiToXPointer(eq(bookFile), any()))
                .thenReturn(xpr("/body/DocFragment[1]/body/p/text().0",
                                "/body/DocFragment[1]/body/p/text().5"));

        AnnotationEntity annA = annotation("c1", "a", "highlight");
        AnnotationEntity annB = annotation("c2", "b", "underline");

        when(annotationRepository.findByBookIdAndUserIdOrderByCreatedAtDesc(anyLong(), anyLong()))
                .thenReturn(List.of(annA))  // first call
                .thenReturn(List.of(annB)); // second call

        service.writeSidecar(bookAt(bookFile), user("alice"));
        service.writeSidecar(bookAt(bookFile), user("bob"));

        assertThat(tempDir.resolve("book.epub.sdr/metadata.epub.alice.lua")).exists();
        assertThat(tempDir.resolve("book.epub.sdr/metadata.epub.bob.lua")).exists();
    }

    // ── Lua content ───────────────────────────────────────────────────────────

    @Test
    void writeSidecar_luaContainsExpectedAnnotationFields() throws Exception {
        Path bookFile = tempDir.resolve("book.epub");
        Files.createFile(bookFile);
        AnnotationEntity ann = annotation("rangeCfi", "highlighted text", "highlight");
        when(annotationRepository.findByBookIdAndUserIdOrderByCreatedAtDesc(anyLong(), anyLong()))
                .thenReturn(List.of(ann));
        when(epubCfiService.convertCfiToXPointer(eq(bookFile), any()))
                .thenReturn(xpr("/body/DocFragment[1]/body/p/text().5",
                                "/body/DocFragment[1]/body/p/text().20"));

        service.writeSidecar(bookAt(bookFile), user("alice"));

        String lua = Files.readString(tempDir.resolve("book.epub.sdr/metadata.epub.alice.lua"));
        // KOReader fields
        assertThat(lua).contains("[\"pos0\"] = \"/body/DocFragment[1]/body/p/text().5\"");
        assertThat(lua).contains("[\"pos1\"] = \"/body/DocFragment[1]/body/p/text().20\"");
        assertThat(lua).contains("[\"drawer\"] = \"lighten\"");
        assertThat(lua).contains("[\"datetime\"] = \"2024-01-15 10:30:00\"");
        assertThat(lua).contains("[\"notes\"] = \"highlighted text\"");
        assertThat(lua).contains("[\"chapter\"] = \"Chapter One\"");
        // booklore_meta header
        assertThat(lua).contains("[\"booklore_meta\"]");
        assertThat(lua).contains("[\"username\"] = \"alice\"");
        assertThat(lua).contains("[\"user_id\"] = 7");
        assertThat(lua).contains("[\"book_id\"] = 1");
        assertThat(lua).contains("[\"schema_version\"] = 1");
        assertThat(lua).contains("-- booklore:schema_version=1");
        // per-entry booklore extension block
        assertThat(lua).contains("[\"booklore\"]");
        assertThat(lua).contains("[\"id\"] = 42");
        assertThat(lua).contains("[\"color\"] = \"#FFFF00\"");
        assertThat(lua).contains("[\"style\"] = \"highlight\"");
        assertThat(lua).contains("[\"note\"] = \"a personal note\"");
        assertThat(lua).contains("[\"version\"] = 1");
        // bookmark entry for the user-typed note
        assertThat(lua).contains("[\"bookmarks\"]");
        assertThat(lua).contains("[\"notes\"] = \"a personal note\"");
    }

    @Test
    void writeSidecar_bookNote_withSelectedText_writesHighlightAndBookmark() throws Exception {
        Path bookFile = tempDir.resolve("book.epub");
        Files.createFile(bookFile);
        BookNoteV2Entity note = bookNote("noteCfi", "selected passage", "My note content");
        when(bookNoteV2Repository.findByBookIdAndUserIdOrderByCreatedAtDesc(anyLong(), anyLong()))
                .thenReturn(List.of(note));
        when(epubCfiService.convertCfiToXPointer(eq(bookFile), any()))
                .thenReturn(xpr("/body/DocFragment[2]/body/p/text().0",
                                "/body/DocFragment[2]/body/p/text().15"));

        service.writeSidecar(bookAt(bookFile), user("alice"));

        String lua = Files.readString(tempDir.resolve("book.epub.sdr/metadata.epub.alice.lua"));
        // selected text in highlights section
        assertThat(lua).contains("[\"notes\"] = \"selected passage\"");
        assertThat(lua).contains("[\"drawer\"] = \"lighten\"");
        // note content in bookmarks section
        assertThat(lua).contains("[\"notes\"] = \"My note content\"");
        assertThat(lua).contains("[\"bookmarks\"]");
        // booklore extension on the highlight entry
        assertThat(lua).contains("[\"id\"] = 55");
        assertThat(lua).contains("[\"note\"] = \"My note content\"");
    }

    @Test
    void writeSidecar_bookNote_withoutSelectedText_bookmarkOnly() throws Exception {
        Path bookFile = tempDir.resolve("book.epub");
        Files.createFile(bookFile);
        BookNoteV2Entity note = bookNote("noteCfi", null, "Just a note, no selection");
        when(bookNoteV2Repository.findByBookIdAndUserIdOrderByCreatedAtDesc(anyLong(), anyLong()))
                .thenReturn(List.of(note));
        when(epubCfiService.convertCfiToXPointer(eq(bookFile), any()))
                .thenReturn(xpr("/body/DocFragment[2]/body/p/text().0",
                                "/body/DocFragment[2]/body/p/text().0"));

        service.writeSidecar(bookAt(bookFile), user("alice"));

        String lua = Files.readString(tempDir.resolve("book.epub.sdr/metadata.epub.alice.lua"));
        // note content in bookmarks
        assertThat(lua).contains("[\"notes\"] = \"Just a note, no selection\"");
        // highlights section should be empty (no drawer for a no-selection note)
        assertThat(lua).contains("[\"highlights\"] = {\n    },");
    }

    @Test
    void writeSidecar_styleMapping_underlineBecomesUnderscore() throws Exception {
        Path bookFile = tempDir.resolve("book.epub");
        Files.createFile(bookFile);
        AnnotationEntity ann = annotation("c", "text", "underline");
        when(annotationRepository.findByBookIdAndUserIdOrderByCreatedAtDesc(anyLong(), anyLong()))
                .thenReturn(List.of(ann));
        when(epubCfiService.convertCfiToXPointer(eq(bookFile), any()))
                .thenReturn(xpr("/body/DocFragment[1]/body/p/text().0",
                                "/body/DocFragment[1]/body/p/text().5"));

        service.writeSidecar(bookAt(bookFile), user("alice"));

        String lua = Files.readString(tempDir.resolve("book.epub.sdr/metadata.epub.alice.lua"));
        assertThat(lua).contains("[\"drawer\"] = \"underscore\"");
    }

    // ── empty / delete ────────────────────────────────────────────────────────

    @Test
    void writeSidecar_emptyAnnotationsAndNotes_deletesSidecarIfExists() throws Exception {
        Path bookFile = tempDir.resolve("book.epub");
        Files.createFile(bookFile);
        Path sidecar = tempDir.resolve("book.epub.sdr/metadata.epub.alice.lua");
        Files.createDirectories(sidecar.getParent());
        Files.writeString(sidecar, "old content");
        // default stubs return empty lists

        service.writeSidecar(bookAt(bookFile), user("alice"));

        assertThat(sidecar).doesNotExist();
    }

    @Test
    void writeSidecar_annotationsEmptyButNotesPresent_writesSidecar() throws Exception {
        Path bookFile = tempDir.resolve("book.epub");
        Files.createFile(bookFile);
        BookNoteV2Entity note = bookNote("noteCfi", null, "note with no highlight");
        when(bookNoteV2Repository.findByBookIdAndUserIdOrderByCreatedAtDesc(anyLong(), anyLong()))
                .thenReturn(List.of(note));
        when(epubCfiService.convertCfiToXPointer(eq(bookFile), any()))
                .thenReturn(xpr("/body/p/text().0", "/body/p/text().0"));

        service.writeSidecar(bookAt(bookFile), user("alice"));

        assertThat(tempDir.resolve("book.epub.sdr/metadata.epub.alice.lua")).exists();
    }

    @Test
    void writeSidecar_emptyAnnotations_noSidecarExists_doesNothing() throws Exception {
        Path bookFile = tempDir.resolve("book.epub");
        Files.createFile(bookFile);
        // default stubs return empty lists

        service.writeSidecar(bookAt(bookFile), user("alice"));

        assertThat(tempDir.resolve("book.epub.sdr/metadata.epub.alice.lua")).doesNotExist();
    }

    // ── error resilience ──────────────────────────────────────────────────────

    @Test
    void writeSidecar_cfiConversionFailure_skipsAnnotationAndContinues() throws Exception {
        Path bookFile = tempDir.resolve("book.epub");
        Files.createFile(bookFile);
        AnnotationEntity bad = annotation("bad-cfi", "skip me", "highlight");
        AnnotationEntity good = annotation("good-cfi", "keep me", "highlight");
        when(annotationRepository.findByBookIdAndUserIdOrderByCreatedAtDesc(anyLong(), anyLong()))
                .thenReturn(List.of(bad, good));
        when(epubCfiService.convertCfiToXPointer(eq(bookFile), eq("bad-cfi")))
                .thenThrow(new IllegalArgumentException("bad cfi"));
        when(epubCfiService.convertCfiToXPointer(eq(bookFile), eq("good-cfi")))
                .thenReturn(xpr("/body/DocFragment[1]/body/p/text().0",
                                "/body/DocFragment[1]/body/p/text().5"));

        service.writeSidecar(bookAt(bookFile), user("alice"));

        String lua = Files.readString(tempDir.resolve("book.epub.sdr/metadata.epub.alice.lua"));
        assertThat(lua).contains("keep me");
        assertThat(lua).doesNotContain("skip me");
    }

    // ── annotation with note bookmark ─────────────────────────────────────────

    @Test
    void writeSidecar_annotationWithNote_writesBookmarkEntry() throws Exception {
        Path bookFile = tempDir.resolve("book.epub");
        Files.createFile(bookFile);
        AnnotationEntity ann = annotation("cfi", "highlighted text", "highlight");
        when(annotationRepository.findByBookIdAndUserIdOrderByCreatedAtDesc(anyLong(), anyLong()))
                .thenReturn(List.of(ann));
        when(epubCfiService.convertCfiToXPointer(eq(bookFile), any()))
                .thenReturn(xpr("/body/DocFragment[1]/body/p/text().5",
                                "/body/DocFragment[1]/body/p/text().20"));

        service.writeSidecar(bookAt(bookFile), user("alice"));

        String lua = Files.readString(tempDir.resolve("book.epub.sdr/metadata.epub.alice.lua"));
        assertThat(lua).containsPattern("\\[\"bookmarks\"\\] = \\{[^}]+\\[\"notes\"\\] = \"a personal note\"");
        assertThat(lua).contains("[\"highlights\"]");
    }

    @Test
    void writeSidecar_annotationWithoutNote_bookmarksEmpty() throws Exception {
        Path bookFile = tempDir.resolve("book.epub");
        Files.createFile(bookFile);
        AnnotationEntity noNote = annotation("cfi", "just a highlight", "highlight");
        when(noNote.getNote()).thenReturn(null);
        when(annotationRepository.findByBookIdAndUserIdOrderByCreatedAtDesc(anyLong(), anyLong()))
                .thenReturn(List.of(noNote));
        when(epubCfiService.convertCfiToXPointer(eq(bookFile), any()))
                .thenReturn(xpr("/body/DocFragment[1]/body/p/text().0",
                                "/body/DocFragment[1]/body/p/text().5"));

        service.writeSidecar(bookAt(bookFile), user("alice"));

        String lua = Files.readString(tempDir.resolve("book.epub.sdr/metadata.epub.alice.lua"));
        assertThat(lua).contains("[\"bookmarks\"] = {\n    },");
    }
}
