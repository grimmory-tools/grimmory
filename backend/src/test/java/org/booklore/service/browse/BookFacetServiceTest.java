package org.booklore.service.browse;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.booklore.BookloreApplication;
import org.booklore.browse.Link;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.Library;
import org.booklore.model.dto.browse.FacetGroupsResponse;
import org.booklore.model.dto.browse.FacetGroupsResponse.FacetGroup;
import org.booklore.model.dto.browse.FacetGroupsResponse.FacetLink;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.CategoryEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.service.opds.MagicShelfBookService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    @Autowired
    private ObjectMapper springMapper;
    @MockitoBean
    private AuthenticationService authenticationService;
    @MockitoBean
    private MagicShelfBookService magicShelfBookService;

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
        facetService.clearCache();
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

    private BookMetadataEntity book(String title, String genre, String authorName) {
        BookEntity bookEntity = BookEntity.builder()
                .library(library).libraryPath(libraryPath).addedOn(Instant.now()).deleted(false).build();
        em.persist(bookEntity);
        BookMetadataEntity metadata = BookMetadataEntity.builder().book(bookEntity).title(title).build();
        metadata.setCategories(java.util.Set.of(category(genre)));
        metadata.setAuthors(List.of(author(authorName)));
        em.persist(metadata);
        bookEntity.setMetadata(metadata);
        return metadata;
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

    private FacetGroup facet(String key, List<String> selection) {
        return facet(key, selection, null);
    }

    private FacetGroup facet(String key, List<String> selection, String facetLogic) {
        return group(facetService.getFacet(key, selection, facetLogic, null, null, PageRequest.of(0, 100)), key);
    }

    private Long count(FacetGroup group, String value) {
        Optional<FacetLink> link = group.links().stream().filter(l -> value.equals(l.value())).findFirst();
        return link.map(l -> l.properties().numberOfItems()).orElse(null);
    }

    private FacetLink link(FacetGroup group, String value) {
        return group.links().stream()
                .filter(l -> value.equals(l.value()))
                .findFirst().orElseThrow();
    }

    @Test
    void scalarValuesAndRatingBandsCountMatchingBooks() {
        BookMetadataEntity first = book("A", "Horror", "Alice");
        first.setLanguage("en");
        first.setGoodreadsRating(3.5);
        BookMetadataEntity second = book("B", "Romance", "Bob");
        second.setLanguage("en");
        second.setGoodreadsRating(4.5);
        BookMetadataEntity third = book("C", "Fantasy", "Cara");
        third.setLanguage("fr");
        third.setGoodreadsRating(3.5);
        em.flush();

        assertThat(count(facet("language", null), "en")).isEqualTo(2);
        assertThat(count(facet("language", null), "fr")).isEqualTo(1);
        FacetGroup ratings = facet("goodreads_rating", List.of("language:en"));
        assertThat(count(ratings, "3..4")).isEqualTo(1);
        assertThat(count(ratings, "4.5..*")).isEqualTo(1);
    }

    @Test
    void repeatedAuthorCreditsAndFileTypesCountEachBookOnce() {
        BookMetadataEntity metadata = book("A", "Horror", "Alice");
        metadata.setAuthors(List.of(author("Alice"), author("Alice")));
        for (String name : List.of("first.epub", "second.epub")) {
            em.persist(BookFileEntity.builder().book(metadata.getBook())
                    .fileName(name).fileSubPath("").isBookFormat(true)
                    .bookType(BookFileType.EPUB).build());
        }
        em.flush();

        assertThat(count(facet("author", null), "Alice")).isEqualTo(1);
        assertThat(count(facet("file_type", null), "EPUB")).isEqualTo(1);
    }

    @Test
    void nestedCollectionFilterKeepsScalarAndBandCountsDistinct() {
        BookMetadataEntity first = book("A", "Horror", "Alice");
        first.setAuthors(List.of(author("Alice"), author("Bob")));
        first.setLanguage("en");
        first.setGoodreadsRating(3.5);
        BookMetadataEntity second = book("B", "Romance", "Alice");
        second.setLanguage("en");
        second.setGoodreadsRating(3.5);
        when(magicShelfBookService.toSpecification(userEntity.getId(), 99L))
                .thenReturn((root, query, cb) ->
                        cb.isNotNull(root.join("metadata").join("authors").get("id")));
        em.flush();

        List<String> selection = List.of("shelf:magic:99");
        assertThat(count(facet("language", selection), "en")).isEqualTo(2);
        assertThat(count(facet("goodreads_rating", selection), "3..4")).isEqualTo(2);
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
    void linksCarryAddHrefAndCount() {
        book("A", "Horror", "Alice");
        em.flush();

        FacetLink horror = link(group(facetService.getFacets(null, null, null), "genre"), "Horror");
        assertThat(horror.href()).isEqualTo("/api/v1/books/page?facet=genre%3AHorror");
        assertThat(horror.properties().numberOfItems()).isEqualTo(1);
        assertThat(horror.rel()).containsExactly("facet");
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
        assertThat(sort.links()).allSatisfy(l -> assertThat(l.rel()).containsExactly("sort"));
    }

    @Test
    void emptyFacetListBehavesLikeNullFacet() {
        book("A", "Horror", "Alice");
        book("B", "Romance", "Bob");
        em.flush();

        FacetGroupsResponse withNull = facetService.getFacets(null, null, null);
        FacetGroupsResponse withEmpty = facetService.getFacets(List.of(), null, null);

        assertThat(count(group(withEmpty, "genre"), "Horror")).isEqualTo(count(group(withNull, "genre"), "Horror"));
        assertThat(count(group(withEmpty, "genre"), "Romance")).isEqualTo(count(group(withNull, "genre"), "Romance"));
        assertThat(count(group(withEmpty, "genre"), "Horror")).isEqualTo(1);
    }

    @Test
    void multipleSelectionsFilterAcrossFacets() {
        book("A", "Horror", "Alice");
        book("B", "Horror", "Bob");
        book("C", "Romance", "Alice");
        em.flush();

        FacetGroupsResponse response = facetService.getFacets(List.of("genre:Horror", "author:Alice"), null, null);

        assertThat(count(group(response, "genre"), "Horror")).isEqualTo(1);
        assertThat(count(group(response, "genre"), "Romance")).isEqualTo(1);

        assertThat(count(group(response, "author"), "Alice")).isEqualTo(1);
        assertThat(count(group(response, "author"), "Bob")).isEqualTo(1);
    }

    @Test
    void facetLogicCombinesSelectedValues() {
        book("A", "Horror", "Alice");
        book("B", "Romance", "Bob");
        book("C", "Fantasy", "Cara");
        em.flush();

        List<String> genres = List.of("genre:Horror", "genre:Romance");

        assertThat(group(facetService.getFacets(genres, "or", null), "author").links())
                .extracting(FacetLink::value).containsExactlyInAnyOrder("Alice", "Bob");

        assertThat(group(facetService.getFacets(genres, "and", null), "author").links()).isEmpty();

        assertThat(group(facetService.getFacets(genres, "not", null), "author").links())
                .extracting(FacetLink::value).containsExactly("Cara");
    }

    @Test
    void truncatesValuesAtMax() {
        for (int i = 0; i < 101; i++) {
            book("T" + i, "Genre" + i, "Author" + i);
        }
        em.flush();

        FacetGroupsResponse response = facetService.getFacets(null, null, null);

        assertThat(group(response, "genre").links()).hasSize(100);
        assertThat(group(response, "author").links()).hasSize(100);
    }

    @Test
    void individualFacetPagesWithNextLink() {
        book("A", "Horror", "Alice");
        book("B", "Romance", "Alice");
        book("C", "Fantasy", "Bob");
        em.flush();

        List<String> selection = List.of("genre:Horror", "author:Alice");
        FacetGroupsResponse first = facetService.getFacet("genre", selection, null, null, null, PageRequest.of(0, 1));
        FacetGroupsResponse last = facetService.getFacet("genre", selection, null, null, null, PageRequest.of(1, 1));

        assertThat(group(first, "genre").links()).extracting(FacetLink::value).containsExactly("Horror");
        assertThat(group(first, "genre").links().getFirst().rel()).containsExactly("self", "facet");
        assertThat(first.links().getLast().href())
                .isEqualTo("/api/v1/books/facets/genre?facet=genre%3AHorror&facet=author%3AAlice&page=1&size=1");
        assertThat(group(last, "genre").links()).extracting(FacetLink::value).containsExactly("Romance");
        assertThat(last.links()).extracting(Link::rel).containsExactly(List.of("self"));
    }

    @Test
    void individualNumberFacetsMatchTheOverallEndpoint() {
        BookMetadataEntity metadata = book("A", "Genre", "Author");
        metadata.setPageCount(120);
        metadata.setGoodreadsRating(4.2);
        em.flush();

        FacetGroupsResponse overall = facetService.getFacets(null, null, null);
        for (String key : List.of("page_count", "goodreads_rating")) {
            FacetGroupsResponse single = facetService.getFacet(key, null, null, null, null, PageRequest.of(0, 1));
            assertThat(single.facets()).containsExactly(group(overall, key));
            assertThat(single.links()).extracting(Link::rel).containsExactly(List.of("self"));
        }
    }

    @Test
    void numberFacetBoundsCoverValuesPastTheCap() {
        for (int i = 1; i <= 101; i++) {
            book("T" + i, "Genre", "Author").setPageCount(i);
        }
        em.flush();

        FacetGroup pageCount = group(facetService.getFacets(null, null, null), "page_count");

        assertThat(pageCount.links()).isEmpty();
        assertThat(pageCount.metadata().min().intValue()).isEqualTo(1);
        assertThat(pageCount.metadata().max().intValue()).isEqualTo(101);
        assertThat(group(facetService.getFacets(null, null, null), "genre").metadata().max()).isNull();
    }

    @Test
    void ratingFacetCountsBandsAcrossEveryValue() {
        for (int i = 0; i < 150; i++) {
            book("R" + i, "Genre", "Author").setGoodreadsRating(3.5 + i / 1000.0);
        }
        book("Four", "Genre", "Author").setGoodreadsRating(4.0);
        book("High", "Genre", "Author").setGoodreadsRating(4.8);
        em.flush();

        FacetGroup goodreads = group(facetService.getFacets(List.of("goodreads_rating:4..4.5"), null, null), "goodreads_rating");

        assertThat(goodreads.links()).extracting(FacetLink::value)
                .containsExactly("0..1", "1..2", "2..3", "3..4", "4..4.5", "4.5..*");
        assertThat(count(goodreads, "0..1")).isZero();
        assertThat(count(goodreads, "3..4")).isEqualTo(151);
        assertThat(count(goodreads, "4..4.5")).isEqualTo(1);
        assertThat(count(goodreads, "4.5..*")).isEqualTo(1);
        assertThat(link(goodreads, "4..4.5").rel()).containsExactly("self", "facet");
    }

    @Test
    void individualFacetSearchesValuesPastTheTopHundred() {
        for (int i = 0; i < 120; i++) {
            book("T" + i, "Genre" + i, "Author");
        }
        book("Rare", "Zebra Fiction", "Author");
        em.flush();

        FacetGroupsResponse response = facetService.getFacet("genre", null, null, null, "zebra", PageRequest.of(0, 20));

        assertThat(group(response, "genre").links()).extracting(FacetLink::value).containsExactly("Zebra Fiction");
        assertThat(response.links().getFirst().href()).isEqualTo("/api/v1/books/facets/genre?search=zebra&page=0&size=20");
    }

    @Test
    void individualFacetSearchIsLimitedToNameFacets() {
        assertThatThrownBy(() -> facetService.getFacet("page_count", null, null, null, "12", PageRequest.of(0, 20)))
                .hasMessage("Facet cannot be searched: page_count");
    }

    @Test
    void activeFacetIsMarkedSelfWithCurrentPageHref() {
        book("A", "Horror", "Alice");
        book("B", "Romance", "Bob");
        em.flush();

        FacetGroup genre = group(facetService.getFacets(List.of("genre:Horror"), null, null), "genre");
        FacetLink horror = link(genre, "Horror");
        FacetLink romance = link(genre, "Romance");

        assertThat(horror.rel()).containsExactly("self", "facet");
        assertThat(horror.href()).isEqualTo("/api/v1/books/page?facet=genre%3AHorror");

        assertThat(romance.rel()).containsExactly("facet");
        assertThat(romance.href()).isEqualTo("/api/v1/books/page?facet=genre%3AHorror&facet=genre%3ARomance");
    }

    @Test
    void activeFacetSelfHrefKeepsAllSelections() {
        book("A", "Horror", "Alice");
        em.flush();

        FacetGroup genre = group(facetService.getFacets(List.of("genre:Horror", "author:Alice"), null, null), "genre");
        FacetLink horror = link(genre, "Horror");

        assertThat(horror.rel()).containsExactly("self", "facet");
        assertThat(horror.href()).isEqualTo("/api/v1/books/page?facet=genre%3AHorror&facet=author%3AAlice");
    }

    @Test
    void activeFacetMatchIsCaseInsensitive() {
        book("A", "Horror", "Alice");
        em.flush();

        FacetGroup genre = group(facetService.getFacets(List.of("genre:horror"), null, null), "genre");
        FacetLink horror = link(genre, "Horror");

        assertThat(horror.rel()).contains("self");
    }

    @Test
    void responseCarriesTopLevelSelfLink() {
        book("A", "Horror", "Alice");
        em.flush();

        Link bare = facetService.getFacets(null, null, null).links().getFirst();
        assertThat(bare.rel()).containsExactly("self");
        assertThat(bare.href()).isEqualTo("/api/v1/books/facets");
        assertThat(bare.type()).isEqualTo(Link.JSON_TYPE);

        Link filtered = facetService.getFacets(List.of("genre:Horror"), null, "dune").links().getFirst();
        assertThat(filtered.rel()).containsExactly("self");
        assertThat(filtered.href()).isEqualTo("/api/v1/books/facets?facet=genre%3AHorror&query=dune");
    }

    // Serializes through the Spring-managed Jackson 3 mapper, the same one the HTTP
    // layer uses, so a mapper/annotation mismatch can't slip through unit tests again.
    @Test
    void springMapperSerializesSingleRelAsString() {
        book("A", "Horror", "Alice");
        em.flush();

        String json = springMapper.writeValueAsString(facetService.getFacets(List.of("genre:Horror"), null, null));

        assertThat(json).contains("\"rel\":\"self\"");
        assertThat(json).contains("\"rel\":[\"self\",\"facet\"]");
        assertThat(json).doesNotContain("\"rel\":[\"self\"]");
        assertThat(json).doesNotContain("\"rel\":[\"facet\"]");
    }
}
