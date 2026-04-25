package org.booklore.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.booklore.BookloreApplication;
import org.booklore.model.dto.*;
import org.booklore.model.entity.*;
import org.booklore.model.enums.ReadStatus;
import org.booklore.repository.BookRepository;
import org.booklore.service.task.TaskCronService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

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
        "spring.datasource.url=jdbc:h2:mem:ruleeval_fields;DB_CLOSE_DELAY=-1",
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
@Import(BookRuleEvaluatorFieldCoverageTest.TestConfig.class)
@Execution(ExecutionMode.SAME_THREAD)
class BookRuleEvaluatorFieldCoverageTest {

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
    private LibraryEntity library2;
    private LibraryPathEntity libraryPath;
    private LibraryPathEntity libraryPath2;
    private BookLoreUserEntity user;

    @BeforeEach
    void setUp() {
        library = LibraryEntity.builder().name("Test Library").icon("book").watch(false).build();
        em.persist(library);

        library2 = LibraryEntity.builder().name("Second Library").icon("book").watch(false).build();
        em.persist(library2);
        em.flush();

        libraryPath = LibraryPathEntity.builder().library(library).path("/test/path").build();
        em.persist(libraryPath);

        libraryPath2 = LibraryPathEntity.builder().library(library2).path("/test/path2").build();
        em.persist(libraryPath2);
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
        return createBook(title, library, libraryPath);
    }

    private BookEntity createBook(String title, LibraryEntity lib, LibraryPathEntity libPath) {
        BookEntity book = BookEntity.builder()
                .library(lib)
                .libraryPath(libPath)
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
    class LibraryFieldTests {
        @Test
        void equals_matchesBookInLibrary() {
            BookEntity book1 = createBook("Lib1 Book", library, libraryPath);
            BookEntity book2 = createBook("Lib2 Book", library2, libraryPath2);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.LIBRARY, RuleOperator.EQUALS, library.getId()));
            assertThat(ids).contains(book1.getId());
            assertThat(ids).doesNotContain(book2.getId());
        }

        @Test
        void notEquals_excludesBookInLibrary() {
            BookEntity book1 = createBook("Lib1 Book", library, libraryPath);
            BookEntity book2 = createBook("Lib2 Book", library2, libraryPath2);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.LIBRARY, RuleOperator.NOT_EQUALS, library.getId()));
            assertThat(ids).contains(book2.getId());
            assertThat(ids).doesNotContain(book1.getId());
        }

        @Test
        void includesAny_matchesBooksInMultipleLibraries() {
            BookEntity book1 = createBook("Lib1 Book", library, libraryPath);
            BookEntity book2 = createBook("Lib2 Book", library2, libraryPath2);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.LIBRARY, RuleOperator.INCLUDES_ANY,
                    List.of(String.valueOf(library.getId()), String.valueOf(library2.getId()))));
            assertThat(ids).contains(book1.getId(), book2.getId());
        }
    }

    @Nested
    class ShelfFieldTests {
        @Test
        void equals_matchesBookOnShelf() {
            ShelfEntity shelf = ShelfEntity.builder().user(user).name("Favorites").build();
            em.persist(shelf);

            BookEntity onShelf = createBook("On Shelf");
            onShelf.setShelves(new HashSet<>(Set.of(shelf)));
            em.merge(onShelf);

            BookEntity offShelf = createBook("Off Shelf");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.SHELF, RuleOperator.EQUALS,
                    List.of(String.valueOf(shelf.getId()))));
            assertThat(ids).contains(onShelf.getId());
            assertThat(ids).doesNotContain(offShelf.getId());
        }

        @Test
        void isEmpty_matchesBookWithNoShelves() {
            ShelfEntity shelf = ShelfEntity.builder().user(user).name("My Shelf").build();
            em.persist(shelf);

            BookEntity onShelf = createBook("On Shelf");
            onShelf.setShelves(new HashSet<>(Set.of(shelf)));
            em.merge(onShelf);

            BookEntity noShelf = createBook("No Shelf");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.SHELF, RuleOperator.IS_EMPTY, null));
            assertThat(ids).contains(noShelf.getId());
            assertThat(ids).doesNotContain(onShelf.getId());
        }

        @Test
        void isNotEmpty_matchesBookWithShelves() {
            ShelfEntity shelf = ShelfEntity.builder().user(user).name("My Shelf 2").build();
            em.persist(shelf);

            BookEntity onShelf = createBook("On Shelf");
            onShelf.setShelves(new HashSet<>(Set.of(shelf)));
            em.merge(onShelf);

            BookEntity noShelf = createBook("No Shelf");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.SHELF, RuleOperator.IS_NOT_EMPTY, null));
            assertThat(ids).contains(onShelf.getId());
            assertThat(ids).doesNotContain(noShelf.getId());
        }

        @Test
        void includesAny_matchesBookOnAnyShelf() {
            ShelfEntity shelf1 = ShelfEntity.builder().user(user).name("Shelf One").build();
            em.persist(shelf1);
            ShelfEntity shelf2 = ShelfEntity.builder().user(user).name("Shelf Two").build();
            em.persist(shelf2);

            BookEntity onShelf1 = createBook("On Shelf 1");
            onShelf1.setShelves(new HashSet<>(Set.of(shelf1)));
            em.merge(onShelf1);

            BookEntity onShelf2 = createBook("On Shelf 2");
            onShelf2.setShelves(new HashSet<>(Set.of(shelf2)));
            em.merge(onShelf2);

            BookEntity noShelf = createBook("No Shelf");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.SHELF, RuleOperator.INCLUDES_ANY,
                    List.of(String.valueOf(shelf1.getId()))));
            assertThat(ids).contains(onShelf1.getId());
            assertThat(ids).doesNotContain(onShelf2.getId(), noShelf.getId());
        }

        @Test
        void excludesAll_excludesBooksOnShelves() {
            ShelfEntity shelf1 = ShelfEntity.builder().user(user).name("Excluded Shelf").build();
            em.persist(shelf1);
            ShelfEntity shelf2 = ShelfEntity.builder().user(user).name("Other Shelf").build();
            em.persist(shelf2);

            BookEntity onExcluded = createBook("On Excluded Shelf");
            onExcluded.setShelves(new HashSet<>(Set.of(shelf1)));
            em.merge(onExcluded);

            BookEntity onOther = createBook("On Other Shelf");
            onOther.setShelves(new HashSet<>(Set.of(shelf2)));
            em.merge(onOther);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.SHELF, RuleOperator.EXCLUDES_ALL,
                    List.of(String.valueOf(shelf1.getId()))));
            assertThat(ids).contains(onOther.getId());
            assertThat(ids).doesNotContain(onExcluded.getId());
        }
    }

    @Nested
    class MultiShelfFilterTests {

        private Rule shelfRule(RuleOperator operator, Object value) {
            Rule rule = new Rule();
            rule.setField(RuleField.SHELF);
            rule.setOperator(operator);
            rule.setValue(value);
            return rule;
        }

        private List<Long> findMatchingIdsPaged(GroupRule group, int pageSize) {
            Specification<BookEntity> spec = evaluator.toSpecification(group, user.getId());
            Page<BookEntity> page = bookRepository.findAll(spec, PageRequest.of(0, pageSize));
            return page.getContent().stream().map(BookEntity::getId).toList();
        }

        @Test
        void orGroup_multipleShelfEquals_noDuplicateRows() {
            ShelfEntity shelf1 = ShelfEntity.builder().user(user).name("Shelf A").build();
            ShelfEntity shelf2 = ShelfEntity.builder().user(user).name("Shelf B").build();
            em.persist(shelf1);
            em.persist(shelf2);

            BookEntity bookOnBoth = createBook("On Both Shelves");
            bookOnBoth.setShelves(new HashSet<>(Set.of(shelf1, shelf2)));
            em.merge(bookOnBoth);

            BookEntity bookOnShelf1 = createBook("On Shelf A Only");
            bookOnShelf1.setShelves(new HashSet<>(Set.of(shelf1)));
            em.merge(bookOnShelf1);

            BookEntity bookOnShelf2 = createBook("On Shelf B Only");
            bookOnShelf2.setShelves(new HashSet<>(Set.of(shelf2)));
            em.merge(bookOnShelf2);

            BookEntity bookOnNeither = createBook("On No Shelf");
            em.flush();
            em.clear();

            GroupRule group = new GroupRule();
            group.setJoin(JoinType.OR);
            group.setRules(List.of(
                    shelfRule(RuleOperator.EQUALS, List.of(String.valueOf(shelf1.getId()))),
                    shelfRule(RuleOperator.EQUALS, List.of(String.valueOf(shelf2.getId())))
            ));

            List<Long> ids = findMatchingIds(group);
            assertThat(ids).containsExactlyInAnyOrder(bookOnBoth.getId(), bookOnShelf1.getId(), bookOnShelf2.getId());
            assertThat(ids).doesNotContain(bookOnNeither.getId());
        }

        @Test
        void orGroup_multipleShelfEquals_paginationNotTruncated() {
            ShelfEntity shelf1 = ShelfEntity.builder().user(user).name("Page Shelf A").build();
            ShelfEntity shelf2 = ShelfEntity.builder().user(user).name("Page Shelf B").build();
            em.persist(shelf1);
            em.persist(shelf2);

            List<BookEntity> books = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                BookEntity book = createBook("Multi-shelf Book " + i);
                book.setShelves(new HashSet<>(Set.of(shelf1, shelf2)));
                em.merge(book);
                books.add(book);
            }
            em.flush();
            em.clear();

            GroupRule group = new GroupRule();
            group.setJoin(JoinType.OR);
            group.setRules(List.of(
                    shelfRule(RuleOperator.EQUALS, List.of(String.valueOf(shelf1.getId()))),
                    shelfRule(RuleOperator.EQUALS, List.of(String.valueOf(shelf2.getId())))
            ));

            List<Long> ids = findMatchingIdsPaged(group, 10);
            assertThat(ids).hasSize(10);
        }

        @Test
        void andGroup_inclusionAndExclusion_excludesCorrectly() {
            ShelfEntity includeShelf = ShelfEntity.builder().user(user).name("Include Me").build();
            ShelfEntity excludeShelf = ShelfEntity.builder().user(user).name("Exclude Me").build();
            em.persist(includeShelf);
            em.persist(excludeShelf);

            BookEntity onIncludeOnly = createBook("Include Only");
            onIncludeOnly.setShelves(new HashSet<>(Set.of(includeShelf)));
            em.merge(onIncludeOnly);

            BookEntity onBoth = createBook("On Both");
            onBoth.setShelves(new HashSet<>(Set.of(includeShelf, excludeShelf)));
            em.merge(onBoth);

            BookEntity onExcludeOnly = createBook("Exclude Only");
            onExcludeOnly.setShelves(new HashSet<>(Set.of(excludeShelf)));
            em.merge(onExcludeOnly);

            BookEntity onNeither = createBook("On Neither");
            em.flush();
            em.clear();

            GroupRule group = new GroupRule();
            group.setJoin(JoinType.AND);
            group.setRules(List.of(
                    shelfRule(RuleOperator.EQUALS, List.of(String.valueOf(includeShelf.getId()))),
                    shelfRule(RuleOperator.EXCLUDES_ALL, List.of(String.valueOf(excludeShelf.getId())))
            ));

            List<Long> ids = findMatchingIds(group);
            assertThat(ids).containsExactly(onIncludeOnly.getId());
            assertThat(ids).doesNotContain(onBoth.getId(), onExcludeOnly.getId(), onNeither.getId());
        }

        @Test
        void includesAll_multipleShelvesRequired() {
            ShelfEntity shelf1 = ShelfEntity.builder().user(user).name("Required A").build();
            ShelfEntity shelf2 = ShelfEntity.builder().user(user).name("Required B").build();
            ShelfEntity shelf3 = ShelfEntity.builder().user(user).name("Extra C").build();
            em.persist(shelf1);
            em.persist(shelf2);
            em.persist(shelf3);

            BookEntity onAll = createBook("On All Three");
            onAll.setShelves(new HashSet<>(Set.of(shelf1, shelf2, shelf3)));
            em.merge(onAll);

            BookEntity onFirstTwo = createBook("On First Two");
            onFirstTwo.setShelves(new HashSet<>(Set.of(shelf1, shelf2)));
            em.merge(onFirstTwo);

            BookEntity onFirstOnly = createBook("On First Only");
            onFirstOnly.setShelves(new HashSet<>(Set.of(shelf1)));
            em.merge(onFirstOnly);

            BookEntity onNone = createBook("On None");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.SHELF, RuleOperator.INCLUDES_ALL,
                    List.of(String.valueOf(shelf1.getId()), String.valueOf(shelf2.getId()))));
            assertThat(ids).containsExactlyInAnyOrder(onAll.getId(), onFirstTwo.getId());
            assertThat(ids).doesNotContain(onFirstOnly.getId(), onNone.getId());
        }

        @Test
        void includesAny_multipleShelvesMatch() {
            ShelfEntity shelf1 = ShelfEntity.builder().user(user).name("Any A").build();
            ShelfEntity shelf2 = ShelfEntity.builder().user(user).name("Any B").build();
            ShelfEntity shelf3 = ShelfEntity.builder().user(user).name("Any C").build();
            em.persist(shelf1);
            em.persist(shelf2);
            em.persist(shelf3);

            BookEntity onFirst = createBook("On First");
            onFirst.setShelves(new HashSet<>(Set.of(shelf1)));
            em.merge(onFirst);

            BookEntity onSecond = createBook("On Second");
            onSecond.setShelves(new HashSet<>(Set.of(shelf2)));
            em.merge(onSecond);

            BookEntity onThird = createBook("On Third Only");
            onThird.setShelves(new HashSet<>(Set.of(shelf3)));
            em.merge(onThird);

            BookEntity onNone = createBook("On None");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.SHELF, RuleOperator.INCLUDES_ANY,
                    List.of(String.valueOf(shelf1.getId()), String.valueOf(shelf2.getId()))));
            assertThat(ids).containsExactlyInAnyOrder(onFirst.getId(), onSecond.getId());
            assertThat(ids).doesNotContain(onThird.getId(), onNone.getId());
        }

        @Test
        void excludesAll_multipleShelvesExcluded() {
            ShelfEntity shelf1 = ShelfEntity.builder().user(user).name("Excl A").build();
            ShelfEntity shelf2 = ShelfEntity.builder().user(user).name("Excl B").build();
            ShelfEntity shelf3 = ShelfEntity.builder().user(user).name("Keep C").build();
            em.persist(shelf1);
            em.persist(shelf2);
            em.persist(shelf3);

            BookEntity onExcluded1 = createBook("On Excluded 1");
            onExcluded1.setShelves(new HashSet<>(Set.of(shelf1)));
            em.merge(onExcluded1);

            BookEntity onExcluded2 = createBook("On Excluded 2");
            onExcluded2.setShelves(new HashSet<>(Set.of(shelf2)));
            em.merge(onExcluded2);

            BookEntity onBothExcluded = createBook("On Both Excluded");
            onBothExcluded.setShelves(new HashSet<>(Set.of(shelf1, shelf2)));
            em.merge(onBothExcluded);

            BookEntity onKeep = createBook("On Keep Shelf");
            onKeep.setShelves(new HashSet<>(Set.of(shelf3)));
            em.merge(onKeep);

            BookEntity onNone = createBook("On No Shelf");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.SHELF, RuleOperator.EXCLUDES_ALL,
                    List.of(String.valueOf(shelf1.getId()), String.valueOf(shelf2.getId()))));
            assertThat(ids).contains(onKeep.getId(), onNone.getId());
            assertThat(ids).doesNotContain(onExcluded1.getId(), onExcluded2.getId(), onBothExcluded.getId());
        }

        @Test
        void nestedGroups_shelfAndTitleCombined() {
            ShelfEntity shelf1 = ShelfEntity.builder().user(user).name("Nested Shelf").build();
            em.persist(shelf1);

            BookEntity matchesBoth = createBook("Magic Book");
            matchesBoth.setShelves(new HashSet<>(Set.of(shelf1)));
            em.merge(matchesBoth);

            BookEntity matchesShelfOnly = createBook("Normal Book");
            matchesShelfOnly.setShelves(new HashSet<>(Set.of(shelf1)));
            em.merge(matchesShelfOnly);

            BookEntity matchesTitleOnly = createBook("Magic Elsewhere");
            em.flush();
            em.clear();

            Rule shelfEq = shelfRule(RuleOperator.EQUALS, List.of(String.valueOf(shelf1.getId())));

            Rule titleContains = new Rule();
            titleContains.setField(RuleField.TITLE);
            titleContains.setOperator(RuleOperator.CONTAINS);
            titleContains.setValue("Magic");

            GroupRule group = new GroupRule();
            group.setJoin(JoinType.AND);
            group.setRules(List.of(shelfEq, titleContains));

            List<Long> ids = findMatchingIds(group);
            assertThat(ids).containsExactly(matchesBoth.getId());
            assertThat(ids).doesNotContain(matchesShelfOnly.getId(), matchesTitleOnly.getId());
        }

        @Test
        void bookOnManyShelves_paginationReturnsCorrectCount() {
            ShelfEntity shelf1 = ShelfEntity.builder().user(user).name("Many A").build();
            ShelfEntity shelf2 = ShelfEntity.builder().user(user).name("Many B").build();
            ShelfEntity shelf3 = ShelfEntity.builder().user(user).name("Many C").build();
            ShelfEntity shelf4 = ShelfEntity.builder().user(user).name("Many D").build();
            em.persist(shelf1);
            em.persist(shelf2);
            em.persist(shelf3);
            em.persist(shelf4);

            List<BookEntity> books = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                BookEntity book = createBook("Many Shelf Book " + i);
                book.setShelves(new HashSet<>(Set.of(shelf1, shelf2, shelf3, shelf4)));
                em.merge(book);
                books.add(book);
            }
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIdsPaged(
                    singleRule(RuleField.SHELF, RuleOperator.EQUALS, List.of(String.valueOf(shelf1.getId()))),
                    5);
            assertThat(ids).hasSize(5);
        }

        @Test
        void orGroup_includesAnyAndExcludesAll() {
            ShelfEntity shelf1 = ShelfEntity.builder().user(user).name("Or Inc").build();
            ShelfEntity shelf2 = ShelfEntity.builder().user(user).name("Or Exc").build();
            em.persist(shelf1);
            em.persist(shelf2);

            BookEntity onShelf1 = createBook("On Shelf1");
            onShelf1.setShelves(new HashSet<>(Set.of(shelf1)));
            em.merge(onShelf1);

            BookEntity onShelf2 = createBook("On Shelf2");
            onShelf2.setShelves(new HashSet<>(Set.of(shelf2)));
            em.merge(onShelf2);

            BookEntity onBoth = createBook("On Both");
            onBoth.setShelves(new HashSet<>(Set.of(shelf1, shelf2)));
            em.merge(onBoth);
            em.flush();
            em.clear();

            GroupRule group = new GroupRule();
            group.setJoin(JoinType.AND);
            group.setRules(List.of(
                    shelfRule(RuleOperator.INCLUDES_ANY, List.of(String.valueOf(shelf1.getId()), String.valueOf(shelf2.getId()))),
                    shelfRule(RuleOperator.EXCLUDES_ALL, List.of(String.valueOf(shelf2.getId())))
            ));

            List<Long> ids = findMatchingIds(group);
            assertThat(ids).containsExactly(onShelf1.getId());
            assertThat(ids).doesNotContain(onShelf2.getId(), onBoth.getId());
        }

        @Test
        void bugReport2822_orShelfFilter_paginationTruncation() {
            ShelfEntity shelfA = ShelfEntity.builder().user(user).name("Bug Shelf A").build();
            ShelfEntity shelfB = ShelfEntity.builder().user(user).name("Bug Shelf B").build();
            em.persist(shelfA);
            em.persist(shelfB);

            List<Long> expectedIds = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                BookEntity book = createBook("Bug2822 Book " + i);
                if (i < 10) {
                    book.setShelves(new HashSet<>(Set.of(shelfA, shelfB)));
                } else if (i < 15) {
                    book.setShelves(new HashSet<>(Set.of(shelfA)));
                } else {
                    book.setShelves(new HashSet<>(Set.of(shelfB)));
                }
                em.merge(book);
                expectedIds.add(book.getId());
            }

            BookEntity unrelated = createBook("Bug2822 Unrelated");
            em.flush();
            em.clear();

            GroupRule group = new GroupRule();
            group.setJoin(JoinType.OR);
            group.setRules(List.of(
                    shelfRule(RuleOperator.EQUALS, List.of(String.valueOf(shelfA.getId()))),
                    shelfRule(RuleOperator.EQUALS, List.of(String.valueOf(shelfB.getId())))
            ));

            List<Long> pagedIds = findMatchingIdsPaged(group, 20);
            assertThat(pagedIds).hasSize(20);
            assertThat(pagedIds).containsExactlyInAnyOrderElementsOf(expectedIds);
            assertThat(pagedIds).doesNotContain(unrelated.getId());

            List<Long> allIds = findMatchingIds(group);
            assertThat(allIds).hasSize(20);
        }

        @Test
        void bugReport2822_inclusionExclusion_exclusionActuallyWorks() {
            ShelfEntity includeShelf = ShelfEntity.builder().user(user).name("Bug Include").build();
            ShelfEntity excludeShelf = ShelfEntity.builder().user(user).name("Bug Exclude").build();
            ShelfEntity extraShelf = ShelfEntity.builder().user(user).name("Bug Extra").build();
            em.persist(includeShelf);
            em.persist(excludeShelf);
            em.persist(extraShelf);

            BookEntity shouldAppear = createBook("Bug2822 Include Only");
            shouldAppear.setShelves(new HashSet<>(Set.of(includeShelf)));
            em.merge(shouldAppear);

            BookEntity shouldAppear2 = createBook("Bug2822 Include + Extra");
            shouldAppear2.setShelves(new HashSet<>(Set.of(includeShelf, extraShelf)));
            em.merge(shouldAppear2);

            BookEntity shouldNotAppear = createBook("Bug2822 Include + Exclude");
            shouldNotAppear.setShelves(new HashSet<>(Set.of(includeShelf, excludeShelf)));
            em.merge(shouldNotAppear);

            BookEntity excludeOnly = createBook("Bug2822 Exclude Only");
            excludeOnly.setShelves(new HashSet<>(Set.of(excludeShelf)));
            em.merge(excludeOnly);

            BookEntity excludeAndExtra = createBook("Bug2822 Exclude + Extra");
            excludeAndExtra.setShelves(new HashSet<>(Set.of(excludeShelf, extraShelf)));
            em.merge(excludeAndExtra);

            BookEntity noShelf = createBook("Bug2822 No Shelf");
            em.flush();
            em.clear();

            GroupRule group = new GroupRule();
            group.setJoin(JoinType.AND);
            group.setRules(List.of(
                    shelfRule(RuleOperator.EQUALS, List.of(String.valueOf(includeShelf.getId()))),
                    shelfRule(RuleOperator.EXCLUDES_ALL, List.of(String.valueOf(excludeShelf.getId())))
            ));

            List<Long> ids = findMatchingIds(group);
            assertThat(ids).containsExactlyInAnyOrder(shouldAppear.getId(), shouldAppear2.getId());
            assertThat(ids).doesNotContain(
                    shouldNotAppear.getId(),
                    excludeOnly.getId(),
                    excludeAndExtra.getId(),
                    noShelf.getId()
            );

            List<Long> pagedIds = findMatchingIdsPaged(group, 50);
            assertThat(pagedIds).hasSize(2);
            assertThat(pagedIds).containsExactlyInAnyOrder(shouldAppear.getId(), shouldAppear2.getId());
        }

        @Test
        void stressTest_manyBooksManyShelves_correctCountWithPagination() {
            ShelfEntity s1 = ShelfEntity.builder().user(user).name("Stress 1").build();
            ShelfEntity s2 = ShelfEntity.builder().user(user).name("Stress 2").build();
            ShelfEntity s3 = ShelfEntity.builder().user(user).name("Stress 3").build();
            ShelfEntity s4 = ShelfEntity.builder().user(user).name("Stress 4").build();
            ShelfEntity s5 = ShelfEntity.builder().user(user).name("Stress 5").build();
            em.persist(s1);
            em.persist(s2);
            em.persist(s3);
            em.persist(s4);
            em.persist(s5);

            for (int i = 0; i < 50; i++) {
                BookEntity book = createBook("Stress Book " + i);
                book.setShelves(new HashSet<>(Set.of(s1, s2, s3, s4, s5)));
                em.merge(book);
            }

            BookEntity outsideBook = createBook("Stress Outside");
            em.flush();
            em.clear();

            GroupRule group = new GroupRule();
            group.setJoin(JoinType.OR);
            group.setRules(List.of(
                    shelfRule(RuleOperator.EQUALS, List.of(String.valueOf(s1.getId()))),
                    shelfRule(RuleOperator.EQUALS, List.of(String.valueOf(s2.getId()))),
                    shelfRule(RuleOperator.EQUALS, List.of(String.valueOf(s3.getId())))
            ));

            List<Long> pagedIds = findMatchingIdsPaged(group, 50);
            assertThat(pagedIds).hasSize(50);

            List<Long> allIds = findMatchingIds(group);
            assertThat(allIds).hasSize(50);
            assertThat(allIds).doesNotContain(outsideBook.getId());
        }
    }

    @Nested
    class SubtitleFieldTests {
        @Test
        void contains_matchesSubtitleSubstring() {
            BookEntity with = createBook("Book With Subtitle");
            with.getMetadata().setSubtitle("A Journey Through Time");
            em.merge(with.getMetadata());

            BookEntity without = createBook("Book Without Match");
            without.getMetadata().setSubtitle("Cooking Adventures");
            em.merge(without.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.SUBTITLE, RuleOperator.CONTAINS, "journey"));
            assertThat(ids).contains(with.getId());
            assertThat(ids).doesNotContain(without.getId());
        }

        @Test
        void equals_caseInsensitive() {
            BookEntity book = createBook("Some Book");
            book.getMetadata().setSubtitle("The Complete Guide");
            em.merge(book.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.SUBTITLE, RuleOperator.EQUALS, "the complete guide"));
            assertThat(ids).contains(book.getId());
        }

        @Test
        void isEmpty_matchesNullSubtitle() {
            BookEntity noSub = createBook("No Subtitle");

            BookEntity withSub = createBook("With Subtitle");
            withSub.getMetadata().setSubtitle("Has Subtitle");
            em.merge(withSub.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.SUBTITLE, RuleOperator.IS_EMPTY, null));
            assertThat(ids).contains(noSub.getId());
            assertThat(ids).doesNotContain(withSub.getId());
        }
    }

    @Nested
    class DescriptionFieldTests {
        @Test
        void contains_matchesDescriptionSubstring() {
            BookEntity match = createBook("Matching Book");
            match.getMetadata().setDescription("An epic tale of adventure and mystery");
            em.merge(match.getMetadata());

            BookEntity noMatch = createBook("Non Matching");
            noMatch.getMetadata().setDescription("A cookbook for beginners");
            em.merge(noMatch.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.DESCRIPTION, RuleOperator.CONTAINS, "adventure"));
            assertThat(ids).contains(match.getId());
            assertThat(ids).doesNotContain(noMatch.getId());
        }

        @Test
        void doesNotContain_excludesDescriptionSubstring() {
            BookEntity scifi = createBook("Sci-Fi Book");
            scifi.getMetadata().setDescription("A story about space exploration");
            em.merge(scifi.getMetadata());

            BookEntity romance = createBook("Romance Book");
            romance.getMetadata().setDescription("A love story set in Paris");
            em.merge(romance.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.DESCRIPTION, RuleOperator.DOES_NOT_CONTAIN, "space"));
            assertThat(ids).contains(romance.getId());
            assertThat(ids).doesNotContain(scifi.getId());
        }
    }

    @Nested
    class PublisherFieldTests {
        @Test
        void equals_matchesPublisher() {
            BookEntity penguin = createBook("Penguin Book");
            penguin.getMetadata().setPublisher("Penguin Random House");
            em.merge(penguin.getMetadata());

            BookEntity harper = createBook("Harper Book");
            harper.getMetadata().setPublisher("HarperCollins");
            em.merge(harper.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.PUBLISHER, RuleOperator.EQUALS, "penguin random house"));
            assertThat(ids).contains(penguin.getId());
            assertThat(ids).doesNotContain(harper.getId());
        }

        @Test
        void startsWith_matchesPublisherPrefix() {
            BookEntity penguin = createBook("Penguin Book");
            penguin.getMetadata().setPublisher("Penguin Random House");
            em.merge(penguin.getMetadata());

            BookEntity harper = createBook("Harper Book");
            harper.getMetadata().setPublisher("HarperCollins");
            em.merge(harper.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.PUBLISHER, RuleOperator.STARTS_WITH, "penguin"));
            assertThat(ids).contains(penguin.getId());
            assertThat(ids).doesNotContain(harper.getId());
        }

        @Test
        void contains_matchesPublisherSubstring() {
            BookEntity book = createBook("Random House Book");
            book.getMetadata().setPublisher("Penguin Random House");
            em.merge(book.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.PUBLISHER, RuleOperator.CONTAINS, "random"));
            assertThat(ids).contains(book.getId());
        }
    }

    @Nested
    class LanguageFieldTests {
        @Test
        void equals_matchesLanguage() {
            BookEntity english = createBook("English Book");
            english.getMetadata().setLanguage("en");
            em.merge(english.getMetadata());

            BookEntity french = createBook("French Book");
            french.getMetadata().setLanguage("fr");
            em.merge(french.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.LANGUAGE, RuleOperator.EQUALS, "en"));
            assertThat(ids).contains(english.getId());
            assertThat(ids).doesNotContain(french.getId());
        }

        @Test
        void notEquals_excludesLanguage() {
            BookEntity english = createBook("English Book");
            english.getMetadata().setLanguage("en");
            em.merge(english.getMetadata());

            BookEntity french = createBook("French Book");
            french.getMetadata().setLanguage("fr");
            em.merge(french.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.LANGUAGE, RuleOperator.NOT_EQUALS, "en"));
            assertThat(ids).contains(french.getId());
            assertThat(ids).doesNotContain(english.getId());
        }
    }

    @Nested
    class NarratorFieldTests {
        @Test
        void contains_matchesNarratorName() {
            BookEntity book1 = createBook("Narrated Book");
            book1.getMetadata().setNarrator("Stephen Fry");
            em.merge(book1.getMetadata());

            BookEntity book2 = createBook("Other Narrator");
            book2.getMetadata().setNarrator("Jim Dale");
            em.merge(book2.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.NARRATOR, RuleOperator.CONTAINS, "fry"));
            assertThat(ids).contains(book1.getId());
            assertThat(ids).doesNotContain(book2.getId());
        }

        @Test
        void equals_caseInsensitive() {
            BookEntity book = createBook("Narrated Book");
            book.getMetadata().setNarrator("Stephen Fry");
            em.merge(book.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.NARRATOR, RuleOperator.EQUALS, "stephen fry"));
            assertThat(ids).contains(book.getId());
        }
    }

    @Nested
    class IsbnFieldTests {
        @Test
        void isbn13_equals_matchesExact() {
            BookEntity book = createBook("ISBN Book");
            book.getMetadata().setIsbn13("9781234567890");
            em.merge(book.getMetadata());

            BookEntity other = createBook("Other ISBN");
            other.getMetadata().setIsbn13("9789876543210");
            em.merge(other.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.ISBN13, RuleOperator.EQUALS, "9781234567890"));
            assertThat(ids).contains(book.getId());
            assertThat(ids).doesNotContain(other.getId());
        }

        @Test
        void isbn10_contains_matchesSubstring() {
            BookEntity book = createBook("ISBN10 Book");
            book.getMetadata().setIsbn10("0123456789");
            em.merge(book.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.ISBN10, RuleOperator.CONTAINS, "01234"));
            assertThat(ids).contains(book.getId());
        }
    }

    @Nested
    class ContentRatingFieldTests {
        @Test
        void equals_matchesContentRating() {
            BookEntity mature = createBook("Mature Book");
            mature.getMetadata().setContentRating("MATURE");
            em.merge(mature.getMetadata());

            BookEntity teen = createBook("Teen Book");
            teen.getMetadata().setContentRating("TEEN");
            em.merge(teen.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.CONTENT_RATING, RuleOperator.EQUALS, "mature"));
            assertThat(ids).contains(mature.getId());
            assertThat(ids).doesNotContain(teen.getId());
        }
    }

    @Nested
    class SeriesNameFieldTests {
        @Test
        void contains_matchesSeriesNameSubstring() {
            BookEntity hp = createBook("HP Book");
            hp.getMetadata().setSeriesName("Harry Potter");
            em.merge(hp.getMetadata());

            BookEntity lotr = createBook("LOTR Book");
            lotr.getMetadata().setSeriesName("Lord of the Rings");
            em.merge(lotr.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.SERIES_NAME, RuleOperator.CONTAINS, "potter"));
            assertThat(ids).contains(hp.getId());
            assertThat(ids).doesNotContain(lotr.getId());
        }

        @Test
        void startsWith_matchesSeriesNamePrefix() {
            BookEntity hp = createBook("HP Book");
            hp.getMetadata().setSeriesName("Harry Potter");
            em.merge(hp.getMetadata());

            BookEntity lotr = createBook("LOTR Book");
            lotr.getMetadata().setSeriesName("Lord of the Rings");
            em.merge(lotr.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.SERIES_NAME, RuleOperator.STARTS_WITH, "harry"));
            assertThat(ids).contains(hp.getId());
            assertThat(ids).doesNotContain(lotr.getId());
        }

        @Test
        void isEmpty_matchesNoSeriesName() {
            BookEntity noSeries = createBook("No Series");

            BookEntity hasSeries = createBook("Has Series");
            hasSeries.getMetadata().setSeriesName("Some Series");
            em.merge(hasSeries.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.SERIES_NAME, RuleOperator.IS_EMPTY, null));
            assertThat(ids).contains(noSeries.getId());
            assertThat(ids).doesNotContain(hasSeries.getId());
        }
    }

    @Nested
    class LastReadTimeTests {
        @Test
        void withinLast_matchesRecentlyRead() {
            BookEntity recent = createBook("Recently Read");
            UserBookProgressEntity p1 = createProgress(recent, ReadStatus.READING);
            p1.setLastReadTime(Instant.now().minus(1, ChronoUnit.DAYS));
            em.merge(p1);

            BookEntity old = createBook("Read Long Ago");
            UserBookProgressEntity p2 = createProgress(old, ReadStatus.READING);
            p2.setLastReadTime(Instant.now().minus(60, ChronoUnit.DAYS));
            em.merge(p2);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.LAST_READ_TIME, RuleOperator.WITHIN_LAST, 7, null, "days"));
            assertThat(ids).contains(recent.getId());
            assertThat(ids).doesNotContain(old.getId());
        }

        @Test
        void olderThan_matchesOldRead() {
            BookEntity old = createBook("Read Long Ago");
            UserBookProgressEntity p1 = createProgress(old, ReadStatus.READING);
            p1.setLastReadTime(Instant.now().minus(90, ChronoUnit.DAYS));
            em.merge(p1);

            BookEntity recent = createBook("Recently Read");
            UserBookProgressEntity p2 = createProgress(recent, ReadStatus.READING);
            p2.setLastReadTime(Instant.now().minus(1, ChronoUnit.DAYS));
            em.merge(p2);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.LAST_READ_TIME, RuleOperator.OLDER_THAN, 30, null, "days"));
            assertThat(ids).contains(old.getId());
            assertThat(ids).doesNotContain(recent.getId());
        }
    }

    @Nested
    class AddedOnRelativeDateTests {
        @Test
        void withinLast_weeks_matchesRecentBook() {
            BookEntity recent = createBook("Recent Book");
            recent.setAddedOn(Instant.now().minus(5, java.time.temporal.ChronoUnit.DAYS));
            em.merge(recent);

            BookEntity old = createBook("Old Book");
            old.setAddedOn(Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS));
            em.merge(old);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.ADDED_ON, RuleOperator.WITHIN_LAST, 2, null, "weeks"));
            assertThat(ids).contains(recent.getId());
            assertThat(ids).doesNotContain(old.getId());
        }

        @Test
        void olderThan_weeks_matchesOldBook() {
            BookEntity old = createBook("Old Book");
            old.setAddedOn(Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS));
            em.merge(old);

            BookEntity recent = createBook("Recent Book");
            recent.setAddedOn(Instant.now().minus(3, java.time.temporal.ChronoUnit.DAYS));
            em.merge(recent);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.ADDED_ON, RuleOperator.OLDER_THAN, 2, null, "weeks"));
            assertThat(ids).contains(old.getId());
            assertThat(ids).doesNotContain(recent.getId());
        }
    }

    @Nested
    class PublishedDateFieldTests {
        @Test
        void equals_matchesExactDate() {
            BookEntity book = createBook("Exact Date Book");
            book.getMetadata().setPublishedDate(LocalDate.of(2023, 6, 15));
            em.merge(book.getMetadata());

            BookEntity other = createBook("Other Date Book");
            other.getMetadata().setPublishedDate(LocalDate.of(2020, 1, 1));
            em.merge(other.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.PUBLISHED_DATE, RuleOperator.EQUALS, "2023-06-15"));
            assertThat(ids).contains(book.getId());
            assertThat(ids).doesNotContain(other.getId());
        }

        @Test
        void isEmpty_matchesNullPublishedDate() {
            BookEntity noDate = createBook("No Date");

            BookEntity withDate = createBook("With Date");
            withDate.getMetadata().setPublishedDate(LocalDate.of(2023, 1, 1));
            em.merge(withDate.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.PUBLISHED_DATE, RuleOperator.IS_EMPTY, null));
            assertThat(ids).contains(noDate.getId());
            assertThat(ids).doesNotContain(withDate.getId());
        }
    }

    @Nested
    class CategoryFieldTests {
        @Test
        void contains_matchesCategorySubstring() {
            BookEntity fiction = createBook("Fiction Book");
            CategoryEntity cat = CategoryEntity.builder().name("Science Fiction").build();
            em.persist(cat);
            fiction.getMetadata().setCategories(new HashSet<>(Set.of(cat)));
            em.merge(fiction.getMetadata());

            BookEntity history = createBook("History Book");
            CategoryEntity cat2 = CategoryEntity.builder().name("Ancient History").build();
            em.persist(cat2);
            history.getMetadata().setCategories(new HashSet<>(Set.of(cat2)));
            em.merge(history.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.CATEGORIES, RuleOperator.CONTAINS, "fiction"));
            assertThat(ids).contains(fiction.getId());
            assertThat(ids).doesNotContain(history.getId());
        }

        @Test
        void isEmpty_matchesBookWithNoCategories() {
            BookEntity noCats = createBook("No Categories");

            BookEntity withCats = createBook("With Categories");
            CategoryEntity cat = CategoryEntity.builder().name("Fantasy FC").build();
            em.persist(cat);
            withCats.getMetadata().setCategories(new HashSet<>(Set.of(cat)));
            em.merge(withCats.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.CATEGORIES, RuleOperator.IS_EMPTY, null));
            assertThat(ids).contains(noCats.getId());
            assertThat(ids).doesNotContain(withCats.getId());
        }
    }

    @Nested
    class MoodFieldTests {
        @Test
        void includesAny_matchesMood() {
            BookEntity darkBook = createBook("Dark Book");
            MoodEntity dark = MoodEntity.builder().name("Dark").build();
            em.persist(dark);
            darkBook.getMetadata().setMoods(new HashSet<>(Set.of(dark)));
            em.merge(darkBook.getMetadata());

            BookEntity lightBook = createBook("Light Book");
            MoodEntity light = MoodEntity.builder().name("Light").build();
            em.persist(light);
            lightBook.getMetadata().setMoods(new HashSet<>(Set.of(light)));
            em.merge(lightBook.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.MOODS, RuleOperator.INCLUDES_ANY, List.of("Dark")));
            assertThat(ids).contains(darkBook.getId());
            assertThat(ids).doesNotContain(lightBook.getId());
        }

        @Test
        void isEmpty_matchesBookWithNoMoods() {
            BookEntity noMoods = createBook("No Moods");

            BookEntity withMoods = createBook("With Moods");
            MoodEntity mood = MoodEntity.builder().name("Happy FC").build();
            em.persist(mood);
            withMoods.getMetadata().setMoods(new HashSet<>(Set.of(mood)));
            em.merge(withMoods.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.MOODS, RuleOperator.IS_EMPTY, null));
            assertThat(ids).contains(noMoods.getId());
            assertThat(ids).doesNotContain(withMoods.getId());
        }
    }

    @Nested
    class TagFieldTests {
        @Test
        void includesAll_matchesBookWithAllTags() {
            BookEntity bookBoth = createBook("Both Tags");
            TagEntity tag1 = TagEntity.builder().name("Tag1 FC").build();
            TagEntity tag2 = TagEntity.builder().name("Tag2 FC").build();
            em.persist(tag1);
            em.persist(tag2);
            bookBoth.getMetadata().setTags(new HashSet<>(Set.of(tag1, tag2)));
            em.merge(bookBoth.getMetadata());

            BookEntity bookOne = createBook("One Tag");
            TagEntity tag3 = TagEntity.builder().name("Tag1 FC2").build();
            em.persist(tag3);
            bookOne.getMetadata().setTags(new HashSet<>(Set.of(tag3)));
            em.merge(bookOne.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.TAGS, RuleOperator.INCLUDES_ALL, List.of("Tag1 FC", "Tag2 FC")));
            assertThat(ids).contains(bookBoth.getId());
            assertThat(ids).doesNotContain(bookOne.getId());
        }

        @Test
        void isEmpty_matchesBookWithNoTags() {
            BookEntity noTags = createBook("No Tags");

            BookEntity withTags = createBook("With Tags");
            TagEntity tag = TagEntity.builder().name("SomeTag FC").build();
            em.persist(tag);
            withTags.getMetadata().setTags(new HashSet<>(Set.of(tag)));
            em.merge(withTags.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.TAGS, RuleOperator.IS_EMPTY, null));
            assertThat(ids).contains(noTags.getId());
            assertThat(ids).doesNotContain(withTags.getId());
        }
    }

    @Nested
    class IsPhysicalFieldTests {
        @Test
        void equals_true_matchesPhysical() {
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
        void notEquals_true_matchesDigital() {
            BookEntity physical = createBook("Physical Book");
            physical.setIsPhysical(true);
            em.merge(physical);

            BookEntity digital = createBook("Digital Book");
            digital.setIsPhysical(false);
            em.merge(digital);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.IS_PHYSICAL, RuleOperator.NOT_EQUALS, "true"));
            assertThat(ids).contains(digital.getId());
            assertThat(ids).doesNotContain(physical.getId());
        }
    }

    @Nested
    class AbridgedFieldTests {
        @Test
        void equals_true_matchesAbridged() {
            BookEntity abridged = createBook("Abridged Book");
            abridged.getMetadata().setAbridged(true);
            em.merge(abridged.getMetadata());

            BookEntity full = createBook("Full Book");
            full.getMetadata().setAbridged(false);
            em.merge(full.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.ABRIDGED, RuleOperator.EQUALS, "true"));
            assertThat(ids).contains(abridged.getId());
            assertThat(ids).doesNotContain(full.getId());
        }

        @Test
        void equals_false_matchesUnabridged() {
            BookEntity abridged = createBook("Abridged Book");
            abridged.getMetadata().setAbridged(true);
            em.merge(abridged.getMetadata());

            BookEntity full = createBook("Full Book");
            full.getMetadata().setAbridged(false);
            em.merge(full.getMetadata());
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.ABRIDGED, RuleOperator.EQUALS, "false"));
            assertThat(ids).contains(full.getId());
            assertThat(ids).doesNotContain(abridged.getId());
        }
    }

    @Nested
    class DateFinishedRelativeTests {
        @Test
        void thisPeriod_year_matchesThisYearFinished() {
            BookEntity thisYear = createBook("Recently Finished");
            UserBookProgressEntity p1 = createProgress(thisYear, ReadStatus.READ);
            p1.setDateFinished(Instant.now().minus(10, java.time.temporal.ChronoUnit.DAYS));
            em.merge(p1);

            BookEntity lastYear = createBook("Finished Last Year");
            UserBookProgressEntity p2 = createProgress(lastYear, ReadStatus.READ);
            p2.setDateFinished(Instant.now().minus(400, java.time.temporal.ChronoUnit.DAYS));
            em.merge(p2);
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.DATE_FINISHED, RuleOperator.THIS_PERIOD, "year"));
            assertThat(ids).contains(thisYear.getId());
            assertThat(ids).doesNotContain(lastYear.getId());
        }
    }

    @Nested
    class StringEdgeCaseTests {
        @Test
        void contains_caseInsensitive() {
            BookEntity book = createBook("The Great Gatsby");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.TITLE, RuleOperator.CONTAINS, "GREAT"));
            assertThat(ids).contains(book.getId());
        }

        @Test
        void endsWith_matchesTitleSuffix() {
            BookEntity book = createBook("Lord of the Rings");
            BookEntity other = createBook("Harry Potter");
            em.flush();
            em.clear();

            List<Long> ids = findMatchingIds(singleRule(RuleField.TITLE, RuleOperator.ENDS_WITH, "rings"));
            assertThat(ids).contains(book.getId());
            assertThat(ids).doesNotContain(other.getId());
        }
    }
}
