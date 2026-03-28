package org.booklore.service.kobo;

import org.booklore.model.dto.kobo.KoboSpanPositionMap;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.entity.UserBookFileProgressEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.util.koreader.EpubCfiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KoboBookmarkLocationResolverTest {

    @Mock
    private KoboSpanMapService koboSpanMapService;

    @Mock
    private EpubCfiService epubCfiService;

    private KoboBookmarkLocationResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new KoboBookmarkLocationResolver(koboSpanMapService, epubCfiService);
    }

    @Test
    void resolve_UsesFirstKoboSpanWhenOnlyHrefIsAvailable() {
        BookFileEntity bookFile = createBookFile();
        when(koboSpanMapService.getValidMap(bookFile)).thenReturn(Optional.of(singleChapterMap()));

        UserBookFileProgressEntity fileProgress = new UserBookFileProgressEntity();
        fileProgress.setBookFile(bookFile);
        fileProgress.setPositionHref("chapter1.xhtml");

        UserBookProgressEntity progress = new UserBookProgressEntity();
        progress.setEpubProgressHref("chapter1.xhtml");

        Optional<KoboBookmarkLocationResolver.ResolvedBookmarkLocation> result =
                resolver.resolve(progress, fileProgress);

        assertTrue(result.isPresent());
        assertEquals("kobo.1.1", result.get().value());
        assertEquals("KoboSpan", result.get().type());
        assertEquals("OPS/chapter1.xhtml", result.get().source());
        assertNull(result.get().contentSourceProgressPercent());
    }

    @Test
    void resolve_UsesStoredContentSourceProgressPercentToSelectNearestKoboSpan() {
        BookFileEntity bookFile = createBookFile();
        when(koboSpanMapService.getValidMap(bookFile)).thenReturn(Optional.of(singleChapterMap()));

        UserBookFileProgressEntity fileProgress = new UserBookFileProgressEntity();
        fileProgress.setBookFile(bookFile);
        fileProgress.setPositionHref("chapter1.xhtml");
        fileProgress.setContentSourceProgressPercent(80f);

        UserBookProgressEntity progress = new UserBookProgressEntity();
        progress.setEpubProgressHref("chapter1.xhtml");

        Optional<KoboBookmarkLocationResolver.ResolvedBookmarkLocation> result =
                resolver.resolve(progress, fileProgress);

        assertTrue(result.isPresent());
        assertEquals("kobo.1.2", result.get().value());
        assertEquals("KoboSpan", result.get().type());
        assertEquals("OPS/chapter1.xhtml", result.get().source());
        assertEquals(80f, result.get().contentSourceProgressPercent());
    }

    @Test
    void resolve_FallsBackToPrimaryEpubWhenFileProgressIsMissing() {
        BookFileEntity bookFile = createBookFile();
        when(koboSpanMapService.getValidMap(bookFile)).thenReturn(Optional.of(singleChapterMap()));

        UserBookProgressEntity progress = new UserBookProgressEntity();
        progress.setBook(bookFile.getBook());
        progress.setEpubProgressHref("chapter1.xhtml");

        Optional<KoboBookmarkLocationResolver.ResolvedBookmarkLocation> result =
                resolver.resolve(progress, null);

        assertTrue(result.isPresent());
        assertEquals("kobo.1.1", result.get().value());
        assertEquals("OPS/chapter1.xhtml", result.get().source());
        assertNull(result.get().contentSourceProgressPercent());
    }

    @Test
    void resolve_UsesGlobalProgressWhenHrefIsMissing() {
        BookFileEntity bookFile = createBookFile();
        when(koboSpanMapService.getValidMap(bookFile)).thenReturn(Optional.of(twoChapterMap()));

        UserBookFileProgressEntity fileProgress = new UserBookFileProgressEntity();
        fileProgress.setBookFile(bookFile);
        fileProgress.setProgressPercent(75f);

        UserBookProgressEntity progress = new UserBookProgressEntity();
        progress.setEpubProgressPercent(75f);

        Optional<KoboBookmarkLocationResolver.ResolvedBookmarkLocation> result =
                resolver.resolve(progress, fileProgress);

        assertTrue(result.isPresent());
        assertEquals("kobo.2.2", result.get().value());
        assertEquals("OPS/chapter2.xhtml", result.get().source());
        assertEquals(50f, result.get().contentSourceProgressPercent());
    }

    @Test
    void resolve_UsesCfiDerivedChapterPositionWhenStoredChapterProgressIsMissing() {
        BookFileEntity bookFile = createBookFile();
        when(koboSpanMapService.getValidMap(bookFile)).thenReturn(Optional.of(singleChapterMap()));
        when(epubCfiService.resolveCfiLocation(Path.of("/library/book.epub"), "epubcfi(/6/2!/4/2/2:15)"))
                .thenReturn(Optional.of(new EpubCfiService.CfiLocation("chapter1.xhtml", 80f)));

        UserBookFileProgressEntity fileProgress = new UserBookFileProgressEntity();
        fileProgress.setBookFile(bookFile);
        fileProgress.setPositionData("epubcfi(/6/2!/4/2/2:15)");

        UserBookProgressEntity progress = new UserBookProgressEntity();
        progress.setEpubProgress("epubcfi(/6/2!/4/2/2:15)");

        Optional<KoboBookmarkLocationResolver.ResolvedBookmarkLocation> result =
                resolver.resolve(progress, fileProgress);

        assertTrue(result.isPresent());
        assertEquals("kobo.1.2", result.get().value());
        assertEquals("OPS/chapter1.xhtml", result.get().source());
        assertEquals(80f, result.get().contentSourceProgressPercent());
    }

    private BookFileEntity createBookFile() {
        BookEntity book = new BookEntity();
        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setPath("/library");
        book.setLibraryPath(libraryPath);

        BookFileEntity bookFile = new BookFileEntity();
        bookFile.setId(10L);
        bookFile.setBook(book);
        bookFile.setBookType(BookFileType.EPUB);
        bookFile.setCurrentHash("hash-123");
        bookFile.setFileName("book.epub");
        bookFile.setFileSubPath("");
        book.setBookFiles(List.of(bookFile));

        return bookFile;
    }

    private KoboSpanPositionMap singleChapterMap() {
        return new KoboSpanPositionMap(List.of(
                new KoboSpanPositionMap.Chapter(
                        "OPS/chapter1.xhtml",
                        "OPS/chapter1.xhtml",
                        0,
                        0f,
                        1f,
                        List.of(
                                new KoboSpanPositionMap.Span("kobo.1.1", 0.2f),
                                new KoboSpanPositionMap.Span("kobo.1.2", 0.85f)
                        ))
        ));
    }

    private KoboSpanPositionMap twoChapterMap() {
        return new KoboSpanPositionMap(List.of(
                new KoboSpanPositionMap.Chapter(
                        "OPS/chapter1.xhtml",
                        "OPS/chapter1.xhtml",
                        0,
                        0f,
                        0.5f,
                        List.of(
                                new KoboSpanPositionMap.Span("kobo.1.1", 0.1f),
                                new KoboSpanPositionMap.Span("kobo.1.2", 0.9f)
                        )),
                new KoboSpanPositionMap.Chapter(
                        "OPS/chapter2.xhtml",
                        "OPS/chapter2.xhtml",
                        1,
                        0.5f,
                        1f,
                        List.of(
                                new KoboSpanPositionMap.Span("kobo.2.1", 0.1f),
                                new KoboSpanPositionMap.Span("kobo.2.2", 0.5f),
                                new KoboSpanPositionMap.Span("kobo.2.3", 0.9f)
                        ))
        ));
    }
}
