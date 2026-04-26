package org.grimmory.service.book;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
import org.booklore.mapper.BookMapper;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.response.DetachBookFileResponse;
import org.booklore.model.entity.*;
import org.booklore.model.enums.AuditAction;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.booklore.service.audit.AuditService;
import org.booklore.service.metadata.BookCoverService;
import org.booklore.service.progress.ReadingProgressService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookFileDetachmentServiceTest {

    @Mock private BookRepository bookRepository;
    @Mock private UserBookProgressRepository userBookProgressRepository;
    @Mock private AuthenticationService authenticationService;
    @Mock private ReadingProgressService readingProgressService;
    @Mock private BookMapper bookMapper;
    @Mock private BookService bookService;
    @Mock private AuditService auditService;
    @Mock private BookCoverService bookCoverService;

    @InjectMocks
    private BookFileDetachmentService service;

    private LibraryEntity library;
    private LibraryPathEntity libraryPath;

    @BeforeEach
    void setUp() {
        library = LibraryEntity.builder()
                .id(1L)
                .name("Test Library")
                .build();

        libraryPath = LibraryPathEntity.builder()
                .id(1L)
                .library(library)
                .path("/tmp/test-library")
                .build();

        library.setLibraryPaths(new ArrayList<>(List.of(libraryPath)));
    }

    private BookEntity createBook(Long id) {
        BookEntity book = BookEntity.builder()
                .id(id)
                .library(library)
                .libraryPath(libraryPath)
                .build();

        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .book(book)
                .title("Test Book " + id)
                .build();
        book.setMetadata(metadata);

        return book;
    }

    private BookFileEntity createBookFile(Long id, BookEntity book, boolean isBookFormat, BookFileType type) {
        BookFileEntity file = BookFileEntity.builder()
                .id(id)
                .book(book)
                .fileName("file-" + id + "." + type.name().toLowerCase())
                .fileSubPath("")
                .isBookFormat(isBookFormat)
                .bookType(type)
                .build();
        book.getBookFiles().add(file);
        return file;
    }

    private void setupMocksForGetUpdatedBook() {
        BookLoreUser user = new BookLoreUser();
        user.setId(1L);
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(userBookProgressRepository.findByUserIdAndBookId(anyLong(), anyLong()))
                .thenReturn(Optional.of(new UserBookProgressEntity()));
        when(readingProgressService.fetchUserFileProgress(anyLong(), anySet()))
                .thenReturn(new HashMap<>());
        when(bookMapper.toBook(any(BookEntity.class))).thenReturn(Book.builder().build());
    }

    @Test
    void detachAlternativeFormat_withCopyMetadata() {
        BookEntity book = createBook(1L);
        BookFileEntity primaryFile = createBookFile(10L, book, true, BookFileType.EPUB);
        BookFileEntity altFile = createBookFile(11L, book, true, BookFileType.PDF);

        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));
        when(bookRepository.saveAndFlush(any(BookEntity.class))).thenAnswer(inv -> {
            BookEntity saved = inv.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        setupMocksForGetUpdatedBook();
        when(bookRepository.findByIdWithBookFiles(2L)).thenReturn(Optional.of(createBook(2L)));

        DetachBookFileResponse response = service.detachBookFile(1L, 11L, true);

        assertThat(response).isNotNull();
        assertThat(book.getBookFiles()).hasSize(1);
        assertThat(book.getBookFiles().getFirst().getId()).isEqualTo(10L);
        verify(auditService).log(eq(AuditAction.BOOK_FILE_DETACHED), anyString(), eq(1L), anyString());
        verify(bookRepository).saveAndFlush(argThat(newBook -> {
            assertThat(newBook.getMetadata().getTitle()).isEqualTo("Test Book 1");
            return true;
        }));
    }

    @Test
    void detachAlternativeFormat_withoutCopyMetadata() {
        BookEntity book = createBook(1L);
        createBookFile(10L, book, true, BookFileType.EPUB);
        createBookFile(11L, book, true, BookFileType.PDF);

        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));
        when(bookRepository.saveAndFlush(any(BookEntity.class))).thenAnswer(inv -> {
            BookEntity saved = inv.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        setupMocksForGetUpdatedBook();
        when(bookRepository.findByIdWithBookFiles(2L)).thenReturn(Optional.of(createBook(2L)));

        service.detachBookFile(1L, 11L, false);

        verify(bookRepository).saveAndFlush(argThat(newBook -> {
            assertThat(newBook.getMetadata().getTitle()).isEqualTo("file-11");
            return true;
        }));
    }

    @Test
    void detachSupplementaryFile() {
        BookEntity book = createBook(1L);
        createBookFile(10L, book, true, BookFileType.EPUB);
        BookFileEntity suppFile = createBookFile(11L, book, false, BookFileType.PDF);
        suppFile.setFileName("notes.txt");

        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));
        when(bookRepository.saveAndFlush(any(BookEntity.class))).thenAnswer(inv -> {
            BookEntity saved = inv.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        setupMocksForGetUpdatedBook();
        when(bookRepository.findByIdWithBookFiles(2L)).thenReturn(Optional.of(createBook(2L)));

        service.detachBookFile(1L, 11L, false);

        assertThat(book.getBookFiles()).hasSize(1);
        assertThat(suppFile.getBook()).isNotSameAs(book);
        assertThat(suppFile.getFileName()).isEqualTo("notes.txt");
        verify(bookRepository).saveAndFlush(any(BookEntity.class));
    }

    @Test
    void detachPrimaryFile_alternativeGetsPromoted() {
        BookEntity book = createBook(1L);
        BookFileEntity primaryFile = createBookFile(10L, book, true, BookFileType.EPUB);
        createBookFile(11L, book, true, BookFileType.PDF);

        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));
        when(bookRepository.saveAndFlush(any(BookEntity.class))).thenAnswer(inv -> {
            BookEntity saved = inv.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        setupMocksForGetUpdatedBook();
        when(bookRepository.findByIdWithBookFiles(2L)).thenReturn(Optional.of(createBook(2L)));

        service.detachBookFile(1L, 10L, false);

        assertThat(book.getBookFiles()).hasSize(1);
        assertThat(book.getBookFiles().getFirst().getId()).isEqualTo(11L);
        assertThat(book.getBookFiles().getFirst().isBookFormat()).isTrue();
    }

    @Test
    void rejectDetach_onlyOneFile() {
        BookEntity book = createBook(1L);
        BookFileEntity file = createBookFile(10L, book, true, BookFileType.EPUB);

        assertThat(book.getBookFiles()).hasSize(1);
        assertThat(file.isBookFormat()).isTrue();

        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));

        assertThatThrownBy(() -> service.detachBookFile(1L, 10L, false))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("Cannot detach the only file");
    }

    @Test
    void rejectDetach_fileNotOnBook() {
        BookEntity book = createBook(1L);
        createBookFile(10L, book, true, BookFileType.EPUB);
        createBookFile(11L, book, true, BookFileType.PDF);

        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));

        assertThatThrownBy(() -> service.detachBookFile(1L, 99L, false))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("File not found on this book");
    }

    @Test
    void rejectDetach_bookNotFound() {
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detachBookFile(1L, 10L, false))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("Book not found");
    }

    @Test
    void rejectDetach_onlyBookFormatWithSupplementary() {
        BookEntity book = createBook(1L);
        createBookFile(10L, book, true, BookFileType.EPUB);
        createBookFile(11L, book, false, BookFileType.PDF);

        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));

        assertThatThrownBy(() -> service.detachBookFile(1L, 10L, false))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("only book format file");
    }

    @Test
    void detachFile_resolvesCorrectLibraryPath() {
        LibraryPathEntity libraryPath2 = LibraryPathEntity.builder()
                .id(2L)
                .library(library)
                .path("/tmp/test-library/folder-2")
                .build();
        library.getLibraryPaths().add(libraryPath2);

        BookEntity book = createBook(1L);
        BookFileEntity primaryFile = createBookFile(10L, book, true, BookFileType.EPUB);
        BookFileEntity altFile = createBookFile(11L, book, true, BookFileType.PDF);
        altFile.setFileSubPath("folder-2");

        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));
        when(bookRepository.saveAndFlush(any(BookEntity.class))).thenAnswer(inv -> {
            BookEntity saved = inv.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        setupMocksForGetUpdatedBook();
        when(bookRepository.findByIdWithBookFiles(2L)).thenReturn(Optional.of(createBook(2L)));

        service.detachBookFile(1L, 11L, false);

        verify(bookRepository).saveAndFlush(argThat(newBook -> {
            assertThat(newBook.getLibraryPath().getId()).isEqualTo(2L);
            return true;
        }));
        assertThat(altFile.getFileSubPath()).isEmpty();
    }
}
