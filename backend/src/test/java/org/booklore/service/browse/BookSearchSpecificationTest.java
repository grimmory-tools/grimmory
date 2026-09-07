package org.booklore.service.browse;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.booklore.BookloreApplication;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookRepository;
import org.booklore.service.task.TaskCronService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies {@link BookSearchSpecification#matching(String)} against H2, exercising the real
 * JPA criteria query. This is the spec behind the {@code query} parameter of
 * {@code GET /api/v1/books/page} (the command-palette search), which must match on filename
 * (see issue #2293).
 */
@SpringBootTest(classes = BookloreApplication.class)
@Transactional
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
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
@Import(BookSearchSpecificationTest.TestConfig.class)
class BookSearchSpecificationTest {

    @Autowired
    private BookRepository bookRepository;

    @PersistenceContext
    private EntityManager entityManager;

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

    @Test
    void matching_findsBookByFilename_whenTitleAndAuthorDoNotMatch() {
        LibraryEntity library = persistLibrary();
        BookEntity book = persistBook(library, "Unrelated Title", "The Fellowship of the Ring.epub");

        entityManager.flush();
        entityManager.clear();

        List<BookEntity> results = bookRepository.findAll(BookSearchSpecification.matching("fellowship"));

        assertThat(results).extracting(BookEntity::getId).containsExactly(book.getId());
    }

    @Test
    void matching_isCaseInsensitiveOnFilename() {
        LibraryEntity library = persistLibrary();
        BookEntity book = persistBook(library, "Unrelated Title", "Dune-Messiah.pdf");

        entityManager.flush();
        entityManager.clear();

        List<BookEntity> results = bookRepository.findAll(BookSearchSpecification.matching("DUNE"));

        assertThat(results).extracting(BookEntity::getId).containsExactly(book.getId());
    }

    @Test
    void matching_doesNotMatch_whenQueryIsAbsentFromAllFields() {
        LibraryEntity library = persistLibrary();
        persistBook(library, "Unrelated Title", "some-file.epub");

        entityManager.flush();
        entityManager.clear();

        List<BookEntity> results = bookRepository.findAll(BookSearchSpecification.matching("nonexistent"));

        assertThat(results).isEmpty();
    }

    @Test
    void matching_returnsAllBooks_whenQueryIsNullEmptyOrBlank() {
        LibraryEntity library = persistLibrary();
        BookEntity first = persistBook(library, "Some Title", "some-file.epub");
        BookEntity second = persistBook(library, "Another Title", "another-file.pdf");

        entityManager.flush();
        entityManager.clear();

        // A null/blank query short-circuits to cb.conjunction(); every persisted book must match,
        // not just one, so assert the full set is returned.
        for (String blank : Arrays.asList(null, "", "   ")) {
            List<BookEntity> results = bookRepository.findAll(BookSearchSpecification.matching(blank));
            assertThat(results).extracting(BookEntity::getId)
                    .containsExactlyInAnyOrder(first.getId(), second.getId());
        }
    }

    @Test
    void matching_returnsBookOnce_whenMultipleFilesShareTheQuery() {
        LibraryEntity library = persistLibrary();
        BookEntity book = persistBook(library, "Unrelated Title", "The Hobbit.epub");
        addFile(book, "The Hobbit.pdf", BookFileType.PDF);

        entityManager.flush();
        entityManager.clear();

        List<BookEntity> results = bookRepository.findAll(BookSearchSpecification.matching("hobbit"));

        assertThat(results).extracting(BookEntity::getId).containsExactly(book.getId());
    }

    private LibraryEntity persistLibrary() {
        LibraryEntity library = LibraryEntity.builder()
                .name("Search Library " + System.nanoTime())
                .icon("book")
                .watch(false)
                .formatPriority(List.of(BookFileType.EPUB, BookFileType.PDF))
                .build();
        entityManager.persist(library);

        LibraryPathEntity libraryPath = LibraryPathEntity.builder()
                .library(library)
                .path("/search/" + System.nanoTime())
                .build();
        entityManager.persist(libraryPath);
        library.getLibraryPaths().add(libraryPath);
        return library;
    }

    private BookEntity persistBook(LibraryEntity library, String title, String fileName) {
        BookEntity book = BookEntity.builder()
                .library(library)
                .libraryPath(library.getLibraryPaths().getFirst())
                .addedOn(Instant.now())
                .deleted(false)
                .build();
        entityManager.persist(book);

        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .book(book)
                .title(title)
                .build();
        book.setMetadata(metadata);
        entityManager.persist(metadata);

        addFile(book, fileName, BookFileType.EPUB);
        return book;
    }

    private void addFile(BookEntity book, String fileName, BookFileType type) {
        BookFileEntity file = BookFileEntity.builder()
                .book(book)
                .fileName(fileName)
                .fileSubPath(".")
                .isBookFormat(true)
                .bookType(type)
                .fileSizeKb(256L)
                .currentHash("hash-" + System.nanoTime())
                .build();
        book.getBookFiles().add(file);
        entityManager.persist(file);
    }
}
