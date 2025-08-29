package com.adityachandel.booklore.service.library;

import com.adityachandel.booklore.model.entity.BookAdditionalFileEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.repository.BookAdditionalFileRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.FileFingerprint;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessor;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessorRegistry;
import com.adityachandel.booklore.util.FileUtils;
import com.adityachandel.booklore.util.builder.LibraryTestBuilder;
import static com.adityachandel.booklore.util.builder.LibraryTestBuilderAssert.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FolderAsBookFileProcessorExampleTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private BookAdditionalFileRepository bookAdditionalFileRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private BookFileProcessorRegistry bookFileProcessorRegistry;

    @Mock
    private BookFileProcessor mockBookFileProcessor;

    @InjectMocks
    private FolderAsBookFileProcessor processor;

    @Captor
    private ArgumentCaptor<BookAdditionalFileEntity> additionalFileCaptor;

    private MockedStatic<FileUtils> fileUtilsMock;
    private  MockedStatic<FileFingerprint> fileFingerprintMock;
    private LibraryTestBuilder libraryTestBuilder;

    @BeforeEach
    void setUp() {
        fileUtilsMock = mockStatic(FileUtils.class);
        fileFingerprintMock = mockStatic(FileFingerprint.class);
        libraryTestBuilder = new LibraryTestBuilder(fileUtilsMock, fileFingerprintMock, bookFileProcessorRegistry, mockBookFileProcessor, bookRepository, bookAdditionalFileRepository);
    }

    @AfterEach
    void tearDown() {
        fileUtilsMock.close();
        fileFingerprintMock.close();
    }

    @Test
    void processLibraryFiles_shouldCreateNewBookFromDirectory() {
        // Given
        libraryTestBuilder
                .addDefaultLibrary()
                .addLibraryFile("/101/Accounting", "Accounting 101.pdf")
                .addLibraryFile("/101/Anatomy", "Anatomy 101.pdf");

        // When
        processor.processLibraryFiles(libraryTestBuilder.getLibraryFiles(), libraryTestBuilder.getLibraryEntity());

        // Then
        assertThat(libraryTestBuilder)
                .hasBooks("Accounting 101", "Anatomy 101")
                .hasNoAdditionalFiles();
    }

    @Test
    void processLibraryFiles_shouldCreateNewBookFromDirectoryWithAdditionalBookFormats() {
        // Given
        libraryTestBuilder
                .addDefaultLibrary()
                .addLibraryFile("/101/Accounting", "Accounting 101.pdf")
                .addLibraryFile("/101/Accounting", "Accounting 101.epub")
                .addLibraryFile("/101/Anatomy", "Anatomy 101.pdf");

        // When
        processor.processLibraryFiles(libraryTestBuilder.getLibraryFiles(), libraryTestBuilder.getLibraryEntity());

        // Then
        assertThat(libraryTestBuilder)
                .hasBooks("Accounting 101", "Anatomy 101")
                .bookHasAdditionalFormats("Accounting 101", BookFileType.PDF)
                .bookHasNoSupplementaryFiles("Accounting 101")
                .bookHasNoAdditionalFiles("Anatomy 101");
    }

    @Test
    void processLibraryFiles_shouldCreateNewBookFromDirectoryWithAdditionalBookFormatsAndSupplementaryFiles() {
        // Given
        libraryTestBuilder
                .addDefaultLibrary()
                .addLibraryFile("/101/Accounting", "Accounting 101.pdf")
                .addLibraryFile("/101/Accounting", "Accounting 101.epub")
                .addLibraryFile("/101/Accounting", "Accounting 101.zip")
                .addLibraryFile("/101/Anatomy", "Anatomy 101.pdf");

        // When
        processor.processLibraryFiles(libraryTestBuilder.getLibraryFiles(), libraryTestBuilder.getLibraryEntity());

        // Then
        assertThat(libraryTestBuilder)
                .hasBooks("Accounting 101", "Anatomy 101")
                .bookHasAdditionalFormats("Accounting 101", BookFileType.PDF)
                .bookHasSupplementaryFiles("Accounting 101", "Accounting 101.zip")
                .bookHasNoAdditionalFiles("Anatomy 101");
    }

    @Test
    void processLibraryFiles_shouldCreateNewBookFromDirectoryWithSameSupplementaryFilesByHash() {
        // Given
        String supplementaryFileHash = "hash-accounting-101";
        libraryTestBuilder
                .addDefaultLibrary()
                .addLibraryFile("/101/Accounting", "Accounting 101.pdf")
                .addLibraryFile(
                        "/101/Accounting",
                        "Accounting 101.zip",
                        supplementaryFileHash)
                .addLibraryFile("/101/Accounting 2nd Edition", "Accounting 101 2nd Edition.pdf")
                .addLibraryFile(
                        "/101/Accounting 2nd Edition",
                        "Accounting 101 2nd Edition.zip",
                        supplementaryFileHash);

        // When
        processor.processLibraryFiles(libraryTestBuilder.getLibraryFiles(), libraryTestBuilder.getLibraryEntity());

        // Then
        assertThat(libraryTestBuilder)
                .hasBooks("Accounting 101", "Accounting 101 2nd Edition")
                .bookHasSupplementaryFiles("Accounting 101", "Accounting 101.zip")
                .bookHasSupplementaryFiles("Accounting 101 2nd Edition", "Accounting 101 2nd Edition.zip");
    }

    @Test
    void processLibraryFiles_shouldIgnoreAdditionalFormatWithTheSameHashUsedInOtherBook() {
        // Given
        String additionalFormatHash = "hash-accounting-101";
        libraryTestBuilder
                .addDefaultLibrary()
                .addLibraryFile("/101/Accounting", "Accounting 101.epub")
                .addLibraryFile(
                        "/101/Accounting",
                        "Accounting 101.pdf",
                        additionalFormatHash)
                .addLibraryFile("/101/Accounting 2nd Edition", "Accounting 101 2nd Edition.epub")
                .addLibraryFile(
                        "/101/Accounting 2nd Edition",
                        "Accounting 101 2nd Edition.pdf",
                        additionalFormatHash);

        // When
        processor.processLibraryFiles(libraryTestBuilder.getLibraryFiles(), libraryTestBuilder.getLibraryEntity());

        // Then
        assertThat(libraryTestBuilder)
                .hasBooks("Accounting 101", "Accounting 101 2nd Edition")
                .bookHasAdditionalFormats("Accounting 101", BookFileType.PDF)
                .bookHasNoAdditionalFiles("Accounting 101 2nd Edition");
    }

    @Test
    void processLibraryFiles_shouldAddAdditionalFormatsToExistingBook() {
        // Given
        libraryTestBuilder
                .addDefaultLibrary()
                .addBook("/101/Accounting", "Accounting 101.pdf")
                .addLibraryFile("/101/Accounting", "Accounting 101.epub");

        // When
        processor.processLibraryFiles(libraryTestBuilder.getLibraryFiles(), libraryTestBuilder.getLibraryEntity());

        // Then
        assertThat(libraryTestBuilder)
                .hasBooks("Accounting 101")
                .bookHasAdditionalFormats("Accounting 101", BookFileType.EPUB)
                .bookHasNoSupplementaryFiles("Accounting 101");
    }

    @Test
    void processLibraryFiles_shouldAddSupplementaryFilesToExistingBook() {
        // Given
        libraryTestBuilder
                .addDefaultLibrary()
                .addBook("/101/Accounting", "Accounting 101.pdf")
                .addLibraryFile("/101/Accounting", "sources.zip");

        // When
        processor.processLibraryFiles(libraryTestBuilder.getLibraryFiles(), libraryTestBuilder.getLibraryEntity());

        // Then
        assertThat(libraryTestBuilder)
                .hasBooks("Accounting 101")
                .bookHasNoAdditionalFormats("Accounting 101")
                .bookHasSupplementaryFiles("Accounting 101", "sources.zip");
    }

    @Test
    void processLibraryFiles_shouldAddAdditionalFilesToExistingBook() {
        // Given
        libraryTestBuilder
                .addDefaultLibrary()
                .addBook("/101/Accounting", "Accounting 101.pdf")
                .addLibraryFile("/101/Accounting", "Accounting 101.epub")
                .addLibraryFile("/101/Accounting", "sources.zip");

        // When
        processor.processLibraryFiles(libraryTestBuilder.getLibraryFiles(), libraryTestBuilder.getLibraryEntity());

        // Then
        assertThat(libraryTestBuilder)
                .hasBooks("Accounting 101")
                .bookHasAdditionalFormats("Accounting 101", BookFileType.EPUB)
                .bookHasSupplementaryFiles("Accounting 101", "sources.zip");
    }

    @Test
    void processLibraryFiles_shouldProcessDeepSubfolders() {
        var javaSourcesSameHash = "hash-java-sources";

        libraryTestBuilder
                .addDefaultLibrary()
                // Basic books
                .addLibraryFile("/Basic/101/Accounting", "Accounting 101.epub")
                .addLibraryFile("/Basic/101/Accounting", "Accounting 101.pdf")
                .addLibraryFile("/Basic/101/Anatomy", "Anatomy 101.pdf")
                .addLibraryFile("/Basic/How-To/Repair", "How to Repair.epub")
                .addLibraryFile("/Basic/How-To/Repair", "How to Repair.pdf")
                // Software Engineering books
                .addLibraryFile("/Software Engineering/Java/Design Patterns", "Design Patterns.pdf")
                .addLibraryFile("/Software Engineering/Java/Design Patterns", "Design Patterns.epub")
                .addLibraryFile("/Software Engineering/Java/Design Patterns", "Design Patterns.zip", javaSourcesSameHash)
                .addLibraryFile("/Software Engineering/Java/Effective Java", "Effective Java.pdf")
                .addLibraryFile("/Software Engineering/Java/Effective Java", "Effective Java.epub")
                .addLibraryFile("/Software Engineering/Java/Effective Java", "Effective Java.zip", javaSourcesSameHash)
                .addLibraryFile("/Software Engineering/Python/AI/Machine Learning/Pytorch", "PyTorch for Machine Learning.pdf")
                .addLibraryFile("/Software Engineering/Python/AI/Machine Learning/Pytorch", "PyTorch for Machine Learning.epub")
                .addLibraryFile("/Software Engineering/Python/AI/Machine Learning/Pytorch", "sources.zip")
                .addLibraryFile("/Software Engineering/Python/AI/Machine Learning/TensorFlow", "TensorFlow for Machine Learning.pdf")
                .addLibraryFile("/Software Engineering/Python/AI/Machine Learning/TensorFlow", "TensorFlow for Machine Learning.epub")
                .addLibraryFile("/Software Engineering/Python/AI/Machine Learning/TensorFlow", "sources.zip")
                .addLibraryFile("/Software Engineering/Python/Flask/Flask Web Development", "Flask Web Development.pdf")
                .addLibraryFile("/Software Engineering/Python/Flask/Flask Web Development", "Flask Web Development.epub")
                // Comics Marvel
                .addLibraryFile("/Comics/Marvel/Batman/Volume 1", "Batman v1.cbr")
                .addLibraryFile("/Comics/Marvel/Batman/Volume 2", "Batman v2.cbr")
                .addLibraryFile("/Comics/Marvel/Spiderman/Volume 1", "Spiderman v1.cbz")
                .addLibraryFile("/Comics/Marvel/Spiderman/Volume 2", "Spiderman v2.cbz")
                // Comics DC
                .addLibraryFile("/Comics/DC/Superman/Volume 1", "Superman v1.cbr")
                .addLibraryFile("/Comics/DC/Superman/Volume 1", "Poster.jpg")
                .addLibraryFile("/Comics/DC/Superman/Volume 2", "Superman v2.cbr")
                // Manga
                .addLibraryFile("/Manga/One Piece/Volume 1", "One Piece v1.cbz")
                .addLibraryFile("/Manga/One Piece/Volume 2", "One Piece v2.cbz")
                .addLibraryFile("/Manga/Naruto/Volume 1", "Naruto v1.cbr")
                .addLibraryFile("/Manga/Naruto/Volume 2", "Naruto v2.cbr");

        // When
        processor.processLibraryFiles(libraryTestBuilder.getLibraryFiles(), libraryTestBuilder.getLibraryEntity());

        // Then
        assertThat(libraryTestBuilder)
                .hasBooks(
                        "Accounting 101", "Anatomy 101", "How to Repair",
                        "Design Patterns", "Effective Java",
                        "PyTorch for Machine Learning", "TensorFlow for Machine Learning",
                        "Flask Web Development",
                        "Batman v1", "Batman v2",
                        "Spiderman v1", "Spiderman v2",
                        "Superman v1", "Superman v2",
                        "One Piece v1", "One Piece v2",
                        "Naruto v1", "Naruto v2")
                // Basic books
                .bookHasAdditionalFormats("Accounting 101", BookFileType.PDF)
                .bookHasNoSupplementaryFiles("Anatomy 101")
                .bookHasAdditionalFormats("How to Repair", BookFileType.PDF)
                // Software Engineering books
                .bookHasAdditionalFormats("Design Patterns", BookFileType.PDF)
                .bookHasSupplementaryFiles("Design Patterns", "Design Patterns.zip")
                .bookHasAdditionalFormats("Effective Java", BookFileType.PDF)
                .bookHasSupplementaryFiles("Effective Java", "Effective Java.zip")
                .bookHasAdditionalFormats("PyTorch for Machine Learning", BookFileType.PDF)
                .bookHasSupplementaryFiles("PyTorch for Machine Learning", "sources.zip")
                .bookHasAdditionalFormats("TensorFlow for Machine Learning", BookFileType.PDF)
                .bookHasSupplementaryFiles("TensorFlow for Machine Learning", "sources.zip")
                .bookHasAdditionalFormats("Flask Web Development", BookFileType.PDF)
                // Comics Marvel
                .bookHasNoAdditionalFiles("Batman v1")
                .bookHasNoAdditionalFiles("Batman v2")
                .bookHasNoAdditionalFiles("Spiderman v1")
                .bookHasNoAdditionalFiles("Spiderman v2")
                // Comics DC
                .bookHasSupplementaryFiles("Superman v1", "Poster.jpg")
                .bookHasNoAdditionalFiles("Superman v2")
                // Manga
                .bookHasNoAdditionalFiles("One Piece v1")
                .bookHasNoAdditionalFiles("One Piece v2")
                .bookHasNoAdditionalFiles("Naruto v1")
                .bookHasNoAdditionalFiles("Naruto v2");
    }
}
