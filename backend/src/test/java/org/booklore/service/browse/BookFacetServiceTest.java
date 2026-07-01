package org.booklore.service.browse;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.booklore.BookloreApplication;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.Library;
import org.booklore.model.dto.browse.FacetGroupsResponse;
import org.booklore.model.dto.browse.FacetGroupsResponse.FacetGroup;
import org.booklore.model.dto.browse.FacetGroupsResponse.FacetLink;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.CategoryEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.enums.BookFileType;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = BookloreApplication.class)
@Transactional
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:facettest;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE",
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
@Import(BookFacetServiceTest.TestConfig.class)
class BookFacetServiceTest {

    @Autowired
    private BookFacetService facetService;
    @MockitoBean
    private AuthenticationService authenticationService;

    @PersistenceContext
    private EntityManager em;

    private BookLoreUserEntity userEntity;
    private LibraryEntity library;
    private LibraryPathEntity libraryPath;
    private final Map<String, CategoryEntity> categories = new HashMap<>();
    private final Map<String, AuthorEntity> authors = new HashMap<>();

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

    private void book(String title, String genre, String authorName) {
        BookEntity bookEntity = BookEntity.builder()
                .library(library).libraryPath(libraryPath).addedOn(Instant.now()).deleted(false).build();
        em.persist(bookEntity);
        BookMetadataEntity metadata = BookMetadataEntity.builder().book(bookEntity).title(title).build();
        metadata.setCategories(java.util.Set.of(category(genre)));
        metadata.setAuthors(List.of(author(authorName)));
        em.persist(metadata);
        bookEntity.setMetadata(metadata);
    }

    private CategoryEntity category(String name) {
        return categories.computeIfAbsent(name, n -> {
            CategoryEntity e = CategoryEntity.builder().name(n).build();
            em.persist(e);
            return e;
        });
    }

    private AuthorEntity author(String name) {
        return authors.computeIfAbsent(name, n -> {
            AuthorEntity e = AuthorEntity.builder().name(n).build();
            em.persist(e);
            return e;
        });
    }

    private FacetGroup group(FacetGroupsResponse response, String key) {
        return response.facets().stream()
                .filter(g -> g.metadata() != null && key.equals(g.metadata().key()))
                .findFirst().orElseThrow();
    }

    private Long count(FacetGroup group, String value) {
        Optional<FacetLink> link = group.links().stream().filter(l -> value.equals(l.value())).findFirst();
        return link.map(l -> l.properties().numberOfItems()).orElse(null);
    }

    @Test
    void countsDiscreteFacetsWithCounts() {
        book("A", "Horror", "Alice");
        book("B", "Romance", "Bob");
        em.flush();

        FacetGroupsResponse response = facetService.getFacets(null, null, null);

        assertThat(count(group(response, "genre"), "Horror")).isEqualTo(1);
        assertThat(count(group(response, "genre"), "Romance")).isEqualTo(1);
        assertThat(group(response, "author").links()).extracting(FacetLink::value).contains("Alice", "Bob");
    }

    @Test
    void selectedFacetIsOmittedFromItsOwnCounts() {
        book("A", "Horror", "Alice");
        book("B", "Horror", "Alice");
        book("C", "Romance", "Bob");
        em.flush();

        FacetGroupsResponse response = facetService.getFacets(List.of("genre:Horror"), null, null);

        // genre omits itself: both Horror (2) and Romance (1) still appear with full counts.
        assertThat(count(group(response, "genre"), "Horror")).isEqualTo(2);
        assertThat(count(group(response, "genre"), "Romance")).isEqualTo(1);

        // a different facet honors the genre:Horror filter: only Horror authors remain.
        assertThat(group(response, "author").links()).extracting(FacetLink::value).containsExactly("Alice");
    }

    @Test
    void valuesAreOrderedByCountDescending() {
        book("A", "Horror", "Alice");
        book("B", "Horror", "Alice");
        book("C", "Romance", "Bob");
        em.flush();

        List<String> genres = group(facetService.getFacets(null, null, null), "genre").links()
                .stream().map(FacetLink::value).toList();
        assertThat(genres).containsExactly("Horror", "Romance");
    }

    @Test
    void linksCarryToggleHrefAndCount() {
        book("A", "Horror", "Alice");
        em.flush();

        FacetLink horror = group(facetService.getFacets(null, null, null), "genre").links()
                .stream().filter(l -> "Horror".equals(l.value())).findFirst().orElseThrow();
        assertThat(horror.href()).isEqualTo("/api/v1/books/page?facet=genre%3AHorror");
        assertThat(horror.properties().numberOfItems()).isEqualTo(1);
        assertThat(horror.rel()).isEqualTo("facet");
    }

    @Test
    void responseIsCachedPerParameters() {
        book("A", "Horror", "Alice");
        em.flush();
        FacetGroupsResponse first = facetService.getFacets(null, null, null);
        FacetGroupsResponse second = facetService.getFacets(null, null, null);
        assertThat(first).isSameAs(second);
    }

    @Test
    void includesSortGroup() {
        book("A", "Horror", "Alice");
        em.flush();
        FacetGroup sort = group(facetService.getFacets(null, null, null), "sort");
        assertThat(sort.metadata().rel()).isEqualTo("sort");
        assertThat(sort.links()).extracting(FacetLink::value).contains("title", "-title");
    }
}
