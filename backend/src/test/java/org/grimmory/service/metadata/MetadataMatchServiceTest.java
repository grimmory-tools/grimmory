package org.grimmory.service.metadata;

import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.settings.MetadataMatchWeights;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.CategoryEntity;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.book.BookQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetadataMatchServiceTest {

    @Mock
    private AppSettingService appSettingsService;

    @Mock
    private BookQueryService bookQueryService;

    @InjectMocks
    private MetadataMatchService service;

    private MetadataMatchWeights defaultWeights;

    @BeforeEach
    void setUp() {
        defaultWeights = MetadataMatchWeights.builder()
                .lubimyczytacRating(0)
                .audibleRating(0)
                .audibleReviewCount(0)
                .build();
    }

    private void stubWeights(MetadataMatchWeights weights) {
        when(appSettingsService.getAppSettings()).thenReturn(
                AppSettings.builder().metadataMatchWeights(weights).build()
        );
    }

    private BookEntity bookWith(BookMetadataEntity metadata) {
        BookEntity book = BookEntity.builder().metadata(metadata).build();
        return book;
    }

    @Nested
    class NullSafety {

        @Test
        void returnsZeroForNullBook() {
            assertThat(service.calculateMatchScore(null)).isEqualTo(0f);
        }

        @Test
        void returnsZeroForNullMetadata() {
            BookEntity book = BookEntity.builder().metadata(null).build();
            assertThat(service.calculateMatchScore(book)).isEqualTo(0f);
        }

        @Test
        void returnsZeroForNullAppSettings() {
            when(appSettingsService.getAppSettings()).thenReturn(null);
            BookEntity book = bookWith(BookMetadataEntity.builder().build());
            assertThat(service.calculateMatchScore(book)).isEqualTo(0f);
        }

        @Test
        void returnsZeroForNullWeights() {
            when(appSettingsService.getAppSettings()).thenReturn(AppSettings.builder().build());
            BookEntity book = bookWith(BookMetadataEntity.builder().build());
            assertThat(service.calculateMatchScore(book)).isEqualTo(0f);
        }

        @Test
        void returnsZeroWhenTotalWeightIsZero() {
            MetadataMatchWeights zeroWeights = MetadataMatchWeights.builder()
                    .title(0).subtitle(0).description(0).authors(0).publisher(0)
                    .publishedDate(0).seriesName(0).seriesNumber(0).seriesTotal(0)
                    .isbn13(0).isbn10(0).language(0).pageCount(0).categories(0)
                    .amazonRating(0).amazonReviewCount(0).goodreadsRating(0)
                    .goodreadsReviewCount(0).hardcoverRating(0).hardcoverReviewCount(0)
                    .doubanRating(0).doubanReviewCount(0).ranobedbRating(0).lubimyczytacRating(0)
                    .audibleRating(0).audibleReviewCount(0).coverImage(0)
                    .build();
            stubWeights(zeroWeights);

            BookEntity book = bookWith(BookMetadataEntity.builder().title("Test").build());
            assertThat(service.calculateMatchScore(book)).isEqualTo(0f);
        }
    }

    @Nested
    class ScoreCalculation {

        @Test
        void returnsZeroForEmptyMetadata() {
            stubWeights(defaultWeights);
            BookEntity book = bookWith(BookMetadataEntity.builder().build());
            assertThat(service.calculateMatchScore(book)).isEqualTo(0f);
        }

        @Test
        void scoresTitlePresence() {
            MetadataMatchWeights weights = MetadataMatchWeights.builder()
                    .title(10).subtitle(0).description(0).authors(0).publisher(0)
                    .publishedDate(0).seriesName(0).seriesNumber(0).seriesTotal(0)
                    .isbn13(0).isbn10(0).language(0).pageCount(0).categories(0)
                    .amazonRating(0).amazonReviewCount(0).goodreadsRating(0)
                    .goodreadsReviewCount(0).hardcoverRating(0).hardcoverReviewCount(0)
                    .doubanRating(0).doubanReviewCount(0).ranobedbRating(0).lubimyczytacRating(0)
                    .audibleRating(0).audibleReviewCount(0).coverImage(0)
                    .build();
            stubWeights(weights);

            BookEntity book = bookWith(BookMetadataEntity.builder().title("A Book").build());
            assertThat(service.calculateMatchScore(book)).isCloseTo(100f, within(0.01f));
        }

        @Test
        void blankStringDoesNotCount() {
            MetadataMatchWeights weights = MetadataMatchWeights.builder()
                    .title(10).subtitle(0).description(0).authors(0).publisher(0)
                    .publishedDate(0).seriesName(0).seriesNumber(0).seriesTotal(0)
                    .isbn13(0).isbn10(0).language(0).pageCount(0).categories(0)
                    .amazonRating(0).amazonReviewCount(0).goodreadsRating(0)
                    .goodreadsReviewCount(0).hardcoverRating(0).hardcoverReviewCount(0)
                    .doubanRating(0).doubanReviewCount(0).ranobedbRating(0).lubimyczytacRating(0)
                    .audibleRating(0).audibleReviewCount(0).coverImage(0)
                    .build();
            stubWeights(weights);

            BookEntity book = bookWith(BookMetadataEntity.builder().title("   ").build());
            assertThat(service.calculateMatchScore(book)).isEqualTo(0f);
        }

        @Test
        void scoresMultipleFieldsProportionally() {
            stubWeights(defaultWeights);

            BookMetadataEntity metadata = BookMetadataEntity.builder()
                    .title("Title")
                    .description("Desc")
                    .authors(List.of(AuthorEntity.builder().name("Author").build()))
                    .build();
            BookEntity book = bookWith(metadata);

            float expectedScore = (float) (defaultWeights.getTitle() + defaultWeights.getDescription() + defaultWeights.getAuthors())
                    / defaultWeights.totalWeight() * 100f;

            assertThat(service.calculateMatchScore(book)).isCloseTo(expectedScore, within(0.01f));
        }

        @Test
        void scoresAllFieldsFullyPopulated() {
            stubWeights(defaultWeights);

            BookMetadataEntity metadata = BookMetadataEntity.builder()
                    .title("Title")
                    .subtitle("Subtitle")
                    .description("Description")
                    .authors(List.of(AuthorEntity.builder().name("Author").build()))
                    .publisher("Publisher")
                    .publishedDate(LocalDate.now())
                    .seriesName("Series")
                    .seriesNumber(1.0f)
                    .seriesTotal(3)
                    .isbn13("1234567890123")
                    .isbn10("1234567890")
                    .language("en")
                    .pageCount(300)
                    .categories(Set.of(CategoryEntity.builder().name("Fiction").build()))
                    .amazonRating(4.5)
                    .amazonReviewCount(100)
                    .goodreadsRating(4.2)
                    .goodreadsReviewCount(200)
                    .hardcoverRating(4.0)
                    .hardcoverReviewCount(50)
                    .ranobedbRating(3.5)
                    .coverUpdatedOn(Instant.now())
                    .build();
            BookEntity book = bookWith(metadata);

            Float score = service.calculateMatchScore(book);
            assertThat(score).isGreaterThan(50f);
        }

        @Test
        void zeroSeriesNumberDoesNotScore() {
            MetadataMatchWeights weights = MetadataMatchWeights.builder()
                    .title(0).subtitle(0).description(0).authors(0).publisher(0)
                    .publishedDate(0).seriesName(0).seriesNumber(10).seriesTotal(0)
                    .isbn13(0).isbn10(0).language(0).pageCount(0).categories(0)
                    .amazonRating(0).amazonReviewCount(0).goodreadsRating(0)
                    .goodreadsReviewCount(0).hardcoverRating(0).hardcoverReviewCount(0)
                    .doubanRating(0).doubanReviewCount(0).ranobedbRating(0).lubimyczytacRating(0)
                    .audibleRating(0).audibleReviewCount(0).coverImage(0)
                    .build();
            stubWeights(weights);

            BookEntity book = bookWith(BookMetadataEntity.builder().seriesNumber(0f).build());
            assertThat(service.calculateMatchScore(book)).isEqualTo(0f);
        }

        @Test
        void zeroPageCountDoesNotScore() {
            MetadataMatchWeights weights = MetadataMatchWeights.builder()
                    .title(0).subtitle(0).description(0).authors(0).publisher(0)
                    .publishedDate(0).seriesName(0).seriesNumber(0).seriesTotal(0)
                    .isbn13(0).isbn10(0).language(0).pageCount(10).categories(0)
                    .amazonRating(0).amazonReviewCount(0).goodreadsRating(0)
                    .goodreadsReviewCount(0).hardcoverRating(0).hardcoverReviewCount(0)
                    .doubanRating(0).doubanReviewCount(0).ranobedbRating(0).lubimyczytacRating(0)
                    .audibleRating(0).audibleReviewCount(0).coverImage(0)
                    .build();
            stubWeights(weights);

            BookEntity book = bookWith(BookMetadataEntity.builder().pageCount(0).build());
            assertThat(service.calculateMatchScore(book)).isEqualTo(0f);
        }

        @Test
        void zeroRatingDoesNotScore() {
            MetadataMatchWeights weights = MetadataMatchWeights.builder()
                    .title(0).subtitle(0).description(0).authors(0).publisher(0)
                    .publishedDate(0).seriesName(0).seriesNumber(0).seriesTotal(0)
                    .isbn13(0).isbn10(0).language(0).pageCount(0).categories(0)
                    .amazonRating(10).amazonReviewCount(0).goodreadsRating(0)
                    .goodreadsReviewCount(0).hardcoverRating(0).hardcoverReviewCount(0)
                    .doubanRating(0).doubanReviewCount(0).ranobedbRating(0).lubimyczytacRating(0)
                    .audibleRating(0).audibleReviewCount(0).coverImage(0)
                    .build();
            stubWeights(weights);

            BookEntity book = bookWith(BookMetadataEntity.builder().amazonRating(0.0).build());
            assertThat(service.calculateMatchScore(book)).isEqualTo(0f);
        }

        @Test
        void emptyAuthorsDoNotScore() {
            MetadataMatchWeights weights = MetadataMatchWeights.builder()
                    .title(0).subtitle(0).description(0).authors(10).publisher(0)
                    .publishedDate(0).seriesName(0).seriesNumber(0).seriesTotal(0)
                    .isbn13(0).isbn10(0).language(0).pageCount(0).categories(0)
                    .amazonRating(0).amazonReviewCount(0).goodreadsRating(0)
                    .goodreadsReviewCount(0).hardcoverRating(0).hardcoverReviewCount(0)
                    .doubanRating(0).doubanReviewCount(0).ranobedbRating(0).lubimyczytacRating(0)
                    .audibleRating(0).audibleReviewCount(0).coverImage(0)
                    .build();
            stubWeights(weights);

            BookEntity book = bookWith(BookMetadataEntity.builder().authors(new ArrayList<>()).build());
            assertThat(service.calculateMatchScore(book)).isEqualTo(0f);
        }
    }

    @Nested
    class LockedFieldHandling {

        @Test
        void lockedFieldScoresEvenWhenEmpty() {
            MetadataMatchWeights weights = MetadataMatchWeights.builder()
                    .title(10).subtitle(0).description(0).authors(0).publisher(0)
                    .publishedDate(0).seriesName(0).seriesNumber(0).seriesTotal(0)
                    .isbn13(0).isbn10(0).language(0).pageCount(0).categories(0)
                    .amazonRating(0).amazonReviewCount(0).goodreadsRating(0)
                    .goodreadsReviewCount(0).hardcoverRating(0).hardcoverReviewCount(0)
                    .doubanRating(0).doubanReviewCount(0).ranobedbRating(0).lubimyczytacRating(0)
                    .audibleRating(0).audibleReviewCount(0).coverImage(0)
                    .build();
            stubWeights(weights);

            BookEntity book = bookWith(BookMetadataEntity.builder()
                    .title(null)
                    .titleLocked(true)
                    .build());

            assertThat(service.calculateMatchScore(book)).isCloseTo(100f, within(0.01f));
        }

        @Test
        void lockedPublishedDateScoresWithoutValue() {
            MetadataMatchWeights weights = MetadataMatchWeights.builder()
                    .title(0).subtitle(0).description(0).authors(0).publisher(0)
                    .publishedDate(10).seriesName(0).seriesNumber(0).seriesTotal(0)
                    .isbn13(0).isbn10(0).language(0).pageCount(0).categories(0)
                    .amazonRating(0).amazonReviewCount(0).goodreadsRating(0)
                    .goodreadsReviewCount(0).hardcoverRating(0).hardcoverReviewCount(0)
                    .doubanRating(0).doubanReviewCount(0).ranobedbRating(0).lubimyczytacRating(0)
                    .audibleRating(0).audibleReviewCount(0).coverImage(0)
                    .build();
            stubWeights(weights);

            BookEntity book = bookWith(BookMetadataEntity.builder()
                    .publishedDate(null)
                    .publishedDateLocked(true)
                    .build());

            assertThat(service.calculateMatchScore(book)).isCloseTo(100f, within(0.01f));
        }

        @Test
        void lockedCoverScoresWithoutCoverUpdatedOn() {
            MetadataMatchWeights weights = MetadataMatchWeights.builder()
                    .title(0).subtitle(0).description(0).authors(0).publisher(0)
                    .publishedDate(0).seriesName(0).seriesNumber(0).seriesTotal(0)
                    .isbn13(0).isbn10(0).language(0).pageCount(0).categories(0)
                    .amazonRating(0).amazonReviewCount(0).goodreadsRating(0)
                    .goodreadsReviewCount(0).hardcoverRating(0).hardcoverReviewCount(0)
                    .doubanRating(0).doubanReviewCount(0).ranobedbRating(0).lubimyczytacRating(0)
                    .lubimyczytacRating(0).audibleRating(0).audibleReviewCount(0).coverImage(10)
                    .build();
            stubWeights(weights);

            BookEntity book = bookWith(BookMetadataEntity.builder()
                    .coverUpdatedOn(null)
                    .coverLocked(true)
                    .build());

            assertThat(service.calculateMatchScore(book)).isCloseTo(100f, within(0.01f));
        }

        @Test
        void lockedSeriesNumberScoresWithZeroValue() {
            MetadataMatchWeights weights = MetadataMatchWeights.builder()
                    .title(0).subtitle(0).description(0).authors(0).publisher(0)
                    .publishedDate(0).seriesName(0).seriesNumber(10).seriesTotal(0)
                    .isbn13(0).isbn10(0).language(0).pageCount(0).categories(0)
                    .amazonRating(0).amazonReviewCount(0).goodreadsRating(0)
                    .goodreadsReviewCount(0).hardcoverRating(0).hardcoverReviewCount(0)
                    .doubanRating(0).doubanReviewCount(0).ranobedbRating(0).lubimyczytacRating(0)
                    .audibleRating(0).audibleReviewCount(0).coverImage(0)
                    .build();
            stubWeights(weights);

            BookEntity book = bookWith(BookMetadataEntity.builder()
                    .seriesNumber(0f)
                    .seriesNumberLocked(true)
                    .build());

            assertThat(service.calculateMatchScore(book)).isCloseTo(100f, within(0.01f));
        }

        @Test
        void lockedAuthorsScoresWithEmptySet() {
            MetadataMatchWeights weights = MetadataMatchWeights.builder()
                    .title(0).subtitle(0).description(0).authors(10).publisher(0)
                    .publishedDate(0).seriesName(0).seriesNumber(0).seriesTotal(0)
                    .isbn13(0).isbn10(0).language(0).pageCount(0).categories(0)
                    .amazonRating(0).amazonReviewCount(0).goodreadsRating(0)
                    .goodreadsReviewCount(0).hardcoverRating(0).hardcoverReviewCount(0)
                    .doubanRating(0).doubanReviewCount(0).ranobedbRating(0).lubimyczytacRating(0)
                    .audibleRating(0).audibleReviewCount(0).coverImage(0)
                    .build();
            stubWeights(weights);

            BookEntity book = bookWith(BookMetadataEntity.builder()
                    .authors(new ArrayList<>())
                    .authorsLocked(true)
                    .build());

            assertThat(service.calculateMatchScore(book)).isCloseTo(100f, within(0.01f));
        }
    }

    @Nested
    class RecalculateAll {

        @Test
        void recalculatesAndSavesAllBooks() {
            stubWeights(defaultWeights);

            BookEntity book1 = bookWith(BookMetadataEntity.builder().title("Book 1").build());
            BookEntity book2 = bookWith(BookMetadataEntity.builder().build());

            when(bookQueryService.getAllFullBookEntities()).thenReturn(List.of(book1, book2));

            service.recalculateAllMatchScores();

            assertThat(book1.getMetadataMatchScore()).isGreaterThan(0f);
            assertThat(book2.getMetadataMatchScore()).isEqualTo(0f);
            verify(bookQueryService).saveAll(List.of(book1, book2));
        }
    }
}
