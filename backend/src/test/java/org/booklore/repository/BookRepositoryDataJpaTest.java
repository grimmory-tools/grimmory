package org.booklore.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.booklore.BookloreApplication;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.service.task.TaskCronService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import org.springframework.boot.test.context.TestConfiguration;

@SpringBootTest(classes = {
        BookloreApplication.class
})
@Transactional
@TestPropertySource(properties = {
        "logging.level.root=debug",
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
        "spring.jpa.properties.hibernate.connection.provider_disables_autocommit=false",
        "spring.jpa.properties.hibernate.enable_lazy_load_no_trans=false"
})
@Import(BookRepositoryDataJpaTest.TestConfig.class)
class BookRepositoryDataJpaTest {

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
    void contextLoads() {
        assertThat(bookRepository).isNotNull();
    }

    @Test
    void findAllWithMetadataByIds_executesAgainstJpaMetamodel() {
        LibraryEntity library = LibraryEntity.builder()
                .name("Test Library")
                .icon("book")
                .watch(false)
                .formatPriority(List.of(BookFileType.EPUB, BookFileType.PDF))
                .build();
        entityManager.persist(library);
        entityManager.flush();

        LibraryPathEntity libraryPath = LibraryPathEntity.builder()
                .library(library)
                .path("/test/path")
                .build();
        entityManager.persist(libraryPath);
        entityManager.flush();

        BookEntity book = BookEntity.builder()
                .library(library)
                .libraryPath(libraryPath)
                .addedOn(Instant.now())
                .deleted(false)
                .build();
        entityManager.persist(book);
        entityManager.flush();

        BookFileEntity bookFile = BookFileEntity.builder()
                .book(book)
                .fileName("test.epub")
                .fileSubPath("")
                .isBookFormat(true)
                .bookType(BookFileType.EPUB)
                .fileSizeKb(500L)
                .initialHash("hash1")
                .currentHash("hash1")
                .addedOn(Instant.now())
                .build();
        entityManager.persist(bookFile);
        entityManager.flush();

        entityManager.clear();

        Optional<BookEntity> result = bookRepository.findByIdForKoboDownload(book.getId());

        TestTransaction.end();

        assertThat(result).isPresent();

        BookEntity bookEntity = result.get();
        assertThat(bookEntity.getId()).isEqualTo(book.getId());
        assertThat(bookEntity.getPrimaryBookFile()).isNotNull();
    }

    private record LibraryFixture(Long libraryId, List<Long> bookIds) {
    }

    private LibraryFixture persistLibraryWithBooks(int bookCount) {
        LibraryEntity library = LibraryEntity.builder()
                .name("Test Library")
                .icon("book")
                .watch(false)
                .formatPriority(List.of(BookFileType.EPUB, BookFileType.PDF))
                .build();
        entityManager.persist(library);
        entityManager.flush();

        LibraryPathEntity libraryPath = LibraryPathEntity.builder()
                .library(library)
                .path("/test/path")
                .build();
        entityManager.persist(libraryPath);
        entityManager.flush();

        List<Long> bookIds = new java.util.ArrayList<>();
        for (int i = 0; i < bookCount; i++) {
            BookEntity book = BookEntity.builder()
                    .library(library)
                    .libraryPath(libraryPath)
                    .addedOn(Instant.now())
                    .deleted(false)
                    .build();
            entityManager.persist(book);
            entityManager.flush();

            BookMetadataEntity metadata = BookMetadataEntity.builder()
                    .book(book)
                    .title("Book " + i)
                    .build();
            entityManager.persist(metadata);
            bookIds.add(book.getId());
        }
        entityManager.flush();
        entityManager.clear();
        return new LibraryFixture(library.getId(), bookIds);
    }

    private org.hibernate.stat.Statistics resetStatistics() {
        org.hibernate.stat.Statistics statistics = entityManager.getEntityManagerFactory()
                .unwrap(org.hibernate.SessionFactory.class)
                .getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        return statistics;
    }

    // No comic_metadata row exists for any book in these fixtures — accessing it is what
    // previously triggered a per-book SELECT (see #2482). Each fetch plan below issues exactly
    // one SELECT regardless of book count, so the statement count is asserted against a fixed
    // upper bound rather than one derived from bookCount.
    private static final long MAX_STATEMENTS_FOR_FIXED_FETCH_PLAN = 2;

    @Test
    void findAllWithMetadata_doesNotIssueOneSelectPerBookForComicMetadata() {
        int bookCount = 20;
        persistLibraryWithBooks(bookCount);
        org.hibernate.stat.Statistics statistics = resetStatistics();

        List<BookEntity> books = bookRepository.findAllWithMetadata();
        for (BookEntity book : books) {
            assertThat(book.getMetadata().getComicMetadata()).isNull();
        }

        long statementCount = statistics.getPrepareStatementCount();
        TestTransaction.end();

        assertThat(books).hasSize(bookCount);
        assertThat(statementCount)
                .as("statement count must not scale with the number of books")
                .isLessThanOrEqualTo(MAX_STATEMENTS_FOR_FIXED_FETCH_PLAN);
    }

    @Test
    void findAllWithMetadataByIds_doesNotIssueOneSelectPerBookForComicMetadata() {
        int bookCount = 20;
        LibraryFixture fixture = persistLibraryWithBooks(bookCount);
        org.hibernate.stat.Statistics statistics = resetStatistics();

        List<BookEntity> books = bookRepository.findAllWithMetadataByIds(new java.util.HashSet<>(fixture.bookIds()));
        for (BookEntity book : books) {
            assertThat(book.getMetadata().getComicMetadata()).isNull();
        }

        long statementCount = statistics.getPrepareStatementCount();
        TestTransaction.end();

        assertThat(books).hasSize(bookCount);
        assertThat(statementCount)
                .as("statement count must not scale with the number of books")
                .isLessThanOrEqualTo(MAX_STATEMENTS_FOR_FIXED_FETCH_PLAN);
    }

    @Test
    void findAllWithMetadataByIds_includesBooksWithNullLibraryPath() {
        LibraryEntity library = LibraryEntity.builder()
                .name("Test Library")
                .icon("book")
                .watch(false)
                .formatPriority(List.of(BookFileType.EPUB, BookFileType.PDF))
                .build();
        entityManager.persist(library);
        entityManager.flush();

        BookEntity book = BookEntity.builder()
                .library(library)
                .libraryPath(null)
                .addedOn(Instant.now())
                .deleted(false)
                .build();
        entityManager.persist(book);
        entityManager.flush();
        entityManager.clear();

        List<BookEntity> books = bookRepository.findAllWithMetadataByIds(java.util.Set.of(book.getId()));
        TestTransaction.end();

        assertThat(books).hasSize(1);
        assertThat(books.get(0).getLibraryPath()).isNull();
    }

    @Test
    void findAllWithMetadataByLibraryIds_doesNotIssueOneSelectPerBookForComicMetadata() {
        int bookCount = 20;
        LibraryFixture fixture = persistLibraryWithBooks(bookCount);
        org.hibernate.stat.Statistics statistics = resetStatistics();

        List<BookEntity> books = bookRepository.findAllWithMetadataByLibraryIds(List.of(fixture.libraryId()));
        for (BookEntity book : books) {
            assertThat(book.getMetadata().getComicMetadata()).isNull();
        }

        long statementCount = statistics.getPrepareStatementCount();
        TestTransaction.end();

        assertThat(books).hasSize(bookCount);
        assertThat(statementCount)
                .as("statement count must not scale with the number of books")
                .isLessThanOrEqualTo(MAX_STATEMENTS_FOR_FIXED_FETCH_PLAN);
    }

    @Test
    void findAllWithMetadataPage_doesNotIssueOneSelectPerBookForComicMetadata() {
        int bookCount = 20;
        persistLibraryWithBooks(bookCount);
        org.hibernate.stat.Statistics statistics = resetStatistics();

        List<BookEntity> books = bookRepository.findAllWithMetadataPage(
                org.springframework.data.domain.PageRequest.of(0, bookCount)).getContent();
        for (BookEntity book : books) {
            assertThat(book.getMetadata().getComicMetadata()).isNull();
        }

        long statementCount = statistics.getPrepareStatementCount();
        TestTransaction.end();

        assertThat(books).hasSize(bookCount);
        assertThat(statementCount)
                .as("statement count must not scale with the number of books")
                .isLessThanOrEqualTo(MAX_STATEMENTS_FOR_FIXED_FETCH_PLAN + 1); // +1 for the paging COUNT query
    }

    @Test
    void findAllWithMetadataPage_includesBooksWithNullLibraryPath() {
        LibraryEntity library = LibraryEntity.builder()
                .name("Test Library")
                .icon("book")
                .watch(false)
                .formatPriority(List.of(BookFileType.EPUB, BookFileType.PDF))
                .build();
        entityManager.persist(library);
        entityManager.flush();

        BookEntity book = BookEntity.builder()
                .library(library)
                .libraryPath(null)
                .addedOn(Instant.now())
                .deleted(false)
                .build();
        entityManager.persist(book);
        entityManager.flush();
        entityManager.clear();

        List<BookEntity> books = bookRepository.findAllWithMetadataPage(
                org.springframework.data.domain.PageRequest.of(0, 10)).getContent();
        TestTransaction.end();

        assertThat(books).hasSize(1);
        assertThat(books.get(0).getLibraryPath()).isNull();
    }
}
