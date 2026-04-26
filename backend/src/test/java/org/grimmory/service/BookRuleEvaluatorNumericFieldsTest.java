package org.grimmory.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.booklore.BookloreApplication;
import org.booklore.model.dto.*;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.ReadStatus;
import org.booklore.repository.BookRepository;
import org.booklore.service.task.TaskCronService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SpringBootTest(classes = {BookloreApplication.class})
@Transactional
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:ruleeval_numeric;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.path-config=build/tmp/test-config",
        "app.bookdrop-folder=build/tmp/test-bookdrop",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.task.scheduling.enabled=false",
        "app.task.scan-library-cron=*/1 * * * * *",
        "app.task.process-bookdrop-cron=*/1 * * * * *",
        "app.features.oidc-enabled=false",
        "spring.jpa.properties.hibernate.connection.provider_disables_autocommit=false"
})
@Import(BookRuleEvaluatorNumericFieldsTest.TestConfig.class)
class BookRuleEvaluatorNumericFieldsTest {

    @TestConfiguration
    static class TestConfig {
        @Bean("flyway")
        @Primary
        public Flyway flyway() {
            return mock(Flyway.class);
        }

        @Bean
        @Primary
        public TaskCronService taskCronService() {
            return mock(TaskCronService.class);
        }
    }

    @Autowired
    private BookRuleEvaluatorService evaluator;

    @Autowired
    private BookRepository bookRepository;

    @PersistenceContext
    private EntityManager em;

    private LibraryEntity library;
    private LibraryPathEntity libraryPath;
    private BookLoreUserEntity user;

    @BeforeEach
    void setUp() {
        library = LibraryEntity.builder().name("Test Library").icon("book").watch(false).build();
        em.persist(library);
        em.flush();

        libraryPath = LibraryPathEntity.builder().library(library).path("/test/path").build();
        em.persist(libraryPath);
        em.flush();

        user = BookLoreUserEntity.builder()
                .username("testuser")
                .passwordHash("hash")
                .isDefaultPassword(true)
                .name("Test User")
                .build();
        em.persist(user);
        em.flush();
    }

    private BookEntity createBook(String title) {
        BookEntity book = BookEntity.builder()
                .library(library)
                .libraryPath(libraryPath)
                .addedOn(Instant.now())
                .deleted(false)
                .build();
        em.persist(book);
        em.flush();

        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .book(book)
                .bookId(book.getId())
                .title(title)
                .build();
        em.persist(metadata);
        em.flush();

        book.setMetadata(metadata);
        return book;
    }

    private UserBookProgressEntity createProgress(BookEntity book, ReadStatus status) {
        UserBookProgressEntity progress = UserBookProgressEntity.builder()
                .user(user)
                .book(book)
                .readStatus(status)
                .build();
        em.persist(progress);
        em.flush();
        return progress;
    }

    private List<Long> findMatchingIds(GroupRule group) {
        Specification<BookEntity> spec = evaluator.toSpecification(group, user.getId());
        return bookRepository.findAll(spec).stream().map(BookEntity::getId).distinct().toList();
    }

    private GroupRule singleRule(RuleField field, RuleOperator operator, Object value) {
        return singleRule(field, operator, value, null, null);
    }

    private GroupRule singleRule(RuleField field, RuleOperator operator, Object value, Object valueStart, Object valueEnd) {
        Rule rule = new Rule();
        rule.setField(field);
        rule.setOperator(operator);
        rule.setValue(value);
        rule.setValueStart(valueStart);
        rule.setValueEnd(valueEnd);
        GroupRule group = new GroupRule();
        group.setJoin(JoinType.AND);
        group.setRules(List.of(rule));
        return group;
    }

    @Nested
    class StringValueAsNumberTests {
        @Test
        void lessThan_withStringValue_parsesCorrectly() {
            BookEntity low = createBook("Low Score");
            low.setMetadataMatchScore(30f);
            em.merge(low);

            BookEntity high = createBook("High Score");
            high.setMetadataMatchScore(80f);
            em.merge(high);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_SCORE, RuleOperator.LESS_THAN, "50"));
            assertThat(ids).contains(low.getId());
            assertThat(ids).doesNotContain(high.getId());
        }

        @Test
        void greaterThan_withStringValue_parsesCorrectly() {
            BookEntity low = createBook("Low Score");
            low.setMetadataMatchScore(30f);
            em.merge(low);

            BookEntity high = createBook("High Score");
            high.setMetadataMatchScore(80f);
            em.merge(high);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_SCORE, RuleOperator.GREATER_THAN, "50"));
            assertThat(ids).contains(high.getId());
            assertThat(ids).doesNotContain(low.getId());
        }

        @Test
        void greaterThanEqual_withStringValue_parsesCorrectly() {
            BookEntity exact = createBook("Exact Score");
            exact.setMetadataMatchScore(50f);
            em.merge(exact);

            BookEntity below = createBook("Below Score");
            below.setMetadataMatchScore(49f);
            em.merge(below);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_SCORE, RuleOperator.GREATER_THAN_EQUAL_TO, "50"));
            assertThat(ids).contains(exact.getId());
            assertThat(ids).doesNotContain(below.getId());
        }

        @Test
        void lessThanEqual_withStringValue_parsesCorrectly() {
            BookEntity exact = createBook("Exact Score");
            exact.setMetadataMatchScore(50f);
            em.merge(exact);

            BookEntity above = createBook("Above Score");
            above.setMetadataMatchScore(51f);
            em.merge(above);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_SCORE, RuleOperator.LESS_THAN_EQUAL_TO, "50"));
            assertThat(ids).contains(exact.getId());
            assertThat(ids).doesNotContain(above.getId());
        }

        @Test
        void inBetween_withStringValues_parsesCorrectly() {
            BookEntity inRange = createBook("In Range");
            inRange.setMetadataMatchScore(60f);
            em.merge(inRange);

            BookEntity outOfRange = createBook("Out of Range");
            outOfRange.setMetadataMatchScore(90f);
            em.merge(outOfRange);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_SCORE, RuleOperator.IN_BETWEEN, null, "40", "70"));
            assertThat(ids).contains(inRange.getId());
            assertThat(ids).doesNotContain(outOfRange.getId());
        }

        @Test
        void pageCount_greaterThan_withStringValue() {
            BookEntity longBook = createBook("Long Book");
            longBook.getMetadata().setPageCount(500);
            em.merge(longBook.getMetadata());

            BookEntity shortBook = createBook("Short Book");
            shortBook.getMetadata().setPageCount(100);
            em.merge(shortBook.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.PAGE_COUNT, RuleOperator.GREATER_THAN, "300"));
            assertThat(ids).contains(longBook.getId());
            assertThat(ids).doesNotContain(shortBook.getId());
        }

        @Test
        void nonNumericStringValue_returnsAllBooks() {
            BookEntity book = createBook("Any Book");
            book.setMetadataMatchScore(50f);
            em.merge(book);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_SCORE, RuleOperator.GREATER_THAN, "not_a_number"));
            assertThat(ids).contains(book.getId());
        }
    }

    @Nested
    class MetadataScoreTests {
        @Test
        void greaterThan_matchesHighScore() {
            BookEntity high = createBook("High Score");
            high.setMetadataMatchScore(90f);
            em.merge(high);

            BookEntity low = createBook("Low Score");
            low.setMetadataMatchScore(20f);
            em.merge(low);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_SCORE, RuleOperator.GREATER_THAN, 50));
            assertThat(ids).contains(high.getId());
            assertThat(ids).doesNotContain(low.getId());
        }

        @Test
        void lessThan_matchesLowScore() {
            BookEntity high = createBook("High Score");
            high.setMetadataMatchScore(90f);
            em.merge(high);

            BookEntity low = createBook("Low Score");
            low.setMetadataMatchScore(20f);
            em.merge(low);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_SCORE, RuleOperator.LESS_THAN, 50));
            assertThat(ids).contains(low.getId());
            assertThat(ids).doesNotContain(high.getId());
        }

        @Test
        void equals_matchesExactScore() {
            BookEntity exact = createBook("Exact Score");
            exact.setMetadataMatchScore(75f);
            em.merge(exact);

            BookEntity other = createBook("Other Score");
            other.setMetadataMatchScore(50f);
            em.merge(other);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_SCORE, RuleOperator.EQUALS, 75));
            assertThat(ids).contains(exact.getId());
            assertThat(ids).doesNotContain(other.getId());
        }

        @Test
        void inBetween_matchesScoreInRange() {
            BookEntity inRange = createBook("In Range");
            inRange.setMetadataMatchScore(60f);
            em.merge(inRange);

            BookEntity tooLow = createBook("Too Low");
            tooLow.setMetadataMatchScore(20f);
            em.merge(tooLow);

            BookEntity tooHigh = createBook("Too High");
            tooHigh.setMetadataMatchScore(95f);
            em.merge(tooHigh);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_SCORE, RuleOperator.IN_BETWEEN, null, 40, 80));
            assertThat(ids).contains(inRange.getId());
            assertThat(ids).doesNotContain(tooLow.getId(), tooHigh.getId());
        }

        @Test
        void nullScore_doesNotMatchComparison() {
            BookEntity noScore = createBook("No Score");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_SCORE, RuleOperator.GREATER_THAN, 0));
            assertThat(ids).doesNotContain(noScore.getId());
        }
    }

    @Nested
    class RatingFieldTests {
        @Test
        void amazonRating_greaterThan() {
            BookEntity highRated = createBook("High Rated");
            highRated.getMetadata().setAmazonRating(4.5);
            em.merge(highRated.getMetadata());

            BookEntity lowRated = createBook("Low Rated");
            lowRated.getMetadata().setAmazonRating(2.0);
            em.merge(lowRated.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.AMAZON_RATING, RuleOperator.GREATER_THAN, 3.5));
            assertThat(ids).contains(highRated.getId());
            assertThat(ids).doesNotContain(lowRated.getId());
        }

        @Test
        void goodreadsRating_lessThanEqual() {
            BookEntity rated3 = createBook("Rated 3");
            rated3.getMetadata().setGoodreadsRating(3.0);
            em.merge(rated3.getMetadata());

            BookEntity rated5 = createBook("Rated 5");
            rated5.getMetadata().setGoodreadsRating(5.0);
            em.merge(rated5.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.GOODREADS_RATING, RuleOperator.LESS_THAN_EQUAL_TO, 3.0));
            assertThat(ids).contains(rated3.getId());
            assertThat(ids).doesNotContain(rated5.getId());
        }

        @Test
        void hardcoverRating_inBetween() {
            BookEntity low = createBook("Low HC");
            low.getMetadata().setHardcoverRating(1.0);
            em.merge(low.getMetadata());

            BookEntity mid = createBook("Mid HC");
            mid.getMetadata().setHardcoverRating(3.5);
            em.merge(mid.getMetadata());

            BookEntity high = createBook("High HC");
            high.getMetadata().setHardcoverRating(5.0);
            em.merge(high.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.HARDCOVER_RATING, RuleOperator.IN_BETWEEN, null, 3.0, 4.0));
            assertThat(ids).contains(mid.getId());
            assertThat(ids).doesNotContain(low.getId(), high.getId());
        }

        @Test
        void amazonReviewCount_greaterThan() {
            BookEntity popular = createBook("Popular");
            popular.getMetadata().setAmazonReviewCount(5000);
            em.merge(popular.getMetadata());

            BookEntity niche = createBook("Niche");
            niche.getMetadata().setAmazonReviewCount(10);
            em.merge(niche.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.AMAZON_REVIEW_COUNT, RuleOperator.GREATER_THAN, 1000));
            assertThat(ids).contains(popular.getId());
            assertThat(ids).doesNotContain(niche.getId());
        }

        @Test
        void goodreadsReviewCount_lessThan() {
            BookEntity popular = createBook("Popular");
            popular.getMetadata().setGoodreadsReviewCount(10000);
            em.merge(popular.getMetadata());

            BookEntity obscure = createBook("Obscure");
            obscure.getMetadata().setGoodreadsReviewCount(5);
            em.merge(obscure.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.GOODREADS_REVIEW_COUNT, RuleOperator.LESS_THAN, 100));
            assertThat(ids).contains(obscure.getId());
            assertThat(ids).doesNotContain(popular.getId());
        }

        @Test
        void audibleRating_greaterThanWithStringValue() {
            BookEntity rated = createBook("Well Rated Audiobook");
            rated.getMetadata().setAudibleRating(4.8);
            em.merge(rated.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.AUDIBLE_RATING, RuleOperator.GREATER_THAN, "4.0"));
            assertThat(ids).contains(rated.getId());
        }

        @Test
        void lubimyczytacRating_equals() {
            BookEntity rated = createBook("Lubimyczytac Book");
            rated.getMetadata().setLubimyczytacRating(4.0);
            em.merge(rated.getMetadata());

            BookEntity other = createBook("Other Rating");
            other.getMetadata().setLubimyczytacRating(3.0);
            em.merge(other.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.LUBIMYCZYTAC_RATING, RuleOperator.EQUALS, 4.0));
            assertThat(ids).contains(rated.getId());
            assertThat(ids).doesNotContain(other.getId());
        }

        @Test
        void ranobedbRating_lessThan() {
            BookEntity low = createBook("Low Ranobe");
            low.getMetadata().setRanobedbRating(2.5);
            em.merge(low.getMetadata());

            BookEntity high = createBook("High Ranobe");
            high.getMetadata().setRanobedbRating(4.5);
            em.merge(high.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.RANOBEDB_RATING, RuleOperator.LESS_THAN, 3.0));
            assertThat(ids).contains(low.getId());
            assertThat(ids).doesNotContain(high.getId());
        }
    }

    @Nested
    class PersonalRatingTests {
        @Test
        void greaterThan_matchesHighRating() {
            BookEntity highRated = createBook("Loved It");
            UserBookProgressEntity p1 = createProgress(highRated, ReadStatus.READ);
            p1.setPersonalRating(5);
            em.merge(p1);

            BookEntity lowRated = createBook("Meh");
            UserBookProgressEntity p2 = createProgress(lowRated, ReadStatus.READ);
            p2.setPersonalRating(2);
            em.merge(p2);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.PERSONAL_RATING, RuleOperator.GREATER_THAN, 3));
            assertThat(ids).contains(highRated.getId());
            assertThat(ids).doesNotContain(lowRated.getId());
        }

        @Test
        void equals_matchesExactRating() {
            BookEntity rated4 = createBook("Rated 4");
            UserBookProgressEntity p1 = createProgress(rated4, ReadStatus.READ);
            p1.setPersonalRating(4);
            em.merge(p1);

            BookEntity rated2 = createBook("Rated 2");
            UserBookProgressEntity p2 = createProgress(rated2, ReadStatus.READ);
            p2.setPersonalRating(2);
            em.merge(p2);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.PERSONAL_RATING, RuleOperator.EQUALS, 4));
            assertThat(ids).contains(rated4.getId());
            assertThat(ids).doesNotContain(rated2.getId());
        }

        @Test
        void inBetween_matchesRatingInRange() {
            BookEntity rated1 = createBook("Rated 1");
            UserBookProgressEntity p1 = createProgress(rated1, ReadStatus.READ);
            p1.setPersonalRating(1);
            em.merge(p1);

            BookEntity rated3 = createBook("Rated 3");
            UserBookProgressEntity p2 = createProgress(rated3, ReadStatus.READ);
            p2.setPersonalRating(3);
            em.merge(p2);

            BookEntity rated5 = createBook("Rated 5");
            UserBookProgressEntity p3 = createProgress(rated5, ReadStatus.READ);
            p3.setPersonalRating(5);
            em.merge(p3);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.PERSONAL_RATING, RuleOperator.IN_BETWEEN, null, 2, 4));
            assertThat(ids).contains(rated3.getId());
            assertThat(ids).doesNotContain(rated1.getId(), rated5.getId());
        }

        @Test
        void greaterThan_withStringValue_parsesCorrectly() {
            BookEntity rated5 = createBook("Top Rated");
            UserBookProgressEntity p = createProgress(rated5, ReadStatus.READ);
            p.setPersonalRating(5);
            em.merge(p);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.PERSONAL_RATING, RuleOperator.GREATER_THAN, "3"));
            assertThat(ids).contains(rated5.getId());
        }
    }

    @Nested
    class AgeRatingTests {
        @Test
        void greaterThan_matchesMatureContent() {
            BookEntity mature = createBook("Mature Book");
            mature.getMetadata().setAgeRating(18);
            em.merge(mature.getMetadata());

            BookEntity kids = createBook("Kids Book");
            kids.getMetadata().setAgeRating(6);
            em.merge(kids.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.AGE_RATING, RuleOperator.GREATER_THAN, 12));
            assertThat(ids).contains(mature.getId());
            assertThat(ids).doesNotContain(kids.getId());
        }

        @Test
        void lessThanEqual_matchesYoungAudience() {
            BookEntity teens = createBook("Teen Book");
            teens.getMetadata().setAgeRating(13);
            em.merge(teens.getMetadata());

            BookEntity adult = createBook("Adult Book");
            adult.getMetadata().setAgeRating(18);
            em.merge(adult.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.AGE_RATING, RuleOperator.LESS_THAN_EQUAL_TO, 13));
            assertThat(ids).contains(teens.getId());
            assertThat(ids).doesNotContain(adult.getId());
        }
    }

    @Nested
    class PageCountComparisonTests {
        @Test
        void greaterThanEqual_matchesBoundary() {
            BookEntity exact = createBook("Exact Pages");
            exact.getMetadata().setPageCount(300);
            em.merge(exact.getMetadata());

            BookEntity below = createBook("Below Pages");
            below.getMetadata().setPageCount(299);
            em.merge(below.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.PAGE_COUNT, RuleOperator.GREATER_THAN_EQUAL_TO, 300));
            assertThat(ids).contains(exact.getId());
            assertThat(ids).doesNotContain(below.getId());
        }

        @Test
        void lessThanEqual_matchesBoundary() {
            BookEntity exact = createBook("Exact Pages");
            exact.getMetadata().setPageCount(200);
            em.merge(exact.getMetadata());

            BookEntity above = createBook("Above Pages");
            above.getMetadata().setPageCount(201);
            em.merge(above.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.PAGE_COUNT, RuleOperator.LESS_THAN_EQUAL_TO, 200));
            assertThat(ids).contains(exact.getId());
            assertThat(ids).doesNotContain(above.getId());
        }

        @Test
        void inBetween_matchesPageRange() {
            BookEntity short_ = createBook("Short");
            short_.getMetadata().setPageCount(50);
            em.merge(short_.getMetadata());

            BookEntity medium = createBook("Medium");
            medium.getMetadata().setPageCount(250);
            em.merge(medium.getMetadata());

            BookEntity long_ = createBook("Long");
            long_.getMetadata().setPageCount(800);
            em.merge(long_.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.PAGE_COUNT, RuleOperator.IN_BETWEEN, null, 100, 500));
            assertThat(ids).contains(medium.getId());
            assertThat(ids).doesNotContain(short_.getId(), long_.getId());
        }
    }

    @Nested
    class SeriesNumberTests {
        @Test
        void greaterThan_matchesHighSeriesNumber() {
            BookEntity book1 = createBook("Book 1");
            book1.getMetadata().setSeriesName("Test Series");
            book1.getMetadata().setSeriesNumber(1f);
            em.merge(book1.getMetadata());

            BookEntity book5 = createBook("Book 5");
            book5.getMetadata().setSeriesName("Test Series");
            book5.getMetadata().setSeriesNumber(5f);
            em.merge(book5.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.SERIES_NUMBER, RuleOperator.GREATER_THAN, 3));
            assertThat(ids).contains(book5.getId());
            assertThat(ids).doesNotContain(book1.getId());
        }

        @Test
        void equals_matchesExactSeriesNumber() {
            BookEntity book2 = createBook("Book 2");
            book2.getMetadata().setSeriesName("Test Series");
            book2.getMetadata().setSeriesNumber(2f);
            em.merge(book2.getMetadata());

            BookEntity book3 = createBook("Book 3");
            book3.getMetadata().setSeriesName("Test Series");
            book3.getMetadata().setSeriesNumber(3f);
            em.merge(book3.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.SERIES_NUMBER, RuleOperator.EQUALS, 2));
            assertThat(ids).contains(book2.getId());
            assertThat(ids).doesNotContain(book3.getId());
        }
    }

    @Nested
    class AudiobookDurationTests {
        @Test
        void greaterThan_matchesLongAudiobook() {
            BookEntity longBook = createBook("Long Audiobook");
            BookFileEntity longFile = BookFileEntity.builder()
                    .book(longBook)
                    .fileName("long.m4b")
                    .fileSubPath("")
                    .isBookFormat(true)
                    .bookType(BookFileType.AUDIOBOOK)
                    .durationSeconds(36000L)
                    .build();
            em.persist(longFile);

            BookEntity shortBook = createBook("Short Audiobook");
            BookFileEntity shortFile = BookFileEntity.builder()
                    .book(shortBook)
                    .fileName("short.m4b")
                    .fileSubPath("")
                    .isBookFormat(true)
                    .bookType(BookFileType.AUDIOBOOK)
                    .durationSeconds(1800L)
                    .build();
            em.persist(shortFile);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.AUDIOBOOK_DURATION, RuleOperator.GREATER_THAN, 7200));
            assertThat(ids).contains(longBook.getId());
            assertThat(ids).doesNotContain(shortBook.getId());
        }

        @Test
        void lessThan_matchesShortAudiobook() {
            BookEntity longBook = createBook("Long Audiobook");
            BookFileEntity longFile = BookFileEntity.builder()
                    .book(longBook)
                    .fileName("long2.m4b")
                    .fileSubPath("")
                    .isBookFormat(true)
                    .bookType(BookFileType.AUDIOBOOK)
                    .durationSeconds(36000L)
                    .build();
            em.persist(longFile);

            BookEntity shortBook = createBook("Short Audiobook");
            BookFileEntity shortFile = BookFileEntity.builder()
                    .book(shortBook)
                    .fileName("short2.m4b")
                    .fileSubPath("")
                    .isBookFormat(true)
                    .bookType(BookFileType.AUDIOBOOK)
                    .durationSeconds(1800L)
                    .build();
            em.persist(shortFile);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.AUDIOBOOK_DURATION, RuleOperator.LESS_THAN, 3600));
            assertThat(ids).contains(shortBook.getId());
            assertThat(ids).doesNotContain(longBook.getId());
        }

        @Test
        void inBetween_matchesMidLengthAudiobook() {
            BookEntity shortBook = createBook("Short");
            BookFileEntity sf = BookFileEntity.builder()
                    .book(shortBook).fileName("s.m4b").fileSubPath("")
                    .isBookFormat(true).bookType(BookFileType.AUDIOBOOK)
                    .durationSeconds(600L).build();
            em.persist(sf);

            BookEntity midBook = createBook("Medium");
            BookFileEntity mf = BookFileEntity.builder()
                    .book(midBook).fileName("m.m4b").fileSubPath("")
                    .isBookFormat(true).bookType(BookFileType.AUDIOBOOK)
                    .durationSeconds(7200L).build();
            em.persist(mf);

            BookEntity longBook = createBook("Long");
            BookFileEntity lf = BookFileEntity.builder()
                    .book(longBook).fileName("l.m4b").fileSubPath("")
                    .isBookFormat(true).bookType(BookFileType.AUDIOBOOK)
                    .durationSeconds(72000L).build();
            em.persist(lf);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.AUDIOBOOK_DURATION, RuleOperator.IN_BETWEEN, null, 3600, 36000));
            assertThat(ids).contains(midBook.getId());
            assertThat(ids).doesNotContain(shortBook.getId(), longBook.getId());
        }
    }

    @Nested
    class HardcoverReviewCountTests {
        @Test
        void greaterThanEqual_matchesBoundary() {
            BookEntity exact = createBook("Exact Reviews");
            exact.getMetadata().setHardcoverReviewCount(100);
            em.merge(exact.getMetadata());

            BookEntity below = createBook("Below Reviews");
            below.getMetadata().setHardcoverReviewCount(99);
            em.merge(below.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.HARDCOVER_REVIEW_COUNT, RuleOperator.GREATER_THAN_EQUAL_TO, 100));
            assertThat(ids).contains(exact.getId());
            assertThat(ids).doesNotContain(below.getId());
        }
    }

    @Nested
    class AudibleReviewCountTests {
        @Test
        void lessThanEqual_matchesLowCount() {
            BookEntity few = createBook("Few Reviews");
            few.getMetadata().setAudibleReviewCount(10);
            em.merge(few.getMetadata());

            BookEntity many = createBook("Many Reviews");
            many.getMetadata().setAudibleReviewCount(5000);
            em.merge(many.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.AUDIBLE_REVIEW_COUNT, RuleOperator.LESS_THAN_EQUAL_TO, 50));
            assertThat(ids).contains(few.getId());
            assertThat(ids).doesNotContain(many.getId());
        }
    }

    @Nested
    class SeriesTotalTests {
        @Test
        void greaterThan_matchesLongSeries() {
            BookEntity longSeries = createBook("Long Series Book");
            longSeries.getMetadata().setSeriesName("Long Series");
            longSeries.getMetadata().setSeriesTotal(20);
            em.merge(longSeries.getMetadata());

            BookEntity shortSeries = createBook("Short Series Book");
            shortSeries.getMetadata().setSeriesName("Short Series");
            shortSeries.getMetadata().setSeriesTotal(3);
            em.merge(shortSeries.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.SERIES_TOTAL, RuleOperator.GREATER_THAN, 10));
            assertThat(ids).contains(longSeries.getId());
            assertThat(ids).doesNotContain(shortSeries.getId());
        }
    }
}
