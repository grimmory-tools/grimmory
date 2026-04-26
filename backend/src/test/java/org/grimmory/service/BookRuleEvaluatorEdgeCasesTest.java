package org.grimmory.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.booklore.BookloreApplication;
import org.booklore.model.dto.*;
import org.booklore.model.entity.*;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

@SpringBootTest(classes = {BookloreApplication.class})
@Transactional
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:ruleeval_edge;DB_CLOSE_DELAY=-1",
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
@Import(BookRuleEvaluatorEdgeCasesTest.TestConfig.class)
class BookRuleEvaluatorEdgeCasesTest {

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

    private List<Long> findMatchingIds(GroupRule group) {
        Specification<BookEntity> spec = evaluator.toSpecification(group, user.getId());
        return bookRepository.findAll(spec).stream().map(BookEntity::getId).distinct().toList();
    }

    private GroupRule singleRule(RuleField field, RuleOperator operator, Object value) {
        Rule rule = new Rule();
        rule.setField(field);
        rule.setOperator(operator);
        rule.setValue(value);
        GroupRule group = new GroupRule();
        group.setJoin(JoinType.AND);
        group.setRules(List.of(rule));
        return group;
    }

    @Nested
    class NullRuleHandlingTests {
        @Test
        void nullFieldOnRule_returnsAllBooks() {
            BookEntity book = createBook("Any Book");
            em.flush();
            em.clear();

            Rule rule = new Rule();
            rule.setField(null);
            rule.setOperator(RuleOperator.EQUALS);
            rule.setValue("test");

            GroupRule group = new GroupRule();
            group.setJoin(JoinType.AND);
            group.setRules(List.of(rule));

            List<Long> ids = findMatchingIds(group);
            assertThat(ids).contains(book.getId());
        }

        @Test
        void nullOperatorOnRule_returnsAllBooks() {
            BookEntity book = createBook("Any Book");
            em.flush();
            em.clear();

            Rule rule = new Rule();
            rule.setField(RuleField.TITLE);
            rule.setOperator(null);
            rule.setValue("test");

            GroupRule group = new GroupRule();
            group.setJoin(JoinType.AND);
            group.setRules(List.of(rule));

            List<Long> ids = findMatchingIds(group);
            assertThat(ids).contains(book.getId());
        }

        @Test
        void nullRulesInGroup_matchesAllBooks() {
            BookEntity book = createBook("Any Book");
            em.flush();
            em.clear();

            GroupRule group = new GroupRule();
            group.setJoin(JoinType.AND);
            group.setRules(null);

            List<Long> ids = findMatchingIds(group);
            assertThat(ids).contains(book.getId());
        }

        @Test
        void nullRuleObjectInList_skipped() {
            BookEntity book = createBook("Any Book");
            em.flush();
            em.clear();

            List<Object> rules = new ArrayList<>();
            rules.add(null);

            GroupRule group = new GroupRule();
            group.setJoin(JoinType.AND);
            group.setRules(rules);

            List<Long> ids = findMatchingIds(group);
            assertThat(ids).contains(book.getId());
        }
    }

    @Nested
    class MalformedRuleTests {
        @Test
        void invalidRuleObject_loggedAndSkipped() {
            BookEntity book = createBook("Any Book");
            em.flush();
            em.clear();

            Map<String, Object> badRule = new HashMap<>();
            badRule.put("field", "INVALID_FIELD");
            badRule.put("operator", "EQUALS");
            badRule.put("value", "test");

            GroupRule group = new GroupRule();
            group.setJoin(JoinType.AND);
            group.setRules(List.of(badRule));

            assertThatCode(() -> findMatchingIds(group)).doesNotThrowAnyException();
        }

        @Test
        void inBetween_withNullStart_returnsAllBooks() {
            BookEntity book = createBook("Any Book");
            book.getMetadata().setPageCount(300);
            em.merge(book.getMetadata());
            em.flush();
            em.clear();

            Rule rule = new Rule();
            rule.setField(RuleField.PAGE_COUNT);
            rule.setOperator(RuleOperator.IN_BETWEEN);
            rule.setValueStart(null);
            rule.setValueEnd(500);

            GroupRule group = new GroupRule();
            group.setJoin(JoinType.AND);
            group.setRules(List.of(rule));

            List<Long> ids = findMatchingIds(group);
            assertThat(ids).contains(book.getId());
        }

        @Test
        void inBetween_withNullEnd_returnsAllBooks() {
            BookEntity book = createBook("Any Book");
            book.getMetadata().setPageCount(300);
            em.merge(book.getMetadata());
            em.flush();
            em.clear();

            Rule rule = new Rule();
            rule.setField(RuleField.PAGE_COUNT);
            rule.setOperator(RuleOperator.IN_BETWEEN);
            rule.setValueStart(100);
            rule.setValueEnd(null);

            GroupRule group = new GroupRule();
            group.setJoin(JoinType.AND);
            group.setRules(List.of(rule));

            List<Long> ids = findMatchingIds(group);
            assertThat(ids).contains(book.getId());
        }
    }

    @Nested
    class EmptyGroupTests {
        @Test
        void emptyAndGroup_matchesAll() {
            BookEntity book = createBook("Any Book");
            em.flush();
            em.clear();

            GroupRule group = new GroupRule();
            group.setJoin(JoinType.AND);
            group.setRules(new ArrayList<>());

            List<Long> ids = findMatchingIds(group);
            assertThat(ids).contains(book.getId());
        }

        @Test
        void emptyOrGroup_matchesAll() {
            BookEntity book = createBook("Any Book");
            em.flush();
            em.clear();

            GroupRule group = new GroupRule();
            group.setJoin(JoinType.OR);
            group.setRules(new ArrayList<>());

            List<Long> ids = findMatchingIds(group);
            assertThat(ids).contains(book.getId());
        }
    }

    @Nested
    class MultiUserIsolationTests {
        @Test
        void progressFromDifferentUser_notVisible() {
            BookLoreUserEntity user2 = BookLoreUserEntity.builder()
                    .username("otheruser")
                    .passwordHash("hash2")
                    .isDefaultPassword(true)
                    .name("Other User")
                    .build();
            em.persist(user2);

            BookEntity book = createBook("Shared Book");

            UserBookProgressEntity otherProgress = UserBookProgressEntity.builder()
                    .user(user2)
                    .book(book)
                    .readStatus(ReadStatus.READ)
                    .build();
            em.persist(otherProgress);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.READ_STATUS, RuleOperator.EQUALS, "READ"));
            assertThat(ids).doesNotContain(book.getId());
        }
    }

    @Nested
    class NestedGroupEdgeCasesTests {
        @Test
        void deeplyNestedGroups_evaluateCorrectly() {
            BookEntity match = createBook("Deep Match");
            match.getMetadata().setPageCount(200);
            match.getMetadata().setLanguage("en");
            match.getMetadata().setPublisher("Test Publisher");
            em.merge(match.getMetadata());

            BookEntity noMatch = createBook("Deep No Match");
            noMatch.getMetadata().setPageCount(200);
            noMatch.getMetadata().setLanguage("fr");
            em.merge(noMatch.getMetadata());
            em.flush();
            em.clear();

            Rule langRule = new Rule();
            langRule.setField(RuleField.LANGUAGE);
            langRule.setOperator(RuleOperator.EQUALS);
            langRule.setValue("en");

            Rule pubRule = new Rule();
            pubRule.setField(RuleField.PUBLISHER);
            pubRule.setOperator(RuleOperator.CONTAINS);
            pubRule.setValue("test");

            GroupRule innerGroup = new GroupRule();
            innerGroup.setType("group");
            innerGroup.setJoin(JoinType.AND);
            innerGroup.setRules(List.of(langRule, pubRule));

            Rule pageRule = new Rule();
            pageRule.setField(RuleField.PAGE_COUNT);
            pageRule.setOperator(RuleOperator.GREATER_THAN);
            pageRule.setValue(100);

            GroupRule outerGroup = new GroupRule();
            outerGroup.setJoin(JoinType.AND);
            outerGroup.setRules(List.of(pageRule, innerGroup));

            List<Long> ids = findMatchingIds(outerGroup);
            assertThat(ids).contains(match.getId());
            assertThat(ids).doesNotContain(noMatch.getId());
        }

        @Test
        void mixedAndOrGroups_evaluateCorrectly() {
            BookEntity matchA = createBook("Match A");
            matchA.getMetadata().setLanguage("en");
            matchA.getMetadata().setPageCount(500);
            em.merge(matchA.getMetadata());

            BookEntity matchB = createBook("Match B");
            matchB.getMetadata().setLanguage("fr");
            matchB.getMetadata().setPageCount(500);
            matchB.getMetadata().setPublisher("Big Publisher");
            em.merge(matchB.getMetadata());

            BookEntity noMatch = createBook("No Match");
            noMatch.getMetadata().setLanguage("fr");
            noMatch.getMetadata().setPageCount(500);
            em.merge(noMatch.getMetadata());
            em.flush();
            em.clear();

            // Outer AND: pageCount > 300 AND (language = en OR publisher contains "big")
            Rule pageRule = new Rule();
            pageRule.setField(RuleField.PAGE_COUNT);
            pageRule.setOperator(RuleOperator.GREATER_THAN);
            pageRule.setValue(300);

            Rule langRule = new Rule();
            langRule.setField(RuleField.LANGUAGE);
            langRule.setOperator(RuleOperator.EQUALS);
            langRule.setValue("en");

            Rule pubRule = new Rule();
            pubRule.setField(RuleField.PUBLISHER);
            pubRule.setOperator(RuleOperator.CONTAINS);
            pubRule.setValue("big");

            GroupRule orGroup = new GroupRule();
            orGroup.setType("group");
            orGroup.setJoin(JoinType.OR);
            orGroup.setRules(List.of(langRule, pubRule));

            GroupRule outerGroup = new GroupRule();
            outerGroup.setJoin(JoinType.AND);
            outerGroup.setRules(List.of(pageRule, orGroup));

            List<Long> ids = findMatchingIds(outerGroup);
            assertThat(ids).contains(matchA.getId(), matchB.getId());
            assertThat(ids).doesNotContain(noMatch.getId());
        }
    }

    @Nested
    class MultipleRulesOnSameFieldTests {
        @Test
        void twoComparisonsOnSameField_actsAsRange() {
            BookEntity low = createBook("Low Pages");
            low.getMetadata().setPageCount(50);
            em.merge(low.getMetadata());

            BookEntity mid = createBook("Mid Pages");
            mid.getMetadata().setPageCount(250);
            em.merge(mid.getMetadata());

            BookEntity high = createBook("High Pages");
            high.getMetadata().setPageCount(600);
            em.merge(high.getMetadata());
            em.flush();
            em.clear();

            Rule gtRule = new Rule();
            gtRule.setField(RuleField.PAGE_COUNT);
            gtRule.setOperator(RuleOperator.GREATER_THAN);
            gtRule.setValue(100);

            Rule ltRule = new Rule();
            ltRule.setField(RuleField.PAGE_COUNT);
            ltRule.setOperator(RuleOperator.LESS_THAN);
            ltRule.setValue(500);

            GroupRule group = new GroupRule();
            group.setJoin(JoinType.AND);
            group.setRules(List.of(gtRule, ltRule));

            List<Long> ids = findMatchingIds(group);
            assertThat(ids).contains(mid.getId());
            assertThat(ids).doesNotContain(low.getId(), high.getId());
        }
    }

    @Nested
    class ReadStatusEdgeCasesTests {
        @Test
        void allReadStatuses_matchCorrectBooks() {
            BookEntity reading = createBook("Reading Book");
            UserBookProgressEntity p1 = UserBookProgressEntity.builder()
                    .user(user).book(reading).readStatus(ReadStatus.READING).build();
            em.persist(p1);

            BookEntity read = createBook("Read Book");
            UserBookProgressEntity p2 = UserBookProgressEntity.builder()
                    .user(user).book(read).readStatus(ReadStatus.READ).build();
            em.persist(p2);

            BookEntity paused = createBook("Paused Book");
            UserBookProgressEntity p3 = UserBookProgressEntity.builder()
                    .user(user).book(paused).readStatus(ReadStatus.PAUSED).build();
            em.persist(p3);

            BookEntity reReading = createBook("ReReading Book");
            UserBookProgressEntity p4 = UserBookProgressEntity.builder()
                    .user(user).book(reReading).readStatus(ReadStatus.RE_READING).build();
            em.persist(p4);

            BookEntity unread = createBook("Unread Book");
            UserBookProgressEntity p5 = UserBookProgressEntity.builder()
                    .user(user).book(unread).readStatus(ReadStatus.UNREAD).build();
            em.persist(p5);
            em.flush();
            em.clear();

            List<Long> readingIds = findMatchingIds(singleRule(RuleField.READ_STATUS, RuleOperator.EQUALS, "READING"));
            assertThat(readingIds).contains(reading.getId());
            assertThat(readingIds).doesNotContain(read.getId(), paused.getId(), reReading.getId(), unread.getId());

            List<Long> pausedIds = findMatchingIds(singleRule(RuleField.READ_STATUS, RuleOperator.EQUALS, "PAUSED"));
            assertThat(pausedIds).contains(paused.getId());

            List<Long> reReadingIds = findMatchingIds(singleRule(RuleField.READ_STATUS, RuleOperator.EQUALS, "RE_READING"));
            assertThat(reReadingIds).contains(reReading.getId());
        }

        @Test
        void excludesAll_withUnset_excludesBooksWithNoProgress() {
            BookEntity noProgress = createBook("No Progress");

            BookEntity readBook = createBook("Read Book");
            UserBookProgressEntity p = UserBookProgressEntity.builder()
                    .user(user).book(readBook).readStatus(ReadStatus.READ).build();
            em.persist(p);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.READ_STATUS, RuleOperator.EXCLUDES_ALL, List.of("UNSET")));
            assertThat(ids).contains(readBook.getId());
            assertThat(ids).doesNotContain(noProgress.getId());
        }
    }

    @Nested
    class ReadStatusNullProgressTests {

        private BookEntity createBookWithReadStatus(String title, ReadStatus status) {
            BookEntity book = createBook(title);
            UserBookProgressEntity progress = UserBookProgressEntity.builder()
                    .user(user).book(book).readStatus(status).build();
            em.persist(progress);
            return book;
        }

        @Test
        void excludesAll_withNoProgress_includesBookWithoutProgress() {
            BookEntity noProgress = createBook("No Progress");
            BookEntity readBook = createBookWithReadStatus("Read Book", ReadStatus.READ);
            BookEntity abandonedBook = createBookWithReadStatus("Abandoned Book", ReadStatus.ABANDONED);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.READ_STATUS, RuleOperator.EXCLUDES_ALL,
                    List.of("READ", "ABANDONED", "WONT_READ")));
            assertThat(ids).contains(noProgress.getId());
            assertThat(ids).doesNotContain(readBook.getId(), abandonedBook.getId());
        }

        @Test
        void excludesAll_withUnsetInList_excludesNoProgressBooks() {
            BookEntity noProgress = createBook("No Progress");
            BookEntity readBook = createBookWithReadStatus("Read Book", ReadStatus.READ);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.READ_STATUS, RuleOperator.EXCLUDES_ALL,
                    List.of("UNSET")));
            assertThat(ids).contains(readBook.getId());
            assertThat(ids).doesNotContain(noProgress.getId());
        }

        @Test
        void excludesAll_withUnsetAndOtherStatuses_excludesNoProgressBooks() {
            BookEntity noProgress = createBook("No Progress");
            BookEntity readBook = createBookWithReadStatus("Read Book", ReadStatus.READ);
            BookEntity readingBook = createBookWithReadStatus("Reading Book", ReadStatus.READING);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.READ_STATUS, RuleOperator.EXCLUDES_ALL,
                    List.of("READ", "UNSET")));
            assertThat(ids).contains(readingBook.getId());
            assertThat(ids).doesNotContain(noProgress.getId(), readBook.getId());
        }

        @Test
        void notEquals_withNoProgress_includesBookWithoutProgress() {
            BookEntity noProgress = createBook("No Progress");
            BookEntity readBook = createBookWithReadStatus("Read Book", ReadStatus.READ);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.READ_STATUS, RuleOperator.NOT_EQUALS, "READ"));
            assertThat(ids).contains(noProgress.getId());
            assertThat(ids).doesNotContain(readBook.getId());
        }

        @Test
        void notEquals_unset_withNoProgress_excludesBookWithoutProgress() {
            BookEntity noProgress = createBook("No Progress");
            BookEntity readBook = createBookWithReadStatus("Read Book", ReadStatus.READ);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.READ_STATUS, RuleOperator.NOT_EQUALS, "UNSET"));
            assertThat(ids).contains(readBook.getId());
            assertThat(ids).doesNotContain(noProgress.getId());
        }

        @Test
        void doesNotContain_withNoProgress_includesBookWithoutProgress() {
            BookEntity noProgress = createBook("No Progress");
            BookEntity readBook = createBookWithReadStatus("Read Book", ReadStatus.READ);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.READ_STATUS, RuleOperator.DOES_NOT_CONTAIN, "READ"));
            assertThat(ids).contains(noProgress.getId());
            assertThat(ids).doesNotContain(readBook.getId());
        }

        @Test
        void equals_withNoProgress_doesNotMatchNonUnsetStatus() {
            BookEntity noProgress = createBook("No Progress");
            BookEntity readBook = createBookWithReadStatus("Read Book", ReadStatus.READ);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.READ_STATUS, RuleOperator.EQUALS, "READ"));
            assertThat(ids).contains(readBook.getId());
            assertThat(ids).doesNotContain(noProgress.getId());
        }

        @Test
        void equals_unset_withNoProgress_matchesNoProgressBook() {
            BookEntity noProgress = createBook("No Progress");
            BookEntity readBook = createBookWithReadStatus("Read Book", ReadStatus.READ);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.READ_STATUS, RuleOperator.EQUALS, "UNSET"));
            assertThat(ids).contains(noProgress.getId());
            assertThat(ids).doesNotContain(readBook.getId());
        }

        @Test
        void includesAny_withoutUnset_doesNotMatchNoProgress() {
            BookEntity noProgress = createBook("No Progress");
            BookEntity readBook = createBookWithReadStatus("Read Book", ReadStatus.READ);
            BookEntity readingBook = createBookWithReadStatus("Reading Book", ReadStatus.READING);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.READ_STATUS, RuleOperator.INCLUDES_ANY,
                    List.of("READ", "READING")));
            assertThat(ids).contains(readBook.getId(), readingBook.getId());
            assertThat(ids).doesNotContain(noProgress.getId());
        }

        @Test
        void includesAny_withUnset_matchesNoProgress() {
            BookEntity noProgress = createBook("No Progress");
            BookEntity readBook = createBookWithReadStatus("Read Book", ReadStatus.READ);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.READ_STATUS, RuleOperator.INCLUDES_ANY,
                    List.of("UNSET", "READ")));
            assertThat(ids).contains(noProgress.getId(), readBook.getId());
        }

        @Test
        void isEmpty_readStatus_noProgress_matchesNoProgressBook() {
            BookEntity noProgress = createBook("No Progress");
            BookEntity readBook = createBookWithReadStatus("Read Book", ReadStatus.READ);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.READ_STATUS, RuleOperator.IS_EMPTY, null));
            assertThat(ids).contains(noProgress.getId());
            assertThat(ids).doesNotContain(readBook.getId());
        }

        @Test
        void isNotEmpty_readStatus_noProgress_doesNotMatch() {
            BookEntity noProgress = createBook("No Progress");
            BookEntity readBook = createBookWithReadStatus("Read Book", ReadStatus.READ);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.READ_STATUS, RuleOperator.IS_NOT_EMPTY, null));
            assertThat(ids).contains(readBook.getId());
            assertThat(ids).doesNotContain(noProgress.getId());
        }

        @Test
        void isNotEmpty_readStatus_withProgress_matches() {
            BookEntity readingBook = createBookWithReadStatus("Reading Book", ReadStatus.READING);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.READ_STATUS, RuleOperator.IS_NOT_EMPTY, null));
            assertThat(ids).contains(readingBook.getId());
        }

        @Test
        void excludesAll_readStatus_combinedWithTitleRule() {
            BookEntity noProgressMatch = createBook("Magic Title");
            BookEntity readMatch = createBookWithReadStatus("Magic Read", ReadStatus.READ);
            BookEntity noProgressNoTitle = createBook("Other Book");
            em.flush();
            em.clear();

            Rule titleRule = new Rule();
            titleRule.setField(RuleField.TITLE);
            titleRule.setOperator(RuleOperator.CONTAINS);
            titleRule.setValue("magic");

            Rule statusRule = new Rule();
            statusRule.setField(RuleField.READ_STATUS);
            statusRule.setOperator(RuleOperator.EXCLUDES_ALL);
            statusRule.setValue(List.of("READ", "ABANDONED"));

            GroupRule group = new GroupRule();
            group.setJoin(JoinType.AND);
            group.setRules(List.of(titleRule, statusRule));

            List<Long> ids = findMatchingIds(group);
            assertThat(ids).contains(noProgressMatch.getId());
            assertThat(ids).doesNotContain(readMatch.getId(), noProgressNoTitle.getId());
        }

        @Test
        void notEquals_readStatus_combinedWithMetadataRule() {
            BookEntity noProgressEn = createBook("English Book");
            noProgressEn.getMetadata().setLanguage("en");
            em.merge(noProgressEn.getMetadata());

            BookEntity readEn = createBookWithReadStatus("Read English", ReadStatus.READ);
            readEn.getMetadata().setLanguage("en");
            em.merge(readEn.getMetadata());

            BookEntity noProgressFr = createBook("French Book");
            noProgressFr.getMetadata().setLanguage("fr");
            em.merge(noProgressFr.getMetadata());
            em.flush();
            em.clear();

            Rule langRule = new Rule();
            langRule.setField(RuleField.LANGUAGE);
            langRule.setOperator(RuleOperator.EQUALS);
            langRule.setValue("en");

            Rule statusRule = new Rule();
            statusRule.setField(RuleField.READ_STATUS);
            statusRule.setOperator(RuleOperator.NOT_EQUALS);
            statusRule.setValue("READ");

            GroupRule group = new GroupRule();
            group.setJoin(JoinType.AND);
            group.setRules(List.of(langRule, statusRule));

            List<Long> ids = findMatchingIds(group);
            assertThat(ids).contains(noProgressEn.getId());
            assertThat(ids).doesNotContain(readEn.getId(), noProgressFr.getId());
        }

        @Test
        void excludesAll_noProgress_withOtherUserProgress_bookExcludedByUserFilter() {
            BookLoreUserEntity user2 = BookLoreUserEntity.builder()
                    .username("otheruser2")
                    .passwordHash("hash2")
                    .isDefaultPassword(true)
                    .name("Other User 2")
                    .build();
            em.persist(user2);

            BookEntity book = createBook("Multi User Book");
            UserBookProgressEntity otherProgress = UserBookProgressEntity.builder()
                    .user(user2).book(book).readStatus(ReadStatus.READ).build();
            em.persist(otherProgress);

            BookEntity noProgressBook = createBook("No Progress Book");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.READ_STATUS, RuleOperator.EXCLUDES_ALL,
                    List.of("READ", "ABANDONED")));
            assertThat(ids).doesNotContain(book.getId());
            assertThat(ids).contains(noProgressBook.getId());
        }
    }

    @Nested
    class ComparisonWithNullFieldValueTests {
        @Test
        void greaterThan_onNullField_doesNotMatch() {
            BookEntity book = createBook("No Page Count");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.PAGE_COUNT, RuleOperator.GREATER_THAN, 0));
            assertThat(ids).doesNotContain(book.getId());
        }

        @Test
        void lessThan_onNullField_doesNotMatch() {
            BookEntity book = createBook("No Page Count");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.PAGE_COUNT, RuleOperator.LESS_THAN, 999999));
            assertThat(ids).doesNotContain(book.getId());
        }

        @Test
        void inBetween_onNullField_doesNotMatch() {
            BookEntity book = createBook("No Score");
            em.flush();
            em.clear();

            Rule rule = new Rule();
            rule.setField(RuleField.METADATA_SCORE);
            rule.setOperator(RuleOperator.IN_BETWEEN);
            rule.setValueStart(0);
            rule.setValueEnd(100);

            GroupRule group = new GroupRule();
            group.setJoin(JoinType.AND);
            group.setRules(List.of(rule));

            List<Long> ids = findMatchingIds(group);
            assertThat(ids).doesNotContain(book.getId());
        }

        @Test
        void equals_onNullField_doesNotMatchNumericValue() {
            BookEntity book = createBook("No Rating");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.AMAZON_RATING, RuleOperator.EQUALS, 0));
            assertThat(ids).doesNotContain(book.getId());
        }
    }

    @Nested
    class WithinLastEdgeCasesTests {
        @Test
        void withinLast_withStringAmount_parsesCorrectly() {
            BookEntity recent = createBook("Recent");
            recent.setAddedOn(Instant.now().minusSeconds(86400));
            em.merge(recent);
            em.flush();
            em.clear();

            Rule rule = new Rule();
            rule.setField(RuleField.ADDED_ON);
            rule.setOperator(RuleOperator.WITHIN_LAST);
            rule.setValue("7");
            rule.setValueEnd("days");

            GroupRule group = new GroupRule();
            group.setJoin(JoinType.AND);
            group.setRules(List.of(rule));

            List<Long> ids = findMatchingIds(group);
            assertThat(ids).contains(recent.getId());
        }

        @Test
        void withinLast_withDefaultUnit_usesDays() {
            BookEntity recent = createBook("Recent");
            recent.setAddedOn(Instant.now().minusSeconds(86400));
            em.merge(recent);
            em.flush();
            em.clear();

            Rule rule = new Rule();
            rule.setField(RuleField.ADDED_ON);
            rule.setOperator(RuleOperator.WITHIN_LAST);
            rule.setValue(7);

            GroupRule group = new GroupRule();
            group.setJoin(JoinType.AND);
            group.setRules(List.of(rule));

            List<Long> ids = findMatchingIds(group);
            assertThat(ids).contains(recent.getId());
        }

        @Test
        void thisPeriod_defaultsToYear() {
            BookEntity recent = createBook("Recent");
            recent.setAddedOn(Instant.now().minusSeconds(86400));
            em.merge(recent);
            em.flush();
            em.clear();

            Rule rule = new Rule();
            rule.setField(RuleField.ADDED_ON);
            rule.setOperator(RuleOperator.THIS_PERIOD);
            rule.setValue(null);

            GroupRule group = new GroupRule();
            group.setJoin(JoinType.AND);
            group.setRules(List.of(rule));

            List<Long> ids = findMatchingIds(group);
            assertThat(ids).contains(recent.getId());
        }
    }

    @Nested
    class CombinedRuleTypeTests {
        @Test
        void presenceAndComparisonRules_combinedCorrectly() {
            BookEntity matchBoth = createBook("Match Both");
            matchBoth.getMetadata().setDescription("Has description");
            matchBoth.getMetadata().setPageCount(400);
            em.merge(matchBoth.getMetadata());

            BookEntity hasDescLowPages = createBook("Desc Low Pages");
            hasDescLowPages.getMetadata().setDescription("Has description too");
            hasDescLowPages.getMetadata().setPageCount(50);
            em.merge(hasDescLowPages.getMetadata());

            BookEntity highPagesNoDesc = createBook("High Pages No Desc");
            highPagesNoDesc.getMetadata().setPageCount(400);
            em.merge(highPagesNoDesc.getMetadata());
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
            assertThat(ids).contains(matchBoth.getId());
            assertThat(ids).doesNotContain(hasDescLowPages.getId(), highPagesNoDesc.getId());
        }

        @Test
        void stringAndNumericRules_combinedCorrectly() {
            BookEntity match = createBook("The Great Gatsby");
            match.getMetadata().setPageCount(180);
            match.getMetadata().setLanguage("en");
            em.merge(match.getMetadata());

            BookEntity wrongLang = createBook("El Gran Gatsby");
            wrongLang.getMetadata().setPageCount(180);
            wrongLang.getMetadata().setLanguage("es");
            em.merge(wrongLang.getMetadata());
            em.flush();
            em.clear();

            Rule titleRule = new Rule();
            titleRule.setField(RuleField.TITLE);
            titleRule.setOperator(RuleOperator.CONTAINS);
            titleRule.setValue("gatsby");

            Rule langRule = new Rule();
            langRule.setField(RuleField.LANGUAGE);
            langRule.setOperator(RuleOperator.EQUALS);
            langRule.setValue("en");

            GroupRule group = new GroupRule();
            group.setJoin(JoinType.AND);
            group.setRules(List.of(titleRule, langRule));

            List<Long> ids = findMatchingIds(group);
            assertThat(ids).contains(match.getId());
            assertThat(ids).doesNotContain(wrongLang.getId());
        }
    }
}
