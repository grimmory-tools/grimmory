package org.grimmory.service.metadata.parser;

import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.request.FetchMetadataRequest;
import org.booklore.model.enums.MetadataProvider;
import org.booklore.service.metadata.parser.hardcover.GraphQLResponse;
import org.booklore.service.metadata.parser.hardcover.HardcoverBookDetails;
import org.booklore.service.metadata.parser.hardcover.HardcoverBookSearchService;
import org.booklore.service.metadata.parser.hardcover.HardcoverCachedTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

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
 */
class HardcoverParserTest {

    @Mock
    private HardcoverBookSearchService hardcoverBookSearchService;

    private HardcoverParser parser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        parser = new HardcoverParser(hardcoverBookSearchService);
    }

    @Nested
    @DisplayName("Search Strategy Tests")
    class SearchStrategyTests {

        @Test
        @DisplayName("Should search with combined title+author when both provided")
        void fetchMetadata_titleAndAuthor_searchesCombined() {
            Book book = Book.builder().title("Lamb").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Lamb")
                    .author("Christopher Moore")
                    .build();

            GraphQLResponse.Hit hit = createHitWithAuthor("Lamb: The Gospel According to Biff", "Christopher Moore");
            when(hardcoverBookSearchService.searchBooks("Lamb Christopher Moore"))
                    .thenReturn(List.of(hit));

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            verify(hardcoverBookSearchService).searchBooks("Lamb Christopher Moore");
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getTitle()).isEqualTo("Lamb: The Gospel According to Biff");
        }

        @Test
        @DisplayName("Should fall back to title-only search when combined search returns empty")
        void fetchMetadata_combinedSearchEmpty_fallsBackToTitleOnly() {
            Book book = Book.builder().title("Some Book").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Some Book")
                    .author("Unknown Author")
                    .build();

            when(hardcoverBookSearchService.searchBooks("Some Book Unknown Author"))
                    .thenReturn(Collections.emptyList());

            GraphQLResponse.Hit hit = createHitWithAuthor("Some Book", "Different Author");
            when(hardcoverBookSearchService.searchBooks("Some Book"))
                    .thenReturn(List.of(hit));

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            verify(hardcoverBookSearchService).searchBooks("Some Book Unknown Author");
            verify(hardcoverBookSearchService).searchBooks("Some Book");
        }

        @Test
        @DisplayName("Should fall back to title-only search when combined search returns results but they are filtered out")
        void fetchMetadata_combinedSearchFilteredOut_fallsBackToTitleOnly() {
            Book book = Book.builder().title("Portrait of a Thief").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Portrait of a Thief")
                    .author("Grace D. Li")
                    .build();

            // Simulate combined search returning a result that DOES NOT match the author (e.g. some other book matched the string)
            GraphQLResponse.Hit badHit = createHitWithAuthor("Portrait of something", "Random Person");
            when(hardcoverBookSearchService.searchBooks("Portrait of a Thief Grace D. Li"))
                    .thenReturn(List.of(badHit)); // Returns a hit, but fuzzy score will fail or simple check will fail

            // Fallback search should match
            GraphQLResponse.Hit goodHit = createHitWithAuthor("Portrait of a Thief", "Grace D. Li");
            when(hardcoverBookSearchService.searchBooks("Portrait of a Thief"))
                    .thenReturn(List.of(goodHit));

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            verify(hardcoverBookSearchService).searchBooks("Portrait of a Thief Grace D. Li");
            verify(hardcoverBookSearchService).searchBooks("Portrait of a Thief");
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getTitle()).isEqualTo("Portrait of a Thief");
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
            when(hardcoverBookSearchService.searchBookByIsbn("9780316769488"))
                    .thenReturn(List.of(hit));

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            verify(hardcoverBookSearchService).searchBookByIsbn("9780316769488");
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

            when(hardcoverBookSearchService.searchBookByIsbn("9780316769488"))
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

            when(hardcoverBookSearchService.searchBookByIsbn("9780316769488"))
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

            when(hardcoverBookSearchService.searchBookByIsbn("9780316769488"))
                    .thenReturn(null);

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("Should return all editions when ISBN search returns book with multiple editions")
        void fetchMetadata_isbnSearchMultipleEditions_returnsAllEditions() {
            Book book = Book.builder().title("Any Title").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Any Title")
                    .isbn("9780316769488")
                    .build();

            GraphQLResponse.BookWithEditions bookWithEditions = createBookWithEditions();

            // Add a second edition
            GraphQLResponse.Edition secondEdition = new GraphQLResponse.Edition();
            secondEdition.setId(2);
            secondEdition.setTitle("Test Book - Paperback Edition");
            secondEdition.setIsbn13("9780316769489");
            secondEdition.setIsbn10("0316769489");
            secondEdition.setPages(400);

            GraphQLResponse.Author author = new GraphQLResponse.Author();
            author.setName("Test Author");
            GraphQLResponse.Contributor contributor = new GraphQLResponse.Contributor();
            contributor.setAuthor(author);
            secondEdition.setCachedContributors(List.of(contributor));

            bookWithEditions.setEditions(List.of(bookWithEditions.getEditions().get(0), secondEdition));

            when(hardcoverBookSearchService.searchBookByIsbn("9780316769488"))
                    .thenReturn(List.of(bookWithEditions));

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            assertThat(results).hasSize(2);
            assertThat(results.get(0).getTitle()).isEqualTo("Test Book - Hardcover Edition");
            assertThat(results.get(1).getTitle()).isEqualTo("Test Book - Paperback Edition");
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

            when(hardcoverBookSearchService.searchBookByIsbn("9780316769488"))
                    .thenReturn(List.of(bookWithEditions));

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            assertThat(results).hasSize(1);
            BookMetadata metadata = results.get(0);
            assertThat(metadata.getTitle()).isEqualTo("Test Book Edition");
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

            GraphQLResponse.Hit hit = createHitWithAuthor("The Prince", "Niccolò Machiavelli");
            when(hardcoverBookSearchService.searchBooks("The Prince"))
                    .thenReturn(List.of(hit));

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            verify(hardcoverBookSearchService).searchBooks("The Prince");
        }
    }

    @Nested
    @DisplayName("Author Filtering Tests")
    class AuthorFilteringTests {

        @Test
        @DisplayName("Should filter results by author name when author provided")
        void fetchMetadata_authorProvided_filtersResults() {
            Book book = Book.builder().title("The Prince").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("The Prince")
                    .author("Machiavelli")
                    .build();

            List<GraphQLResponse.Hit> hits = List.of(
                    createHitWithAuthor("The Prince", "Kiera Cass"),
                    createHitWithAuthor("The Prince", "Niccolò Machiavelli"),
                    createHitWithAuthor("The Prince", "Tiffany Reisz")
            );
            when(hardcoverBookSearchService.searchBooks("The Prince Machiavelli"))
                    .thenReturn(hits);

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getAuthors()).contains("Niccolò Machiavelli");
        }

        @Test
        @DisplayName("Should use fuzzy matching for author names")
        void fetchMetadata_fuzzyAuthorMatch_includesPartialMatches() {
            Book book = Book.builder().title("Test Book").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Test Book")
                    .author("Moore")
                    .build();

            GraphQLResponse.Hit hit = createHitWithAuthor("Test Book", "Christopher Moore");
            when(hardcoverBookSearchService.searchBooks("Test Book Moore"))
                    .thenReturn(List.of(hit));

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            assertThat(results).hasSize(1);
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

            GraphQLResponse.BookWithEditions bookWithEditions = createBookWithEditions();
            // The book has "Test Author" but request has "Wrong Author" - should still return results
            when(hardcoverBookSearchService.searchBookByIsbn("123456789X"))
                    .thenReturn(List.of(bookWithEditions));
            List<BookMetadata> results = parser.fetchMetadata(book, request);

            assertThat(results).hasSize(1);  // Should not filter out
            verify(hardcoverBookSearchService).searchBookByIsbn("123456789X");

            assertThat(results.get(0).getAuthors()).contains("Test Author");
        }
    }

    @Nested
    @DisplayName("Metadata Mapping Tests")
    class MetadataMappingTests {

        @Test
        @DisplayName("Should map all basic metadata fields correctly")
        void fetchMetadata_fullDocument_mapsAllFields() {
            Book book = Book.builder().title("Test").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Test")
                    .build();

            GraphQLResponse.Hit hit = createFullyPopulatedHit();
            when(hardcoverBookSearchService.searchBooks("Test"))
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
            assertThat(metadata.getIsbn13()).isEqualTo("9781111111113");
            assertThat(metadata.getIsbn10()).isEqualTo("1111111111");
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

            GraphQLResponse.BookWithEditions hit = createBookWithEditions();
            when(hardcoverBookSearchService.searchBookByIsbn("9781234567897"))
                    .thenReturn(List.of(hit));

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            assertThat(results).hasSize(1);
            BookMetadata metadata = results.get(0);

            assertThat(metadata.getTitle()).isEqualTo("Test Book - Hardcover Edition");
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

            GraphQLResponse.BookWithEditions hit = createBookWithEditions();
            when(hardcoverBookSearchService.searchBookByIsbn("123456789X"))
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

            when(hardcoverBookSearchService.searchBookByIsbn("9791111111112"))
                    .thenReturn(List.of(hit));

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            assertThat(results.get(0).getIsbn10()).isNull();
            assertThat(results.get(0).getIsbn13()).isEqualTo("9791111111112");
        }

        @Test
        @DisplayName("Should map thumbnail URL correctly")
        void fetchMetadata_withImage_mapsThumbnailUrl() {
            Book book = Book.builder().title("Test").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Test")
                    .build();

            GraphQLResponse.Hit hit = createHitWithAuthor("Test", "Author");
            GraphQLResponse.Image image = new GraphQLResponse.Image();
            image.setUrl("https://example.com/cover.jpg");
            hit.getDocument().setImage(image);

            when(hardcoverBookSearchService.searchBooks("Test"))
                    .thenReturn(List.of(hit));

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            assertThat(results.get(0).getThumbnailUrl()).isEqualTo("https://example.com/cover.jpg");
        }

        @Test
        @DisplayName("Should handle null image gracefully")
        void fetchMetadata_nullImage_handlesGracefully() {
            Book book = Book.builder().title("Test").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Test")
                    .build();

            GraphQLResponse.Hit hit = createHitWithAuthor("Test", "Author");
            hit.getDocument().setImage(null);

            when(hardcoverBookSearchService.searchBooks("Test"))
                    .thenReturn(List.of(hit));

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
            Book book = Book.builder().title("Test").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Test")
                    .build();

            GraphQLResponse.Hit hit = createHitWithAuthor("Test", "Author");
            hit.getDocument().setId("12345");
            hit.getDocument().setMoods(List.of("sad", "dark", "funny", "hopeful"));

            when(hardcoverBookSearchService.searchBooks("Test"))
                    .thenReturn(List.of(hit));

            HardcoverBookDetails details = createBookDetailsWithMoodCounts();
            when(hardcoverBookSearchService.fetchBookDetails(12345))
                    .thenReturn(details);

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            verify(hardcoverBookSearchService).fetchBookDetails(12345);
            assertThat(results.get(0).getMoods()).isNotNull();
        }

        @Test
        @DisplayName("Should fall back to basic mood filtering when detail fetch fails")
        void fetchMetadata_detailFetchFails_fallsBackToBasicFilter() {
            Book book = Book.builder().title("Test").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Test")
                    .build();

            GraphQLResponse.Hit hit = createHitWithAuthor("Test", "Author");
            hit.getDocument().setId("12345");
            hit.getDocument().setMoods(List.of("sad", "funny", "invalid-mood"));

            when(hardcoverBookSearchService.searchBooks("Test"))
                    .thenReturn(List.of(hit));
            when(hardcoverBookSearchService.fetchBookDetails(12345))
                    .thenReturn(null);  // Simulate failure

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            assertThat(results.get(0).getMoods())
                    .containsAnyOf("Sad", "Funny")
                    .doesNotContain("Invalid-Mood");
        }

        @Test
        @DisplayName("Should handle books without moods")
        void fetchMetadata_noMoods_handlesGracefully() {
            Book book = Book.builder().title("Test").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Test")
                    .build();

            GraphQLResponse.Hit hit = createHitWithAuthor("Test", "Author");
            hit.getDocument().setMoods(null);

            when(hardcoverBookSearchService.searchBooks("Test"))
                    .thenReturn(List.of(hit));

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            assertThat(results).hasSize(1);
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
        @DisplayName("Should handle invalid book ID gracefully")
        void fetchMetadata_invalidBookId_handlesGracefully() {
            Book book = Book.builder().title("Test").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Test")
                    .build();

            GraphQLResponse.Hit hit = createHitWithAuthor("Test", "Author");
            hit.getDocument().setId("not-a-number");

            when(hardcoverBookSearchService.searchBooks("Test"))
                    .thenReturn(List.of(hit));

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getHardcoverBookId()).isEqualTo("not-a-number");
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
                    .title("Test")
                    .build();

            GraphQLResponse.Hit hit = createHitWithAuthor("Test", "Author");
            hit.getDocument().setReleaseDate("invalid-date");

            when(hardcoverBookSearchService.searchBooks("Test"))
                    .thenReturn(List.of(hit));

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getPublishedDate()).isNull();
        }
    }

    @Nested
    @DisplayName("fetchTopMetadata Tests")
    class FetchTopMetadataTests {

        @Test
        @DisplayName("Should return first result")
        void fetchTopMetadata_hasResults_returnsFirst() {
            Book book = Book.builder().title("Test").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Test")
                    .build();

            List<GraphQLResponse.Hit> hits = List.of(
                    createHitWithAuthor("First Book", "Author 1"),
                    createHitWithAuthor("Second Book", "Author 2")
            );
            when(hardcoverBookSearchService.searchBooks("Test"))
                    .thenReturn(hits);

            BookMetadata result = parser.fetchTopMetadata(book, request);

            assertThat(result).isNotNull();
            assertThat(result.getTitle()).isEqualTo("First Book");
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

    private GraphQLResponse.Hit createHitWithAuthor(String title, String author) {
        GraphQLResponse.Document doc = new GraphQLResponse.Document();
        doc.setTitle(title);
        doc.setSlug(title.toLowerCase().replace(" ", "-"));
        doc.setAuthorNames(Set.of(author));
        doc.setId(String.valueOf(new Random().nextInt(100000)));

        GraphQLResponse.Hit hit = new GraphQLResponse.Hit();
        hit.setDocument(doc);
        return hit;
    }

    private GraphQLResponse.Hit createFullyPopulatedHit() {
        GraphQLResponse.Document doc = new GraphQLResponse.Document();
        doc.setId("12345");
        doc.setSlug("test-book-slug");
        doc.setTitle("Test Book");
        doc.setSubtitle("A Subtitle");
        doc.setDescription("A description");
        doc.setAuthorNames(Set.of("Test Author"));
        doc.setRating(4.25);
        doc.setRatingsCount(100);
        doc.setPages(350);
        doc.setReleaseDate("2023-01-15");
        doc.setIsbns(List.of("9781111111113", "1111111111", "9781234567897", "123456789X", "9791111111112"));
        doc.setGenres(List.of("Fiction", "Fantasy"));
        doc.setMoods(List.of("adventurous", "exciting"));
        doc.setTags(List.of("Epic"));

        // Series info
        GraphQLResponse.Series series = new GraphQLResponse.Series();
        series.setName("Test Series");
        series.setBooksCount(5);
        series.setPrimaryBooksCount(3);

        GraphQLResponse.FeaturedSeries featuredSeries = new GraphQLResponse.FeaturedSeries();
        featuredSeries.setSeries(series);
        featuredSeries.setPosition(2f);
        doc.setFeaturedSeries(featuredSeries);

        // Image
        GraphQLResponse.Image image = new GraphQLResponse.Image();
        image.setUrl("https://example.com/cover.jpg");
        doc.setImage(image);

        GraphQLResponse.Hit hit = new GraphQLResponse.Hit();
        hit.setDocument(doc);
        return hit;
    }

    private HardcoverBookDetails createBookDetailsWithMoodCounts() {
        HardcoverBookDetails details = new HardcoverBookDetails();
        details.setId(12345);
        details.setTitle("Test Book");

        Map<String, List<HardcoverCachedTag>> cachedTags = new HashMap<>();

        List<HardcoverCachedTag> moods = new ArrayList<>();
        moods.add(createCachedTag("sad", 15));
        moods.add(createCachedTag("dark", 12));
        moods.add(createCachedTag("emotional", 8));
        moods.add(createCachedTag("funny", 2));  // Low count, should be filtered
        cachedTags.put("Mood", moods);

        List<HardcoverCachedTag> genres = new ArrayList<>();
        genres.add(createCachedTag("Fiction", 10));
        genres.add(createCachedTag("Drama", 8));
        cachedTags.put("Genre", genres);

        details.setCachedTags(cachedTags);
        return details;
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

    private HardcoverCachedTag createCachedTag(String tag, int count) {
        HardcoverCachedTag cachedTag = new HardcoverCachedTag();
        cachedTag.setTag(tag);
        cachedTag.setCount(count);
        return cachedTag;
    }
}
