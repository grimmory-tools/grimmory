package org.grimmory.service.opds;

import org.grimmory.config.security.service.AuthenticationService;
import org.grimmory.config.security.userdetails.OpdsUserDetails;
import org.grimmory.model.dto.Book;
import org.grimmory.model.dto.BookFile;
import org.grimmory.model.dto.BookMetadata;
import org.grimmory.model.dto.LibraryPath;
import org.grimmory.model.dto.OpdsUserV2;
import org.grimmory.model.enums.BookFileType;
import org.grimmory.model.enums.OpdsSortOrder;
import org.grimmory.service.MagicShelfService;
import org.grimmory.util.ArchiveUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpdsFeedServiceMimeTypeTest {

    private AuthenticationService authenticationService;
    private OpdsBookService opdsBookService;
    private MagicShelfService magicShelfService;
    private MagicShelfBookService magicShelfBookService;
    private OpdsFeedService opdsFeedService;
    private HttpServletRequest request;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        authenticationService = mock(AuthenticationService.class);
        opdsBookService = mock(OpdsBookService.class);
        magicShelfService = mock(MagicShelfService.class);
        magicShelfBookService = mock(MagicShelfBookService.class);
        opdsFeedService = new OpdsFeedService(authenticationService, opdsBookService, magicShelfService, magicShelfBookService);
        request = mock(HttpServletRequest.class);
        
        mockAuthenticatedUser();
        mockRequest();
    }

    private void mockAuthenticatedUser() {
        OpdsUserDetails userDetails = mock(OpdsUserDetails.class);
        OpdsUserV2 v2 = mock(OpdsUserV2.class);
        when(userDetails.getOpdsUserV2()).thenReturn(v2);
        when(v2.getUserId()).thenReturn(1L);
        when(v2.getSortOrder()).thenReturn(OpdsSortOrder.RECENT);
        when(authenticationService.getOpdsUser()).thenReturn(userDetails);
    }

    private void mockRequest() {
        when(request.getParameter(any())).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/api/v1/opds/catalog");
        when(request.getQueryString()).thenReturn(null);
    }

    private void mockBooksPage(Book book) {
        Page<Book> page = new PageImpl<>(List.of(book), PageRequest.of(0, 50), 1);
        when(opdsBookService.getBooksPage(eq(1L), any(), any(), any(), eq(0), eq(50))).thenReturn(page);
        when(opdsBookService.applySortOrder(any(), any())).thenReturn(page);
    }

    @Test
    void testMimeTypeForEpub() {
        Book book = createBook(BookFileType.EPUB, "book.epub");
        mockBooksPage(book);
        String xml = opdsFeedService.generateCatalogFeed(request);
        assertThat(xml).contains("type=\"application/epub+zip\"");
    }

    @Test
    void testMimeTypeForPdf() {
        Book book = createBook(BookFileType.PDF, "document.pdf");
        mockBooksPage(book);
        String xml = opdsFeedService.generateCatalogFeed(request);
        assertThat(xml).contains("type=\"application/pdf\"");
    }

    @Test
    void testMimeTypeForCbz() {
        Book book = createBook(BookFileType.CBX, "comic.cbz");
        mockBooksPage(book);
        String xml = opdsFeedService.generateCatalogFeed(request);
        assertThat(xml).contains("type=\"application/vnd.comicbook+zip\"");
    }

    @Test
    void testMimeTypeForCbr() throws IOException {
        File rarFile = tempDir.resolve("comic.cbr").toFile();
        try (FileOutputStream fos = new FileOutputStream(rarFile)) {
            // RAR 4.x magic bytes
            fos.write(new byte[]{0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00});
        }
        Book book = createBook(BookFileType.CBX, "comic.cbr");
        mockBooksPage(book);
        String xml = opdsFeedService.generateCatalogFeed(request);
        assertThat(xml).contains("type=\"application/vnd.comicbook-rar\"");
    }

    @Test
    void testMimeTypeForCb7() throws IOException {
        File sevenZFile = tempDir.resolve("comic.cb7").toFile();
        try (FileOutputStream fos = new FileOutputStream(sevenZFile)) {
            // 7z magic bytes
            fos.write(new byte[]{0x37, 0x7A, (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C});
        }
        Book book = createBook(BookFileType.CBX, "comic.cb7");
        mockBooksPage(book);
        String xml = opdsFeedService.generateCatalogFeed(request);
        assertThat(xml).contains("type=\"application/x-7z-compressed\"");
    }

    @Test
    void testMimeTypeForCbt() {
        // CBT is TAR-based; ArchiveUtils has no TAR magic byte detection,
        // so an unrecognised archive defaults to the CBX fallback mime type.
        Book book = createBook(BookFileType.CBX, "comic.cbt");
        mockBooksPage(book);
        String xml = opdsFeedService.generateCatalogFeed(request);
        assertThat(xml).contains("type=\"application/vnd.comicbook+zip\"");
    }

    @Test
    void testMimeTypeForFb2() {
        Book book = createBook(BookFileType.FB2, "book.fb2");
        mockBooksPage(book);
        String xml = opdsFeedService.generateCatalogFeed(request);
        assertThat(xml).contains("type=\"application/x-fictionbook+xml\"");
    }

    @Test
    void testMimeTypeForZippedFb2() throws IOException {
        File zipAsFb2 = tempDir.resolve("book_zipped.fb2").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipAsFb2))) {
            zos.putNextEntry(new ZipEntry("book.fb2"));
            zos.write("<FictionBook>...</FictionBook>".getBytes());
            zos.closeEntry();
        }

        Book book = createBook(BookFileType.FB2, "book_zipped.fb2");
        mockBooksPage(book);

        String xml = opdsFeedService.generateCatalogFeed(request);
        assertThat(xml).contains("type=\"application/zip\"");
    }

    @Test
    void testMimeTypeForZipNamedAsCbr() throws IOException {
        // Create a ZIP file named .cbr
        File zipAsCbr = tempDir.resolve("mismatched.cbr").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipAsCbr))) {
            zos.putNextEntry(new ZipEntry("test.txt"));
            zos.write("hello".getBytes());
            zos.closeEntry();
        }

        Book book = createBook(BookFileType.CBX, "mismatched.cbr");
        mockBooksPage(book);

        String xml = opdsFeedService.generateCatalogFeed(request);
        
        // Should detect as ZIP (application/vnd.comicbook+zip) despite .cbr extension
        assertThat(xml).contains("type=\"application/vnd.comicbook+zip\"");
    }

    @Test
    void testMimeTypeForRarNamedAsCbz() throws IOException {
        // Create a RAR file named .cbz (fake RAR header)
        File rarAsCbz = tempDir.resolve("mismatched.cbz").toFile();
        try (FileOutputStream fos = new FileOutputStream(rarAsCbz)) {
            // RAR 5.0 magic number
            fos.write(new byte[]{0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00});
        }

        Book book = createBook(BookFileType.CBX, "mismatched.cbz");
        mockBooksPage(book);

        String xml = opdsFeedService.generateCatalogFeed(request);
        
        // Should detect as RAR (application/vnd.comicbook-rar) despite .cbz extension
        assertThat(xml).contains("type=\"application/vnd.comicbook-rar\"");
    }

    @Test
    void testMimeTypeFromCachedArchiveType() {
        Book book = createBookWithArchiveType(BookFileType.CBX, "whatever.cbz", ArchiveUtils.ArchiveType.RAR);

        mockBooksPage(book);

        String xml = opdsFeedService.generateCatalogFeed(request);

        // Should use cached type (RAR) even if filename is .cbz and no file exists (mocked logic)
        // Note: The logic in OpdsFeedService prioritizes cached type.
        // However, my test creates a real file in other tests, but here I can skip file creation
        // because the cached type check happens first.

        assertThat(xml).contains("type=\"application/vnd.comicbook-rar\"");
    }

    private Book createBook(BookFileType type, String fileName) {
        return createBookWithArchiveType(type, fileName, null);
    }

    private Book createBookWithArchiveType(BookFileType type, String fileName, ArchiveUtils.ArchiveType archiveType) {
        String filePath = tempDir.resolve(fileName).toString();
        return Book.builder()
                .id(1L)
                .primaryFile(BookFile.builder()
                        .id(1L)
                        .bookType(type)
                        .fileName(fileName)
                        .filePath(filePath)
                        .fileSubPath("")
                        .archiveType(archiveType)
                        .build())
                .libraryPath(LibraryPath.builder().path(tempDir.toString()).build())
                .addedOn(Instant.now())
                .metadata(BookMetadata.builder().title("Test Book").build())
                .build();
    }
}
