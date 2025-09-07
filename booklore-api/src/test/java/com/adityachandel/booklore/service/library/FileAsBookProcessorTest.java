package com.adityachandel.booklore.service.library;

import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.FileProcessResult;
import com.adityachandel.booklore.model.enums.FileProcessStatus;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.service.event.BookEventBroadcaster;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessor;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessorRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FileAsBookProcessorTest {

    @Mock
    private BookEventBroadcaster bookEventBroadcaster;

    @Mock
    private BookFileProcessorRegistry processorRegistry;

    @Mock
    private BookFileProcessor bookFileProcessor;

    @InjectMocks
    private FileAsBookProcessor fileAsBookProcessor;

    @Captor
    private ArgumentCaptor<Book> bookCaptor;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    void processLibraryFiles_shouldProcessAllValidFiles() {
        // Given
        LibraryEntity libraryEntity = new LibraryEntity();
        LibraryPathEntity libraryPathEntity = new LibraryPathEntity();
        libraryPathEntity.setPath("/library/path");
        List<LibraryFile> libraryFiles = new ArrayList<>();

        LibraryFile file1 = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileName("book1.epub")
                .fileSubPath("books")
                .bookFileType(BookFileType.EPUB)
                .build();

        LibraryFile file2 = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileName("book2.pdf")
                .fileSubPath("books")
                .bookFileType(BookFileType.PDF)
                .build();

        libraryFiles.add(file1);
        libraryFiles.add(file2);

        Book book1 = Book.builder()
                .fileName("book1.epub")
                .title("Book 1")
                .bookType(BookFileType.EPUB)
                .build();

        Book book2 = Book.builder()
                .fileName("book2.pdf")
                .title("Book 2")
                .bookType(BookFileType.PDF)
                .build();

        when(processorRegistry.getProcessorOrThrow(BookFileType.EPUB)).thenReturn(bookFileProcessor);
        when(processorRegistry.getProcessorOrThrow(BookFileType.PDF)).thenReturn(bookFileProcessor);
        when(bookFileProcessor.processFile(file1))
                .thenReturn(new FileProcessResult(book1, FileProcessStatus.NEW, null));
        when(bookFileProcessor.processFile(file2))
                .thenReturn(new FileProcessResult(book2, FileProcessStatus.NEW, null));

        // When
        fileAsBookProcessor.processLibraryFiles(libraryFiles, libraryEntity);

        // Then
        verify(bookEventBroadcaster, times(2)).broadcastBookAddEvent(bookCaptor.capture());

        List<Book> capturedBooks = bookCaptor.getAllValues();
        assertThat(capturedBooks).hasSize(2);
        assertThat(capturedBooks).containsExactly(book1, book2);
    }

    @Test
    void processLibraryFiles_shouldSkipFilesWithUnsupportedExtensions() {
        // Given
        LibraryEntity libraryEntity = new LibraryEntity();
        LibraryPathEntity libraryPathEntity = new LibraryPathEntity();
        libraryPathEntity.setPath("/library/path");
        List<LibraryFile> libraryFiles = new ArrayList<>();

        LibraryFile validFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileName("book.epub")
                .fileSubPath("books")
                .bookFileType(BookFileType.EPUB)
                .build();

        LibraryFile invalidFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileName("document.txt")
                .fileSubPath("docs")
                .bookFileType(null)
                .build();

        libraryFiles.add(validFile);
        libraryFiles.add(invalidFile);

        Book book = Book.builder()
                .fileName("book.epub")
                .title("Valid Book")
                .bookType(BookFileType.EPUB)
                .build();

        when(processorRegistry.getProcessorOrThrow(BookFileType.EPUB)).thenReturn(bookFileProcessor);
        when(bookFileProcessor.processFile(validFile))
                .thenReturn(new FileProcessResult(book, FileProcessStatus.NEW, null));

        // When
        fileAsBookProcessor.processLibraryFiles(libraryFiles, libraryEntity);

        // Then
        verify(bookEventBroadcaster, times(1)).broadcastBookAddEvent(book);
        verify(processorRegistry, times(1)).getProcessorOrThrow(any());
    }

    @Test
    void processLibraryFiles_shouldHandleEmptyList() {
        // Given
        LibraryEntity libraryEntity = new LibraryEntity();
        List<LibraryFile> libraryFiles = new ArrayList<>();

        // When
        fileAsBookProcessor.processLibraryFiles(libraryFiles, libraryEntity);

        // Then
        verify(bookEventBroadcaster, never()).broadcastBookAddEvent(any());
        verify(processorRegistry, never()).getProcessorOrThrow(any());
    }

    @Test
    void processLibraryFile_shouldReturnNullForUnsupportedFileType() {
        // Given
        LibraryEntity libraryEntity = new LibraryEntity();
        LibraryPathEntity libraryPathEntity = new LibraryPathEntity();
        libraryPathEntity.setPath("/library/path");

        LibraryFile libraryFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileName("document.txt")
                .fileSubPath("docs")
                .bookFileType(null)
                .build();

        // When
        FileProcessResult result = fileAsBookProcessor.processLibraryFile(libraryFile);

        // Then
        assertThat(result).isNull();
        verify(processorRegistry, never()).getProcessorOrThrow(any());
    }

    @Test
    void processLibraryFile_shouldProcessSupportedFileTypes() {
        // Given
        LibraryEntity libraryEntity = new LibraryEntity();
        LibraryPathEntity libraryPathEntity = new LibraryPathEntity();
        libraryPathEntity.setPath("/library/path");

        LibraryFile libraryFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileName("book.epub")
                .fileSubPath("books")
                .bookFileType(BookFileType.EPUB)
                .build();

        Book expectedBook = Book.builder()
                .fileName("book.epub")
                .title("Test Book")
                .bookType(BookFileType.EPUB)
                .build();

        when(processorRegistry.getProcessorOrThrow(BookFileType.EPUB)).thenReturn(bookFileProcessor);
        when(bookFileProcessor.processFile(libraryFile))
                .thenReturn(new FileProcessResult(expectedBook, FileProcessStatus.NEW, null));

        // When
        FileProcessResult result = fileAsBookProcessor.processLibraryFile(libraryFile);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getBook()).isEqualTo(expectedBook);

        verify(processorRegistry).getProcessorOrThrow(BookFileType.EPUB);
        verify(bookFileProcessor).processFile(libraryFile);
    }

    @Test
    void processLibraryFile_shouldHandleNullReturnFromProcessor() {
        // Given
        LibraryEntity libraryEntity = new LibraryEntity();
        LibraryPathEntity libraryPathEntity = new LibraryPathEntity();
        libraryPathEntity.setPath("/library/path");

        LibraryFile libraryFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileName("book.pdf")
                .fileSubPath("books")
                .bookFileType(BookFileType.PDF)
                .build();

        when(processorRegistry.getProcessorOrThrow(BookFileType.PDF)).thenReturn(bookFileProcessor);
        when(bookFileProcessor.processFile(libraryFile)).thenReturn(null);

        // When
        FileProcessResult result = fileAsBookProcessor.processLibraryFile(libraryFile);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void processLibraryFiles_shouldNotSendNotificationWhenProcessorReturnsNull() {
        // Given
        LibraryEntity libraryEntity = new LibraryEntity();
        LibraryPathEntity libraryPathEntity = new LibraryPathEntity();
        libraryPathEntity.setPath("/library/path");
        List<LibraryFile> libraryFiles = new ArrayList<>();

        LibraryFile file = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileName("book.epub")
                .fileSubPath("books")
                .bookFileType(BookFileType.EPUB)
                .build();

        libraryFiles.add(file);

        when(processorRegistry.getProcessorOrThrow(BookFileType.EPUB)).thenReturn(bookFileProcessor);
        when(bookFileProcessor.processFile(file)).thenReturn(null);

        // When
        fileAsBookProcessor.processLibraryFiles(libraryFiles, libraryEntity);

        // Then
        verify(bookEventBroadcaster, never()).broadcastBookAddEvent(any());
    }

    @Test
    void processLibraryFiles_shouldProcessAllSupportedFileExtensions() {
        // Given
        LibraryEntity libraryEntity = new LibraryEntity();
        LibraryPathEntity libraryPathEntity = new LibraryPathEntity();
        libraryPathEntity.setPath("/library/path");
        List<LibraryFile> libraryFiles = new ArrayList<>();

        LibraryFile epubFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileName("book.epub")
                .fileSubPath("books")
                .bookFileType(BookFileType.EPUB)
                .build();

        LibraryFile pdfFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileName("book.pdf")
                .fileSubPath("books")
                .bookFileType(BookFileType.PDF)
                .build();

        LibraryFile cbzFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileName("comic.cbz")
                .fileSubPath("comics")
                .bookFileType(BookFileType.CBX)
                .build();

        LibraryFile cbrFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileName("comic.cbr")
                .fileSubPath("comics")
                .bookFileType(BookFileType.CBX)
                .build();

        libraryFiles.add(epubFile);
        libraryFiles.add(pdfFile);
        libraryFiles.add(cbzFile);
        libraryFiles.add(cbrFile);

        Book epubBook = Book.builder()
                .fileName("book.epub")
                .bookType(BookFileType.EPUB)
                .build();

        Book pdfBook = Book.builder()
                .fileName("book.pdf")
                .bookType(BookFileType.PDF)
                .build();

        Book cbzBook = Book.builder()
                .fileName("comic.cbz")
                .bookType(BookFileType.CBX)
                .build();

        Book cbrBook = Book.builder()
                .fileName("comic.cbr")
                .bookType(BookFileType.CBX)
                .build();

        when(processorRegistry.getProcessorOrThrow(BookFileType.EPUB)).thenReturn(bookFileProcessor);
        when(processorRegistry.getProcessorOrThrow(BookFileType.PDF)).thenReturn(bookFileProcessor);
        when(processorRegistry.getProcessorOrThrow(BookFileType.CBX)).thenReturn(bookFileProcessor);

        when(bookFileProcessor.processFile(epubFile))
                .thenReturn(new FileProcessResult(epubBook, FileProcessStatus.NEW, null));
        when(bookFileProcessor.processFile(pdfFile))
                .thenReturn(new FileProcessResult(pdfBook, FileProcessStatus.NEW, null));
        when(bookFileProcessor.processFile(cbzFile))
                .thenReturn(new FileProcessResult(cbzBook, FileProcessStatus.NEW, null));
        when(bookFileProcessor.processFile(cbrFile))
                .thenReturn(new FileProcessResult(cbrBook, FileProcessStatus.NEW, null));

        // When
        fileAsBookProcessor.processLibraryFiles(libraryFiles, libraryEntity);

        // Then
        verify(bookEventBroadcaster, times(4)).broadcastBookAddEvent(any(Book.class));
    }
}