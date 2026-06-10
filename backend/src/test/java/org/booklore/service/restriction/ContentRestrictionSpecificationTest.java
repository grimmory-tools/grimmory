package org.booklore.service.restriction;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.booklore.BookloreApplication;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.CategoryEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.entity.MoodEntity;
import org.booklore.model.entity.TagEntity;
import org.booklore.model.entity.UserContentRestrictionEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.ContentRestrictionMode;
import org.booklore.model.enums.ContentRestrictionType;
import org.booklore.model.dto.Book;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserContentRestrictionRepository;
import org.booklore.security.policy.ContentRestrictionSpecification;
import org.springframework.data.jpa.domain.Specification;
import org.booklore.service.book.BookQueryService;
import org.booklore.service.task.TaskCronService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SpringBootTest(classes = BookloreApplication.class)
@Transactional
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:restrictiontest;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE",
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
@Import(ContentRestrictionSpecificationTest.TestConfig.class)
class ContentRestrictionSpecificationTest {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private ContentRestrictionService contentRestrictionService;

    @Autowired
    private BookQueryService bookQueryService;

    @Autowired
    private UserContentRestrictionRepository restrictionRepository;

    @PersistenceContext
    private EntityManager em;

    private BookLoreUserEntity user;
    private LibraryEntity library;
    private LibraryPathEntity libraryPath;
    private final Map<String, CategoryEntity> categories = new HashMap<>();
    private final Map<String, TagEntity> tags = new HashMap<>();
    private final Map<String, MoodEntity> moods = new HashMap<>();

    @TestConfiguration
    public static class TestConfig {
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

    @BeforeEach
    void seed() {
        user = BookLoreUserEntity.builder().username("restricted").passwordHash("x").name("Restricted").build();
        em.persist(user);

        library = LibraryEntity.builder().name("Lib").icon("book").watch(false)
                .formatPriority(List.of(BookFileType.EPUB)).build();
        em.persist(library);
        libraryPath = LibraryPathEntity.builder().library(library).path("/p").build();
        em.persist(libraryPath);

        book("horror-cat", null, null, List.of("Horror"), List.of(), List.of());
        book("romance-cat", null, null, List.of("Romance"), List.of(), List.of());
        book("horror-and-romance", null, null, List.of("Horror", "Romance"), List.of(), List.of());
        book("explicit-tag", null, null, List.of(), List.of("Explicit"), List.of());
        book("dark-mood", null, null, List.of(), List.of(), List.of("Dark"));
        book("mature-rating", "Mature", null, List.of(), List.of(), List.of());
        book("everyone-rating", "Everyone", null, List.of(), List.of(), List.of());
        book("age-18", null, 18, List.of(), List.of(), List.of());
        book("age-12", null, 12, List.of(), List.of(), List.of());
        book("age-null", null, null, List.of(), List.of(), List.of());
        book("bare-metadata", null, null, List.of(), List.of(), List.of());
        bookWithoutMetadata("no-metadata");

        em.flush();
    }

    private BookEntity book(String title, String contentRating, Integer ageRating,
                            List<String> categoryNames, List<String> tagNames, List<String> moodNames) {
        BookEntity bookEntity = BookEntity.builder()
                .library(library).libraryPath(libraryPath).addedOn(Instant.now()).deleted(false).build();
        em.persist(bookEntity);

        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .book(bookEntity).title(title).contentRating(contentRating).ageRating(ageRating).build();
        metadata.setCategories(categoryNames.stream().map(this::category).collect(Collectors.toSet()));
        metadata.setTags(tagNames.stream().map(this::tag).collect(Collectors.toSet()));
        metadata.setMoods(moodNames.stream().map(this::mood).collect(Collectors.toSet()));
        em.persist(metadata);
        bookEntity.setMetadata(metadata);
        return bookEntity;
    }

    private void bookWithoutMetadata(String title) {
        BookEntity bookEntity = BookEntity.builder()
                .library(library).libraryPath(libraryPath).addedOn(Instant.now()).deleted(false).build();
        em.persist(bookEntity);
    }

    private CategoryEntity category(String name) {
        return categories.computeIfAbsent(name, n -> {
            CategoryEntity e = CategoryEntity.builder().name(n).build();
            em.persist(e);
            return e;
        });
    }

    private TagEntity tag(String name) {
        return tags.computeIfAbsent(name, n -> {
            TagEntity e = TagEntity.builder().name(n).build();
            em.persist(e);
            return e;
        });
    }

    private MoodEntity mood(String name) {
        return moods.computeIfAbsent(name, n -> {
            MoodEntity e = MoodEntity.builder().name(n).build();
            em.persist(e);
            return e;
        });
    }

    private void restrict(ContentRestrictionType type, ContentRestrictionMode mode, String value) {
        UserContentRestrictionEntity entity = UserContentRestrictionEntity.builder()
                .user(user).restrictionType(type).mode(mode).value(value).build();
        em.persist(entity);
        em.flush();
    }

    private Specification<BookEntity> restrictionSpec() {
        return ContentRestrictionSpecification.from(restrictionRepository.findByUserId(user.getId()));
    }

    private void assertEquivalent() {
        List<BookEntity> all = bookRepository.findAll();
        Set<Long> expected = contentRestrictionService.applyRestrictions(all, user.getId()).stream()
                .map(BookEntity::getId).collect(Collectors.toSet());
        Set<Long> actual = bookRepository.findAll(restrictionSpec()).stream()
                .map(BookEntity::getId).collect(Collectors.toSet());
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void noRestrictionsKeepsEverything() {
        assertEquivalent();
        assertThat(bookRepository.findAll(restrictionSpec()))
                .hasSize(bookRepository.findAll().size());
    }

    @Test
    void excludeCategory() {
        restrict(ContentRestrictionType.CATEGORY, ContentRestrictionMode.EXCLUDE, "Horror");
        assertEquivalent();
    }

    @Test
    void excludeTag() {
        restrict(ContentRestrictionType.TAG, ContentRestrictionMode.EXCLUDE, "Explicit");
        assertEquivalent();
    }

    @Test
    void excludeMood() {
        restrict(ContentRestrictionType.MOOD, ContentRestrictionMode.EXCLUDE, "Dark");
        assertEquivalent();
    }

    @Test
    void excludeContentRating() {
        restrict(ContentRestrictionType.CONTENT_RATING, ContentRestrictionMode.EXCLUDE, "Mature");
        assertEquivalent();
    }

    @Test
    void excludeAgeRatingThreshold() {
        restrict(ContentRestrictionType.AGE_RATING, ContentRestrictionMode.EXCLUDE, "16");
        assertEquivalent();
    }

    @Test
    void allowOnlyCategory() {
        restrict(ContentRestrictionType.CATEGORY, ContentRestrictionMode.ALLOW_ONLY, "Romance");
        assertEquivalent();
    }

    @Test
    void allowOnlyContentRating() {
        restrict(ContentRestrictionType.CONTENT_RATING, ContentRestrictionMode.ALLOW_ONLY, "Everyone");
        assertEquivalent();
    }

    @Test
    void caseInsensitiveMatching() {
        restrict(ContentRestrictionType.CATEGORY, ContentRestrictionMode.EXCLUDE, "horror");
        assertEquivalent();
    }

    @Test
    void multipleExcludeTypesCombined() {
        restrict(ContentRestrictionType.CATEGORY, ContentRestrictionMode.EXCLUDE, "Horror");
        restrict(ContentRestrictionType.CONTENT_RATING, ContentRestrictionMode.EXCLUDE, "Mature");
        restrict(ContentRestrictionType.AGE_RATING, ContentRestrictionMode.EXCLUDE, "16");
        assertEquivalent();
    }

    @Test
    void excludeAndAllowCombined() {
        restrict(ContentRestrictionType.CATEGORY, ContentRestrictionMode.ALLOW_ONLY, "Horror");
        restrict(ContentRestrictionType.CATEGORY, ContentRestrictionMode.ALLOW_ONLY, "Romance");
        restrict(ContentRestrictionType.CONTENT_RATING, ContentRestrictionMode.EXCLUDE, "Mature");
        assertEquivalent();
    }

    @Test
    void allowOnlyMultipleValuesWithinType() {
        restrict(ContentRestrictionType.CATEGORY, ContentRestrictionMode.ALLOW_ONLY, "Horror");
        restrict(ContentRestrictionType.CATEGORY, ContentRestrictionMode.ALLOW_ONLY, "Romance");
        assertEquivalent();
    }

    @Test
    void paginatedTotalReflectsRestrictedSet() {
        restrict(ContentRestrictionType.CATEGORY, ContentRestrictionMode.EXCLUDE, "Horror");
        Set<Long> permitted = permittedIds();

        var page = bookQueryService.getAllBooksByLibraryIdsPaged(List.of(library.getId()), user.getId(), PageRequest.of(0, 100));

        assertThat(page.getTotalElements()).isEqualTo(permitted.size());
        assertThat(page.getContent().stream().map(Book::getId).collect(Collectors.toSet())).isEqualTo(permitted);
    }

    @Test
    void paginatedPagesStayFullDespiteRestrictions() {
        restrict(ContentRestrictionType.CATEGORY, ContentRestrictionMode.EXCLUDE, "Horror");
        Set<Long> permitted = permittedIds();
        int pageSize = 4;

        var firstPage = bookQueryService.getAllBooksByLibraryIdsPaged(List.of(library.getId()), user.getId(), PageRequest.of(0, pageSize));

        assertThat(firstPage.getTotalElements()).isEqualTo(permitted.size());
        assertThat(firstPage.getContent()).hasSize(pageSize);

        Set<Long> collected = new java.util.HashSet<>();
        int pages = (int) Math.ceil((double) permitted.size() / pageSize);
        for (int p = 0; p < pages; p++) {
            bookQueryService.getAllBooksByLibraryIdsPaged(List.of(library.getId()), user.getId(), PageRequest.of(p, pageSize))
                    .getContent().forEach(b -> collected.add(b.getId()));
        }
        assertThat(collected).isEqualTo(permitted);
    }

    private Set<Long> permittedIds() {
        return contentRestrictionService.applyRestrictions(bookRepository.findAll(), user.getId()).stream()
                .map(BookEntity::getId).collect(Collectors.toSet());
    }
    @Test
    void emptyLibraryScopeReturnsEmptyPage() {
        var page = bookQueryService.getAllBooksByLibraryIdsPaged(List.of(), user.getId(), PageRequest.of(0, 20));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    void foreignLibraryBooksAreNotReturned() {
        LibraryEntity otherLibrary = LibraryEntity.builder().name("Other").icon("book").watch(false)
                .formatPriority(List.of(BookFileType.EPUB)).build();
        em.persist(otherLibrary);
        BookEntity foreign = BookEntity.builder().library(otherLibrary).addedOn(Instant.now()).deleted(false).build();
        em.persist(foreign);
        em.flush();

        var page = bookQueryService.getAllBooksByLibraryIdsPaged(List.of(library.getId()), user.getId(), PageRequest.of(0, 100));

        assertThat(page.getContent().stream().map(Book::getId)).doesNotContain(foreign.getId());
    }
}
