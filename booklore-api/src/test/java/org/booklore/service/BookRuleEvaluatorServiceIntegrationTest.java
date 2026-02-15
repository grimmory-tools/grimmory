package org.booklore.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.booklore.BookloreApplication;
import org.booklore.model.dto.*;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.ComicCreatorRole;
import org.booklore.model.enums.ReadStatus;
import org.booklore.repository.BookRepository;
import org.booklore.service.task.TaskCronService;
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
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SpringBootTest(classes = {BookloreApplication.class})
@Transactional
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:ruleeval;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.path-config=build/tmp/test-config",
        "app.bookdrop-folder=build/tmp/test-bookdrop",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.task.scheduling.enabled=false",
        "app.task.scan-library-cron=*/1 * * * * *",
        "app.task.process-bookdrop-cron=*/1 * * * * *",
        "app.features.oidc-enabled=false"
})
@Import(BookRuleEvaluatorServiceIntegrationTest.TestConfig.class)
class BookRuleEvaluatorServiceIntegrationTest {

    @TestConfiguration
    static class TestConfig {
        @Bean("flyway")
        @Primary
        public org.flywaydb.core.Flyway flyway() {
            return mock(org.flywaydb.core.Flyway.class);
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
        return createBook(title, null, null, null);
    }

    private BookEntity createBook(String title, String seriesName, Float seriesNumber, Integer seriesTotal) {
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
                .seriesName(seriesName)
                .seriesNumber(seriesNumber)
                .seriesTotal(seriesTotal)
                .build();
        em.persist(metadata);
        em.flush();

        book.setMetadata(metadata);
        return book;
    }

    private UserBookProgressEntity createProgress(BookEntity book, ReadStatus status) {
        return createProgress(book, status, null, null);
    }

    private UserBookProgressEntity createProgress(BookEntity book, ReadStatus status, Float koreaderPercent, Instant dateFinished) {
        UserBookProgressEntity progress = UserBookProgressEntity.builder()
                .user(user)
                .book(book)
                .readStatus(status)
                .koreaderProgressPercent(koreaderPercent)
                .dateFinished(dateFinished)
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
    class ReadingProgressTests {
        @Test
        void greaterThan_matchesBookWithHighProgress() {
            BookEntity book = createBook("High Progress Book");
            createProgress(book, ReadStatus.READING, 75f, null);

            BookEntity lowBook = createBook("Low Progress Book");
            createProgress(lowBook, ReadStatus.READING, 20f, null);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.READING_PROGRESS, RuleOperator.GREATER_THAN, 50));
            assertThat(ids).contains(book.getId());
            assertThat(ids).doesNotContain(lowBook.getId());
        }

        @Test
        void lessThan_matchesBookWithLowProgress() {
            BookEntity book = createBook("Low Progress Book");
            createProgress(book, ReadStatus.READING, 20f, null);

            BookEntity highBook = createBook("High Progress Book");
            createProgress(highBook, ReadStatus.READING, 80f, null);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.READING_PROGRESS, RuleOperator.LESS_THAN, 50));
            assertThat(ids).contains(book.getId());
            assertThat(ids).doesNotContain(highBook.getId());
        }
    }

    @Nested
    class RelativeDateTests {
        @Test
        void withinLast_matchesRecentBook() {
            BookEntity recent = createBook("Recent Book");
            recent.setAddedOn(Instant.now().minus(2, ChronoUnit.DAYS));
            em.merge(recent);

            BookEntity old = createBook("Old Book");
            old.setAddedOn(Instant.now().minus(30, ChronoUnit.DAYS));
            em.merge(old);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.ADDED_ON, RuleOperator.WITHIN_LAST, 7, null, "days"));
            assertThat(ids).contains(recent.getId());
            assertThat(ids).doesNotContain(old.getId());
        }

        @Test
        void olderThan_matchesOldBook() {
            BookEntity old = createBook("Old Book");
            old.setAddedOn(Instant.now().minus(60, ChronoUnit.DAYS));
            em.merge(old);

            BookEntity recent = createBook("Recent Book");
            recent.setAddedOn(Instant.now().minus(2, ChronoUnit.DAYS));
            em.merge(recent);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.ADDED_ON, RuleOperator.OLDER_THAN, 30, null, "days"));
            assertThat(ids).contains(old.getId());
            assertThat(ids).doesNotContain(recent.getId());
        }

        @Test
        void thisPeriod_year_matchesThisYearBook() {
            BookEntity thisYear = createBook("This Year Book");
            thisYear.setAddedOn(Instant.now().minus(10, ChronoUnit.DAYS));
            em.merge(thisYear);

            BookEntity lastYear = createBook("Last Year Book");
            lastYear.setAddedOn(Instant.now().minus(400, ChronoUnit.DAYS));
            em.merge(lastYear);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.ADDED_ON, RuleOperator.THIS_PERIOD, "year"));
            assertThat(ids).contains(thisYear.getId());
            assertThat(ids).doesNotContain(lastYear.getId());
        }

        @Test
        void withinLast_months_matchesRecentBook() {
            BookEntity recent = createBook("Two Months Ago");
            recent.setAddedOn(Instant.now().minus(50, ChronoUnit.DAYS));
            em.merge(recent);

            BookEntity old = createBook("Six Months Ago");
            old.setAddedOn(Instant.now().minus(200, ChronoUnit.DAYS));
            em.merge(old);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.ADDED_ON, RuleOperator.WITHIN_LAST, 3, null, "months"));
            assertThat(ids).contains(recent.getId());
            assertThat(ids).doesNotContain(old.getId());
        }
    }

    @Nested
    class SeriesStatusTests {
        @Test
        void fullyRead_matchesSeriesWhereAllBooksRead() {
            BookEntity b1 = createBook("FullRead S1", "FullRead Series", 1f, 2);
            BookEntity b2 = createBook("FullRead S2", "FullRead Series", 2f, 2);
            createProgress(b1, ReadStatus.READ);
            createProgress(b2, ReadStatus.READ);

            BookEntity u1 = createBook("Unread S1", "Unread Series", 1f, 2);
            BookEntity u2 = createBook("Unread S2", "Unread Series", 2f, 2);
            createProgress(u1, ReadStatus.UNREAD);
            createProgress(u2, ReadStatus.UNREAD);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.SERIES_STATUS, RuleOperator.EQUALS, "fully_read"));
            assertThat(ids).contains(b1.getId(), b2.getId());
            assertThat(ids).doesNotContain(u1.getId(), u2.getId());
        }

        @Test
        void notStarted_matchesSeriesWithNoProgress() {
            BookEntity u1 = createBook("NotStarted S1", "Fresh Series", 1f, 2);
            BookEntity u2 = createBook("NotStarted S2", "Fresh Series", 2f, 2);
            createProgress(u1, ReadStatus.UNREAD);
            createProgress(u2, ReadStatus.UNREAD);

            BookEntity r1 = createBook("Started S1", "Started Series", 1f, 2);
            createProgress(r1, ReadStatus.READING);
            BookEntity r2 = createBook("Started S2", "Started Series", 2f, 2);
            createProgress(r2, ReadStatus.UNREAD);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.SERIES_STATUS, RuleOperator.EQUALS, "not_started"));
            assertThat(ids).contains(u1.getId(), u2.getId());
            assertThat(ids).doesNotContain(r1.getId(), r2.getId());
        }

        @Test
        void reading_matchesSeriesWithActiveReading() {
            BookEntity b1 = createBook("Active S1", "Active Series", 1f, 3);
            BookEntity b2 = createBook("Active S2", "Active Series", 2f, 3);
            createProgress(b1, ReadStatus.READ);
            createProgress(b2, ReadStatus.READING);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.SERIES_STATUS, RuleOperator.EQUALS, "reading"));
            assertThat(ids).contains(b1.getId(), b2.getId());
        }

        @Test
        void completed_matchesSeriesOwningLastBook() {
            BookEntity b1 = createBook("Complete S1", "Complete Series", 1f, 3);
            BookEntity b3 = createBook("Complete S3", "Complete Series", 3f, 3);

            BookEntity i1 = createBook("Incomplete S1", "Incomplete Series", 1f, 5);
            BookEntity i2 = createBook("Incomplete S2", "Incomplete Series", 2f, 5);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.SERIES_STATUS, RuleOperator.EQUALS, "completed"));
            assertThat(ids).contains(b1.getId(), b3.getId());
            assertThat(ids).doesNotContain(i1.getId(), i2.getId());
        }
    }

    @Nested
    class SeriesGapsTests {
        @Test
        void anyGap_matchesSeriesWithMissingNumbers() {
            BookEntity g1 = createBook("Gap S1", "Gap Series", 1f, 4);
            BookEntity g3 = createBook("Gap S3", "Gap Series", 3f, 4);

            BookEntity n1 = createBook("NoGap S1", "NoGap Series", 1f, 2);
            BookEntity n2 = createBook("NoGap S2", "NoGap Series", 2f, 2);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.SERIES_GAPS, RuleOperator.EQUALS, "any_gap"));
            assertThat(ids).contains(g1.getId(), g3.getId());
            assertThat(ids).doesNotContain(n1.getId(), n2.getId());
        }

        @Test
        void missingFirst_matchesSeriesWithoutBookOne() {
            BookEntity m2 = createBook("MissFirst S2", "MissFirst Series", 2f, 3);
            BookEntity m3 = createBook("MissFirst S3", "MissFirst Series", 3f, 3);

            BookEntity h1 = createBook("HasFirst S1", "HasFirst Series", 1f, 2);
            BookEntity h2 = createBook("HasFirst S2", "HasFirst Series", 2f, 2);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.SERIES_GAPS, RuleOperator.EQUALS, "missing_first"));
            assertThat(ids).contains(m2.getId(), m3.getId());
            assertThat(ids).doesNotContain(h1.getId(), h2.getId());
        }

        @Test
        void duplicateNumber_matchesSeriesWithDuplicates() {
            BookEntity d1a = createBook("Dup S1a", "Dup Series", 1f, 3);
            BookEntity d1b = createBook("Dup S1b", "Dup Series", 1f, 3);
            BookEntity d2 = createBook("Dup S2", "Dup Series", 2f, 3);

            BookEntity u1 = createBook("Unique S1", "Unique Series", 1f, 2);
            BookEntity u2 = createBook("Unique S2", "Unique Series", 2f, 2);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.SERIES_GAPS, RuleOperator.EQUALS, "duplicate_number"));
            assertThat(ids).contains(d1a.getId(), d1b.getId(), d2.getId());
            assertThat(ids).doesNotContain(u1.getId(), u2.getId());
        }
    }

    @Nested
    class SeriesPositionTests {
        @Test
        void firstInSeries_matchesLowestNumberedBook() {
            BookEntity b1 = createBook("Pos S1", "Pos Series", 1f, 3);
            BookEntity b2 = createBook("Pos S2", "Pos Series", 2f, 3);
            BookEntity b3 = createBook("Pos S3", "Pos Series", 3f, 3);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.SERIES_POSITION, RuleOperator.EQUALS, "first_in_series"));
            assertThat(ids).contains(b1.getId());
            assertThat(ids).doesNotContain(b2.getId(), b3.getId());
        }

        @Test
        void lastInSeries_matchesHighestNumberedBook() {
            BookEntity b1 = createBook("Last S1", "Last Series", 1f, 3);
            BookEntity b2 = createBook("Last S2", "Last Series", 2f, 3);
            BookEntity b3 = createBook("Last S3", "Last Series", 3f, 3);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.SERIES_POSITION, RuleOperator.EQUALS, "last_in_series"));
            assertThat(ids).contains(b3.getId());
            assertThat(ids).doesNotContain(b1.getId(), b2.getId());
        }

        @Test
        void nextUnread_matchesFirstUnreadAfterReadBook() {
            BookEntity b1 = createBook("Next S1", "Next Series", 1f, 3);
            BookEntity b2 = createBook("Next S2", "Next Series", 2f, 3);
            BookEntity b3 = createBook("Next S3", "Next Series", 3f, 3);
            createProgress(b1, ReadStatus.READ);
            createProgress(b2, ReadStatus.UNREAD);
            createProgress(b3, ReadStatus.UNREAD);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.SERIES_POSITION, RuleOperator.EQUALS, "next_unread"));
            assertThat(ids).contains(b2.getId());
            assertThat(ids).doesNotContain(b1.getId(), b3.getId());
        }
    }

    @Nested
    class NegationTests {
        @Test
        void seriesStatus_notEquals_excludesMatching() {
            BookEntity b1 = createBook("FullRead N1", "FullRead Neg", 1f, 2);
            BookEntity b2 = createBook("FullRead N2", "FullRead Neg", 2f, 2);
            createProgress(b1, ReadStatus.READ);
            createProgress(b2, ReadStatus.READ);

            BookEntity u1 = createBook("NotRead N1", "NotRead Neg", 1f, 2);
            BookEntity u2 = createBook("NotRead N2", "NotRead Neg", 2f, 2);
            createProgress(u1, ReadStatus.UNREAD);
            createProgress(u2, ReadStatus.UNREAD);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.SERIES_STATUS, RuleOperator.NOT_EQUALS, "fully_read"));
            assertThat(ids).contains(u1.getId(), u2.getId());
            assertThat(ids).doesNotContain(b1.getId(), b2.getId());
        }
    }

    @Nested
    class ReadingProgressEdgeCases {
        @Test
        void inBetween_matchesBooksInRange() {
            BookEntity low = createBook("Low Progress");
            createProgress(low, ReadStatus.READING, 10f, null);

            BookEntity mid = createBook("Mid Progress");
            createProgress(mid, ReadStatus.READING, 50f, null);

            BookEntity high = createBook("High Progress");
            createProgress(high, ReadStatus.READING, 90f, null);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.READING_PROGRESS, RuleOperator.IN_BETWEEN, null, 30, 70));
            assertThat(ids).contains(mid.getId());
            assertThat(ids).doesNotContain(low.getId(), high.getId());
        }

        @Test
        void equals_matchesExactProgress() {
            BookEntity book = createBook("Exact Progress");
            createProgress(book, ReadStatus.READING, 50f, null);
            em.flush();
            em.clear();

            List<Long> matchIds = findMatchingIds(singleRule(RuleField.READING_PROGRESS, RuleOperator.EQUALS, 50));
            assertThat(matchIds).contains(book.getId());

            List<Long> noMatchIds = findMatchingIds(singleRule(RuleField.READING_PROGRESS, RuleOperator.EQUALS, 60));
            assertThat(noMatchIds).doesNotContain(book.getId());
        }

        @Test
        void greaterThanEqualTo_matchesBoundary() {
            BookEntity book = createBook("Boundary Book");
            createProgress(book, ReadStatus.READING, 50f, null);
            em.flush();
            em.clear();

            List<Long> matchIds = findMatchingIds(singleRule(RuleField.READING_PROGRESS, RuleOperator.GREATER_THAN_EQUAL_TO, 50));
            assertThat(matchIds).contains(book.getId());

            List<Long> noMatchIds = findMatchingIds(singleRule(RuleField.READING_PROGRESS, RuleOperator.GREATER_THAN_EQUAL_TO, 51));
            assertThat(noMatchIds).doesNotContain(book.getId());
        }

        @Test
        void lessThanEqualTo_matchesBoundary() {
            BookEntity book = createBook("Boundary Book");
            createProgress(book, ReadStatus.READING, 50f, null);
            em.flush();
            em.clear();

            List<Long> matchIds = findMatchingIds(singleRule(RuleField.READING_PROGRESS, RuleOperator.LESS_THAN_EQUAL_TO, 50));
            assertThat(matchIds).contains(book.getId());

            List<Long> noMatchIds = findMatchingIds(singleRule(RuleField.READING_PROGRESS, RuleOperator.LESS_THAN_EQUAL_TO, 49));
            assertThat(noMatchIds).doesNotContain(book.getId());
        }

        @Test
        void noProgressRecord_treatedAsZero() {
            BookEntity book = createBook("No Progress Book");
            // No progress entity created
            em.flush();
            em.clear();

            List<Long> zeroIds = findMatchingIds(singleRule(RuleField.READING_PROGRESS, RuleOperator.EQUALS, 0));
            assertThat(zeroIds).contains(book.getId());

            List<Long> gtIds = findMatchingIds(singleRule(RuleField.READING_PROGRESS, RuleOperator.GREATER_THAN, 0));
            assertThat(gtIds).doesNotContain(book.getId());
        }

        @Test
        void multipleProgressSources_usesGreatest() {
            BookEntity book = createBook("Multi Source Book");
            UserBookProgressEntity progress = createProgress(book, ReadStatus.READING, 30f, null);
            progress.setKoboProgressPercent(70f);
            em.merge(progress);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.READING_PROGRESS, RuleOperator.GREATER_THAN, 50));
            assertThat(ids).contains(book.getId());
        }
    }

    @Nested
    class RelativeDateEdgeCases {
        @Test
        void withinLast_weeks_matchesRecentBook() {
            BookEntity recent = createBook("One Week Ago");
            recent.setAddedOn(Instant.now().minus(7, ChronoUnit.DAYS));
            em.merge(recent);

            BookEntity old = createBook("Twenty Days Ago");
            old.setAddedOn(Instant.now().minus(20, ChronoUnit.DAYS));
            em.merge(old);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.ADDED_ON, RuleOperator.WITHIN_LAST, 2, null, "weeks"));
            assertThat(ids).contains(recent.getId());
            assertThat(ids).doesNotContain(old.getId());
        }

        @Test
        void withinLast_years_matchesRecentBook() {
            BookEntity recent = createBook("Six Months Ago");
            recent.setAddedOn(Instant.now().minus(180, ChronoUnit.DAYS));
            em.merge(recent);

            BookEntity old = createBook("Two Years Ago");
            old.setAddedOn(Instant.now().minus(730, ChronoUnit.DAYS));
            em.merge(old);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.ADDED_ON, RuleOperator.WITHIN_LAST, 1, null, "years"));
            assertThat(ids).contains(recent.getId());
            assertThat(ids).doesNotContain(old.getId());
        }

        @Test
        void olderThan_months_matchesOldBook() {
            BookEntity old = createBook("Four Months Ago");
            old.setAddedOn(Instant.now().minus(120, ChronoUnit.DAYS));
            em.merge(old);

            BookEntity recent = createBook("One Month Ago");
            recent.setAddedOn(Instant.now().minus(25, ChronoUnit.DAYS));
            em.merge(recent);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.ADDED_ON, RuleOperator.OLDER_THAN, 3, null, "months"));
            assertThat(ids).contains(old.getId());
            assertThat(ids).doesNotContain(recent.getId());
        }

        @Test
        void olderThan_years_matchesOldBook() {
            BookEntity old = createBook("Two Years Ago");
            old.setAddedOn(Instant.now().minus(730, ChronoUnit.DAYS));
            em.merge(old);

            BookEntity recent = createBook("Six Months Ago");
            recent.setAddedOn(Instant.now().minus(180, ChronoUnit.DAYS));
            em.merge(recent);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.ADDED_ON, RuleOperator.OLDER_THAN, 1, null, "years"));
            assertThat(ids).contains(old.getId());
            assertThat(ids).doesNotContain(recent.getId());
        }

        @Test
        void thisPeriod_month_matchesThisMonthBook() {
            BookEntity thisMonth = createBook("This Month Book");
            thisMonth.setAddedOn(Instant.now().minus(1, ChronoUnit.DAYS));
            em.merge(thisMonth);

            BookEntity notThisMonth = createBook("Not This Month Book");
            notThisMonth.setAddedOn(Instant.now().minus(60, ChronoUnit.DAYS));
            em.merge(notThisMonth);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.ADDED_ON, RuleOperator.THIS_PERIOD, "month"));
            assertThat(ids).contains(thisMonth.getId());
            assertThat(ids).doesNotContain(notThisMonth.getId());
        }

        @Test
        void dateFinished_withinLast_matchesRecentlyFinished() {
            BookEntity book = createBook("Recently Finished");
            createProgress(book, ReadStatus.READ, null, Instant.now().minus(2, ChronoUnit.DAYS));
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.DATE_FINISHED, RuleOperator.WITHIN_LAST, 7, null, "days"));
            assertThat(ids).contains(book.getId());
        }

        @Test
        void dateFinished_olderThan_matchesOldFinish() {
            BookEntity book = createBook("Old Finish");
            createProgress(book, ReadStatus.READ, null, Instant.now().minus(60, ChronoUnit.DAYS));
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.DATE_FINISHED, RuleOperator.OLDER_THAN, 30, null, "days"));
            assertThat(ids).contains(book.getId());
        }

        @Test
        void publishedDate_withinLast_matchesRecentlyPublished() {
            BookEntity book = createBook("Recent Publish");
            BookMetadataEntity metadata = book.getMetadata();
            metadata.setPublishedDate(LocalDate.now().minusDays(10));
            em.merge(metadata);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.PUBLISHED_DATE, RuleOperator.WITHIN_LAST, 30, null, "days"));
            assertThat(ids).contains(book.getId());
        }

        @Test
        void nullValue_returnsAllBooks() {
            BookEntity book = createBook("Any Book");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.ADDED_ON, RuleOperator.WITHIN_LAST, null, null, "days"));
            assertThat(ids).contains(book.getId());
        }
    }

    @Nested
    class SeriesStatusEdgeCases {
        @Test
        void ongoing_matchesSeriesWithTotalButMissingLast() {
            // Ongoing: total=3, books #1 and #2 but no #3
            BookEntity o1 = createBook("Ongoing S1", "Ongoing Series", 1f, 3);
            BookEntity o2 = createBook("Ongoing S2", "Ongoing Series", 2f, 3);

            // Not ongoing: total=2, books #1 and #2 (complete)
            BookEntity c1 = createBook("Complete S1", "Complete Series2", 1f, 2);
            BookEntity c2 = createBook("Complete S2", "Complete Series2", 2f, 2);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.SERIES_STATUS, RuleOperator.EQUALS, "ongoing"));
            assertThat(ids).contains(o1.getId(), o2.getId());
            assertThat(ids).doesNotContain(c1.getId(), c2.getId());
        }

        @Test
        void booksWithoutSeries_neverMatchSeriesStatus() {
            BookEntity noSeries = createBook("No Series Book");
            em.flush();
            em.clear();

            List<Long> readingIds = findMatchingIds(singleRule(RuleField.SERIES_STATUS, RuleOperator.EQUALS, "reading"));
            assertThat(readingIds).doesNotContain(noSeries.getId());

            List<Long> fullyReadIds = findMatchingIds(singleRule(RuleField.SERIES_STATUS, RuleOperator.EQUALS, "fully_read"));
            assertThat(fullyReadIds).doesNotContain(noSeries.getId());
        }

        @Test
        void emptyStringSeriesName_neverMatchSeriesStatus() {
            BookEntity emptySeriesBook = createBook("Empty Series Book", "", 1f, 2);
            em.flush();
            em.clear();

            List<Long> readingIds = findMatchingIds(singleRule(RuleField.SERIES_STATUS, RuleOperator.EQUALS, "reading"));
            assertThat(readingIds).doesNotContain(emptySeriesBook.getId());

            List<Long> fullyReadIds = findMatchingIds(singleRule(RuleField.SERIES_STATUS, RuleOperator.EQUALS, "fully_read"));
            assertThat(fullyReadIds).doesNotContain(emptySeriesBook.getId());
        }

        @Test
        void reReading_countsAsReading() {
            BookEntity b1 = createBook("ReRead S1", "ReRead Series", 1f, 2);
            BookEntity b2 = createBook("ReRead S2", "ReRead Series", 2f, 2);
            createProgress(b1, ReadStatus.RE_READING);
            createProgress(b2, ReadStatus.UNREAD);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.SERIES_STATUS, RuleOperator.EQUALS, "reading"));
            assertThat(ids).contains(b1.getId(), b2.getId());
        }
    }

    @Nested
    class SeriesGapsEdgeCases {
        @Test
        void missingLatest_matchesSeriesMissingLastBook() {
            // Missing latest: total=3, books #1 and #2 only
            BookEntity m1 = createBook("MissLatest S1", "MissLatest Series", 1f, 3);
            BookEntity m2 = createBook("MissLatest S2", "MissLatest Series", 2f, 3);

            // Not missing latest: total=2, books #1 and #2
            BookEntity c1 = createBook("HasLatest S1", "HasLatest Series", 1f, 2);
            BookEntity c2 = createBook("HasLatest S2", "HasLatest Series", 2f, 2);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.SERIES_GAPS, RuleOperator.EQUALS, "missing_latest"));
            assertThat(ids).contains(m1.getId(), m2.getId());
            assertThat(ids).doesNotContain(c1.getId(), c2.getId());
        }

        @Test
        void noGaps_doesNotMatch() {
            BookEntity n1 = createBook("NoGap S1", "Consecutive Series", 1f, 3);
            BookEntity n2 = createBook("NoGap S2", "Consecutive Series", 2f, 3);
            BookEntity n3 = createBook("NoGap S3", "Consecutive Series", 3f, 3);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.SERIES_GAPS, RuleOperator.EQUALS, "any_gap"));
            assertThat(ids).doesNotContain(n1.getId(), n2.getId(), n3.getId());
        }
    }

    @Nested
    class SeriesPositionEdgeCases {
        @Test
        void nextUnread_noReadBooks_doesNotMatch() {
            BookEntity b1 = createBook("AllUnread S1", "AllUnread Series", 1f, 3);
            BookEntity b2 = createBook("AllUnread S2", "AllUnread Series", 2f, 3);
            BookEntity b3 = createBook("AllUnread S3", "AllUnread Series", 3f, 3);
            createProgress(b1, ReadStatus.UNREAD);
            createProgress(b2, ReadStatus.UNREAD);
            createProgress(b3, ReadStatus.UNREAD);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.SERIES_POSITION, RuleOperator.EQUALS, "next_unread"));
            assertThat(ids).doesNotContain(b1.getId(), b2.getId(), b3.getId());
        }

        @Test
        void nextUnread_allBooksRead_doesNotMatch() {
            BookEntity b1 = createBook("AllRead S1", "AllRead Series", 1f, 3);
            BookEntity b2 = createBook("AllRead S2", "AllRead Series", 2f, 3);
            BookEntity b3 = createBook("AllRead S3", "AllRead Series", 3f, 3);
            createProgress(b1, ReadStatus.READ);
            createProgress(b2, ReadStatus.READ);
            createProgress(b3, ReadStatus.READ);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.SERIES_POSITION, RuleOperator.EQUALS, "next_unread"));
            assertThat(ids).doesNotContain(b1.getId(), b2.getId(), b3.getId());
        }

        @Test
        void firstInSeries_fractionalNumbers() {
            BookEntity b05 = createBook("Frac S0.5", "Frac Series", 0.5f, 3);
            BookEntity b1 = createBook("Frac S1", "Frac Series", 1f, 3);
            BookEntity b2 = createBook("Frac S2", "Frac Series", 2f, 3);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.SERIES_POSITION, RuleOperator.EQUALS, "first_in_series"));
            assertThat(ids).contains(b05.getId());
            assertThat(ids).doesNotContain(b1.getId(), b2.getId());
        }

        @Test
        void positionFilters_requireSeriesNumber() {
            BookEntity noNumber = createBook("No Number", "NoNum Series", null, 3);
            em.flush();
            em.clear();

            List<Long> firstIds = findMatchingIds(singleRule(RuleField.SERIES_POSITION, RuleOperator.EQUALS, "first_in_series"));
            assertThat(firstIds).doesNotContain(noNumber.getId());

            List<Long> lastIds = findMatchingIds(singleRule(RuleField.SERIES_POSITION, RuleOperator.EQUALS, "last_in_series"));
            assertThat(lastIds).doesNotContain(noNumber.getId());

            List<Long> nextIds = findMatchingIds(singleRule(RuleField.SERIES_POSITION, RuleOperator.EQUALS, "next_unread"));
            assertThat(nextIds).doesNotContain(noNumber.getId());
        }
    }

    @Nested
    class GroupLogicTests {
        @Test
        void andJoin_requiresBothRulesToMatch() {
            BookEntity matchBoth = createBook("Both Match");
            matchBoth.getMetadata().setPageCount(200);
            em.merge(matchBoth.getMetadata());
            matchBoth.setMetadataMatchScore(70f);
            em.merge(matchBoth);

            BookEntity matchOne = createBook("One Match");
            matchOne.getMetadata().setPageCount(200);
            em.merge(matchOne.getMetadata());
            matchOne.setMetadataMatchScore(30f);
            em.merge(matchOne);
            em.flush();
            em.clear();

            Rule rule1 = new Rule();
            rule1.setField(RuleField.PAGE_COUNT);
            rule1.setOperator(RuleOperator.GREATER_THAN);
            rule1.setValue(100);

            Rule rule2 = new Rule();
            rule2.setField(RuleField.METADATA_SCORE);
            rule2.setOperator(RuleOperator.GREATER_THAN);
            rule2.setValue(50);

            GroupRule group = new GroupRule();
            group.setJoin(JoinType.AND);
            group.setRules(List.of(rule1, rule2));

            List<Long> ids = findMatchingIds(group);
            assertThat(ids).contains(matchBoth.getId());
            assertThat(ids).doesNotContain(matchOne.getId());
        }

        @Test
        void orJoin_requiresEitherRuleToMatch() {
            BookEntity matchFirst = createBook("First Match");
            matchFirst.getMetadata().setPageCount(200);
            em.merge(matchFirst.getMetadata());
            matchFirst.setMetadataMatchScore(30f);
            em.merge(matchFirst);

            BookEntity matchNeither = createBook("Neither Match");
            matchNeither.getMetadata().setPageCount(50);
            em.merge(matchNeither.getMetadata());
            matchNeither.setMetadataMatchScore(30f);
            em.merge(matchNeither);
            em.flush();
            em.clear();

            Rule rule1 = new Rule();
            rule1.setField(RuleField.PAGE_COUNT);
            rule1.setOperator(RuleOperator.GREATER_THAN);
            rule1.setValue(100);

            Rule rule2 = new Rule();
            rule2.setField(RuleField.METADATA_SCORE);
            rule2.setOperator(RuleOperator.GREATER_THAN);
            rule2.setValue(50);

            GroupRule group = new GroupRule();
            group.setJoin(JoinType.OR);
            group.setRules(List.of(rule1, rule2));

            List<Long> ids = findMatchingIds(group);
            assertThat(ids).contains(matchFirst.getId());
            assertThat(ids).doesNotContain(matchNeither.getId());
        }

        @Test
        void nestedGroup_evaluatesCorrectly() {
            // Book that matches outer rule AND inner OR group
            BookEntity matchAll = createBook("Match All");
            matchAll.getMetadata().setPageCount(200);
            matchAll.getMetadata().setLanguage("en");
            em.merge(matchAll.getMetadata());
            matchAll.setMetadataMatchScore(70f);
            em.merge(matchAll);

            // Book that matches outer rule but NOT inner OR group
            BookEntity matchOuterOnly = createBook("Match Outer Only");
            matchOuterOnly.getMetadata().setPageCount(200);
            matchOuterOnly.getMetadata().setLanguage("xx");
            em.merge(matchOuterOnly.getMetadata());
            matchOuterOnly.setMetadataMatchScore(30f);
            em.merge(matchOuterOnly);
            em.flush();
            em.clear();

            // Outer rule: pageCount > 100
            Rule outerRule = new Rule();
            outerRule.setField(RuleField.PAGE_COUNT);
            outerRule.setOperator(RuleOperator.GREATER_THAN);
            outerRule.setValue(100);

            // Inner OR group: metadataScore > 50 OR language = "en"
            Rule innerRule1 = new Rule();
            innerRule1.setField(RuleField.METADATA_SCORE);
            innerRule1.setOperator(RuleOperator.GREATER_THAN);
            innerRule1.setValue(50);

            Rule innerRule2 = new Rule();
            innerRule2.setField(RuleField.LANGUAGE);
            innerRule2.setOperator(RuleOperator.EQUALS);
            innerRule2.setValue("en");

            GroupRule innerGroup = new GroupRule();
            innerGroup.setType("group");
            innerGroup.setJoin(JoinType.OR);
            innerGroup.setRules(List.of(innerRule1, innerRule2));

            GroupRule outerGroup = new GroupRule();
            outerGroup.setJoin(JoinType.AND);
            outerGroup.setRules(List.of(outerRule, innerGroup));

            List<Long> ids = findMatchingIds(outerGroup);
            assertThat(ids).contains(matchAll.getId());
            assertThat(ids).doesNotContain(matchOuterOnly.getId());
        }

        @Test
        void emptyGroupRules_matchesAllBooks() {
            BookEntity book1 = createBook("Book One");
            BookEntity book2 = createBook("Book Two");
            em.flush();
            em.clear();

            GroupRule group = new GroupRule();
            group.setJoin(JoinType.AND);
            group.setRules(new ArrayList<>());

            List<Long> ids = findMatchingIds(group);
            assertThat(ids).contains(book1.getId(), book2.getId());
        }
    }

    @Nested
    class StringOperatorTests {
        @Test
        void contains_matchesTitleSubstring() {
            BookEntity hp = createBook("Harry Potter and the Philosopher's Stone");
            BookEntity lotr = createBook("The Lord of the Rings");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.TITLE, RuleOperator.CONTAINS, "potter"));
            assertThat(ids).contains(hp.getId());
            assertThat(ids).doesNotContain(lotr.getId());
        }

        @Test
        void doesNotContain_excludesTitleSubstring() {
            BookEntity hp = createBook("Harry Potter and the Philosopher's Stone");
            BookEntity lotr = createBook("The Lord of the Rings");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.TITLE, RuleOperator.DOES_NOT_CONTAIN, "potter"));
            assertThat(ids).contains(lotr.getId());
            assertThat(ids).doesNotContain(hp.getId());
        }

        @Test
        void startsWith_matchesTitlePrefix() {
            BookEntity hp = createBook("Harry Potter");
            BookEntity hobbit = createBook("The Hobbit");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.TITLE, RuleOperator.STARTS_WITH, "harry"));
            assertThat(ids).contains(hp.getId());
            assertThat(ids).doesNotContain(hobbit.getId());
        }

        @Test
        void endsWith_matchesTitleSuffix() {
            BookEntity hp = createBook("Harry Potter");
            BookEntity hobbit = createBook("The Hobbit");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.TITLE, RuleOperator.ENDS_WITH, "hobbit"));
            assertThat(ids).contains(hobbit.getId());
            assertThat(ids).doesNotContain(hp.getId());
        }

        @Test
        void contains_onArrayField_matchesAuthorName() {
            BookEntity book1 = createBook("Mistborn");
            AuthorEntity author1 = AuthorEntity.builder().name("Brandon Sanderson").build();
            em.persist(author1);
            book1.getMetadata().setAuthors(new HashSet<>(Set.of(author1)));
            em.merge(book1.getMetadata());

            BookEntity book2 = createBook("It");
            AuthorEntity author2 = AuthorEntity.builder().name("Stephen King").build();
            em.persist(author2);
            book2.getMetadata().setAuthors(new HashSet<>(Set.of(author2)));
            em.merge(book2.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.AUTHORS, RuleOperator.CONTAINS, "sanderson"));
            assertThat(ids).contains(book1.getId());
            assertThat(ids).doesNotContain(book2.getId());
        }
    }

    @Nested
    class EmptyOperatorTests {
        @Test
        void isEmpty_matchesBookWithNullPublisher() {
            BookEntity noPublisher = createBook("No Publisher Book");
            // publisher is null by default

            BookEntity withPublisher = createBook("With Publisher Book");
            withPublisher.getMetadata().setPublisher("Penguin");
            em.merge(withPublisher.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.PUBLISHER, RuleOperator.IS_EMPTY, null));
            assertThat(ids).contains(noPublisher.getId());
            assertThat(ids).doesNotContain(withPublisher.getId());
        }

        @Test
        void isNotEmpty_matchesBookWithPublisher() {
            BookEntity noPublisher = createBook("No Publisher Book");

            BookEntity withPublisher = createBook("With Publisher Book");
            withPublisher.getMetadata().setPublisher("Penguin");
            em.merge(withPublisher.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.PUBLISHER, RuleOperator.IS_NOT_EMPTY, null));
            assertThat(ids).contains(withPublisher.getId());
            assertThat(ids).doesNotContain(noPublisher.getId());
        }

        @Test
        void isEmpty_onArrayField_matchesBookWithNoAuthors() {
            BookEntity noAuthors = createBook("No Authors Book");
            // authors is null/empty by default

            BookEntity withAuthors = createBook("With Authors Book");
            AuthorEntity author = AuthorEntity.builder().name("Test Author").build();
            em.persist(author);
            withAuthors.getMetadata().setAuthors(new HashSet<>(Set.of(author)));
            em.merge(withAuthors.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.AUTHORS, RuleOperator.IS_EMPTY, null));
            assertThat(ids).contains(noAuthors.getId());
            assertThat(ids).doesNotContain(withAuthors.getId());
        }

        @Test
        void isNotEmpty_onArrayField_matchesBookWithAuthors() {
            BookEntity noAuthors = createBook("No Authors Book");

            BookEntity withAuthors = createBook("With Authors Book");
            AuthorEntity author = AuthorEntity.builder().name("Test Author 2").build();
            em.persist(author);
            withAuthors.getMetadata().setAuthors(new HashSet<>(Set.of(author)));
            em.merge(withAuthors.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.AUTHORS, RuleOperator.IS_NOT_EMPTY, null));
            assertThat(ids).contains(withAuthors.getId());
            assertThat(ids).doesNotContain(noAuthors.getId());
        }
    }

    @Nested
    class MultiValueOperatorTests {
        @Test
        void includesAny_matchesReadStatusInList() {
            BookEntity readBook = createBook("Read Book");
            createProgress(readBook, ReadStatus.READ);

            BookEntity readingBook = createBook("Reading Book");
            createProgress(readingBook, ReadStatus.READING);

            BookEntity pausedBook = createBook("Paused Book");
            createProgress(pausedBook, ReadStatus.PAUSED);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.READ_STATUS, RuleOperator.INCLUDES_ANY, List.of("READ", "READING")));
            assertThat(ids).contains(readBook.getId(), readingBook.getId());
            assertThat(ids).doesNotContain(pausedBook.getId());
        }

        @Test
        void excludesAll_excludesReadStatusInList() {
            BookEntity readBook = createBook("Read Book");
            createProgress(readBook, ReadStatus.READ);

            BookEntity readingBook = createBook("Reading Book");
            createProgress(readingBook, ReadStatus.READING);

            BookEntity pausedBook = createBook("Paused Book");
            createProgress(pausedBook, ReadStatus.PAUSED);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.READ_STATUS, RuleOperator.EXCLUDES_ALL, List.of("READ", "READING")));
            assertThat(ids).contains(pausedBook.getId());
            assertThat(ids).doesNotContain(readBook.getId(), readingBook.getId());
        }

        @Test
        void includesAny_onArrayField_matchesAnyAuthor() {
            BookEntity bookA = createBook("Book A");
            AuthorEntity authorA = AuthorEntity.builder().name("Author A").build();
            em.persist(authorA);
            bookA.getMetadata().setAuthors(new HashSet<>(Set.of(authorA)));
            em.merge(bookA.getMetadata());

            BookEntity bookB = createBook("Book B");
            AuthorEntity authorB = AuthorEntity.builder().name("Author B").build();
            em.persist(authorB);
            bookB.getMetadata().setAuthors(new HashSet<>(Set.of(authorB)));
            em.merge(bookB.getMetadata());

            BookEntity bookC = createBook("Book C");
            AuthorEntity authorC = AuthorEntity.builder().name("Author C").build();
            em.persist(authorC);
            bookC.getMetadata().setAuthors(new HashSet<>(Set.of(authorC)));
            em.merge(bookC.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.AUTHORS, RuleOperator.INCLUDES_ANY, List.of("Author A", "Author B")));
            assertThat(ids).contains(bookA.getId(), bookB.getId());
            assertThat(ids).doesNotContain(bookC.getId());
        }

        @Test
        void excludesAll_onArrayField_excludesAllAuthors() {
            BookEntity bookA = createBook("Book A2");
            AuthorEntity authorA = AuthorEntity.builder().name("Author A2").build();
            em.persist(authorA);
            bookA.getMetadata().setAuthors(new HashSet<>(Set.of(authorA)));
            em.merge(bookA.getMetadata());

            BookEntity bookB = createBook("Book B2");
            AuthorEntity authorB = AuthorEntity.builder().name("Author B2").build();
            em.persist(authorB);
            bookB.getMetadata().setAuthors(new HashSet<>(Set.of(authorB)));
            em.merge(bookB.getMetadata());

            BookEntity bookC = createBook("Book C2");
            AuthorEntity authorC = AuthorEntity.builder().name("Author C2").build();
            em.persist(authorC);
            bookC.getMetadata().setAuthors(new HashSet<>(Set.of(authorC)));
            em.merge(bookC.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.AUTHORS, RuleOperator.EXCLUDES_ALL, List.of("Author A2", "Author B2")));
            assertThat(ids).contains(bookC.getId());
            assertThat(ids).doesNotContain(bookA.getId(), bookB.getId());
        }

        @Test
        void includesAll_onArrayField_matchesBookWithAllAuthors() {
            BookEntity bookBoth = createBook("Book Both Authors");
            AuthorEntity authorX = AuthorEntity.builder().name("Author X").build();
            AuthorEntity authorY = AuthorEntity.builder().name("Author Y").build();
            em.persist(authorX);
            em.persist(authorY);
            bookBoth.getMetadata().setAuthors(new HashSet<>(Set.of(authorX, authorY)));
            em.merge(bookBoth.getMetadata());

            BookEntity bookOne = createBook("Book One Author");
            AuthorEntity authorX2 = AuthorEntity.builder().name("Author X2").build();
            em.persist(authorX2);
            bookOne.getMetadata().setAuthors(new HashSet<>(Set.of(authorX2)));
            em.merge(bookOne.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.AUTHORS, RuleOperator.INCLUDES_ALL, List.of("Author X", "Author Y")));
            assertThat(ids).contains(bookBoth.getId());
            assertThat(ids).doesNotContain(bookOne.getId());
        }
    }

    @Nested
    class EqualsEdgeCaseTests {
        @Test
        void equals_stringField_caseInsensitive() {
            BookEntity hp = createBook("Harry Potter");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.TITLE, RuleOperator.EQUALS, "harry potter"));
            assertThat(ids).contains(hp.getId());
        }

        @Test
        void equals_booleanField_matchesTrue() {
            BookEntity physical = createBook("Physical Book");
            physical.setIsPhysical(true);
            em.merge(physical);

            BookEntity digital = createBook("Digital Book");
            digital.setIsPhysical(false);
            em.merge(digital);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.IS_PHYSICAL, RuleOperator.EQUALS, "true"));
            assertThat(ids).contains(physical.getId());
            assertThat(ids).doesNotContain(digital.getId());
        }

        @Test
        void equals_booleanField_matchesFalse() {
            BookEntity physical = createBook("Physical Book 2");
            physical.setIsPhysical(true);
            em.merge(physical);

            BookEntity digital = createBook("Digital Book 2");
            digital.setIsPhysical(false);
            em.merge(digital);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.IS_PHYSICAL, RuleOperator.EQUALS, "false"));
            assertThat(ids).contains(digital.getId());
            assertThat(ids).doesNotContain(physical.getId());
        }

        @Test
        void notEquals_stringField_excludesMatch() {
            BookEntity hp = createBook("Harry Potter");
            BookEntity hobbit = createBook("The Hobbit");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.TITLE, RuleOperator.NOT_EQUALS, "harry potter"));
            assertThat(ids).contains(hobbit.getId());
            assertThat(ids).doesNotContain(hp.getId());
        }

        @Test
        void equals_readStatus_matchesSpecificStatus() {
            BookEntity readBook = createBook("Read Book");
            createProgress(readBook, ReadStatus.READ);

            BookEntity readingBook = createBook("Reading Book");
            createProgress(readingBook, ReadStatus.READING);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.READ_STATUS, RuleOperator.EQUALS, "READ"));
            assertThat(ids).contains(readBook.getId());
            assertThat(ids).doesNotContain(readingBook.getId());
        }

        @Test
        void equals_readStatus_unset_matchesNullProgress() {
            BookEntity noProgress = createBook("No Progress Book");
            // No UserBookProgressEntity created

            BookEntity readingBook = createBook("Reading Book");
            createProgress(readingBook, ReadStatus.READING);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.READ_STATUS, RuleOperator.EQUALS, "UNSET"));
            assertThat(ids).contains(noProgress.getId());
            assertThat(ids).doesNotContain(readingBook.getId());
        }
    }

    @Nested
    class DateComparisonTests {
        @Test
        void greaterThan_matchesPublishedDateAfterThreshold() {
            BookEntity recentBook = createBook("Recent Book");
            recentBook.getMetadata().setPublishedDate(LocalDate.of(2024, 6, 15));
            em.merge(recentBook.getMetadata());

            BookEntity oldBook = createBook("Old Book");
            oldBook.getMetadata().setPublishedDate(LocalDate.of(2020, 1, 1));
            em.merge(oldBook.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.PUBLISHED_DATE, RuleOperator.GREATER_THAN, "2023-01-01"));
            assertThat(ids).contains(recentBook.getId());
            assertThat(ids).doesNotContain(oldBook.getId());
        }

        @Test
        void lessThan_matchesPublishedDateBeforeThreshold() {
            BookEntity recentBook = createBook("Recent Book");
            recentBook.getMetadata().setPublishedDate(LocalDate.of(2024, 6, 15));
            em.merge(recentBook.getMetadata());

            BookEntity oldBook = createBook("Old Book");
            oldBook.getMetadata().setPublishedDate(LocalDate.of(2020, 1, 1));
            em.merge(oldBook.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.PUBLISHED_DATE, RuleOperator.LESS_THAN, "2023-01-01"));
            assertThat(ids).contains(oldBook.getId());
            assertThat(ids).doesNotContain(recentBook.getId());
        }

        @Test
        void inBetween_matchesPublishedDateInRange() {
            BookEntity inRange = createBook("In Range Book");
            inRange.getMetadata().setPublishedDate(LocalDate.of(2023, 6, 15));
            em.merge(inRange.getMetadata());

            BookEntity outOfRange = createBook("Out of Range Book");
            outOfRange.getMetadata().setPublishedDate(LocalDate.of(2020, 1, 1));
            em.merge(outOfRange.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.PUBLISHED_DATE, RuleOperator.IN_BETWEEN, null, "2023-01-01", "2024-01-01"));
            assertThat(ids).contains(inRange.getId());
            assertThat(ids).doesNotContain(outOfRange.getId());
        }
    }

    @Nested
    class ReadStatusUnsetTests {
        @Test
        void includesAny_withUnsetAndOtherStatuses() {
            BookEntity noProgress = createBook("No Progress Book");

            BookEntity readBook = createBook("Read Book");
            createProgress(readBook, ReadStatus.READ);

            BookEntity pausedBook = createBook("Paused Book");
            createProgress(pausedBook, ReadStatus.PAUSED);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.READ_STATUS, RuleOperator.INCLUDES_ANY, List.of("UNSET", "READ")));
            assertThat(ids).contains(noProgress.getId(), readBook.getId());
            assertThat(ids).doesNotContain(pausedBook.getId());
        }

        @Test
        void includesAny_withOnlyUnset() {
            BookEntity noProgress = createBook("No Progress Book");

            BookEntity readBook = createBook("Read Book");
            createProgress(readBook, ReadStatus.READ);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.READ_STATUS, RuleOperator.INCLUDES_ANY, List.of("UNSET")));
            assertThat(ids).contains(noProgress.getId());
            assertThat(ids).doesNotContain(readBook.getId());
        }
    }

    @Nested
    class CompositeNegationTests {
        @Test
        void seriesGaps_notEquals_excludesMatching() {
            // Series with gap (books 1, 3 — missing 2)
            BookEntity g1 = createBook("Gap S1", "GapNeg Series", 1f, 4);
            BookEntity g3 = createBook("Gap S3", "GapNeg Series", 3f, 4);

            // Series without gap (books 1, 2 — consecutive)
            BookEntity n1 = createBook("NoGap S1", "NoGapNeg Series", 1f, 2);
            BookEntity n2 = createBook("NoGap S2", "NoGapNeg Series", 2f, 2);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.SERIES_GAPS, RuleOperator.NOT_EQUALS, "any_gap"));
            assertThat(ids).contains(n1.getId(), n2.getId());
            assertThat(ids).doesNotContain(g1.getId(), g3.getId());
        }

        @Test
        void seriesPosition_notEquals_excludesMatching() {
            BookEntity b1 = createBook("Pos S1", "PosNeg Series", 1f, 3);
            BookEntity b2 = createBook("Pos S2", "PosNeg Series", 2f, 3);
            BookEntity b3 = createBook("Pos S3", "PosNeg Series", 3f, 3);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.SERIES_POSITION, RuleOperator.NOT_EQUALS, "first_in_series"));
            assertThat(ids).contains(b2.getId(), b3.getId());
            assertThat(ids).doesNotContain(b1.getId());
        }
    }

    @Nested
    class PublishedDateRelativeTests {
        @Test
        void olderThan_publishedDate_matchesOldBook() {
            BookEntity oldBook = createBook("Old Published Book");
            oldBook.getMetadata().setPublishedDate(LocalDate.now().minusMonths(6));
            em.merge(oldBook.getMetadata());

            BookEntity recentBook = createBook("Recent Published Book");
            recentBook.getMetadata().setPublishedDate(LocalDate.now().minusDays(10));
            em.merge(recentBook.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.PUBLISHED_DATE, RuleOperator.OLDER_THAN, 3, null, "months"));
            assertThat(ids).contains(oldBook.getId());
            assertThat(ids).doesNotContain(recentBook.getId());
        }

        @Test
        void thisPeriod_publishedDate_matchesThisYearBook() {
            BookEntity recentBook = createBook("This Year Published");
            recentBook.getMetadata().setPublishedDate(LocalDate.now().minusDays(10));
            em.merge(recentBook.getMetadata());

            BookEntity oldBook = createBook("Two Years Ago Published");
            oldBook.getMetadata().setPublishedDate(LocalDate.now().minusYears(2));
            em.merge(oldBook.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.PUBLISHED_DATE, RuleOperator.THIS_PERIOD, "year"));
            assertThat(ids).contains(recentBook.getId());
            assertThat(ids).doesNotContain(oldBook.getId());
        }

        @Test
        void thisPeriod_week_matchesThisWeekBook() {
            BookEntity yesterday = createBook("Yesterday Book");
            yesterday.setAddedOn(Instant.now().minus(1, ChronoUnit.DAYS));
            em.merge(yesterday);

            BookEntity twoWeeksAgo = createBook("Two Weeks Ago Book");
            twoWeeksAgo.setAddedOn(Instant.now().minus(14, ChronoUnit.DAYS));
            em.merge(twoWeeksAgo);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.ADDED_ON, RuleOperator.THIS_PERIOD, "week"));
            assertThat(ids).contains(yesterday.getId());
            assertThat(ids).doesNotContain(twoWeeksAgo.getId());
        }
    }


    @Nested
    class MetadataPresenceTests {
        @Test
        void has_stringField_matchesWhenPresent() {
            BookEntity withDesc = createBook("With Description");
            withDesc.getMetadata().setDescription("A great book");
            em.merge(withDesc.getMetadata());

            BookEntity noDesc = createBook("No Description");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "description"));
            assertThat(ids).contains(withDesc.getId());
            assertThat(ids).doesNotContain(noDesc.getId());
        }

        @Test
        void hasNot_stringField_matchesWhenAbsent() {
            BookEntity withDesc = createBook("With Description");
            withDesc.getMetadata().setDescription("A great book");
            em.merge(withDesc.getMetadata());

            BookEntity noDesc = createBook("No Description");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.NOT_EQUALS, "description"));
            assertThat(ids).contains(noDesc.getId());
            assertThat(ids).doesNotContain(withDesc.getId());
        }

        @Test
        void has_emptyStringTreatedAsAbsent() {
            BookEntity emptyDesc = createBook("Empty Description");
            emptyDesc.getMetadata().setDescription("  ");
            em.merge(emptyDesc.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "description"));
            assertThat(ids).doesNotContain(emptyDesc.getId());
        }

        @Test
        void has_numericField_matchesWhenPresent() {
            BookEntity withPages = createBook("With Pages");
            withPages.getMetadata().setPageCount(300);
            em.merge(withPages.getMetadata());

            BookEntity noPages = createBook("No Pages");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "pageCount"));
            assertThat(ids).contains(withPages.getId());
            assertThat(ids).doesNotContain(noPages.getId());
        }

        @Test
        void has_isbn13_matchesWhenPresent() {
            BookEntity withIsbn = createBook("With ISBN");
            withIsbn.getMetadata().setIsbn13("9781234567890");
            em.merge(withIsbn.getMetadata());

            BookEntity noIsbn = createBook("No ISBN");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "isbn13"));
            assertThat(ids).contains(withIsbn.getId());
            assertThat(ids).doesNotContain(noIsbn.getId());
        }

        @Test
        void has_coverImage_matchesWhenPresent() {
            BookEntity withCover = createBook("With Cover");
            withCover.setBookCoverHash("abc123");
            em.merge(withCover);

            BookEntity noCover = createBook("No Cover");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "thumbnailUrl"));
            assertThat(ids).contains(withCover.getId());
            assertThat(ids).doesNotContain(noCover.getId());
        }

        @Test
        void has_authors_matchesWhenPresent() {
            BookEntity withAuthors = createBook("With Authors");
            AuthorEntity author = AuthorEntity.builder().name("Test Author MP").build();
            em.persist(author);
            withAuthors.getMetadata().setAuthors(new HashSet<>(Set.of(author)));
            em.merge(withAuthors.getMetadata());

            BookEntity noAuthors = createBook("No Authors");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "authors"));
            assertThat(ids).contains(withAuthors.getId());
            assertThat(ids).doesNotContain(noAuthors.getId());
        }

        @Test
        void hasNot_authors_matchesWhenAbsent() {
            BookEntity withAuthors = createBook("With Authors");
            AuthorEntity author = AuthorEntity.builder().name("Test Author MP2").build();
            em.persist(author);
            withAuthors.getMetadata().setAuthors(new HashSet<>(Set.of(author)));
            em.merge(withAuthors.getMetadata());

            BookEntity noAuthors = createBook("No Authors");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.NOT_EQUALS, "authors"));
            assertThat(ids).contains(noAuthors.getId());
            assertThat(ids).doesNotContain(withAuthors.getId());
        }

        @Test
        void has_categories_matchesWhenPresent() {
            BookEntity withCats = createBook("With Categories");
            CategoryEntity cat = CategoryEntity.builder().name("Fiction MP").build();
            em.persist(cat);
            withCats.getMetadata().setCategories(new HashSet<>(Set.of(cat)));
            em.merge(withCats.getMetadata());

            BookEntity noCats = createBook("No Categories");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "categories"));
            assertThat(ids).contains(withCats.getId());
            assertThat(ids).doesNotContain(noCats.getId());
        }

        @Test
        void has_rating_matchesWhenPresent() {
            BookEntity withRating = createBook("With Rating");
            withRating.getMetadata().setAmazonRating(4.5);
            em.merge(withRating.getMetadata());

            BookEntity noRating = createBook("No Rating");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "amazonRating"));
            assertThat(ids).contains(withRating.getId());
            assertThat(ids).doesNotContain(noRating.getId());
        }

        @Test
        void has_externalId_matchesWhenPresent() {
            BookEntity withId = createBook("With Goodreads ID");
            withId.getMetadata().setGoodreadsId("12345");
            em.merge(withId.getMetadata());

            BookEntity noId = createBook("No Goodreads ID");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "goodreadsId"));
            assertThat(ids).contains(withId.getId());
            assertThat(ids).doesNotContain(noId.getId());
        }

        @Test
        void has_personalRating_matchesWhenPresent() {
            BookEntity withRating = createBook("With Personal Rating");
            UserBookProgressEntity progress = createProgress(withRating, ReadStatus.READ);
            progress.setPersonalRating(4);
            em.merge(progress);

            BookEntity noRating = createBook("No Personal Rating");
            createProgress(noRating, ReadStatus.READING);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "personalRating"));
            assertThat(ids).contains(withRating.getId());
            assertThat(ids).doesNotContain(noRating.getId());
        }

        @Test
        void has_publishedDate_matchesWhenPresent() {
            BookEntity withDate = createBook("With Published Date");
            withDate.getMetadata().setPublishedDate(LocalDate.of(2023, 1, 1));
            em.merge(withDate.getMetadata());

            BookEntity noDate = createBook("No Published Date");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "publishedDate"));
            assertThat(ids).contains(withDate.getId());
            assertThat(ids).doesNotContain(noDate.getId());
        }

        @Test
        void has_seriesInfo_matchesWhenPresent() {
            BookEntity withSeries = createBook("With Series", "My Series", 1f, 3);

            BookEntity noSeries = createBook("No Series");
            em.flush();
            em.clear();

            List<Long> idsName = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "seriesName"));
            assertThat(idsName).contains(withSeries.getId());
            assertThat(idsName).doesNotContain(noSeries.getId());

            List<Long> idsNumber = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "seriesNumber"));
            assertThat(idsNumber).contains(withSeries.getId());
            assertThat(idsNumber).doesNotContain(noSeries.getId());

            List<Long> idsTotal = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "seriesTotal"));
            assertThat(idsTotal).contains(withSeries.getId());
            assertThat(idsTotal).doesNotContain(noSeries.getId());
        }

        @Test
        void has_narrator_matchesWhenPresent() {
            BookEntity withNarrator = createBook("With Narrator");
            withNarrator.getMetadata().setNarrator("John Smith");
            em.merge(withNarrator.getMetadata());

            BookEntity noNarrator = createBook("No Narrator");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "narrator"));
            assertThat(ids).contains(withNarrator.getId());
            assertThat(ids).doesNotContain(noNarrator.getId());
        }

        @Test
        void unknownField_matchesAll() {
            BookEntity book = createBook("Any Book");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "nonExistentField"));
            assertThat(ids).contains(book.getId());
        }

        @Test
        void has_moods_matchesWhenPresent() {
            BookEntity withMoods = createBook("With Moods");
            MoodEntity mood = MoodEntity.builder().name("Dark MP").build();
            em.persist(mood);
            withMoods.getMetadata().setMoods(new HashSet<>(Set.of(mood)));
            em.merge(withMoods.getMetadata());

            BookEntity noMoods = createBook("No Moods");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "moods"));
            assertThat(ids).contains(withMoods.getId());
            assertThat(ids).doesNotContain(noMoods.getId());
        }

        @Test
        void has_tags_matchesWhenPresent() {
            BookEntity withTags = createBook("With Tags");
            TagEntity tag = TagEntity.builder().name("Favorite MP").build();
            em.persist(tag);
            withTags.getMetadata().setTags(new HashSet<>(Set.of(tag)));
            em.merge(withTags.getMetadata());

            BookEntity noTags = createBook("No Tags");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "tags"));
            assertThat(ids).contains(withTags.getId());
            assertThat(ids).doesNotContain(noTags.getId());
        }

        @Test
        void has_multipleFieldsCombined_andLogic() {
            BookEntity withBoth = createBook("With Both");
            withBoth.getMetadata().setDescription("A book");
            withBoth.getMetadata().setIsbn13("9781234567890");
            em.merge(withBoth.getMetadata());

            BookEntity withOneOnly = createBook("With One Only");
            withOneOnly.getMetadata().setDescription("A book");
            em.merge(withOneOnly.getMetadata());
            em.flush();
            em.clear();

            Rule rule1 = new Rule();
            rule1.setField(RuleField.METADATA_PRESENCE);
            rule1.setOperator(RuleOperator.EQUALS);
            rule1.setValue("description");

            Rule rule2 = new Rule();
            rule2.setField(RuleField.METADATA_PRESENCE);
            rule2.setOperator(RuleOperator.EQUALS);
            rule2.setValue("isbn13");

            GroupRule group = new GroupRule();
            group.setJoin(JoinType.AND);
            group.setRules(List.of(rule1, rule2));

            List<Long> ids = findMatchingIds(group);
            assertThat(ids).contains(withBoth.getId());
            assertThat(ids).doesNotContain(withOneOnly.getId());
        }

        @Test
        void hasNot_multipleFieldsCombined_orLogic() {
            BookEntity missingBoth = createBook("Missing Both");

            BookEntity missingOne = createBook("Missing One");
            missingOne.getMetadata().setDescription("A book");
            em.merge(missingOne.getMetadata());

            BookEntity hasBoth = createBook("Has Both");
            hasBoth.getMetadata().setDescription("A book");
            hasBoth.getMetadata().setPublisher("Penguin");
            em.merge(hasBoth.getMetadata());
            em.flush();
            em.clear();

            Rule rule1 = new Rule();
            rule1.setField(RuleField.METADATA_PRESENCE);
            rule1.setOperator(RuleOperator.NOT_EQUALS);
            rule1.setValue("description");

            Rule rule2 = new Rule();
            rule2.setField(RuleField.METADATA_PRESENCE);
            rule2.setOperator(RuleOperator.NOT_EQUALS);
            rule2.setValue("publisher");

            GroupRule group = new GroupRule();
            group.setJoin(JoinType.OR);
            group.setRules(List.of(rule1, rule2));

            List<Long> ids = findMatchingIds(group);
            assertThat(ids).contains(missingBoth.getId(), missingOne.getId());
            assertThat(ids).doesNotContain(hasBoth.getId());
        }

        @Test
        void has_booleanField_matchesWhenPresent() {
            BookEntity withAbridged = createBook("Abridged Book");
            withAbridged.getMetadata().setAbridged(true);
            em.merge(withAbridged.getMetadata());

            BookEntity noAbridged = createBook("Not Set Abridged");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "abridged"));
            assertThat(ids).contains(withAbridged.getId());
            assertThat(ids).doesNotContain(noAbridged.getId());
        }

        @Test
        void has_booleanField_falseCountsAsPresent() {
            BookEntity abridgedFalse = createBook("Not Abridged");
            abridgedFalse.getMetadata().setAbridged(false);
            em.merge(abridgedFalse.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "abridged"));
            assertThat(ids).contains(abridgedFalse.getId());
        }

        @Test
        void has_ageRating_matchesWhenPresent() {
            BookEntity withAge = createBook("With Age Rating");
            withAge.getMetadata().setAgeRating(16);
            em.merge(withAge.getMetadata());

            BookEntity noAge = createBook("No Age Rating");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "ageRating"));
            assertThat(ids).contains(withAge.getId());
            assertThat(ids).doesNotContain(noAge.getId());
        }

        @Test
        void has_audiobookDuration_matchesWhenPresent() {
            BookEntity withDuration = createBook("Audiobook");
            BookFileEntity audioFile = BookFileEntity.builder()
                    .book(withDuration)
                    .fileName("book.m4b")
                    .fileSubPath("")
                    .isBookFormat(true)
                    .bookType(BookFileType.AUDIOBOOK)
                    .durationSeconds(3600L)
                    .build();
            em.persist(audioFile);

            BookEntity noDuration = createBook("No Duration");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "audiobookDuration"));
            assertThat(ids).contains(withDuration.getId());
            assertThat(ids).doesNotContain(noDuration.getId());
        }

        @Test
        void hasNot_audiobookDuration_matchesWhenAbsent() {
            BookEntity withDuration = createBook("Audiobook");
            BookFileEntity audioFile = BookFileEntity.builder()
                    .book(withDuration)
                    .fileName("book2.m4b")
                    .fileSubPath("")
                    .isBookFormat(true)
                    .bookType(BookFileType.AUDIOBOOK)
                    .durationSeconds(3600L)
                    .build();
            em.persist(audioFile);

            BookEntity noDuration = createBook("No Duration");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.NOT_EQUALS, "audiobookDuration"));
            assertThat(ids).contains(noDuration.getId());
            assertThat(ids).doesNotContain(withDuration.getId());
        }

        @Test
        void has_comicCharacters_matchesWhenPresent() {
            BookEntity comicBook = createBook("Comic Book");
            ComicMetadataEntity comicMeta = ComicMetadataEntity.builder()
                    .bookId(comicBook.getId())
                    .bookMetadata(comicBook.getMetadata())
                    .build();
            ComicCharacterEntity character = ComicCharacterEntity.builder().name("Spider-Man MP").build();
            em.persist(character);
            comicMeta.setCharacters(new HashSet<>(Set.of(character)));
            em.persist(comicMeta);
            comicBook.getMetadata().setComicMetadata(comicMeta);
            em.merge(comicBook.getMetadata());

            BookEntity noComic = createBook("Not a Comic");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "comicCharacters"));
            assertThat(ids).contains(comicBook.getId());
            assertThat(ids).doesNotContain(noComic.getId());
        }

        @Test
        void has_comicTeams_matchesWhenPresent() {
            BookEntity comicBook = createBook("Team Comic");
            ComicMetadataEntity comicMeta = ComicMetadataEntity.builder()
                    .bookId(comicBook.getId())
                    .bookMetadata(comicBook.getMetadata())
                    .build();
            ComicTeamEntity team = ComicTeamEntity.builder().name("Avengers MP").build();
            em.persist(team);
            comicMeta.setTeams(new HashSet<>(Set.of(team)));
            em.persist(comicMeta);
            comicBook.getMetadata().setComicMetadata(comicMeta);
            em.merge(comicBook.getMetadata());

            BookEntity noTeams = createBook("No Teams");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "comicTeams"));
            assertThat(ids).contains(comicBook.getId());
            assertThat(ids).doesNotContain(noTeams.getId());
        }

        @Test
        void has_comicLocations_matchesWhenPresent() {
            BookEntity comicBook = createBook("Location Comic");
            ComicMetadataEntity comicMeta = ComicMetadataEntity.builder()
                    .bookId(comicBook.getId())
                    .bookMetadata(comicBook.getMetadata())
                    .build();
            ComicLocationEntity location = ComicLocationEntity.builder().name("Gotham MP").build();
            em.persist(location);
            comicMeta.setLocations(new HashSet<>(Set.of(location)));
            em.persist(comicMeta);
            comicBook.getMetadata().setComicMetadata(comicMeta);
            em.merge(comicBook.getMetadata());

            BookEntity noLocations = createBook("No Locations");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "comicLocations"));
            assertThat(ids).contains(comicBook.getId());
            assertThat(ids).doesNotContain(noLocations.getId());
        }

        @Test
        void has_comicPencillers_matchesWhenPresent() {
            BookEntity comicBook = createBook("Penciller Comic");
            ComicMetadataEntity comicMeta = ComicMetadataEntity.builder()
                    .bookId(comicBook.getId())
                    .bookMetadata(comicBook.getMetadata())
                    .build();
            em.persist(comicMeta);
            comicBook.getMetadata().setComicMetadata(comicMeta);
            em.merge(comicBook.getMetadata());

            ComicCreatorEntity creator = ComicCreatorEntity.builder().name("Jim Lee MP").build();
            em.persist(creator);
            ComicCreatorMappingEntity mapping = ComicCreatorMappingEntity.builder()
                    .comicMetadata(comicMeta)
                    .creator(creator)
                    .role(ComicCreatorRole.PENCILLER)
                    .build();
            em.persist(mapping);

            BookEntity noCreators = createBook("No Creators");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "comicPencillers"));
            assertThat(ids).contains(comicBook.getId());
            assertThat(ids).doesNotContain(noCreators.getId());
        }

        @Test
        void has_comicCreator_onlyMatchesCorrectRole() {
            BookEntity comicBook = createBook("Colorist Comic");
            ComicMetadataEntity comicMeta = ComicMetadataEntity.builder()
                    .bookId(comicBook.getId())
                    .bookMetadata(comicBook.getMetadata())
                    .build();
            em.persist(comicMeta);
            comicBook.getMetadata().setComicMetadata(comicMeta);
            em.merge(comicBook.getMetadata());

            ComicCreatorEntity creator = ComicCreatorEntity.builder().name("Colorist Person MP").build();
            em.persist(creator);
            ComicCreatorMappingEntity mapping = ComicCreatorMappingEntity.builder()
                    .comicMetadata(comicMeta)
                    .creator(creator)
                    .role(ComicCreatorRole.COLORIST)
                    .build();
            em.persist(mapping);
            em.flush();
            em.clear();

            // Has colorist → should match
            List<Long> coloristIds = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "comicColorists"));
            assertThat(coloristIds).contains(comicBook.getId());

            // Has penciller → should NOT match (only colorist assigned)
            List<Long> pencillerIds = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "comicPencillers"));
            assertThat(pencillerIds).doesNotContain(comicBook.getId());

            // Has inker → should NOT match
            List<Long> inkerIds = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "comicInkers"));
            assertThat(inkerIds).doesNotContain(comicBook.getId());
        }

        @Test
        void hasNot_coverImage_matchesWhenAbsent() {
            BookEntity withCover = createBook("With Cover 2");
            withCover.setBookCoverHash("xyz789");
            em.merge(withCover);

            BookEntity noCover = createBook("No Cover 2");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.NOT_EQUALS, "thumbnailUrl"));
            assertThat(ids).contains(noCover.getId());
            assertThat(ids).doesNotContain(withCover.getId());
        }

        @Test
        void hasNot_numericField_matchesWhenAbsent() {
            BookEntity withPages = createBook("With Pages 2");
            withPages.getMetadata().setPageCount(200);
            em.merge(withPages.getMetadata());

            BookEntity noPages = createBook("No Pages 2");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.NOT_EQUALS, "pageCount"));
            assertThat(ids).contains(noPages.getId());
            assertThat(ids).doesNotContain(withPages.getId());
        }

        @Test
        void hasNot_unknownField_matchesNothing() {
            BookEntity book = createBook("Any Book 2");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.NOT_EQUALS, "nonExistentField"));
            assertThat(ids).doesNotContain(book.getId());
        }

        @Test
        void has_nullValue_matchesAll() {
            BookEntity book = createBook("Null Value Book");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, null));
            assertThat(ids).contains(book.getId());
        }

        @Test
        void has_subtitle_matchesWhenPresent() {
            BookEntity withSubtitle = createBook("With Subtitle");
            withSubtitle.getMetadata().setSubtitle("A Subtitle");
            em.merge(withSubtitle.getMetadata());

            BookEntity noSubtitle = createBook("No Subtitle");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "subtitle"));
            assertThat(ids).contains(withSubtitle.getId());
            assertThat(ids).doesNotContain(noSubtitle.getId());
        }

        @Test
        void has_publisher_matchesWhenPresent() {
            BookEntity withPublisher = createBook("With Publisher MP");
            withPublisher.getMetadata().setPublisher("Penguin");
            em.merge(withPublisher.getMetadata());

            BookEntity noPublisher = createBook("No Publisher MP");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "publisher"));
            assertThat(ids).contains(withPublisher.getId());
            assertThat(ids).doesNotContain(noPublisher.getId());
        }

        @Test
        void has_language_matchesWhenPresent() {
            BookEntity withLang = createBook("With Language");
            withLang.getMetadata().setLanguage("en");
            em.merge(withLang.getMetadata());

            BookEntity noLang = createBook("No Language");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "language"));
            assertThat(ids).contains(withLang.getId());
            assertThat(ids).doesNotContain(noLang.getId());
        }

        @Test
        void has_asin_matchesWhenPresent() {
            BookEntity withAsin = createBook("With ASIN");
            withAsin.getMetadata().setAsin("B00TEST123");
            em.merge(withAsin.getMetadata());

            BookEntity noAsin = createBook("No ASIN");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "asin"));
            assertThat(ids).contains(withAsin.getId());
            assertThat(ids).doesNotContain(noAsin.getId());
        }

        @Test
        void has_contentRating_matchesWhenPresent() {
            BookEntity withCR = createBook("With Content Rating");
            withCR.getMetadata().setContentRating("MATURE");
            em.merge(withCR.getMetadata());

            BookEntity noCR = createBook("No Content Rating");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "contentRating"));
            assertThat(ids).contains(withCR.getId());
            assertThat(ids).doesNotContain(noCR.getId());
        }

        @Test
        void has_isbn10_matchesWhenPresent() {
            BookEntity withIsbn10 = createBook("With ISBN10");
            withIsbn10.getMetadata().setIsbn10("0123456789");
            em.merge(withIsbn10.getMetadata());

            BookEntity noIsbn10 = createBook("No ISBN10");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "isbn10"));
            assertThat(ids).contains(withIsbn10.getId());
            assertThat(ids).doesNotContain(noIsbn10.getId());
        }

        @Test
        void has_reviewCount_matchesWhenPresent() {
            BookEntity withReviews = createBook("With Reviews");
            withReviews.getMetadata().setAudibleReviewCount(150);
            em.merge(withReviews.getMetadata());

            BookEntity noReviews = createBook("No Reviews");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "audibleReviewCount"));
            assertThat(ids).contains(withReviews.getId());
            assertThat(ids).doesNotContain(noReviews.getId());
        }

        @Test
        void has_otherExternalIds_matchWhenPresent() {
            BookEntity withIds = createBook("With External IDs");
            withIds.getMetadata().setAudibleId("AUD123");
            withIds.getMetadata().setComicvineId("CV456");
            withIds.getMetadata().setHardcoverId("HC789");
            withIds.getMetadata().setGoogleId("G012");
            withIds.getMetadata().setLubimyczytacId("LUB345");
            withIds.getMetadata().setRanobedbId("RAN678");
            em.merge(withIds.getMetadata());

            BookEntity noIds = createBook("No External IDs");
            em.flush();
            em.clear();

            for (String field : List.of("audibleId", "comicvineId", "hardcoverId", "googleId", "lubimyczytacId", "ranobedbId")) {
                List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, field));
                assertThat(ids).as("Has %s", field).contains(withIds.getId());
                assertThat(ids).as("Has %s", field).doesNotContain(noIds.getId());
            }
        }

        @Test
        void has_otherRatings_matchWhenPresent() {
            BookEntity withRatings = createBook("With Ratings");
            withRatings.getMetadata().setGoodreadsRating(4.2);
            withRatings.getMetadata().setHardcoverRating(3.8);
            withRatings.getMetadata().setRanobedbRating(4.0);
            withRatings.getMetadata().setLubimyczytacRating(4.5);
            withRatings.getMetadata().setAudibleRating(4.1);
            em.merge(withRatings.getMetadata());

            BookEntity noRatings = createBook("No Ratings");
            em.flush();
            em.clear();

            for (String field : List.of("goodreadsRating", "hardcoverRating", "ranobedbRating", "lubimyczytacRating", "audibleRating")) {
                List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, field));
                assertThat(ids).as("Has %s", field).contains(withRatings.getId());
                assertThat(ids).as("Has %s", field).doesNotContain(noRatings.getId());
            }
        }

        @Test
        void has_personalRating_noProgressEntity_treatedAsAbsent() {
            BookEntity noProgress = createBook("No Progress At All");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "personalRating"));
            assertThat(ids).doesNotContain(noProgress.getId());
        }

        @Test
        void mixedWithOtherRuleTypes() {
            BookEntity matchesBoth = createBook("Matching Book");
            matchesBoth.getMetadata().setDescription("A great story");
            matchesBoth.getMetadata().setPageCount(250);
            em.merge(matchesBoth.getMetadata());

            BookEntity hasDescNoPages = createBook("Has Desc No Pages");
            hasDescNoPages.getMetadata().setDescription("Another story");
            em.merge(hasDescNoPages.getMetadata());

            BookEntity shortWithPages = createBook("Short with Pages");
            shortWithPages.getMetadata().setPageCount(100);
            em.merge(shortWithPages.getMetadata());
            em.flush();
            em.clear();

            Rule presenceRule = new Rule();
            presenceRule.setField(RuleField.METADATA_PRESENCE);
            presenceRule.setOperator(RuleOperator.EQUALS);
            presenceRule.setValue("description");

            Rule pageRule = new Rule();
            pageRule.setField(RuleField.PAGE_COUNT);
            pageRule.setOperator(RuleOperator.GREATER_THAN);
            pageRule.setValue(200);

            GroupRule group = new GroupRule();
            group.setJoin(JoinType.AND);
            group.setRules(List.of(presenceRule, pageRule));

            List<Long> ids = findMatchingIds(group);
            assertThat(ids).contains(matchesBoth.getId());
            assertThat(ids).doesNotContain(hasDescNoPages.getId(), shortWithPages.getId());
        }

        @Test
        void has_otherReviewCounts_matchWhenPresent() {
            BookEntity withReviews = createBook("With All Reviews");
            withReviews.getMetadata().setAmazonReviewCount(100);
            withReviews.getMetadata().setGoodreadsReviewCount(200);
            withReviews.getMetadata().setHardcoverReviewCount(50);
            em.merge(withReviews.getMetadata());

            BookEntity noReviews = createBook("No Review Counts");
            em.flush();
            em.clear();

            for (String field : List.of("amazonReviewCount", "goodreadsReviewCount", "hardcoverReviewCount")) {
                List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, field));
                assertThat(ids).as("Has %s", field).contains(withReviews.getId());
                assertThat(ids).as("Has %s", field).doesNotContain(noReviews.getId());
            }
        }

        @Test
        void has_comicEditors_matchesWhenPresent() {
            BookEntity comicBook = createBook("Editor Comic");
            ComicMetadataEntity comicMeta = ComicMetadataEntity.builder()
                    .bookId(comicBook.getId())
                    .bookMetadata(comicBook.getMetadata())
                    .build();
            em.persist(comicMeta);
            comicBook.getMetadata().setComicMetadata(comicMeta);
            em.merge(comicBook.getMetadata());

            ComicCreatorEntity editor = ComicCreatorEntity.builder().name("Editor Person MP").build();
            em.persist(editor);
            ComicCreatorMappingEntity mapping = ComicCreatorMappingEntity.builder()
                    .comicMetadata(comicMeta)
                    .creator(editor)
                    .role(ComicCreatorRole.EDITOR)
                    .build();
            em.persist(mapping);

            BookEntity noEditors = createBook("No Editors");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "comicEditors"));
            assertThat(ids).contains(comicBook.getId());
            assertThat(ids).doesNotContain(noEditors.getId());
        }

        @Test
        void hasNot_comicCharacters_matchesWhenAbsent() {
            BookEntity comicBook = createBook("Comic With Chars");
            ComicMetadataEntity comicMeta = ComicMetadataEntity.builder()
                    .bookId(comicBook.getId())
                    .bookMetadata(comicBook.getMetadata())
                    .build();
            ComicCharacterEntity character = ComicCharacterEntity.builder().name("Batman MP").build();
            em.persist(character);
            comicMeta.setCharacters(new HashSet<>(Set.of(character)));
            em.persist(comicMeta);
            comicBook.getMetadata().setComicMetadata(comicMeta);
            em.merge(comicBook.getMetadata());

            BookEntity noChars = createBook("No Characters");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.NOT_EQUALS, "comicCharacters"));
            assertThat(ids).contains(noChars.getId());
            assertThat(ids).doesNotContain(comicBook.getId());
        }

        @Test
        void has_audiobookDuration_fileWithoutDuration_doesNotMatch() {
            BookEntity bookNoDuration = createBook("File Without Duration");
            BookFileEntity fileNoDuration = BookFileEntity.builder()
                    .book(bookNoDuration)
                    .fileName("noduration.m4b")
                    .fileSubPath("")
                    .isBookFormat(true)
                    .bookType(BookFileType.AUDIOBOOK)
                    .build();
            em.persist(fileNoDuration);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "audiobookDuration"));
            assertThat(ids).doesNotContain(bookNoDuration.getId());
        }
    }

    @Nested
    class IntegerFieldTests {
        @Test
        void equals_integerField_matchesExact() {
            BookEntity book300 = createBook("300 Pages");
            book300.getMetadata().setPageCount(300);
            em.merge(book300.getMetadata());

            BookEntity book500 = createBook("500 Pages");
            book500.getMetadata().setPageCount(500);
            em.merge(book500.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.PAGE_COUNT, RuleOperator.EQUALS, 300));
            assertThat(ids).contains(book300.getId());
            assertThat(ids).doesNotContain(book500.getId());
        }

        @Test
        void notEquals_integerField_excludesMatch() {
            BookEntity book300 = createBook("300 Pages");
            book300.getMetadata().setPageCount(300);
            em.merge(book300.getMetadata());

            BookEntity book500 = createBook("500 Pages");
            book500.getMetadata().setPageCount(500);
            em.merge(book500.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.PAGE_COUNT, RuleOperator.NOT_EQUALS, 300));
            assertThat(ids).contains(book500.getId());
            assertThat(ids).doesNotContain(book300.getId());
        }
    }
}
