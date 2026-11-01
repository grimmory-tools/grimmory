package org.booklore.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.booklore.BookloreApplication;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.service.task.TaskCronService;
import org.flywaydb.core.Flyway;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

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

    private record LibraryFixture(Long libraryId, List<Long> bookIds, List<Long> comicBookIds) {
    }

    private LibraryFixture persistLibraryWithBooksAndComics() {
        LibraryEntity library = LibraryEntity.builder()
                .name("Test Library")
                .icon("book")
                .watch(false)
                .formatPriority(List.of(BookFileType.EPUB, BookFileType.CBX))
                .build();
        entityManager.persist(library);

        List<Long> bookIds = new ArrayList<>();
        List<Long> comicBookIds = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            BookEntity book = BookEntity.builder().library(library).addedOn(Instant.now()).deleted(false).build();
            entityManager.persist(book);
            BookMetadataEntity metadata = BookMetadataEntity.builder().book(book).title("Book " + i).build();
            entityManager.persist(metadata);
            entityManager.flush();
            bookIds.add(book.getId());
            if (i % 2 == 0) {
                entityManager.persist(ComicMetadataEntity.builder()
                        .bookId(book.getId())
                        .bookMetadata(metadata)
                        .issueNumber("Issue " + book.getId())
                        .build());
                comicBookIds.add(book.getId());
            }
        }
        entityManager.flush();
        entityManager.clear();
        return new LibraryFixture(library.getId(), bookIds, comicBookIds);
    }

    private void assertLoadsComicMetadataInOneStatement(String query, Supplier<List<BookEntity>> load, LibraryFixture fixture) {
        entityManager.clear();
        Statistics statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        List<BookEntity> books = load.get();

        assertThat(books).as(query).extracting(BookEntity::getId).containsExactlyInAnyOrderElementsOf(fixture.bookIds());
        for (BookEntity book : books) {
            ComicMetadataEntity comic = book.getMetadata().getComicMetadata();
            assertThat(comic == null ? null : comic.getIssueNumber())
                    .as(query + " book " + book.getId())
                    .isEqualTo(fixture.comicBookIds().contains(book.getId()) ? "Issue " + book.getId() : null);
        }
        assertThat(statistics.getPrepareStatementCount()).as(query + " statements").isEqualTo(1);
    }

    @Test
    void bookQueries_loadComicMetadataWithTheBooks() {
        LibraryFixture fixture = persistLibraryWithBooksAndComics();
        Long libraryId = fixture.libraryId();

        Map<String, Supplier<List<BookEntity>>> queries = Map.of(
                "findAllWithMetadata", bookRepository::findAllWithMetadata,
                "findAllWithMetadataByIds", () -> bookRepository.findAllWithMetadataByIds(new HashSet<>(fixture.bookIds())),
                "findAllWithMetadataByLibraryIds", () -> bookRepository.findAllWithMetadataByLibraryIds(List.of(libraryId)),
                "findAllWithMetadataPage", () -> bookRepository.findAllWithMetadataPage(PageRequest.of(0, 50)).getContent(),
                "findAllWithMetadataByLibraryId", () -> bookRepository.findAllWithMetadataByLibraryId(libraryId));

        queries.forEach((query, load) -> assertLoadsComicMetadataInOneStatement(query, load, fixture));
    }
}
