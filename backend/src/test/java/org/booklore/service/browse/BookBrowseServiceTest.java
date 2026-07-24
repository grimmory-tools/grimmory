package org.booklore.service.browse;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.booklore.BookloreApplication;
import org.booklore.browse.BrowsePage;
import org.booklore.browse.Link;
import org.booklore.exception.APIException;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.Library;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.CategoryEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookRepository;
import org.booklore.service.task.TaskCronService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
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
        "spring.datasource.url=jdbc:h2:mem:browseservicetest;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE",
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
@Import(BookBrowseServiceTest.TestConfig.class)
class BookBrowseServiceTest {

    @Autowired
    private BookBrowseService browseService;
    @Autowired
    private BookRepository bookRepository;
    @MockitoBean
    private AuthenticationService authenticationService;

    @PersistenceContext
    private EntityManager em;

    private BookLoreUserEntity userEntity;
    private LibraryEntity library;
    private LibraryPathEntity libraryPath;
    private final Map<String, CategoryEntity> categories = new HashMap<>();

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

    private BookEntity book(String title, List<String> categoryNames) {
        BookEntity bookEntity = BookEntity.builder()
                .library(library).libraryPath(libraryPath).addedOn(Instant.now()).deleted(false).build();
        em.persist(bookEntity);
        BookMetadataEntity metadata = BookMetadataEntity.builder().book(bookEntity).title(title).build();
        metadata.setCategories(categoryNames.stream().map(this::category).collect(Collectors.toSet()));
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

    private BrowsePage<Book> browse(String sort, List<String> facet, String query, String cursor, int page, int size) {
        return browseService.browse(sort, facet, null, query, cursor, PageRequest.of(page, size));
    }

    private String nextCursor(BrowsePage<Book> page) {
        Link next = page.links().stream().filter(l -> l.rel().contains("next")).findFirst().orElseThrow();
        return next.href().substring(next.href().indexOf("cursor=") + "cursor=".length());
    }

    @Test
    void returnsPageWithTotalsAndCursorAndLinks() {
        book("Alpha", List.of());
        book("Bravo", List.of());
        em.flush();

        BrowsePage<Book> result = browse(null, null, null, null, 0, 20);

        assertThat(result.content()).hasSize(2);
        assertThat(result.page().totalElements()).isEqualTo(2);
        assertThat(result.page().cursor()).isNotBlank();
        assertThat(result.links().stream().anyMatch(l -> l.rel().contains("self"))).isTrue();
    }

    @Test
    void sortIsApplied() {
        Long b = book("Bravo", List.of()).getId();
        Long a = book("Alpha", List.of()).getId();
        em.flush();
        assertThat(browse("title", null, null, null, 0, 20).content().stream().map(Book::getId)).containsExactly(a, b);
        assertThat(browse("-title", null, null, null, 0, 20).content().stream().map(Book::getId)).containsExactly(b, a);
    }

    @Test
    void facetIsApplied() {
        Long horror = book("H", List.of("Horror")).getId();
        book("R", List.of("Romance"));
        em.flush();
        BrowsePage<Book> result = browse(null, List.of("genre:Horror"), null, null, 0, 20);
        assertThat(result.content().stream().map(Book::getId)).containsExactly(horror);
        assertThat(result.page().totalElements()).isEqualTo(1);
    }

    @Test
    void queryIsApplied() {
        Long hobbit = book("The Hobbit", List.of()).getId();
        book("Dune", List.of());
        em.flush();
        assertThat(browse(null, null, "hobbit", null, 0, 20).content().stream().map(Book::getId)).containsExactly(hobbit);
    }

    @Test
    void cursorWalkIsConsistent() {
        for (int i = 0; i < 5; i++) {
            book(String.format("Book%02d", i), List.of());
        }
        em.flush();

        BrowsePage<Book> page0 = browse("title", null, null, null, 0, 2);
        assertThat(page0.content()).hasSize(2);
        assertThat(page0.page().totalElements()).isEqualTo(5);

        BrowsePage<Book> page1 = browse("title", null, null, nextCursor(page0), 0, 2);
        assertThat(page1.content()).hasSize(2);
        assertThat(page1.content().stream().map(Book::getId))
                .doesNotContainAnyElementsOf(page0.content().stream().map(Book::getId).toList());
    }

    @Test
    void cursorWithConflictingFacetsIsRejected() {
        book("Alpha", List.of("Horror"));
        em.flush();
        String cursor = browse(null, null, null, null, 0, 20).page().cursor();
        assertThatThrownBy(() -> browse(null, List.of("genre:Horror"), null, cursor, 0, 20))
                .isInstanceOfSatisfying(APIException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("does not match");
    }

    @Test
    void deferredSortKeyIsRejected() {
        book("Alpha", List.of());
        em.flush();
        assertThatThrownBy(() -> browse("random", null, null, null, 0, 20))
                .isInstanceOfSatisfying(APIException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("Unknown sort key");
        assertThatThrownBy(() -> browse("fileSizeKb", null, null, null, 0, 20))
                .isInstanceOfSatisfying(APIException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("Unknown sort key");
    }

    @Test
    void findAllIdsMatchesPageOrderingForSameFilters() {
        for (int i = 0; i < 5; i++) {
            book(String.format("Book%02d", i), List.of());
        }
        em.flush();

        List<Long> pageIds = browse("title", null, null, null, 0, 20).content().stream().map(Book::getId).toList();
        List<Long> allIds = browseService.findAllIds("title", null, null, null);

        assertThat(allIds).isEqualTo(pageIds);
    }

    @Test
    void findAllIdsAppliesFacets() {
        Long horror = book("H", List.of("Horror")).getId();
        book("R", List.of("Romance"));
        em.flush();
        assertThat(browseService.findAllIds(null, List.of("genre:Horror"), null, null)).containsExactly(horror);
    }

    @Test
    void findAllIdsRespectsLibraryScoping() {
        Long inLibrary = book("InLibrary", List.of()).getId();
        LibraryEntity otherLibrary = LibraryEntity.builder().name("Other").icon("book").watch(false)
                .formatPriority(List.of(BookFileType.EPUB)).build();
        em.persist(otherLibrary);
        LibraryPathEntity otherPath = LibraryPathEntity.builder().library(otherLibrary).path("/o").build();
        em.persist(otherPath);
        BookEntity outside = BookEntity.builder()
                .library(otherLibrary).libraryPath(otherPath).addedOn(Instant.now()).deleted(false).build();
        em.persist(outside);
        em.persist(BookMetadataEntity.builder().book(outside).title("Outside").build());
        em.flush();

        assertThat(browseService.findAllIds(null, null, null, null)).containsExactly(inLibrary);
    }

    @Test
    void wrapLegacyAddsCursorAndLinksToExistingPage() {
        Book book = Book.builder().id(1L).build();
        org.springframework.data.domain.Page<Book> page =
                new org.springframework.data.domain.PageImpl<>(List.of(book), PageRequest.of(0, 20), 1);

        BrowsePage<Book> wrapped = browseService.wrapLegacy(page, PageRequest.of(0, 20));

        assertThat(wrapped.content()).hasSize(1);
        assertThat(wrapped.page().totalElements()).isEqualTo(1);
        assertThat(wrapped.page().cursor()).isNotBlank();
        assertThat(wrapped.links().stream().anyMatch(l -> l.rel().contains("self"))).isTrue();
    }

    @Test
    void nonAdminSeesOnlyAssignedLibraries() {
        book("InLibrary", List.of());
        LibraryEntity otherLibrary = LibraryEntity.builder().name("Other").icon("book").watch(false)
                .formatPriority(List.of(BookFileType.EPUB)).build();
        em.persist(otherLibrary);
        LibraryPathEntity otherPath = LibraryPathEntity.builder().library(otherLibrary).path("/o").build();
        em.persist(otherPath);
        BookEntity outside = BookEntity.builder()
                .library(otherLibrary).libraryPath(otherPath).addedOn(Instant.now()).deleted(false).build();
        em.persist(outside);
        BookMetadataEntity md = BookMetadataEntity.builder().book(outside).title("Outside").build();
        em.persist(md);
        em.flush();

        BrowsePage<Book> result = browse(null, null, null, null, 0, 20);
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).getId()).isNotEqualTo(outside.getId());
    }
}
