package org.booklore.service.book;

import org.booklore.mapper.BookMapper;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.request.DuplicateDetectionRequest;
import org.booklore.model.dto.response.DuplicateGroup;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DuplicateDetectionServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private BookMapper bookMapper;

    @InjectMocks
    private DuplicateDetectionService service;

    private static final Long LIBRARY_ID = 1L;
    private long nextBookId = 1L;

    private LibraryEntity library;

    @BeforeEach
    void setUp() {
        library = LibraryEntity.builder().id(LIBRARY_ID).name("Test Library").build();
        nextBookId = 1L;
        lenient().when(bookMapper.toBook(any(BookEntity.class))).thenAnswer(inv -> {
            BookEntity entity = inv.getArgument(0);
            return Book.builder().id(entity.getId()).build();
        });
    }

    // ── Helper methods ──────────────────────────────────────────

    private BookEntity createBook(BookFileType fileType, String title, String authorName) {
        long id = nextBookId++;
        BookFileEntity file = BookFileEntity.builder()
                .id(id * 100)
                .fileName("book" + id + "." + fileType.getExtensions().iterator().next())
                .fileSubPath("books")
                .isBookFormat(true)
                .bookType(fileType)
                .build();

        List<AuthorEntity> authors = new ArrayList<>();
        if (authorName != null) {
            authors.add(AuthorEntity.builder().id(id * 10).name(authorName).build());
        }

        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .bookId(id)
                .title(title)
                .authors(authors)
                .build();

        BookEntity book = BookEntity.builder()
                .id(id)
                .library(library)
                .metadata(metadata)
                .bookFiles(new ArrayList<>(List.of(file)))
                .build();

        file.setBook(book);
        return book;
    }

    private BookEntity createBookWithIsbn(BookFileType fileType, String title, String authorName,
                                           String isbn13, String isbn10) {
        BookEntity book = createBook(fileType, title, authorName);
        book.getMetadata().setIsbn13(isbn13);
        book.getMetadata().setIsbn10(isbn10);
        return book;
    }

    private DuplicateDetectionRequest allSignals() {
        return new DuplicateDetectionRequest(LIBRARY_ID, true, true, true, true, true);
    }

    private DuplicateDetectionRequest onlyIsbn() {
        return new DuplicateDetectionRequest(LIBRARY_ID, true, false, false, false, false);
    }

    private DuplicateDetectionRequest onlyExternalId() {
        return new DuplicateDetectionRequest(LIBRARY_ID, false, true, false, false, false);
    }

    private DuplicateDetectionRequest onlyTitleAuthor() {
        return new DuplicateDetectionRequest(LIBRARY_ID, false, false, true, false, false);
    }

    private DuplicateDetectionRequest onlyDirectory() {
        return new DuplicateDetectionRequest(LIBRARY_ID, false, false, false, true, false);
    }

    private DuplicateDetectionRequest onlyFilename() {
        return new DuplicateDetectionRequest(LIBRARY_ID, false, false, false, false, true);
    }

    private void stubBooks(BookEntity... books) {
        when(bookRepository.findAllForDuplicateDetection(LIBRARY_ID)).thenReturn(List.of(books));
    }

    // ── General tests ───────────────────────────────────────────

    @Nested
    class General {

        @Test
        void returnsEmptyWhenLessThanTwoBooks() {
            BookEntity single = createBook(BookFileType.EPUB, "Solo Book", "Author");
            stubBooks(single);

            List<DuplicateGroup> result = service.findDuplicates(allSignals());

            assertThat(result).isEmpty();
        }

        @Test
        void returnsEmptyWhenNoBooksInLibrary() {
            when(bookRepository.findAllForDuplicateDetection(LIBRARY_ID)).thenReturn(List.of());

            List<DuplicateGroup> result = service.findDuplicates(allSignals());

            assertThat(result).isEmpty();
        }

        @Test
        void returnsEmptyWhenNoSignalsEnabled() {
            BookEntity book1 = createBookWithIsbn(BookFileType.EPUB, "Same", "Author", "9781234567890", null);
            BookEntity book2 = createBookWithIsbn(BookFileType.MOBI, "Same", "Author", "9781234567890", null);
            stubBooks(book1, book2);

            DuplicateDetectionRequest noSignals = new DuplicateDetectionRequest(LIBRARY_ID, false, false, false, false, false);
            List<DuplicateGroup> result = service.findDuplicates(noSignals);

            assertThat(result).isEmpty();
        }

        @Test
        void returnsEmptyWhenNoDuplicatesExist() {
            BookEntity book1 = createBook(BookFileType.EPUB, "Book A", "Author X");
            BookEntity book2 = createBook(BookFileType.MOBI, "Book B", "Author Y");
            stubBooks(book1, book2);

            List<DuplicateGroup> result = service.findDuplicates(allSignals());

            assertThat(result).isEmpty();
        }
    }

    // ── ISBN matching ───────────────────────────────────────────

    @Nested
    class IsbnMatching {

        @Test
        void groupsBooksWithSameIsbn13() {
            BookEntity book1 = createBookWithIsbn(BookFileType.EPUB, "Book", "Author", "9781234567890", null);
            BookEntity book2 = createBookWithIsbn(BookFileType.MOBI, "Book", "Author", "9781234567890", null);
            stubBooks(book1, book2);

            List<DuplicateGroup> result = service.findDuplicates(onlyIsbn());

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().matchReason()).isEqualTo("ISBN");
            assertThat(result.getFirst().books()).hasSize(2);
        }

        @Test
        void convertsIsbn10ToIsbn13ForMatching() {
            BookEntity book1 = createBookWithIsbn(BookFileType.EPUB, "Book", "Author", "9780306406157", null);
            BookEntity book2 = createBookWithIsbn(BookFileType.MOBI, "Book", "Author", null, "0306406152");
            stubBooks(book1, book2);

            List<DuplicateGroup> result = service.findDuplicates(onlyIsbn());

            assertThat(result).hasSize(1);
        }

        @Test
        void doesNotGroupDifferentIsbns() {
            BookEntity book1 = createBookWithIsbn(BookFileType.EPUB, "Book A", "Author", "9781234567890", null);
            BookEntity book2 = createBookWithIsbn(BookFileType.MOBI, "Book B", "Author", "9780987654321", null);
            stubBooks(book1, book2);

            List<DuplicateGroup> result = service.findDuplicates(onlyIsbn());

            assertThat(result).isEmpty();
        }

        @Test
        void skipsBooksWithNullMetadata() {
            BookEntity book1 = createBookWithIsbn(BookFileType.EPUB, "Book", "Author", "9781234567890", null);
            BookEntity book2 = createBook(BookFileType.MOBI, "Book", "Author");
            book2.setMetadata(null);
            stubBooks(book1, book2);

            List<DuplicateGroup> result = service.findDuplicates(onlyIsbn());

            assertThat(result).isEmpty();
        }

        @Test
        void skipsBooksWithBlankIsbn() {
            BookEntity book1 = createBookWithIsbn(BookFileType.EPUB, "Book", "Author", "  ", null);
            BookEntity book2 = createBookWithIsbn(BookFileType.MOBI, "Book", "Author", "  ", null);
            stubBooks(book1, book2);

            List<DuplicateGroup> result = service.findDuplicates(onlyIsbn());

            assertThat(result).isEmpty();
        }

        @Test
        void trimsIsbnBeforeGrouping() {
            BookEntity book1 = createBookWithIsbn(BookFileType.EPUB, "Book", "Author", "9781234567890 ", null);
            BookEntity book2 = createBookWithIsbn(BookFileType.MOBI, "Book", "Author", " 9781234567890", null);
            stubBooks(book1, book2);

            List<DuplicateGroup> result = service.findDuplicates(onlyIsbn());

            assertThat(result).hasSize(1);
        }
    }

    // ── External ID matching ────────────────────────────────────

    @Nested
    class ExternalIdMatching {

        @Test
        void groupsBooksWithSameGoodreadsId() {
            BookEntity book1 = createBook(BookFileType.EPUB, "Book A", "Author");
            book1.getMetadata().setGoodreadsId("12345");
            BookEntity book2 = createBook(BookFileType.MOBI, "Book B", "Author");
            book2.getMetadata().setGoodreadsId("12345");
            stubBooks(book1, book2);

            List<DuplicateGroup> result = service.findDuplicates(onlyExternalId());

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().matchReason()).isEqualTo("EXTERNAL_ID");
        }

        @Test
        void groupsBooksWithSameAsin() {
            BookEntity book1 = createBook(BookFileType.EPUB, "Book A", "Author");
            book1.getMetadata().setAsin("B0ABCDEFGH");
            BookEntity book2 = createBook(BookFileType.MOBI, "Book B", "Author");
            book2.getMetadata().setAsin("B0ABCDEFGH");
            stubBooks(book1, book2);

            List<DuplicateGroup> result = service.findDuplicates(onlyExternalId());

            assertThat(result).hasSize(1);
        }

        @Test
        void unionFindGroupsTransitively() {
            BookEntity book1 = createBook(BookFileType.EPUB, "Book", "Author");
            book1.getMetadata().setGoodreadsId("111");
            BookEntity book2 = createBook(BookFileType.MOBI, "Book", "Author");
            book2.getMetadata().setGoodreadsId("111");
            book2.getMetadata().setAsin("ASIN1");
            BookEntity book3 = createBook(BookFileType.PDF, "Book", "Author");
            book3.getMetadata().setAsin("ASIN1");
            stubBooks(book1, book2, book3);

            List<DuplicateGroup> result = service.findDuplicates(onlyExternalId());

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().books()).hasSize(3);
        }

        @Test
        void doesNotGroupBooksWithDifferentExternalIds() {
            BookEntity book1 = createBook(BookFileType.EPUB, "Book A", "Author");
            book1.getMetadata().setGoodreadsId("111");
            BookEntity book2 = createBook(BookFileType.MOBI, "Book B", "Author");
            book2.getMetadata().setGoodreadsId("222");
            stubBooks(book1, book2);

            List<DuplicateGroup> result = service.findDuplicates(onlyExternalId());

            assertThat(result).isEmpty();
        }

        @Test
        void skipsBooksWithNoExternalIds() {
            BookEntity book1 = createBook(BookFileType.EPUB, "Book A", "Author");
            BookEntity book2 = createBook(BookFileType.MOBI, "Book B", "Author");
            stubBooks(book1, book2);

            List<DuplicateGroup> result = service.findDuplicates(onlyExternalId());

            assertThat(result).isEmpty();
        }

        @Test
        void trimsExternalIdsBeforeGrouping() {
            BookEntity book1 = createBook(BookFileType.EPUB, "Book A", "Author");
            book1.getMetadata().setHardcoverId("  abc  ");
            BookEntity book2 = createBook(BookFileType.MOBI, "Book B", "Author");
            book2.getMetadata().setHardcoverId("abc");
            stubBooks(book1, book2);

            List<DuplicateGroup> result = service.findDuplicates(onlyExternalId());

            assertThat(result).hasSize(1);
        }

        @Test
        void matchesAcrossDifferentIdTypes() {
            BookEntity book1 = createBook(BookFileType.EPUB, "Book A", "Author");
            book1.getMetadata().setGoodreadsId("shared-id");
            BookEntity book2 = createBook(BookFileType.MOBI, "Book B", "Author");
            book2.getMetadata().setGoodreadsId("shared-id");
            book2.getMetadata().setAudibleId("aud-123");
            BookEntity book3 = createBook(BookFileType.PDF, "Book C", "Author");
            book3.getMetadata().setAudibleId("aud-123");
            stubBooks(book1, book2, book3);

            List<DuplicateGroup> result = service.findDuplicates(onlyExternalId());

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().books()).hasSize(3);
        }
    }

    // ── Title + Author matching ─────────────────────────────────

    @Nested
    class TitleAuthorMatching {

        @Test
        void groupsBooksWithSameTitleAndAuthor() {
            BookEntity book1 = createBook(BookFileType.EPUB, "The Great Gatsby", "F. Scott Fitzgerald");
            BookEntity book2 = createBook(BookFileType.MOBI, "The Great Gatsby", "F. Scott Fitzgerald");
            stubBooks(book1, book2);

            List<DuplicateGroup> result = service.findDuplicates(onlyTitleAuthor());

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().matchReason()).isEqualTo("TITLE_AUTHOR");
        }

        @Test
        void matchesCaseInsensitive() {
            BookEntity book1 = createBook(BookFileType.EPUB, "THE GREAT GATSBY", "F. SCOTT FITZGERALD");
            BookEntity book2 = createBook(BookFileType.MOBI, "the great gatsby", "f. scott fitzgerald");
            stubBooks(book1, book2);

            List<DuplicateGroup> result = service.findDuplicates(onlyTitleAuthor());

            assertThat(result).hasSize(1);
        }

        @Test
        void doesNotGroupSameTitleDifferentAuthors() {
            BookEntity book1 = createBook(BookFileType.EPUB, "Introduction", "Alice Smith");
            BookEntity book2 = createBook(BookFileType.MOBI, "Introduction", "Bob Jones");
            stubBooks(book1, book2);

            List<DuplicateGroup> result = service.findDuplicates(onlyTitleAuthor());

            assertThat(result).isEmpty();
        }

        @Test
        void skipsBooksWithNoAuthors() {
            BookEntity book1 = createBook(BookFileType.EPUB, "Orphan Book", null);
            book1.getMetadata().setAuthors(new ArrayList<>());
            BookEntity book2 = createBook(BookFileType.MOBI, "Orphan Book", null);
            book2.getMetadata().setAuthors(new ArrayList<>());
            stubBooks(book1, book2);

            List<DuplicateGroup> result = service.findDuplicates(onlyTitleAuthor());

            assertThat(result).isEmpty();
        }

        @Test
        void skipsBooksWithNullTitle() {
            BookEntity book1 = createBook(BookFileType.EPUB, null, "Author");
            BookEntity book2 = createBook(BookFileType.MOBI, null, "Author");
            stubBooks(book1, book2);

            List<DuplicateGroup> result = service.findDuplicates(onlyTitleAuthor());

            assertThat(result).isEmpty();
        }

        @Test
        void skipsBooksWithBlankTitle() {
            BookEntity book1 = createBook(BookFileType.EPUB, "  ", "Author");
            BookEntity book2 = createBook(BookFileType.MOBI, "  ", "Author");
            stubBooks(book1, book2);

            List<DuplicateGroup> result = service.findDuplicates(onlyTitleAuthor());

            assertThat(result).isEmpty();
        }

        @Test
        void groupsByOverlappingAuthors() {
            BookEntity book1 = createBook(BookFileType.EPUB, "Collab Work", "Author A");
            book1.getMetadata().getAuthors().add(AuthorEntity.builder().id(90L).name("Author B").build());

            BookEntity book2 = createBook(BookFileType.MOBI, "Collab Work", "Author B");
            book2.getMetadata().getAuthors().add(AuthorEntity.builder().id(91L).name("Author C").build());
            stubBooks(book1, book2);

            List<DuplicateGroup> result = service.findDuplicates(onlyTitleAuthor());

            assertThat(result).hasSize(1);
        }

        @Test
        void requiresAtLeastTwoBooksWithAuthorsInTitleGroup() {
            BookEntity book1 = createBook(BookFileType.EPUB, "Solo Title", "Author A");
            BookEntity book2 = createBook(BookFileType.MOBI, "Solo Title", null);
            book2.getMetadata().setAuthors(new ArrayList<>());
            stubBooks(book1, book2);

            List<DuplicateGroup> result = service.findDuplicates(onlyTitleAuthor());

            assertThat(result).isEmpty();
        }
    }

    // ── Directory matching ──────────────────────────────────────

    @Nested
    class DirectoryMatching {

        @Test
        void groupsBooksInSameDirectory() {
            LibraryPathEntity path = LibraryPathEntity.builder().id(10L).build();

            BookEntity book1 = createBook(BookFileType.EPUB, "Book", "Author");
            book1.setLibraryPath(path);
            book1.getBookFiles().getFirst().setFileSubPath("fiction/scifi");

            BookEntity book2 = createBook(BookFileType.MOBI, "Book", "Author");
            book2.setLibraryPath(path);
            book2.getBookFiles().getFirst().setFileSubPath("fiction/scifi");
            stubBooks(book1, book2);

            List<DuplicateGroup> result = service.findDuplicates(onlyDirectory());

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().matchReason()).isEqualTo("DIRECTORY");
        }

        @Test
        void doesNotGroupBooksInDifferentDirectories() {
            LibraryPathEntity path = LibraryPathEntity.builder().id(10L).build();

            BookEntity book1 = createBook(BookFileType.EPUB, "Book", "Author");
            book1.setLibraryPath(path);
            book1.getBookFiles().getFirst().setFileSubPath("fiction");

            BookEntity book2 = createBook(BookFileType.MOBI, "Book", "Author");
            book2.setLibraryPath(path);
            book2.getBookFiles().getFirst().setFileSubPath("nonfiction");
            stubBooks(book1, book2);

            List<DuplicateGroup> result = service.findDuplicates(onlyDirectory());

            assertThat(result).isEmpty();
        }

        @Test
        void doesNotGroupBooksFromDifferentLibraryPaths() {
            LibraryPathEntity path1 = LibraryPathEntity.builder().id(10L).build();
            LibraryPathEntity path2 = LibraryPathEntity.builder().id(20L).build();

            BookEntity book1 = createBook(BookFileType.EPUB, "Book", "Author");
            book1.setLibraryPath(path1);
            book1.getBookFiles().getFirst().setFileSubPath("shared");

            BookEntity book2 = createBook(BookFileType.MOBI, "Book", "Author");
            book2.setLibraryPath(path2);
            book2.getBookFiles().getFirst().setFileSubPath("shared");
            stubBooks(book1, book2);

            List<DuplicateGroup> result = service.findDuplicates(onlyDirectory());

            assertThat(result).isEmpty();
        }

        @Test
        void skipsBooksWithNullLibraryPath() {
            BookEntity book1 = createBook(BookFileType.EPUB, "Book", "Author");
            book1.setLibraryPath(null);
            BookEntity book2 = createBook(BookFileType.MOBI, "Book", "Author");
            book2.setLibraryPath(null);
            stubBooks(book1, book2);

            List<DuplicateGroup> result = service.findDuplicates(onlyDirectory());

            assertThat(result).isEmpty();
        }

        @Test
        void skipsBooksWithBlankFileSubPath() {
            LibraryPathEntity path = LibraryPathEntity.builder().id(10L).build();

            BookEntity book1 = createBook(BookFileType.EPUB, "Book", "Author");
            book1.setLibraryPath(path);
            book1.getBookFiles().getFirst().setFileSubPath("  ");

            BookEntity book2 = createBook(BookFileType.MOBI, "Book", "Author");
            book2.setLibraryPath(path);
            book2.getBookFiles().getFirst().setFileSubPath("  ");
            stubBooks(book1, book2);

            List<DuplicateGroup> result = service.findDuplicates(onlyDirectory());

            assertThat(result).isEmpty();
        }
    }

    // ── Filename matching ───────────────────────────────────────

    @Nested
    class FilenameMatching {

        @Test
        void groupsBooksWithSameBaseFilename() {
            BookEntity book1 = createBook(BookFileType.EPUB, "Book", "Author");
            book1.getBookFiles().getFirst().setFileName("my_book.epub");

            BookEntity book2 = createBook(BookFileType.MOBI, "Book", "Author");
            book2.getBookFiles().getFirst().setFileName("my_book.mobi");
            stubBooks(book1, book2);

            List<DuplicateGroup> result = service.findDuplicates(onlyFilename());

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().matchReason()).isEqualTo("FILENAME");
        }

        @Test
        void normalizesUnderscoresHyphensCaseForMatching() {
            BookEntity book1 = createBook(BookFileType.EPUB, "Book", "Author");
            book1.getBookFiles().getFirst().setFileName("My-Book_Title.epub");

            BookEntity book2 = createBook(BookFileType.MOBI, "Book", "Author");
            book2.getBookFiles().getFirst().setFileName("my book title.mobi");
            stubBooks(book1, book2);

            List<DuplicateGroup> result = service.findDuplicates(onlyFilename());

            assertThat(result).hasSize(1);
        }

        @Test
        void stripsPunctuationForMatching() {
            BookEntity book1 = createBook(BookFileType.EPUB, "Book", "Author");
            book1.getBookFiles().getFirst().setFileName("book!@#title.epub");

            BookEntity book2 = createBook(BookFileType.MOBI, "Book", "Author");
            book2.getBookFiles().getFirst().setFileName("booktitle.mobi");
            stubBooks(book1, book2);

            List<DuplicateGroup> result = service.findDuplicates(onlyFilename());

            assertThat(result).hasSize(1);
        }

        @Test
        void doesNotGroupDifferentFilenames() {
            BookEntity book1 = createBook(BookFileType.EPUB, "Book", "Author");
            book1.getBookFiles().getFirst().setFileName("alpha.epub");

            BookEntity book2 = createBook(BookFileType.MOBI, "Book", "Author");
            book2.getBookFiles().getFirst().setFileName("beta.mobi");
            stubBooks(book1, book2);

            List<DuplicateGroup> result = service.findDuplicates(onlyFilename());

            assertThat(result).isEmpty();
        }

        @Test
        void skipsBooksWithNoBookFiles() {
            BookEntity book1 = createBook(BookFileType.EPUB, "Book", "Author");
            book1.setBookFiles(new ArrayList<>());
            BookEntity book2 = createBook(BookFileType.MOBI, "Book", "Author");
            book2.setBookFiles(new ArrayList<>());
            stubBooks(book1, book2);

            List<DuplicateGroup> result = service.findDuplicates(onlyFilename());

            assertThat(result).isEmpty();
        }

        @Test
        void skipsBooksWithNullBookFiles() {
            BookEntity book1 = createBook(BookFileType.EPUB, "Book", "Author");
            book1.setBookFiles(null);
            BookEntity book2 = createBook(BookFileType.MOBI, "Book", "Author");
            book2.setBookFiles(null);
            stubBooks(book1, book2);

            List<DuplicateGroup> result = service.findDuplicates(onlyFilename());

            assertThat(result).isEmpty();
        }
    }

    // ── Strategy ordering (already-grouped exclusion) ───────────

    @Nested
    class StrategyOrdering {

        @Test
        void higherConfidenceStrategyPreventsLowerFromRegrouping() {
            BookEntity book1 = createBookWithIsbn(BookFileType.EPUB, "Same Title", "Same Author", "9781234567890", null);
            book1.getMetadata().setGoodreadsId("gr1");
            BookEntity book2 = createBookWithIsbn(BookFileType.MOBI, "Same Title", "Same Author", "9781234567890", null);
            book2.getMetadata().setGoodreadsId("gr1");
            stubBooks(book1, book2);

            List<DuplicateGroup> result = service.findDuplicates(allSignals());

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().matchReason()).isEqualTo("ISBN");
        }

        @Test
        void booksGroupedByIsbnNotRegroupedByTitleAuthor() {
            BookEntity book1 = createBookWithIsbn(BookFileType.EPUB, "The Book", "Author", "9781234567890", null);
            BookEntity book2 = createBookWithIsbn(BookFileType.MOBI, "The Book", "Author", "9781234567890", null);
            BookEntity book3 = createBook(BookFileType.PDF, "The Book", "Author");
            BookEntity book4 = createBook(BookFileType.AZW3, "The Book", "Author");
            stubBooks(book1, book2, book3, book4);

            List<DuplicateGroup> result = service.findDuplicates(allSignals());

            assertThat(result).hasSize(2);

            DuplicateGroup isbnGroup = result.stream()
                    .filter(g -> g.matchReason().equals("ISBN")).findFirst().orElseThrow();
            DuplicateGroup titleGroup = result.stream()
                    .filter(g -> g.matchReason().equals("TITLE_AUTHOR")).findFirst().orElseThrow();

            assertThat(isbnGroup.books()).hasSize(2);
            assertThat(titleGroup.books()).hasSize(2);

            Set<Long> isbnBookIds = new HashSet<>();
            isbnGroup.books().forEach(b -> isbnBookIds.add(b.getId()));
            assertThat(isbnBookIds).containsExactlyInAnyOrder(book1.getId(), book2.getId());

            Set<Long> titleBookIds = new HashSet<>();
            titleGroup.books().forEach(b -> titleBookIds.add(b.getId()));
            assertThat(titleBookIds).containsExactlyInAnyOrder(book3.getId(), book4.getId());
        }

        @Test
        void multipleStrategiesCreateSeparateGroups() {
            BookEntity book1 = createBookWithIsbn(BookFileType.EPUB, "Book A", "Author A", "9781234567890", null);
            BookEntity book2 = createBookWithIsbn(BookFileType.MOBI, "Book A", "Author A", "9781234567890", null);

            BookEntity book3 = createBook(BookFileType.EPUB, "Book B", "Author B");
            BookEntity book4 = createBook(BookFileType.MOBI, "Book B", "Author B");
            stubBooks(book1, book2, book3, book4);

            List<DuplicateGroup> result = service.findDuplicates(allSignals());

            assertThat(result).hasSize(2);
        }
    }

    // ── Target selection ────────────────────────────────────────

    @Nested
    class TargetSelection {

        @Test
        void defaultPriorityPrefersEpubOverMobi() {
            BookEntity mobi = createBook(BookFileType.MOBI, "Book", "Author");
            BookEntity epub = createBook(BookFileType.EPUB, "Book", "Author");
            stubBooks(mobi, epub);

            List<DuplicateGroup> result = service.findDuplicates(onlyTitleAuthor());

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().suggestedTargetBookId()).isEqualTo(epub.getId());
        }

        @Test
        void defaultPriorityPrefersEpubOverPdf() {
            BookEntity pdf = createBook(BookFileType.PDF, "Book", "Author");
            BookEntity epub = createBook(BookFileType.EPUB, "Book", "Author");
            stubBooks(pdf, epub);

            List<DuplicateGroup> result = service.findDuplicates(onlyTitleAuthor());

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().suggestedTargetBookId()).isEqualTo(epub.getId());
        }

        @Test
        void defaultPriorityPrefersPdfOverMobi() {
            BookEntity mobi = createBook(BookFileType.MOBI, "Book", "Author");
            BookEntity pdf = createBook(BookFileType.PDF, "Book", "Author");
            stubBooks(mobi, pdf);

            List<DuplicateGroup> result = service.findDuplicates(onlyTitleAuthor());

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().suggestedTargetBookId()).isEqualTo(pdf.getId());
        }

        @Test
        void defaultPriorityPrefersAzw3OverMobi() {
            BookEntity mobi = createBook(BookFileType.MOBI, "Book", "Author");
            BookEntity azw3 = createBook(BookFileType.AZW3, "Book", "Author");
            stubBooks(mobi, azw3);

            List<DuplicateGroup> result = service.findDuplicates(onlyTitleAuthor());

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().suggestedTargetBookId()).isEqualTo(azw3.getId());
        }

        @Test
        void usesLibraryFormatPriorityWhenConfigured() {
            library.setFormatPriority(List.of(BookFileType.MOBI, BookFileType.EPUB));

            BookEntity epub = createBook(BookFileType.EPUB, "Book", "Author");
            BookEntity mobi = createBook(BookFileType.MOBI, "Book", "Author");
            stubBooks(epub, mobi);

            List<DuplicateGroup> result = service.findDuplicates(onlyTitleAuthor());

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().suggestedTargetBookId()).isEqualTo(mobi.getId());
        }

        @Test
        void fallsBackToDefaultWhenLibraryPriorityEmpty() {
            library.setFormatPriority(new ArrayList<>());

            BookEntity mobi = createBook(BookFileType.MOBI, "Book", "Author");
            BookEntity epub = createBook(BookFileType.EPUB, "Book", "Author");
            stubBooks(mobi, epub);

            List<DuplicateGroup> result = service.findDuplicates(onlyTitleAuthor());

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().suggestedTargetBookId()).isEqualTo(epub.getId());
        }

        @Test
        void fallsBackToDefaultWhenLibraryPriorityNull() {
            library.setFormatPriority(null);

            BookEntity mobi = createBook(BookFileType.MOBI, "Book", "Author");
            BookEntity epub = createBook(BookFileType.EPUB, "Book", "Author");
            stubBooks(mobi, epub);

            List<DuplicateGroup> result = service.findDuplicates(onlyTitleAuthor());

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().suggestedTargetBookId()).isEqualTo(epub.getId());
        }

        @Test
        void tiebreaksByFileCountWhenFormatPriorityEqual() {
            BookEntity singleFile = createBook(BookFileType.EPUB, "Book", "Author");

            BookEntity multiFile = createBook(BookFileType.EPUB, "Book", "Author");
            BookFileEntity extraFile = BookFileEntity.builder()
                    .id(999L)
                    .fileName("extra.pdf")
                    .fileSubPath("books")
                    .isBookFormat(true)
                    .bookType(BookFileType.PDF)
                    .book(multiFile)
                    .build();
            multiFile.getBookFiles().add(extraFile);
            stubBooks(singleFile, multiFile);

            List<DuplicateGroup> result = service.findDuplicates(onlyTitleAuthor());

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().suggestedTargetBookId()).isEqualTo(multiFile.getId());
        }

        @Test
        void tiebreaksByMetadataMatchScoreWhenFileCountEqual() {
            BookEntity lowScore = createBook(BookFileType.EPUB, "Book", "Author");
            lowScore.setMetadataMatchScore(0.5f);

            BookEntity highScore = createBook(BookFileType.EPUB, "Book", "Author");
            highScore.setMetadataMatchScore(0.9f);
            stubBooks(lowScore, highScore);

            List<DuplicateGroup> result = service.findDuplicates(onlyTitleAuthor());

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().suggestedTargetBookId()).isEqualTo(highScore.getId());
        }

        @Test
        void handlesNullMetadataMatchScoreGracefully() {
            BookEntity nullScore = createBook(BookFileType.EPUB, "Book", "Author");
            nullScore.setMetadataMatchScore(null);

            BookEntity withScore = createBook(BookFileType.EPUB, "Book", "Author");
            withScore.setMetadataMatchScore(0.8f);
            stubBooks(nullScore, withScore);

            List<DuplicateGroup> result = service.findDuplicates(onlyTitleAuthor());

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().suggestedTargetBookId()).isEqualTo(withScore.getId());
        }

        @Test
        void handlesNullLibraryOnBookGracefully() {
            BookEntity book1 = createBook(BookFileType.EPUB, "Book", "Author");
            book1.setLibrary(null);
            BookEntity book2 = createBook(BookFileType.MOBI, "Book", "Author");
            book2.setLibrary(null);
            stubBooks(book1, book2);

            List<DuplicateGroup> result = service.findDuplicates(onlyTitleAuthor());

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().suggestedTargetBookId()).isEqualTo(book1.getId());
        }
    }

    // ── Edge cases ──────────────────────────────────────────────

    @Nested
    class EdgeCases {

        @Test
        void singletonGroupsAreFiltered() {
            BookEntity book1 = createBookWithIsbn(BookFileType.EPUB, "Unique A", "Author", "9781111111111", null);
            BookEntity book2 = createBookWithIsbn(BookFileType.MOBI, "Unique B", "Author", "9782222222222", null);
            stubBooks(book1, book2);

            List<DuplicateGroup> result = service.findDuplicates(onlyIsbn());

            assertThat(result).isEmpty();
        }

        @Test
        void booksWithOnlyNonBookFilesAreSkippedInFilenameMatch() {
            BookEntity book1 = createBook(BookFileType.EPUB, "Book", "Author");
            book1.getBookFiles().getFirst().setBookFormat(false);

            BookEntity book2 = createBook(BookFileType.MOBI, "Book", "Author");
            book2.getBookFiles().getFirst().setBookFormat(false);
            stubBooks(book1, book2);

            List<DuplicateGroup> result = service.findDuplicates(onlyFilename());

            assertThat(result).isEmpty();
        }

        @Test
        void handlesLargeGroupCorrectly() {
            List<BookEntity> books = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                BookEntity book = createBookWithIsbn(BookFileType.EPUB, "Popular Book", "Author",
                        "9781234567890", null);
                books.add(book);
            }
            when(bookRepository.findAllForDuplicateDetection(LIBRARY_ID)).thenReturn(books);

            List<DuplicateGroup> result = service.findDuplicates(onlyIsbn());

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().books()).hasSize(10);
        }

        @Test
        void bookWithNullBookTypeGetsZeroFormatScore() {
            BookEntity book1 = createBook(BookFileType.EPUB, "Book", "Author");

            BookEntity book2 = createBook(BookFileType.MOBI, "Book", "Author");
            book2.getBookFiles().getFirst().setBookType(null);
            stubBooks(book1, book2);

            List<DuplicateGroup> result = service.findDuplicates(onlyTitleAuthor());

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().suggestedTargetBookId()).isEqualTo(book1.getId());
        }

        @Test
        void handlesFilenameWithNoExtension() {
            BookEntity book1 = createBook(BookFileType.EPUB, "Book", "Author");
            book1.getBookFiles().getFirst().setFileName("mybook");

            BookEntity book2 = createBook(BookFileType.MOBI, "Book", "Author");
            book2.getBookFiles().getFirst().setFileName("mybook");
            stubBooks(book1, book2);

            List<DuplicateGroup> result = service.findDuplicates(onlyFilename());

            assertThat(result).hasSize(1);
        }

        @Test
        void threeBooksInTitleAuthorGroupWithPartialAuthorOverlap() {
            BookEntity book1 = createBook(BookFileType.EPUB, "Shared Title", "Author A");
            book1.getMetadata().getAuthors().add(AuthorEntity.builder().id(80L).name("Author B").build());

            BookEntity book2 = createBook(BookFileType.MOBI, "Shared Title", "Author B");
            book2.getMetadata().getAuthors().add(AuthorEntity.builder().id(81L).name("Author C").build());

            BookEntity book3 = createBook(BookFileType.PDF, "Shared Title", "Author C");
            stubBooks(book1, book2, book3);

            List<DuplicateGroup> result = service.findDuplicates(onlyTitleAuthor());

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().books()).hasSize(3);
        }

        @Test
        void duplicateGroupContainsCorrectBookDtos() {
            BookEntity book1 = createBookWithIsbn(BookFileType.EPUB, "Book", "Author", "9781234567890", null);
            BookEntity book2 = createBookWithIsbn(BookFileType.MOBI, "Book", "Author", "9781234567890", null);
            stubBooks(book1, book2);

            List<DuplicateGroup> result = service.findDuplicates(onlyIsbn());

            assertThat(result).hasSize(1);
            Set<Long> bookIds = new HashSet<>();
            for (Book b : result.getFirst().books()) {
                bookIds.add(b.getId());
            }
            assertThat(bookIds).containsExactlyInAnyOrder(book1.getId(), book2.getId());
        }
    }
}
