package org.booklore.app.specification;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.booklore.BookloreApplication;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookRepository;
import org.booklore.service.task.TaskCronService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SpringBootTest(classes = BookloreApplication.class)
@Transactional
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:app_book_spec_file_size;DB_CLOSE_DELAY=-1",
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
@Import(AppBookSpecificationTest.TestConfig.class)
class AppBookSpecificationTest {

    @TestConfiguration
    static class TestConfig {
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

    @Autowired
    private BookRepository bookRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private LibraryEntity library;
    private LibraryPathEntity libraryPath;

    @BeforeEach
    void setUp() {
        library = LibraryEntity.builder()
                .name("Test Library")
                .icon("book")
                .watch(false)
                .build();
        entityManager.persist(library);

        libraryPath = LibraryPathEntity.builder()
                .library(library)
                .path("/test/path")
                .build();
        entityManager.persist(libraryPath);
        entityManager.flush();
    }

    @Nested
    class FileSizeFilters {

        @Test
        void matchesWhenAnyBookFormatFileFallsInSelectedBucket() {
            BookEntity primarySmall = createBook("Primary Small", 500L, 5_000L);
            BookEntity alternativeSmallOnly = createBook("Alternative Small Only", 5_000L, 500L);
            BookEntity largeOnly = createBook("Large Only", 70_000L);

            entityManager.flush();
            entityManager.clear();

            var page = bookRepository.findAll(
                    AppBookSpecification.withFileSizes(List.of("0"), "or"),
                    PageRequest.of(0, 10)
            );

            assertThat(page.getContent())
                    .extracting(BookEntity::getId)
                    .containsExactlyInAnyOrder(primarySmall.getId(), alternativeSmallOnly.getId())
                    .doesNotContain(largeOnly.getId());
        }

        @Test
        void notMode_excludesBooksWhoseAnyBookFormatFileMatches() {
            BookEntity primarySmall = createBook("Primary Small", 500L, 5_000L);
            BookEntity alternativeSmallOnly = createBook("Alternative Small Only", 5_000L, 500L);
            BookEntity largeOnly = createBook("Large Only", 70_000L);

            entityManager.flush();
            entityManager.clear();

            var page = bookRepository.findAll(
                    AppBookSpecification.withFileSizes(List.of("1"), "not"),
                    PageRequest.of(0, 10)
            );

            assertThat(page.getContent())
                    .extracting(BookEntity::getId)
                    .contains(largeOnly.getId())
                    .doesNotContain(primarySmall.getId(), alternativeSmallOnly.getId());
        }

        @Test
        void orMode_matchesBooksWithFilesInAnySelectedRange() {
            BookEntity smallOnly = createBook("Small Only", 500L);
            BookEntity mediumOnly = createBook("Medium Only", 5_000L);
            BookEntity mixedFormats = createBook("Mixed Formats", 70_000L, 5_000L);
            BookEntity largeOnly = createBook("Large Only", 70_000L);

            entityManager.flush();
            entityManager.clear();

            var page = bookRepository.findAll(
                    AppBookSpecification.withFileSizes(List.of("0", "1"), "or"),
                    PageRequest.of(0, 10)
            );

            assertThat(page.getContent())
                    .extracting(BookEntity::getId)
                    .containsExactlyInAnyOrder(smallOnly.getId(), mediumOnly.getId(), mixedFormats.getId())
                    .doesNotContain(largeOnly.getId());
        }

        @Test
        void andMode_requiresFilesAcrossAllSelectedRanges() {
            BookEntity spansBothRanges = createBook("Spans Both Ranges", 500L, 5_000L);
            BookEntity smallOnly = createBook("Small Only", 500L);
            BookEntity mediumOnly = createBook("Medium Only", 5_000L);
            BookEntity largeOnly = createBook("Large Only", 70_000L);

            entityManager.flush();
            entityManager.clear();

            var page = bookRepository.findAll(
                    AppBookSpecification.withFileSizes(List.of("0", "1"), "and"),
                    PageRequest.of(0, 10)
            );

            assertThat(page.getContent())
                    .extracting(BookEntity::getId)
                    .containsExactly(spansBothRanges.getId())
                    .doesNotContain(smallOnly.getId(), mediumOnly.getId(), largeOnly.getId());
        }

        @Test
        void ignoresNonBookFormatFiles() {
            BookEntity onlySupplementaryMatch = createBook("Only Supplementary Match", 70_000L);
            BookFileEntity supplementary = BookFileEntity.builder()
                    .book(onlySupplementaryMatch)
                    .fileName("only-supplementary-match-cover.jpg")
                    .fileSubPath("")
                    .isBookFormat(false)
                    .bookType(BookFileType.PDF)
                    .fileSizeKb(500L)
                    .initialHash("supp-initial")
                    .currentHash("supp-current")
                    .addedOn(Instant.now())
                    .build();
            entityManager.persist(supplementary);

            entityManager.flush();
            entityManager.clear();

            var page = bookRepository.findAll(
                    AppBookSpecification.withFileSizes(List.of("0"), "or"),
                    PageRequest.of(0, 10)
            );

            assertThat(page.getContent())
                    .extracting(BookEntity::getId)
                    .doesNotContain(onlySupplementaryMatch.getId());
        }

        @Test
        void notMode_includesBooksWithOnlySupplementaryMatches() {
            BookEntity onlySupplementaryMatch = createBook("Only Supplementary Match", 70_000L);
            BookFileEntity supplementary = BookFileEntity.builder()
                    .book(onlySupplementaryMatch)
                    .fileName("only-supplementary-match-cover.jpg")
                    .fileSubPath("")
                    .isBookFormat(false)
                    .bookType(BookFileType.PDF)
                    .fileSizeKb(500L)
                    .initialHash("supp-initial")
                    .currentHash("supp-current")
                    .addedOn(Instant.now())
                    .build();
            entityManager.persist(supplementary);

            entityManager.flush();
            entityManager.clear();

            var page = bookRepository.findAll(
                    AppBookSpecification.withFileSizes(List.of("0"), "not"),
                    PageRequest.of(0, 10)
            );

            assertThat(page.getContent())
                    .extracting(BookEntity::getId)
                    .contains(onlySupplementaryMatch.getId());
        }

        @Test
        void andMode_noLongerBehavesLikeOrForMutuallyExclusiveRanges() {
            BookEntity underOneMb = createBook("Under One Mb", 500L);
            BookEntity oneToTenMb = createBook("One To Ten Mb", 5_000L);
            BookEntity spansBothRanges = createBook("Spans Both Ranges", 500L, 5_000L);

            entityManager.flush();
            entityManager.clear();

            var page = bookRepository.findAll(
                    AppBookSpecification.withFileSizes(List.of("0", "1"), "and"),
                    PageRequest.of(0, 10)
            );

            assertThat(page.getContent())
                    .extracting(BookEntity::getId)
                    .containsExactly(spansBothRanges.getId())
                    .doesNotContain(underOneMb.getId(), oneToTenMb.getId());
        }
    }

    private BookEntity createBook(String title, Long... fileSizesKb) {
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
                .bookId(book.getId())
                .title(title)
                .build();
        entityManager.persist(metadata);
        book.setMetadata(metadata);

        for (int index = 0; index < fileSizesKb.length; index++) {
            BookFileEntity file = BookFileEntity.builder()
                    .book(book)
                    .fileName(title.toLowerCase().replace(' ', '-') + "-" + index + ".epub")
                    .fileSubPath("")
                    .isBookFormat(true)
                    .bookType(index == 0 ? BookFileType.EPUB : BookFileType.PDF)
                    .fileSizeKb(fileSizesKb[index])
                    .initialHash("initial-" + title + "-" + index)
                    .currentHash("current-" + title + "-" + index)
                    .addedOn(Instant.now().plusSeconds(index))
                    .build();
            entityManager.persist(file);
        }

        entityManager.flush();
        return book;
    }
}
