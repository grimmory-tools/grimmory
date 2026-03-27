package org.booklore.service.kobo;

import io.documentnode.epub4j.domain.Author;
import io.documentnode.epub4j.domain.Book;
import io.documentnode.epub4j.domain.Resource;
import io.documentnode.epub4j.epub.EpubWriter;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.settings.KoboSettings;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.entity.UserBookFileProgressEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.service.appsettings.AppSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KoboBookmarkLocationResolverTest {

    private static final String EPUB_CHAPTER = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE html>
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head><title>Chapter 1</title></head>
            <body>
                <div>
                    <h1>Chapter 1</h1>
                    <p>First paragraph with some text content.</p>
                    <p>Second paragraph with more text.</p>
                </div>
            </body>
            </html>
            """;

    private static final String KEPUB_CHAPTER = """
            <html>
            <body>
                <div>
                    <h1>Chapter 1</h1>
                    <p><span class="koboSpan" id="kobo.1.1"></span>First paragraph with some text content.</p>
                    <p><span class="koboSpan" id="kobo.1.2"></span>Second paragraph with more text.</p>
                </div>
            </body>
            </html>
            """;

    @TempDir
    Path tempDir;

    @Mock
    private AppSettingService appSettingService;

    @Mock
    private KepubConversionService kepubConversionService;

    private KoboBookmarkLocationResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new KoboBookmarkLocationResolver(appSettingService, kepubConversionService);

        AppSettings appSettings = new AppSettings();
        appSettings.setKoboSettings(KoboSettings.builder()
                .convertToKepub(false)
                .forceEnableHyphenation(false)
                .build());
        when(appSettingService.getAppSettings()).thenReturn(appSettings);
    }

    @Test
    void resolve_UsesFirstKoboSpanWhenOnlyHrefIsAvailable() throws Exception {
        File epubFile = createTestEpub("test.epub");
        File kepubFile = createKepub("test.kepub.epub");
        when(kepubConversionService.convertEpubToKepub(any(), any(), anyBoolean())).thenReturn(kepubFile);

        BookFileEntity bookFile = createBookFile(epubFile);

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
        assertEquals("OEBPS/chapter1.xhtml", result.get().source());
        assertEquals(0f, result.get().contentSourceProgressPercent());
    }

    @Test
    void resolve_UsesStoredContentSourceProgressPercentToSelectNearestKoboSpan() throws Exception {
        File epubFile = createTestEpub("test.epub");
        File kepubFile = createKepub("test.kepub.epub");
        when(kepubConversionService.convertEpubToKepub(any(), any(), anyBoolean())).thenReturn(kepubFile);

        BookFileEntity bookFile = createBookFile(epubFile);

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
        assertEquals("OEBPS/chapter1.xhtml", result.get().source());
        assertEquals(80f, result.get().contentSourceProgressPercent());
    }

    @Test
    void resolve_FallsBackToPrimaryEpubWhenFileProgressIsMissing() throws Exception {
        File epubFile = createTestEpub("test.epub");
        File kepubFile = createKepub("test.kepub.epub");
        when(kepubConversionService.convertEpubToKepub(any(), any(), anyBoolean())).thenReturn(kepubFile);

        BookFileEntity bookFile = createBookFile(epubFile);

        UserBookProgressEntity progress = new UserBookProgressEntity();
        progress.setBook(bookFile.getBook());
        progress.setEpubProgressHref("chapter1.xhtml");

        Optional<KoboBookmarkLocationResolver.ResolvedBookmarkLocation> result =
                resolver.resolve(progress, null);

        assertTrue(result.isPresent());
        assertEquals("kobo.1.1", result.get().value());
        assertEquals("KoboSpan", result.get().type());
        assertEquals("OEBPS/chapter1.xhtml", result.get().source());
        assertEquals(0f, result.get().contentSourceProgressPercent());
    }

    private BookFileEntity createBookFile(File epubFile) {
        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setPath(tempDir.toString());

        BookEntity book = new BookEntity();
        book.setLibraryPath(libraryPath);

        BookFileEntity bookFile = new BookFileEntity();
        bookFile.setBook(book);
        bookFile.setBookType(BookFileType.EPUB);
        bookFile.setFileName(epubFile.getName());
        bookFile.setFileSubPath("");
        book.setBookFiles(List.of(bookFile));

        return bookFile;
    }

    private File createTestEpub(String fileName) throws IOException {
        Book book = new Book();
        book.getMetadata().addTitle("Resolver Test");
        book.getMetadata().addAuthor(new Author("Test Author"));
        book.addSection("Chapter 1", new Resource(EPUB_CHAPTER.getBytes(StandardCharsets.UTF_8), "chapter1.xhtml"));

        File epubFile = tempDir.resolve(fileName).toFile();
        try (FileOutputStream out = new FileOutputStream(epubFile)) {
            new EpubWriter().write(book, out);
        }
        return epubFile;
    }

    private File createKepub(String fileName) throws IOException {
        File kepubFile = tempDir.resolve(fileName).toFile();
        try (ZipOutputStream outputStream = new ZipOutputStream(new FileOutputStream(kepubFile))) {
            outputStream.putNextEntry(new ZipEntry("OEBPS/chapter1.xhtml"));
            outputStream.write(KEPUB_CHAPTER.getBytes(StandardCharsets.UTF_8));
            outputStream.closeEntry();
        }
        return kepubFile;
    }
}
