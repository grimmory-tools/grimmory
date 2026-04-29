package org.booklore.app.service;

import jakarta.persistence.EntityManager;
import org.booklore.BookloreApplication;
import org.booklore.app.dto.AppAuthorSummary;
import org.booklore.app.dto.AppPageResponse;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.AuthorRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = BookloreApplication.class)
@Transactional
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:author_int_test_db;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.path-config=build/tmp/test-config",
        "app.features.oidc-enabled=false",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.jpa.properties.hibernate.connection.provider_disables_autocommit=false"
})
@Import(AppAuthorServiceIntegrationTest.TestConfig.class)
class AppAuthorServiceIntegrationTest {

    @Autowired
    private AppAuthorService appAuthorService;

    @Autowired
    private AuthorRepository authorRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private AuthenticationService authenticationService;

    @TestConfiguration
    public static class TestConfig {
        @Bean("flyway")
        @Primary
        public Flyway flyway() {
            return mock(Flyway.class);
        }

        @Bean
        @Primary
        public AuthenticationService authenticationService() {
            return mock(AuthenticationService.class);
        }

        @Bean
        @Primary
        public FileService fileService() {
            FileService mock = mock(FileService.class);
            when(mock.getIconsSvgFolder()).thenReturn("build/tmp/test-icons");
            when(mock.getAuthorThumbnailFile(anyLong())).thenReturn("build/tmp/test-authors/thumb.jpg");
            return mock;
        }
    }

    private LibraryEntity library;

    @BeforeEach
    void setUp() {
        library = LibraryEntity.builder()
                .name("Test Library")
                .icon("book")
                .formatPriority(List.of(BookFileType.EPUB))
                .build();
        entityManager.persist(library);

        BookLoreUser.UserPermissions permissions = new BookLoreUser.UserPermissions();
        permissions.setAdmin(true);
        
        BookLoreUser admin = BookLoreUser.builder()
                .id(1L)
                .permissions(permissions)
                .build();
        when(authenticationService.getAuthenticatedUser()).thenReturn(admin);
    }

    @Test
    void getAuthors_returnsCorrectBookCounts() {
        AuthorEntity author1 = createAuthor("Author 1");
        AuthorEntity author2 = createAuthor("Author 2");

        BookEntity book1 = createBook("Book 1", library);
        createBookFile(book1);
        createBookMetadata(book1, "Book 1", List.of(author1));

        BookEntity book2 = createBook("Book 2", library);
        createBookFile(book2);
        createBookMetadata(book2, "Book 2", List.of(author1, author2));

        entityManager.flush();
        entityManager.clear();

        AppPageResponse<AppAuthorSummary> response = appAuthorService.getAuthors(0, 10, "name", "asc", null, null, null);

        assertThat(response.getContent()).hasSize(2);
        
        AppAuthorSummary summary1 = response.getContent().stream()
                .filter(s -> s.getName().equals("Author 1"))
                .findFirst().orElseThrow();
        assertThat(summary1.getBookCount()).isEqualTo(2);

        AppAuthorSummary summary2 = response.getContent().stream()
                .filter(s -> s.getName().equals("Author 2"))
                .findFirst().orElseThrow();
        assertThat(summary2.getBookCount()).isEqualTo(1);
    }

    @Test
    void getAuthors_filtersBySearchText() {
        AuthorEntity author1 = createAuthor("Tolkien");
        AuthorEntity author2 = createAuthor("Asimov");

        BookEntity book1 = createBook("The Hobbit", library);
        createBookFile(book1);
        createBookMetadata(book1, "The Hobbit", List.of(author1));

        BookEntity book2 = createBook("Foundation", library);
        createBookFile(book2);
        createBookMetadata(book2, "Foundation", List.of(author2));

        AppPageResponse<AppAuthorSummary> response = appAuthorService.getAuthors(0, 10, "name", "asc", null, "tolk", null);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getName()).isEqualTo("Tolkien");
    }

    @Test
    void getAuthors_filtersByHasPhoto() {
        AuthorEntity author1 = createAuthor("Author with photo");
        author1.setHasPhoto(true);
        authorRepository.save(author1);

        AuthorEntity author2 = createAuthor("Author without photo");
        author2.setHasPhoto(false);
        authorRepository.save(author2);

        BookEntity book1 = createBook("Book 1", library);
        createBookFile(book1);
        createBookMetadata(book1, "Book 1", List.of(author1));

        BookEntity book2 = createBook("Book 2", library);
        createBookFile(book2);
        createBookMetadata(book2, "Book 2", List.of(author2));

        AppPageResponse<AppAuthorSummary> response = appAuthorService.getAuthors(0, 10, "name", "asc", null, null, true);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getName()).isEqualTo("Author with photo");
    }

    private AuthorEntity createAuthor(String name) {
        AuthorEntity author = AuthorEntity.builder()
                .name(name)
                .build();
        return authorRepository.save(author);
    }

    private BookEntity createBook(String title, LibraryEntity library) {
        BookEntity book = BookEntity.builder()
                .library(library)
                .build();
        entityManager.persist(book);
        return book;
    }

    private void createBookFile(BookEntity book) {
        BookFileEntity bookFile = BookFileEntity.builder()
                .book(book)
                .fileName("test.epub")
                .fileSubPath("test")
                .bookType(BookFileType.EPUB)
                .isBookFormat(true)
                .build();
        entityManager.persist(bookFile);
    }

    private void createBookMetadata(BookEntity book, String title, List<AuthorEntity> authors) {
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .book(book)
                .bookId(book.getId())
                .title(title)
                .authors(authors)
                .build();
        entityManager.persist(metadata);
    }
}
