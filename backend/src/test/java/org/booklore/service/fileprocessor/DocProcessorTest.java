package org.booklore.service.fileprocessor;

import org.booklore.mapper.BookMapper;
import org.booklore.model.document.DocumentBlock;
import org.booklore.model.document.DocumentContent;
import org.booklore.model.document.DocumentParseResult;
import org.booklore.model.dto.settings.LibraryFile;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.DocumentParseStatus;
import org.booklore.repository.BookAdditionalFileRepository;
import org.booklore.repository.BookRepository;
import org.booklore.service.book.BookCreatorService;
import org.booklore.service.document.DocumentContentExtractor;
import org.booklore.service.metadata.MetadataMatchService;
import org.booklore.service.metadata.sidecar.SidecarMetadataWriter;
import org.booklore.util.BookUtils;
import org.booklore.util.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocProcessorTest {

    @Mock
    private BookRepository bookRepository;
    @Mock
    private BookAdditionalFileRepository bookAdditionalFileRepository;
    @Mock
    private BookCreatorService bookCreatorService;
    @Mock
    private BookMapper bookMapper;
    @Mock
    private FileService fileService;
    @Mock
    private MetadataMatchService metadataMatchService;
    @Mock
    private SidecarMetadataWriter sidecarMetadataWriter;
    @Mock
    private DocumentContentExtractor documentContentExtractor;
    @Mock
    private LibraryFile libraryFile;

    @TempDir
    Path tempDir;

    private DocProcessor processor;
    private BookEntity book;
    private BookFileEntity bookFile;

    @BeforeEach
    void setUp() {
        processor = new DocProcessor(
                bookRepository,
                bookAdditionalFileRepository,
                bookCreatorService,
                bookMapper,
                fileService,
                metadataMatchService,
                sidecarMetadataWriter,
                documentContentExtractor);
        bookFile = BookFileEntity.builder()
                .fileName("report.docx")
                .fileSubPath("")
                .bookType(BookFileType.DOC)
                .isBookFormat(true)
                .build();
        book = BookEntity.builder()
                .bookFiles(List.of(bookFile))
                .libraryPath(LibraryPathEntity.builder().path(tempDir.toString()).build())
                .metadata(BookMetadataEntity.builder().build())
                .build();
        when(bookCreatorService.createShellBook(libraryFile, BookFileType.DOC)).thenReturn(book);
    }

    @Test
    void appliesPropertiesAndReadableStatusFromSingleParseResult() {
        DocumentContent content = new DocumentContent(
                List.of(
                        new DocumentBlock(1, 0, "Opening paragraph"),
                        new DocumentBlock(4, 0, "Distinctive café phrase"),
                        new DocumentBlock(7, 0, "Closing paragraph")),
                "Internal Report", "Ada Lovelace", LocalDate.of(2026, 7, 31));
        when(documentContentExtractor.parse(book.getFullFilePath().toFile()))
                .thenReturn(DocumentParseResult.readable(content));

        BookEntity result = processor.processNewFile(libraryFile);

        assertThat(result.getMetadata().getTitle()).isEqualTo("Internal Report");
        assertThat(result.getMetadata().getPublishedDate()).isEqualTo(LocalDate.of(2026, 7, 31));
        assertThat(result.getPrimaryBookFile().getDocumentParseStatus()).isEqualTo(DocumentParseStatus.READABLE);
        assertThat(BookUtils.extractDocumentBodySearchText(result.getMetadata().getSearchText()))
                .isEqualTo("opening paragraph distinctive cafe phrase closing paragraph");
        verify(bookCreatorService).addAuthorsToBook(Set.of("Ada Lovelace"), book);
        verify(documentContentExtractor).parse(book.getFullFilePath().toFile());
    }

    @Test
    void keepsFilenameTitleAndCatalogEntityForUnreadableDocument() {
        when(documentContentExtractor.parse(book.getFullFilePath().toFile()))
                .thenReturn(DocumentParseResult.unreadable());

        BookEntity result = processor.processNewFile(libraryFile);

        assertThat(result).isSameAs(book);
        assertThat(result.getMetadata().getTitle()).isEqualTo("report");
        assertThat(result.getPrimaryBookFile().getDocumentParseStatus()).isEqualTo(DocumentParseStatus.UNREADABLE);
        assertThat(BookUtils.extractDocumentBodySearchText(result.getMetadata().getSearchText())).isEmpty();
        verify(documentContentExtractor).parse(book.getFullFilePath().toFile());
    }

    @Test
    void leavesVerdictUnknownWhenParserCapacityIsTemporarilyUnavailable() {
        when(documentContentExtractor.parse(book.getFullFilePath().toFile()))
                .thenReturn(DocumentParseResult.indeterminate());

        BookEntity result = processor.processNewFile(libraryFile);

        assertThat(result.getMetadata().getTitle()).isEqualTo("report");
        assertThat(result.getPrimaryBookFile().getDocumentParseStatus()).isNull();
        assertThat(BookUtils.extractDocumentBodySearchText(result.getMetadata().getSearchText())).isEmpty();
    }

    @Test
    void keepsReadableVerdictWhenBodyProjectionRequiresTruncation() {
        DocumentContent content = new DocumentContent(
                List.of(new DocumentBlock(0, 0, "📚".repeat(20_000))));
        when(documentContentExtractor.parse(book.getFullFilePath().toFile()))
                .thenReturn(DocumentParseResult.readable(content));

        BookEntity result = processor.processNewFile(libraryFile);

        assertThat(result.getPrimaryBookFile().getDocumentParseStatus()).isEqualTo(DocumentParseStatus.READABLE);
        assertThat(result.getMetadata().getSearchText().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .hasSizeLessThanOrEqualTo(60 * 1024);
        verify(documentContentExtractor).parse(book.getFullFilePath().toFile());
    }
}
