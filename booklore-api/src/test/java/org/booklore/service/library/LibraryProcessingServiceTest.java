package org.booklore.service.library;

import jakarta.persistence.EntityManager;
import org.booklore.exception.APIException;
import org.booklore.model.dto.settings.LibraryFile;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.repository.BookAdditionalFileRepository;
import org.booklore.repository.LibraryRepository;
import org.booklore.service.NotificationService;
import org.booklore.task.options.RescanLibraryContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LibraryProcessingServiceTest {

    @Mock
    private LibraryRepository libraryRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private BookAdditionalFileRepository bookAdditionalFileRepository;
    @Mock
    private FileAsBookProcessor fileAsBookProcessor;
    @Mock
    private BookRestorationService bookRestorationService;
    @Mock
    private BookDeletionService bookDeletionService;
    @Mock
    private LibraryFileHelper libraryFileHelper;
    @Mock
    private BookGroupingService bookGroupingService;
    @Mock
    private EntityManager entityManager;

    private LibraryProcessingService libraryProcessingService;

    @BeforeEach
    void setUp() {
        libraryProcessingService = new LibraryProcessingService(
                libraryRepository,
                notificationService,
                bookAdditionalFileRepository,
                fileAsBookProcessor,
                bookRestorationService,
                bookDeletionService,
                libraryFileHelper,
                bookGroupingService,
                entityManager
        );
    }

    @Test
    void processLibrary_shouldOnlyProcessNewFiles() throws IOException {
        long libraryId = 1L;
        LibraryEntity libraryEntity = new LibraryEntity();
        libraryEntity.setId(libraryId);
        libraryEntity.setName("Test Library");

        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(10L);
        pathEntity.setPath("/library");

        BookEntity existingBook = new BookEntity();
        existingBook.setLibraryPath(pathEntity);
        BookFileEntity existingBookFile = new BookFileEntity();
        existingBookFile.setBook(existingBook);
        existingBook.setBookFiles(List.of(existingBookFile));
        existingBook.getPrimaryBookFile().setFileSubPath("");
        existingBook.getPrimaryBookFile().setFileName("book1.epub");
        libraryEntity.setBookEntities(List.of(existingBook));

        when(libraryRepository.findById(libraryId)).thenReturn(Optional.of(libraryEntity));

        LibraryFile existingFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(pathEntity)
                .fileSubPath("")
                .fileName("book1.epub")
                .build();

        LibraryFile newFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(pathEntity)
                .fileSubPath("")
                .fileName("book2.epub")
                .build();

        when(libraryFileHelper.getLibraryFiles(libraryEntity)).thenReturn(List.of(existingFile, newFile));
        when(bookAdditionalFileRepository.findByLibraryId(libraryId)).thenReturn(Collections.emptyList());

        // Mock grouping service to pass through the files
        when(bookGroupingService.groupForInitialScan(anyList(), eq(libraryEntity)))
                .thenAnswer(invocation -> {
                    List<LibraryFile> files = invocation.getArgument(0);
                    Map<String, List<LibraryFile>> result = new LinkedHashMap<>();
                    for (LibraryFile f : files) {
                        result.put(f.getFileName(), List.of(f));
                    }
                    return result;
                });

        libraryProcessingService.processLibrary(libraryId);

        ArgumentCaptor<List<LibraryFile>> captor = ArgumentCaptor.forClass(List.class);
        verify(bookGroupingService).groupForInitialScan(captor.capture(), eq(libraryEntity));

        List<LibraryFile> processedFiles = captor.getValue();

        assertThat(processedFiles).hasSize(1);
        assertThat(processedFiles.getFirst().getFileName()).isEqualTo("book2.epub");
    }

    @Test
    void processLibrary_noNewFiles_shouldProcessNothing() throws IOException {
        long libraryId = 1L;
        LibraryEntity libraryEntity = new LibraryEntity();
        libraryEntity.setId(libraryId);
        libraryEntity.setName("Test Library");

        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(10L);
        pathEntity.setPath("/library");

        BookEntity existingBook = new BookEntity();
        existingBook.setLibraryPath(pathEntity);
        BookFileEntity existingBookFile = new BookFileEntity();
        existingBookFile.setBook(existingBook);
        existingBook.setBookFiles(List.of(existingBookFile));
        existingBook.getPrimaryBookFile().setFileSubPath("");
        existingBook.getPrimaryBookFile().setFileName("book1.epub");
        libraryEntity.setBookEntities(List.of(existingBook));

        when(libraryRepository.findById(libraryId)).thenReturn(Optional.of(libraryEntity));

        LibraryFile existingFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(pathEntity)
                .fileSubPath("")
                .fileName("book1.epub")
                .build();

        when(libraryFileHelper.getLibraryFiles(libraryEntity)).thenReturn(List.of(existingFile));
        when(bookAdditionalFileRepository.findByLibraryId(libraryId)).thenReturn(Collections.emptyList());
        when(bookGroupingService.groupForInitialScan(anyList(), eq(libraryEntity))).thenReturn(Collections.emptyMap());

        libraryProcessingService.processLibrary(libraryId);

        ArgumentCaptor<List<LibraryFile>> captor = ArgumentCaptor.forClass(List.class);
        verify(bookGroupingService).groupForInitialScan(captor.capture(), eq(libraryEntity));

        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    void processLibrary_allNewFiles_shouldProcessAll() throws IOException {
        long libraryId = 1L;
        LibraryEntity libraryEntity = new LibraryEntity();
        libraryEntity.setId(libraryId);
        libraryEntity.setName("Test Library");
        libraryEntity.setBookEntities(Collections.emptyList());

        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(10L);
        pathEntity.setPath("/library");

        when(libraryRepository.findById(libraryId)).thenReturn(Optional.of(libraryEntity));

        LibraryFile newFile1 = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(pathEntity)
                .fileSubPath("")
                .fileName("book1.epub")
                .build();
        LibraryFile newFile2 = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(pathEntity)
                .fileSubPath("")
                .fileName("book2.epub")
                .build();

        when(libraryFileHelper.getLibraryFiles(libraryEntity)).thenReturn(List.of(newFile1, newFile2));
        when(bookAdditionalFileRepository.findByLibraryId(libraryId)).thenReturn(Collections.emptyList());
        when(bookGroupingService.groupForInitialScan(anyList(), eq(libraryEntity)))
                .thenAnswer(invocation -> {
                    List<LibraryFile> files = invocation.getArgument(0);
                    Map<String, List<LibraryFile>> result = new LinkedHashMap<>();
                    for (LibraryFile f : files) {
                        result.put(f.getFileName(), List.of(f));
                    }
                    return result;
                });

        libraryProcessingService.processLibrary(libraryId);

        ArgumentCaptor<List<LibraryFile>> captor = ArgumentCaptor.forClass(List.class);
        verify(bookGroupingService).groupForInitialScan(captor.capture(), eq(libraryEntity));

        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    void processLibrary_newFileInSubdirectory_shouldProcess() throws IOException {
        long libraryId = 1L;
        LibraryEntity libraryEntity = new LibraryEntity();
        libraryEntity.setId(libraryId);
        libraryEntity.setName("Test Library");
        libraryEntity.setBookEntities(Collections.emptyList());

        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(10L);
        pathEntity.setPath("/library");

        when(libraryRepository.findById(libraryId)).thenReturn(Optional.of(libraryEntity));

        LibraryFile newFileInSub = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(pathEntity)
                .fileSubPath("fantasy")
                .fileName("book1.epub")
                .build();

        when(libraryFileHelper.getLibraryFiles(libraryEntity)).thenReturn(List.of(newFileInSub));
        when(bookAdditionalFileRepository.findByLibraryId(libraryId)).thenReturn(Collections.emptyList());
        when(bookGroupingService.groupForInitialScan(anyList(), eq(libraryEntity)))
                .thenAnswer(invocation -> {
                    List<LibraryFile> files = invocation.getArgument(0);
                    Map<String, List<LibraryFile>> result = new LinkedHashMap<>();
                    for (LibraryFile f : files) {
                        result.put(f.getFileName(), List.of(f));
                    }
                    return result;
                });

        libraryProcessingService.processLibrary(libraryId);

        ArgumentCaptor<List<LibraryFile>> captor = ArgumentCaptor.forClass(List.class);
        verify(bookGroupingService).groupForInitialScan(captor.capture(), eq(libraryEntity));

        List<LibraryFile> processedFiles = captor.getValue();
        assertThat(processedFiles).hasSize(1);
        assertThat(processedFiles.getFirst().getFileName()).isEqualTo("book1.epub");
        assertThat(processedFiles.getFirst().getFileSubPath()).isEqualTo("fantasy");
    }

    @Test
    void processLibrary_additionalFile_shouldNotProcess() throws IOException {
        long libraryId = 1L;
        LibraryEntity libraryEntity = new LibraryEntity();
        libraryEntity.setId(libraryId);
        libraryEntity.setName("Test Library");
        libraryEntity.setBookEntities(Collections.emptyList());

        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(10L);
        pathEntity.setPath("/library");

        when(libraryRepository.findById(libraryId)).thenReturn(Optional.of(libraryEntity));

        LibraryFile additionalFileAsLibraryFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(pathEntity)
                .fileSubPath("")
                .fileName("extra.pdf")
                .build();

        BookEntity parentBook = new BookEntity();
        parentBook.setLibraryPath(pathEntity);

        BookFileEntity additionalFileEntity = new BookFileEntity();
        additionalFileEntity.setBook(parentBook);
        additionalFileEntity.setFileSubPath("");
        additionalFileEntity.setFileName("extra.pdf");

        when(libraryFileHelper.getLibraryFiles(libraryEntity)).thenReturn(List.of(additionalFileAsLibraryFile));
        when(bookAdditionalFileRepository.findByLibraryId(libraryId)).thenReturn(List.of(additionalFileEntity));
        when(bookGroupingService.groupForInitialScan(anyList(), eq(libraryEntity))).thenReturn(Collections.emptyMap());

        libraryProcessingService.processLibrary(libraryId);

        ArgumentCaptor<List<LibraryFile>> captor = ArgumentCaptor.forClass(List.class);
        verify(bookGroupingService).groupForInitialScan(captor.capture(), eq(libraryEntity));

        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    void rescanLibrary_shouldDeleteRemovedBookFormatAdditionalFiles(@TempDir Path tempDir) throws IOException {
        long libraryId = 1L;
        Path accessiblePath = tempDir.resolve("library");
        Files.createDirectory(accessiblePath);

        LibraryEntity libraryEntity = new LibraryEntity();
        libraryEntity.setId(libraryId);
        libraryEntity.setName("Test Library");

        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(10L);
        pathEntity.setPath(accessiblePath.toString());
        libraryEntity.setLibraryPaths(List.of(pathEntity));

        BookEntity book = new BookEntity();
        book.setId(11L);
        book.setLibrary(libraryEntity);
        book.setLibraryPath(pathEntity);

        BookFileEntity epub = new BookFileEntity();
        epub.setId(1L);
        epub.setBook(book);
        epub.setFileSubPath("author/title");
        epub.setFileName("book.epub");
        epub.setBookFormat(true);

        BookFileEntity pdf = new BookFileEntity();
        pdf.setId(2L);
        pdf.setBook(book);
        pdf.setFileSubPath("author/title");
        pdf.setFileName("book.pdf");
        pdf.setBookFormat(true);

        BookFileEntity image = new BookFileEntity();
        image.setId(3L);
        image.setBook(book);
        image.setFileSubPath("author/title");
        image.setFileName("image.png");
        image.setBookFormat(false);

        book.setBookFiles(List.of(epub, pdf, image));
        libraryEntity.setBookEntities(List.of(book));

        when(libraryRepository.findByIdWithBooks(libraryId)).thenReturn(Optional.of(libraryEntity));

        // Only epub exists on disk, pdf was removed
        LibraryFile epubOnDisk = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(pathEntity)
                .fileSubPath("author/title")
                .fileName("book.epub")
                .build();

        when(libraryFileHelper.getAllLibraryFiles(libraryEntity)).thenReturn(List.of(epubOnDisk));
        when(libraryFileHelper.filterByAllowedFormats(anyList(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(bookAdditionalFileRepository.findByLibraryId(libraryId)).thenReturn(List.of(epub, pdf, image));
        when(bookGroupingService.groupForRescan(anyList(), eq(libraryEntity)))
                .thenReturn(new BookGroupingService.GroupingResult(Collections.emptyMap(), Collections.emptyMap()));

        libraryProcessingService.rescanLibrary(RescanLibraryContext.builder().libraryId(libraryId).build());

        // pdf (book format) should be deleted; image (non-book format) should NOT be deleted
        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(bookDeletionService).deleteRemovedAdditionalFiles(captor.capture());
        assertThat(captor.getValue()).containsExactly(2L);
    }

    @Test
    void rescanLibrary_shouldAbortWhenPathNotAccessible(@TempDir Path tempDir) {
        long libraryId = 1L;
        Path nonExistentPath = tempDir.resolve("non_existent_path");

        LibraryEntity libraryEntity = new LibraryEntity();
        libraryEntity.setId(libraryId);
        libraryEntity.setName("Test Library");

        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(10L);
        pathEntity.setPath(nonExistentPath.toString());
        libraryEntity.setLibraryPaths(List.of(pathEntity));
        libraryEntity.setBookEntities(Collections.emptyList());

        when(libraryRepository.findByIdWithBooks(libraryId)).thenReturn(Optional.of(libraryEntity));

        RescanLibraryContext context = RescanLibraryContext.builder().libraryId(libraryId).build();

        assertThatThrownBy(() -> libraryProcessingService.rescanLibrary(context))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("not accessible");

        verify(bookDeletionService, never()).processDeletedLibraryFiles(any(), any());
        verify(bookDeletionService, never()).deleteRemovedAdditionalFiles(any());
    }

    @Test
    void rescanLibrary_shouldAbortWhenLibraryHasBooksButScanReturnsEmpty(@TempDir Path tempDir) throws IOException {
        long libraryId = 1L;
        Path accessiblePath = tempDir.resolve("accessible");
        Files.createDirectory(accessiblePath);

        LibraryEntity libraryEntity = new LibraryEntity();
        libraryEntity.setId(libraryId);
        libraryEntity.setName("Test Library");

        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(10L);
        pathEntity.setPath(accessiblePath.toString());
        libraryEntity.setLibraryPaths(List.of(pathEntity));

        BookEntity existingBook = new BookEntity();
        existingBook.setId(1L);
        existingBook.setLibraryPath(pathEntity);
        BookFileEntity existingBookFile = new BookFileEntity();
        existingBookFile.setBook(existingBook);
        existingBook.setBookFiles(List.of(existingBookFile));
        existingBook.getPrimaryBookFile().setFileSubPath("");
        existingBook.getPrimaryBookFile().setFileName("book1.epub");
        libraryEntity.setBookEntities(List.of(existingBook));

        when(libraryRepository.findByIdWithBooks(libraryId)).thenReturn(Optional.of(libraryEntity));
        when(libraryFileHelper.getAllLibraryFiles(libraryEntity)).thenReturn(Collections.emptyList());

        RescanLibraryContext context = RescanLibraryContext.builder().libraryId(libraryId).build();

        assertThatThrownBy(() -> libraryProcessingService.rescanLibrary(context))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("not accessible");

        verify(bookDeletionService, never()).processDeletedLibraryFiles(any(), any());
        verify(bookDeletionService, never()).deleteRemovedAdditionalFiles(any());
    }

    @Test
    void rescanLibrary_shouldProceedWhenLibraryHasBooksAndScanFindsThem(@TempDir Path tempDir) throws IOException {
        long libraryId = 1L;
        Path accessiblePath = tempDir.resolve("accessible");
        Files.createDirectory(accessiblePath);

        LibraryEntity libraryEntity = new LibraryEntity();
        libraryEntity.setId(libraryId);
        libraryEntity.setName("Test Library");

        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(10L);
        pathEntity.setPath(accessiblePath.toString());
        libraryEntity.setLibraryPaths(List.of(pathEntity));

        BookEntity existingBook = new BookEntity();
        existingBook.setId(1L);
        existingBook.setLibraryPath(pathEntity);
        BookFileEntity existingBookFile = new BookFileEntity();
        existingBookFile.setBook(existingBook);
        existingBook.setBookFiles(List.of(existingBookFile));
        existingBook.getPrimaryBookFile().setFileSubPath("");
        existingBook.getPrimaryBookFile().setFileName("book1.epub");
        libraryEntity.setBookEntities(List.of(existingBook));

        LibraryFile fileOnDisk = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(pathEntity)
                .fileSubPath("")
                .fileName("book1.epub")
                .build();

        when(libraryRepository.findByIdWithBooks(libraryId)).thenReturn(Optional.of(libraryEntity));
        when(libraryFileHelper.getAllLibraryFiles(libraryEntity)).thenReturn(List.of(fileOnDisk));
        when(libraryFileHelper.filterByAllowedFormats(anyList(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(bookAdditionalFileRepository.findByLibraryId(libraryId)).thenReturn(Collections.emptyList());
        when(bookGroupingService.groupForRescan(anyList(), eq(libraryEntity)))
                .thenReturn(new BookGroupingService.GroupingResult(Collections.emptyMap(), Collections.emptyMap()));

        RescanLibraryContext context = RescanLibraryContext.builder().libraryId(libraryId).build();

        libraryProcessingService.rescanLibrary(context);

        verify(bookDeletionService, never()).processDeletedLibraryFiles(any(), any());
    }

    @Test
    void rescanLibrary_shouldProceedForEmptyLibraryWithNoFilesFound(@TempDir Path tempDir) throws IOException {
        long libraryId = 1L;
        Path accessiblePath = tempDir.resolve("accessible");
        Files.createDirectory(accessiblePath);

        LibraryEntity libraryEntity = new LibraryEntity();
        libraryEntity.setId(libraryId);
        libraryEntity.setName("Test Library");

        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(10L);
        pathEntity.setPath(accessiblePath.toString());
        libraryEntity.setLibraryPaths(List.of(pathEntity));
        libraryEntity.setBookEntities(Collections.emptyList());

        when(libraryRepository.findByIdWithBooks(libraryId)).thenReturn(Optional.of(libraryEntity));
        when(libraryFileHelper.getAllLibraryFiles(libraryEntity)).thenReturn(Collections.emptyList());
        when(libraryFileHelper.filterByAllowedFormats(anyList(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(bookAdditionalFileRepository.findByLibraryId(libraryId)).thenReturn(Collections.emptyList());
        when(bookGroupingService.groupForRescan(anyList(), eq(libraryEntity)))
                .thenReturn(new BookGroupingService.GroupingResult(Collections.emptyMap(), Collections.emptyMap()));

        RescanLibraryContext context = RescanLibraryContext.builder().libraryId(libraryId).build();

        libraryProcessingService.rescanLibrary(context);

        verify(bookDeletionService, never()).processDeletedLibraryFiles(any(), any());
    }

    @Test
    void rescanLibrary_shouldRefetchLibraryAfterEntityManagerClear(@TempDir Path tempDir) throws IOException {

        long libraryId = 1L;
        Path accessiblePath = tempDir.resolve("accessible");
        Files.createDirectory(accessiblePath);

        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(10L);
        pathEntity.setPath(accessiblePath.toString());

        BookEntity existingBook = new BookEntity();
        existingBook.setId(1L);
        existingBook.setLibraryPath(pathEntity);
        BookFileEntity existingBookFile = new BookFileEntity();
        existingBookFile.setBook(existingBook);
        existingBook.setBookFiles(new ArrayList<>(List.of(existingBookFile)));
        existingBook.getPrimaryBookFile().setFileSubPath("");
        existingBook.getPrimaryBookFile().setFileName("book (with parens).epub");

        // First call returns entity with one book, second call returns fresh entity with same book
        LibraryEntity libraryEntity = new LibraryEntity();
        libraryEntity.setId(libraryId);
        libraryEntity.setName("Test Library");
        libraryEntity.setLibraryPaths(new ArrayList<>(List.of(pathEntity)));
        libraryEntity.setBookEntities(new ArrayList<>(List.of(existingBook)));

        LibraryEntity freshLibraryEntity = new LibraryEntity();
        freshLibraryEntity.setId(libraryId);
        freshLibraryEntity.setName("Test Library");
        freshLibraryEntity.setLibraryPaths(new ArrayList<>(List.of(pathEntity)));
        freshLibraryEntity.setBookEntities(new ArrayList<>(List.of(existingBook)));

        LibraryFile fileOnDisk = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(pathEntity)
                .fileSubPath("")
                .fileName("book (with parens).epub")
                .build();

        when(libraryRepository.findByIdWithBooks(libraryId))
                .thenReturn(Optional.of(libraryEntity))
                .thenReturn(Optional.of(freshLibraryEntity)); // Second call returns fresh entity
        when(libraryFileHelper.getAllLibraryFiles(any(LibraryEntity.class))).thenReturn(List.of(fileOnDisk));
        when(libraryFileHelper.filterByAllowedFormats(anyList(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(bookAdditionalFileRepository.findByLibraryId(libraryId)).thenReturn(Collections.emptyList());
        when(bookGroupingService.groupForRescan(anyList(), any(LibraryEntity.class)))
                .thenReturn(new BookGroupingService.GroupingResult(Collections.emptyMap(), Collections.emptyMap()));

        RescanLibraryContext context = RescanLibraryContext.builder().libraryId(libraryId).build();

        libraryProcessingService.rescanLibrary(context);

        verify(libraryRepository, times(2)).findByIdWithBooks(libraryId);

        verify(bookGroupingService).groupForRescan(eq(Collections.emptyList()), any(LibraryEntity.class));
    }
}
