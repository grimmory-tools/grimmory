package org.booklore.service.metadata.parser;

import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookFile;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.request.FetchMetadataRequest;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.MetadataProvider;
import org.booklore.service.metadata.parser.hardcover.GraphQLResponse;
import org.booklore.service.metadata.parser.hardcover.HardcoverBookSearchService;
import org.booklore.service.metadata.parser.hardcover.HardcoverCachedTag;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;


/**
 * Unit tests for HardcoverParser.
 * <p>
 * These tests verify:
 * - Combined title+author search strategy for better reliability
 * - ISBN search behavior
 * - Author filtering logic
 * - Mood/genre/tag mapping with quality filtering
 * - Edge cases and error handling
 * - Edition filtering logic
 */
class HardcoverParserTest {
    private static final Logger log = LoggerFactory.getLogger(HardcoverParserTest.class);
    private Locale previousDefaultLocale;

    @Mock
    private HardcoverBookSearchService hardcoverBookSearchService;

    private HardcoverParser parser;

    private MockedStatic<Jsoup> mockJsoup;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        parser = new HardcoverParser(hardcoverBookSearchService);
        mockJsoup = mockStatic(Jsoup.class);
        previousDefaultLocale = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
    }

    @AfterEach
    void tearDown() {
        Locale.setDefault(previousDefaultLocale);
        mockJsoup.close();
    }

    private GraphQLResponse.Hit createHitWithAuthor(String title, String author, String id) {
        GraphQLResponse.Document doc = new GraphQLResponse.Document();
        doc.setTitle(title);
        doc.setSlug(title.toLowerCase().replace(" ", "-"));
        doc.setAuthorNames(Set.of(author));
        doc.setId(id);
        doc.setUsersCount(5);

        GraphQLResponse.Hit hit = new GraphQLResponse.Hit();
        hit.setDocument(doc);
        return hit;
    }

    private GraphQLResponse.BookWithEditions createBookWithEditions() {
        GraphQLResponse.BookWithEditions book = new GraphQLResponse.BookWithEditions();

        book.setId(12345);
        book.setSlug("test-book-slug");
        book.setTitle("Test Book");
        book.setSubtitle("A Subtitle");
        book.setDescription("A description");
        book.setRating(4.25);
        book.setRatingsCount(100);
        book.setReviewsCount(50);
        book.setPages(350);
        book.setReleaseDate("2023-01-15");
        book.setReleaseYear(2023);

        // Series info
        GraphQLResponse.Series series = new GraphQLResponse.Series();
        series.setName("Test Series");
        series.setBooksCount(5);
        series.setPrimaryBooksCount(3);

        GraphQLResponse.FeaturedSeries featuredSeries = new GraphQLResponse.FeaturedSeries();
        featuredSeries.setSeries(series);
        featuredSeries.setPosition(2f);
        book.setFeaturedBookSeries(featuredSeries);

        GraphQLResponse.Image image = new GraphQLResponse.Image();
        image.setUrl("https://example.com/cover.jpg");
        book.setImage(image);

        // Cached contributors for the book
        GraphQLResponse.Author bookAuthor = new GraphQLResponse.Author();
        bookAuthor.setId(1);
        bookAuthor.setSlug("test-author");
        bookAuthor.setName("Test Author");

        GraphQLResponse.Contributor bookContributor = new GraphQLResponse.Contributor();
        bookContributor.setAuthor(bookAuthor);
        bookContributor.setContribution("Author");
        book.setCachedContributors(List.of(bookContributor));

        // Editions
        GraphQLResponse.Edition edition = new GraphQLResponse.Edition();
        edition.setId(1);
        edition.setTitle("Test Book - Hardcover Edition");
        edition.setSubtitle("A Subtitle");
        edition.setPages(350);
        edition.setReleaseDate("2023-01-15");
        edition.setReleaseYear(2023);

        // Cached contributors for the edition
        GraphQLResponse.Author editionAuthor = new GraphQLResponse.Author();
        editionAuthor.setId(1);
        editionAuthor.setSlug("test-author");
        editionAuthor.setName("Test Author");

        GraphQLResponse.Contributor editionContributor = new GraphQLResponse.Contributor();
        editionContributor.setAuthor(editionAuthor);
        editionContributor.setContribution("Author");
        edition.setCachedContributors(List.of(editionContributor));

        GraphQLResponse.Image editionImage = new GraphQLResponse.Image();
        editionImage.setUrl("https://example.com/edition-cover.jpg");
        edition.setImage(editionImage);

        edition.setIsbn10("123456789X");
        edition.setIsbn13("9781234567897");

        GraphQLResponse.Publisher publisher = new GraphQLResponse.Publisher();
        publisher.setName("Test Publisher");
        edition.setPublisher(publisher);

        GraphQLResponse.Language language = new GraphQLResponse.Language();
        language.setCode2("en");
        edition.setLanguage(language);

        book.setEditions(List.of(edition));

        // Cached tags for the book (moods, genres, tags)
        GraphQLResponse.CachedTags cachedTags = new GraphQLResponse.CachedTags();
        cachedTags.setMood(List.of(createCachedTag("adventurous", 15), createCachedTag("exciting", 12), createCachedTag("novotes", 0)));
        cachedTags.setGenre(List.of(createCachedTag("fiction", 20), createCachedTag("fantasy", 18), createCachedTag("novotes", 0)));
        cachedTags.setTag(List.of(createCachedTag("epic", 10), createCachedTag("quest", 8), createCachedTag("novotes", 0)));
        book.setCachedTags(cachedTags);

        return book;
    }

    private List<GraphQLResponse.BookWithEditions> createListBooksWithEditions(){
        return List.of(createBookWithEditions());
    }

    private HardcoverCachedTag createCachedTag(String tag, int count) {
        HardcoverCachedTag cachedTag = new HardcoverCachedTag();
        cachedTag.setTag(tag);
        cachedTag.setCount(count);
        return cachedTag;
    }

    private String readFixture(String fixtureName) {
        String filename = Paths.get("hardcover", fixtureName + ".fixture").toString();

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(filename)) {
            assert is != null;

            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new UncheckedIOException("Failed to load fixture: " + filename, e);
        }
    }

    private GraphQLResponse parseFixture(String fixture) {
        ObjectMapper objectMapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
        return objectMapper.readValue(fixture, GraphQLResponse.class);
    }

    private List<GraphQLResponse.BookWithEditions> parseBooks(String fixture){
        return parseFixture(fixture)
                .getData()
                .getBooks();
    }

    @Nested
    @DisplayName("Search Strategy Tests")
    class SearchStrategyTests {

        @Test
        @DisplayName("Should search with combined title+author when both provided")   //Ready Player One, Ernest Cline, 263L63
        void fetchMetadata_titleAndAuthor_searchesCombined() {
            // Arrange
            Book book = Book.builder()
                    .title("Lamb")
                    .build();

            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Lamb")
                    .author("Christopher Moore")
                    .build();

            List<GraphQLResponse.Hit> hits = new ArrayList<>();
            hits.add(createHitWithAuthor("Lamb","Christopher Moore", "358881"));
            hits.add(createHitWithAuthor("Lamb","Lucy Rose", "1437114"));

            String booksFixture = readFixture("example-results-lamb.json");
            List<GraphQLResponse.BookWithEditions> books = parseBooks(booksFixture);

            when(hardcoverBookSearchService.searchBooks("Lamb", "Christopher Moore"))
                    .thenReturn(hits);
            when(hardcoverBookSearchService.searchBookByHcid((List<Integer>) any()))
                    .thenReturn(books);

            // Act
            List<BookMetadata> results = parser.fetchMetadata(book, request);

            // Assert
            verify(hardcoverBookSearchService).searchBooks("Lamb", "Christopher Moore");
            assertThat(results.get(0).getTitle()).isEqualTo("Lamb: The Gospel According to Biff, Christ's Childhood Pal");
            assertThat(results.get(0).getAuthors().getFirst()).isEqualTo("Christopher Moore");
    }

        @Test
        @DisplayName("Should fall back to title-only search when combined search returns results but they are filtered out")
        void fetchMetadata_combinedSearchFilteredOut_fallsBackToTitleOnly() {
            Book book = Book.builder()
                    .title("Ready Player One")
                    .build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Ready Player One")
                    .author("Ernest Cline")
                    .build();

            List<GraphQLResponse.Hit> badHit = new ArrayList<>();
            badHit.add(createHitWithAuthor("Ready Player One", "Random Person", "0"));
            when(hardcoverBookSearchService.searchBooks("Ready Player One", "Ernest Cline"))
                    .thenReturn(badHit); // Returns a hit, but fuzzy score will fail or simple check will fail

            List<GraphQLResponse.Hit> goodHit = new ArrayList<>();
            goodHit.add(createHitWithAuthor("Ready Player One", "Ernest Cline", "26363"));
            when(hardcoverBookSearchService.searchBooks("Ready Player One"))
                    .thenReturn(goodHit);

            String booksFixture = readFixture("example-results.json");
            List<GraphQLResponse.BookWithEditions> books = parseBooks(booksFixture);

            when(hardcoverBookSearchService.searchBookByHcid((List<Integer>) any()))
                    .thenReturn(books);

            // Act
            List<BookMetadata> results = parser.fetchMetadata(book, request);

            // Assert
            verify(hardcoverBookSearchService).searchBooks("Ready Player One");

            assertThat(results.get(0).getTitle()).isEqualTo("Ready Player One");
        }

        @Test
        @DisplayName("Should search by ISBN when provided")
        void fetchMetadata_isbnProvided_searchesByIsbn() {
            Book book = Book.builder().title("Any Title").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Any Title")
                    .isbn("978-0316769488")
                    .build();

            GraphQLResponse.BookWithEditions hit = createBookWithEditions();
            hit.getEditions().get(0).setIsbn13("9780316769488");
            hit.getEditions().get(0).setIsbn10("0316769487");
            List<String> isbn = new ArrayList<>();
            isbn.add("9780316769488");
            when(hardcoverBookSearchService.searchBookByIsbn(isbn))
                    .thenReturn(List.of(hit));

            parser.fetchMetadata(book, request);

            verify(hardcoverBookSearchService).searchBookByIsbn(isbn);
            verify(hardcoverBookSearchService, never()).searchBooks(contains("title"));
        }

        @Test
        @DisplayName("Should return empty list when ISBN search returns book with no editions")
        void fetchMetadata_isbnSearchNoEditions_returnsEmptyList() {
            Book book = Book.builder().title("Any Title").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Any Title")
                    .isbn("9780316769488")
                    .build();

            GraphQLResponse.BookWithEditions bookWithNoEditions = new GraphQLResponse.BookWithEditions();
            bookWithNoEditions.setId(12345);
            bookWithNoEditions.setTitle("Test Book");
            bookWithNoEditions.setEditions(Collections.emptyList());

            List<String> isbn = new ArrayList<>();
            isbn.add("9780316769488");

            when(hardcoverBookSearchService.searchBookByIsbn(isbn))
                    .thenReturn(List.of(bookWithNoEditions));

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("Should return empty list when ISBN search returns book with null editions")
        void fetchMetadata_isbnSearchNullEditions_returnsEmptyList() {
            Book book = Book.builder().title("Any Title").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Any Title")
                    .isbn("9780316769488")
                    .build();

            GraphQLResponse.BookWithEditions bookWithNullEditions = new GraphQLResponse.BookWithEditions();
            bookWithNullEditions.setId(12345);
            bookWithNullEditions.setTitle("Test Book");

            bookWithNullEditions.setEditions(null);

            List<String> isbn = new ArrayList<>();
            isbn.add("9780316769488");

            when(hardcoverBookSearchService.searchBookByIsbn(isbn))
                    .thenReturn(List.of(bookWithNullEditions));

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("Should return empty list when ISBN search returns null")
        void fetchMetadata_isbnSearchReturnsNull_returnsEmptyList() {
            Book book = Book.builder().title("Any Title").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Any Title")
                    .isbn("9780316769488")
                    .build();

            List<String> isbn = new ArrayList<>();
            isbn.add("9780316769488");

            when(hardcoverBookSearchService.searchBookByIsbn(isbn))
                    .thenReturn(null); //hmmm

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("Should handle edition with null optional fields gracefully")
        void fetchMetadata_editionWithNullOptionalFields_handlesGracefully() {
            Book book = Book.builder().title("Any Title").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Any Title")
                    .isbn("9780316769488")
                    .build();

            GraphQLResponse.BookWithEditions bookWithEditions = new GraphQLResponse.BookWithEditions();
            bookWithEditions.setId(12345);
            bookWithEditions.setSlug("test-book");
            bookWithEditions.setTitle("Test Book");
            bookWithEditions.setDescription("A description");
            bookWithEditions.setRating(4.0);
            bookWithEditions.setRatingsCount(50);

            // Edition with null language, publisher, and cachedContributors
            GraphQLResponse.Edition edition = new GraphQLResponse.Edition();
            edition.setId(1);
            edition.setTitle("Test Book Edition");
            edition.setIsbn13("9780316769488");
            edition.setLanguage(null);
            edition.setPublisher(null);
            edition.setCachedContributors(null);
            edition.setImage(null);

            bookWithEditions.setEditions(List.of(edition));

            List<String> isbn = new ArrayList<>();
            isbn.add("9780316769488");

            when(hardcoverBookSearchService.searchBookByIsbn(isbn))
                    .thenReturn(List.of(bookWithEditions));

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            assertThat(results).hasSize(1);
            BookMetadata metadata = results.get(0);
            assertThat(metadata.getTitle()).isEqualTo("Test Book");
            assertThat(metadata.getLanguage()).isNull();
            assertThat(metadata.getPublisher()).isNull();
            assertThat(metadata.getAuthors()).isNullOrEmpty();
            assertThat(metadata.getThumbnailUrl()).isNull();
        }

        @Test
        @DisplayName("Should search title-only when no author provided")
        void fetchMetadata_noAuthor_searchesTitleOnly() {
            Book book = Book.builder().title("The Prince").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("The Prince")
                    .build();

            GraphQLResponse.Hit hit = createHitWithAuthor("The Prince", "Niccolò Machiavelli", "1150494");
            hit.getDocument().setId("1150494");
            when(hardcoverBookSearchService.searchBooks("The Prince"))
                    .thenReturn(List.of(hit));

            parser.fetchMetadata(book, request);

            verify(hardcoverBookSearchService).searchBooks("The Prince");
        }
    }

    @Nested
    @DisplayName("Author Filtering Tests")
    class AuthorFilteringTests {

        @Test
        @DisplayName("Should filter results by author name when author provided")
        void fetchMetadata_authorProvided_filtersResults() {
            // Arrange
            Book book = Book.builder()
                    .title("Summary of Ready Player One by Ernest Cline | Summary & Analysis")
                    .build();

            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Ready Player One")
                    .author("Nosco Publishing")
                    .build();

            List<GraphQLResponse.Hit> hits = new ArrayList<>();
            hits.add(createHitWithAuthor("Ready Player One","Ernest Cline", "26363"));
            hits.add(createHitWithAuthor("Summary of Ready Player One by Ernest Cline | Summary & Analysis","Nosco Publishing", "1106368"));

            String booksFixture = readFixture("example-results-author.json");
            List<GraphQLResponse.BookWithEditions> books = parseBooks(booksFixture);

            when(hardcoverBookSearchService.searchBooks("Ready Player One"))
                    .thenReturn(hits);
            when(hardcoverBookSearchService.searchBookByHcid((List<Integer>) any()))
                    .thenReturn(books);

            // Act
            List<BookMetadata> results = parser.fetchMetadata(book, request);

            // Assert
            verify(hardcoverBookSearchService).searchBooks("Ready Player One");
            verify(hardcoverBookSearchService).searchBookByHcid((List<Integer>) any());
            assertThat(results.get(0).getTitle()).isEqualTo("Summary of Ready Player One by Ernest Cline | Summary & Analysis");
        }

        @Test
        @DisplayName("Should use fuzzy matching for author names")
        void fetchMetadata_fuzzyAuthorMatch_includesPartialMatches() {
            // Arrange
            Book book = Book.builder()
                    .title("Summary of Ready Player One by Ernest Cline | Summary & Analysis")
                    .build();

            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Ready Player One")
                    .author("Nosco pub")
                    .build();


            List<GraphQLResponse.Hit> hits = new ArrayList<>();
            hits.add(createHitWithAuthor("Ready Player One","Ernest Cline", "26363"));
            hits.add(createHitWithAuthor("Summary of Ready Player One by Ernest Cline | Summary & Analysis","Nosco Publishing", "1106368"));

            String booksFixture = readFixture("example-results-author.json");
            List<GraphQLResponse.BookWithEditions> books = parseBooks(booksFixture);

            when(hardcoverBookSearchService.searchBooks("Ready Player One"))
                    .thenReturn(hits);
            when(hardcoverBookSearchService.searchBookByHcid((List<Integer>) any()))
                    .thenReturn(books);

            // Act
            List<BookMetadata> results = parser.fetchMetadata(book, request);

            // Assert
            verify(hardcoverBookSearchService).searchBooks("Ready Player One");
            verify(hardcoverBookSearchService).searchBookByHcid((List<Integer>) any());
            assertThat(results.get(0).getTitle()).isEqualTo("Summary of Ready Player One by Ernest Cline | Summary & Analysis");
        }

        @Test
        @DisplayName("Should not filter by author for ISBN searches")
        void fetchMetadata_isbnSearch_noAuthorFilter() {
            Book book = Book.builder().title("Any Book").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Any Book")
                    .isbn("123456789X")
                    .author("Wrong Author")  // Should be ignored
                    .build();

            List<String> isbn = new ArrayList<>();
            isbn.add("123456789X");

            GraphQLResponse.BookWithEditions bookWithEditions = createBookWithEditions();
            // The book has "Test Author" but request has "Wrong Author" - should still return results
            when(hardcoverBookSearchService.searchBookByIsbn(isbn))
                    .thenReturn(List.of(bookWithEditions));
            List<BookMetadata> results = parser.fetchMetadata(book, request);



            assertThat(results).hasSize(1);  // Should not filter out
            verify(hardcoverBookSearchService).searchBookByIsbn(isbn);

            assertThat(results.get(0).getAuthors()).contains("Test Author");
        }
    }

    @Nested
    @DisplayName("Metadata Mapping Tests")
    class MetadataMappingTests {

        @Test
        @DisplayName("Should map all basic metadata fields correctly")
        void fetchMetadata_fullDocument_mapsAllFields() {
            // Arrange
            Book book = Book.builder()
                    .title("Ready Player One")
                    .build();

            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Ready Player One")
                    .build();

            List<GraphQLResponse.Hit> hits = new ArrayList<>();
            hits.add(createHitWithAuthor("Ready Player One","Ernest Cline", "26363"));

            String booksFixture = readFixture("example-results.json");
            List<GraphQLResponse.BookWithEditions> books = parseBooks(booksFixture);

            when(hardcoverBookSearchService.searchBooks("Ready Player One"))
                    .thenReturn(hits);
            when(hardcoverBookSearchService.searchBookByHcid((List<Integer>) any()))
                    .thenReturn(books);

            // Act
            List<BookMetadata> results = parser.fetchMetadata(book, request);

            // Assert
            BookMetadata metadata = results.get(0);

            assertThat(metadata.getTitle()).isEqualTo("Ready Player One");
            assertThat(metadata.getSubtitle()).isNull();
            assertThat(metadata.getDescription()).isEqualTo("In the year 2045, reality is an ugly place. The only time Wade Watts really feels alive is when he’s jacked into the OASIS, a vast virtual world where most of humanity spends their days.\n\nWhen the eccentric creator of the OASIS dies, he leaves behind a series of fiendish puzzles, based on his obsession with the pop culture of decades past. Whoever is first to solve them will inherit his vast fortune—and control of the OASIS itself. \n\nThen Wade cracks the first clue. Suddenly he’s beset by rivals who’ll kill to take this prize. The race is on—and the only way to survive is to win.\n");
            assertThat(metadata.getHardcoverId()).isEqualTo("ready-player-one");
            assertThat(metadata.getHardcoverBookId()).isEqualTo("26363");
            assertThat(metadata.getHardcoverRating()).isEqualTo(4.03);
            assertThat(metadata.getHardcoverReviewCount()).isEqualTo(4572);
            assertThat(metadata.getPageCount()).isEqualTo(387);
            assertThat(metadata.getAuthors()).contains("Ernest Cline");
            assertThat(metadata.getSeriesName()).isEqualTo("Ready Player One");
            assertThat(metadata.getSeriesNumber()).isEqualTo(1.0f);
            assertThat(metadata.getSeriesTotal()).isEqualTo(2);
            assertThat(metadata.getIsbn13()).isEqualTo("9780307887436");
            assertThat(metadata.getIsbn10()).isEqualTo("030788743X");
            assertThat(metadata.getProvider()).isEqualTo(MetadataProvider.Hardcover);
        }

        @Test
        @DisplayName("Should map all basic metadata fields correctly when searching with ISBN")
        void fetchMetadata_fullDocument_mapsAllFields_fromISBN() {
            Book book = Book.builder().title("Test").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Test")
                    .isbn("9781234567897")
                    .build();

            List<String> isbn = new ArrayList<>();
            isbn.add("9781234567897");

            GraphQLResponse.BookWithEditions hit = createBookWithEditions();
            when(hardcoverBookSearchService.searchBookByIsbn(isbn))
                    .thenReturn(List.of(hit));

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            assertThat(results).hasSize(1);
            BookMetadata metadata = results.get(0);

            assertThat(metadata.getTitle()).isEqualTo("Test Book");
            assertThat(metadata.getSubtitle()).isEqualTo("A Subtitle");
            assertThat(metadata.getDescription()).isEqualTo("A description");
            assertThat(metadata.getHardcoverId()).isEqualTo("test-book-slug");
            assertThat(metadata.getHardcoverBookId()).isEqualTo("12345");
            assertThat(metadata.getHardcoverRating()).isEqualTo(4.25);
            assertThat(metadata.getHardcoverReviewCount()).isEqualTo(100);
            assertThat(metadata.getPageCount()).isEqualTo(350);
            assertThat(metadata.getAuthors()).contains("Test Author");
            assertThat(metadata.getSeriesName()).isEqualTo("Test Series");
            assertThat(metadata.getSeriesNumber()).isEqualTo(2.0f);
            assertThat(metadata.getSeriesTotal()).isEqualTo(3);
            assertThat(metadata.getIsbn13()).isEqualTo("9781234567897");
            assertThat(metadata.getIsbn10()).isEqualTo("123456789X");
            assertThat(metadata.getPublisher()).isEqualTo("Test Publisher");
            assertThat(metadata.getLanguage()).isEqualTo("en");
            assertThat(metadata.getMoods()).containsExactlyInAnyOrder("Adventurous", "Exciting");
            assertThat(metadata.getCategories()).containsExactlyInAnyOrder("Fiction", "Fantasy");
            assertThat(metadata.getTags()).containsExactlyInAnyOrder("Epic", "Quest");
            assertThat(metadata.getProvider()).isEqualTo(MetadataProvider.Hardcover);
        }

        @Test
        @DisplayName("Should map correct ISBN-13 from ISBN-10")
        void fetchMetadata_fullDocument_isbn10() {
            Book book = Book.builder().title("Test").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Test")
                    .isbn("123456789X")
                    .build();

            List<String> isbn = new ArrayList<>();
            isbn.add("123456789X");

            GraphQLResponse.BookWithEditions hit = createBookWithEditions();
            when(hardcoverBookSearchService.searchBookByIsbn(isbn))
                    .thenReturn(List.of(hit));

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            assertThat(results.get(0).getIsbn10()).isEqualTo("123456789X");
            assertThat(results.get(0).getIsbn13()).isEqualTo("9781234567897");
        }

        @Test
        @DisplayName("ISBN-13 not starting with 978 should not have an ISBN-10")
        void fetchMetadata_fullDocument_noIsbn10() {
            Book book = Book.builder().title("Test").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Test")
                    .isbn("9791111111112")
                    .build();

            GraphQLResponse.BookWithEditions hit = createBookWithEditions();
            hit.getEditions().get(0).setIsbn13("9791111111112");
            hit.getEditions().get(0).setIsbn10(null);

            List<String> isbn = new ArrayList<>();
            isbn.add("9791111111112");

            when(hardcoverBookSearchService.searchBookByIsbn(isbn))
                    .thenReturn(List.of(hit));

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            assertThat(results.get(0).getIsbn10()).isNull();
            assertThat(results.get(0).getIsbn13()).isEqualTo("9791111111112");
        }

        @Test
        @DisplayName("Should map thumbnail URL correctly")
        void fetchMetadata_withImage_mapsThumbnailUrl() {
            Book book = Book.builder()
                    .title("Ready Player One")
                    .build();

            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Ready Player One")
                    .build();

            List<GraphQLResponse.Hit> hits = new ArrayList<>();
            hits.add(createHitWithAuthor("Ready Player One","Ernest Cline", "26363"));

            String booksFixture = readFixture("example-results.json");
            List<GraphQLResponse.BookWithEditions> books = parseBooks(booksFixture);

            when(hardcoverBookSearchService.searchBooks("Ready Player One"))
                    .thenReturn(hits);
            when(hardcoverBookSearchService.searchBookByHcid((List<Integer>) any()))
                    .thenReturn(books);

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            assertThat(results.get(0).getThumbnailUrl()).isEqualTo("https://assets.hardcover.app/edition/31561015/c72731ad-c2bc-49c8-a087-66605cead82f.jpg");
        }

        @Test
        @DisplayName("Should handle null image gracefully")
        void fetchMetadata_nullImage_handlesGracefully() {
            Book book = Book.builder()
                    .title("Ready Player One")
                    .build();

            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Ready Player One")
                    .build();

            List<GraphQLResponse.Hit> hits = new ArrayList<>();
            hits.add(createHitWithAuthor("Ready Player One","Ernest Cline", "26363"));

            String booksFixture = readFixture("example-results.json");
            List<GraphQLResponse.BookWithEditions> books = parseBooks(booksFixture);
            books.stream().toList().forEach(n -> n.setImage(null));

            when(hardcoverBookSearchService.searchBooks("Ready Player One"))
                    .thenReturn(hits);
            when(hardcoverBookSearchService.searchBookByHcid((List<Integer>) any()))
                    .thenReturn(books);

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            assertThat(results.get(0).getThumbnailUrl()).isNull();
        }
    }

    @Nested
    @DisplayName("Mood and Genre Filtering Tests")
    class MoodFilteringTests {

        @Test
        @DisplayName("Should fetch detailed book info for mood filtering when book ID available")
        void fetchMetadata_withBookId_fetchesDetailedMoods() {
            // Arrange
            Book book = Book.builder()
                    .title("Ready Player One")
                    .build();

            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Ready Player One")
                    .author("Ernest Cline")
                    .build();

            List<GraphQLResponse.Hit> hits = new ArrayList<>();
            hits.add(createHitWithAuthor("Ready Player One","Ernest Cline", "26363"));

            String booksFixture = readFixture("example-results.json");
            List<GraphQLResponse.BookWithEditions> books = parseBooks(booksFixture);

            when(hardcoverBookSearchService.searchBooks("Ready Player One", "Ernest Cline"))
                    .thenReturn(hits);
            when(hardcoverBookSearchService.searchBookByHcid((List<Integer>) any()))
                    .thenReturn(books);

            // Act
            List<BookMetadata> results = parser.fetchMetadata(book, request);

            // Assert
            verify(hardcoverBookSearchService).searchBooks("Ready Player One", "Ernest Cline");

            assertThat(results.get(0).getTitle()).isEqualTo("Ready Player One");
            assertThat(results.get(0).getMoods()).isNotNull();
        }

        @Test
        @DisplayName("Should handle books without moods")
        void fetchMetadata_noMoods_handlesGracefully() {
            // Arrange
            Book book = Book.builder()
                    .title("Ready Player One")
                    .build();

            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Ready Player One")
                    .author("Ernest Cline")
                    .build();

            List<GraphQLResponse.Hit> hits = new ArrayList<>();
            hits.add(createHitWithAuthor("Ready Player One","Ernest Cline", "26363"));

            String booksFixture = readFixture("example-results-purged.json");
            List<GraphQLResponse.BookWithEditions> books = parseBooks(booksFixture);

            when(hardcoverBookSearchService.searchBooks("Ready Player One"))
                    .thenReturn(hits);
            when(hardcoverBookSearchService.searchBookByHcid((List<Integer>) any()))
                    .thenReturn(books);

            // Act
            List<BookMetadata> results = parser.fetchMetadata(book, request);

            // Assert
            verify(hardcoverBookSearchService).searchBooks("Ready Player One");

            assertThat(results.get(0).getTitle()).isEqualTo("Ready Player One");
            assertThat(results.get(0)).isNotNull();
            assertThat(results.get(0).getMoods()).isNull();
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should return empty list when search returns null")
        void fetchMetadata_nullResponse_returnsEmptyList() {
            Book book = Book.builder().title("Test").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Test")
                    .build();

            when(hardcoverBookSearchService.searchBooks(anyString()))
                    .thenReturn(null);

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("Should return empty list when search returns empty")
        void fetchMetadata_emptyResponse_returnsEmptyList() {
            Book book = Book.builder().title("Nonexistent Book").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Nonexistent Book")
                    .build();

            when(hardcoverBookSearchService.searchBooks(anyString()))
                    .thenReturn(Collections.emptyList());

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("Should handle null title in request")
        void fetchMetadata_nullTitle_returnsEmptyList() {
            Book book = Book.builder().build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .author("Some Author")
                    .build();

            when(hardcoverBookSearchService.searchBooks(anyString()))
                    .thenReturn(Collections.emptyList());

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("Should handle invalid date format gracefully")
        void fetchMetadata_invalidDate_handlesGracefully() {
            Book book = Book.builder().title("Test").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Test Book")
                    .isbn("9781234567897")
                    .build();

            GraphQLResponse.Hit hit = createHitWithAuthor("Test", "Author", "0");
            hit.getDocument().setReleaseDate("invalid-date");

            List<GraphQLResponse.BookWithEditions> books = createListBooksWithEditions();
            books.getFirst().setReleaseDate("invalid-date");
            books.getFirst().getEditions().getFirst().setReleaseDate("invalid-date");

            when(hardcoverBookSearchService.searchBooks("Test"))
                    .thenReturn(List.of(hit));
            when(hardcoverBookSearchService.searchBookByIsbn((List<String>) any()))
                    .thenReturn(books);

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getPublishedDate()).isNull();
        }
    }

    @Nested
    @DisplayName("fetchTopMetadata Tests") //return to this
    class FetchTopMetadataTests {

        @Test
        @DisplayName("Should return first result")
        void fetchTopMetadata_hasResults_returnsFirst() {
            Book book = Book.builder().title("Ready Player One").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Ready Player One")
                    .build();

            List<GraphQLResponse.Hit> hits = List.of(
                    createHitWithAuthor("Ready Player One", "Ernest Cline", "26363"),
                    createHitWithAuthor("Ready Player Two", "Ernest Cline", "427565")
            );

            String booksFixture = readFixture("example-results.json");
            List<GraphQLResponse.BookWithEditions> books = parseBooks(booksFixture);


            when(hardcoverBookSearchService.searchBooks("Ready Player One"))
                    .thenReturn(hits);

            when(hardcoverBookSearchService.searchBookByHcid((List<Integer>) any()))
                    .thenReturn(books);

            BookMetadata result = parser.fetchTopMetadata(book, request);

            assertThat(result).isNotNull();
            assertThat(result.getTitle()).isEqualTo("Ready Player One");
        }

        @Test
        @DisplayName("Should return null when no results")
        void fetchTopMetadata_noResults_returnsNull() {
            Book book = Book.builder().title("Nonexistent").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Nonexistent")
                    .build();

            when(hardcoverBookSearchService.searchBooks(anyString()))
                    .thenReturn(Collections.emptyList());

            BookMetadata result = parser.fetchTopMetadata(book, request);

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("Edition Filtering Tests")
        class EditionFilteringTests{

        @Test
        @DisplayName("Should Return Audiobooks when file format is provided")
        void fetchAudiobookEditions_whenFormat_returnsList(){

            BookFile file = BookFile.builder()
                    .bookType(BookFileType.AUDIOBOOK)
                    .build();
            Book book = Book.builder()
                    .title("Ready Player One")
                    .primaryFile(file)
                    .build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Ready Player One")
                    .author("Ernest Cline")
                    .build();

            List<GraphQLResponse.Hit> badHit = new ArrayList<>();
            badHit.add(createHitWithAuthor("Ready Player One", "Random Person", "0"));
            when(hardcoverBookSearchService.searchBooks("Ready Player One", "Ernest Cline"))
                    .thenReturn(badHit); // Returns a hit, but fuzzy score will fail or simple check will fail

            List<GraphQLResponse.Hit> goodHit = new ArrayList<>();
            goodHit.add(createHitWithAuthor("Ready Player One", "Ernest Cline", "26363"));
            when(hardcoverBookSearchService.searchBooks("Ready Player One"))
                    .thenReturn(goodHit);

            String booksFixture = readFixture("example-results.json");
            List<GraphQLResponse.BookWithEditions> books = parseBooks(booksFixture);

            when(hardcoverBookSearchService.searchBookByHcid((List<Integer>) any()))
                    .thenReturn(books);

            // Act
            List<BookMetadata> results = parser.fetchMetadata(book, request);

            // Assert
            verify(hardcoverBookSearchService).searchBooks("Ready Player One");

            assertThat(results.get(0).getTitle()).isEqualTo("Ready Player One");
            assertThat(results.get(0).getIsbn10()).isEqualTo("0307913147");
            assertThat(results.get(0).getIsbn13()).isEqualTo("9780307913142");
        }

        @Test
        @DisplayName("Should Return Original list if no file format is provided")
        void fetchEditions_whenNoFormat_returnsList(){

            Book book = Book.builder()
                    .title("Ready Player One")
                    .build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Ready Player One")
                    .author("Ernest Cline")
                    .build();

            List<GraphQLResponse.Hit> badHit = new ArrayList<>();
            badHit.add(createHitWithAuthor("Ready Player One", "Random Person", "0"));
            when(hardcoverBookSearchService.searchBooks("Ready Player One", "Ernest Cline"))
                    .thenReturn(badHit); // Returns a hit, but fuzzy score will fail or simple check will fail

            List<GraphQLResponse.Hit> goodHit = new ArrayList<>();
            goodHit.add(createHitWithAuthor("Ready Player One", "Ernest Cline", "26363"));
            when(hardcoverBookSearchService.searchBooks("Ready Player One"))
                    .thenReturn(goodHit);

            String booksFixture = readFixture("example-results.json");
            List<GraphQLResponse.BookWithEditions> books = parseBooks(booksFixture);

            when(hardcoverBookSearchService.searchBookByHcid((List<Integer>) any()))
                    .thenReturn(books);

            // Act
            List<BookMetadata> results = parser.fetchMetadata(book, request);

            // Assert
            verify(hardcoverBookSearchService).searchBooks("Ready Player One");

            assertThat(results.get(0).getTitle()).isEqualTo("Ready Player One");
            assertThat(results.get(0).getIsbn10()).isEqualTo("030788743X");
            assertThat(results.get(0).getIsbn13()).isEqualTo("9780307887436");
        }

        @Test
        @DisplayName("Should Return only French editions when locale is provided")
        void fetchEditions_whenNonEnglish_returnsList(){
            Locale.setDefault(Locale.FRENCH);
            Book book = Book.builder()
                    .title("Ready Player One")
                    .build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Ready Player One")
                    .author("Ernest Cline")
                    .build();

            List<GraphQLResponse.Hit> badHit = new ArrayList<>();
            badHit.add(createHitWithAuthor("Ready Player One", "Random Person", "0"));
            when(hardcoverBookSearchService.searchBooks("Ready Player One", "Ernest Cline"))
                    .thenReturn(badHit); // Returns a hit, but fuzzy score will fail or simple check will fail

            List<GraphQLResponse.Hit> goodHit = new ArrayList<>();
            goodHit.add(createHitWithAuthor("Ready Player One", "Ernest Cline", "26363"));
            when(hardcoverBookSearchService.searchBooks("Ready Player One"))
                    .thenReturn(goodHit);

            String booksFixture = readFixture("example-results.json");
            List<GraphQLResponse.BookWithEditions> books = parseBooks(booksFixture);

            when(hardcoverBookSearchService.searchBookByHcid((List<Integer>) any()))
                    .thenReturn(books);

            // Act
            List<BookMetadata> results = parser.fetchMetadata(book, request);

            // Assert
            verify(hardcoverBookSearchService).searchBooks("Ready Player One");

            assertThat(results.get(0).getTitle()).isEqualTo("Ready Player One");
            assertThat(results.get(0).getIsbn10()).isEqualTo("2266242334");
            assertThat(results.get(0).getIsbn13()).isEqualTo("9782266242332");
            assertThat(results.get(0).getLanguage()).isEqualTo("fr");
        }

        @Test
        @DisplayName("Should Return Original list if no editions for provied locale are found")
        void fetchEditions_whenNoLocale_returnsList(){
            Locale.setDefault(Locale.ROOT);
            Book book = Book.builder()
                    .title("Ready Player One")
                    .build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Ready Player One")
                    .author("Ernest Cline")
                    .build();

            List<GraphQLResponse.Hit> badHit = new ArrayList<>();
            badHit.add(createHitWithAuthor("Ready Player One", "Random Person", "0"));
            when(hardcoverBookSearchService.searchBooks("Ready Player One", "Ernest Cline"))
                    .thenReturn(badHit); // Returns a hit, but fuzzy score will fail or simple check will fail

            List<GraphQLResponse.Hit> goodHit = new ArrayList<>();
            goodHit.add(createHitWithAuthor("Ready Player One", "Ernest Cline", "26363"));
            when(hardcoverBookSearchService.searchBooks("Ready Player One"))
                    .thenReturn(goodHit);

            String booksFixture = readFixture("example-results.json");
            List<GraphQLResponse.BookWithEditions> books = parseBooks(booksFixture);

            when(hardcoverBookSearchService.searchBookByHcid((List<Integer>) any()))
                    .thenReturn(books);

            // Act
            List<BookMetadata> results = parser.fetchMetadata(book, request);

            // Assert
            verify(hardcoverBookSearchService).searchBooks("Ready Player One");

            assertThat(results.get(0).getTitle()).isEqualTo("Ready Player One");
            assertThat(results.get(0).getIsbn10()).isEqualTo("030788743X");
            assertThat(results.get(0).getIsbn13()).isEqualTo("9780307887436");
        }
    }

}