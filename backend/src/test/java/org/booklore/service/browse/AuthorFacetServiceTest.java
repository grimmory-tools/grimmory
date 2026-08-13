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
import org.booklore.util.FileService;
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        "spring.datasource.url=jdbc:h2:mem:authorfacetservicetest;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE",
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
@Import(AuthorFacetServiceTest.TestConfig.class)
class AuthorFacetServiceTest {

    @Autowired
    private AuthorFacetService facetService;
    @Autowired
    private AuthorPhotoIndex photoIndex;
    @Autowired
    private FileService fileService;
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
    void seed() throws IOException {
        facetService.clearCache();
        photoIndex.clearCache();
        Path imagesRoot = Paths.get(fileService.getAuthorImagesRoot());
        if (Files.isDirectory(imagesRoot)) {
            try (Stream<Path> paths = Files.walk(imagesRoot)) {
                paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
        }

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

    private AuthorEntity author(String name) {
        AuthorEntity author = AuthorEntity.builder().name(name).build();
        em.persist(author);
        return author;
    }

    private void book(String title, List<AuthorEntity> authors, List<String> categoryNames, String language) {
        BookEntity bookEntity = BookEntity.builder()
                .library(library).libraryPath(libraryPath).addedOn(Instant.now()).deleted(false).build();
        em.persist(bookEntity);
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .book(bookEntity).title(title).language(language).build();
        metadata.setAuthors(new ArrayList<>(authors));
        if (categoryNames != null) {
            metadata.setCategories(categoryNames.stream().map(this::category).collect(Collectors.toSet()));
        }
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

    private FacetGroup group(FacetGroupsResponse response, String key) {
        return response.facets().stream()
                .filter(g -> g.metadata().key().equals(key))
                .findFirst().orElseThrow();
    }

    private Long count(FacetGroup group, String value) {
        return group.links().stream()
                .filter(l -> l.value().equals(value))
                .findFirst()
                .map(l -> l.properties().numberOfItems())
                .orElse(0L);
    }

    @Test
    void returnsSortGroupAndFacetGroupsWithCounts() {
        AuthorEntity matched = author("Matched");
        matched.setAsin("B0FAKE");
        matched.setDescription("Bio");
        AuthorEntity unmatched = author("Unmatched");
        book("A", List.of(matched), List.of("Horror"), "en");
        book("B", List.of(unmatched), List.of("Romance", "Horror"), "fr");
        em.flush();

        FacetGroupsResponse response = facetService.getFacets(null, null, null);

        FacetGroup sort = group(response, "sort");
        assertThat(sort.metadata().rel()).isEqualTo("sort");
        assertThat(sort.links().stream().map(FacetLink::value))
                .contains("name", "-name", "bookCount", "-bookCount", "amazonRating", "-personalRating");
        assertThat(sort.links().stream().map(FacetLink::value)).doesNotContain("id", "-id");

        assertThat(count(group(response, "has_asin"), "true")).isEqualTo(1);
        assertThat(count(group(response, "has_asin"), "false")).isEqualTo(1);
        assertThat(count(group(response, "has_description"), "true")).isEqualTo(1);

        FacetGroup genre = group(response, "genre");
        assertThat(count(genre, "Horror")).isEqualTo(2);
        assertThat(count(genre, "Romance")).isEqualTo(1);

        FacetGroup language = group(response, "language");
        assertThat(count(language, "en")).isEqualTo(1);
        assertThat(count(language, "fr")).isEqualTo(1);

        assertThat(response.facets().stream().map(g -> g.metadata().key()))
                .doesNotContain("book_count", "library", "read_status");
    }

    @Test
    void hasPhotoFacetCountsFromDisk() throws IOException {
        AuthorEntity photographed = author("Photographed");
        AuthorEntity faceless = author("Faceless");
        book("A", List.of(photographed), null, null);
        book("B", List.of(faceless), null, null);
        em.flush();

        Path thumbnail = Paths.get(fileService.getAuthorThumbnailFile(photographed.getId()));
        Files.createDirectories(thumbnail.getParent());
        Files.write(thumbnail, new byte[]{1});

        FacetGroupsResponse response = facetService.getFacets(null, null, null);
        assertThat(count(group(response, "has_photo"), "true")).isEqualTo(1);
        assertThat(count(group(response, "has_photo"), "false")).isEqualTo(1);
    }

    @Test
    void ownFacetSelectionsAreOmittedFromItsCounts() {
        AuthorEntity matched = author("Matched");
        matched.setAsin("B0FAKE");
        AuthorEntity unmatched = author("Unmatched");
        book("A", List.of(matched), List.of("Horror"), null);
        book("B", List.of(unmatched), List.of("Romance"), null);
        em.flush();

        FacetGroupsResponse response = facetService.getFacets(List.of("has_asin:true"), null, null);

        assertThat(count(group(response, "has_asin"), "false")).isEqualTo(1);
        assertThat(count(group(response, "genre"), "Horror")).isEqualTo(1);
        assertThat(count(group(response, "genre"), "Romance")).isEqualTo(0);
    }

    @Test
    void zeroCountValuesAreOmitted() {
        AuthorEntity unmatched = author("Unmatched");
        book("A", List.of(unmatched), null, null);
        em.flush();

        FacetGroupsResponse response = facetService.getFacets(null, null, null);
        assertThat(group(response, "has_asin").links().stream().map(FacetLink::value))
                .containsExactly("false");
    }

    @Test
    void activeFacetLinksCarrySelfRel() {
        AuthorEntity matched = author("Matched");
        matched.setAsin("B0FAKE");
        AuthorEntity unmatched = author("Unmatched");
        book("A", List.of(matched), null, null);
        book("B", List.of(unmatched), null, null);
        em.flush();

        FacetGroupsResponse response = facetService.getFacets(List.of("has_asin:true"), null, null);
        FacetLink active = group(response, "has_asin").links().stream()
                .filter(l -> l.value().equals("true"))
                .findFirst().orElseThrow();
        assertThat(active.rel()).contains("self", "facet");

        Set<String> hrefs = group(response, "has_asin").links().stream()
                .map(FacetLink::href).collect(Collectors.toSet());
        assertThat(hrefs).allSatisfy(href -> assertThat(href).startsWith("/api/v1/authors/page"));
    }
}
