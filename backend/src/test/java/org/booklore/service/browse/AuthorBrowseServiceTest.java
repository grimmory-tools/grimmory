package org.booklore.service.browse;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.booklore.BookloreApplication;
import org.booklore.browse.BrowsePage;
import org.booklore.browse.Link;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
import org.booklore.model.dto.AuthorSummary;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.Library;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.CategoryEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.entity.UserContentRestrictionEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.ContentRestrictionMode;
import org.booklore.model.enums.ContentRestrictionType;
import org.booklore.model.enums.ReadStatus;
import org.booklore.model.entity.TagEntity;
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
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = BookloreApplication.class)
@Transactional
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:authorbrowseservicetest;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE",
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
@Import(AuthorBrowseServiceTest.TestConfig.class)
class AuthorBrowseServiceTest {

    @Autowired
    private AuthorBrowseService browseService;
    @MockitoBean
    private AuthenticationService authenticationService;

    @PersistenceContext
    private EntityManager em;

    private BookLoreUserEntity userEntity;
    private LibraryEntity library;
    private LibraryPathEntity libraryPath;
    private final Map<String, CategoryEntity> categories = new HashMap<>();
    private final Map<String, TagEntity> tags = new HashMap<>();

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
        userEntity = BookLoreUserEntity.builder().username("reader").passwordHash("x").name("Reader").build();
        em.persist(userEntity);
        library = LibraryEntity.builder().name("Lib").icon("book").watch(false)
                .formatPriority(List.of(BookFileType.EPUB)).build();
        em.persist(library);
        libraryPath = LibraryPathEntity.builder().library(library).path("/p").build();
        em.persist(libraryPath);
        when(authenticationService.getAuthenticatedUser()).thenReturn(nonAdminUser());
    }

    private BookLoreUser nonAdminUser() {
        BookLoreUser.UserPermissions permissions = new BookLoreUser.UserPermissions();
        permissions.setAdmin(false);
        return BookLoreUser.builder()
                .id(userEntity.getId())
                .assignedLibraries(List.of(Library.builder().id(library.getId()).build()))
                .permissions(permissions)
                .build();
    }

    private BookLoreUser adminUser() {
        BookLoreUser.UserPermissions permissions = new BookLoreUser.UserPermissions();
        permissions.setAdmin(true);
        return BookLoreUser.builder()
                .id(userEntity.getId())
                .assignedLibraries(List.of())
                .permissions(permissions)
                .build();
    }

    private AuthorEntity author(String name) {
        AuthorEntity author = AuthorEntity.builder().name(name).build();
        em.persist(author);
        return author;
    }

    private BookEntity book(String title, LibraryEntity lib, LibraryPathEntity path, List<AuthorEntity> authors) {
        return book(title, lib, path, authors, null, null, null);
    }

    private BookEntity book(String title, LibraryEntity lib, LibraryPathEntity path, List<AuthorEntity> authors,
                            String seriesName, List<String> categoryNames, String language) {
        BookEntity bookEntity = BookEntity.builder()
                .library(lib).libraryPath(path).addedOn(Instant.now()).deleted(false).build();
        em.persist(bookEntity);
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .book(bookEntity).title(title).seriesName(seriesName).language(language).build();
        metadata.setAuthors(new java.util.ArrayList<>(authors));
        if (categoryNames != null) {
            metadata.setCategories(categoryNames.stream().map(this::category).collect(Collectors.toSet()));
        }
        em.persist(metadata);
        bookEntity.setMetadata(metadata);
        return bookEntity;
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

    private void progress(BookEntity book, ReadStatus status) {
        em.persist(UserBookProgressEntity.builder()
                .user(userEntity).book(book).readStatus(status).lastReadTime(Instant.now()).build());
    }

    private BrowsePage<AuthorSummary> browse(String sort, List<String> facet, String query, String cursor, int page, int size) {
        return browseService.browse(sort, facet, null, query, cursor, PageRequest.of(page, size));
    }

    private List<Long> ids(BrowsePage<AuthorSummary> page) {
        return page.content().stream().map(AuthorSummary::getId).toList();
    }

    @Test
    void returnsPageWithTotalsAndCursorAndLinks() {
        AuthorEntity alice = author("Alice");
        AuthorEntity bob = author("Bob");
        book("A", library, libraryPath, List.of(alice));
        book("B", library, libraryPath, List.of(bob));
        em.flush();

        BrowsePage<AuthorSummary> result = browse(null, null, null, null, 0, 20);

        assertThat(result.content()).hasSize(2);
        assertThat(result.page().totalElements()).isEqualTo(2);
        assertThat(result.page().cursor()).isNotBlank();
        assertThat(result.links().stream().anyMatch(l -> l.rel().contains("self"))).isTrue();
    }

    @Test
    void sortByNameAndSortName() {
        AuthorEntity zeta = author("Zeta Abbott");
        AuthorEntity anna = author("Anna Zimmer");
        book("A", library, libraryPath, List.of(zeta));
        book("B", library, libraryPath, List.of(anna));
        em.flush();

        assertThat(ids(browse("name", null, null, null, 0, 20))).containsExactly(anna.getId(), zeta.getId());
        assertThat(ids(browse("-name", null, null, null, 0, 20))).containsExactly(zeta.getId(), anna.getId());
        assertThat(ids(browse("sortName", null, null, null, 0, 20))).containsExactly(zeta.getId(), anna.getId());
    }

    @Test
    void sortByBookCount() {
        AuthorEntity prolific = author("Prolific");
        AuthorEntity oneHit = author("One Hit");
        book("A", library, libraryPath, List.of(prolific));
        book("B", library, libraryPath, List.of(prolific));
        book("C", library, libraryPath, List.of(oneHit));
        em.flush();

        BrowsePage<AuthorSummary> result = browse("-bookCount", null, null, null, 0, 20);
        assertThat(ids(result)).containsExactly(prolific.getId(), oneHit.getId());
        assertThat(result.content().get(0).getBookCount()).isEqualTo(2);
        assertThat(result.content().get(1).getBookCount()).isEqualTo(1);
    }

    @Test
    void sortBySeriesCountAndAddedOn() {
        AuthorEntity serial = author("Serial");
        AuthorEntity standalone = author("Standalone");
        book("S1", library, libraryPath, List.of(serial), "Saga", null, null);
        book("S2", library, libraryPath, List.of(serial), "Other Saga", null, null);
        book("Solo", library, libraryPath, List.of(standalone));
        em.flush();

        assertThat(ids(browse("-seriesCount", null, null, null, 0, 20)))
                .containsExactly(serial.getId(), standalone.getId());
        assertThat(ids(browse("-addedOn", null, null, null, 0, 20))).hasSize(2);
    }

    @Test
    void sortByLastReadTimeAndAvgRating() {
        AuthorEntity read = author("Read Author");
        AuthorEntity unread = author("Unread Author");
        BookEntity readBook = book("R", library, libraryPath, List.of(read));
        book("U", library, libraryPath, List.of(unread));
        progress(readBook, ReadStatus.READ);
        em.flush();

        assertThat(ids(browse("-lastReadTime", null, null, null, 0, 20)))
                .containsExactly(read.getId(), unread.getId());
        assertThat(ids(browse("-goodreadsRating", null, null, null, 0, 20))).hasSize(2);
        assertThat(ids(browse("-personalRating", null, null, null, 0, 20))).hasSize(2);
    }

    @Test
    void queryFiltersOnNameAndAsin() {
        AuthorEntity tolkien = author("J.R.R. Tolkien");
        tolkien.setAsin("B000AP9A6K");
        AuthorEntity herbert = author("Frank Herbert");
        book("A", library, libraryPath, List.of(tolkien));
        book("B", library, libraryPath, List.of(herbert));
        em.flush();

        assertThat(ids(browse(null, null, "tolkien", null, 0, 20))).containsExactly(tolkien.getId());
        assertThat(ids(browse(null, null, "b000ap9a6k", null, 0, 20))).containsExactly(tolkien.getId());
    }

    @Test
    void matchedFacetIsApplied() {
        AuthorEntity matched = author("Matched");
        matched.setAsin("B0FAKE");
        AuthorEntity unmatched = author("Unmatched");
        book("A", library, libraryPath, List.of(matched));
        book("B", library, libraryPath, List.of(unmatched));
        em.flush();

        assertThat(ids(browse(null, List.of("matched:true"), null, null, 0, 20))).containsExactly(matched.getId());
        assertThat(ids(browse(null, List.of("matched:false"), null, null, 0, 20))).containsExactly(unmatched.getId());
    }

    @Test
    void hasDescriptionFacetIsApplied() {
        AuthorEntity documented = author("Documented");
        documented.setDescription("Has a bio.");
        AuthorEntity bare = author("Bare");
        book("A", library, libraryPath, List.of(documented));
        book("B", library, libraryPath, List.of(bare));
        em.flush();

        assertThat(ids(browse(null, List.of("has_description:true"), null, null, 0, 20)))
                .containsExactly(documented.getId());
    }

    @Test
    void bookCountRangeFacetIsApplied() {
        AuthorEntity one = author("One");
        AuthorEntity three = author("Three");
        book("A", library, libraryPath, List.of(one));
        for (int i = 0; i < 3; i++) {
            book("B" + i, library, libraryPath, List.of(three));
        }
        em.flush();

        assertThat(ids(browse(null, List.of("book_count:1"), null, null, 0, 20))).containsExactly(one.getId());
        assertThat(ids(browse(null, List.of("book_count:2-"), null, null, 0, 20))).containsExactly(three.getId());
        assertThat(ids(browse(null, List.of("book_count:-2"), null, null, 0, 20))).containsExactly(one.getId());
        assertThat(ids(browse(null, List.of("book_count:1-3"), null, null, 0, 20)))
                .containsExactlyInAnyOrder(one.getId(), three.getId());
    }

    @Test
    void invalidBookCountRangeIsRejected() {
        assertThatThrownBy(() -> browse(null, List.of("book_count:abc"), null, null, 0, 20))
                .isInstanceOfSatisfying(APIException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> browse(null, List.of("book_count:5-2"), null, null, 0, 20))
                .isInstanceOfSatisfying(APIException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void genreAndLanguageFacetsAreApplied() {
        AuthorEntity horror = author("Horror Writer");
        AuthorEntity romance = author("Romance Writer");
        book("H", library, libraryPath, List.of(horror), null, List.of("Horror"), "en");
        book("R", library, libraryPath, List.of(romance), null, List.of("Romance"), "fr");
        em.flush();

        assertThat(ids(browse(null, List.of("genre:Horror"), null, null, 0, 20))).containsExactly(horror.getId());
        assertThat(ids(browse(null, List.of("language:fr"), null, null, 0, 20))).containsExactly(romance.getId());
    }

    @Test
    void libraryFacetIsApplied() {
        LibraryEntity otherLibrary = LibraryEntity.builder().name("Other").icon("book").watch(false)
                .formatPriority(List.of(BookFileType.EPUB)).build();
        em.persist(otherLibrary);
        LibraryPathEntity otherPath = LibraryPathEntity.builder().library(otherLibrary).path("/o").build();
        em.persist(otherPath);
        when(authenticationService.getAuthenticatedUser()).thenReturn(adminUser());

        AuthorEntity here = author("Here");
        AuthorEntity there = author("There");
        book("A", library, libraryPath, List.of(here));
        book("B", otherLibrary, otherPath, List.of(there));
        em.flush();

        assertThat(ids(browse(null, List.of("library:" + library.getId()), null, null, 0, 20)))
                .containsExactly(here.getId());
    }

    @Test
    void readStatusFacetUsesBookVocabulary() {
        AuthorEntity finished = author("Finished");
        AuthorEntity reading = author("Reading");
        AuthorEntity unsetRow = author("Unset Row");
        AuthorEntity untouched = author("Untouched");

        BookEntity f = book("F", library, libraryPath, List.of(finished));
        progress(f, ReadStatus.READ);

        BookEntity r = book("R", library, libraryPath, List.of(reading));
        progress(r, ReadStatus.READING);

        BookEntity u = book("U", library, libraryPath, List.of(unsetRow));
        progress(u, ReadStatus.UNSET);

        book("N", library, libraryPath, List.of(untouched));
        em.flush();

        assertThat(ids(browse(null, List.of("read_status:READ"), null, null, 0, 20)))
                .containsExactly(finished.getId());
        assertThat(ids(browse(null, List.of("read_status:reading"), null, null, 0, 20)))
                .containsExactly(reading.getId());
        assertThat(ids(browse(null, List.of("read_status:UNSET"), null, null, 0, 20)))
                .containsExactlyInAnyOrder(unsetRow.getId(), untouched.getId());

        assertThatThrownBy(() -> browse(null, List.of("read_status:some_read"), null, null, 0, 20))
                .isInstanceOfSatisfying(APIException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("Invalid read_status value");
    }

    @Test
    void nonAdminSeesOnlyAuthorsWithVisibleBooks() {
        LibraryEntity otherLibrary = LibraryEntity.builder().name("Other").icon("book").watch(false)
                .formatPriority(List.of(BookFileType.EPUB)).build();
        em.persist(otherLibrary);
        LibraryPathEntity otherPath = LibraryPathEntity.builder().library(otherLibrary).path("/o").build();
        em.persist(otherPath);

        AuthorEntity visible = author("Visible");
        AuthorEntity hidden = author("Hidden");
        AuthorEntity bookless = author("Bookless");
        book("In", library, libraryPath, List.of(visible));
        book("Out", otherLibrary, otherPath, List.of(hidden));
        em.flush();

        assertThat(ids(browse(null, null, null, null, 0, 20))).containsExactly(visible.getId());

        when(authenticationService.getAuthenticatedUser()).thenReturn(adminUser());
        assertThat(ids(browse("name", null, null, null, 0, 20)))
                .containsExactly(bookless.getId(), hidden.getId(), visible.getId());
    }

    @Test
    void nonAdminWithNoLibrariesSeesNothing() {
        AuthorEntity a = author("Alice");
        book("A", library, libraryPath, List.of(a));
        em.flush();
        BookLoreUser.UserPermissions permissions = new BookLoreUser.UserPermissions();
        permissions.setAdmin(false);
        when(authenticationService.getAuthenticatedUser()).thenReturn(BookLoreUser.builder()
                .id(userEntity.getId())
                .assignedLibraries(List.of())
                .permissions(permissions)
                .build());

        BrowsePage<AuthorSummary> result = browse(null, null, null, null, 0, 20);
        assertThat(result.content()).isEmpty();
        assertThat(result.page().totalElements()).isZero();
        assertThat(browseService.findAllIds(null, null, null, null)).isEmpty();
    }

    @Test
    void contentRestrictionsHideAuthorsAndTheirCounts() {
        em.persist(UserContentRestrictionEntity.builder()
                .user(userEntity)
                .restrictionType(ContentRestrictionType.TAG)
                .mode(ContentRestrictionMode.EXCLUDE)
                .value("adult")
                .build());

        AuthorEntity clean = author("Clean");
        AuthorEntity mixed = author("Mixed");
        AuthorEntity restricted = author("Restricted");

        book("C", library, libraryPath, List.of(clean));
        book("M1", library, libraryPath, List.of(mixed));
        BookEntity m2 = book("M2", library, libraryPath, List.of(mixed));
        m2.getMetadata().setTags(java.util.Set.of(tag("adult")));
        BookEntity r = book("R", library, libraryPath, List.of(restricted));
        r.getMetadata().setTags(java.util.Set.of(tag("adult")));
        em.flush();

        BrowsePage<AuthorSummary> result = browse("name", null, null, null, 0, 20);
        assertThat(ids(result)).containsExactly(clean.getId(), mixed.getId());
        AuthorSummary mixedSummary = result.content().get(1);
        assertThat(mixedSummary.getBookCount()).isEqualTo(1);
    }

    @Test
    void cursorWalkIsConsistent() {
        for (int i = 0; i < 5; i++) {
            AuthorEntity a = author(String.format("Author%02d", i));
            book("B" + i, library, libraryPath, List.of(a));
        }
        em.flush();

        BrowsePage<AuthorSummary> page0 = browse("name", null, null, null, 0, 2);
        assertThat(page0.content()).hasSize(2);
        assertThat(page0.page().totalElements()).isEqualTo(5);

        Link next = page0.links().stream().filter(l -> l.rel().contains("next")).findFirst().orElseThrow();
        String cursor = next.href().substring(next.href().indexOf("cursor=") + "cursor=".length());
        BrowsePage<AuthorSummary> page1 = browse("name", null, null, cursor, 0, 2);
        assertThat(page1.content()).hasSize(2);
        assertThat(ids(page1)).doesNotContainAnyElementsOf(ids(page0));
    }

    @Test
    void cursorWithConflictingFacetsIsRejected() {
        AuthorEntity a = author("Alice");
        book("A", library, libraryPath, List.of(a));
        em.flush();
        String cursor = browse(null, null, null, null, 0, 20).page().cursor();
        assertThatThrownBy(() -> browse(null, List.of("matched:true"), null, cursor, 0, 20))
                .isInstanceOfSatisfying(APIException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("does not match");
    }

    @Test
    void unknownSortAndFacetAreRejected() {
        assertThatThrownBy(() -> browse("title", null, null, null, 0, 20))
                .isInstanceOfSatisfying(APIException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("Unknown sort key");
        assertThatThrownBy(() -> browse(null, List.of("shoe_size:12"), null, null, 0, 20))
                .isInstanceOfSatisfying(APIException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("Unknown facet");
    }

    @Test
    void findAllIdsMatchesPageOrderingForSameFilters() {
        for (int i = 0; i < 5; i++) {
            AuthorEntity a = author(String.format("Author%02d", i));
            book("B" + i, library, libraryPath, List.of(a));
        }
        em.flush();

        List<Long> pageIds = ids(browse("name", null, null, null, 0, 20));
        assertThat(browseService.findAllIds("name", null, null, null)).isEqualTo(pageIds);
    }

    @Test
    void findAllIdsAppliesFacets() {
        AuthorEntity matched = author("Matched");
        matched.setAsin("B0FAKE");
        AuthorEntity unmatched = author("Unmatched");
        book("A", library, libraryPath, List.of(matched));
        book("B", library, libraryPath, List.of(unmatched));
        em.flush();

        assertThat(browseService.findAllIds(null, List.of("matched:true"), null, null))
                .containsExactly(matched.getId());
    }
}
